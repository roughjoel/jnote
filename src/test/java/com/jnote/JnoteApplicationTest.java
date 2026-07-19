package com.jnote;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JnoteApplicationTest {
    @Test
    void windowsNativeClipboardImageIsUsedBeforeTheJavafxDecoder() {
        BufferedImage nativeImage = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        AtomicBoolean javafxDecoderCalled = new AtomicBoolean();

        BufferedImage selected = JnoteApplication.preferredClipboardImage(
                () -> nativeImage,
                () -> {
                    javafxDecoderCalled.set(true);
                    return new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
                });

        assertSame(nativeImage, selected);
        assertFalse(javafxDecoderCalled.get());
    }

    @Test
    void javafxDecoderRemainsAvailableWhenTheNativeClipboardHasNoImage() {
        BufferedImage javafxImage = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        AtomicBoolean javafxDecoderCalled = new AtomicBoolean();

        BufferedImage selected = JnoteApplication.preferredClipboardImage(
                () -> null,
                () -> {
                    javafxDecoderCalled.set(true);
                    return javafxImage;
                });

        assertSame(javafxImage, selected);
        assertTrue(javafxDecoderCalled.get());
    }
}
