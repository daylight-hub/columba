// Fire-once callback returning the IRnsTelephony sub-interface binder. See
// IRnsCoreCallback.aidl for the usage pattern.
package network.libertychat.app.rns.ipc.callback;

import network.libertychat.app.rns.ipc.IRnsTelephony;

oneway interface IRnsTelephonyCallback {
    void onTelephony(IRnsTelephony service);
}
