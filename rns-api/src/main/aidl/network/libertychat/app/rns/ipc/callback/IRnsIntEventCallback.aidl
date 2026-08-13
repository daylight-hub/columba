// Observer callback for Int-emitting StateFlow surfaces:
//   - RnsTelephony.activeProfileCode
package network.columba.app.rns.ipc.callback;

oneway interface IRnsIntEventCallback {
    void onInt(int value);
}
