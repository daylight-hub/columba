package network.libertychat.app.rns.backend.kt

import network.libertychat.app.rns.api.model.ConversationLinkResult
import network.libertychat.app.rns.api.model.DeliveryMethod
import network.libertychat.app.rns.api.model.DeliveryStatusUpdate
import network.libertychat.app.rns.api.model.DiscoveredInterface
import network.libertychat.app.rns.api.model.FailedInterface
import network.libertychat.app.rns.api.model.IconAppearance
import network.libertychat.app.rns.api.model.MessageReceipt
import network.libertychat.app.rns.api.model.PropagationState
import network.libertychat.app.rns.api.model.ReceivedMessage
import network.libertychat.app.rns.api.model.VoiceCallState

/** Type aliases to disambiguate reticulum-kt types from Columba model types. */
internal typealias NativeIdentity = network.reticulum.identity.Identity
internal typealias NativeDestination = network.reticulum.destination.Destination
internal typealias NativeDestinationType = network.reticulum.common.DestinationType
internal typealias NativeDeliveryMethod = network.reticulum.lxmf.DeliveryMethod
