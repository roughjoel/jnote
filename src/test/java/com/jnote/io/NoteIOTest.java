package com.jnote.io;

import com.jnote.model.NoteFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteIOTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void textRoundTripPreservesConsecutiveBlankLinesAndTrailingNewlines() throws IOException {
        Path note = temporaryDirectory.resolve("换行测试.txt");
        String content = "第一行\n\n\n第二行\n\n";

        NoteIO.write(note, NoteFormat.TEXT, content);

        assertEquals(content, Files.readString(note, StandardCharsets.UTF_8));
        assertEquals(content, NoteIO.read(note));
        try (var files = Files.list(temporaryDirectory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".jnote-")));
        }
    }

    @Test
    void docxRoundTripWorksThroughAtomicTemporaryFile() throws IOException {
        Path note = temporaryDirectory.resolve("说明.docx");

        NoteIO.write(note, NoteFormat.WORD, "第一段\n第二段");

        assertEquals("第一段\n\n第二段", NoteIO.read(note));
    }

    @Test
    void imageDirectoryFollowsTheMarkdownFileStem() {
        Path note = temporaryDirectory.resolve("项目周报.md");

        assertEquals(temporaryDirectory.resolve("image_项目周报"), NoteIO.imageDirectory(note));
    }

    @Test
    void createsADataUriForLocalImages() throws IOException {
        Path image = temporaryDirectory.resolve("preview.png");
        Files.write(image, new byte[]{1, 2, 3, 4});

        String uri = NoteIO.imageDataUri(image);

        assertTrue(uri.startsWith("data:image/png;base64,"));
        assertTrue(uri.endsWith("AQIDBA=="));
    }

    @Test
    void repairsWindowsClipboardImagesWhoseAlphaIsEntirelyZero() {
        BufferedImage broken = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        broken.setRGB(0, 0, 0x00ff0000);
        broken.setRGB(1, 0, 0x0000ff00);

        BufferedImage repaired = NoteIO.repairFullyTransparentImage(broken);

        assertEquals(255, new Color(repaired.getRGB(0, 0), true).getAlpha());
        assertEquals(255, new Color(repaired.getRGB(0, 0), true).getRed());
        assertEquals(255, new Color(repaired.getRGB(1, 0), true).getGreen());
    }

    @Test
    void imageDataUriRepairsMalformedClipboardPng(@TempDir Path directory) throws IOException {
        BufferedImage broken = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        broken.setRGB(0, 0, 0x00ff0000);
        broken.setRGB(1, 0, 0x0000ff00);
        Path image = directory.resolve("broken.png");
        ImageIO.write(broken, "png", image.toFile());

        String uri = NoteIO.imageDataUri(image);
        byte[] repairedBytes = Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
        BufferedImage repaired = ImageIO.read(new ByteArrayInputStream(repairedBytes));

        assertEquals(255, new Color(repaired.getRGB(0, 0), true).getAlpha());
        assertEquals(255, new Color(repaired.getRGB(0, 0), true).getRed());
        assertEquals(255, new Color(repaired.getRGB(1, 0), true).getGreen());
    }

    @Test
    void copyingAMalformedClipboardImageWritesVisiblePixels(@TempDir Path directory) throws IOException {
        BufferedImage broken = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        broken.setRGB(0, 0, 0x00ff0000);
        broken.setRGB(1, 0, 0x0000ff00);
        Path source = directory.resolve("clipboard.png");
        ImageIO.write(broken, "png", source.toFile());
        Path document = directory.resolve("note.md");

        Path copied = NoteIO.copyImageForDocument(document, source);
        BufferedImage repaired = ImageIO.read(copied.toFile());

        assertEquals(255, new Color(repaired.getRGB(0, 0), true).getAlpha());
        assertEquals(255, new Color(repaired.getRGB(0, 0), true).getRed());
        assertEquals(255, new Color(repaired.getRGB(1, 0), true).getGreen());
    }
}
