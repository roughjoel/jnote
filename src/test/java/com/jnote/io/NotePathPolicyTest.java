package com.jnote.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotePathPolicyTest {
    @TempDir
    Path directory;

    @Test
    void resolvesAValidNameInsideTheWorkingDirectory() {
        assertEquals(
                directory.resolve("周报.md").toAbsolutePath().normalize(),
                NotePathPolicy.resolveChild(directory, "周报.md"));
    }

    @Test
    void rejectsNamesThatCanEscapeOrCreateNestedPaths() {
        assertFalse(NotePathPolicy.isValidChildName(".."));
        assertFalse(NotePathPolicy.isValidChildName("../outside.md"));
        assertFalse(NotePathPolicy.isValidChildName("folder/note.md"));
        assertFalse(NotePathPolicy.isValidChildName("folder\\note.md"));
        assertThrows(
                IllegalArgumentException.class,
                () -> NotePathPolicy.resolveChild(directory, "../outside.md"));
    }

    @Test
    void rejectsWindowsReservedAndInvalidNames() {
        assertFalse(NotePathPolicy.isValidChildName("CON.txt"));
        assertFalse(NotePathPolicy.isValidChildName("bad?.md"));
        assertFalse(NotePathPolicy.isValidChildName("trailing."));
        assertFalse(NotePathPolicy.isValidChildName("trailing "));
    }

    @Test
    void onlyAllowsWritableNoteFormatsForNewFiles() {
        assertTrue(NotePathPolicy.isCreatableNote(directory.resolve("note.md")));
        assertTrue(NotePathPolicy.isCreatableNote(directory.resolve("note.markdown")));
        assertTrue(NotePathPolicy.isCreatableNote(directory.resolve("note.txt")));
        assertTrue(NotePathPolicy.isCreatableNote(directory.resolve("note.docx")));
        assertFalse(NotePathPolicy.isCreatableNote(directory.resolve("note.doc")));
        assertFalse(NotePathPolicy.isCreatableNote(directory.resolve("note.pdf")));
    }
}
