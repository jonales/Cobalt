package com.github.auties00.cobalt.device.icdc;

import com.github.auties00.cobalt.wire.linked.device.DeviceListMetadataBuilder;
import com.github.auties00.cobalt.wire.linked.device.DeviceListMetadataSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * O WhatsApp Web grava {@code senderTimestamp} e {@code recipientTimestamp} do
 * {@code DeviceListMetadata} em segundos Unix (é o {@code timestamp} da lista de
 * aparelhos, comparado com {@code unixTime()}). Em milissegundos, quem recebe passa a
 * esperar uma lista de aparelhos ~1000x mais nova, que nunca chega.
 */
class DeviceListMetadataWireTest {
    private static final long SECONDS = 1_763_000_000L;

    @Test
    @DisplayName("senderTimestamp vai no fio em segundos Unix")
    void senderTimestampIsEncodedInSeconds() {
        var metadata = new DeviceListMetadataBuilder()
                .senderTimestamp(Instant.ofEpochSecond(SECONDS))
                .build();

        assertEquals(SECONDS, varintField(DeviceListMetadataSpec.encode(metadata), 2));
    }

    @Test
    @DisplayName("recipientTimestamp vai no fio em segundos Unix")
    void recipientTimestampIsEncodedInSeconds() {
        var metadata = new DeviceListMetadataBuilder()
                .recipientTimestamp(Instant.ofEpochSecond(SECONDS))
                .build();

        assertEquals(SECONDS, varintField(DeviceListMetadataSpec.encode(metadata), 9));
    }

    @Test
    @DisplayName("segundos recebidos do WhatsApp Web voltam como o mesmo instante")
    void secondsFromTheWireDecodeToTheSameInstant() {
        var decoded = DeviceListMetadataSpec.decode(field(2, SECONDS));

        assertEquals(Instant.ofEpochSecond(SECONDS), decoded.senderTimestamp().orElseThrow());
    }

    /** Lê o valor varint do campo {@code index}; os outros campos são pulados pelo tipo. */
    private static long varintField(byte[] bytes, int index) {
        var position = new int[]{0};
        while (position[0] < bytes.length) {
            var key = readVarint(bytes, position);
            var wireType = (int) (key & 0x7);
            var value = switch (wireType) {
                case 0 -> readVarint(bytes, position);
                case 2 -> {
                    var length = (int) readVarint(bytes, position);
                    position[0] += length;
                    yield -1L;
                }
                case 1 -> { position[0] += 8; yield -1L; }
                case 5 -> { position[0] += 4; yield -1L; }
                default -> throw new IllegalStateException("wire type " + wireType);
            };
            if ((key >>> 3) == index) {
                return value;
            }
        }
        return fail("campo " + index + " ausente");
    }

    private static long readVarint(byte[] bytes, int[] position) {
        long result = 0;
        for (var shift = 0; ; shift += 7) {
            var b = bytes[position[0]++];
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
    }

    private static byte[] field(int index, long value) {
        var out = new ByteArrayOutputStream();
        writeVarint(out, (long) index << 3);
        writeVarint(out, value);
        return out.toByteArray();
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }
}
