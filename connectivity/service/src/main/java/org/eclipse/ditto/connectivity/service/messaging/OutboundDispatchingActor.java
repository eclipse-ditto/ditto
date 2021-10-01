/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelNotDeclaredException;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.api.InboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.internal.models.acks.AcknowledgementForwarderActor;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * This Actor makes the decision whether to dispatch outbound signals and their acknowledgements or to drop them.
 */
final class OutboundDispatchingActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "outboundDispatching";

    private final DittoDiagnosticLoggingAdapter logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final OutboundMappingSettings settings;
    private final ActorRef outboundMappingProcessorActor;
    private final ActorRef proxyActor;

    @SuppressWarnings("unused")
    private OutboundDispatchingActor(final OutboundMappingSettings settings,
            final ActorRef outboundMappingProcessorActor, final ActorRef proxyActor) {
        this.settings = settings;
        this.outboundMappingProcessorActor = outboundMappingProcessorActor;
        this.proxyActor = proxyActor;
    }

    static Props props(final OutboundMappingSettings settings, final ActorRef outboundMappingProcessorActor,
            final ActorRef proxyActor) {
        return Props.create(OutboundDispatchingActor.class, settings, outboundMappingProcessorActor, proxyActor);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(InboundSignal.class, this::inboundSignal)
                .match(CommandResponse.class, this::forwardWithoutCheck)
                .match(SubscriptionEvent.class, this::forwardWithoutCheck)
                .match(DittoRuntimeException.class, this::forwardWithoutCheck)
                .match(Signal.class, this::handleSignal)
                .matchAny(message -> logger.warning("Unknown message: <{}>", message))
                .build();
    }

    private void inboundSignal(final InboundSignal inboundSignal) {
        handleInboundResponseOrAcknowledgement(inboundSignal.getSignal());
    }

    private void forwardWithoutCheck(final Object message) {
        outboundMappingProcessorActor.tell(message, getSender());
    }

    private void handleSignal(final Signal<?> signal) {
        if (settings.getConnectionId().toString().equals(signal.getDittoHeaders().getOrigin().orElse(null))) {
            logDroppedSignal(signal, signal.getType(), "Was sent by myself.");
            return;
        }
        final List<Target> subscribedAndAuthorizedTargets = settings.getSignalFilter().filter(signal);

        if (subscribedAndAuthorizedTargets.isEmpty()) {
            logDroppedSignal(signal, signal.getType(), "No subscribed and authorized targets present");
            // issue weak acks here as the signal will not reach OutboundMappingProcessorActor
            issueWeakAcknowledgements(signal, getSender());
            return;
        }

        final Optional<EntityId> entityIdOptional = WithEntityId.getEntityIdOfType(EntityId.class, signal);
        final Signal<?> signalToForward;
        if (entityIdOptional.isPresent()) {
            signalToForward = adjustSignalAndStartAckForwarder(signal, entityIdOptional.get());
        } else {
            signalToForward = signal;
        }

        logger.debug("Forwarding signal <{}> to client actor with targets: {}.", signalToForward.getType(),
                subscribedAndAuthorizedTargets);

        final OutboundSignal outbound =
                OutboundSignalFactory.newOutboundSignal(signalToForward, subscribedAndAuthorizedTargets);

        outboundMappingProcessorActor.tell(outbound, getSender());
    }

    private void logDroppedSignal(final WithDittoHeaders withDittoHeaders, final String type, final String reason) {
        logger.withCorrelationId(withDittoHeaders).debug("Signal ({}) dropped: {}", type, reason);
    }

    private void issueWeakAcknowledgements(final Signal<?> signal, final ActorRef sender) {
        OutboundMappingProcessorActor.issueWeakAcknowledgements(signal,
                this::isSourceDeclaredOrTargetIssuedAck,
                sender);
    }

    private void denyNonSourceDeclaredAck(final Acknowledgement ack) {
        getSender().tell(AcknowledgementLabelNotDeclaredException.of(ack.getLabel(), ack.getDittoHeaders()),
                ActorRef.noSender());
    }

    private boolean isNotSourceDeclaredAck(final Acknowledgement acknowledgement) {
        return !settings.getSourceDeclaredAcks().contains(acknowledgement.getLabel());
    }

    private boolean isSourceDeclaredOrTargetIssuedAck(final AcknowledgementLabel label) {
        return settings.getSourceDeclaredAcks().contains(label) || settings.getTargetIssuedAcks().contains(label);
    }

    private Signal<?> adjustSignalAndStartAckForwarder(final Signal<?> signal, final EntityId entityId) {
        final Collection<AcknowledgementRequest> ackRequests = signal.getDittoHeaders().getAcknowledgementRequests();
        if (ackRequests.isEmpty()) {
            return signal;
        }
        final Predicate<AcknowledgementLabel> isSourceDeclaredAck = settings.getSourceDeclaredAcks()::contains;
        final Set<AcknowledgementLabel> targetIssuedAcks = settings.getTargetIssuedAcks();
        final boolean hasSourceDeclaredAcks = ackRequests.stream()
                .map(AcknowledgementRequest::getLabel)
                .anyMatch(isSourceDeclaredAck);
        if (hasSourceDeclaredAcks) {
            // start ackregator for source declared acks
            return AcknowledgementForwarderActor.startAcknowledgementForwarder(getContext(),
                    entityId,
                    signal,
                    settings.getAcknowledgementConfig(),
                    this::isSourceDeclaredOrTargetIssuedAck
            );
        } else {
            // no need to start ackregator for target-issued acks; they go to the sender directly
            return signal.setDittoHeaders(signal.getDittoHeaders().toBuilder()
                    .acknowledgementRequests(ackRequests.stream()
                            .filter(request -> targetIssuedAcks.contains(request.getLabel()))
                            .collect(Collectors.toList()))
                    .build());
        }
    }

    private void handleInboundResponseOrAcknowledgement(final WithDittoHeaders responseOrAck) {
        if (responseOrAck instanceof Acknowledgement) {
            final Acknowledgement ack = (Acknowledgement) responseOrAck;
            if (isNotSourceDeclaredAck(ack)) {
                denyNonSourceDeclaredAck(ack);
                return;
            }
        }

        final ActorContext context = getContext();
        final Consumer<ActorRef> action = forwarder -> {
            if (responseOrAck instanceof ThingQueryCommandResponse && isLiveResponse(responseOrAck)) {
                // forward live command responses to concierge to filter response
                proxyActor.tell(responseOrAck, getSender());
            } else {
                forwarder.forward(responseOrAck, context);
            }
        };
        final Runnable emptyAction = () -> {
            final String template = "No AcknowledgementForwarderActor found, forwarding to concierge: <{}>";
            if (logger.isDebugEnabled()) {
                logger.withCorrelationId(responseOrAck).debug(template, responseOrAck);
            } else {
                logger.withCorrelationId(responseOrAck).info(template, responseOrAck.getClass().getCanonicalName());
            }
            settings.getProxyActor().tell(responseOrAck, ActorRef.noSender());
        };

        context.findChild(AcknowledgementForwarderActor.determineActorName(responseOrAck.getDittoHeaders()))
                .ifPresentOrElse(action, emptyAction);
    }

    private boolean isLiveResponse(final WithDittoHeaders response) {
        return response.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
    }

}
