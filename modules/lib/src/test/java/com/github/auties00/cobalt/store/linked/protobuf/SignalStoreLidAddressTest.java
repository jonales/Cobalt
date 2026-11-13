package com.github.auties00.cobalt.store.linked.protobuf;

import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.wire.core.jid.Jid;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.state.SignalSessionRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A peer with a known LID keeps one session and one identity per device, keyed by the LID. */
@DisplayName("Signal store: peer addresses by phone and by LID")
class SignalStoreLidAddressTest {

    private static final Jid SELF = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid PEER_PN = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid PEER_LID = Jid.of("249533976023044@lid");
    private static final Jid PEER_PN_DEVICE = Jid.of("12025550100:21@s.whatsapp.net");
    private static final Jid PEER_LID_DEVICE = Jid.of("249533976023044:21@lid");

    private static SignalIdentityPublicKey key(int seed) {
        var bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) seed);
        return SignalIdentityPublicKey.ofDirect(bytes);
    }

    @Test
    @DisplayName("with the pair known, a record written by phone is read by LID and by phone")
    void knownPairSharesOneRecord() {
        var store = MessageFixtures.temporaryStore(SELF, null);
        store.contactStore().registerLidMapping(PEER_PN, PEER_LID);

        store.signalStore().saveIdentity(PEER_PN_DEVICE.toSignalAddress(), key(7));
        store.signalStore().addSession(PEER_PN_DEVICE.toSignalAddress(), new SignalSessionRecord());

        assertTrue(store.signalStore().findSessionByAddress(PEER_LID_DEVICE.toSignalAddress()).isPresent());
        assertTrue(store.signalStore().findSessionByAddress(PEER_PN_DEVICE.toSignalAddress()).isPresent());
        assertArrayEquals(key(7).toEncodedPoint(),
                store.signalStore().findIdentityByAddress(PEER_LID_DEVICE.toSignalAddress()).orElseThrow().toEncodedPoint());
    }

    @Test
    @DisplayName("a record written by phone before the pair was known is found by LID afterwards")
    void recordWrittenBeforeThePairMigratesToTheLid() {
        var store = MessageFixtures.temporaryStore(SELF, null);
        store.signalStore().saveIdentity(PEER_PN_DEVICE.toSignalAddress(), key(9));
        store.signalStore().addSession(PEER_PN_DEVICE.toSignalAddress(), new SignalSessionRecord());

        store.contactStore().registerLidMapping(PEER_PN, PEER_LID);

        assertTrue(store.signalStore().findSessionByAddress(PEER_LID_DEVICE.toSignalAddress()).isPresent(),
                "lookup by LID finds the session opened by phone");
        assertArrayEquals(key(9).toEncodedPoint(),
                store.signalStore().findIdentityByAddress(PEER_LID_DEVICE.toSignalAddress()).orElseThrow().toEncodedPoint());
        assertTrue(store.signalStore().findSessionByAddress(PEER_PN_DEVICE.toSignalAddress()).isPresent(),
                "and the phone address still resolves to it");
    }
}
