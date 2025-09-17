package btl;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Utils {
    private static final SimpleDateFormat FULL = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
    private static final SimpleDateFormat TIME_ONLY = new SimpleDateFormat("HH:mm:ss");

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
}
