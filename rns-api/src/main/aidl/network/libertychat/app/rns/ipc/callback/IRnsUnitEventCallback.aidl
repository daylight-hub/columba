// Observer callback for ping-only flows (RnsTransportAdmin.interfaceStatusChanged
// SharedFlow<Unit>). Carries no payload — the emission itself is the signal.
package network.libertychat.app.rns.ipc.callback;

oneway interface IRnsUnitEventCallback {
    void onEvent();
}
