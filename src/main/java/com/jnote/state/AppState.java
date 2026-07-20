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

    public boolean moveRecentRoot(Path source, Path target, boolean placeAfterTarget) {
        int sourceIndex = indexOfRecentRoot(source);
        int targetIndex = indexOfRecentRoot(target);
        if (sourceIndex < 0 || targetIndex < 0 || sourceIndex == targetIndex) {
            return false;
        }

        List<Path> previousOrder = List.copyOf(recentRoots);
        Path moved = recentRoots.remove(sourceIndex);
        if (sourceIndex < targetIndex) {
            targetIndex--;
        }
        int insertionIndex = placeAfterTarget ? targetIndex + 1 : targetIndex;
        recentRoots.add(insertionIndex, moved);
        return !recentRoots.equals(previousOrder);
    }

    private int indexOfRecentRoot(Path root) {
        if (root == null) {
            return -1;
        }
        Path normalized = root.toAbsolutePath().normalize();
        for (int i = 0; i < recentRoots.size(); i++) {
            Path candidate = recentRoots.get(i);
            if (candidate != null && candidate.toAbsolutePath().normalize().equals(normalized)) {
                return i;
            }
        }
        return -1;
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
