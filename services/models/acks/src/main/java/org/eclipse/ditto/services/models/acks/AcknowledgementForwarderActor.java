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
package org.eclipse.ditto.services.models.acks;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.acks.Acknowledgement;
import org.eclipse.ditto.signals.acks.AcknowledgementCorrelationIdMissingException;
import org.eclipse.ditto.signals.acks.AcknowledgementRequestDuplicateCorrelationIdException;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.InvalidActorNameException;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;

/**
 * Actor which is created with a {@code acknowledgementRequester} which requested to receive custom
 * {@link Acknowledgement}s.
 * This actor is started with its Actor name containing the {@code correlation-id} of the signal for which
 * Acknowledgements were requested right at the "edge" were custom Acknowledgements are received.
 */
public final class AcknowledgementForwarderActor extends AbstractActor {

    private static final String ACTOR_NAME_PREFIX = "ackForwarder-";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ActorRef acknowledgementRequester;
    private final String correlationId;

    @SuppressWarnings("unused")
    private AcknowledgementForwarderActor(
            final ActorRef acknowledgementRequester,
            final DittoHeaders dittoHeaders,
            final Duration defaultTimeout) {

        this.acknowledgementRequester = acknowledgementRequester;
        this.correlationId = dittoHeaders.getCorrelationId()
                .orElseGet(() ->
                        // fall back using the actor name which also contains the correlation-id
                        getSelf().path().name().replace(ACTOR_NAME_PREFIX, "")
                );

        getContext().setReceiveTimeout(dittoHeaders.getTimeout().orElse(defaultTimeout));
    }

    /**
     * Determines the actor name to use for the passed {@code dittoHeaders} of a {@code Signal} which contained
     * {@code AcknowledgementRequest}s.
     *
     * @param dittoHeaders the headers to extract the correlation-id from which is used as part of the actor name.
     * @return the actor name to use.
     * @throws AcknowledgementCorrelationIdMissingException if no {@code correlation-id} was present in the passed
     * {@code dittoHeaders}
     */
    public static String determineActorName(final DittoHeaders dittoHeaders) {
        return ACTOR_NAME_PREFIX + dittoHeaders.getCorrelationId().orElseThrow(() ->
                AcknowledgementCorrelationIdMissingException.newBuilder()
                        .dittoHeaders(dittoHeaders)
                        .build());
    }

    /**
     * Creates and starts an {@code AcknowledgementForwarderActor} actor in the passed {@code context} using the passed
     * arguments.
     * The actor's name is derived from the {@code correlation-id} extracted via the passed {@code dittoHeaders} and
     * in case that an Actor with this name already exists, a negative {@code Acknowledgement} (NACK) will be sent back
     * to the {@code context.getSender()} containing the payload of a
     * {@code AcknowledgementRequestDuplicateCorrelationIdException}.
     *
     * @param context the context ({@code getContext()} of the Actor to start the AcknowledgementForwarderActor in.
     * @param entityId the entityId of the {@code Signal} which requested the Acknowledgements.
     * @param dittoHeaders the dittoHeaders of the {@code Signal} which requested the Acknowledgements.
     * @param acknowledgementConfig the AcknowledgementConfig to use for looking up config values.
     * @return the optionally created ActorRef - empty when either no AcknowledgementRequests were contained in the
     * {@code dittoHeaders} or when a conflict caused by a re-used {@code correlation-id} was detected.
     */
    public static Optional<ActorRef> startAcknowledgementForwarder(final ActorContext context, final EntityId entityId,
            final DittoHeaders dittoHeaders, final AcknowledgementConfig acknowledgementConfig) {

        return startAcknowledgementForwarder(context, context.getSender(), context.getSelf(), entityId, dittoHeaders,
                acknowledgementConfig);
    }

    /**
     * Creates and starts an {@code AcknowledgementForwarderActor} actor in the passed {@code context} using the passed
     * arguments.
     * The actor's name is derived from the {@code correlation-id} extracted via the passed {@code dittoHeaders} and
     * in case that an Actor with this name already exists, a negative {@code Acknowledgement} (NACK) will be sent back
     * to the {@code context.getSender()} containing the payload of a
     * {@code AcknowledgementRequestDuplicateCorrelationIdException}.
     *
     * @param actorRefFactory the factory to create/start the AcknowledgementForwarderActor in.
     * @param sender the sender ActorRef to use who shall receive the Acknowlegements.
     * @param self the self ActorRef to use.
     * @param entityId the entityId of the {@code Signal} which requested the Acknowledgements.
     * @param dittoHeaders the dittoHeaders of the {@code Signal} which requested the Acknowledgements.
     * @param acknowledgementConfig the AcknowledgementConfig to use for looking up config values.
     * @return the optionally created ActorRef - empty when either no AcknowledgementRequests were contained in the
     * {@code dittoHeaders} or when a conflict caused by a re-used {@code correlation-id} was detected.
     */
    static Optional<ActorRef> startAcknowledgementForwarder(final ActorRefFactory actorRefFactory,
            final ActorRef sender,
            final ActorRef self,
            final EntityId entityId,
            final DittoHeaders dittoHeaders,
            final AcknowledgementConfig acknowledgementConfig) {

        final Set<AcknowledgementRequest> acknowledgementRequests = dittoHeaders.getAcknowledgementRequests();
        if (!acknowledgementRequests.isEmpty()) {
            try {
                return Optional.of(actorRefFactory.actorOf(
                        AcknowledgementForwarderActor.props(sender, dittoHeaders,
                                acknowledgementConfig.getForwarderFallbackTimeout()),
                        AcknowledgementForwarderActor.determineActorName(dittoHeaders)
                ));
            } catch (final InvalidActorNameException e) {
                // in case that the actor with that name already existed, the correlation-id was already used recently:
                final AcknowledgementRequestDuplicateCorrelationIdException duplicateCorrelationIdException =
                        AcknowledgementRequestDuplicateCorrelationIdException
                                .newBuilder(dittoHeaders.getCorrelationId().orElse("?"))
                                .dittoHeaders(dittoHeaders)
                                .cause(e)
                                .build();

                // answer NACKs for all AcknowledgementRequest labels which were not Ditto defined,
                //  so for all custom labels:
                acknowledgementRequests.stream()
                        .map(AcknowledgementRequest::getLabel)
                        .filter(Predicate.not(DittoAcknowledgementLabel::contains))
                        .forEach(ackLabel -> {
                            final Acknowledgement acknowledgement = Acknowledgement.of(ackLabel, entityId,
                                    duplicateCorrelationIdException.getStatusCode(), dittoHeaders,
                                    duplicateCorrelationIdException.toJson());
                            sender.tell(acknowledgement, self);
                        });
            }
        }
        return Optional.empty();
    }

    /**
     * Creates Akka configuration object Props for this AcknowledgementForwarderActor.
     *
     * @param acknowledgementRequester the ActorRef of the original sender who requested the Acknowledgements.
     * @param dittoHeaders the DittoHeaders of the Signal which contained the request for Acknowledgements.
     * @param defaultTimeout the default timeout to apply when {@code dittoHeaders} did not contain a specific timeout.
     * @return the Akka configuration Props object.
     */
    static Props props(final ActorRef acknowledgementRequester, final DittoHeaders dittoHeaders,
            final Duration defaultTimeout) {

        return Props.create(AcknowledgementForwarderActor.class, acknowledgementRequester, dittoHeaders,
                defaultTimeout);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Acknowledgement.class, this::handleAcknowledgement)
                .match(ReceiveTimeout.class, this::handleReceiveTimeout)
                .matchAny(m -> log.warning("Unexpected message: <{}>", m))
                .build();
    }

    private void handleAcknowledgement(final Acknowledgement acknowledgement) {
        log.withCorrelationId(acknowledgement)
                .debug("Received Acknowledgement, forwarding to acknowledgementRequestsSender: <{}>", acknowledgement);
        acknowledgementRequester.tell(acknowledgement, getSender());
    }

    private void handleReceiveTimeout(final ReceiveTimeout receiveTimeout) {
        log.withCorrelationId(correlationId)
                .debug("Timed out waiting for requested Acknowledgements, stopping Actor..");
        getContext().stop(getSelf());
    }
}
