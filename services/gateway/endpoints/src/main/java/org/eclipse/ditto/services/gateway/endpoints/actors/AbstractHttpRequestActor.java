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

import static org.eclipse.ditto.services.gateway.util.FireAndForgetMessageUtil.isFireAndForget;
import static org.eclipse.ditto.services.gateway.util.FireAndForgetMessageUtil.isFireAndForgetMessage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageTimeoutException;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpConfig;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.acks.Acknowledgement;
import org.eclipse.ditto.signals.acks.AcknowledgementCommandTimeoutException;
import org.eclipse.ditto.signals.base.WithOptionalEntity;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.base.WithEntity;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.messages.SendMessageAcceptedResponse;
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
import scala.concurrent.duration.Duration;
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
    private final HttpConfig httpConfig;

    private final AcknowledgementsPerRequest acknowledgements;
    @Nullable private java.time.Duration timeout;
    private boolean isFireAndForget;
    @Nullable private DittoRuntimeException timeoutException;

    protected AbstractHttpRequestActor(final ActorRef proxyActor,
            final HeaderTranslator headerTranslator,
            final HttpRequest request,
            final CompletableFuture<HttpResponse> httpResponseFuture,
            final HttpConfig httpConfig) {

        this.proxyActor = proxyActor;
        this.headerTranslator = headerTranslator;
        this.httpResponseFuture = httpResponseFuture;
        httpRequest = request;
        this.httpConfig = httpConfig;
        acknowledgements = AcknowledgementsPerRequest.getInstance();
        timeout = null;
        isFireAndForget = false;

        getContext().setReceiveTimeout(httpConfig.getRequestTimeout());
    }

    @Override
    public AbstractActor.Receive createReceive() {
        return ReceiveBuilder.create()
                .match(MessageCommand.class, command -> { // receive MessageCommands
                    logger.withCorrelationId(command)
                            .info("Got <{}> with subject <{}>, telling the targetActor about it.", command.getType(),
                                    command.getMessage().getSubject());

                    final Message<?> message = command.getMessage();

                    // authorized!
                    proxyActor.tell(command, getSelf());
                    becomeCommandResponseAwaiting();

                    timeout = message.getTimeout().orElse(null);
                    isFireAndForget = isFireAndForgetMessage(command);
                    if (timeout != null && !isFireAndForget) {
                        timeoutException = new MessageTimeoutException(timeout.getSeconds());
                        getContext().setReceiveTimeout(Duration.apply(timeout.getSeconds(), TimeUnit.SECONDS));
                    }
                })
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
                .match(ReceiveTimeout.class, this::handleReceiveTimeout)
                .match(Command.class, this::handleCommand)
                .matchAny(m -> {
                    logger.warning("Got unknown message, expected a 'Command': {}", m);
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt()));
                })
                .build();
    }

    private void handleCommand(final Command<?> command) {
        logger.withCorrelationId(command).debug("Got <Command> message {}, telling the targetActor about it.", command);
        DittoHeaders dittoHeaders = command.getDittoHeaders();
        if (dittoHeaders.isResponseRequired()) {
            final UnaryOperator<Command<?>> ackLabelSetter = ThingModifyCommandAckLabelSetter.getInstance();
            final Command<?> commandWithAckLabels = ackLabelSetter.apply(command);
            dittoHeaders = commandWithAckLabels.getDittoHeaders();
            acknowledgements.addRequestedAcknowledgementLabels(dittoHeaders.getRequestedAckLabels());
            proxyActor.tell(commandWithAckLabels, getSelf());

            // After a Command was received, this Actor can only receive the correlating CommandResponse:
            becomeCommandResponseAwaiting();
        } else {
            proxyActor.tell(command, getSelf());
            completeWithResult(HttpResponse.create().withStatus(StatusCodes.ACCEPTED));
        }

        timeout = dittoHeaders.getTimeout().orElse(null);
        isFireAndForget = isFireAndForget(command);
        if (timeout != null && !isFireAndForget) {
            timeoutException = AcknowledgementCommandTimeoutException.newBuilder(timeout)
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
            getContext().setReceiveTimeout(timeout);
        }
    }

    private void becomeCommandResponseAwaiting() {
        final Receive receive = ReceiveBuilder.create()
                .matchEquals(COMPLETE_MESSAGE, s -> logger.debug("Got stream's <{}> message.", COMPLETE_MESSAGE))
                // If an actor downstream replies with an HTTP response, simply forward it.
                .match(HttpResponse.class, this::completeWithResult)
                .match(SendMessageAcceptedResponse.class, cmd -> {
                    final HttpResponse httpResponse = HttpResponse.create().withStatus(HttpStatusCode.ACCEPTED.toInt());
                    completeWithResult(httpResponse);
                })
                .match(MessageCommandResponse.class, cmd -> {
                    final HttpResponse httpResponse = handleMessageResponseMessage(cmd);
                    completeWithResult(httpResponse);
                })
                .match(ThingModifyCommandResponse.class, commandResponse -> {
                    logger.withCorrelationId(commandResponse).debug("Got <{}>.", commandResponse.getType());
                    acknowledgements.addReceivedAcknowledgment(getPersistedAcknowledgementForResponse(commandResponse));
                    completeWithResult(createCommandResponse(httpRequest, commandResponse, commandResponse));
                })
                .match(ThingErrorResponse.class, errorResponse -> {
                    logger.withCorrelationId(errorResponse).debug("Got error response <{}>.", errorResponse.getType());
                    acknowledgements.addReceivedAcknowledgment(getPersistedAcknowledgementForResponse(errorResponse));
                    handleDittoRuntimeException(errorResponse.getDittoRuntimeException());
                })
                .match(CommandResponse.class, cR -> cR instanceof WithEntity, commandResponse -> {
                    logger.withCorrelationId(commandResponse).debug("Got <{}> message.", commandResponse.getType());
                    final WithEntity<?> withEntity = (WithEntity<?>) commandResponse;

                    final HttpResponse responseWithoutHeaders = HttpResponse.create()
                            .withStatus(commandResponse.getStatusCode().toInt());
                    final HttpResponse responseWithoutBody = enhanceResponseWithExternalDittoHeaders(
                            responseWithoutHeaders, commandResponse.getDittoHeaders());

                    final Optional<String> entityPlainStringOptional = withEntity.getEntityPlainString();
                    if (entityPlainStringOptional.isPresent()) {
                        completeWithResult(addEntityAccordingToContentType(responseWithoutBody,
                                entityPlainStringOptional.get(), commandResponse.getDittoHeaders()));
                    } else {
                        completeWithResult(addEntityAccordingToContentType(responseWithoutBody,
                                withEntity.getEntity(commandResponse.getImplementedSchemaVersion()),
                                commandResponse.getDittoHeaders()));
                    }
                })
                .match(CommandResponse.class, cR -> cR instanceof WithOptionalEntity, commandResponse -> {
                    logger.withCorrelationId(commandResponse).debug("Got <{}> message.", commandResponse.getType());
                    final WithOptionalEntity withOptionalEntity = (WithOptionalEntity) commandResponse;
                    completeWithResult(createCommandResponse(httpRequest, commandResponse, withOptionalEntity));
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
                    logger.warning("Got <{}> when a command response was expected: <{}>!",
                            cause.getClass().getSimpleName(), cause.getMessage());
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt()));
                })
                .match(JsonRuntimeException.class, jre -> {
                    // wrap JsonRuntimeExceptions
                    final DittoJsonException dre = new DittoJsonException(jre);
                    handleDittoRuntimeException(dre);
                })
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(ReceiveTimeout.class, this::handleReceiveTimeout)
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
                    logger.warning("Got unknown message when a command response was expected: <{}>!", m);
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt()));
                })
                .build();

        final ActorContext context = getContext();
        context.become(receive);
    }

    private static Acknowledgement getPersistedAcknowledgementForResponse(final CommandResponse<?> commandResponse) {
        return Acknowledgement.of(DittoAcknowledgementLabel.PERSISTED, commandResponse.getEntityId(),
                commandResponse.getStatusCode(), commandResponse.getDittoHeaders());
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

    private void handleReceiveTimeout(final ReceiveTimeout receiveTimeout) {
        if (timeout != null && !isFireAndForget) {
            logger.info("Got <{}> when a response was expected after timeout <{}>.",
                    receiveTimeout.getClass().getSimpleName(), timeout);
            handleDittoRuntimeException(timeoutException);
        } else {
            logger.warning("No response within server request timeout (<{}>), shutting actor down.",
                    httpConfig.getRequestTimeout());
            // note that we do not need to send a response here, this is handled by RequestTimeoutHandlingDirective
            stop();
        }
    }

    private void handleDittoRuntimeException(final DittoRuntimeException exception) {
        logger.withCorrelationId(exception)
                .info("DittoRuntimeException <{}>: <{}>.", exception.getErrorCode(), exception.getMessage());
        completeWithDittoRuntimeException(exception);
    }

    private void completeWithDittoRuntimeException(final DittoRuntimeException dre) {
        final HttpResponse responseWithoutHeaders = buildResponseWithoutHeadersFromDittoRuntimeException(dre);
        final HttpResponse response =
                enhanceResponseWithExternalDittoHeaders(responseWithoutHeaders, dre.getDittoHeaders());

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

    private HttpResponse createCommandResponse(final HttpRequest request, final CommandResponse commandResponse,
            final WithOptionalEntity withOptionalEntity) {

        final UnaryOperator<HttpResponse> addExternalDittoHeaders =
                response -> enhanceResponseWithExternalDittoHeaders(response, commandResponse.getDittoHeaders());
        final UnaryOperator<HttpResponse> modifyResponseOperator =
                response -> modifyResponse(request, commandResponse, response);
        final Function<HttpResponse, HttpResponse> addHeaders =
                addExternalDittoHeaders.andThen(modifyResponseOperator);

        final UnaryOperator<HttpResponse> addBodyIfEntityExists =
                createBodyAddingResponseMapper(commandResponse, withOptionalEntity);

        return createHttpResponseWithHeadersAndBody(commandResponse, addHeaders, addBodyIfEntityExists);
    }

    private static UnaryOperator<HttpResponse> createBodyAddingResponseMapper(final CommandResponse commandResponse,
            final WithOptionalEntity withOptionalEntity) {

        return response -> {
            if (StatusCodes.NO_CONTENT.equals(response.status())) {
                return response;
            }
            return withOptionalEntity.getEntity(commandResponse.getImplementedSchemaVersion())
                    .map(entity -> addEntityAccordingToContentType(response, entity, commandResponse.getDittoHeaders()))
                    .orElse(response);
        };
    }

    private static HttpResponse addEntityAccordingToContentType(final HttpResponse response, final JsonValue entity,
            final DittoHeaders dittoHeaders) {

        final ContentType contentType = getContentType(dittoHeaders);
        final String entityString = CONTENT_TYPE_TEXT.equals(contentType) ? entity.asString() : entity.toString();
        return response.withEntity(contentType, ByteString.fromString(entityString));
    }

    private static HttpResponse createHttpResponseWithHeadersAndBody(final CommandResponse commandResponse,
            final Function<HttpResponse, HttpResponse> addHeaders, final UnaryOperator<HttpResponse> addBody) {

        final HttpResponse response = HttpResponse.create().withStatus(commandResponse.getStatusCodeValue());

        return addBody.apply(addHeaders.apply(response));
    }

    /**
     * Modify an HTTP response according to the request and the command response.
     *
     * @param request the HTTP request.
     * @param commandResponse the command response to the HTTP request.
     * @param response the candidate HTTP response.
     * @return the modified HTTP response.
     */
    protected HttpResponse modifyResponse(final HttpRequest request, final CommandResponse commandResponse,
            final HttpResponse response) {

        if (HttpStatusCode.CREATED == commandResponse.getStatusCode()) {
            return response.addHeader(Location.create(getUriForLocationHeader(request, commandResponse)));
        } else {
            return response;
        }
    }

    protected Uri getUriForLocationHeader(final HttpRequest request, final CommandResponse<?> commandResponse) {
        final UriForLocationHeaderSupplier supplier = new UriForLocationHeaderSupplier(request, commandResponse);
        return supplier.get();
    }

}
