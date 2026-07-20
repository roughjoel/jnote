package com.jnote.state;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AppState {
    private final List<Path> recentRoots = new ArrayList<>();
    private final List<Path> openFiles = new ArrayList<>();
    private final Map<Path, Set<String>> collapsedHeadings = new LinkedHashMap<>();
    private Path activeFile;
    private double windowWidth;
    private double windowHeight;

    public List<Path> recentRoots() {
        return recentRoots;
    }

    public List<Path> openFiles() {
        return openFiles;
    }

    public Map<Path, Set<String>> collapsedHeadings() {
        return collapsedHeadings;
    }

    public Set<String> collapsedHeadings(Path path) {
        if (path == null) {
            return Set.of();
        }
        Set<String> headings = collapsedHeadings.get(path.toAbsolutePath().normalize());
        return headings == null ? Set.of() : Set.copyOf(headings);
    }

    public void setHeadingCollapsed(Path path, String headingKey, boolean collapsed) {
        if (path == null || headingKey == null || headingKey.isBlank()) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (collapsed) {
            collapsedHeadings.computeIfAbsent(normalized, ignored -> new LinkedHashSet<>()).add(headingKey);
            return;
        }
        Set<String> headings = collapsedHeadings.get(normalized);
        if (headings == null) {
            return;
        }
        headings.remove(headingKey);
        if (headings.isEmpty()) {
            collapsedHeadings.remove(normalized);
        }
    }

    public Path activeFile() {
        return activeFile;
    }

    public void setActiveFile(Path activeFile) {
        this.activeFile = activeFile;
    }

    public double windowWidth() {
        return windowWidth;
    }

    public double windowHeight() {
        return windowHeight;
    }

    public void setWindowSize(double windowWidth, double windowHeight) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
    }
}
