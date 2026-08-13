// Observer callback for Flow<AnnounceEvent> (RnsCore.observeAnnounces).
package network.libertychat.app.rns.ipc.callback;

import network.libertychat.app.rns.api.model.AnnounceEvent;

oneway interface IRnsAnnounceCallback {
    void onAnnounce(in AnnounceEvent event);
}
