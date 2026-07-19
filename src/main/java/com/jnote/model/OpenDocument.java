package com.jnote.model;

import java.nio.file.Path;

public final class OpenDocument {
    private Path path;
    private NoteFormat format;
    private String content;
    private String savedContent;
    private boolean dirty;

    public OpenDocument(Path path, NoteFormat format, String content) {
        this.path = path;
        this.format = format;
        this.content = normalize(content);
        this.savedContent = this.content;
    }

    public Path path() {
        return path;
    }

    public NoteFormat format() {
        return format;
    }

    public void moveTo(Path newPath) {
        this.path = newPath;
        this.format = NoteFormat.fromPath(newPath);
    }

    public String content() {
        return content;
    }

    public void setContent(String content) {
        this.content = normalize(content);
        this.dirty = !this.content.equals(savedContent);
    }

    public boolean dirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
        if (!dirty) {
            this.savedContent = content;
        }
    }

    public String displayName() {
        return path == null || path.getFileName() == null ? "Untitled.md" : path.getFileName().toString();
    }

    public String imageDirectoryName() {
        String name = displayName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return "image_" + name;
    }

    private static String normalize(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n');
    }
}
