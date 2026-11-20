/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







// BepHax 0.5.5 theme by Dekto, adapted for Meteor 26.2.
// https://bep.dek.to/changelog 
//To Dekto, if you want this removed then open a issue and ill remove it

package xyz.thm.addon.gui.themes.bephax.widgets;

import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.widgets.WAccount;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.utils.render.color.Color;

import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepAccount extends WAccount implements BepWidget {
    public WBepAccount(WidgetScreen screen, Account<?> account) {
        super(screen, account);
    }

    protected Color loggedInColor() {
        return this.theme().loggedInColor.get();
    }

    protected Color accountTypeColor() {
        return this.theme().textSecondaryColor.get();
    }
}
