package com.jnote.model;

import java.nio.file.Path;
import java.util.Locale;

public enum NoteFormat {
    MARKDOWN("MD"),
    TEXT("TXT"),
    WORD("DOC"),
    UNKNOWN("FILE");

    private final String label;

    NoteFormat(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static NoteFormat fromPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return UNKNOWN;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            return MARKDOWN;
        }
        if (name.endsWith(".txt")) {
            return TEXT;
        }
        if (name.endsWith(".docx") || name.endsWith(".doc")) {
            return WORD;
        }
        return UNKNOWN;
    }

    public boolean isSupportedFile() {
        return this == MARKDOWN || this == TEXT || this == WORD;
    }
}
