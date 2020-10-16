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
import static org.eclipse.ditto.services.connectivity.messaging.validation.ConnectionValidator.resolveConnectionIdPlaceholder;

import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.MessageSendingFailedException;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

/**
 * A signal being sent represented by a future acknowledgement or other command response.
 */
@NotThreadSafe
final class Sending implements SendingOrDropped {

    private final SendingContext sendingContext;
    private final CompletionStage<CommandResponse<?>> futureResponse;
    private final ExpressionResolver connectionIdResolver;
    private final ThreadSafeDittoLoggingAdapter logger;

    /**
     * Constructs a new Sending object.
     *
     * @param sendingContext context information for the sent signal.
     * @param futureResponse represents a signal being sent as either future acknowledgement or another command response.
     * @param connectionIdResolver an ExpressionResolver for looking up {@code {{connection:id}}} placeholders in target acks.
     * @param logger the logger to be used for logging.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Sending(final SendingContext sendingContext, final CompletionStage<CommandResponse<?>> futureResponse,
            final ExpressionResolver connectionIdResolver,
            final ThreadSafeDittoLoggingAdapter logger) {

        this.sendingContext = checkNotNull(sendingContext, "sendingContext");
        this.futureResponse = checkNotNull(futureResponse, "futureResponse");
        this.connectionIdResolver = checkNotNull(connectionIdResolver, "connectionIdResolver");
        this.logger = checkNotNull(logger, "logger");
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Optional<CompletionStage<CommandResponse>> monitorAndAcknowledge(
            final ExceptionToAcknowledgementConverter exceptionConverter) {

        return Optional.of(futureResponse
                .thenApply(response -> acknowledge(response, exceptionConverter))
                .thenApply(this::monitor)
                .exceptionally(error -> {
                    final Acknowledgement result = handleException(getRootCause(error), exceptionConverter);
                    monitor(result);
                    return result;
                }));
    }

    @Nullable
    private CommandResponse<?> acknowledge(@Nullable final CommandResponse<?> response,
            final ExceptionToAcknowledgementConverter exceptionConverter) {

        final CommandResponse<?> result;
        if (sendingContext.shouldAcknowledge() && null == response) {

            /*
             * response == null; report error.
             * This indicates a bug in the publisher actor because of one of the following reasons:
             * - Ack is only allowed to be null if the issued-ack should be "live-response".
             * - If the issued-ack is "live-response" the response must be considered as the ack which
             *   has to be issued by the auto ack target.
             */
            result = handleException(getNullAckException(), exceptionConverter);
        } else {
            result = response;
        }
        return result;
    }

    private static MessageSendingFailedException getNullAckException() {
        return MessageSendingFailedException.newBuilder()
                .message("Message sending terminated without the expected acknowledgement.")
                .description("Please contact the service team.")
                .build();
    }

    @SuppressWarnings({"rawtypes", "java:S3740"})
    @Nullable
    private CommandResponse monitor(@Nullable final CommandResponse<?> response) {
        if (null != response) {
            if (isAcknowledgement(response)) {
                monitorAcknowledgement((Acknowledgement) response);
            } else if (isTargetIssuesLiveResponse()) {
                monitorLiveResponse(response);
            }
        }
        return response;
    }

    private static boolean isAcknowledgement(final CommandResponse<?> commandResponse) {
        return commandResponse instanceof Acknowledgement;
    }

    private void monitorAcknowledgement(final Acknowledgement acknowledgement) {
        new AcknowledgementMonitoring(acknowledgement).monitor();
    }

    private boolean isTargetIssuesLiveResponse() {
        return sendingContext.getAutoAckTarget()
                .flatMap(Target::getIssuedAcknowledgementLabel)
                .filter(DittoAcknowledgementLabel.LIVE_RESPONSE::equals)
                .isPresent();
    }

    private void monitorLiveResponse(final CommandResponse<?> liveResponse) {
        new LiveResponseMonitoring(liveResponse).monitor();
    }

    @Nullable
    private Acknowledgement handleException(final Exception exception,
            final ExceptionToAcknowledgementConverter exceptionConverter) {

        monitorSendFailure(exception);
        return convertExceptionToAcknowledgementOrNull(exception, exceptionConverter);
    }

    private void monitorSendFailure(final Exception exception) {
        final ConnectionMonitor publishedMonitor = sendingContext.getPublishedMonitor();
        final ExternalMessage message = sendingContext.getExternalMessage();
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

    @Nullable
    private Acknowledgement convertExceptionToAcknowledgementOrNull(final Exception exception,
            final ExceptionToAcknowledgementConverter exceptionConverter) {

        final Optional<AcknowledgementLabel> labelOptional = sendingContext.getAutoAckTarget()
                .flatMap(Target::getIssuedAcknowledgementLabel)
                .flatMap(ackLabel -> resolveConnectionIdPlaceholder(connectionIdResolver, ackLabel));

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

    private abstract class CommandResponseMonitoring<T extends CommandResponse<?>> {

        private final T cmdResponse;

        protected CommandResponseMonitoring(final T cmdResponse) {
            this.cmdResponse = cmdResponse;
        }

        void monitor() {
            if (isSendSuccess()) {
                monitorSendSuccess();
            } else {
                monitorSendFailure(getExceptionFor(cmdResponse));
            }
        }

        private boolean isSendSuccess() {
            final HttpStatusCode statusCode = cmdResponse.getStatusCode();
            return !(statusCode.isClientError() || statusCode.isInternalError());
        }

        private void monitorSendSuccess() {
            final ConnectionMonitor publishedMonitor = sendingContext.getPublishedMonitor();
            publishedMonitor.success(sendingContext.getExternalMessage());
        }

        abstract DittoRuntimeException getExceptionFor(T response);

        private void monitorSendFailure(final DittoRuntimeException messageSendingFailedException) {
            sendingContext.getAcknowledgedMonitor()
                    .ifPresent(acknowledgedMonitor -> acknowledgedMonitor.failure(sendingContext.getExternalMessage(),
                            messageSendingFailedException));
        }

    }

    private final class AcknowledgementMonitoring extends CommandResponseMonitoring<Acknowledgement> {

        AcknowledgementMonitoring(final Acknowledgement acknowledgement) {
            super(acknowledgement);
        }

        @Override
        DittoRuntimeException getExceptionFor(final Acknowledgement acknowledgement) {
            return MessageSendingFailedException.newBuilder()
                    .statusCode(acknowledgement.getStatusCode())
                    .message("Received negative acknowledgement for label <" + acknowledgement.getLabel() + ">.")
                    .description("Payload: " + acknowledgement.getEntity().map(JsonValue::toString).orElse("<empty>"))
                    .build();
        }

    }

    private final class LiveResponseMonitoring extends CommandResponseMonitoring<CommandResponse<?>> {

        LiveResponseMonitoring(final CommandResponse<?> cmdResponse) {
            super(cmdResponse);
        }

        @Override
        DittoRuntimeException getExceptionFor(final CommandResponse<?> response) {
            return MessageSendingFailedException.newBuilder()
                    .statusCode(response.getStatusCode())
                    .message("Received negative acknowledgement for label <" +
                            DittoAcknowledgementLabel.LIVE_RESPONSE.toString() + ">.")
                    .description("Payload: " + response.toJson())
                    .build();
        }

    }

}
