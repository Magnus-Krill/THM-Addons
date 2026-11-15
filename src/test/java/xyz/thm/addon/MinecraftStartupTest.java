/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon;

import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftStartupTest {
    @ParameterizedTest(name = "Patchy present: {0}")
    @ValueSource(booleans = {true, false})
    void reachesTheMainMenuAndClosesAfterOneSecond(boolean withPatchy) throws Exception {
        Path launch = Path.of(System.getProperty("thm.startup.command"));
        Path run = Files.createTempDirectory(launch.getParent(), "run-");
        Path log = run.resolve("console.log");
        Files.writeString(run.resolve("options.txt"), "onboardAccessibility:false\npauseOnLostFocus:false\npreferredGraphicsBackend:\"opengl\"\n");
        var command = new ArrayList<>(Files.readAllLines(launch));
        boolean testBridge = withPatchy && File.separatorChar == '/';
        if (testBridge) command.add(1, "-Dthm.localClientBridge=true");
        if (!withPatchy) {
            int classpathIndex = command.indexOf("-cp") + 1;
            String classpath = Arrays.stream(command.get(classpathIndex).split(Pattern.quote(File.pathSeparator)))
                .filter(entry -> !Path.of(entry).getFileName().toString().startsWith("patchy-"))
                .collect(Collectors.joining(File.pathSeparator));
            command.set(classpathIndex, classpath);
        }
        Process client = new ProcessBuilder(command)
            .directory(run.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            Path socket = testBridge ? checkLocalBridge(client, log) : null;
            assertTrue(client.waitFor(180, TimeUnit.SECONDS), "Minecraft startup timed out. Log: " + log);
            assertEquals(0, client.exitValue(), "Minecraft crashed. Log: " + log);
            Path success = run.resolve("startup-ok");
            assertTrue(Files.isRegularFile(success), "Minecraft exited before startup completed. Log: " + log);
            assertTrue(Long.parseLong(Files.readString(success)) >= 1_000_000_000L,
                "Minecraft must remain at the main menu for at least one second.");
            String decision = withPatchy ? "Enabled BlockedServersMixin"
                : "Skipped BlockedServersMixin because com.mojang.patchy.BlockedServers is missing";
            assertTrue(Files.readString(log).contains(decision), "Missing mixin decision: " + decision + ". Log: " + log);
            if (socket != null) assertFalse(Files.exists(socket), "Client must remove its diagnostic socket on shutdown.");
        } finally {
            if (client.isAlive()) {
                client.destroy();
                if (!client.waitFor(5, TimeUnit.SECONDS)) {
                    client.destroyForcibly();
                    assertTrue(client.waitFor(5, TimeUnit.SECONDS), "Could not stop the test client: " + client.pid());
                }
            }
        }
    }

    private static Path checkLocalBridge(Process client, Path log) throws Exception {
        var check = new FutureTask<>(() -> {
            var announcement = Pattern.compile("Local client bridge listening at (.+)");
            while (client.isAlive()) {
                if (Files.isRegularFile(log)) {
                    var match = announcement.matcher(Files.readString(log));
                    if (match.find()) {
                        Path socket = Path.of(match.group(1).strip());
                        try (var channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                            channel.connect(UnixDomainSocketAddress.of(socket));
                            var request = ByteBuffer.wrap("{\"method\":\"status\"}\n".getBytes(StandardCharsets.UTF_8));
                            while (request.hasRemaining()) channel.write(request);
                            var response = JsonParser.parseString(
                                new String(java.nio.channels.Channels.newInputStream(channel).readAllBytes(), StandardCharsets.UTF_8)
                            ).getAsJsonObject().getAsJsonObject("result");
                            assertNotNull(response, "The local bridge must return a status result.");
                            assertFalse(response.get("inWorld").getAsBoolean());
                            assertFalse(response.getAsJsonObject("highwayBuilder").get("active").getAsBoolean());
                        }
                        return socket;
                    }
                }
                Thread.sleep(50);
            }
            throw new AssertionError("Client exited before the bridge answered. Log: " + log);
        });
        Thread.ofVirtual().start(check);
        try {
            return check.get(180, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            throw new AssertionError("Could not query the local bridge. Log: " + log, failure.getCause());
        } finally {
            check.cancel(true);
        }
    }
}
