package util;

import java.util.*;

/**
 * Utility: SimpleJson
 * คลาสช่วยแปลง Java Objects / Collections เป็น JSON String
 * และแยกวิเคราะห์ JSON แบบเบาโดยไม่ต้องพึ่งพาไลบรารีภายนอก (Pure Java SE)
 */
// [OOP: CLASS] คลาส: แม่แบบสำหรับสร้างออบเจ็กต์และรวมข้อมูลกับพฤติกรรมไว้ด้วยกัน
public class SimpleJson {

    // [OOP: METHOD] Method toJson() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return "\"" + escape((String) obj) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Enum<?>) {
            return "\"" + ((Enum<?>) obj).name() + "\"";
        }
        if (obj instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escape(String.valueOf(entry.getKey()))).append("\":");
                sb.append(toJson(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Iterable<?>) {
            Iterable<?> iter = (Iterable<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : iter) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(item));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escape(obj.toString()) + "\"";
    }

    // [OOP: METHOD] Method escape() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * ดึงค่า Key จาก JSON Object สตริงอย่างง่าย
     */
    // [OOP: METHOD] Method parseSimpleJson() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    public static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("{")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("}")) trimmed = trimmed.substring(0, trimmed.length() - 1);

        // Simple tokenizer for key-value pairs
        String[] pairs = trimmed.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            int colonIdx = pair.indexOf(':');
            if (colonIdx > 0) {
                String key = stripQuotes(pair.substring(0, colonIdx).trim());
                String val = stripQuotes(pair.substring(colonIdx + 1).trim());
                map.put(key, val);
            }
        }
        return map;
    }

    // [OOP: METHOD] Method stripQuotes() คือพฤติกรรม/การทำงานที่ object หรือ class นี้ให้บริการ
    private static String stripQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
