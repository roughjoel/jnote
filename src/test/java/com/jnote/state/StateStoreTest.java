package com.jnote.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateStoreTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsApplicationStateWithoutLeavingTemporaryFiles() throws IOException {
        Path stateFile = directory.resolve("config/state.properties");
        StateStore store = new StateStore(stateFile);
        AppState expected = new AppState();
        expected.recentRoots().add(directory.resolve("notes"));
        expected.openFiles().add(directory.resolve("notes/one.md"));
        expected.setActiveFile(directory.resolve("notes/one.md"));
        expected.setWindowSize(1280, 800);

        store.save(expected);
        AppState actual = store.load();

        assertEquals(expected.recentRoots(), actual.recentRoots());
        assertEquals(expected.openFiles(), actual.openFiles());
        assertEquals(expected.activeFile(), actual.activeFile());
        assertEquals(1280, actual.windowWidth());
        assertEquals(800, actual.windowHeight());
        try (var files = Files.list(stateFile.getParent())) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".jnote-")));
        }
    }

    @Test
    void ignoresCorruptPathsCountsAndWindowSizes() throws IOException {
        Path stateFile = directory.resolve("state.properties");
        Files.writeString(stateFile, """
                recentRoot.count=999999999
                recentRoot.0=valid
                recentRoot.1=bad\\u0000path
                openFile.count=not-a-number
                activeFile=bad\\u0000path
                window.width=NaN
                window.height=Infinity
                """);

        AppState state = new StateStore(stateFile).load();

        assertEquals(1, state.recentRoots().size());
        assertEquals(Path.of("valid"), state.recentRoots().get(0));
        assertTrue(state.openFiles().isEmpty());
        assertNull(state.activeFile());
        assertEquals(0, state.windowWidth());
        assertEquals(0, state.windowHeight());
    }
}
