package org.acme;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper to provide a single logger with a consistent format that helps
 * identify different endpoints in the messages
 *
 */
public class Log {
    private final static Logger log = Logger.getLogger("org.gameontext.sample");

    private static final String log_format = ": %-8x : %s";
    private static final boolean NO_LOG_LEVEL_PROMOTION = Boolean.valueOf(System.getenv("NO_LOG_LEVEL_PROMOTION"));

    public static void log(Level level, Object source, String message, Object... args) {
        if (log.isLoggable(level)) {
            String msg = String.format(log_format, getHash(source), message);
            log.log(useLevel(level), msg, args);
        }
    }

    public static void log(Level level, Object source, String message, Throwable thrown) {
        if (log.isLoggable(level)) {
            String msg = String.format(log_format, getHash(source), message);
            log.log(useLevel(level), msg, thrown);
        }
    }

    public static String getHexHash(Object source) {
        return Integer.toHexString(getHash(source));
    }

    public static int getHash(Object source) {
        return source == null ? 0 : System.identityHashCode(source);
    }

    /**
     * This bumps enabled trace up to INFO level, so it appears in messages.log
     * @param level Original level
     * @return Original Level or INFO level, whichever is greater
     */
    private static Level useLevel(Level level) {
        // The "not not" here isn't great, but it makes the environment variable
        // much easier to read if the default behaviour is to have this level
        // promotion switched on
        if ( !NO_LOG_LEVEL_PROMOTION && level.intValue() < Level.INFO.intValue() ) {
            return Level.INFO;
        }
        return level;
    }
}
