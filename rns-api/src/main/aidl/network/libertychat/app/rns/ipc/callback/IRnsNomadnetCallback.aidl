// Fire-once callback returning the IRnsNomadnet sub-interface binder. See
// IRnsCoreCallback.aidl for the usage pattern.
package network.libertychat.app.rns.ipc.callback;

import network.libertychat.app.rns.ipc.IRnsNomadnet;

oneway interface IRnsNomadnetCallback {
    void onNomadnet(IRnsNomadnet service);
}
