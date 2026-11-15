/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.kitbot;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import meteordevelopment.orbit.EventHandler;
import xyz.thm.addon.modules.KitbotFrontend;

public final class KitbotChatRouter {
    private static KitbotChatRouter INSTANCE;
    private static boolean enabled = true;

    private KitbotChatRouter() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public static KitbotChatRouter getInstance() {
        if (INSTANCE == null) INSTANCE = new KitbotChatRouter();
        return INSTANCE;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (!enabled) return;

        if (event.message == null) return;
        String message = event.message.trim();
        if (!message.startsWith("$")) return;

        String loopTarget = KitbotChatCommandParser.loopUpdateTarget(message);
        if (loopTarget != null) {
            event.cancel();
            HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
            if (builder == null || !builder.canRequestLoopUpdate(loopTarget)) {
                ChatUtils.warning("Start HighwayBuilder on the requested official Nether ring road or diamond.");
                return;
            }
            ChatUtils.sendPlayerMsg("/msg " + KitbotFrontend.KITBOT_NAME + " $update " + loopTarget);
            return;
        }

        KitbotChatCommandParser.ParseResult result = KitbotChatCommandParser.parse(message, KitbotChatCommandParser.getOnlinePlayerNames());
        if (!result.isRecognized()) return;

        event.cancel();

        KitbotFrontend frontend = Modules.get().get(KitbotFrontend.class);
        if (frontend == null) return;

        if (!result.isValid()) {
            frontend.warning(result.errorMessage());
            return;
        }

        if (result.request().type() == KitbotChatCommandParser.CommandType.Update && result.request().direction() == null) {
            HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
            if (builder != null && builder.isActive() && !builder.canRequestDetectedUpdate()) {
                frontend.warning("Update on a main highway or an official Nether ring road or diamond.");
                return;
            }
        }

        if (!KitbotFrontend.submitChatRequest(result.request())) {
            frontend.warning("KitBot request window is already active.");
            return;
        }
    }
}
