package com.ikalagaming.graphics.ui.script;

import com.ikalagaming.launcher.events.Shutdown;
import com.ikalagaming.scripting.ScriptManager;
import com.ikalagaming.scripting.interpreter.ScriptRuntime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ends a run once its startup script does, for checks and captures run from a shell: with {@value
 * #EXIT_PROPERTY} set, the app shuts down when the script finishes or a UI check in it fails, and
 * the process exits with 1 if one failed, else 0. Without it, the app stays open for a person.
 */
@Slf4j
public final class StartupScripts {
    /** The startup property that ends the run with its startup script. */
    public static final String EXIT_PROPERTY = "ikala.test.exitAfterScript";

    /** How often the script is checked on, in milliseconds. */
    private static final long POLL_MILLIS = 200;

    /** How many checks in a row must find it gone, as it may not be listed the moment it starts. */
    private static final int GONE_CHECKS = 3;

    /** The exit code for a script whose UI check failed. */
    private static final int FAILED = 1;

    /** The owners of scripts a UI check failed in. */
    private static final Set<String> FAILURES = ConcurrentHashMap.newKeySet();

    /** Not made. */
    private StartupScripts() {}

    /**
     * Note that a UI check failed in a script, so ending the run reports it.
     *
     * @param owner The script's owner.
     */
    static void failed(@NonNull String owner) {
        FAILURES.add(owner);
    }

    /**
     * Whether runs end with their startup scripts.
     *
     * @return True if {@value #EXIT_PROPERTY} is set.
     */
    public static boolean exitAfterScript() {
        return Boolean.getBoolean(EXIT_PROPERTY);
    }

    /**
     * End the run because its startup script couldn't start, if {@value #EXIT_PROPERTY} is set,
     * exiting with 1, rather than leave it open with nothing to do.
     */
    public static void couldNotStart() {
        if (exitAfterScript()) {
            log.warn("The startup script couldn't start; shutting down");
            final Thread later = new Thread(() -> end(FAILED), "StartupScriptExit");
            later.setDaemon(true);
            later.start();
        }
    }

    /**
     * Shut the app down once a startup script ends, if {@value #EXIT_PROPERTY} is set, exiting with
     * 1 if a UI check in it failed.
     *
     * @param runtime The script.
     * @param owner Its owner.
     */
    public static void exitWhenDone(@NonNull ScriptRuntime runtime, @NonNull String owner) {
        if (!exitAfterScript()) {
            return;
        }
        final Thread watcher =
                new Thread(
                        () -> {
                            int gone = 0;
                            while (gone < GONE_CHECKS) {
                                try {
                                    Thread.sleep(POLL_MILLIS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                                final boolean alive =
                                        ScriptManager.getRunningScripts().contains(runtime)
                                                || ScriptManager.getYieldedScripts()
                                                        .containsKey(runtime);
                                gone = alive ? 0 : gone + 1;
                            }
                            end(FAILURES.contains(owner) ? FAILED : 0);
                        },
                        "StartupScriptExit");
        watcher.setDaemon(true);
        watcher.start();
    }

    /**
     * Shut down, then exit with a code once the main thread is done.
     *
     * @param code The exit code.
     */
    private static void end(int code) {
        log.info("Startup script ended{}; shutting down", code == 0 ? "" : ", a UI check failed");
        Thread main = null;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if ("main".equals(thread.getName())) {
                main = thread;
            }
        }
        new Shutdown().fire();
        final Thread mainThread = main;
        // Not a daemon, so the process waits for it, and so exits with its code
        final Thread exit =
                new Thread(
                        () -> {
                            try {
                                if (mainThread != null) {
                                    mainThread.join();
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            System.exit(code);
                        },
                        "StartupScriptExitCode");
        exit.start();
    }
}
