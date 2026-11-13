package com.github.auties00.cobalt.media.transcode.audio;

import com.github.auties00.cobalt.wire.linked.sync.action.setting.SettingsSyncAction;
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
 * Áudio comum (não nota de voz) a 44,1 kHz: a reamostragem para 48 kHz entrega blocos de tamanho
 * variável, e o codificador AAC exige exatamente frame_size amostras em todo quadro menos o último.
 * Antes, o pipeline mandava blocos curtos ("avcodec_send_frame failed: Invalid argument") e
 * reenviava o mesmo quadro de entrada ao swr_convert.
 */
class AudioPipelineFrameSizeTest {
    private static final int RATE = 44_100;
    private static final int SECONDS = 3;

    @Test
    @DisplayName("áudio a 44,1 kHz estéreo vira AAC com a duração certa")
    void resampledAudioEncodesWithTheRightDuration() throws Exception {
        var wav = Files.createTempFile("audio-frames", ".wav");
        MediaPayload payload = null;
        try {
            Files.write(wav, stereoWav());
            var audio = new AudioMessageBuilder().mimetype("audio/wav").build();
            try (var channel = Files.newByteChannel(wav, StandardOpenOption.READ)) {
                payload = new AudioPipeline().run(audio, channel, SettingsSyncAction.MediaQualitySetting.STANDARD);
            }
            assertEquals(SECONDS, audio.seconds().orElseThrow());
            assertTrue(audio.mediaSize().orElseThrow() > 0, "saída vazia");
        } finally {
            if (payload != null) {
                payload.close();
            }
            Files.deleteIfExists(wav);
        }
    }

    private static byte[] stereoWav() {
        var frames = RATE * SECONDS;
        var data = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (var i = 0; i < frames; i++) {
            var sample = (short) (Math.sin(2 * Math.PI * 330 * i / RATE) * 8_000);
            data.putShort(sample).putShort(sample);
        }
        var header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(36 + frames * 4).put("WAVE".getBytes())
                .put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2)
                .putInt(RATE).putInt(RATE * 4).putShort((short) 4).putShort((short) 16)
                .put("data".getBytes()).putInt(frames * 4);
        var out = new ByteArrayOutputStream();
        out.writeBytes(header.array());
        out.writeBytes(data.array());
        return out.toByteArray();
    }
}
