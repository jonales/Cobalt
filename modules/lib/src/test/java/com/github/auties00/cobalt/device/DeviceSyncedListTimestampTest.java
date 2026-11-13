package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.wire.core.jid.Jid;
import com.github.auties00.cobalt.wire.linked.device.info.DeviceListBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O WhatsApp Web guarda, para a lista de aparelhos, o {@code timestamp} da lista de índices de chave
 * assinada; é ele que sai como {@code senderTimestamp} no ICDC. Com o horário da consulta, toda
 * mensagem dizia ao destinatário que a lista do remetente era mais nova que a assinada, e o WhatsApp
 * Web mostrava "Não foi possível confirmar seu código de segurança".
 */
class DeviceSyncedListTimestampTest {
    private static final Jid USER = Jid.of("19254863482@s.whatsapp.net");

    @Test
    @DisplayName("a lista sincronizada guarda o timestamp assinado, não o horário da consulta")
    void keepsTheSignedTimestamp() {
        var signed = Instant.ofEpochSecond(1_778_522_044L);
        var list = new DeviceListBuilder().userJid(USER).timestamp(signed).build();

        assertEquals(signed, LiveDeviceService.syncedListTimestamp(list));
    }

    @Test
    @DisplayName("sem timestamp assinado, usa o horário atual")
    void fallsBackToNowWithoutSignedTimestamp() {
        var before = Instant.now();
        var list = new DeviceListBuilder().userJid(USER).build();

        var stored = LiveDeviceService.syncedListTimestamp(list);

        assertTrue(!stored.isBefore(before), "esperava um instante a partir de " + before + ", veio " + stored);
    }
}
