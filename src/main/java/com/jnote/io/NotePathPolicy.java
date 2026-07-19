package com.jnote.io;

import com.jnote.model.NoteFormat;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Centralizes the filename and workspace-boundary rules used by file operations.
 */
public final class NotePathPolicy {
    private static final String INVALID_WINDOWS_CHARACTERS = "<>:\"/\\|?*";
    private static final Set<String> RESERVED_WINDOWS_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private NotePathPolicy() {
    }

    public static boolean isValidChildName(String name) {
        if (name == null || name.isBlank() || name.equals(".") || name.equals("..")) {
            return false;
        }
        if (!name.equals(name.trim()) || name.endsWith(".")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (character < 32 || INVALID_WINDOWS_CHARACTERS.indexOf(character) >= 0) {
                return false;
            }
        }

        int dot = name.indexOf('.');
        String stem = (dot < 0 ? name : name.substring(0, dot)).toUpperCase(Locale.ROOT);
        if (RESERVED_WINDOWS_NAMES.contains(stem)) {
            return false;
        }

        try {
            Path candidate = Path.of(name);
            return !candidate.isAbsolute() && candidate.getNameCount() == 1;
        } catch (InvalidPathException ex) {
            return false;
        }
    }

    public static Path resolveChild(Path directory, String name) {
        if (directory == null || !isValidChildName(name)) {
            throw new IllegalArgumentException("无效的文件或文件夹名称");
        }
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        Path target = normalizedDirectory.resolve(name).normalize();
        if (!normalizedDirectory.equals(target.getParent())) {
            throw new IllegalArgumentException("目标必须位于当前文件夹内");
        }
        return target;
    }

    public static boolean isCreatableNote(Path path) {
        NoteFormat format = NoteFormat.fromPath(path);
        if (format == NoteFormat.MARKDOWN || format == NoteFormat.TEXT) {
            return true;
        }
        if (format != NoteFormat.WORD || path == null || path.getFileName() == null) {
            return false;
        }
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx");
    }
}
