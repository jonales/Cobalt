package com.github.auties00.cobalt.media.transcode.audio;

import com.github.auties00.cobalt.media.MediaPayload;
import com.github.auties00.cobalt.wire.linked.message.media.AudioMessageBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O granule das páginas Ogg de um stream Opus conta amostras a 48 kHz (RFC 7845), qualquer que seja
 * a taxa do codificador. Sem reescalar os pacotes de 1/16000 para o time base do muxer, a nota de voz
 * dizia durar um terço do real.
 */
class PttPipelineGranuleTest {
    private static final int WAV_RATE = 16_000;
    private static final int SECONDS = 2;

    @Test
    @DisplayName("o granule final da nota de voz corresponde à duração real, a 48 kHz")
    void lastGranuleMatchesTheRealDuration() throws Exception {
        var wav = Files.createTempFile("ptt-granule", ".wav");
        try {
            Files.write(wav, sineWav());
            var audio = new AudioMessageBuilder().mimetype("audio/ogg; codecs=opus").ptt(true).build();
            byte[] ogg;
            try (var channel = Files.newByteChannel(wav, StandardOpenOption.READ)) {
                ogg = ((MediaPayload.OfBytes) new PttPipeline().run(audio, channel)).bytes();
            }

            var head = ByteBuffer.wrap(ogg).order(ByteOrder.LITTLE_ENDIAN);
            var preSkip = Short.toUnsignedInt(head.getShort(28 + 10));   // OpusHead: pre-skip
            var seconds = (lastGranule(ogg) - preSkip) / 48_000.0;
            assertTrue(Math.abs(seconds - SECONDS) < 0.05,
                    "esperava ~" + SECONDS + " s pelo granule, veio " + seconds + " s");
            assertEquals(SECONDS, audio.seconds().orElseThrow());
        } finally {
            Files.deleteIfExists(wav);
        }
    }

    private static long lastGranule(byte[] ogg) {
        var buffer = ByteBuffer.wrap(ogg).order(ByteOrder.LITTLE_ENDIAN);
        long granule = -1;
        var offset = 0;
        while (offset + 27 <= ogg.length) {
            assertEquals('O', ogg[offset], "página Ogg fora de lugar em " + offset);
            granule = buffer.getLong(offset + 6);
            var segments = Byte.toUnsignedInt(ogg[offset + 26]);
            var body = 0;
            for (var i = 0; i < segments; i++) {
                body += Byte.toUnsignedInt(ogg[offset + 27 + i]);
            }
            offset += 27 + segments + body;
        }
        return granule;
    }

    private static byte[] sineWav() {
        var samples = WAV_RATE * SECONDS;
        var data = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (var i = 0; i < samples; i++) {
            data.putShort((short) (Math.sin(2 * Math.PI * 440 * i / WAV_RATE) * 8_000));
        }
        var header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(36 + samples * 2).put("WAVE".getBytes())
                .put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(WAV_RATE).putInt(WAV_RATE * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(samples * 2);
        var out = new ByteArrayOutputStream();
        out.writeBytes(header.array());
        out.writeBytes(data.array());
        return out.toByteArray();
    }
}
