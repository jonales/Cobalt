package com.github.auties00.cobalt.device.stanza;

import com.github.auties00.cobalt.stanza.model.StanzaBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Nas respostas reais de usync (fixtures {@code usync-*.jsonl}), o {@code key-index} vem em cada
 * {@code <device>} do {@code <device-list>}; o {@code <key-index-list>} só traz {@code ts} e os bytes
 * assinados. Lido do nó errado, todo aparelho vinculado ficava com índice 0.
 */
class DeviceUSyncKeyIndexTest {
    @Test
    @DisplayName("key-index do <device> do <device-list> é o índice do aparelho")
    void keyIndexComesFromTheDeviceListEntry() {
        var device = new StanzaBuilder()
                .description("device")
                .attribute("id", 75)
                .attribute("key-index", 3)
                .build();

        assertEquals(3, DeviceUSyncResponseParser.resolveKeyIndex(device, Map.of(), 75));
    }

    @Test
    @DisplayName("o celular, sem key-index, fica com índice 0")
    void primaryWithoutKeyIndexIsZero() {
        var primary = new StanzaBuilder()
                .description("device")
                .attribute("id", 0)
                .build();

        assertEquals(0, DeviceUSyncResponseParser.resolveKeyIndex(primary, Map.of(), 0));
    }

    @Test
    @DisplayName("sem key-index no <device>, usa o índice listado no <key-index-list>")
    void fallsBackToTheKeyIndexList() {
        var device = new StanzaBuilder()
                .description("device")
                .attribute("id", 73)
                .build();

        assertEquals(1, DeviceUSyncResponseParser.resolveKeyIndex(device, Map.of(73, 1), 73));
    }
}
