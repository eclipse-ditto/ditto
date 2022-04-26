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
package org.eclipse.ditto.internal.models.acks;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.AcknowledgementRequestDuplicateCorrelationIdException;
import org.eclipse.ditto.internal.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.InvalidActorNameException;
import akka.actor.Props;
import akka.japi.Pair;

/**
 * Starting an acknowledgement forwarder actor is more complex than simply call {@code actorOf}.
 * Thus starting logic is worth to be handled within its own class.
 *
 * @since 1.1.0
 */
final class AcknowledgementForwarderActorStarter implements Supplier<Optional<ActorRef>> {

    private static final String PREFIX_COUNTER_SEPARATOR = "#";

    private static final String LIVE_CHANNEL = "live";

    private final ActorRefFactory actorRefFactory;
    private final ActorRef parent;
    private final ActorRef ackRequester;
    private final EntityId entityId;
    private final Signal<?> signal;
    private final DittoHeaders dittoHeaders;
    private final AcknowledgementConfig acknowledgementConfig;
    private final Set<AcknowledgementRequest> acknowledgementRequests;

    private AcknowledgementForwarderActorStarter(final ActorRefFactory actorRefFactory,
            final ActorRef parent,
            final ActorRef ackRequester,
            final EntityId entityId,
            final Signal<?> signal,
            final AcknowledgementConfig acknowledgementConfig,
            final Predicate<AcknowledgementLabel> isAckLabelAllowed) {

        this.actorRefFactory = checkNotNull(actorRefFactory, "actorRefFactory");
        this.parent = parent;
        this.ackRequester = ackRequester;
        this.entityId = checkNotNull(entityId, "entityId");
        this.signal = checkNotNull(signal, "signal");
        dittoHeaders = signal.getDittoHeaders();
        this.acknowledgementConfig = checkNotNull(acknowledgementConfig, "acknowledgementConfig");
        acknowledgementRequests = dittoHeaders.getAcknowledgementRequests()
                .stream()
                .filter(request -> isAckLabelAllowed.test(request.getLabel()))
                .collect(Collectors.toSet());
    }

    /**
     * Returns an instance of {@code ActorStarter}.
     *
     * @param actorRefFactory the factory to start the forwarder actor in.
     * @param parent the parent of the forwarder actor.
     * @param ackRequester the actor which should receive the forwarded acknowledgements.
     * @param entityId is used for the NACKs if the forwarder actor cannot be started.
     * @param signal the signal for which the forwarder actor is to start.
     * @param acknowledgementConfig provides configuration setting regarding acknowledgement handling.
     * @param isAckLabelAllowed predicate for whether an ack label is allowed for publication at this channel.
     * "live-response" is always allowed.
     * @return a means to start an acknowledgement forwarder actor.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static AcknowledgementForwarderActorStarter getInstance(final ActorRefFactory actorRefFactory,
            final ActorRef parent,
            final ActorRef ackRequester,
            final EntityId entityId,
            final Signal<?> signal,
            final AcknowledgementConfig acknowledgementConfig,
            final Predicate<AcknowledgementLabel> isAckLabelAllowed) {

        return new AcknowledgementForwarderActorStarter(actorRefFactory, parent, ackRequester, entityId, signal,
                acknowledgementConfig,
                // live-response is always allowed
                isAckLabelAllowed.or(DittoAcknowledgementLabel.LIVE_RESPONSE::equals));
    }

    /**
     * Retrieve the acknowledgement requests allowed for this actor starter.
     * Any acknowledgement request in the original signal are discarded if this channel is not allowed to fulfill them.
     *
     * @return the meaningful acknowledgement requests.
     */
    public Set<AcknowledgementRequest> getAllowedAckRequests() {
        return acknowledgementRequests;
    }

    @Override
    public Optional<ActorRef> get() {
        ActorRef actorRef = null;
        if (hasEffectiveAckRequests(signal, acknowledgementRequests)) {
            try {
                actorRef = startAckForwarderActor(dittoHeaders);
            } catch (final InvalidActorNameException e) {
                // In case that the actor with that name already existed, the correlation-id was already used recently:
                declineAllNonDittoAckRequests(getDuplicateCorrelationIdException(e));
            }
        }
        return Optional.ofNullable(actorRef);
    }

    /**
     * Start an acknowledgement forwarder.
     * Always succeeds.
     *
     * @return the new correlation ID if an ack forwarder started, or an empty optional if the ack forwarder did not
     * start because no acknowledgement was requested.
     */
    public Optional<String> getConflictFree() {
        if (hasEffectiveAckRequests(signal, acknowledgementRequests)) {
            final DittoHeadersBuilder<?, ?> builder = dittoHeaders.toBuilder()
                    .acknowledgementRequests(acknowledgementRequests);
            final Pair<String, Integer> prefixPair = parseCorrelationId(dittoHeaders);
            final String prefix = prefixPair.first();
            int counter = prefixPair.second();
            String correlationId = dittoHeaders.getCorrelationId().orElse(prefix);
            while (true) {
                try {
                    builder.correlationId(correlationId);
                    startAckForwarderActor(builder.build());
                    return Optional.of(correlationId);
                } catch (final InvalidActorNameException e) {
                    // generate a new ID
                    correlationId = joinPrefixAndCounter(prefix, ++counter);
                }
            }
        } else {
            return Optional.empty();
        }
    }

    private String joinPrefixAndCounter(final String prefix, final int counter) {
        return String.format("%s%s%d", prefix, PREFIX_COUNTER_SEPARATOR, counter);
    }

    private Pair<String, Integer> parseCorrelationId(final DittoHeaders dittoHeaders) {
        final Optional<String> providedCorrelationId = dittoHeaders.getCorrelationId();
        if (providedCorrelationId.isPresent()) {
            final String correlationId = providedCorrelationId.get();
            final int separatorIndex = correlationId.lastIndexOf(PREFIX_COUNTER_SEPARATOR);
            if (separatorIndex >= 0 && isNumber(correlationId, separatorIndex + 1)) {
                final String prefix = correlationId.substring(0, separatorIndex);
                final String number = correlationId.substring(separatorIndex + 1);
                try {
                    return Pair.create(prefix, Integer.valueOf(number));
                } catch (final NumberFormatException e) {
                    return Pair.create(prefix, -1);
                }
            } else {
                return Pair.create(correlationId, -1);
            }
        }
        return Pair.create(UUID.randomUUID().toString(), -1);
    }

    private ActorRef startAckForwarderActor(final DittoHeaders dittoHeaders) {
        final Props props = AcknowledgementForwarderActor.props(ackRequester, dittoHeaders,
                acknowledgementConfig.getForwarderFallbackTimeout());
        return actorRefFactory.actorOf(props, AcknowledgementForwarderActor.determineActorName(dittoHeaders));
    }

    private DittoRuntimeException getDuplicateCorrelationIdException(final Throwable cause) {
        return AcknowledgementRequestDuplicateCorrelationIdException
                .newBuilder(dittoHeaders.getCorrelationId().orElse("?"))
                .dittoHeaders(dittoHeaders)
                .cause(cause)
                .build();
    }

    private void declineAllNonDittoAckRequests(final DittoRuntimeException dittoRuntimeException) {
        // answer NACKs for all AcknowledgementRequests with labels which were not Ditto-defined
        acknowledgementRequests.stream()
                .map(AcknowledgementRequest::getLabel)
                .filter(Predicate.not(DittoAcknowledgementLabel::contains))
                .map(label -> getNack(label, dittoRuntimeException))
                .forEach(nack -> ackRequester.tell(nack, parent));
    }

    private Acknowledgement getNack(final AcknowledgementLabel label,
            final DittoRuntimeException dittoRuntimeException) {

        return Acknowledgement.of(label, entityId, dittoRuntimeException.getHttpStatus(), dittoHeaders,
                dittoRuntimeException.toJson());
    }

    static boolean isNotTwinPersistedOrLiveResponse(final AcknowledgementRequest request) {
        return isNotLiveResponse(request) && isNotTwinPersisted(request);
    }

    static boolean isNotTwinPersisted(final AcknowledgementRequest request) {
        return !DittoAcknowledgementLabel.TWIN_PERSISTED.equals(request.getLabel());
    }

    static boolean isNotLiveResponse(final AcknowledgementRequest request) {
        return !DittoAcknowledgementLabel.LIVE_RESPONSE.equals(request.getLabel());
    }

    static boolean isLiveSignal(final Signal<?> signal) {
        return signal.getDittoHeaders().getChannel().stream().anyMatch(LIVE_CHANNEL::equals);
    }

    static boolean hasEffectiveAckRequests(final Signal<?> signal, final Set<AcknowledgementRequest> ackRequests) {
        final boolean isLiveSignal = isLiveSignal(signal);
        if (signal instanceof ThingEvent && !isLiveSignal) {
            return ackRequests.stream()
                    .anyMatch(AcknowledgementForwarderActorStarter::isNotTwinPersistedOrLiveResponse);
        } else if (signal instanceof MessageCommand || (isLiveSignal && signal instanceof ThingCommand)) {
            return ackRequests.stream().anyMatch(AcknowledgementForwarderActorStarter::isNotTwinPersisted) ||
                    signal.getDittoHeaders().isResponseRequired();
        } else if (signal instanceof PolicyAnnouncement) {
            return !ackRequests.isEmpty();
        } else {
            return false;
        }
    }

    private static boolean isNumber(final String string, final int startIndex) {
        if (startIndex > string.length()) {
            return false;
        }
        final char firstChar = string.charAt(startIndex);
        if (!Character.isDigit(firstChar)) {
            if (firstChar != '-' || startIndex + 1 > string.length()) {
                // singular "-" is not a number.
                return false;
            }
        }
        for (int i = startIndex + 1; i < string.length(); ++i) {
            if (!Character.isDigit(string.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
