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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Optional;

import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.AcknowledgementCorrelationIdMissingException;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;

/**
 * Actor which is created with an acknowledgement requester actor reference that requested to receive custom
 * {@link Acknowledgement}s.
 * This actor is started with its actor name containing the {@code correlation-id} of the signal for which
 * acknowledgements were requested right at the "edge" were custom acknowledgements are received.
 *
 * @since 1.1.0
 */
public final class AcknowledgementForwarderActor extends AbstractActor {

    /**
     * Prefix of the acknowledgement forwarder actor's name.
     */
    static final String ACTOR_NAME_PREFIX = "ackForwarder-";

    private final ActorRef acknowledgementRequester;
    private final String correlationId;
    private final DittoDiagnosticLoggingAdapter log;

    @SuppressWarnings("unused")
    private AcknowledgementForwarderActor(final ActorRef acknowledgementRequester, final DittoHeaders dittoHeaders,
            final Duration defaultTimeout) {

        this.acknowledgementRequester = acknowledgementRequester;
        correlationId = dittoHeaders.getCorrelationId()
                .orElseGet(() ->
                        // fall back using the actor name which also contains the correlation-id
                        getSelf().path().name().replace(ACTOR_NAME_PREFIX, "")
                );
        log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

        getContext().setReceiveTimeout(dittoHeaders.getTimeout().orElse(defaultTimeout));
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
                .matchAny(m -> log.warning("Received unexpected message: <{}>", m))
                .build();
    }

    private void handleAcknowledgement(final Acknowledgement acknowledgement) {
        log.withCorrelationId(acknowledgement)
                .debug("Received Acknowledgement, forwarding to original requester: <{}>", acknowledgement);
        acknowledgementRequester.tell(acknowledgement, getSender());
    }

    private void handleReceiveTimeout(final ReceiveTimeout receiveTimeout) {
        log.withCorrelationId(correlationId)
                .debug("Timed out waiting for requested acknowledgements, stopping myself ...");
        getContext().stop(getSelf());
    }

    /**
     * Determines the actor name to use for the passed DittoHeaders of a Signal which contained AcknowledgementRequests.
     *
     * @param dittoHeaders the headers to extract the correlation-id from which is used as part of the actor name.
     * @return the actor name to use.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @throws AcknowledgementCorrelationIdMissingException if no {@code correlation-id} was present in the passed
     * {@code dittoHeaders}.
     */
    public static String determineActorName(final DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "dittoHeaders");
        final String correlationId = dittoHeaders.getCorrelationId()
                .orElseThrow(() -> AcknowledgementCorrelationIdMissingException.newBuilder()
                        .dittoHeaders(dittoHeaders)
                        .build());
        return ACTOR_NAME_PREFIX + URLEncoder.encode(correlationId, Charset.defaultCharset());
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
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Optional<ActorRef> startAcknowledgementForwarder(final akka.actor.ActorContext context,
            final EntityIdWithType entityId,
            final DittoHeaders dittoHeaders,
            final AcknowledgementConfig acknowledgementConfig) {

        final AcknowledgementForwarderActorStarter starter =
                AcknowledgementForwarderActorStarter.getInstance(context, entityId, dittoHeaders,
                        acknowledgementConfig);
        return starter.get();
    }

}
