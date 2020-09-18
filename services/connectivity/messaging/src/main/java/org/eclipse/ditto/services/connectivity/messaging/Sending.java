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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.headers.DittoHeaderDefinition.CORRELATION_ID;

import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.MessageSendingFailedException;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;

/**
 * A signal being sent represented by a future acknowledgement.
 */
@NotThreadSafe
final class Sending implements SendingOrDropped {

    static final MessageSendingFailedException NULL_ACK_EXCEPTION = MessageSendingFailedException.newBuilder()
            .message("Message sending terminated without the expected acknowledgement.")
            .description("Please contact the service team.")
            .build();

    private final SendingContext sendingContext;
    private final CompletionStage<Acknowledgement> future;
    private final ThreadSafeDittoLoggingAdapter logger;

    /**
     * Constructs a new Sending object.
     *
     * @param sendingContext context information for the sent signal.
     * @param future represents a signal being sent as future acknowledgement.
     * @param logger the logger to be used for logging.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Sending(final SendingContext sendingContext, final CompletionStage<Acknowledgement> future,
            final ThreadSafeDittoLoggingAdapter logger) {

        this.sendingContext = checkNotNull(sendingContext, "sendingContext");
        this.future = checkNotNull(future, "future");
        this.logger = checkNotNull(logger, "logger");
    }

    @Override
    public Optional<CompletionStage<Acknowledgement>> monitorAndAcknowledge(
            final ExceptionToAcknowledgementConverter exceptionConverter) {

        final ConnectionMonitor publishedMonitor = sendingContext.getPublishedMonitor();
        final ExternalMessage externalMessage = sendingContext.getExternalMessage();
        final CompletionStage<Acknowledgement> futureAcknowledgement = future
                .thenApply(acknowledgement -> {
                    final Acknowledgement result;
                    if (isSendSuccess(acknowledgement)) {
                        publishedMonitor.success(externalMessage);
                        result = sendingContext.shouldAcknowledge() ? acknowledgement : null;
                    } else if (null != acknowledgement) {
                        sendingContext.getAcknowledgedMonitor()
                                .ifPresent(acknowledgedMonitor -> acknowledgedMonitor.failure(externalMessage,
                                        getExceptionFor(acknowledgement)));
                        result = acknowledgement;
                    } else {
                        // ack == null; report error.
                        // This indicates a bug in the publisher actor because ack should never be null.
                        publishedMonitor.failure(externalMessage, NULL_ACK_EXCEPTION);
                        result = convertExceptionToAcknowledgementOrNull(NULL_ACK_EXCEPTION, exceptionConverter);
                    }
                    return result;
                })
                .exceptionally(error -> {
                    final Exception rootCause = getRootCause(error);
                    monitorSendFailure(externalMessage, rootCause, publishedMonitor);
                    return convertExceptionToAcknowledgementOrNull(rootCause, exceptionConverter);
                });

        return Optional.of(futureAcknowledgement);
    }

    private boolean isSendSuccess(@Nullable final Acknowledgement acknowledgement) {
        final boolean result;
        if (sendingContext.shouldAcknowledge()) {
            if (null == acknowledgement) {
                result = false;
            } else {
                final HttpStatusCode statusCode = acknowledgement.getStatusCode();
                result = !(statusCode.isClientError() || statusCode.isInternalError());
            }
        } else {
            result = true;
        }
        return result;
    }

    private static DittoRuntimeException getExceptionFor(final Acknowledgement acknowledgement) {
        return MessageSendingFailedException.newBuilder()
                .statusCode(acknowledgement.getStatusCode())
                .message("Received negative acknowledgement for label <" + acknowledgement.getLabel() + ">.")
                .description("Payload: " + acknowledgement.getEntity().map(JsonValue::toString).orElse("<empty>"))
                .build();
    }

    @Nullable
    private Acknowledgement convertExceptionToAcknowledgementOrNull(final Exception exception,
            final ExceptionToAcknowledgementConverter exceptionConverter) {

        final Optional<AcknowledgementLabel> labelOptional =
                sendingContext.getAutoAckTarget().flatMap(Target::getIssuedAcknowledgementLabel);

        final Acknowledgement result;
        if (labelOptional.isEmpty()) {

            // auto ACK not requested
            result = null;
        } else {

            // no ACK possible for non-twin-events, thus entityId must be ThingId
            final OutboundSignal.Mapped outboundSignal = sendingContext.getMappedOutboundSignal();
            final Signal<?> source = outboundSignal.getSource();
            final ThingId entityId = ThingId.of(source.getEntityId());

            // assume DittoRuntimeException payload or exception message fits within quota
            result = exceptionConverter.convertException(exception, labelOptional.get(), entityId,
                    source.getDittoHeaders());
        }
        return result;
    }

    @SuppressWarnings("ChainOfInstanceofChecks")
    private static Exception getRootCause(final Throwable throwable) {
        final Throwable cause = throwable.getCause();
        if (throwable instanceof CompletionException && null != cause) {
            return getRootCause(cause);
        } else if (throwable instanceof Exception) {
            return (Exception) throwable;
        } else {
            return new RuntimeException(throwable);
        }
    }

    private void monitorSendFailure(final ExternalMessage message, final Exception exception,
            final ConnectionMonitor publishedMonitor) {

        if (exception instanceof DittoRuntimeException) {
            final DittoRuntimeException dittoRuntimeException = (DittoRuntimeException) exception;
            publishedMonitor.failure(message, dittoRuntimeException);
            logger.withCorrelationId(dittoRuntimeException)
                    .info("Ran into a failure when publishing signal - {}: {}",
                            dittoRuntimeException.getClass().getSimpleName(), dittoRuntimeException.getMessage());
        } else {
            publishedMonitor.exception(message, exception);
            final DittoHeaders internalHeaders = message.getInternalHeaders();
            @Nullable final String correlationId = internalHeaders.getCorrelationId()
                    .orElseGet(() -> message.findHeaderIgnoreCase(CORRELATION_ID.getKey()).orElse(null));
            logger.withCorrelationId(correlationId)
                    .info("Unexpected failure when publishing signal  - {}: {}",
                            exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

}
