/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageTimeoutException;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.services.models.acks.AcknowledgementAggregator;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.acks.things.ThingAcknowledgementFactory;
import org.eclipse.ditto.signals.base.WithOptionalEntity;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.base.WithEntity;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayCommandTimeoutException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceUnavailableException;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ReceiveTimeout;
import akka.actor.Status;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.headers.Location;
import akka.http.javadsl.model.headers.RawHeader;
import akka.http.scaladsl.model.ContentType$;
import akka.http.scaladsl.model.EntityStreamSizeException;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.util.ByteString;
import scala.util.Either;

/**
 * Abstract actor to handle one HTTP request. It is created with an HTTP request and a promise of an HTTP response that
 * it should fulfill. When it receives a command response, exception, status or timeout message, it renders the message
 * into an HTTP response and stops itself. Its behavior can be modified by overriding the protected instance methods.
 */
public abstract class AbstractHttpRequestActor extends AbstractActor {

    /**
     * Signals the completion of a stream request.
     */
    public static final String COMPLETE_MESSAGE = "complete";

    private static final ContentType CONTENT_TYPE_JSON = ContentTypes.APPLICATION_JSON;
    private static final ContentType CONTENT_TYPE_TEXT = ContentTypes.TEXT_PLAIN_UTF8;

    private final DittoDiagnosticLoggingAdapter logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ActorRef proxyActor;
    private final HeaderTranslator headerTranslator;
    private final CompletableFuture<HttpResponse> httpResponseFuture;
    private final HttpRequest httpRequest;

    @Nullable private DittoHeaders requestDittoHeaders;
    @Nullable private AcknowledgementAggregator acknowledgements;
    @Nullable private Uri responseLocationUri;

    protected AbstractHttpRequestActor(final ActorRef proxyActor,
            final HeaderTranslator headerTranslator,
            final HttpRequest request,
            final CompletableFuture<HttpResponse> httpResponseFuture,
            final HttpConfig httpConfig) {

        this.proxyActor = proxyActor;
        this.headerTranslator = headerTranslator;
        this.httpResponseFuture = httpResponseFuture;
        httpRequest = request;

        getContext().setReceiveTimeout(httpConfig.getRequestTimeout());
    }

    @Override
    public AbstractActor.Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Status.Failure.class, failure -> {
                    Throwable cause = failure.cause();
                    if (cause instanceof JsonRuntimeException) {
                        // wrap JsonRuntimeExceptions
                        cause = new DittoJsonException((RuntimeException) cause);
                    }
                    if (cause instanceof DittoRuntimeException) {
                        handleDittoRuntimeException((DittoRuntimeException) cause);
                    } else if (cause instanceof EntityStreamSizeException) {
                        logger.warning("Got EntityStreamSizeException when a 'Command' was expected which means that" +
                                " the max. allowed http payload size configured in Akka was overstepped in this" +
                                " request.");
                        completeWithResult(
                                HttpResponse.create().withStatus(HttpStatusCode.REQUEST_ENTITY_TOO_LARGE.toInt()));
                    } else {
                        logger.error(cause, "Got unknown Status.Failure when a 'Command' was expected.");
                        completeWithResult(
                                HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt()));
                    }
                })
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(ReceiveTimeout.class, receiveTimeout -> handleAcknowledgementsTimeout(null))
                .match(Acknowledgement.class, this::handleAcknowledgement)
                .match(Command.class, command -> !isResponseRequired(command), this::handleCommandWithoutResponse)
                .match(ThingCommand.class, this::handleThingCommand)
                .match(MessageCommand.class, this::handleMessageCommand)
                .match(Command.class, command -> handleCommandWithResponse(command,
                        getResponseAwaitingBehavior(getTimeoutExceptionSupplier(command))))
                .matchAny(m -> {
                    logger.warning("Got unknown message, expected a 'Command': {}", m);
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt()));
                })
                .build();
    }

    protected void handleAcknowledgementsTimeout(
            @Nullable final Supplier<DittoRuntimeException> timeoutExceptionSupplier) {

        final DittoHeaders dittoHeaders = requestDittoHeaders != null ?
                DittoHeaders.of(headerTranslator.toExternalHeaders(requestDittoHeaders)) :
                DittoHeaders.empty();
        getAcknowledgements().ifPresentOrElse(acks -> completeAcknowledgements(dittoHeaders, acks), () -> {
            if (null != timeoutExceptionSupplier) {
                final DittoRuntimeException timeoutException = timeoutExceptionSupplier.get();
                logger.withCorrelationId(timeoutException)
                        .info("Got <{}> when a response was expected after timeout <{}>.",
                                ReceiveTimeout.class.getSimpleName(), getContext().getReceiveTimeout());
                handleDittoRuntimeException(timeoutException);
            } else {
                handleDittoRuntimeException(GatewayServiceUnavailableException.newBuilder()
                        .dittoHeaders(dittoHeaders)
                        .build());
            }
        });
    }

    private void potentiallyCompleteAcknowledgements(final DittoHeaders dittoHeaders,
            final AcknowledgementAggregator acks) {

        if (acks.receivedAllRequestedAcknowledgements()) {
            completeAcknowledgements(dittoHeaders, acks);
        }
    }

    private void completeAcknowledgements(final DittoHeaders dittoHeaders, final AcknowledgementAggregator acks) {

        final Acknowledgements aggregatedAcknowledgements = acks.getAggregatedAcknowledgements(dittoHeaders);
        completeWithResult(createCommandResponse(aggregatedAcknowledgements.getDittoHeaders(),
                aggregatedAcknowledgements.getStatusCode(),
                aggregatedAcknowledgements)
        );
    }

    private Optional<AcknowledgementAggregator> getAcknowledgements() {
        return Optional.ofNullable(acknowledgements);
    }

    private static boolean isResponseRequired(final WithDittoHeaders<?> withDittoHeaders) {
        final DittoHeaders dittoHeaders = withDittoHeaders.getDittoHeaders();
        return dittoHeaders.isResponseRequired();
    }

    private void handleCommandWithoutResponse(final Command<?> command) {
        logger.withCorrelationId(command)
                .debug("Received <{}> that does't expect a response. Telling the target actor about it.", command);
        proxyActor.tell(command, getSelf());
        completeWithResult(HttpResponse.create().withStatus(StatusCodes.ACCEPTED));
    }

    private void handleThingCommand(final ThingCommand<?> thingCommand) {
        final Receive awaitCommandResponseBehavior =
                getAwaitThingCommandResponseBehavior(getTimeoutExceptionSupplier(thingCommand));

        final String correlationId = thingCommand.getDittoHeaders().getCorrelationId().orElseThrow();
        acknowledgements = AcknowledgementAggregator.getInstance(thingCommand.getEntityId(), correlationId,
                thingCommand.getDittoHeaders().getTimeout().orElse(getContext().getReceiveTimeout()));

        handleCommandWithResponse(thingCommand, awaitCommandResponseBehavior);
    }

    private Supplier<DittoRuntimeException> getTimeoutExceptionSupplier(final WithDittoHeaders<?> command) {
        return () -> {
            final ActorContext context = getContext();
            final Duration receiveTimeout = context.getReceiveTimeout();
            return GatewayCommandTimeoutException.newBuilder(receiveTimeout)
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        };
    }

    private Receive getAwaitThingCommandResponseBehavior(
            final Supplier<DittoRuntimeException> timeoutExceptionSupplier) {

        final Receive awaitThingCommandResponseBehavior = ReceiveBuilder.create()
                .match(ThingModifyCommandResponse.class, commandResponse -> {
                    logger.withCorrelationId(commandResponse).debug("Got <{}>.", commandResponse.getType());
                    if (HttpStatusCode.CREATED == commandResponse.getStatusCode()) {
                        responseLocationUri = getUriForLocationHeader(httpRequest, commandResponse);
                    }
                    getAcknowledgements().ifPresentOrElse(acks -> {
                                acks.addReceivedAcknowledgment(getTwinPersistedAcknowledgement(commandResponse));
                                potentiallyCompleteAcknowledgements(commandResponse.getDittoHeaders(), acks);
                            },
                            () -> completeWithResult(createCommandResponse(commandResponse.getDittoHeaders(),
                                    commandResponse.getStatusCode(),
                                    commandResponse))
                    );
                })
                .match(ThingErrorResponse.class, errorResponse -> {
                    logger.withCorrelationId(errorResponse).debug("Got error response <{}>.", errorResponse.getType());
                    getAcknowledgements().ifPresentOrElse(acks -> {
                        acks.addReceivedAcknowledgment(getTwinPersistedAcknowledgement(errorResponse));
                        potentiallyCompleteAcknowledgements(errorResponse.getDittoHeaders(), acks);
                    }, () -> handleDittoRuntimeException(errorResponse.getDittoRuntimeException()));
                })
                .build();

        return awaitThingCommandResponseBehavior.orElse(getResponseAwaitingBehavior(timeoutExceptionSupplier));
    }

    private Acknowledgement getTwinPersistedAcknowledgement(final ThingCommandResponse<?> response) {
        @Nullable final JsonValue jsonValue;
        if (response instanceof WithOptionalEntity) {
            jsonValue = ((WithOptionalEntity) response).getEntity().orElse(null);
        } else {
            jsonValue = null;
        }

        final DittoHeadersBuilder<?, ?> filteredHeadersBuilder = DittoHeaders.newBuilder(
                headerTranslator.toExternalHeaders(response.getDittoHeaders()));
        if (HttpStatusCode.CREATED == response.getStatusCode() && null != responseLocationUri) {
            final Location location = Location.create(responseLocationUri);
            filteredHeadersBuilder.putHeader(location.lowercaseName(), location.value());
        }
        return ThingAcknowledgementFactory.newAcknowledgement(DittoAcknowledgementLabel.PERSISTED,
                response.getEntityId(), response.getStatusCode(), filteredHeadersBuilder.build(), jsonValue);
    }

    private void handleCommandWithResponse(final Command<?> command, final Receive awaitCommandResponseBehavior) {
        logger.withCorrelationId(command).debug("Got <{}>. Telling the target actor about it.", command);
        final UnaryOperator<Command<?>> ackRequestSetter = ThingModifyCommandAckRequestSetter.getInstance();
        final Command<?> commandWithAckLabels = ackRequestSetter.apply(command);

        requestDittoHeaders = commandWithAckLabels.getDittoHeaders();
        if (requestDittoHeaders.getAcknowledgementRequests().isEmpty()) {
            acknowledgements = null;
        }
        getAcknowledgements().ifPresent(acks ->
                acks.addAcknowledgementRequests(requestDittoHeaders.getAcknowledgementRequests())
        );

        proxyActor.tell(commandWithAckLabels, getSelf());

        final ActorContext context = getContext();
        requestDittoHeaders.getTimeout()
                .ifPresent(context::setReceiveTimeout);

        // After a Command was received, this Actor can only receive the correlating CommandResponse:
        context.become(awaitCommandResponseBehavior);
    }

    private void handleMessageCommand(final MessageCommand<?, ?> command) {
        logger.withCorrelationId(command)
                .info("Got <{}> with subject <{}>, telling the targetActor about it.", command.getType(),
                        command.getMessage().getSubject());
        final Supplier<DittoRuntimeException> timeoutExceptionSupplier = () -> {
            final ActorContext context = getContext();
            final Duration receiveTimeout = context.getReceiveTimeout();
            return MessageTimeoutException.newBuilder(receiveTimeout.getSeconds())
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        };

        final Receive responseAwaitingBehavior = getResponseAwaitingBehavior(timeoutExceptionSupplier);
        handleCommandWithResponse(command, responseAwaitingBehavior);
    }

    private void handleAcknowledgement(final Acknowledgement acknowledgement) {
        getAcknowledgements().ifPresent(acks -> {
            final DittoHeaders externalHeaders =
                    DittoHeaders.of(headerTranslator.toExternalHeaders(acknowledgement.getDittoHeaders()));
            final Acknowledgement adjustedAcknowledgement = Acknowledgement.of(acknowledgement.getLabel(),
                    acknowledgement.getEntityId(),
                    acknowledgement.getStatusCode(),
                    externalHeaders,
                    acknowledgement.getEntity().orElse(null));

            final DittoHeaders dittoHeaders = requestDittoHeaders != null ?
                    DittoHeaders.of(headerTranslator.toExternalHeaders(requestDittoHeaders)) :
                    externalHeaders;
            acks.addReceivedAcknowledgment(adjustedAcknowledgement);
            potentiallyCompleteAcknowledgements(dittoHeaders, acks);
        });
    }

    private Receive getResponseAwaitingBehavior(final Supplier<DittoRuntimeException> timeoutExceptionSupplier) {
        return ReceiveBuilder.create()
                .matchEquals(COMPLETE_MESSAGE, s -> logger.debug("Got stream's <{}> message.", COMPLETE_MESSAGE))
                // If an actor downstream replies with an HTTP response, simply forward it.
                .match(HttpResponse.class, response -> getAcknowledgements().ifPresentOrElse(acks -> {
                    throw new IllegalStateException("Acknowledgements were present for a plain " +
                            "HttpResponse message: " + response);
                }, () -> completeWithResult(response)))
                .match(Acknowledgement.class, this::handleAcknowledgement)
                .match(MessageCommandResponse.class, cmd -> {
                    final HttpResponse response = handleMessageResponseMessage(cmd);
                    getAcknowledgements().ifPresentOrElse(acks -> {
                        throw new IllegalStateException("Acknowledgements were present for a " +
                                "MessageCommandResponse: " + response);
                    }, () -> completeWithResult(response));
                })
                .match(CommandResponse.class, cR -> cR instanceof WithEntity, commandResponse -> {
                    logger.withCorrelationId(commandResponse).debug("Got <{}> message.", commandResponse.getType());
                    if (HttpStatusCode.CREATED == commandResponse.getStatusCode()) {
                        responseLocationUri = getUriForLocationHeader(httpRequest, commandResponse);
                    }

                    final WithEntity<?> withEntity = (WithEntity<?>) commandResponse;

                    final HttpResponse responseWithoutHeaders = HttpResponse.create()
                            .withStatus(commandResponse.getStatusCode().toInt());
                    final HttpResponse responseWithoutBody = enhanceResponseWithExternalDittoHeaders(
                            responseWithoutHeaders, commandResponse.getDittoHeaders());

                    final Optional<String> entityPlainStringOptional = withEntity.getEntityPlainString();
                    final HttpResponse response;
                    if (entityPlainStringOptional.isPresent()) {
                        response = addEntityAccordingToContentType(responseWithoutBody,
                                entityPlainStringOptional.get(), commandResponse.getDittoHeaders());
                    } else {
                        response = addEntityAccordingToContentType(responseWithoutBody,
                                withEntity.getEntity(commandResponse.getImplementedSchemaVersion()),
                                commandResponse.getDittoHeaders());
                    }

                    getAcknowledgements().ifPresentOrElse(acks -> {
                        throw new IllegalStateException("Acknowledgements were present for an unsupported " +
                                "CommandResponse: " + commandResponse.getType());
                    }, () -> completeWithResult(response));
                })
                .match(CommandResponse.class, cR -> cR instanceof WithOptionalEntity, commandResponse -> {
                    logger.withCorrelationId(commandResponse).debug("Got <{}> message.", commandResponse.getType());
                    if (HttpStatusCode.CREATED == commandResponse.getStatusCode()) {
                        responseLocationUri = getUriForLocationHeader(httpRequest, commandResponse);
                    }
                    final WithOptionalEntity withOptionalEntity = (WithOptionalEntity) commandResponse;
                    final HttpResponse response = createCommandResponse(commandResponse.getDittoHeaders(),
                            commandResponse.getStatusCode(),
                            withOptionalEntity);

                    getAcknowledgements().ifPresentOrElse(acks -> {
                        throw new IllegalStateException("Acknowledgements were present for an unsupported " +
                                "CommandResponse: " + commandResponse.getType());
                    }, () -> completeWithResult(response));
                })
                .match(ErrorResponse.class,
                        errorResponse -> handleDittoRuntimeException(errorResponse.getDittoRuntimeException()))
                .match(CommandResponse.class, commandResponse -> {
                    logger.withCorrelationId(commandResponse)
                            .error("Got 'CommandResponse' message which did neither implement 'WithEntity' nor" +
                                    " 'WithOptionalEntity': <{}>!", commandResponse);
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt()));
                })
                .match(Status.Failure.class, f -> f.cause() instanceof AskTimeoutException, failure -> {
                    final Throwable cause = failure.cause();
                    logger.error(cause, "Got <{}> when a command response was expected: <{}>!",
                            cause.getClass().getSimpleName(), cause.getMessage());
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt()));
                })
                .match(JsonRuntimeException.class, jre -> {
                    // wrap JsonRuntimeExceptions
                    final DittoJsonException dre = new DittoJsonException(jre);
                    handleDittoRuntimeException(dre);
                })
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(ReceiveTimeout.class, receiveTimeout -> handleReceiveTimeout(timeoutExceptionSupplier))
                .match(Status.Failure.class, failure -> failure.cause() instanceof DittoRuntimeException, failure -> {
                    final DittoRuntimeException dre = (DittoRuntimeException) failure.cause();
                    handleDittoRuntimeException(dre);
                })
                .match(Status.Failure.class, failure -> {
                    logger.error(failure.cause().fillInStackTrace(),
                            "Got <Status.Failure> when a command response was expected: <{}>!",
                            failure.cause().getMessage());
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt()));
                })
                .matchAny(m -> {
                    logger.error("Got unknown message when a command response was expected: <{}>!", m);
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt()));
                })
                .build();
    }

    private HttpResponse handleMessageResponseMessage(final MessageCommandResponse<?, ?> messageCommandResponse) {
        HttpResponse httpResponse;

        final Message<?> message = messageCommandResponse.getMessage();
        final Optional<?> optionalPayload = message.getPayload();
        final Optional<ByteBuffer> optionalRawPayload = message.getRawPayload();
        final Optional<HttpStatusCode> responseStatusCode = Optional.of(messageCommandResponse.getStatusCode())
                .filter(code -> StatusCodes.lookup(code.toInt()).isPresent())
                // only allow status code which are known to akka-http
                .filter(code -> !HttpStatusCode.BAD_GATEWAY.equals(code));
        // filter "bad gateway" 502 from being used as this is used Ditto internally for graceful HTTP shutdown

        // if statusCode is != NO_CONTENT
        if (responseStatusCode.map(status -> status != HttpStatusCode.NO_CONTENT).orElse(true)) {
            final Optional<ContentType> optionalContentType = message.getContentType().map(ContentType$.MODULE$::parse)
                    .filter(Either::isRight)
                    .map(Either::right)
                    .map(Either.RightProjection::get);

            httpResponse = HttpResponse.create().withStatus(responseStatusCode.orElse(HttpStatusCode.OK).toInt());

            if (optionalPayload.isPresent()) {
                final Object payload = optionalPayload.get();

                if (optionalContentType.isPresent()) {
                    httpResponse = httpResponse.withEntity(
                            HttpEntities.create(optionalContentType.get(),
                                    ByteString.ByteStrings.fromString(payload.toString())));
                } else {
                    httpResponse = httpResponse.withEntity(HttpEntities.create(payload.toString()));
                }
            } else if (optionalRawPayload.isPresent()) {

                final ByteBuffer rawPayload = optionalRawPayload.get();
                if (optionalContentType.isPresent()) {
                    httpResponse = httpResponse.withEntity(
                            HttpEntities.create(optionalContentType.get(), rawPayload.array()));
                } else {
                    httpResponse = httpResponse.withEntity(HttpEntities.create(rawPayload.array()));
                }
            }
        } else {
            // if payload was missing OR statusCode was NO_CONTENT:
            optionalRawPayload.ifPresent(byteBuffer -> logger.withCorrelationId(messageCommandResponse)
                    .info("Response payload was set but response status code was also set to <{}>." +
                                    " Ignoring the response payload. Command=<{}>", responseStatusCode,
                            messageCommandResponse));
            httpResponse =
                    HttpResponse.create().withStatus(responseStatusCode.orElse(HttpStatusCode.NO_CONTENT).toInt());
        }

        return enhanceResponseWithExternalDittoHeaders(httpResponse, messageCommandResponse.getDittoHeaders());
    }

    private void handleReceiveTimeout(final Supplier<DittoRuntimeException> timeoutExceptionSupplier) {
        handleAcknowledgementsTimeout(timeoutExceptionSupplier);
    }

    private void handleDittoRuntimeException(final DittoRuntimeException exception) {
        logger.withCorrelationId(exception)
                .info("DittoRuntimeException <{}>: <{}>.", exception.getErrorCode(), exception.getMessage());

        final HttpResponse responseWithoutHeaders = buildResponseWithoutHeadersFromDittoRuntimeException(exception);
        final HttpResponse response =
                enhanceResponseWithExternalDittoHeaders(responseWithoutHeaders, exception.getDittoHeaders());

        completeWithResult(response);
    }

    private static HttpResponse buildResponseWithoutHeadersFromDittoRuntimeException(
            final DittoRuntimeException exception) {

        final HttpResponse responseWithoutHeaders = HttpResponse.create().withStatus(exception.getStatusCode().toInt());
        if (HttpStatusCode.NOT_MODIFIED.equals(exception.getStatusCode())) {
            return responseWithoutHeaders;
        }
        return responseWithoutHeaders.withEntity(CONTENT_TYPE_JSON, ByteString.fromString(exception.toJsonString()));
    }

    private HttpResponse enhanceResponseWithExternalDittoHeaders(final HttpResponse response,
            final DittoHeaders allDittoHeaders) {

        logger.setCorrelationId(allDittoHeaders);
        final Map<String, String> externalHeaders = headerTranslator.toExternalHeaders(allDittoHeaders);

        if (externalHeaders.isEmpty()) {
            logger.debug("No external headers for enhancing the response, returning it as-is.");
            return response;
        }

        logger.debug("Enhancing response with external headers <{}>.", externalHeaders);
        final List<HttpHeader> externalHttpHeaders = new ArrayList<>(externalHeaders.size());
        externalHeaders.forEach((k, v) -> externalHttpHeaders.add(RawHeader.create(k, v)));
        logger.discardCorrelationId();

        return response.withHeaders(externalHttpHeaders);
    }

    private void completeWithResult(final HttpResponse response) {
        final int statusCode = response.status().intValue();
        if (logger.isDebugEnabled()) {
            logger.debug("Responding with HTTP response code <{}>.", statusCode);
            logger.debug("Responding with entity <{}>.", response.entity());
        }
        httpResponseFuture.complete(response);
        stop();
    }

    private void stop() {
        logger.clearMDC();
        // destroy ourselves:
        getContext().stop(getSelf());
    }

    private static HttpResponse addEntityAccordingToContentType(final HttpResponse response, final String entityPlain,
            final DittoHeaders dittoHeaders) {

        return response.withEntity(getContentType(dittoHeaders), ByteString.fromString(entityPlain));
    }

    private static ContentType getContentType(final DittoHeaders dittoHeaders) {
        if ("text/plain".equalsIgnoreCase(dittoHeaders.get(DittoHeaderDefinition.CONTENT_TYPE.name()))) {
            return CONTENT_TYPE_TEXT;
        }
        return CONTENT_TYPE_JSON;
    }

    private HttpResponse createCommandResponse(final DittoHeaders dittoHeaders,
            final HttpStatusCode statusCode,
            final WithOptionalEntity withOptionalEntity) {

        final UnaryOperator<HttpResponse> addExternalDittoHeaders =
                response -> enhanceResponseWithExternalDittoHeaders(response, dittoHeaders);
        final UnaryOperator<HttpResponse> modifyResponseOperator = this::modifyResponse;
        final Function<HttpResponse, HttpResponse> addHeaders = addExternalDittoHeaders
                .andThen(modifyResponseOperator);

        final UnaryOperator<HttpResponse> addBodyIfEntityExists =
                createBodyAddingResponseMapper(dittoHeaders, withOptionalEntity);

        return createHttpResponseWithHeadersAndBody(statusCode, addHeaders, addBodyIfEntityExists);
    }

    private static UnaryOperator<HttpResponse> createBodyAddingResponseMapper(final DittoHeaders dittoHeaders,
            final WithOptionalEntity withOptionalEntity) {

        return response -> {
            if (StatusCodes.NO_CONTENT.equals(response.status())) {
                return response;
            }
            return withOptionalEntity.getEntity(dittoHeaders.getSchemaVersion()
                    .orElse(dittoHeaders.getImplementedSchemaVersion())
            )
                    .map(entity -> addEntityAccordingToContentType(response, entity, dittoHeaders))
                    .orElse(response);
        };
    }

    private static HttpResponse addEntityAccordingToContentType(final HttpResponse response, final JsonValue entity,
            final DittoHeaders dittoHeaders) {

        final ContentType contentType = getContentType(dittoHeaders);
        final String entityString = CONTENT_TYPE_TEXT.equals(contentType) ? entity.asString() : entity.toString();
        return response.withEntity(contentType, ByteString.fromString(entityString));
    }

    private static HttpResponse createHttpResponseWithHeadersAndBody(final HttpStatusCode statusCode,
            final Function<HttpResponse, HttpResponse> addHeaders, final UnaryOperator<HttpResponse> addBody) {

        final HttpResponse response = HttpResponse.create().withStatus(statusCode.toInt());
        return addBody.apply(addHeaders.apply(response));
    }

    /**
     * Modify an HTTP response according to the HTTP response's status code, add the {@code Location} header
     * when the status code was {@link HttpStatusCode#CREATED}.
     *
     * @param response the candidate HTTP response.
     * @return the modified HTTP response.
     */
    protected HttpResponse modifyResponse(final HttpResponse response) {

        if (HttpStatusCode.CREATED.toInt() == response.status().intValue() && null != responseLocationUri) {
            return response.addHeader(Location.create(responseLocationUri));
        } else {
            return response;
        }
    }

    protected Uri getUriForLocationHeader(final HttpRequest request, final CommandResponse<?> commandResponse) {
        final UriForLocationHeaderSupplier supplier = new UriForLocationHeaderSupplier(request, commandResponse);
        return supplier.get();
    }

}
