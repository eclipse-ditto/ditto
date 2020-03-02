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

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.acks.Acknowledgement;
import org.eclipse.ditto.signals.acks.AcknowledgementCorrelationIdMissingException;
import org.eclipse.ditto.signals.acks.AcknowledgementRequestDuplicateCorrelationIdException;

import akka.actor.AbstractActor;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.InvalidActorNameException;
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
public final class AcknowledgementForwarderActor {

    /**
     * Prefix of the acknowledgement forwarder actor's name.
     */
    static final String ACTOR_NAME_PREFIX = "ackForwarder-";

    private AcknowledgementForwarderActor() {
        throw new AssertionError();
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
        return ACTOR_NAME_PREFIX + correlationId;
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
    public static Optional<ActorRef> startAcknowledgementForwarder(final ActorContext context,
            final EntityId entityId,
            final DittoHeaders dittoHeaders,
            final AcknowledgementConfig acknowledgementConfig) {

        final ActorStarter starter = ActorStarter.getInstance(context, entityId, dittoHeaders, acknowledgementConfig);
        return starter.get();
    }

    /**
     * Starting an acknowledgement forwarder actor is more complex than simply call {@code actorOf}.
     * Thus starting logic is worth to be handled within its own class.
     *
     * @since 1.1.0
     */
    static final class ActorStarter implements Supplier<Optional<ActorRef>> {

        private final ActorContext actorContext;
        private final EntityId entityId;
        private final DittoHeaders dittoHeaders;
        private final Set<AcknowledgementRequest> acknowledgementRequests;
        private final AcknowledgementConfig acknowledgementConfig;

        private ActorStarter(final ActorContext context,
                final EntityId entityId,
                final DittoHeaders dittoHeaders,
                final AcknowledgementConfig acknowledgementConfig) {

            actorContext = checkNotNull(context, "context");
            this.entityId = checkNotNull(entityId, "entityId");
            this.dittoHeaders = checkNotNull(dittoHeaders, "dittoHeaders");
            acknowledgementRequests = dittoHeaders.getAcknowledgementRequests();
            this.acknowledgementConfig = checkNotNull(acknowledgementConfig, "acknowledgementConfig");
        }

        /**
         * Returns an instance of {@code ActorStarter}.
         *
         * @param context the context to start the forwarder actor in. Furthermore provides the sender and self
         * reference for forwarding.
         * @param entityId is used for the NACKs if the forwarder actor cannot be started.
         * @param dittoHeaders the headers which contain the acknowledgement requests.
         * @param acknowledgementConfig provides configuration setting regarding acknowledgement handling.
         * @return a means to start an acknowledgement forwarder actor.
         * @throws NullPointerException if any argument is {@code null}.
         */
        static ActorStarter getInstance(final ActorContext context,
                final EntityId entityId,
                final DittoHeaders dittoHeaders,
                final AcknowledgementConfig acknowledgementConfig) {

            return new ActorStarter(context, entityId, dittoHeaders, acknowledgementConfig);
        }

        @Override
        public Optional<ActorRef> get() {
            ActorRef actorRef = null;
            if (!acknowledgementRequests.isEmpty()) {
                actorRef = tryToStartAckForwarderActor();
            }
            return Optional.ofNullable(actorRef);
        }

        @Nullable
        private ActorRef tryToStartAckForwarderActor() {
            try {
                return startAckForwarderActor();
            } catch (final InvalidActorNameException e) {

                // In case that the actor with that name already existed, the correlation-id was already used recently:
                declineAllNonDittoAckRequests(getDuplicateCorrelationIdException(e));
                return null;
            }
        }

        private ActorRef startAckForwarderActor() {
            final Props props = ActorImplementation.props(actorContext.sender(), dittoHeaders,
                    acknowledgementConfig.getForwarderFallbackTimeout());
            return actorContext.actorOf(props, determineActorName(dittoHeaders));
        }

        private DittoRuntimeException getDuplicateCorrelationIdException(final Throwable cause) {
            return AcknowledgementRequestDuplicateCorrelationIdException
                    .newBuilder(dittoHeaders.getCorrelationId().orElse("?"))
                    .dittoHeaders(dittoHeaders)
                    .cause(cause)
                    .build();
        }

        private void declineAllNonDittoAckRequests(final DittoRuntimeException dittoRuntimeException) {
            final ActorRef sender = actorContext.sender();
            final ActorRef self = actorContext.self();

            // answer NACKs for all AcknowledgementRequests with labels which were not Ditto-defined
            acknowledgementRequests.stream()
                    .map(AcknowledgementRequest::getLabel)
                    .filter(Predicate.not(DittoAcknowledgementLabel::contains))
                    .map(label -> getNack(label, dittoRuntimeException))
                    .forEach(nack -> sender.tell(nack, self));
        }

        private Acknowledgement getNack(final AcknowledgementLabel label,
                final DittoRuntimeException dittoRuntimeException) {

            return Acknowledgement.of(label, entityId, dittoRuntimeException.getStatusCode(), dittoHeaders,
                    dittoRuntimeException.toJson());
        }

    }

    private static final class ActorImplementation extends AbstractActor {

        private final ActorRef acknowledgementRequester;
        private final String correlationId;
        private final DittoDiagnosticLoggingAdapter log;

        @SuppressWarnings("unused")
        private ActorImplementation(final ActorRef acknowledgementRequester, final DittoHeaders dittoHeaders,
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

            return Props.create(ActorImplementation.class, acknowledgementRequester, dittoHeaders, defaultTimeout);
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

    }

}
