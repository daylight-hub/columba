// Observer callback for SharedFlow<PropagationState> (RnsLxmf.propagationStateFlow).
package network.libertychat.app.rns.ipc.callback;

import network.libertychat.app.rns.api.model.PropagationState;

oneway interface IRnsPropagationStateCallback {
    void onPropagationState(in PropagationState state);
}
