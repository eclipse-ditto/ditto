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
package org.eclipse.ditto.services.gateway.endpoints.actors;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.signals.acks.Acknowledgement;

/**
 * This class can be used to manage the required and actually received acknowledgements for a <em>single request.</em>
 *
 * @since 1.1.0
 */
@NotThreadSafe
final class AcknowledgementsPerRequest {

    private static final byte DEFAULT_INITIAL_CAPACITY = 4;

    private final Map<AcknowledgementLabel, AcknowledgementRequest> acknowledgementRequests;
    private final Map<AcknowledgementLabel, Acknowledgement> successfulAcknowledgements;
    private final Map<AcknowledgementLabel, Acknowledgement> failedAcknowledgements;

    private AcknowledgementsPerRequest() {
        acknowledgementRequests = new LinkedHashMap<>(DEFAULT_INITIAL_CAPACITY);
        successfulAcknowledgements = new LinkedHashMap<>(DEFAULT_INITIAL_CAPACITY);
        failedAcknowledgements = new LinkedHashMap<>(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Returns an instance of {@code Acknowledgements}.
     *
     * @return the instance.
     */
    public static AcknowledgementsPerRequest getInstance() {
        return new AcknowledgementsPerRequest();
    }

    /**
     * Adds the given acknowledgement request if it is not already present.
     *
     * @param acknowledgementRequest the acknowledgement request to be added.
     * @throws NullPointerException if {@code requestedAckLabel} is {@code null}.
     */
    public void addAcknowledgementRequest(final AcknowledgementRequest acknowledgementRequest) {
        checkNotNull(acknowledgementRequest, "acknowledgementRequest");
        acknowledgementRequests.put(acknowledgementRequest.getLabel(), acknowledgementRequest);
    }

    /**
     * Adds the given acknowledgement requests if they are not already present.
     *
     * @param acknowledgementRequests the acknowledgement requests to be added.
     * @throws NullPointerException if {@code acknowledgementRequests} is {@code null}.
     */
    public void addAcknowledgementRequests(final Collection<AcknowledgementRequest> acknowledgementRequests) {
        checkNotNull(acknowledgementRequests, "acknowledgementRequests");
        acknowledgementRequests.forEach(ackReq -> this.acknowledgementRequests.put(ackReq.getLabel(), ackReq));
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
            if (isSuccessful(acknowledgement)) {
                successfulAcknowledgements.put(acknowledgement.getLabel(), acknowledgement);
            } else {
                failedAcknowledgements.put(acknowledgement.getLabel(), acknowledgement);
            }
        }
    }

    private boolean isRequested(final Acknowledgement acknowledgement) {
        return acknowledgementRequests.containsKey(acknowledgement.getLabel());
    }

    private static boolean isSuccessful(final Acknowledgement acknowledgement) {
        final HttpStatusCode statusCode = acknowledgement.getStatusCode();
        return statusCode.isSuccess();
    }

    private boolean isFirstOfItsLabel(final Acknowledgement acknowledgement) {
        final AcknowledgementLabel ackLabel = acknowledgement.getLabel();
        return !successfulAcknowledgements.containsKey(ackLabel) && !failedAcknowledgements.containsKey(ackLabel);
    }

    /**
     * Indicates whether all requested acknowledgements were received.
     *
     * @return {@code true} if all requested acknowledgements were received, i. e. there are no pending
     * acknowledgements.
     */
    public boolean receivedAllRequestedAcknowledgements() {
        return acknowledgementRequests.size() == successfulAcknowledgements.size() + failedAcknowledgements.size();
    }

    /**
     * Indicates whether all requested acknowledgements were received and all were successful.
     *
     * @return {@code true} if all requested acknowledgements were received and all were successful, {@code false} else.
     */
    public boolean isSuccessful() {
        return receivedAllRequestedAcknowledgements() && failedAcknowledgements.isEmpty();
    }

    /**
     * Returns the acknowledgement requests for which not yet an acknowledgement was received.
     *
     * @return the unanswered acknowledgement requests.
     * Changes on the returned set are not reflected back to this AcknowledgementsPerRequest instance.
     */
    public Set<AcknowledgementRequest> getMissingAcknowledgementRequests() {
        final Set<AcknowledgementRequest> result = getCopyAsLinkedHashSet(acknowledgementRequests.values());
        result.removeIf(this::isAnswered);
        return result;
    }

    private boolean isAnswered(final AcknowledgementRequest acknowledgementRequest) {
        final AcknowledgementLabel ackLabel = acknowledgementRequest.getLabel();
        return successfulAcknowledgements.containsKey(ackLabel) || failedAcknowledgements.containsKey(ackLabel);
    }

    /*
     * Creates a copy of the given collection.
     * The returned set is not supposed to be extended by the caller.
     * Thus and because the final size is already known the load factor of the returned Set is set to 1.0 to reduce the
     * memory footprint of the Set.
     */
    private static <T> Set<T> getCopyAsLinkedHashSet(final Collection<T> c) {
        final Set<T> result = new LinkedHashSet<>(c.size(), 1.0F);
        result.addAll(c);
        return result;
    }

    /**
     * Returns a set containing the the successful acknowledgements.
     *
     * @return the successful acknowledgements.
     * The returned set maintains the order in which the acknowledgement were received.
     * Changes on the returned set are not reflected back to this AcknowledgementsPerRequest instance.
     */
    public Set<Acknowledgement> getSuccessfulAcknowledgements() {
        return getCopyAsLinkedHashSet(successfulAcknowledgements.values());
    }

    /**
     * Returns a set containing the the failed acknowledgements.
     *
     * @return the failed acknowledgements.
     * The returned set maintains the order in which the acknowledgement were received.
     * Changes on the returned set are not reflected back to this AcknowledgementsPerRequest instance.
     */
    public Set<Acknowledgement> getFailedAcknowledgements() {
        return getCopyAsLinkedHashSet(failedAcknowledgements.values());
    }

}
