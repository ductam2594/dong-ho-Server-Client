package btl;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Utils {
    public static String getCurrentTime() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }
}
