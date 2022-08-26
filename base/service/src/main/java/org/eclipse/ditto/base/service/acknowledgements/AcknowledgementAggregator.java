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
package org.eclipse.ditto.base.service.acknowledgements;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.entity.id.AbstractNamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.AcknowledgementRequestTimeoutException;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;

/**
 * This class can be used to aggregate the required and actually received acknowledgements for a single request which
 * requested acknowledgements.
 * The aggregator works in the context of a correlation ID as well as an entity ID; all received acknowledgements have
 * to comply to these.
 *
 * @since 1.1.0
 */
@NotThreadSafe
public final class AcknowledgementAggregator {

    private static final byte DEFAULT_INITIAL_CAPACITY = 4;

    private final EntityId entityId;
    private final String correlationId;
    private final HeaderTranslator headerTranslator;
    private final Map<AcknowledgementLabel, Acknowledgement> acknowledgementMap;
    private final Duration timeout;
    private final Set<AcknowledgementLabel> expectedLabels;

    private AcknowledgementAggregator(final EntityId entityId,
            final CharSequence correlationId,
            final Duration timeout,
            final HeaderTranslator headerTranslator) {

        this.entityId = checkNotNull(entityId, "entityId");
        this.correlationId = argumentNotEmpty(correlationId).toString();
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
        acknowledgementMap = new LinkedHashMap<>(DEFAULT_INITIAL_CAPACITY);
        expectedLabels = new HashSet<>();
        this.timeout = checkNotNull(timeout, "timeout");
    }

    /**
     * Returns an instance of {@code AcknowledgementAggregator}.
     *
     * @param entityId the ID of the entity for which acknowledgements should be correlated and aggregated.
     * @param correlationId the ID for correlating acknowledgement requests with acknowledgements.
     * @param timeout the timeout applied how long to wait for acknowledgements.
     * @param headerTranslator the header translator to use in order to translate internal headers to externally
     * publishable ones.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code correlationId} is empty.
     */
    public static AcknowledgementAggregator getInstance(final EntityId entityId,
            final CharSequence correlationId,
            final Duration timeout,
            final HeaderTranslator headerTranslator) {

        return new AcknowledgementAggregator(entityId, correlationId, timeout, headerTranslator);
    }

    /**
     * Returns an instance of {@code AcknowledgementAggregator}.
     *
     * @param entityId the ID of the entity for which acknowledgements should be correlated and aggregated.
     * @param correlationId the ID for correlating acknowledgement requests with acknowledgements.
     * @param timeout the timeout applied how long to wait for acknowledgements.
     * @param headerTranslator the header translator to use in order to translate internal headers to externally
     * publishable ones.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code correlationId} is empty.
     */
    public static AcknowledgementAggregator getInstance(final AbstractNamespacedEntityId entityId,
            final CharSequence correlationId,
            final Duration timeout,
            final HeaderTranslator headerTranslator) {

        return new AcknowledgementAggregator(entityId, correlationId, timeout, headerTranslator);
    }

    /**
     * Returns the {@code correlationId} this instance was created for.
     *
     * @return the correlation ID this acknowledgement aggregator handles.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Adds the given acknowledgement request.
     *
     * @param acknowledgementRequest the acknowledgement request to be added.
     * @throws NullPointerException if {@code acknowledgementRequest} is {@code null}.
     */
    public void addAcknowledgementRequest(final AcknowledgementRequest acknowledgementRequest) {
        checkNotNull(acknowledgementRequest, "acknowledgementRequest");
        final AcknowledgementLabel ackLabel = acknowledgementRequest.getLabel();
        acknowledgementMap.put(ackLabel, getTimeoutAcknowledgement(ackLabel));
        expectedLabels.add(ackLabel);
    }

    private Acknowledgement getTimeoutAcknowledgement(final AcknowledgementLabel acknowledgementLabel) {

        // This Acknowledgement was not actually received, thus it cannot have "real" DittoHeaders.
        final DittoRuntimeException timeoutException = AcknowledgementRequestTimeoutException.newBuilder(timeout)
                .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                .build();
        return Acknowledgement.of(acknowledgementLabel, entityId, timeoutException.getHttpStatus(),
                timeoutException.getDittoHeaders(), timeoutException.toJson());
    }

    /**
     * Adds the given acknowledgement requests if they are not already present.
     *
     * @param acknowledgementRequests the acknowledgement requests to be added.
     * @throws NullPointerException if {@code acknowledgementRequests} is {@code null}.
     */
    public void addAcknowledgementRequests(final Collection<AcknowledgementRequest> acknowledgementRequests) {
        checkNotNull(acknowledgementRequests, "acknowledgementRequests");
        acknowledgementRequests.forEach(this::addAcknowledgementRequest);
    }

    /**
     * Adds the given received acknowledgement and processes it accordingly.
     * An acknowledgement which as not requested will be ignored by this method, i. e. it does not affect the result of
     * ACK handling.
     * If an acknowledgement <em>with the same label</em> was already received, the new acknowledgement is discarded,
     * i. e. only the already received ACK is taken into account.
     *
     * @param acknowledgement the acknowledgement to be added.
     * @throws NullPointerException if {@code acknowledgement} is {@code null}.
     * @throws IllegalArgumentException <ul>
     * <li>if {@code acknowledgement} did not provide a correlation ID at all,</li>
     * <li>the provided correlation ID differs from the correlation ID of this aggregator instance or</li>
     * <li>if acknowledgement provides an unexpected entity ID.</li>
     * </ul>
     */
    public void addReceivedAcknowledgment(final Acknowledgement acknowledgement) {
        checkNotNull(acknowledgement, "acknowledgement");
        if (isExpected(acknowledgement)) {
            final DittoHeaders acknowledgementHeaders = filterHeaders(acknowledgement.getDittoHeaders());
            final Acknowledgement adjustedAck = acknowledgement.setDittoHeaders(acknowledgementHeaders);
            final AcknowledgementLabel label = adjustedAck.getLabel();
            acknowledgementMap.put(label, adjustedAck);
            expectedLabels.remove(label);
        }
    }

    private boolean isExpected(final Acknowledgement acknowledgement) {
        final AcknowledgementLabel ackLabel = acknowledgement.getLabel();
        return expectedLabels.contains(ackLabel);
    }

    private DittoHeaders filterHeaders(final DittoHeaders dittoHeaders) {
        return DittoHeaders.of(headerTranslator.toExternalHeaders(dittoHeaders));
    }

    /**
     * Indicates whether all requested acknowledgements were received.
     *
     * @return {@code true} if all requested acknowledgements were received, i. e. there are no pending
     * acknowledgements.
     */
    public boolean receivedAllRequestedAcknowledgements() {
        return expectedLabels.isEmpty();
    }

    /**
     * Indicates whether all requested acknowledgements were received and all were successful.
     *
     * @return {@code true} if all requested acknowledgements were received and all were successful, {@code false} else.
     */
    public boolean isSuccessful() {
        boolean result = false;
        if (receivedAllRequestedAcknowledgements()) {
            final Collection<Acknowledgement> acknowledgements = acknowledgementMap.values();
            result = acknowledgements.stream()
                    .allMatch(Acknowledgement::isSuccess);
        }
        return result;
    }

    /**
     * Builds the aggregated {@link Acknowledgements} based on the {@link Acknowledgement}s collected in this instance.
     *
     * @param dittoHeaders the {@code DittoHeaders} to include in the built Acknowledgements.
     * @return the built Acknowledgements.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code dittoHeaders} did contain another correlation ID than the expected one
     * of this aggregator instance.
     */
    public Acknowledgements getAggregatedAcknowledgements(final DittoHeaders dittoHeaders) {
        validateCorrelationId(checkNotNull(dittoHeaders, "dittoHeaders"));
        final Collection<Acknowledgement> acknowledgements = acknowledgementMap.values();
        final Acknowledgements result;
        if (acknowledgements.isEmpty()) {
            result = Acknowledgements.empty(entityId, dittoHeaders);
        } else {
            result = Acknowledgements.of(acknowledgements, dittoHeaders);
        }

        return result;
    }

    private void validateCorrelationId(final DittoHeaders dittoHeaders) {
        dittoHeaders.getCorrelationId()
                .filter(ci -> !ci.equals(correlationId))
                .ifPresent(ci -> {
                    final String pattern = "The provided correlation ID <{0}> differs from the expected <{1}>!";
                    throw new IllegalArgumentException(MessageFormat.format(pattern, ci, correlationId));
                });
    }

}
