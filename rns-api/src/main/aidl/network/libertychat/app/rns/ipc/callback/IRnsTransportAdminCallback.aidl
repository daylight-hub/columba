// Fire-once callback returning the IRnsTransportAdmin sub-interface binder.
// See IRnsCoreCallback.aidl for the usage pattern.
package network.libertychat.app.rns.ipc.callback;

import network.libertychat.app.rns.ipc.IRnsTransportAdmin;

oneway interface IRnsTransportAdminCallback {
    void onTransportAdmin(IRnsTransportAdmin service);
}
