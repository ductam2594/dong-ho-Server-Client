package btl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Ghi / đọc log đơn giản.
 */
public class FileUtils {
    public static synchronized boolean appendLog(String filename, String text) {
        try {
            Path path = Paths.get(filename);
            Files.write(path, (text + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            System.err.println("FileUtils.appendLog error: " + e.getMessage());
            return false;
        }
    }

    public static String readAll(String filename) throws IOException {
        Path path = Paths.get(filename);
        if (!Files.exists(path)) return "";
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}