package ui;

import simulator.enums.ServerProcess;
import simulator.server.transactionManager.Transaction;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class Log {

    private static boolean LOGGING_ENABLED = false;
    private final ServerProcess process;
    private final int serverID;
    private final Supplier<Integer> timeProvider;
    private final Consumer<String> log;
    private final String serverProcess;

    public Log(ServerProcess process, int serverID, Supplier<Integer> timeProvider, Consumer<String> log) {
        this.process = process;
        this.serverID = serverID;
        this.timeProvider = timeProvider;
        this.log = log;
        serverProcess = ":" + serverID + ":" + process.toString() + ":";
    }

    public void log(String message) {
        log.accept(timeProvider.get() + serverProcess + ": " + message);
    }

    public void log(Transaction t, String message) {
        log.accept(timeProvider.get() + serverProcess + t.getID() + ": " + message);
    }

    public void log(int transID, String message) {
        log.accept(timeProvider.get() + serverProcess + transID + ": " + message.replace(":", "-"));
    }

    public static boolean isLoggingEnabled() {
        return LOGGING_ENABLED;
    }

    public static void setLoggingEnabled(boolean b) {
        LOGGING_ENABLED = b;
    }
}