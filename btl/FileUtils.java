package btl;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple append-only logger
 */
public class FileUtils {
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static synchronized void append(String filename, String message) {
        String line = "[" + LocalDateTime.now().format(DF) + "] " + message;
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new FileWriter(filename, true));
            pw.println(line);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (pw != null) pw.close();
        }
    }
}
