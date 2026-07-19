package com.jnote.io;

import com.jnote.model.NoteFormat;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Locale;
import java.util.stream.Collectors;

public final class NoteIO {
    private NoteIO() {
    }

    public static String read(Path path) throws IOException {
        NoteFormat format = NoteFormat.fromPath(path);
        return switch (format) {
            case MARKDOWN, TEXT, UNKNOWN -> readText(path);
            case WORD -> readWord(path);
        };
    }

    public static void write(Path path, NoteFormat format, String content) throws IOException {
        if (format == NoteFormat.WORD && !isDocx(path)) {
            throw new IOException("暂不支持保存旧版 .doc 文件，请新建或使用 .docx 文件。");
        }
        AtomicFiles.write(path, temporaryFile -> {
            switch (format) {
                case MARKDOWN, TEXT, UNKNOWN ->
                        Files.writeString(temporaryFile, normalize(content), StandardCharsets.UTF_8);
                case WORD -> writeDocx(temporaryFile, content);
            }
        });
    }

    public static Path copyImageForDocument(Path documentPath, Path imagePath) throws IOException {
        Path imageDirectory = imageDirectory(documentPath);
        Files.createDirectories(imageDirectory);
        byte[] sourceBytes = Files.readAllBytes(imagePath);
        byte[] visibleBytes = repairFullyTransparentImage(sourceBytes);
        if (visibleBytes != sourceBytes) {
            Path target = uniqueTarget(imageDirectory, pngFileName(imagePath.getFileName().toString()));
            Files.write(target, visibleBytes);
            return target;
        }
        Path target = uniqueTarget(imageDirectory, imagePath.getFileName().toString());
        Files.copy(imagePath, target, StandardCopyOption.COPY_ATTRIBUTES);
        return target;
    }

    public static Path imageDirectory(Path documentPath) {
        String name = documentPath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return documentPath.getParent().resolve("image_" + name);
    }

    public static boolean isImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".bmp");
    }

    public static String imageDataUri(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String mimeType = imageMimeType(path);
        byte[] visibleBytes = repairFullyTransparentImage(bytes);
        if (visibleBytes != bytes) {
            bytes = visibleBytes;
            mimeType = "image/png";
        }
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    public static String imageMimeType(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".bmp")) return "image/bmp";
        return "image/png";
    }

    public static BufferedImage repairFullyTransparentImage(BufferedImage source) {
        if (source == null || !source.getColorModel().hasAlpha()) {
            return source;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        for (int pixel : pixels) {
            if ((pixel >>> 24) != 0) {
                return source;
            }
        }

        for (int i = 0; i < pixels.length; i++) {
            pixels[i] |= 0xff000000;
        }
        BufferedImage repaired = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        repaired.setRGB(0, 0, width, height, pixels, 0, width);
        return repaired;
    }

    public static byte[] repairFullyTransparentImage(byte[] encodedImage) throws IOException {
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(encodedImage));
        if (decoded == null) {
            return encodedImage;
        }
        BufferedImage repaired = repairFullyTransparentImage(decoded);
        if (repaired == decoded) {
            return encodedImage;
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(repaired, "png", output)) {
                return encodedImage;
            }
            return output.toByteArray();
        }
    }

    private static String pngFileName(String originalName) {
        int dot = originalName.lastIndexOf('.');
        String stem = dot > 0 ? originalName.substring(0, dot) : originalName;
        return stem + ".png";
    }

    private static String readText(Path path) throws IOException {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (MalformedInputException ignored) {
            return Files.readString(path, Charset.defaultCharset());
        }
    }

    private static String readWord(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".docx")) {
            try (InputStream input = Files.newInputStream(path);
                 XWPFDocument document = new XWPFDocument(input)) {
                return document.getParagraphs()
                        .stream()
                        .map(XWPFParagraph::getText)
                        .collect(Collectors.joining("\n\n"));
            }
        }

        try (InputStream input = Files.newInputStream(path);
             HWPFDocument document = new HWPFDocument(input);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private static void writeDocx(Path path, String content) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             OutputStream output = Files.newOutputStream(path)) {
            String[] lines = normalize(content).split("\\R", -1);
            for (String line : lines) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.createRun().setText(line);
            }
            document.write(output);
        }
    }

    private static boolean isDocx(Path path) {
        return path != null
                && path.getFileName() != null
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx");
    }

    private static String normalize(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static Path uniqueTarget(Path directory, String originalName) {
        Path target = directory.resolve(originalName);
        if (!Files.exists(target)) {
            return target;
        }
        String stem = originalName;
        String extension = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) {
            stem = originalName.substring(0, dot);
            extension = originalName.substring(dot);
        }
        int index = 1;
        while (Files.exists(directory.resolve(stem + "_" + index + extension))) {
            index++;
        }
        return directory.resolve(stem + "_" + index + extension);
    }
}
