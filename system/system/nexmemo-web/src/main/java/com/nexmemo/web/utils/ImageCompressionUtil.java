package com.nexmemo.web.utils;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * In-place image optimisation built on the JDK {@code ImageIO} stack — no third-party codec is
 * introduced. Re-encodes JPEG at a quality dial and normalises everything else to PNG.
 *
 * <p>Decoding is preceded by a dimension check performed on the reader, so a small "image bomb"
 * (a few KB that decodes to gigabytes of raster) is rejected before any pixels are allocated.</p>
 *
 * @author : NexMemo
 */
@Slf4j
public final class ImageCompressionUtil {

    /** ~40 MP ceiling; comfortably above real documents, far below a memory bomb. */
    private static final long MAX_PIXELS = 40_000_000L;

    private ImageCompressionUtil() {}

    /**
     * @param data       raw image bytes (must already have passed signature validation)
     * @param jpegQuality 0.0-1.0 quality for JPEG output (ignored for other kinds)
     * @return optimised bytes, or the original array when re-encoding would not help
     */
    public static byte[] optimize(byte[] data, float jpegQuality) throws IOException {
        if (data == null || data.length == 0) {
            return data;
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (in == null) {
                throw new IOException("Unreadable image stream");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException("No image reader for the supplied content");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IOException("Image dimensions rejected: " + width + "x" + height);
                }
                BufferedImage image = reader.read(0);
                String format = reader.getFormatName();
                boolean jpeg = format != null && format.toLowerCase().contains("jpeg");

                byte[] encoded = jpeg
                        ? writeJpeg(image, clamp(jpegQuality))
                        : writePng(image);

                if (encoded == null || encoded.length == 0) {
                    return data;
                }
                // Only keep the result if it actually saved space.
                return encoded.length < data.length ? encoded : data;
            } finally {
                reader.dispose();
            }
        }
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer available");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ImageOutputStream out = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
            out.flush();
            return baos.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static byte[] writePng(BufferedImage image) throws IOException {
        // Flatten alpha onto white so GIF/BMP with transparency do not produce black PNGs.
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = rgb.createGraphics();
        try {
            g.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (!ImageIO.write(rgb, "png", baos)) {
                throw new IOException("No PNG writer available");
            }
            return baos.toByteArray();
        }
    }

    private static float clamp(float quality) {
        if (quality <= 0f || quality > 1f) {
            return 0.82f;
        }
        return quality;
    }
}
