/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.interfaces;

import java.util.List;

/** Access to Meteor's LogoutSpots entry list, implemented by LogoutSpotsMixin. */
public interface LogoutSpotsPlayers {
    List<?> thm$getPlayers();
}
