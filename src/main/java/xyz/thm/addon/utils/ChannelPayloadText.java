/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.VarInt;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public final class ChannelPayloadText {
    private static final Pattern SENSITIVE_CHANNEL = Pattern.compile(
        "(?i)(?:^|[:/_.-])(?:auth|login|password|passwd|token|secret|session|credential|cookie)[a-z]*(?:$|[:/_.-])");
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
        "(?i)\\bbearer\\s+\\S|\\b(?:password|passwd|token|secret|credential|authorization|cookie)[\"']?\\s*[:=]");
    private static final Pattern NON_PRINTING = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]");

    private ChannelPayloadText() {}

    public static String decode(String channel, byte[] bytes) {
        if (bytes.length == 0) return "Empty payload";
        ByteBuf input = Unpooled.wrappedBuffer(bytes);
        try {
            switch (channel) {
                case "register", "REGISTER", "minecraft:register", "fabric:register",
                     "unregister", "UNREGISTER", "minecraft:unregister", "fabric:unregister" -> {
                    String channels = utf8(bytes, 0, bytes.length);
                    if (channels != null) return registrationLabel(channel) + channels.replace("\0", ", ");
                }
                case "c:register" -> {
                    int version = VarInt.read(input);
                    String protocol = readString(input, bytes);
                    int count = readCount(input);
                    StringJoiner channels = new StringJoiner(", ");
                    for (int i = 0; i < count; i++) channels.add(readString(input, bytes));
                    if (!input.isReadable()) return "REGISTER (v" + version + ", " + protocol + "): " + channels;
                }
                case "c:version" -> {
                    int count = readCount(input);
                    StringJoiner versions = new StringJoiner(", ");
                    for (int i = 0; i < count; i++) versions.add(Integer.toString(VarInt.read(input)));
                    if (!input.isReadable()) return "Protocol versions: " + versions;
                }
                default -> {
                    String text = readString(input, bytes);
                    if (!input.isReadable()) return channel.equals("minecraft:brand") ? text : "String: " + text;
                }
            }
        } catch (RuntimeException ignored) {
            // A payload without a matching schema falls through to generic decoding.
        } finally {
            input.release();
        }

        if (bytes[0] == 10 || bytes[0] == 9) {
            String nbt = readNbt(bytes, false);
            if (nbt == null) nbt = readNbt(bytes, true);
            if (nbt != null) return "NBT: " + nbt;
        }
        if (bytes.length <= VarInt.MAX_VARINT_SIZE) {
            ByteBuf number = Unpooled.wrappedBuffer(bytes);
            try {
                int value = VarInt.read(number);
                if (!number.isReadable() && VarInt.getByteSize(value) == bytes.length) return "Possible VarInt: " + value;
            } catch (RuntimeException ignored) {
            } finally {
                number.release();
            }
        }
        return utf8(bytes, 0, bytes.length) == null ? "Unknown binary payload" : "UTF-8 text";
    }

    private static int readCount(ByteBuf input) {
        int count = VarInt.read(input);
        if (count < 0 || count > Math.min(input.readableBytes(), 16_384)) throw new IllegalArgumentException("Invalid entry count");
        return count;
    }

    private static String readString(ByteBuf input, byte[] bytes) {
        int size = VarInt.read(input);
        if (size < 0 || size > input.readableBytes()) throw new IllegalArgumentException("Invalid string length");
        String text = utf8(bytes, input.readerIndex(), size);
        if (text == null) throw new IllegalArgumentException("Invalid UTF-8");
        input.skipBytes(size);
        return text;
    }

    private static String registrationLabel(String channel) {
        return channel.toLowerCase(Locale.ROOT).endsWith("unregister") ? "UNREGISTER: " : "REGISTER: ";
    }

    private static String readNbt(byte[] bytes, boolean named) {
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            var limit = NbtAccounter.create(2 * 1024 * 1024);
            var tag = named ? NbtIo.readUnnamedTag(input, limit) : NbtIo.readAnyTag(input, limit);
            return input.available() == 0 ? tag.toString() : null;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    public static String utf8(byte[] bytes, int offset, int size) {
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes, offset, size)).toString();
        } catch (CharacterCodingException ignored) {
            return null;
        }
    }

    public static boolean sensitiveChannel(String channel) {
        return SENSITIVE_CHANNEL.matcher(channel).find();
    }

    public static boolean sensitiveContent(String text, String token, String password) {
        return containsSecret(text, token) || containsSecret(text, password) || SENSITIVE_FIELD.matcher(text).find();
    }

    public static boolean containsSecret(String text, String secret) {
        return !secret.isEmpty() && text.contains(secret);
    }

    public static String escape(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
        return NON_PRINTING.matcher(escaped).replaceAll("�");
    }
}
