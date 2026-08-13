// Fire-once callback returning the IRnsLxmf sub-interface binder. See
// IRnsCoreCallback.aidl for the usage pattern.
package network.libertychat.app.rns.ipc.callback;

import network.libertychat.app.rns.ipc.IRnsLxmf;

oneway interface IRnsLxmfCallback {
    void onLxmf(IRnsLxmf service);
}
