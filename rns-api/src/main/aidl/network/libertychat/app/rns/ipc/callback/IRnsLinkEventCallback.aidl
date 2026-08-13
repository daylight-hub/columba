// Observer callback for Flow<LinkEvent> (RnsCore.observeLinks).
package network.libertychat.app.rns.ipc.callback;

import network.libertychat.app.rns.api.model.LinkEvent;

oneway interface IRnsLinkEventCallback {
    void onLinkEvent(in LinkEvent event);
}
