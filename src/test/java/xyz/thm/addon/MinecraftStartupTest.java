/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftStartupTest {
    @Test
    void reachesTheMainMenuAndClosesAfterOneSecond() throws Exception {
        Path launch = Path.of(System.getProperty("thm.startup.command"));
        Path run = Files.createTempDirectory(launch.getParent(), "run-");
        Path log = run.resolve("console.log");
        Files.writeString(run.resolve("options.txt"), "onboardAccessibility:false\npauseOnLostFocus:false\npreferredGraphicsBackend:\"opengl\"\n");
        Process client = new ProcessBuilder(Files.readAllLines(launch))
            .directory(run.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(client.waitFor(180, TimeUnit.SECONDS), "Minecraft startup timed out. Log: " + log);
            assertEquals(0, client.exitValue(), "Minecraft crashed. Log: " + log);
            Path success = run.resolve("startup-ok");
            assertTrue(Files.isRegularFile(success), "Minecraft exited before startup completed. Log: " + log);
            assertTrue(Long.parseLong(Files.readString(success)) >= 1_000_000_000L,
                "Minecraft must remain at the main menu for at least one second.");
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
}
