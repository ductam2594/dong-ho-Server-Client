package btl;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class Utils {
    private static final SimpleDateFormat FULL = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
    private static final SimpleDateFormat TIME_ONLY = new SimpleDateFormat("HH:mm:ss");
    private static final DateTimeFormatter FULL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    public static String formatNowFull() {
        return FULL.format(new Date(System.currentTimeMillis()));
    }

    public static String formatNowTimeOnly() {
        return TIME_ONLY.format(new Date(System.currentTimeMillis()));
    }

    public static String formatFull(long millis) {
        return FULL.format(new Date(millis));
    }

    public static String formatTimeOnly(long millis) {
        return TIME_ONLY.format(new Date(millis));
    }

    /**
     * Get the current time formatted for a specific time zone.
     * @param timeZoneId The ID of the time zone (e.g., "Asia/Ho_Chi_Minh", "America/New_York").
     * @return The formatted time string.
     */
    public static String formatNowForTimeZone(String timeZoneId) {
        try {
            ZonedDateTime zdt = ZonedDateTime.now(ZoneId.of(timeZoneId));
            return zdt.format(FULL_FORMATTER);
        } catch (Exception e) {
            return "Lỗi múi giờ";
        }
    }
}