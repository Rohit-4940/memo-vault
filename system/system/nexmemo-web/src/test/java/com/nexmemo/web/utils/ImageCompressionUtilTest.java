package com.nexmemo.web.utils;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Image optimiser tests. The decoder is preceded by a dimension check, so the key behaviours are:
 * a valid image is re-encoded, a non-image is refused, and the optimiser never inflates the payload.
 */
class ImageCompressionUtilTest {

    @Test
    void optimizesJpegWithoutInflating() throws Exception {
        byte[] jpeg = sampleJpeg(600, 400);

        byte[] optimized = ImageCompressionUtil.optimize(jpeg, 0.4f);

        assertThat(optimized).isNotEmpty();
        assertThat(optimized.length).isLessThanOrEqualTo(jpeg.length);
        // Result must still be a decodable image.
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(optimized))).isNotNull();
    }

    @Test
    void refusesNonImageContent() {
        assertThatThrownBy(() -> ImageCompressionUtil.optimize("not an image at all".getBytes(), 0.8f))
                .isInstanceOf(IOException.class);
    }

    @Test
    void emptyInputIsReturnedUnchanged() throws Exception {
        byte[] empty = new byte[0];
        assertThat(ImageCompressionUtil.optimize(empty, 0.8f)).isSameAs(empty);
    }

    private static byte[] sampleJpeg(int w, int h) throws IOException {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(20, 120, 200));
            g.fillOval(50, 50, w - 100, h - 100);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
