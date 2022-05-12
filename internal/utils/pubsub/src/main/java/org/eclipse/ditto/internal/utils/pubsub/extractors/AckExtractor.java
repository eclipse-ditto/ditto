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
package org.eclipse.ditto.internal.utils.pubsub.extractors;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.json.JsonValue;

/**
 * Extract information relevant to acknowledgements from a message.
 *
 * @param <T> type of messages.
 */
public interface AckExtractor<T> {

    /**
     * Get the requested and declared  custom acknowledgement labels.
     *
     * @param requestedAcks the acknowledgement requests.
     * @param isDeclaredAck test if an ack label in string form is declared.
     * @return the intersection of requested, declared and non-built-in acknowledgement labels.
     */
    static Collection<AcknowledgementLabel> getRequestedAndDeclaredCustomAcks(
            final Set<AcknowledgementRequest> requestedAcks,
            final Predicate<String> isDeclaredAck) {
        return requestedAcks.stream()
                .map(AcknowledgementRequest::getLabel)
                .filter(label -> !AckExtractorImpl.BUILT_IN_LABELS.contains(label) &&
                        isDeclaredAck.test(label.toString()))
                .toList();
    }

    /**
     * Get the declared custom acknowledgement labels requested by a message.
     *
     * @param message the message.
     * @param isDeclaredAck test if an ack label in string form is declared.
     * @return the intersection of declared and non-built-in acknowledgement labels with the labels requested by the
     * message.
     */
    default Collection<AcknowledgementLabel> getDeclaredCustomAcksRequestedBy(final T message,
            final Predicate<String> isDeclaredAck) {
        return getRequestedAndDeclaredCustomAcks(getAckRequests(message), isDeclaredAck);
    }

    /**
     * Get the acknowledgement requests of a message.
     *
     * @param message the message.
     * @return the acknowledgement requests.
     */
    Set<AcknowledgementRequest> getAckRequests(final T message);

    /**
     * Get the entity ID of a message with entity type information.
     *
     * @param message the message.
     * @return the entity ID.
     */
    EntityId getEntityId(final T message);

    /**
     * Get the Ditto headers of a message.
     *
     * @param message the message.
     * @return the Ditto headers.
     */
    DittoHeaders getDittoHeaders(final T message);

    /**
     * Create weak acknowledgements for a message.
     *
     * @param message the message.
     * @param ackLabels the acknowledgement labels for which weak acknowledgements are to be created.
     * @return the weak acknowledgements.
     */
    default Acknowledgements toWeakAcknowledgements(final T message,
            final Collection<AcknowledgementLabel> ackLabels) {
        final EntityId entityId = getEntityId(message);
        final DittoHeaders dittoHeaders = getDittoHeaders(message);
        return Acknowledgements.of(ackLabels.stream()
                        .map(ackLabel -> weakAck(ackLabel, entityId, dittoHeaders))
                        .toList(),
                dittoHeaders
        );
    }

    /**
     * Create an {@code AckExtractor} from the extractor functions.
     *
     * @param getEntityId a function to extract the entity ID.
     * @param getDittoHeaders a function to extract the Ditto headers.
     * @param <T> the type of messages.
     * @return the AckExtractor.
     */
    static <T> AckExtractor<T> of(final Function<T, EntityId> getEntityId,
            final Function<T, DittoHeaders> getDittoHeaders) {

        return new AckExtractorImpl<>(getEntityId, getDittoHeaders);
    }

    /**
     * Returns a new weak {@code Acknowledgement} to be issued by Ditto pubsub,
     * including an explanation why Ditto pubsub issued it.
     *
     * @param label the label of the new Acknowledgement.
     * @param entityId the ID of the affected entity being acknowledged.
     * @param dittoHeaders the DittoHeaders.
     * @return the Acknowledgement.
     * @throws NullPointerException if one of the required parameters was {@code null}.
     */
    static Acknowledgement weakAck(final AcknowledgementLabel label,
            final EntityId entityId,
            final DittoHeaders dittoHeaders) {
        final JsonValue payload = JsonValue.of("Acknowledgement was issued automatically as weak ack, " +
                "because the signal is not relevant for the subscriber. Possible reasons are: " +
                "the subscriber was not authorized, " +
                "or the subscriber did not subscribe for the signal type.");
        return Acknowledgement.weak(label, entityId, dittoHeaders, payload);
    }

}
