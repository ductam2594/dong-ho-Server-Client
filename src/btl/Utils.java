package btl;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Helper:
 * - getCurrentTime(): HH:mm:ss
 * - getCurrentDateTime(): yyyy-MM-dd HH:mm:ss
 * - formatDuration(ms): HH:mm:ss
 * - formatDurationShort(ms): mm:ss (or hh:mm:ss if > 1 hour)
 */
public class Utils {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String getCurrentTime() {
        return TIME_FORMATTER.format(LocalTime.now());
    }

    public static String getCurrentDateTime() {
        return DATE_TIME_FORMATTER.format(LocalDateTime.now());
    }

    public static String formatDuration(long ms) {
        if (ms < 0) throw new IllegalArgumentException("Duration must be non-negative");
        long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Short format used for countdown displays (mm:ss if < 1h, otherwise hh:mm:ss)
     */
    public static String formatDurationShort(long ms) {
        if (ms < 0) throw new IllegalArgumentException("Duration must be non-negative");
        long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}