package cn.wpush.sdk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON encode/decode for WPUSH envelopes (no external deps). */
final class Json {
  private Json() {}

  static String stringify(Object value) {
    StringBuilder sb = new StringBuilder();
    write(sb, value);
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  private static void write(StringBuilder sb, Object value) {
    if (value == null) {
      sb.append("null");
    } else if (value instanceof String) {
      writeString(sb, (String) value);
    } else if (value instanceof Number) {
      sb.append(value.toString());
    } else if (value instanceof Boolean) {
      sb.append(((Boolean) value) ? "true" : "false");
    } else if (value instanceof Map) {
      sb.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
        if (!first) sb.append(',');
        first = false;
        writeString(sb, String.valueOf(e.getKey()));
        sb.append(':');
        write(sb, e.getValue());
      }
      sb.append('}');
    } else if (value instanceof Iterable) {
      sb.append('[');
      boolean first = true;
      for (Object o : (Iterable<?>) value) {
        if (!first) sb.append(',');
        first = false;
        write(sb, o);
      }
      sb.append(']');
    } else {
      writeString(sb, String.valueOf(value));
    }
  }

  private static void writeString(StringBuilder sb, String s) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"': sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        case '\b': sb.append("\\b"); break;
        case '\f': sb.append("\\f"); break;
        case '\n': sb.append("\\n"); break;
        case '\r': sb.append("\\r"); break;
        case '\t': sb.append("\\t"); break;
        default:
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    sb.append('"');
  }

  static Object parse(String text) {
    return new Parser(text).parseValue();
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> parseObject(String text) {
    Object v = parse(text);
    if (!(v instanceof Map)) {
      throw new IllegalArgumentException("expected JSON object");
    }
    return (Map<String, Object>) v;
  }

  private static final class Parser {
    private final String s;
    private int i;

    Parser(String s) {
      this.s = s == null ? "" : s.trim();
    }

    Object parseValue() {
      skipWs();
      if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
      char c = s.charAt(i);
      if (c == '{') return parseObj();
      if (c == '[') return parseArr();
      if (c == '"') return parseStr();
      if (c == 't') return parseLit("true", Boolean.TRUE);
      if (c == 'f') return parseLit("false", Boolean.FALSE);
      if (c == 'n') return parseLit("null", null);
      return parseNum();
    }

    private Map<String, Object> parseObj() {
      Map<String, Object> m = new LinkedHashMap<>();
      i++; // {
      skipWs();
      if (peek('}')) { i++; return m; }
      while (true) {
        skipWs();
        String key = parseStr();
        skipWs();
        expect(':');
        Object val = parseValue();
        m.put(key, val);
        skipWs();
        if (peek('}')) { i++; return m; }
        expect(',');
      }
    }

    private List<Object> parseArr() {
      List<Object> list = new ArrayList<>();
      i++; // [
      skipWs();
      if (peek(']')) { i++; return list; }
      while (true) {
        list.add(parseValue());
        skipWs();
        if (peek(']')) { i++; return list; }
        expect(',');
      }
    }

    private String parseStr() {
      expect('"');
      StringBuilder sb = new StringBuilder();
      while (i < s.length()) {
        char c = s.charAt(i++);
        if (c == '"') return sb.toString();
        if (c == '\\') {
          if (i >= s.length()) throw new IllegalArgumentException("bad escape");
          char e = s.charAt(i++);
          switch (e) {
            case '"': case '\\': case '/': sb.append(e); break;
            case 'b': sb.append('\b'); break;
            case 'f': sb.append('\f'); break;
            case 'n': sb.append('\n'); break;
            case 'r': sb.append('\r'); break;
            case 't': sb.append('\t'); break;
            case 'u':
              if (i + 4 > s.length()) throw new IllegalArgumentException("bad unicode");
              int code = Integer.parseInt(s.substring(i, i + 4), 16);
              sb.append((char) code);
              i += 4;
              break;
            default: throw new IllegalArgumentException("bad escape");
          }
        } else {
          sb.append(c);
        }
      }
      throw new IllegalArgumentException("unterminated string");
    }

    private Object parseNum() {
      int start = i;
      if (peek('-')) i++;
      while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
      boolean isFloat = false;
      if (peek('.')) {
        isFloat = true;
        i++;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
      }
      if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
        isFloat = true;
        i++;
        if (peek('+') || peek('-')) i++;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
      }
      String n = s.substring(start, i);
      if (isFloat) return Double.valueOf(n);
      try {
        long l = Long.parseLong(n);
        if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
        return l;
      } catch (NumberFormatException e) {
        return Double.valueOf(n);
      }
    }

    private Object parseLit(String lit, Object val) {
      if (!s.startsWith(lit, i)) throw new IllegalArgumentException("expected " + lit);
      i += lit.length();
      return val;
    }

    private void skipWs() {
      while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private boolean peek(char c) {
      return i < s.length() && s.charAt(i) == c;
    }

    private void expect(char c) {
      skipWs();
      if (!peek(c)) throw new IllegalArgumentException("expected " + c);
      i++;
    }
  }
}
