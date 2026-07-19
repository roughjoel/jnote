package com.jnote.markdown;

import java.util.Locale;
import java.util.Set;

final class CodeHighlighter {
    private static final Set<String> JAVA = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "var", "record", "sealed", "permits", "non-sealed");
    private static final Set<String> PYTHON = Set.of(
            "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif",
            "else", "except", "False", "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return", "True", "try", "while",
            "with", "yield");
    private static final Set<String> SQL = Set.of(
            "select", "from", "where", "join", "left", "right", "inner", "outer", "on", "and", "or", "not",
            "insert", "into", "update", "delete", "create", "alter", "drop", "table", "view", "index",
            "group", "by", "order", "having", "limit", "offset", "as", "distinct", "union", "all", "case",
            "when", "then", "else", "end", "values", "set", "desc", "asc");

    private CodeHighlighter() {
    }

    static String highlight(String code, String language) {
        String normalized = language == null ? "" : language.toLowerCase(Locale.ROOT).trim();
        String[] lines = code.split("\\R", -1);
        StringBuilder html = new StringBuilder(code.length() + 64);
        for (int i = 0; i < lines.length; i++) {
            html.append(highlightLine(lines[i], normalized));
            if (i < lines.length - 1) {
                html.append('\n');
            }
        }
        return html.toString();
    }

    private static String highlightLine(String line, String language) {
        String commentPrefix = switch (language) {
            case "python", "py" -> "#";
            case "sql" -> "--";
            default -> "//";
        };

        int commentIndex = findCommentStart(line, commentPrefix);
        String code = commentIndex >= 0 ? line.substring(0, commentIndex) : line;
        String comment = commentIndex >= 0 ? line.substring(commentIndex) : "";

        StringBuilder html = new StringBuilder(line.length() + 32);
        int i = 0;
        while (i < code.length()) {
            char ch = code.charAt(i);
            if (ch == '"' || ch == '\'') {
                int end = readString(code, i, ch);
                html.append("<span class=\"str\">")
                        .append(HtmlEscaper.escape(code.substring(i, end)))
                        .append("</span>");
                i = end;
            } else if (Character.isDigit(ch)) {
                int end = i + 1;
                while (end < code.length() && (Character.isDigit(code.charAt(end)) || code.charAt(end) == '.')) {
                    end++;
                }
                html.append("<span class=\"num\">")
                        .append(HtmlEscaper.escape(code.substring(i, end)))
                        .append("</span>");
                i = end;
            } else if (Character.isJavaIdentifierStart(ch)) {
                int end = i + 1;
                while (end < code.length() && Character.isJavaIdentifierPart(code.charAt(end))) {
                    end++;
                }
                String word = code.substring(i, end);
                if (isKeyword(word, language)) {
                    html.append("<span class=\"kw\">").append(HtmlEscaper.escape(word)).append("</span>");
                } else {
                    html.append(HtmlEscaper.escape(word));
                }
                i = end;
            } else {
                html.append(HtmlEscaper.escape(String.valueOf(ch)));
                i++;
            }
        }

        if (!comment.isEmpty()) {
            html.append("<span class=\"comment\">")
                    .append(HtmlEscaper.escape(comment))
                    .append("</span>");
        }
        return html.toString();
    }

    private static int findCommentStart(String line, String commentPrefix) {
        char quote = 0;
        boolean escaping = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (quote != 0) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
            } else if (line.startsWith(commentPrefix, i)) {
                return i;
            }
        }
        return -1;
    }

    private static int readString(String code, int start, char quote) {
        int i = start + 1;
        boolean escaping = false;
        while (i < code.length()) {
            char current = code.charAt(i);
            if (escaping) {
                escaping = false;
            } else if (current == '\\') {
                escaping = true;
            } else if (current == quote) {
                return i + 1;
            }
            i++;
        }
        return code.length();
    }

    private static boolean isKeyword(String word, String language) {
        return switch (language) {
            case "python", "py" -> PYTHON.contains(word);
            case "sql" -> SQL.contains(word.toLowerCase(Locale.ROOT));
            default -> JAVA.contains(word);
        };
    }
}
