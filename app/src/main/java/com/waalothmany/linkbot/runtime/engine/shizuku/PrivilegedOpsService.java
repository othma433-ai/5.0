package com.waalothmany.linkbot.runtime.engine.shizuku;

import android.content.Context;
import android.system.Os;

import androidx.annotation.Keep;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Shizuku UserService. The service runs with the Shizuku server identity
 * (shell or root) and exposes only the narrowly-scoped operations required by
 * the app. It intentionally does not expose an arbitrary command interface.
 */
public final class PrivilegedOpsService extends IPrivilegedOps.Stub {
    private static final long COMMAND_TIMEOUT_MS = 4_000L;
    private static final int MAX_OUTPUT_CHARS = 512 * 1024;
    static final Pattern PACKAGE_PATTERN = Pattern.compile("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$");
    static final Pattern COMPONENT_PATTERN = Pattern.compile("^[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+$");

    public PrivilegedOpsService() {
    }

    @Keep
    public PrivilegedOpsService(Context ignored) {
        // Shizuku v13+ may prefer the Context constructor. We do not depend on
        // ContentResolver or other application-process-only APIs here.
    }

    @Override
    public int probeUid() {
        return Os.getuid();
    }

    @Override
    public String listUsers() {
        return runCommand(COMMAND_TIMEOUT_MS, "pm", "list", "users").output;
    }

    @Override
    public String listPackagesForUser(int userId) {
        requireUserId(userId);
        return runCommand(
                COMMAND_TIMEOUT_MS,
                "pm", "list", "packages", "--user", Integer.toString(userId)
        ).output;
    }

    @Override
    public int launchPackageForUser(int userId, String packageName) {
        requireUserId(userId);
        requirePackage(packageName);

        CommandResult resolve = runCommand(
                COMMAND_TIMEOUT_MS,
                "cmd", "package", "resolve-activity", "--brief", "--user",
                Integer.toString(userId), packageName
        );
        if (resolve.timedOut || resolve.exitCode != 0) return exitCode(resolve);

        String component = lastComponent(resolve.output);
        if (component == null) return 64;
        if (!COMPONENT_PATTERN.matcher(component).matches()) return 65;

        CommandResult start = runCommand(
                COMMAND_TIMEOUT_MS,
                "am", "start", "--user", Integer.toString(userId), "-n", component
        );
        return exitCode(start);
    }

    @Override
    public int forceStopPackageForUser(int userId, String packageName) {
        requireUserId(userId);
        requirePackage(packageName);
        return exitCode(runCommand(
                COMMAND_TIMEOUT_MS,
                "am", "force-stop", "--user", Integer.toString(userId), packageName
        ));
    }

    @Override
    public String currentUser() {
        return runCommand(COMMAND_TIMEOUT_MS, "am", "get-current-user").output.trim();
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    private static int exitCode(CommandResult result) {
        if (result.timedOut) return 124;
        return result.exitCode;
    }

    private static void requireUserId(int userId) {
        if (userId < 0 || userId > 9999) {
            throw new IllegalArgumentException("Invalid Android user id");
        }
    }

    private static void requirePackage(String packageName) {
        if (packageName == null || !PACKAGE_PATTERN.matcher(packageName).matches()) {
            throw new IllegalArgumentException("Invalid package name");
        }
    }

    private static String lastComponent(String output) {
        String[] lines = output.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.contains("/")) return line;
        }
        return null;
    }

    private static CommandResult runCommand(long timeoutMs, String... args) {
        List<String> command = new ArrayList<>(Arrays.asList(args));
        Process process = null;
        Thread readerThread = null;
        StringBuilder output = new StringBuilder();
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            final Process activeProcess = process;
            readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(activeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    char[] buffer = new char[2048];
                    int read;
                    while ((read = reader.read(buffer)) >= 0) {
                        synchronized (output) {
                            int remaining = MAX_OUTPUT_CHARS - output.length();
                            if (remaining <= 0) break;
                            output.append(buffer, 0, Math.min(read, remaining));
                        }
                    }
                } catch (Throwable ignored) {
                }
            }, "wa-privileged-output");
            readerThread.setDaemon(true);
            readerThread.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
            }
            if (readerThread != null) readerThread.join(300L);
            int exit = finished ? process.exitValue() : 124;
            String text;
            synchronized (output) {
                text = output.toString();
            }
            return new CommandResult(exit, text, !finished);
        } catch (Throwable t) {
            return new CommandResult(70, t.getClass().getSimpleName(), false);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (readerThread != null && readerThread.isAlive()) readerThread.interrupt();
        }
    }

    private static final class CommandResult {
        final int exitCode;
        final String output;
        final boolean timedOut;

        CommandResult(int exitCode, String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.timedOut = timedOut;
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "CommandResult(exit=%d, timeout=%s)", exitCode, timedOut);
        }
    }
}
