package util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class FileStorage {
    private FileStorage() { }

    public static List<String> readLines(Path path) {
        try {
            return Files.exists(path) ? Files.readAllLines(path, StandardCharsets.UTF_8) : new ArrayList<>();
        } catch (IOException ex) {
            throw new IllegalStateException("ไม่สามารถอ่านข้อมูลจาก " + path + " ได้", ex);
        }
    }

    public static void writeLines(Path path, List<String> lines) {
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(path, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("ไม่สามารถบันทึกข้อมูลไปที่ " + path + " ได้", ex);
        }
    }

    public static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
