/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.VarInt;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.ChannelPayloadText;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ChannelLogger extends Module {
    private static final AttributeKey<Session> SESSION = AttributeKey.valueOf("thm:channel-log");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter PACKET_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final HexFormat HEX = HexFormat.ofDelimiter(" ");
    private static final Semaphore QUEUED_BYTES = new Semaphore(32 * 1024 * 1024);
    private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(256), work -> Thread.ofPlatform().daemon().name("THM Channel Logger").unstarted(work));
    private static volatile ChannelLogger active;

    static {
        WRITER.allowCoreThreadTimeOut(true);
    }

    private final Setting<String> channels = settings.getDefaultGroup().add(new StringSetting.Builder()
        .name("channels")
        .description("Comma-separated channels or namespace:* filters, with an empty list matching all channels.")
        .defaultValue("")
        .onChanged(value -> updateOptions())
        .build());
    private final Setting<Boolean> onlyCustomChannels = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("only-custom-channels")
        .description("Skips channels in the minecraft namespace.")
        .defaultValue(false)
        .onChanged(value -> updateOptions())
        .build());
    private final Setting<Boolean> hexDump = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("hex-dump")
        .description("Writes raw bytes as hex instead of Base64.")
        .defaultValue(true)
        .onChanged(value -> updateOptions())
        .build());
    private final Setting<Boolean> redactSensitive = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("redact-sensitive")
        .description("Hides payloads matching credential channels, fields, or configured secrets.")
        .defaultValue(true)
        .onChanged(value -> updateOptions())
        .build());
    private final Setting<Boolean> logToChat = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("log-to-chat")
        .description("Shows decoded payload previews in chat.")
        .defaultValue(true)
        .onChanged(value -> updateOptions())
        .build());

    private final ArrayBlockingQueue<Preview> chat = new ArrayBlockingQueue<>(128);
    private volatile Options options;
    private volatile Secrets secrets = new Secrets("", "");

    public ChannelLogger() {
        super(THMAddon.MAIN, "channel-logger", "Logs incoming and outgoing custom payloads.");
        runInMainMenu = true;
    }

    @Override
    public void onActivate() {
        updateOptions();
        updateSecrets();
        active = this;
    }

    @Override
    public void onDeactivate() {
        if (active == this) active = null;
        chat.clear();
    }

    private void updateOptions() {
        if (logToChat == null) return;
        Set<String> filters = new HashSet<>(Arrays.asList(channels.get().split("[,\\s]+")));
        filters.remove("");
        options = new Options(Set.copyOf(filters), onlyCustomChannels.get(), hexDump.get(), redactSensitive.get(), logToChat.get());
        chat.clear();
    }

    private void updateSecrets() {
        THMSystem system = THMSystem.get();
        if (system == null) return;
        String token = system.getApiToken();
        String password = system.getCrackedPassword();
        if (!token.equals(secrets.token()) || !password.equals(secrets.password())) secrets = new Secrets(token, password);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        updateSecrets();
        if (mc.player == null) return;
        for (int i = 0; i < 6; i++) {
            Preview preview = chat.poll();
            if (preview == null) break;
            if (preview.options() == options) info("%s", preview.text());
        }
    }

    public static void capture(Channel channel, ConnectionProtocol phase, Packet<?> packet, ByteBuf buffer, int start, int end) {
        ChannelLogger logger = active;
        if (logger == null) return;

        try {
            String id = switch (packet) {
                case ClientboundCustomPayloadPacket incoming -> incoming.payload().type().id().toString();
                case ServerboundCustomPayloadPacket outgoing -> outgoing.payload().type().id().toString();
                case ClientboundCustomQueryPacket query -> query.payload().id().toString();
                case ServerboundCustomQueryAnswerPacket ignored -> "";
                default -> null;
            };
            if (id == null) return;

            Session session = channel.attr(SESSION).get();
            if (session == null) {
                var attribute = channel.attr(SESSION);
                var created = new Session();
                Session previous = attribute.setIfAbsent(created);
                session = previous == null ? created : previous;
            }

            boolean incoming = packet.type().flow() == PacketFlow.CLIENTBOUND;
            if (packet instanceof ClientboundCustomQueryPacket query) {
                session.queries.put(query.transactionId(), id);
                if (session.queries.size() > 256) session.queries.pollFirstEntry();
            } else if (packet instanceof ServerboundCustomQueryAnswerPacket answer) {
                id = session.queries.remove(answer.transactionId());
                if (id == null) id = "login-query/" + answer.transactionId();
            }

            Options options = logger.options;
            if (!options.matches(id)) return;
            ByteBuf body = payloadBody(buffer, start, end, phase, incoming);
            logger.enqueue(session, phase, incoming, id, body, options);
        } catch (RuntimeException e) {
            THMAddon.LOG.debug("Custom payload observation failed ({})", e.getClass().getSimpleName());
        }
    }

    static ByteBuf payloadBody(ByteBuf buffer, int start, int end, ConnectionProtocol phase, boolean incoming) {
        ByteBuf body = buffer.slice(start, end - start);
        VarInt.read(body);
        if (phase == ConnectionProtocol.LOGIN) VarInt.read(body);
        if (phase != ConnectionProtocol.LOGIN || incoming) {
            body.skipBytes(VarInt.read(body));
        } else if (!body.readBoolean()) {
            body.skipBytes(body.readableBytes());
        }
        return body;
    }

    private void enqueue(Session session, ConnectionProtocol phase, boolean incoming, String id, ByteBuf body, Options options) {
        // shortcut: report dropped records under writer backpressure instead of growing the queue.
        int size = body.readableBytes();
        if (!QUEUED_BYTES.tryAcquire(size)) {
            session.dropped.incrementAndGet();
            return;
        }

        try {
            byte[] bytes = new byte[size];
            body.getBytes(body.readerIndex(), bytes);
            LocalDateTime time = LocalDateTime.now();
            Secrets capturedSecrets = secrets;
            WRITER.execute(() -> {
                try {
                    write(session, time, phase, incoming, id, bytes, options, capturedSecrets);
                } finally {
                    QUEUED_BYTES.release(size);
                }
            });
        } catch (RejectedExecutionException e) {
            session.dropped.incrementAndGet();
            QUEUED_BYTES.release(size);
        } catch (RuntimeException e) {
            QUEUED_BYTES.release(size);
            throw e;
        }
    }

    private void write(Session session, LocalDateTime time, ConnectionProtocol phase, boolean incoming, String id,
                       byte[] bytes, Options captured, Secrets capturedSecrets) {
        String utf8 = ChannelPayloadText.utf8(bytes, 0, bytes.length);
        String decoded = ChannelPayloadText.decode(id, bytes);
        boolean hideId = captured.redact() && (ChannelPayloadText.containsSecret(id, capturedSecrets.token())
            || ChannelPayloadText.containsSecret(id, capturedSecrets.password()));
        boolean redact = captured.redact() && (hideId || ChannelPayloadText.sensitiveChannel(id)
            || ChannelPayloadText.sensitiveContent(decoded, capturedSecrets.token(), capturedSecrets.password())
            || ChannelPayloadText.sensitiveContent(new String(bytes, StandardCharsets.ISO_8859_1),
                asBinary(capturedSecrets.token()), asBinary(capturedSecrets.password())));
        String direction = incoming ? "S2C" : "C2S";
        String label = hideId ? "[redacted]" : ChannelPayloadText.escape(id);
        String description = redact ? "[redacted]" : ChannelPayloadText.escape(decoded);
        String header = "[" + PACKET_TIME.format(time) + "] [" + direction + "] [" + phase.id() + "] Channel: " + label;
        var entry = new StringBuilder(header).append("\nSize: ").append(bytes.length).append(" bytes\nDecoded: ").append(description);
        if (!redact) {
            if (utf8 != null && !utf8.isEmpty() && !utf8.equals(decoded)) {
                entry.append("\nUTF-8: ").append(ChannelPayloadText.escape(utf8));
            }
            if (captured.hex()) appendHex(entry, bytes);
            else entry.append("\nRaw Base64: ").append(Base64.getEncoder().encodeToString(bytes));
        }
        entry.append("\n\n");

        try {
            if (session.file == null) {
                Path directory = FabricLoader.getInstance().getGameDir().resolve("logs/thm-channels");
                Files.createDirectories(directory);
                session.file = Files.createTempFile(directory, FILE_TIME.format(time) + "_", ".txt");
            }
            int dropped = session.dropped.getAndSet(0);
            if (dropped > 0) entry.append("[Logger] Dropped ").append(dropped).append(" payload(s) while the write queue was full.\n\n");
            Files.writeString(session.file, entry, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            if (!session.reportedWriteError) {
                session.reportedWriteError = true;
                THMAddon.LOG.warn("Unable to write THM channel logs", e);
            }
        }

        if (active == this && options == captured && captured.chat()) {
            if (!redact && decoded.equals("UTF-8 text") && utf8 != null) description = ChannelPayloadText.escape(utf8);
            String preview = "[" + direction + "] " + label + " (" + bytes.length + " bytes) " + description;
            chat.offer(new Preview(captured, preview.length() <= 512 ? preview : preview.substring(0, 511) + "…"));
        }
    }

    private static String asBinary(String text) {
        return new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }

    private static void appendHex(StringBuilder entry, byte[] bytes) {
        entry.append("\nRaw Hex:");
        if (bytes.length <= 16) {
            entry.append(' ').append(HEX.formatHex(bytes));
            return;
        }
        for (int offset = 0; offset < bytes.length; offset += 16) {
            entry.append('\n').append(HEX.toHexDigits(offset)).append("  ");
            HEX.formatHex(entry, bytes, offset, Math.min(offset + 16, bytes.length));
        }
    }

    private record Secrets(String token, String password) {}

    private record Preview(Options options, String text) {}

    record Options(Set<String> channels, boolean onlyCustomChannels, boolean hex, boolean redact, boolean chat) {
        boolean matches(String channel) {
            if (onlyCustomChannels && channel.startsWith("minecraft:")) return false;
            if (channels.isEmpty() || channels.contains("*") || channels.contains(channel)) return true;
            int separator = channel.indexOf(':');
            return separator >= 0 && channels.contains(channel.substring(0, separator) + ":*");
        }
    }

    private static final class Session {
        // Both codec hooks run on the connection's event loop.
        private final LinkedHashMap<Integer, String> queries = new LinkedHashMap<>();
        private final AtomicInteger dropped = new AtomicInteger();
        private Path file;
        private boolean reportedWriteError;
    }
}
