// Observer callback for Int-emitting StateFlow surfaces:
//   - RnsTelephony.activeProfileCode
package network.libertychat.app.rns.ipc.callback;

oneway interface IRnsIntEventCallback {
    void onInt(int value);
}
