package com.jnote.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenDocumentTest {
    @Test
    void moveToUpdatesPathAndPreservesEditingState() {
        OpenDocument document = new OpenDocument(Path.of("D:/notes/old.md"), NoteFormat.MARKDOWN, "saved");
        document.setContent("edited");

        document.moveTo(Path.of("D:/notes/new.md"));

        assertEquals(Path.of("D:/notes/new.md"), document.path());
        assertEquals(NoteFormat.MARKDOWN, document.format());
        assertEquals("edited", document.content());
        assertTrue(document.dirty());
    }
}
