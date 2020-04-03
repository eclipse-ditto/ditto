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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.entity.id.NamespacedEntityIdWithType;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.AcknowledgementRequestTimeoutException;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.acks.things.ThingAcknowledgementFactory;
import org.eclipse.ditto.signals.base.WithOptionalEntity;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;

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

    private final EntityIdWithType entityId;
    private final Consumer<EntityIdWithType> entityIdValidator;
    private final String correlationId;
    private final HeaderTranslator headerTranslator;
    private final Map<AcknowledgementLabel, Acknowledgement> acknowledgementMap;
    private final Duration timeout;

    private AcknowledgementAggregator(final EntityIdWithType entityId,
            final Consumer<EntityIdWithType> entityIdValidator,
            final CharSequence correlationId,
            final Duration timeout,
            final HeaderTranslator headerTranslator) {

        this.entityId = checkNotNull(entityId, "entityId");
        this.entityIdValidator = entityIdValidator;
        this.correlationId = argumentNotEmpty(correlationId).toString();
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
        acknowledgementMap = new LinkedHashMap<>(DEFAULT_INITIAL_CAPACITY);
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
    public static AcknowledgementAggregator getInstance(final EntityIdWithType entityId,
            final CharSequence correlationId, final Duration timeout, final HeaderTranslator headerTranslator) {

        return new AcknowledgementAggregator(entityId,
                EntityIdWithType.createEqualityValidator(entityId),
                correlationId,
                timeout,
                headerTranslator);
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
    public static AcknowledgementAggregator getInstance(final NamespacedEntityIdWithType entityId,
            final CharSequence correlationId, final Duration timeout, final HeaderTranslator headerTranslator) {

        return new AcknowledgementAggregator(entityId,
                EntityIdWithType.createEqualityValidator(entityId),
                correlationId,
                timeout,
                headerTranslator);
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
    }

    private Acknowledgement getTimeoutAcknowledgement(final AcknowledgementLabel acknowledgementLabel) {

        // This Acknowledgement was not actually received, thus it cannot have "real" DittoHeaders.
        final DittoRuntimeException timeoutException = new AcknowledgementRequestTimeoutException(timeout);
        return Acknowledgement.of(acknowledgementLabel, entityId, timeoutException.getStatusCode(),
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
     * Creates an {@code Acknowledgement} with label {@link DittoAcknowledgementLabel#PERSISTED} from the passed
     * {@code thingCommandResponse} and adds it to the received acknowledgements.
     *
     * @param thingCommandResponse the thing command response to create the twin persisted acknowledgement from.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException <ul>
     * <li>if {@code thingCommandResponse} did not provide a correlation ID at all,</li>
     * <li>the provided correlation ID differs from the correlation ID of this aggregator instance or</li>
     * <li>if acknowledgement provides an unexpected entity ID.</li>
     * </ul>
     */
    public void addReceivedTwinPersistedAcknowledgment(final ThingCommandResponse<?> thingCommandResponse) {

        checkNotNull(thingCommandResponse, "thingCommandResponse");
        final DittoHeaders acknowledgementHeaders = getFilteredAcknowledgementHeaders(thingCommandResponse);

        @Nullable JsonValue payload = null;
        if (thingCommandResponse instanceof WithOptionalEntity) {
            payload = ((WithOptionalEntity) thingCommandResponse)
                    .getEntity(thingCommandResponse.getImplementedSchemaVersion())
                    .orElse(null);
        }

        addReceivedAcknowledgment(ThingAcknowledgementFactory.newAcknowledgement(DittoAcknowledgementLabel.PERSISTED,
                thingCommandResponse.getEntityId(), thingCommandResponse.getStatusCode(), acknowledgementHeaders,
                payload));
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
        validateCorrelationId(acknowledgement);
        validateEntityId(acknowledgement);
        if (isRequested(acknowledgement) && isFirstOfItsLabel(acknowledgement)) {
            final DittoHeaders acknowledgementHeaders = getFilteredAcknowledgementHeaders(acknowledgement);
            final Acknowledgement adjustedAck = acknowledgement.setDittoHeaders(acknowledgementHeaders);
            acknowledgementMap.put(adjustedAck.getLabel(), adjustedAck);
        }
    }

    private void validateCorrelationId(final WithDittoHeaders<Acknowledgement> acknowledgement) {
        final DittoHeaders dittoHeaders = acknowledgement.getDittoHeaders();
        final String receivedCorrelationId = dittoHeaders.getCorrelationId()
                .orElseThrow(() -> {
                    final String pattern = "The received Acknowledgement did not provide a correlation ID at all but"
                            + " expected was <{0}>!";
                    return new IllegalArgumentException(MessageFormat.format(pattern, correlationId));
                });

        if (!receivedCorrelationId.equals(correlationId)) {
            final String ptrn = "The received Acknowledgement''s correlation ID <{0}> differs from the expected <{1}>!";
            throw new IllegalArgumentException(MessageFormat.format(ptrn, receivedCorrelationId, correlationId));
        }
    }

    private void validateEntityId(final Acknowledgement acknowledgement) {
        entityIdValidator.accept(acknowledgement.getEntityId());
    }

    private boolean isRequested(final Acknowledgement acknowledgement) {
        final AcknowledgementLabel ackLabel = acknowledgement.getLabel();
        return acknowledgementMap.containsKey(ackLabel);
    }

    private boolean isFirstOfItsLabel(final Acknowledgement acknowledgement) {
        final AcknowledgementLabel ackLabel = acknowledgement.getLabel();
        @Nullable final Acknowledgement knownAcknowledgement = acknowledgementMap.get(ackLabel);
        return null != knownAcknowledgement && knownAcknowledgement.isTimeout();
    }

    private DittoHeaders getFilteredAcknowledgementHeaders(final WithDittoHeaders<?> withDittoHeaders) {
        final DittoHeaders externalHeaders =
                DittoHeaders.of(headerTranslator.toExternalHeaders(withDittoHeaders.getDittoHeaders()));
        final DittoHeadersBuilder<?, ?> headersBuilder = DittoHeaders.newBuilder(externalHeaders);
        return headersBuilder.build();
    }

    /**
     * Indicates whether all requested acknowledgements were received.
     *
     * @return {@code true} if all requested acknowledgements were received, i. e. there are no pending
     * acknowledgements.
     */
    public boolean receivedAllRequestedAcknowledgements() {
        final Collection<Acknowledgement> acknowledgements = acknowledgementMap.values();
        return acknowledgements.stream()
                .noneMatch(Acknowledgement::isTimeout);
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
