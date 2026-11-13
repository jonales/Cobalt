package com.github.auties00.cobalt.message.receive;

import com.github.auties00.cobalt.client.linked.TestWhatsAppClient;
import com.github.auties00.cobalt.message.MessageEncryptionType;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.message.TestSignalSession;
import com.github.auties00.cobalt.message.crypto.SignalCryptoLocks;
import com.github.auties00.cobalt.message.receive.crypto.MessageDecryption;
import com.github.auties00.cobalt.message.send.crypto.MessageEncryption;
import com.github.auties00.cobalt.stanza.model.StanzaBuilder;
import com.github.auties00.cobalt.wam.TestWamService;
import com.github.auties00.cobalt.wire.core.jid.Jid;
import com.github.auties00.cobalt.wire.linked.message.LinkedMessageContainer;
import com.github.auties00.cobalt.wire.linked.message.LinkedMessageContainerSpec;
import com.github.auties00.cobalt.wire.linked.message.text.ExtendedTextMessage;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.groups.SignalGroupCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live regression: this client opens a session with a contact by phone number (outbound pkmsg)
 * and the contact's reply arrives addressed by LID, with the number in {@code sender_pn}. The
 * receiver must learn the pair and find the session opened by phone, or the reply fails to
 * decrypt and triggers a retry receipt.
 */
@DisplayName("ChatMessageReceiver: reply by LID to a session opened by phone number")
class ChatMessageReceiverLidTest {

    private static final Jid SELF_BARE = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid SELF_PRIMARY = Jid.of("19254863482:0@s.whatsapp.net");
    private static final Jid PEER_BARE = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid PEER_PRIMARY = Jid.of("12025550100:0@s.whatsapp.net");
    private static final Jid PEER_LID_BARE = Jid.of("249533976023044@lid");
    private static final Jid PEER_LID_PRIMARY = Jid.of("249533976023044:0@lid");

    @Test
    @DisplayName("a msg from the peer's LID decrypts with the session this client opened by phone")
    void replyByLidDecryptsWithTheSessionOpenedByPhone() {
        var selfStore = MessageFixtures.temporaryStore(SELF_BARE, null);
        var peerStore = MessageFixtures.temporaryStore(PEER_BARE, null);
        // This client starts the conversation: the session is opened by the peer's phone number
        TestSignalSession.establishSession(selfStore, PEER_PRIMARY, peerStore);
        var selfEncryption = encryption(selfStore);
        var outbound = selfEncryption.encryptForDevice(PEER_PRIMARY,
                LinkedMessageContainerSpec.encode(LinkedMessageContainer.of("oi")));
        decryption(peerStore).decryptFromDevice(outbound.ciphertext(), SELF_PRIMARY, MessageEncryptionType.PKMSG);

        // The peer answers on the established session (msg), and the relay delivers it by LID
        var reply = encryption(peerStore).encryptForDevice(SELF_PRIMARY,
                LinkedMessageContainerSpec.encode(LinkedMessageContainer.of("resposta pelo LID")));
        assertEquals(MessageEncryptionType.MSG, reply.type(), "the reply rides the established session");
        var inbound = new StanzaBuilder()
                .description("message")
                .attribute("id", "3EB0LID0001")
                .attribute("from", PEER_LID_PRIMARY)
                .attribute("sender_pn", PEER_BARE)
                .attribute("t", 1700000000L)
                .attribute("type", "text")
                .content(new StanzaBuilder()
                        .description("enc")
                        .attribute("v", "2")
                        .attribute("type", "msg")
                        .content(reply.ciphertext())
                        .build())
                .build();
        var receiver = new ChatMessageReceiver(selfStore, decryption(selfStore),
                TestWamService.create(TestWhatsAppClient.create().withStore(selfStore)));

        var info = receiver.receive(inbound, PEER_LID_PRIMARY);

        assertNotNull(info);
        var text = assertInstanceOf(ExtendedTextMessage.class, info.message().content());
        assertEquals("resposta pelo LID", text.text().orElseThrow());
        assertEquals(PEER_LID_BARE, selfStore.contactStore().findLidByPhone(PEER_BARE).orElseThrow(),
                "the pair from sender_pn is learned");
        assertTrue(selfStore.signalStore().findSessionByAddress(PEER_LID_PRIMARY.toSignalAddress()).isPresent(),
                "the session now lives under the LID address");
    }

    private static MessageEncryption encryption(com.github.auties00.cobalt.store.linked.LinkedWhatsAppStore store) {
        return new MessageEncryption(store, new SignalSessionCipher(store.signalStore()),
                new SignalGroupCipher(store.signalStore()), new SignalCryptoLocks());
    }

    private static MessageDecryption decryption(com.github.auties00.cobalt.store.linked.LinkedWhatsAppStore store) {
        return new MessageDecryption(store, new SignalSessionCipher(store.signalStore()),
                new SignalGroupCipher(store.signalStore()), new SignalCryptoLocks());
    }
}
