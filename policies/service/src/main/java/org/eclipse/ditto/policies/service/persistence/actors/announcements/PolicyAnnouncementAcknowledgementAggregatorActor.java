/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.service.persistence.actors.announcements;

import java.time.Duration;
import java.util.function.Consumer;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.exceptions.CommandTimeoutException;
import org.eclipse.ditto.base.service.acknowledgements.AcknowledgementAggregator;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;

import akka.actor.AbstractActorWithTimers;
import akka.actor.Props;

/**
 * Aggregates acknowledgements for a published {@link PolicyAnnouncement}
 * When all acknowledgements are completed the responseConsumer will be called with an {@link Acknowledgements} in case
 * there are multiple acknowledgements requested or with an {@link Acknowledgement} in case just a single acknowledgement
 * was requested.
 * If an error happens the responseConsumer will be called with a {@link DittoRuntimeException}.
 *
 * @since 3.0.0
 */
final class PolicyAnnouncementAcknowledgementAggregatorActor extends AbstractActorWithTimers {

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final String correlationId;
    private final Duration timeout;
    private final PolicyAnnouncement<?> originatingSignal;
    private final AcknowledgementAggregator aggregator;
    private final Consumer<Object> responseSignalConsumer;

    private PolicyAnnouncementAcknowledgementAggregatorActor(final PolicyAnnouncement<?> policyAnnouncement,
            final Duration timeout,
            final Consumer<Object> responseSignalConsumer) {

        this.originatingSignal = policyAnnouncement;
        this.timeout = timeout;
        this.responseSignalConsumer = responseSignalConsumer;
        final var dittoHeaders = originatingSignal.getDittoHeaders();
        this.correlationId = dittoHeaders.getCorrelationId()
                .orElseGet(() -> self().path().name());
        timers().startSingleTimer(Control.WAITING_FOR_ACKS_TIMED_OUT, Control.WAITING_FOR_ACKS_TIMED_OUT, timeout);
        final var acknowledgementRequests = dittoHeaders.getAcknowledgementRequests();
        aggregator = AcknowledgementAggregator.getInstance(policyAnnouncement.getEntityId(), correlationId, timeout,
                HeaderTranslator.empty());
        aggregator.addAcknowledgementRequests(acknowledgementRequests);
        log.withCorrelationId(correlationId)
                .info("Starting to wait for all requested acknowledgements <{}> for a maximum duration of <{}>.",
                        acknowledgementRequests, timeout);
    }

    static Props props(final PolicyAnnouncement<?> policyAnnouncement,
            final Duration timeout,
            final Consumer<Object> responseSignalConsumer) {
        return Props.create(PolicyAnnouncementAcknowledgementAggregatorActor.class, policyAnnouncement, timeout,
                responseSignalConsumer);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Acknowledgement.class, this::handleAcknowledgement)
                .match(Acknowledgements.class, this::handleAcknowledgements)
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .matchEquals(Control.WAITING_FOR_ACKS_TIMED_OUT, this::handleReceiveTimeout)
                .matchAny(m -> log.warning("Received unexpected message: <{}>", m))
                .build();
    }

    private void handleReceiveTimeout(final Control receiveTimeout) {
        log.withCorrelationId(correlationId).info("Timed out waiting for all requested acknowledgements, " +
                "completing Acknowledgements with timeouts...");
        final var aggregatedAcknowledgements =
                aggregator.getAggregatedAcknowledgements(originatingSignal.getDittoHeaders());
        final PolicyId entityId = originatingSignal.getEntityId();
        final DittoHeaders headersWithEntityId = aggregatedAcknowledgements.getDittoHeaders()
                .toBuilder()
                .putHeader(DittoHeaderDefinition.ENTITY_ID.getKey(), entityId.getEntityType() + ":" + entityId)
                .build();
        responseSignalConsumer.accept(CommandTimeoutException.newBuilder(timeout)
                .dittoHeaders(headersWithEntityId)
                .build());
        getContext().stop(getSelf());
    }

    private void handleAcknowledgement(final Acknowledgement acknowledgement) {
        log.withCorrelationId(correlationId).debug("Received acknowledgement <{}>.", acknowledgement);
        aggregator.addReceivedAcknowledgment(acknowledgement);
        potentiallyCompleteAcknowledgements();
    }

    private void handleAcknowledgements(final Acknowledgements acknowledgements) {
        log.withCorrelationId(correlationId).debug("Received acknowledgements <{}>.", acknowledgements);
        acknowledgements.stream().forEach(aggregator::addReceivedAcknowledgment);
        potentiallyCompleteAcknowledgements();
    }

    private void handleDittoRuntimeException(final DittoRuntimeException dittoRuntimeException) {
        log.withCorrelationId(correlationId)
                .info("Stopped waiting for acknowledgements because of ditto runtime exception <{}>.",
                        dittoRuntimeException);
        responseSignalConsumer.accept(dittoRuntimeException);
        getContext().stop(getSelf());
    }

    private void potentiallyCompleteAcknowledgements() {
        if (aggregator.receivedAllRequestedAcknowledgements()) {
            completeAcknowledgements();
        }
    }

    private void completeAcknowledgements() {
        final var aggregatedAcknowledgements =
                aggregator.getAggregatedAcknowledgements(originatingSignal.getDittoHeaders());

        log.withCorrelationId(originatingSignal)
                .debug("Completing with collected acknowledgements: {}", aggregatedAcknowledgements);
        responseSignalConsumer.accept(aggregatedAcknowledgements);
        getContext().stop(getSelf());
    }

    private enum Control {
        WAITING_FOR_ACKS_TIMED_OUT
    }
}
