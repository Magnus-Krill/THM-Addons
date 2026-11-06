/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.server;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/** Server-only command snapshots and a bounded, correlated completion fallback. */
public final class ServerPluginScan {
    private static final long TIMEOUT_NS = 5_000_000_000L;
    private static final List<String> VERSION_ALIASES = List.of("bukkit:version", "bukkit:ver", "bukkit:about", "version", "ver", "about");
    private enum Stage { Idle, Version, Root }
    public record Request(int id, String command) {}

    private List<String> commands = List.of();
    private final TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private Stage stage = Stage.Idle;
    private int nextId = -1_000_000, pendingId;
    private long deadline;
    private boolean timedOut;

    public void setCommands(Collection<String> rawServerCommands) {
        commands = rawServerCommands.stream().filter(name -> !name.isBlank() && name.length() <= 256
            && name.chars().noneMatch(Character::isISOControl)).limit(16_384).distinct().sorted().toList();
        names.clear();
        names.addAll(ServerTelemetry.pluginHints(commands).stream().limit(512).toList());
    }

    public List<String> commands() { return commands; }
    public List<String> names() { return List.copyOf(names); }
    public boolean running() { return stage != Stage.Idle; }
    public boolean timedOut() { return timedOut; }

    public Request start(long now) {
        if (running()) return null;
        names.clear();
        names.addAll(ServerTelemetry.pluginHints(commands).stream().limit(512).toList());
        timedOut = false;
        String alias = VERSION_ALIASES.stream().filter(commands::contains).findFirst().orElse(null);
        stage = alias == null ? Stage.Root : Stage.Version;
        return request(alias == null ? "/" : alias + " ", now);
    }

    public Request accept(int id, Collection<String> suggestions, long now) {
        if (!running() || id != pendingId) return null;
        if (stage == Stage.Version) {
            int found = 0;
            for (String suggestion : suggestions.stream().limit(512).toList()) {
                String name = suggestion.trim();
                if (!name.matches("[A-Za-z0-9_.-]{1,128}")) continue;
                if (names.size() < 512) names.add(name);
                found++;
            }
            if (found == 0) { stage = Stage.Root; return request("/", now); }
        } else {
            var rootCommands = suggestions.stream().limit(16_384).map(String::trim)
                .map(command -> command.startsWith("/") ? command.substring(1) : command)
                .filter(command -> command.matches("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+")).toList();
            for (String name : ServerTelemetry.pluginHints(rootCommands)) if (names.size() < 512) names.add(name);
        }
        stage = Stage.Idle;
        return null;
    }

    public Request tick(long now) {
        if (!running() || now < deadline) return null;
        if (stage == Stage.Version) { stage = Stage.Root; return request("/", now); }
        timedOut = true;
        stage = Stage.Idle;
        return null;
    }

    private Request request(String command, long now) {
        if (nextId == Integer.MIN_VALUE) nextId = -1_000_000;
        pendingId = nextId--;
        deadline = now + TIMEOUT_NS;
        return new Request(pendingId, command);
    }

    public void reset() {
        commands = List.of();
        names.clear();
        stage = Stage.Idle;
        timedOut = false;
        deadline = 0;
    }
}
