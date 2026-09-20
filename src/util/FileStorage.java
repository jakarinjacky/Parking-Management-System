package util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public final class FileStorage {
    // [OOP: CONSTRUCTOR] Constructor สำหรับสร้างและกำหนดค่าเริ่มต้นให้ object FileStorage
    private FileStorage() { }

    // [OOP: METHOD] Method readLines() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public static List<String> readLines(Path path) {
        try {
            return Files.exists(path) ? Files.readAllLines(path, StandardCharsets.UTF_8) : new ArrayList<>();
        } catch (IOException ex) {
            throw new IllegalStateException("ไม่สามารถอ่านข้อมูลจาก " + path + " ได้", ex);
        }
    }

    // [OOP: METHOD] Method writeLines() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
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

    // [OOP: METHOD] Method encode() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    // [OOP: METHOD] Method decode() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
