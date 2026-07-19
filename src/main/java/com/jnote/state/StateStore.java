package com.jnote.state;

import com.jnote.io.AtomicFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class StateStore {
    private static final int RECENT_ROOT_LIMIT = 10;
    private static final int OPEN_FILE_LIMIT = 100;
    private final Path stateFile;

    public StateStore() {
        this(Path.of(System.getProperty("user.home"), ".jnote", "state.properties"));
    }

    StateStore(Path stateFile) {
        this.stateFile = stateFile.toAbsolutePath().normalize();
    }

    public AppState load() {
        AppState state = new AppState();
        if (!Files.exists(stateFile)) {
            return state;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(stateFile)) {
            properties.load(input);
        } catch (IOException ignored) {
            return state;
        }

        int rootCount = Math.min(RECENT_ROOT_LIMIT, parseCount(properties.getProperty("recentRoot.count")));
        for (int i = 0; i < rootCount; i++) {
            readPath(properties.getProperty("recentRoot." + i), state.recentRoots());
        }

        int openCount = Math.min(OPEN_FILE_LIMIT, parseCount(properties.getProperty("openFile.count")));
        for (int i = 0; i < openCount; i++) {
            readPath(properties.getProperty("openFile." + i), state.openFiles());
        }

        String active = properties.getProperty("activeFile");
        state.setActiveFile(parsePath(active));
        state.setWindowSize(
                parseDouble(properties.getProperty("window.width")),
                parseDouble(properties.getProperty("window.height")));

        return state;
    }

    public void save(AppState state) throws IOException {
        Files.createDirectories(stateFile.getParent());
        Properties properties = new Properties();

        List<Path> recentRoots = validPaths(state.recentRoots(), RECENT_ROOT_LIMIT);
        properties.setProperty("recentRoot.count", String.valueOf(recentRoots.size()));
        for (int i = 0; i < recentRoots.size(); i++) {
            properties.setProperty("recentRoot." + i, recentRoots.get(i).toString());
        }

        List<Path> openFiles = validPaths(state.openFiles(), OPEN_FILE_LIMIT);
        properties.setProperty("openFile.count", String.valueOf(openFiles.size()));
        for (int i = 0; i < openFiles.size(); i++) {
            properties.setProperty("openFile." + i, openFiles.get(i).toString());
        }

        if (state.activeFile() != null) {
            properties.setProperty("activeFile", state.activeFile().toString());
        }
        if (state.windowWidth() > 0 && state.windowHeight() > 0) {
            properties.setProperty("window.width", String.valueOf(state.windowWidth()));
            properties.setProperty("window.height", String.valueOf(state.windowHeight()));
        }

        AtomicFiles.write(stateFile, temporaryFile -> {
            try (OutputStream output = Files.newOutputStream(temporaryFile)) {
                properties.store(output, "Jnote state");
            }
        });
    }

    private static void readPath(String value, List<Path> target) {
        Path path = parsePath(value);
        if (path != null) {
            target.add(path);
        }
    }

    private static Path parsePath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Path.of(value);
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private static List<Path> validPaths(List<Path> paths, int limit) {
        List<Path> valid = new ArrayList<>(Math.min(paths.size(), limit));
        for (Path path : paths) {
            if (path != null) {
                valid.add(path);
                if (valid.size() == limit) {
                    break;
                }
            }
        }
        return valid;
    }

    private static int parseCount(String value) {
        try {
            return value == null ? 0 : Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double parseDouble(String value) {
        try {
            double parsed = value == null ? 0 : Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed > 0 ? parsed : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
