package com.jnote.io;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes a complete replacement before making it visible at the target path. */
public final class AtomicFiles {
    private AtomicFiles() {
    }

    public static void write(Path path, PathWriter writer) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        Path directory = target.getParent();
        if (directory == null) {
            throw new IOException("无法确定文件所在目录：" + path);
        }

        Path temporaryFile = Files.createTempFile(directory, ".jnote-", ".tmp");
        boolean replaced = false;
        try {
            writer.write(temporaryFile);
            try {
                Files.move(
                        temporaryFile,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
            replaced = true;
        } finally {
            if (!replaced) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    @FunctionalInterface
    public interface PathWriter {
        void write(Path path) throws IOException;
    }
}
