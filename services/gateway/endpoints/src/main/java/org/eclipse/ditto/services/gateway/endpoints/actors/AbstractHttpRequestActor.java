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
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageTimeoutException;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.services.models.acks.AcknowledgementAggregator;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
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
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.acks.ThingModifyCommandAckRequestSetter;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
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
        responseLocationUri = null;

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
                .match(ReceiveTimeout.class,
                        receiveTimeout -> handleDittoRuntimeException(GatewayServiceUnavailableException.newBuilder()
                                .dittoHeaders(DittoHeaders.empty())
                                .build()))
                .match(Command.class, command -> !isResponseRequired(command), this::handleCommandWithoutResponse)
                .match(ThingModifyCommand.class, this::handleThingModifyCommand)
                .match(MessageCommand.class, this::handleMessageCommand)
                .match(Command.class, command -> handleCommandWithResponse(command,
                        getResponseAwaitingBehavior(getTimeoutExceptionSupplier(command))))
                .matchAny(m -> {
                    logger.warning("Got unknown message, expected a 'Command': {}", m);
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt()));
                })
                .build();
    }

    private static boolean isResponseRequired(final WithDittoHeaders<?> withDittoHeaders) {
        final DittoHeaders dittoHeaders = withDittoHeaders.getDittoHeaders();
        return dittoHeaders.isResponseRequired();
    }

    private void handleCommandWithoutResponse(final Command<?> command) {
        logger.withCorrelationId(command)
                .debug("Received <{}> that does't expect a response. Answering with status code 202 ..", command);
        proxyActor.tell(command, getSelf());
        completeWithResult(HttpResponse.create().withStatus(StatusCodes.ACCEPTED));
    }

    private void handleThingModifyCommand(final ThingModifyCommand<?> thingCommand) {
        final ThingModifyCommand<?> commandWithAckLabels = (ThingModifyCommand<?>)
                ThingModifyCommandAckRequestSetter.getInstance().apply(thingCommand);
        final DittoHeaders dittoHeaders = commandWithAckLabels.getDittoHeaders();

        final AcknowledgementAggregator ackregator = getAcknowledgementAggregator(commandWithAckLabels);
        ackregator.addAcknowledgementRequests(dittoHeaders.getAcknowledgementRequests());

        final Receive awaitCommandResponseBehavior = getAwaitThingCommandResponseBehavior(dittoHeaders, ackregator,
                getTimeoutExceptionSupplier(commandWithAckLabels));

        handleCommandWithResponse(commandWithAckLabels, awaitCommandResponseBehavior);
    }

    private AcknowledgementAggregator getAcknowledgementAggregator(final ThingCommand<?> thingCommand) {
        final DittoHeaders dittoHeaders = thingCommand.getDittoHeaders();
        final String correlationId = dittoHeaders.getCorrelationId().orElseThrow();
        final Duration timeout = dittoHeaders.getTimeout().orElseGet(() -> getContext().getReceiveTimeout());
        return AcknowledgementAggregator.getInstance(thingCommand.getEntityId(), correlationId, timeout,
                headerTranslator);
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

    private Receive getAwaitThingCommandResponseBehavior(final DittoHeaders requestDittoHeaders,
            final AcknowledgementAggregator ackregator,
            final Supplier<DittoRuntimeException> timeoutExceptionSupplier) {

        final Receive awaitThingCommandResponseBehavior = ReceiveBuilder.create()
                .match(ThingModifyCommandResponse.class, commandResponse -> {
                    logger.withCorrelationId(commandResponse).debug("Got <{}>.", commandResponse.getType());
                    rememberResponseLocationUri(commandResponse);

                    ThingModifyCommandResponse<?> enhancedResponse = commandResponse;
                    if (null != responseLocationUri) {
                        final Location location = Location.create(responseLocationUri);
                        enhancedResponse = commandResponse.setDittoHeaders(
                                commandResponse.getDittoHeaders().toBuilder()
                                        .putHeader(location.lowercaseName(), location.value())
                                        .build()
                        );
                    }
                    ackregator.addReceivedTwinPersistedAcknowledgment(enhancedResponse);
                    potentiallyCompleteAcknowledgements(commandResponse.getDittoHeaders(), ackregator);
                })
                .match(ThingErrorResponse.class, errorResponse -> {
                    logger.withCorrelationId(errorResponse).debug("Got error response <{}>.", errorResponse.getType());
                    ackregator.addReceivedTwinPersistedAcknowledgment(errorResponse);
                    potentiallyCompleteAcknowledgements(errorResponse.getDittoHeaders(), ackregator);
                })
                .match(Acknowledgement.class, ack -> {
                    final Acknowledgement adjustedAck = ack.setDittoHeaders(getExternalHeaders(ack.getDittoHeaders()));
                    ackregator.addReceivedAcknowledgment(adjustedAck);
                    potentiallyCompleteAcknowledgements(requestDittoHeaders, ackregator);
                })
                .match(ReceiveTimeout.class, receiveTimeout -> {
                    final DittoHeaders externalHeaders = getExternalHeaders(requestDittoHeaders);
                    completeAcknowledgements(externalHeaders, ackregator);
                })
                .build();

        return awaitThingCommandResponseBehavior.orElse(getResponseAwaitingBehavior(timeoutExceptionSupplier));
    }

    private void rememberResponseLocationUri(final CommandResponse<?> commandResponse) {
        if (HttpStatusCode.CREATED == commandResponse.getStatusCode()) {
            responseLocationUri = getUriForLocationHeader(httpRequest, commandResponse);
        }
    }

    private void potentiallyCompleteAcknowledgements(final DittoHeaders dittoHeaders,
            final AcknowledgementAggregator ackregator) {

        if (ackregator.receivedAllRequestedAcknowledgements()) {
            completeAcknowledgements(dittoHeaders, ackregator);
        }
    }

    private void completeAcknowledgements(final DittoHeaders dittoHeaders, final AcknowledgementAggregator acks) {
        final Acknowledgements aggregatedAcknowledgements = acks.getAggregatedAcknowledgements(dittoHeaders);
        completeWithResult(createCommandResponse(aggregatedAcknowledgements.getDittoHeaders(),
                aggregatedAcknowledgements.getStatusCode(), aggregatedAcknowledgements));
    }

    private DittoHeaders getExternalHeaders(final DittoHeaders dittoHeaders) {
        return DittoHeaders.of(headerTranslator.toExternalAndRetainKnownHeaders(dittoHeaders));
    }

    private void handleCommandWithResponse(final Command<?> command, final Receive awaitCommandResponseBehavior) {
        logger.withCorrelationId(command).debug("Got <{}>. Telling the target actor about it.", command);
        proxyActor.tell(command, getSelf());

        final ActorContext context = getContext();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        dittoHeaders.getTimeout()
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

    private Receive getResponseAwaitingBehavior(final Supplier<DittoRuntimeException> timeoutExceptionSupplier) {
        return ReceiveBuilder.create()
                .matchEquals(COMPLETE_MESSAGE, s -> logger.debug("Got stream's <{}> message.", COMPLETE_MESSAGE))

                // If an actor downstream replies with an HTTP response, simply forward it.
                .match(HttpResponse.class, this::completeWithResult)
                .match(MessageCommandResponse.class, cmd -> completeWithResult(handleMessageResponseMessage(cmd)))
                .match(CommandResponse.class, cR -> cR instanceof WithEntity, commandResponse -> {
                    logger.withCorrelationId(commandResponse).debug("Got <{}> message.", commandResponse.getType());
                    rememberResponseLocationUri(commandResponse);

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
                    completeWithResult(response);
                })
                .match(CommandResponse.class, cR -> cR instanceof WithOptionalEntity, commandResponse -> {
                    logger.withCorrelationId(commandResponse).debug("Got <{}> message.", commandResponse.getType());
                    rememberResponseLocationUri(commandResponse);
                    completeWithResult(
                            createCommandResponse(commandResponse.getDittoHeaders(), commandResponse.getStatusCode(),
                                    (WithOptionalEntity) commandResponse));
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

                // wrap JsonRuntimeExceptions
                .match(JsonRuntimeException.class, jre -> handleDittoRuntimeException(new DittoJsonException(jre)))
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(ReceiveTimeout.class, receiveTimeout -> handleReceiveTimeout(timeoutExceptionSupplier))
                .match(Status.Failure.class, failure -> failure.cause() instanceof DittoRuntimeException,
                        failure -> handleDittoRuntimeException((DittoRuntimeException) failure.cause()))
                .match(Status.Failure.class, failure -> {
                    final Throwable cause = failure.cause();
                    logger.error(cause.fillInStackTrace(),
                            "Got <Status.Failure> when a command response was expected: <{}>!", cause.getMessage());
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
                            HttpEntities.create(optionalContentType.get(), ByteString.fromString(payload.toString())));
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
        final DittoRuntimeException timeoutException = timeoutExceptionSupplier.get();
        logger.withCorrelationId(timeoutException)
                .info("Got <{}> when a response was expected after timeout <{}>.", ReceiveTimeout.class.getSimpleName(),
                        getContext().getReceiveTimeout());
        handleDittoRuntimeException(timeoutException);
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
        final Map<String, String> externalHeaders = getExternalHeaders(allDittoHeaders);

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

    private HttpResponse createCommandResponse(final DittoHeaders dittoHeaders, final HttpStatusCode statusCode,
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
            final JsonSchemaVersion schemaVersion = dittoHeaders.getSchemaVersion()
                    .orElse(dittoHeaders.getImplementedSchemaVersion());
            return withOptionalEntity.getEntity(schemaVersion)
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
