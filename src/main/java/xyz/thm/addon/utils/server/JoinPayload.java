/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.utils.server;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;

public final class JoinPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("anarchymod", "join");

    private JoinPayload() {}

    public static ServerboundCustomPayloadPacket createPacket() {
        return new ServerboundCustomPayloadPacket(new DiscardedPayload(ID));
    }
}
