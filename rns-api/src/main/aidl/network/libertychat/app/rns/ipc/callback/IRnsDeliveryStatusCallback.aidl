// Observer callback for Flow<DeliveryStatusUpdate> (RnsLxmf.observeDeliveryStatus).
package network.libertychat.app.rns.ipc.callback;

import network.libertychat.app.rns.api.model.DeliveryStatusUpdate;

oneway interface IRnsDeliveryStatusCallback {
    void onDeliveryStatus(in DeliveryStatusUpdate update);
}
