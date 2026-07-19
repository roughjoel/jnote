package com.jnote.state;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AppState {
    private final List<Path> recentRoots = new ArrayList<>();
    private final List<Path> openFiles = new ArrayList<>();
    private Path activeFile;
    private double windowWidth;
    private double windowHeight;

    public List<Path> recentRoots() {
        return recentRoots;
    }

    public List<Path> openFiles() {
        return openFiles;
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
