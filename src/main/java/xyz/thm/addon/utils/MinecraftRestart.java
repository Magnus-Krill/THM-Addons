/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class MinecraftRestart {
    private static final String CLASS_FILE = "xyz/thm/addon/utils/MinecraftRestart.class";
    private static final String KNOT_CLIENT = "net.fabricmc.loader.impl.launch.knot.KnotClient";

    private MinecraftRestart() {}

    static List<String> launcherCommand() {
        if (!"prismlauncher".equalsIgnoreCase(System.getProperty("minecraft.launcher.brand"))) return List.of();
        String id = System.getenv("INST_ID");
        String instanceDirectory = System.getenv("INST_DIR");
        for (var parent = ProcessHandle.current().parent(); parent.isPresent(); parent = parent.get().parent()) {
            List<String> command = prismCommand(id, instanceDirectory, parent.get().info().command().map(Path::of).orElse(null));
            if (!command.isEmpty()) return command;
        }
        return List.of();
    }

    static List<String> prismCommand(String instanceId, String instanceDirectory, Path executable) {
        if (instanceId == null || instanceId.isBlank() || instanceDirectory == null || executable == null) return List.of();
        String name = executable.getFileName().toString().toLowerCase(Locale.ROOT);
        if ((!name.equals("prismlauncher") && !name.equals("prismlauncher.exe")) || !Files.isExecutable(executable)) return List.of();

        Path instance = Path.of(instanceDirectory).toAbsolutePath().normalize();
        Path instances = instance.getParent();
        if (instances == null || !instance.getFileName().toString().equals(instanceId)
            || !Files.isRegularFile(instance.resolve("instance.cfg"))) return List.of();

        Path root = instances.getParent();
        if (root == null || !Files.isRegularFile(root.resolve("prismlauncher.cfg"))) return List.of();
        return List.of(executable.toString(), "--dir", root.toString(), "--launch=" + instanceId);
    }

    static byte[] helperBytecode() throws IOException {
        try (var in = MinecraftRestart.class.getResourceAsStream("MinecraftRestart.class")) {
            if (in == null) throw new IOException("Restart helper is missing");
            return in.readAllBytes();
        }
    }

    static void schedule(List<String> launcher, Path gameDirectory, byte[] helper, String[] gameArguments,
                         Path pendingJar, Path installedJar) throws IOException {
        Path directory = Files.createTempDirectory("thm-restart-");
        Path classFile = directory.resolve(CLASS_FILE);
        Process waiter = null;
        try {
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, helper);
            Path log = gameDirectory.resolve("logs/thm-restart.log");
            Files.createDirectories(log.getParent());

            String java = javaExecutable();
            List<String> command = new ArrayList<>(List.of(java, "-cp", directory.toString(),
                MinecraftRestart.class.getName(), "wait", directory.toString(), Long.toString(ProcessHandle.current().pid()),
                pendingJar == null ? "" : pendingJar.toString(), installedJar == null ? "" : installedJar.toString()));
            if (launcher.isEmpty()) {
                Path options = directory.resolve("java.args");
                List<String> jvmOptions = new ArrayList<>(ManagementFactory.getRuntimeMXBean().getInputArguments());
                jvmOptions.removeIf(option -> option.startsWith("-Djava.class.path="));
                String classpath = System.getProperty("java.class.path");
                jvmOptions.addAll(List.of("-cp", directory + File.pathSeparator + classpath,
                    MinecraftRestart.class.getName(), "fabric", directory.toString()));
                Files.write(options, jvmOptions.stream().map(MinecraftRestart::quoteJavaArgument).toList(),
                    Charset.forName(System.getProperty("native.encoding")));
                command.add("fabric");
                command.addAll(List.of(java, "@" + options));
            } else {
                command.add("launcher");
                command.addAll(launcher);
            }

            ProcessBuilder process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
            // The JVM input arguments already contain flags supplied through these variables.
            process.environment().remove("JDK_JAVA_OPTIONS");
            process.environment().remove("JAVA_TOOL_OPTIONS");
            process.environment().remove("_JAVA_OPTIONS");
            waiter = process.start();
            writeGameArguments(waiter, launcher.isEmpty() ? prepareGameArguments(gameArguments) : new String[0]);
        } catch (IOException | RuntimeException e) {
            if (waiter != null) waiter.destroyForcibly();
            try {
                deleteHelper(directory);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    static String javaExecutable() {
        String name = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", name);
        return Files.isExecutable(java) ? java.toString() : ProcessHandle.current().info().command().orElseThrow();
    }

    static String quoteJavaArgument(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t").replace("\f", "\\f") + "\"";
    }

    // Keep Minecraft's session credentials in pipes.
    static void writeGameArguments(Process process, String[] arguments) throws IOException {
        try (var out = new DataOutputStream(process.getOutputStream())) {
            out.writeInt(arguments.length);
            for (String argument : arguments) {
                byte[] bytes = argument.getBytes(StandardCharsets.UTF_8);
                out.writeInt(bytes.length);
                out.write(bytes);
            }
        }
    }

    static String[] prepareGameArguments(String[] arguments) {
        var result = new ArrayList<>(List.of(arguments));
        int versionType = result.indexOf("--versionType");
        if (versionType >= 0 && versionType + 1 < result.size()) {
            String version = result.get(versionType + 1);
            // Fabric adds this suffix again when it starts.
            if (version.equals("Fabric")) result.set(versionType + 1, "release");
            else if (version.endsWith("/Fabric")) result.set(versionType + 1, version.substring(0, version.length() - 7));
        }
        return result.toArray(String[]::new);
    }

    private static String[] readGameArguments() throws IOException {
        try (var in = new DataInputStream(System.in)) {
            String[] arguments = new String[in.readInt()];
            for (int i = 0; i < arguments.length; i++) {
                byte[] bytes = new byte[in.readInt()];
                in.readFully(bytes);
                arguments[i] = new String(bytes, StandardCharsets.UTF_8);
            }
            return arguments;
        }
    }

    // Runs outside the old game so the mod JAR can be replaced and loaded again.
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[1]);
        if (args[0].equals("fabric")) {
            String[] gameArguments;
            try {
                gameArguments = readGameArguments();
                String classpath = System.getProperty("java.class.path");
                System.setProperty("java.class.path", classpath.substring(directory.toString().length() + 1));
            } finally {
                deleteHelper(directory);
            }
            Class.forName(KNOT_CLIENT).getMethod("main", String[].class).invoke(null, (Object) gameArguments);
            return;
        }

        boolean handedOff = false;
        try {
            String[] gameArguments = readGameArguments();
            var game = ProcessHandle.of(Long.parseLong(args[2]));
            if (game.isPresent()) game.get().onExit().get(3, TimeUnit.MINUTES);
            if (!args[3].isEmpty()) installPendingJar(Path.of(args[3]), Path.of(args[4]));

            List<String> command = List.of(args).subList(6, args.length);
            if (args[5].equals("launcher")) {
                // Give Prism time to clear its running-instance state.
                Thread.sleep(1500);
                new ProcessBuilder(command).inheritIO().start();
            } else {
                Process client = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start();
                try {
                    writeGameArguments(client, gameArguments);
                    handedOff = true;
                } catch (IOException e) {
                    client.destroyForcibly();
                    throw e;
                }
            }
        } finally {
            if (!handedOff) deleteHelper(directory);
        }
    }

    private static void installPendingJar(Path pending, Path installed) {
        try {
            Files.move(pending, installed, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Could not install the staged THM Addon update: " + e.getMessage());
            try {
                Files.deleteIfExists(pending);
            } catch (IOException cleanup) {
                System.err.println("Could not remove the staged update: " + cleanup.getMessage());
            }
        }
    }

    private static void deleteHelper(Path directory) throws IOException {
        Files.deleteIfExists(directory.resolve("java.args"));
        for (Path path = directory.resolve(CLASS_FILE); !path.equals(directory); path = path.getParent()) {
            Files.deleteIfExists(path);
        }
        Files.deleteIfExists(directory);
    }
}
