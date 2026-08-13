// Observer callback for nullable-String-emitting StateFlow surfaces:
//   - RnsTelephony.remoteIdentity
package network.libertychat.app.rns.ipc.callback;

oneway interface IRnsNullableStringEventCallback {
    void onString(@nullable String value);
}
