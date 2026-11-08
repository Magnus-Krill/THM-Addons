/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.accessor;

import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.resources.Identifier;

public interface ServerFolderView {
    String thm$folder();
    String thm$serverQuery();
    ServerSelectionList thm$serverList();

    interface EntryIcon {
        Identifier thm$iconLocation();
    }
}
