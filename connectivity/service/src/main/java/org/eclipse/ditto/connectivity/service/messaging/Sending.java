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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.base.model.headers.DittoHeaderDefinition.CORRELATION_ID;

import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.WithHttpStatus;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.service.messaging.validation.ConnectionValidator;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.ExpressionResolver;

/**
 * A signal being sent represented by a future sending result holding an acknowledgement or other command response
 */
@NotThreadSafe
final class Sending implements SendingOrDropped {

    private final SendingContext sendingContext;
    private final CompletionStage<SendResult> futureResponse;
    private final ExpressionResolver connectionIdResolver;
    private final ThreadSafeDittoLoggingAdapter logger;

    /**
     * Constructs a new Sending object.
     *
     * @param sendingContext context information for the sent signal.
     * @param futureResult contains a signal being sent as either future acknowledgement or another command response.
     * @param connectionIdResolver an ExpressionResolver for looking up {@code {{connection:id}}} placeholders in target acks.
     * @param logger the logger to be used for logging.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Sending(final SendingContext sendingContext, final CompletionStage<SendResult> futureResult,
            final ExpressionResolver connectionIdResolver,
            final ThreadSafeDittoLoggingAdapter logger) {

        this.sendingContext = checkNotNull(sendingContext, "sendingContext");
        this.futureResponse = checkNotNull(futureResult, "futureResult");
        this.connectionIdResolver = checkNotNull(connectionIdResolver, "connectionIdResolver");
        this.logger = checkNotNull(logger, "logger");
    }

    @SuppressWarnings({"rawtypes", "java:S3740"})
    @Override
    public Optional<CompletionStage<CommandResponse>> monitorAndAcknowledge(
            final ExceptionToAcknowledgementConverter exceptionConverter) {

        return Optional.of(futureResponse.handle((result, error) -> {

            final Optional<CommandResponse<?>> ackFromRootCause = Optional.ofNullable(error).map(Sending::getRootCause)
                    .map(rootException -> convertExceptionToAcknowledgementOrNull(rootException, exceptionConverter));
            final Optional<CommandResponse<?>> ackFromNullResponse =
                    Optional.ofNullable(acknowledgementFromNullResponse(exceptionConverter, result));

            final Optional<CommandResponse<?>> responseOrAlternatives = ackFromRootCause
                    .or(() -> ackFromNullResponse)
                    .or(() -> Optional.ofNullable(result).flatMap(SendResult::getCommandResponse));

            updateSendMonitor(getSentFailure(error, result, ackFromNullResponse.isPresent()));
            responseOrAlternatives.ifPresent(commandResponse -> updateAckMonitor(getAckFailure(commandResponse)));
            return responseOrAlternatives.orElse(null);
        }));
    }

    @Nullable
    private CommandResponse<?> acknowledgementFromNullResponse(
            final ExceptionToAcknowledgementConverter exceptionConverter, @Nullable final SendResult result) {
        if (sendingContext.shouldAcknowledge() && null == result) {
            /*
             * result == null; report error.
             * This indicates a bug in the publisher actor because of one of the following reasons:
             * - Ack is only allowed to be null if the issued-ack should be "live-result".
             * - If the issued-ack is "live-result" the result must be considered as the ack which
             *   has to be issued by the auto ack target.
             */
            return convertExceptionToAcknowledgementOrNull(getNullAckException(), exceptionConverter);
        } else {
            return null;
        }
    }

    @Nullable
    private Exception getSentFailure(@Nullable final Throwable error, @Nullable SendResult result,
            boolean isAckFromNullResponse) {
        final Optional<Throwable> messageSendingFailedException =
                Optional.ofNullable(result)
                        .flatMap(SendResult::getSendFailure)
                        .map(Throwable.class::cast);
        final Throwable sendingFailure = Optional.ofNullable(error)
                .or(() -> messageSendingFailedException)
                .orElse(null);

        if (null != sendingFailure) {
            return getRootCause(sendingFailure);
        } else if (isAckFromNullResponse) {
            return getNullAckException();
        } else {
            return null;
        }
    }

    @Nullable
    private Supplier<DittoRuntimeException> getAckFailure(final WithHttpStatus response) {
        final HttpStatus status = response.getHttpStatus();
        if (response instanceof Acknowledgement && (status.isClientError() || status.isServerError())) {
            return () -> getExceptionForAcknowledgement((Acknowledgement) response);
        } else if (response instanceof CommandResponse<?>
                && isTargetIssuesLiveResponse(sendingContext)
                && HttpStatus.REQUEST_TIMEOUT.equals(status)) {
            return () -> getExceptionForLiveResponse((CommandResponse<?>) response);
        } else {
            return null;
        }
    }

    private static MessageSendingFailedException getNullAckException() {
        return MessageSendingFailedException.newBuilder()
                .message("Message sending terminated without the expected acknowledgement.")
                .description("Please contact the service team.")
                .build();
    }

    private static boolean isTargetIssuesLiveResponse(final SendingContext sendingContext) {
        return sendingContext.getAutoAckTarget()
                .flatMap(Target::getIssuedAcknowledgementLabel)
                .filter(DittoAcknowledgementLabel.LIVE_RESPONSE::equals)
                .isPresent();
    }

    @Nullable
    private Acknowledgement convertExceptionToAcknowledgementOrNull(final Exception exception,
            final ExceptionToAcknowledgementConverter exceptionConverter) {

        final Optional<AcknowledgementLabel> autoAckLabel = sendingContext.getAutoAckTarget()
                .flatMap(Target::getIssuedAcknowledgementLabel)
                .flatMap(
                        ackLabel -> ConnectionValidator.resolveConnectionIdPlaceholder(connectionIdResolver, ackLabel));

        final Signal<?> source = sendingContext.getMappedOutboundSignal().getSource();
        final Optional<EntityId> entityIdOptional =
                WithEntityId.getEntityIdOfType(EntityId.class, source);

        if (autoAckLabel.isPresent() && entityIdOptional.isPresent()) {

            // assume DittoRuntimeException payload or exception message fits within quota
            return exceptionConverter.convertException(exception,
                    autoAckLabel.get(),
                    entityIdOptional.get(),
                    source.getDittoHeaders());
        } else {
            return null;
        }

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

    /**
     * Updates the send monitor with eiter success or failure.
     *
     * @param exception if given report failure, else report success.
     */
    private void updateSendMonitor(@Nullable final Exception exception) {
        final ConnectionMonitor publishedMonitor = sendingContext.getPublishedMonitor();
        final ExternalMessage message = sendingContext.getExternalMessage();

        if (exception == null) {
            publishedMonitor.success(message);
        } else {
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

    /**
     * Updates the acknowledged monitor with eiter success or failure.
     *
     * @param messageSendingFailedExceptionSupplier if given report failure, otherwise report success.
     */
    private void updateAckMonitor(
            @Nullable final Supplier<DittoRuntimeException> messageSendingFailedExceptionSupplier) {
        sendingContext.getAcknowledgedMonitor().ifPresent(ackMonitor -> {
            if (null != messageSendingFailedExceptionSupplier) {
                ackMonitor.failure(sendingContext.getExternalMessage(), messageSendingFailedExceptionSupplier.get());
            } else {
                ackMonitor.success(sendingContext.getExternalMessage());
            }
        });
    }

    private static DittoRuntimeException getExceptionForLiveResponse(final CommandResponse<?> response) {
        return MessageSendingFailedException.newBuilder()
                .httpStatus(response.getHttpStatus())
                .message("Received negative acknowledgement for label <" +
                        DittoAcknowledgementLabel.LIVE_RESPONSE.toString() + ">.")
                .description("Payload: " + response.toJson())
                .build();
    }

    private static DittoRuntimeException getExceptionForAcknowledgement(final Acknowledgement acknowledgement) {
        return MessageSendingFailedException.newBuilder()
                .httpStatus(acknowledgement.getHttpStatus())
                .message("Received negative acknowledgement for label <" + acknowledgement.getLabel() + ">.")
                .description("Payload: " + acknowledgement.getEntity().map(JsonValue::toString).orElse("<empty>"))
                .build();
    }

}
