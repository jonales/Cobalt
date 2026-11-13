package com.github.auties00.cobalt.media.transcode.image;

import com.github.auties00.cobalt.media.MediaPayload;
import com.github.auties00.cobalt.wire.linked.message.media.ImageMessageBuilder;
import com.github.auties00.cobalt.wire.linked.sync.action.setting.SettingsSyncAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagePipelineTest {

    @Test
    @DisplayName("a PNG with alpha becomes a JPEG with dimensions and thumbnail, even without image demuxers")
    void pngBecomesJpegWithThumbnail() throws Exception {
        var source = new BufferedImage(320, 240, BufferedImage.TYPE_INT_ARGB);
        var g = source.createGraphics();
        g.setColor(new java.awt.Color(200, 30, 30, 128));
        g.fillRect(10, 10, 100, 100);
        g.dispose();
        var png = new ByteArrayOutputStream();
        ImageIO.write(source, "png", png);
        var file = Files.createTempFile("image-pipeline", ".bin");
        try {
            Files.write(file, png.toByteArray());
            var image = new ImageMessageBuilder().build();
            try (var channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
                var payload = new ImagePipeline().run(image, channel, SettingsSyncAction.MediaQualitySetting.STANDARD);

                var bytes = assertInstanceOf(MediaPayload.OfBytes.class, payload).bytes();
                var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
                assertNotNull(decoded, "output is a decodable image");
                assertEquals(320, decoded.getWidth());
                assertEquals(240, decoded.getHeight());
                assertEquals((byte) 0xFF, bytes[0]);
                assertEquals((byte) 0xD8, bytes[1], "JPEG SOI marker");
                assertEquals("image/jpeg", image.mimetype().orElseThrow());
                assertEquals(320, image.width().orElseThrow());
                assertTrue(image.jpegThumbnail().orElseThrow().length > 0);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("the cleaner accepts a JPEG whose EOI marker is the last two bytes")
    void cleanerAcceptsEoiAtTheEndOfTheFile() throws Exception {
        var source = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        var jpeg = new ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", jpeg);
        var bytes = jpeg.toByteArray();
        assertEquals((byte) 0xD9, bytes[bytes.length - 1], "fixture ends with EOI");

        var cleaned = JpegCleaner.clean(bytes);

        assertEquals((byte) 0xFF, cleaned[cleaned.length - 2]);
        assertEquals((byte) 0xD9, cleaned[cleaned.length - 1]);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(cleaned)));
    }
}
