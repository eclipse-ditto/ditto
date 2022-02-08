/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.slf4j.Logger;

/**
 * Kafka transformation result containing either a {@link DittoRuntimeException} in case of a failure or an
 * {@link ExternalMessage} in case of a successfully transformed message.
 */
@Immutable
final class TransformationResult {

    private static final String CREATION_TIME = "creation-time";
    private static final String TTL = "ttl";
    private static final Logger LOGGER = DittoLoggerFactory.getLogger(TransformationResult.class);

    @Nullable private final DittoRuntimeException dittoRuntimeException;
    @Nullable private final ExternalMessage externalMessage;

    private TransformationResult(@Nullable final DittoRuntimeException dittoRuntimeException,
            @Nullable final ExternalMessage externalMessage) {

        this.dittoRuntimeException = dittoRuntimeException;
        this.externalMessage = externalMessage;
    }

    static TransformationResult successful(final ExternalMessage externalMessage) {
        return new TransformationResult(null, externalMessage);
    }

    static TransformationResult failed(final DittoRuntimeException dittoRuntimeException) {
        return new TransformationResult(dittoRuntimeException, null);
    }

    Optional<DittoRuntimeException> getDittoRuntimeException() {
        return Optional.ofNullable(dittoRuntimeException);
    }

    Optional<ExternalMessage> getExternalMessage() {
        return Optional.ofNullable(externalMessage);
    }

    /**
     * Checks based on the Kafka headers {@code "creation-time"} and {@code "ttl"} (time to live) whether the message
     * should be treated as expired message (and no longer processed) or not.
     *
     * @return whether the message is expired or not.
     */
    boolean isExpired() {
        final Map<String, String> headers = Optional.ofNullable(externalMessage).map(ExternalMessage::getHeaders)
                .or(() -> Optional.ofNullable(dittoRuntimeException).map(DittoRuntimeException::getDittoHeaders))
                .orElseGet(Map::of);
        final long now = Instant.now().toEpochMilli();
        try {
            final Optional<Long> creationTimeOptional = Optional.ofNullable(headers.get(CREATION_TIME))
                    .map(Long::parseLong);
            final Optional<Long> ttlOptional = Optional.ofNullable(headers.get(TTL))
                    .map(Long::parseLong);
            if (creationTimeOptional.isPresent() && ttlOptional.isPresent()) {
                final long timeSinceCreation = now - creationTimeOptional.get();
                final var result = timeSinceCreation >= ttlOptional.get();
                LOGGER.debug("Evaluating Kafka message expiry with creation-time: <{}>, time since creation: " +
                                "<{}> and ttl: <{}> to: <{}>",
                        creationTimeOptional, timeSinceCreation, ttlOptional, result);
                return result;
            }
            return false;
        } catch (final Exception e) {
            // Errors during reading/parsing headers should not cause the message to be dropped.
            final Object message = null != externalMessage ? externalMessage : dittoRuntimeException;
            LOGGER.warn("Encountered error checking the expiry of Kafka message: <{}>, <{}>: {}", message,
                    e.getClass().getSimpleName(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TransformationResult that = (TransformationResult) o;
        return Objects.equals(dittoRuntimeException, that.dittoRuntimeException) &&
                Objects.equals(externalMessage, that.externalMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dittoRuntimeException, externalMessage);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "dittoRuntimeException=" + dittoRuntimeException +
                ", externalMessage=" + externalMessage +
                "]";
    }
}
