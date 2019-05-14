/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.endpoints;

import static org.eclipse.ditto.services.gateway.starter.service.util.FireAndForgetMessageUtil.isFireAndForgetMessage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageTimeoutException;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.WithOptionalEntity;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.base.WithEntity;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.messages.SendMessageAcceptedResponse;

import com.typesafe.config.Config;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
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
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.util.ByteString;
import scala.concurrent.duration.Duration;
import scala.util.Either;

/**
 * Every HTTP Request causes one new Actor instance of this one to be created.
 * It holds the original sender of an issued {@link Command} and tells this one the completed HttpResponse.
 */
public final class HttpRequestActor extends AbstractActor {

    /**
     * Signals the completion of a stream request.
     */
    public static final String COMPLETE_MESSAGE = "complete";

    private static final ContentType CONTENT_TYPE_JSON = ContentTypes.APPLICATION_JSON;
    private static final ContentType CONTENT_TYPE_TEXT = ContentTypes.TEXT_PLAIN_UTF8;

    private final DiagnosticLoggingAdapter logger = LogUtil.obtain(this);

    private final ActorRef proxyActor;
    private final HeaderTranslator headerTranslator;
    private final CompletableFuture<HttpResponse> httpResponseFuture;
    private final java.time.Duration serverRequestTimeout;
    private final Receive commandResponseAwaiting;

    private java.time.Duration messageTimeout;
    private boolean isFireAndForgetMessage = false;

    private HttpRequestActor(final ActorRef proxyActor,
            final HeaderTranslator headerTranslator,
            final HttpRequest request,
            final CompletableFuture<HttpResponse> httpResponseFuture) {

        this.proxyActor = proxyActor;
        this.headerTranslator = headerTranslator;
        this.httpResponseFuture = httpResponseFuture;

        final Config config = getContext().system().settings().config();
        serverRequestTimeout = config.getDuration(ConfigKeys.AKKA_HTTP_SERVER_REQUEST_TIMEOUT);
        getContext().setReceiveTimeout(serverRequestTimeout);

        // wrap JsonRuntimeExceptions
        commandResponseAwaiting = ReceiveBuilder.create()
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
                .match(CommandResponse.class, cR -> cR instanceof WithEntity, commandResponse -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, commandResponse);
                    logger.debug("Got <{}> message.", commandResponse.getType());
                    final WithEntity<?> withEntity = (WithEntity<?>) commandResponse;

                    final HttpResponse responseWithoutHeaders = HttpResponse.create()
                            .withStatus(commandResponse.getStatusCode().toInt());
                    final HttpResponse responseWithoutBody = enhanceResponseWithExternalDittoHeaders(
                            responseWithoutHeaders, commandResponse.getDittoHeaders());

                    if (withEntity.getEntityPlainString().isPresent()) {
                        completeWithResult(addEntityAccordingToContentType(responseWithoutBody,
                                withEntity.getEntityPlainString().get(),
                                commandResponse.getDittoHeaders()));
                    } else {
                        completeWithResult(addEntityAccordingToContentType(responseWithoutBody,
                                withEntity.getEntity(commandResponse.getImplementedSchemaVersion()),
                                commandResponse.getDittoHeaders()));
                    }
                })
                .match(CommandResponse.class, cR -> cR instanceof WithOptionalEntity, commandResponse -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, commandResponse);
                    logger.debug("Got <{}> message.", commandResponse.getType());
                    final WithOptionalEntity withOptionalEntity = (WithOptionalEntity) commandResponse;
                    completeWithResult(createCommandResponse(request, commandResponse, withOptionalEntity));
                })
                .match(ErrorResponse.class,
                        errorResponse -> handleDittoRuntimeException(errorResponse.getDittoRuntimeException()))
                .match(CommandResponse.class, commandResponse -> {
                    LogUtil.enhanceLogWithCorrelationId(logger, commandResponse);
                    logger.error("Got 'CommandResponse' message which did neither implement 'WithEntity' nor " +
                            "'WithOptionalEntity': <{}>!", commandResponse);
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
    }

    private static boolean hasPlainTextContentType(final DittoHeaders dittoHeaders) {
        final String contentTypeHeader = DittoHeaderDefinition.CONTENT_TYPE.name();
        return dittoHeaders.containsKey(contentTypeHeader) &&
                "text/plain".equalsIgnoreCase(dittoHeaders.get(contentTypeHeader));
    }

    private static HttpResponse addEntityAccordingToContentType(final HttpResponse response, final JsonValue entity,
            final DittoHeaders dittoHeaders) {

        if (hasPlainTextContentType(dittoHeaders)) {
            return response.withEntity(CONTENT_TYPE_TEXT, ByteString.fromString(entity.asString()));
        }
        return response.withEntity(CONTENT_TYPE_JSON, ByteString.fromString(entity.toString()));
    }

    private static HttpResponse addEntityAccordingToContentType(final HttpResponse response, final String entityPlain,
            final DittoHeaders dittoHeaders) {

        if (hasPlainTextContentType(dittoHeaders)) {
            return response.withEntity(CONTENT_TYPE_TEXT, ByteString.fromString(entityPlain));
        }
        return response.withEntity(CONTENT_TYPE_JSON, ByteString.fromString(entityPlain));
    }

    private HttpResponse createCommandResponse(final HttpRequest request, final CommandResponse commandResponse,
            final WithOptionalEntity withOptionalEntity) {

        final UnaryOperator<HttpResponse> addExternalDittoHeaders =
                response -> enhanceResponseWithExternalDittoHeaders(response, commandResponse.getDittoHeaders());
        final UnaryOperator<HttpResponse> addModifiedLocationHeaderForCreatedResponse =
                createModifiedLocationHeaderAddingResponseMapper(request, commandResponse);
        final Function<HttpResponse, HttpResponse> addHeaders =
                addExternalDittoHeaders.andThen(addModifiedLocationHeaderForCreatedResponse);

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

    private static UnaryOperator<HttpResponse> createModifiedLocationHeaderAddingResponseMapper(
            final HttpRequest request, final CommandResponse commandResponse) {

        return response -> {
            if (HttpStatusCode.CREATED == commandResponse.getStatusCode()) {
                Uri newUri = request.getUri();
                if (!request.method().isIdempotent()) {
                    // only for not idempotent requests (e.g.: POST), add the "createdId" to the path:
                    final String uriStr = newUri.toString();
                    String createdLocation;
                    final int uriIdIndex = uriStr.indexOf(commandResponse.getId());

                    // if the URI contains the ID, but *not* at the beginning
                    if (uriIdIndex > 0) {
                        createdLocation =
                                uriStr.substring(0, uriIdIndex) + commandResponse.getId() +
                                        commandResponse.getResourcePath().toString();
                    } else {
                        createdLocation = uriStr + "/" + commandResponse.getId() + commandResponse.getResourcePath()
                                .toString();
                    }

                    if (createdLocation.endsWith("/")) {
                        createdLocation = createdLocation.substring(0, createdLocation.length() - 1);
                    }
                    newUri = Uri.create(createdLocation);
                }
                return response.addHeader(Location.create(newUri));
            }
            return response;
        };
    }

    private static HttpResponse createHttpResponseWithHeadersAndBody(final CommandResponse commandResponse,
            final Function<HttpResponse, HttpResponse> addHeaders, final UnaryOperator<HttpResponse> addBody) {

        final HttpResponse response = HttpResponse.create().withStatus(commandResponse.getStatusCodeValue());

        return addBody.apply(addHeaders.apply(response));
    }

    /**
     * Creates the Akka configuration object for this {@code HttpRequestActor} for the given {@code proxyActor}, {@code
     * request}, and {@code httpResponseFuture} which will be completed with a {@link HttpResponse}.
     *
     * @param proxyActor the proxy actor which delegates commands.
     * @param headerTranslator the {@link HeaderTranslator} used to map ditto headers to (external) Http headers.
     * @param request the HTTP request
     * @param httpResponseFuture the completable future which is completed with a HTTP response.
     * @return the configuration object.
     */
    public static Props props(final ActorRef proxyActor,
            final HeaderTranslator headerTranslator,
            final HttpRequest request,
            final CompletableFuture<HttpResponse> httpResponseFuture) {

        return Props.create(HttpRequestActor.class, new Creator<HttpRequestActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public HttpRequestActor create() {
                return new HttpRequestActor(proxyActor, headerTranslator, request, httpResponseFuture);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(MessageCommand.class, command -> { // receive MessageCommands
                    LogUtil.enhanceLogWithCorrelationId(logger, command);
                    logger.info("Got <{}> with subject <{}>, telling the targetActor about it.", command.getType(),
                            command.getMessage().getSubject());

                    final Message<?> message = command.getMessage();

                    // authorized!
                    proxyActor.tell(command, getSelf());
                    getContext().become(commandResponseAwaiting);

                    messageTimeout = message.getTimeout().orElse(null);
                    isFireAndForgetMessage = isFireAndForgetMessage(command);
                    if (messageTimeout != null && !isFireAndForgetMessage) {
                        getContext().setReceiveTimeout(Duration.apply(messageTimeout.getSeconds(), TimeUnit.SECONDS));
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
                        logger.warning("Got EntityStreamSizeException when a 'Command' was expected which means that " +
                                "the max. allowed http payload size configured in Akka was overstepped in this " +
                                "request.");
                        completeWithResult(
                                HttpResponse.create().withStatus(HttpStatusCode.REQUEST_ENTITY_TOO_LARGE.toInt())
                        );
                    } else {
                        logger.error(cause, "Got unknown Status.Failure when a 'Command' was expected.");
                        completeWithResult(
                                HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt())
                        );
                    }
                })
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(ReceiveTimeout.class, this::handleReceiveTimeout)
                .match(Command.class, command -> { // receive Commands
                    LogUtil.enhanceLogWithCorrelationId(logger, command);
                    logger.debug("Got <Command> message {}, telling the targetActor about it.", command);

                    proxyActor.tell(command, getSelf());

                    if (!command.getDittoHeaders().isResponseRequired()) {
                        completeWithResult(HttpResponse.create().withStatus(StatusCodes.ACCEPTED));
                    } else {
                        // after a Command was received, this Actor can only receive the correlating CommandResponse:
                        getContext().become(commandResponseAwaiting);
                    }
                })
                .matchAny(m -> {
                    logger.warning("Got unknown message, expected a 'Command': {}", m);
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt())
                    );
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
            optionalRawPayload.ifPresent(byteBuffer -> {
                LogUtil.enhanceLogWithCorrelationId(logger, messageCommandResponse);
                logger.info("Response payload was set but response status code was also set to <{}>. " +
                        "Ignoring the response payload. Command=<{}>", responseStatusCode, messageCommandResponse);
            });
            httpResponse =
                    HttpResponse.create().withStatus(responseStatusCode.orElse(HttpStatusCode.NO_CONTENT).toInt());
        }

        return enhanceResponseWithExternalDittoHeaders(httpResponse, messageCommandResponse.getDittoHeaders());
    }

    private void handleReceiveTimeout(final ReceiveTimeout receiveTimeout) {
        if (messageTimeout != null && !isFireAndForgetMessage) {
            logger.info("Got <{}> when a message response was expected after timeout <{}>.",
                    receiveTimeout.getClass().getSimpleName(), messageTimeout);
            handleDittoRuntimeException(new MessageTimeoutException(messageTimeout.getSeconds()));
        } else {
            logger.warning("No response within server request timeout (<{}>), shutting actor down.",
                    serverRequestTimeout);
            // note that we do not need to send a response here, this is handled by RequestTimeoutHandlingDirective
            stop();
        }
    }

    private void handleDittoRuntimeException(final DittoRuntimeException exception) {
        LogUtil.enhanceLogWithCorrelationId(logger, exception);
        logger.info("DittoRuntimeException <{}>: <{}>.", exception.getErrorCode(), exception.getMessage());
        completeWithDittoRuntimeException(exception);
    }

    private void completeWithDittoRuntimeException(final DittoRuntimeException dre) {
        final HttpResponse responseWithoutHeaders = buildResponseWithoutHeadersFromDittoRuntimeException(dre);
        final HttpResponse response =
                enhanceResponseWithExternalDittoHeaders(responseWithoutHeaders, dre.getDittoHeaders());

        completeWithResult(response);
    }

    private HttpResponse buildResponseWithoutHeadersFromDittoRuntimeException(final DittoRuntimeException exception) {
        final HttpResponse responseWithoutHeaders = HttpResponse.create().withStatus(exception.getStatusCode().toInt());
        if (HttpStatusCode.NOT_MODIFIED.equals(exception.getStatusCode())) {
            return responseWithoutHeaders;
        }
        return responseWithoutHeaders.withEntity(CONTENT_TYPE_JSON, ByteString.fromString(exception.toJsonString()));
    }

    private HttpResponse enhanceResponseWithExternalDittoHeaders(final HttpResponse response,
            final DittoHeaders allDittoHeaders) {

        LogUtil.enhanceLogWithCorrelationId(logger, allDittoHeaders);
        final Map<String, String> externalHeaders = headerTranslator.toExternalHeaders(allDittoHeaders);

        if (externalHeaders.isEmpty()) {
            logger.debug("No external headers for enhancing the response, returning it as-is.");
            return response;
        }

        logger.debug("Enhancing response with external headers <{}>.", externalHeaders);
        final List<HttpHeader> externalHttpHeaders = new ArrayList<>(externalHeaders.size());
        externalHeaders.forEach((k, v) -> externalHttpHeaders.add(RawHeader.create(k, v)));

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

}
