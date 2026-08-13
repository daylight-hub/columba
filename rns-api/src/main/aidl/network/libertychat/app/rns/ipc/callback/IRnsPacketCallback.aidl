// Observer callback for Flow<ReceivedPacket> (RnsCore.observePackets).
package network.libertychat.app.rns.ipc.callback;

import network.libertychat.app.rns.api.model.ReceivedPacket;

oneway interface IRnsPacketCallback {
    void onPacket(in ReceivedPacket packet);
}
