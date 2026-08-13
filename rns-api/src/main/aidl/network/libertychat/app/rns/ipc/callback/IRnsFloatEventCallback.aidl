// Observer callback for StateFlow<Float> (RnsNomadnet.nomadnetDownloadProgressFlow).
package network.libertychat.app.rns.ipc.callback;

oneway interface IRnsFloatEventCallback {
    void onFloat(float value);
}
