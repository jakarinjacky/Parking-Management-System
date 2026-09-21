package platform;

import java.util.*;

/** Strict nested JSON reader for the platform API; serialization uses the existing SimpleJson. */
public final class Json {
    private final String text;
    private int position;
    private Json(String text) { this.text = text; }
    public static Object parse(String text) {
        Json parser = new Json(text);
        Object value = parser.value(0);
        parser.space();
        if (parser.position != text.length()) throw new IllegalArgumentException("Invalid JSON suffix");
        return value;
    }
    private void space() { while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++; }
    private char next() {
        if (position == text.length()) throw new IllegalArgumentException("Incomplete JSON");
        return text.charAt(position++);
    }
    private Object value(int depth) {
        if (depth > 32) throw new IllegalArgumentException("JSON too deep");
        space(); char c = next();
        if (c == '"') return string();
        if (c == '{') {
            Map<String,Object> result = new LinkedHashMap<>(); space();
            if (position < text.length() && text.charAt(position) == '}') { position++; return result; }
            while (true) {
                space(); if (next() != '"') throw new IllegalArgumentException("Expected key");
                String key = string(); space(); if (next() != ':') throw new IllegalArgumentException("Expected colon");
                if (result.containsKey(key)) throw new IllegalArgumentException("Duplicate JSON key");
                result.put(key, value(depth+1)); space(); c = next();
                if (c == '}') return result;
                if (c != ',') throw new IllegalArgumentException("Expected comma");
            }
        }
        if (c == '[') {
            List<Object> result = new ArrayList<>(); space();
            if (position < text.length() && text.charAt(position) == ']') { position++; return result; }
            while (true) {
                result.add(value(depth+1)); space(); c = next();
                if (c == ']') return result;
                if (c != ',') throw new IllegalArgumentException("Expected comma");
            }
        }
        int start = position-1;
        while (position < text.length() && ",]} \r\n\t".indexOf(text.charAt(position)) < 0) position++;
        String token = text.substring(start, position);
        return switch (token) {
            case "true" -> true; case "false" -> false; case "null" -> null;
            default -> {
                if (!token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw new IllegalArgumentException("Invalid JSON value");
                double number = Double.parseDouble(token);
                if (!Double.isFinite(number)) throw new IllegalArgumentException("Nonfinite number");
                yield number;
            }
        };
    }
    private String string() {
        StringBuilder result = new StringBuilder();
        while (true) {
            char c = next(); if (c == '"') return result.toString();
            if (c < 32) throw new IllegalArgumentException("Control character");
            if (c == '\\') {
                c = next();
                switch (c) {
                    case '"', '\\', '/' -> result.append(c);
                    case 'n' -> result.append('\n'); case 'r' -> result.append('\r');
                    case 't' -> result.append('\t'); case 'b' -> result.append('\b'); case 'f' -> result.append('\f');
                    case 'u' -> { if (position+4 > text.length()) throw new IllegalArgumentException("Unicode escape");
                        result.append((char)Integer.parseInt(text.substring(position,position+4),16)); position+=4; }
                    default -> throw new IllegalArgumentException("Invalid escape");
                }
            } else result.append(c);
        }
    }
}
