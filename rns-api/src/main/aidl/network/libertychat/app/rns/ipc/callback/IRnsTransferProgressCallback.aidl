package network.libertychat.app.rns.ipc.callback;

import network.libertychat.app.rns.api.model.TransferProgressUpdate;

oneway interface IRnsTransferProgressCallback {
    void onTransferProgress(in TransferProgressUpdate update);
}
