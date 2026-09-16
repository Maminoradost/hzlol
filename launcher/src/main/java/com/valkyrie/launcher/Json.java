package com.valkyrie.launcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        Json parser = new Json(text);
        Object value = parser.value();
        parser.space();
        if (parser.at != text.length()) throw parser.error("Unexpected trailing data");
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object value) {
        return (List<Object>) value;
    }

    private Object value() {
        space();
        if (at >= text.length()) throw error("Unexpected end of JSON");
        return switch (text.charAt(at)) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        Map<String, Object> result = new LinkedHashMap<>();
        at++;
        space();
        if (take('}')) return result;
        do {
            space();
            String key = string();
            space();
            if (!take(':')) throw error("Expected ':'");
            result.put(key, value());
            space();
        } while (take(','));
        if (!take('}')) throw error("Expected '}'");
        return result;
    }

    private List<Object> array() {
        List<Object> result = new ArrayList<>();
        at++;
        space();
        if (take(']')) return result;
        do {
            result.add(value());
            space();
        } while (take(','));
        if (!take(']')) throw error("Expected ']'");
        return result;
    }

    private String string() {
        if (!take('"')) throw error("Expected string");
        StringBuilder result = new StringBuilder();
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') return result.toString();
            if (c != '\\') {
                result.append(c);
                continue;
            }
            if (at >= text.length()) throw error("Broken escape");
            char escaped = text.charAt(at++);
            result.append(switch (escaped) {
                case '"', '\\', '/' -> escaped;
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> unicode();
                default -> throw error("Unknown escape");
            });
        }
        throw error("Unclosed string");
    }

    private char unicode() {
        if (at + 4 > text.length()) throw error("Broken unicode escape");
        char result = (char) Integer.parseInt(text.substring(at, at + 4), 16);
        at += 4;
        return result;
    }

    private Object number() {
        int start = at;
        while (at < text.length() && "-+0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
        if (start == at) throw error("Expected value");
        String number = text.substring(start, at);
        return number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0
            ? Double.parseDouble(number) : Long.parseLong(number);
    }

    private Object literal(String expected, Object value) {
        if (!text.startsWith(expected, at)) throw error("Expected " + expected);
        at += expected.length();
        return value;
    }

    private void space() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
    }

    private boolean take(char expected) {
        if (at < text.length() && text.charAt(at) == expected) {
            at++;
            return true;
        }
        return false;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at " + at);
    }
}
