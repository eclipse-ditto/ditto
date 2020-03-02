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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.acks.Acknowledgement;
import org.eclipse.ditto.signals.acks.Acknowledgements;

/**
 * This class can be used to aggregate the required and actually received acknowledgements for a
 * single request which requested Acknowledgements.
 *
 * @since 1.1.0
 */
@NotThreadSafe
public final class AcknowledgementAggregator {

    private static final byte DEFAULT_INITIAL_CAPACITY = 4;

    private final Map<AcknowledgementLabel, Acknowledgement> acknowledgements;

    private AcknowledgementAggregator() {
        acknowledgements = new LinkedHashMap<>(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Returns an instance of {@code Acknowledgements}.
     *
     * @return the instance.
     */
    public static AcknowledgementAggregator getInstance() {
        return new AcknowledgementAggregator();
    }

    /**
     * Adds the given acknowledgement request.
     *
     * @param acknowledgementRequest the acknowledgement request to be added.
     * @throws NullPointerException if one of the required parameters was {@code null}.
     */
    public void addAcknowledgementRequest(final AcknowledgementRequest acknowledgementRequest,
            final CharSequence entityId, final DittoHeaders dittoHeaders) {
        checkNotNull(acknowledgementRequest, "acknowledgementRequest");
        acknowledgements.put(acknowledgementRequest.getLabel(),
                buildTimeoutAcknowledgement(acknowledgementRequest.getLabel(), entityId, dittoHeaders));
    }

    /**
     * Adds the given acknowledgement requests if they are not already present.
     *
     * @param acknowledgementRequests the acknowledgement requests to be added.
     * @throws NullPointerException if one of the required parameters was {@code null}.
     */
    public void addAcknowledgementRequests(final Collection<AcknowledgementRequest> acknowledgementRequests,
            final CharSequence entityId, final DittoHeaders dittoHeaders) {
        checkNotNull(acknowledgementRequests, "acknowledgementRequests");
        acknowledgementRequests.forEach(ackReq -> this.acknowledgements.put(ackReq.getLabel(),
                buildTimeoutAcknowledgement(ackReq.getLabel(), entityId, dittoHeaders)));
    }

    private static Acknowledgement buildTimeoutAcknowledgement(final AcknowledgementLabel acknowledgementLabel,
            final CharSequence entityId, final DittoHeaders dittoHeaders) {

        return Acknowledgement.of(acknowledgementLabel,
                entityId,
                HttpStatusCode.REQUEST_TIMEOUT,
                dittoHeaders
        );
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
     */
    public void addReceivedAcknowledgment(final Acknowledgement acknowledgement) {
        checkNotNull(acknowledgement, "acknowledgement");

        if (isRequested(acknowledgement) && isFirstOfItsLabel(acknowledgement)) {
            acknowledgements.put(acknowledgement.getLabel(), acknowledgement);
        }
    }

    private boolean isRequested(final Acknowledgement acknowledgement) {
        final AcknowledgementLabel ackLabel = acknowledgement.getLabel();
        return acknowledgements.containsKey(ackLabel);
    }

    private boolean isFirstOfItsLabel(final Acknowledgement acknowledgement) {
        final AcknowledgementLabel ackLabel = acknowledgement.getLabel();
        return Optional.ofNullable(acknowledgements.get(ackLabel))
                .filter(Acknowledgement::isTimeout)
                .isPresent();
    }

    /**
     * Indicates whether all requested acknowledgements were received.
     *
     * @return {@code true} if all requested acknowledgements were received, i. e. there are no pending
     * acknowledgements.
     */
    public boolean receivedAllRequestedAcknowledgements() {
        return acknowledgements.values().stream()
                .noneMatch(Acknowledgement::isTimeout);
    }

    /**
     * Indicates whether all requested acknowledgements were received and all were successful.
     *
     * @return {@code true} if all requested acknowledgements were received and all were successful, {@code false} else.
     */
    public boolean isSuccessful() {
        return receivedAllRequestedAcknowledgements() &&
                acknowledgements.values().stream().allMatch(Acknowledgement::isSuccess);
    }

    /**
     * Builds the aggregated {@link Acknowledgements} based on the {@link Acknowledgement} collected in this instance.
     *
     * @param entityId the {@code EntityId} to include in the built Acknowledgements.
     * @param dittoHeaders the {@code DittoHeaders} to include in the built Acknowledgements.
     * @return the built Acknowledgements.
     */
    public Acknowledgements buildAggregatedAcknowledgements(final CharSequence entityId,
            final DittoHeaders dittoHeaders) {
        return Acknowledgements.of(entityId, calculateCombinedStatusCode(), acknowledgements, dittoHeaders);
    }

    private HttpStatusCode calculateCombinedStatusCode() {

        if (acknowledgements.size() == 1) {
            return acknowledgements.values().stream()
                    .findFirst()
                    .map(Acknowledgement::getStatusCode)
                    .orElse(HttpStatusCode.INTERNAL_SERVER_ERROR);
        } else {
            final boolean allAcknowledgementsSuccessful = acknowledgements.values().stream()
                    .allMatch(Acknowledgement::isSuccess);

            if (allAcknowledgementsSuccessful) {
                return HttpStatusCode.OK;
            } else {
                return HttpStatusCode.FAILED_DEPENDENCY;
            }
        }
    }

}
