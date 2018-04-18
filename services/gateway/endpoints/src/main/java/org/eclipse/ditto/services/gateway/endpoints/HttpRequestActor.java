/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints;

import static org.eclipse.ditto.services.gateway.starter.service.util.FireAndForgetMessageUtil.isFireAndForgetMessage;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageTimeoutException;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.WithOptionalEntity;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.base.WithEntity;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.messages.SendMessageAcceptedResponse;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;

import com.typesafe.config.Config;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.Status;
import akka.event.DiagnosticLoggingAdapter;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.headers.Location;
import akka.http.scaladsl.model.ContentType$;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.util.ByteString;
import kamon.Kamon;
import kamon.trace.TraceContext;
import scala.Option;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import scala.util.Either;

/**
 * Every HTTP Request causes one new Actor instance of this one to be created. It holds the original sender of an issued
 * {@link Command} and tells this one the completed HttpResponse.
 */
public final class HttpRequestActor extends AbstractActor {

    /**
     * Signals the completion of a stream request.
     */
    public static final String COMPLETE_MESSAGE = "complete";

    private static final String TRACE_TAG_TYPE = "type";
    private static final String TRACE_TAG_TYPE_PREFIX = "type-prefix";
    private static final String TRACE_ROUNDTRIP_HTTP = "roundtrip.http";
    private static final ContentType CONTENT_TYPE_JSON = ContentTypes.APPLICATION_JSON;
    private static final ContentType CONTENT_TYPE_TEXT = ContentTypes.TEXT_PLAIN_UTF8;

    private static final String AKKA_HTTP_SERVER_REQUEST_TIMEOUT = "akka.http.server.request-timeout";

    private static final double NANO_TO_MS_DIVIDER = 1_000_000.0;
    private static final double HTTP_WARN_TIMEOUT_MS = 1_000.0;
    private static final double SEARCH_WARN_TIMEOUT_MS = 5_000.0;

    private final DiagnosticLoggingAdapter logger = LogUtil.obtain(this);

    private final ActorRef proxyActor;
    private final CompletableFuture<HttpResponse> httpResponseFuture;
    private final Cancellable serverRequestTimeoutCancellable;
    private final java.time.Duration serverRequestTimeout;
    private final Receive commandResponseAwaiting;

    private java.time.Duration messageTimeout;
    private TraceContext traceContext;

    private HttpRequestActor(final ActorRef proxyActor, final HttpRequest request,
            final CompletableFuture<HttpResponse> httpResponseFuture) {
        this.proxyActor = proxyActor;
        this.httpResponseFuture = httpResponseFuture;

        final Config config = getContext().system().settings().config();
        serverRequestTimeout = config.getDuration(AKKA_HTTP_SERVER_REQUEST_TIMEOUT);
        serverRequestTimeoutCancellable = getContext().system().scheduler().scheduleOnce
                (FiniteDuration.apply(serverRequestTimeout.toNanos(), TimeUnit.NANOSECONDS), getSelf(),
                        ServerRequestTimeoutMessage.INSTANCE,
                        getContext().dispatcher(), null);

        // wrap JsonRuntimeExceptions
        commandResponseAwaiting = ReceiveBuilder.create()
                .matchEquals(COMPLETE_MESSAGE, s -> logger.debug("Got stream's '{}' message", COMPLETE_MESSAGE))
                // If an actor downstream replies with an HTTP response, simply forward it.
                .match(HttpResponse.class, response -> {
                    completeWithResult(response);
                    finishTraceAndStop();
                })
                .match(SendMessageAcceptedResponse.class, cmd -> {
                    final HttpResponse httpResponse =
                            HttpResponse.create().withStatus(HttpStatusCode.ACCEPTED.toInt());
                    completeWithResult(httpResponse);
                })
                .match(MessageCommandResponse.class, cmd -> {
                    final HttpResponse httpResponse = handleMessageResponseMessage(cmd);
                    completeWithResult(httpResponse);
                })
                .match(CommandResponse.class, cR -> cR instanceof WithEntity, commandResponse -> {
                    logger.debug("Got 'CommandResponse' 'WithEntity' message");
                    final WithEntity withEntity = (WithEntity) commandResponse;

                    final HttpResponse responseWithoutBody = HttpResponse.create()
                            .withStatus(commandResponse.getStatusCode().toInt());

                    completeWithResult(addEntityAccordingToContentType(responseWithoutBody,
                            withEntity.getEntity(commandResponse.getImplementedSchemaVersion()),
                            commandResponse.getDittoHeaders()));
                })
                .match(CommandResponse.class, cR -> cR instanceof WithOptionalEntity,
                        commandResponse -> {
                            logger.debug("Got 'CommandResponse' 'WithOptionalEntity' message");
                            final WithOptionalEntity withOptionalEntity = (WithOptionalEntity) commandResponse;

                            final HttpResponse response =
                                    createCommandResponse(request, commandResponse, withOptionalEntity);
                            completeWithResult(response);
                        })
                .match(ErrorResponse.class, errorResponse -> {
                    logger.info("Got 'ErrorResponse': {}", errorResponse);
                    final DittoRuntimeException cre = errorResponse.getDittoRuntimeException();
                    completeWithResult(HttpResponse.create().withStatus(cre.getStatusCode().toInt())
                            .withEntity(CONTENT_TYPE_JSON, ByteString.fromString(cre.toJsonString())));
                })
                .match(CommandResponse.class, commandResponse -> {
                    logger.warning("Got 'CommandResponse' message which did not implement the required interfaces "
                            + "'WithEntity' / 'WithOptionalEntity': {}", commandResponse);
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt())
                    );
                })
                .match(RetrieveStatisticsResponse.class, statisticsResponse -> {
                    logger.debug("Got 'RetrieveStatisticsResponse' message");
                    final JsonObject statisticsJson = statisticsResponse.getStatistics();
                    completeWithResult(
                            HttpResponse.create()
                                    .withEntity(CONTENT_TYPE_JSON, ByteString.fromString(statisticsJson.toString()))
                                    .withStatus(HttpStatusCode.OK.toInt())
                    );
                })
                .match(Status.Failure.class, f -> f.cause() instanceof AskTimeoutException, failure -> {
                    logger.warning("Got AskTimeoutException when a command response was expected: '{}'",
                            failure.cause().getMessage());
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.SERVICE_UNAVAILABLE.toInt())
                    );
                })
                .match(JsonRuntimeException.class, jre -> {
                    // wrap JsonRuntimeExceptions
                    final DittoJsonException cre = new DittoJsonException(jre);
                    logDittoRuntimeException(cre);
                    completeWithResult(HttpResponse.create().withStatus(cre.getStatusCode().toInt())
                            .withEntity(CONTENT_TYPE_JSON, ByteString.fromString(cre.toJsonString())));
                })
                .match(DittoRuntimeException.class, cre -> {
                    logDittoRuntimeException(cre);
                    completeWithResult(HttpResponse.create().withStatus(cre.getStatusCode().toInt())
                            .withEntity(CONTENT_TYPE_JSON, ByteString.fromString(cre.toJsonString())));
                })
                .match(ReceiveTimeout.class, receiveTimeout -> {
                    logger.info("Got ReceiveTimeout when a response was expected: '{}'", receiveTimeout);
                    final MessageTimeoutException mte =
                            new MessageTimeoutException(messageTimeout != null ? messageTimeout.getSeconds() : 0);
                    completeWithResult(HttpResponse.create().withStatus(mte.getStatusCode().toInt())
                            .withEntity(CONTENT_TYPE_JSON, ByteString.fromString(mte.toJsonString())));
                })
                .match(Status.Failure.class, f -> f.cause() instanceof AskTimeoutException, failure -> {
                    logger.warning("Got AskTimeoutException when a command response was expected: '{}'",
                            failure.cause().getMessage());
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.SERVICE_UNAVAILABLE.toInt())
                    );
                })
                .match(Status.Failure.class, failure -> failure.cause() instanceof DittoRuntimeException, failure -> {
                    final DittoRuntimeException cre = (DittoRuntimeException) failure.cause();
                    logDittoRuntimeException(cre);
                    completeWithResult(HttpResponse.create().withStatus(cre.getStatusCode().toInt())
                            .withEntity(CONTENT_TYPE_JSON, ByteString.fromString(cre.toJsonString())));
                })
                .match(Status.Failure.class, failure -> {
                    logger.error(failure.cause().fillInStackTrace(),
                            "Got Status.Failure when a command response was expected: '{}'",
                            failure.cause().getMessage());
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt())
                    );
                })
                .matchEquals(ServerRequestTimeoutMessage.INSTANCE,
                        serverRequestTimeoutMessage -> handleServerRequestTimeout())
                .matchAny(m -> {
                    logger.warning("Got unknown message, expected a command response: {}", m);
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt())
                    );
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
        } else {
            return response.withEntity(CONTENT_TYPE_JSON, ByteString.fromString(entity.toString()));
        }
    }

    private static HttpResponse createCommandResponse(final HttpRequest request, final CommandResponse commandResponse,
            final WithOptionalEntity withOptionalEntity) {

        final Function<HttpResponse, HttpResponse> addModifiedLocationHeaderForCreatedResponse =
                createModifiedLocationHeaderAddingResponseMapper(request, commandResponse);

        final Function<HttpResponse, HttpResponse> addBodyIfEntityExists =
                createBodyAddingResponseMapper(commandResponse, withOptionalEntity);

        return createHttpResponseWithHeadersAndBody(commandResponse,
                addModifiedLocationHeaderForCreatedResponse, addBodyIfEntityExists);
    }

    private static Function<HttpResponse, HttpResponse> createBodyAddingResponseMapper(
            final CommandResponse commandResponse, final WithOptionalEntity withOptionalEntity) {
        return response -> {
            if (StatusCodes.NO_CONTENT.equals(response.status())) {
                return response;
            } else {
                return withOptionalEntity.getEntity(commandResponse.getImplementedSchemaVersion())
                        .map(entity ->
                                addEntityAccordingToContentType(response, entity, commandResponse.getDittoHeaders()))
                        .orElse(response);
            }
        };
    }

    private static Function<HttpResponse, HttpResponse> createModifiedLocationHeaderAddingResponseMapper(
            final HttpRequest request, final CommandResponse commandResponse) {
        return response -> {
                if (HttpStatusCode.CREATED == commandResponse.getStatusCode()) {
                    Uri newUri = request.getUri();
                    if (!request.method().isIdempotent()) {
                        // only for not idempotent requests (e.g.: POST), add the "createdId" to the path:
                        final String uriStr = newUri.toString();
                        String createdLocation;
                        final int uriIdIndex = uriStr.indexOf(commandResponse.getId());

                        // if the uri contains the id, but *not* at the beginning
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
                } else {
                    return response;
                }
            };
    }

    private static HttpResponse createHttpResponseWithHeadersAndBody(
            final CommandResponse commandResponse, final Function<HttpResponse, HttpResponse> addHeaders,
            final Function<HttpResponse, HttpResponse> addBody) {
        HttpResponse response = HttpResponse.create().withStatus(commandResponse.getStatusCodeValue());

        return addBody.apply(addHeaders.apply(response));
    }

    private void logDittoRuntimeException(final DittoRuntimeException cre) {
        logger.info("DittoRuntimeException '{}': {}", cre.getErrorCode(), cre.getMessage());
    }

    /**
     * Creates the Akka configuration object for this {@code HttpRequestActor} for the given {@code proxyActor}, {@code
     * request}, and {@code httpResponseFuture} which will be completed with a {@link HttpResponse}.
     *
     * @param proxyActor the proxy actor which delegates commands.
     * @param request the HTTP request
     * @param httpResponseFuture the completable future which is completed with a HTTP response.
     * @return the configuration object.
     */
    public static Props props(final ActorRef proxyActor, final HttpRequest request,
            final CompletableFuture<HttpResponse> httpResponseFuture) {

        return Props.create(HttpRequestActor.class, new Creator<HttpRequestActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public HttpRequestActor create() throws Exception {
                return new HttpRequestActor(proxyActor, request, httpResponseFuture);
            }
        });
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();

        if (serverRequestTimeoutCancellable != null) {
            serverRequestTimeoutCancellable.cancel();
        }
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(MessageCommand.class, command -> { // receive MessageCommands
                    LogUtil.enhanceLogWithCorrelationId(logger, command);
                    logger.info("Got <MessageCommand> with subject <{}>, telling the targetActor about it",
                            command.getMessage().getSubject());

                    final String messageType = command.getMessageType();
                    final Message<?> message = command.getMessage();
                    final MessageDirection direction = message.getDirection();
                    newTraceFor(command, TRACE_ROUNDTRIP_HTTP + "." + messageType + "." + direction);
                    traceContext.addTag("type", messageType);
                    traceContext.addTag("direction", direction.name());
                    traceContext.addTag("subject", message.getSubject());

                    // authorized!
                    proxyActor.tell(command, getSelf());
                    getContext().become(commandResponseAwaiting);

                    messageTimeout = message.getTimeout().orElse(null);
                    if (messageTimeout != null && !isFireAndForgetMessage(command)) {
                        getContext().setReceiveTimeout(Duration.apply(messageTimeout.getSeconds(), TimeUnit.SECONDS));
                    }
                })
                .match(Status.Failure.class, failure -> {
                    Throwable cause = failure.cause();
                    if (cause instanceof JsonRuntimeException) {
                        // wrap JsonRuntimeExceptions
                        cause = new DittoJsonException((JsonRuntimeException) cause);
                    }

                    if (cause instanceof DittoRuntimeException) {
                        final DittoRuntimeException cre = (DittoRuntimeException) cause;
                        logDittoRuntimeException(cre);
                        completeWithResult(HttpResponse.create().withStatus(cre.getStatusCode().toInt())
                                .withEntity(CONTENT_TYPE_JSON, ByteString.fromString(cre.toJsonString()))
                        );
                    } else {
                        logger.error(cause, "Got unknown Status.Failure when a 'Command' was expected");
                        completeWithResult(
                                HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt())
                        );
                    }
                })
                .match(DittoRuntimeException.class, cre -> {
                    logDittoRuntimeException(cre);
                    completeWithResult(HttpResponse.create().withStatus(cre.getStatusCode().toInt())
                            .withEntity(CONTENT_TYPE_JSON, ByteString.fromString(cre.toJsonString())));
                })
                .matchEquals(ServerRequestTimeoutMessage.INSTANCE,
                        serverRequestTimeoutMessage -> handleServerRequestTimeout())
                .match(Command.class, command -> { // receive Commands
                    logger.debug("Got 'Command' message, telling the targetActor about it");

                    newTraceFor(command, TRACE_ROUNDTRIP_HTTP + "_" + command.getType());

                    proxyActor.tell(command, getSelf());

                    if (!command.getDittoHeaders().isResponseRequired()) {
                        completeWithResult(HttpResponse.create().withStatus(StatusCodes.ACCEPTED));
                        finishTraceAndStop();
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
        final Optional<HttpStatusCode> responseStatusCode =
                Optional.of(messageCommandResponse.getStatusCode())
                        .filter(code -> StatusCodes.lookup(code.toInt()).isPresent());
        // only allow status code which are known to akka-http

        // if statusCode is != NO_CONTENT
        if (responseStatusCode.map(status -> status != HttpStatusCode.NO_CONTENT).orElse(true)) {
            final Optional<ContentType> optionalContentType = message.getContentType().map(ContentType$.MODULE$::parse)
                    .filter(Either::isRight)
                    .map(Either::right)
                    .map(right -> (ContentType) right.get());

            httpResponse =
                    HttpResponse.create()
                            .withStatus(responseStatusCode.orElse(HttpStatusCode.OK).toInt());

            if (optionalPayload.isPresent()) {
                final Object payload = optionalPayload.get();

                if (optionalContentType.isPresent()) {
                    httpResponse = httpResponse.withEntity(
                            HttpEntities.create(optionalContentType.get(),
                                    ByteString.ByteStrings.fromString(payload.toString())));
                } else {
                    httpResponse = httpResponse.withEntity(
                            HttpEntities.create(payload.toString()));
                }
            } else if (optionalRawPayload.isPresent()) {

                final ByteBuffer rawPayload = optionalRawPayload.get();
                if (optionalContentType.isPresent()) {
                    httpResponse = httpResponse.withEntity(
                            HttpEntities.create(optionalContentType.get(), rawPayload.array()));
                } else {
                    httpResponse = httpResponse.withEntity(
                            HttpEntities.create(rawPayload.array()));
                }
            }
        } else {
            // if payload was missing OR statusCode was NO_CONTENT:
            optionalRawPayload.ifPresent(byteBuffer ->
                    logger.info("Response payload was set, but response statusCode was also set to: {}. Ignoring the " +
                            "response payload. Command=<{}>", responseStatusCode, messageCommandResponse)
            );
            httpResponse =
                    HttpResponse.create().withStatus(responseStatusCode.orElse(HttpStatusCode.NO_CONTENT).toInt());
        }

        return httpResponse;
    }

    private void handleServerRequestTimeout() {
        logger.warning("No response within server request timeout ({}), shutting actor down.",
                serverRequestTimeout);
        // note that we do not need to send a response here, this is handled by RequestTimeoutHandlingDirective
        finishTraceAndStop();
    }

    private void completeWithResult(final HttpResponse response) {
        httpResponseFuture.complete(response);
        logger.debug("Responding with HttpResponse code '{}'", response.status().intValue());
        if (logger.isDebugEnabled()) {
            logger.debug("Responding with Entity: {}", response.entity());
        }
        finishTraceAndStop();
    }

    private void finishTraceAndStop() {
        if (traceContext != null) {
            traceContext.finish();
            final double durationMs = (System.nanoTime() - traceContext.startTimestamp()) / NANO_TO_MS_DIVIDER;
            final Option<String> typePrefixOption = traceContext.tags().get(TRACE_TAG_TYPE_PREFIX);

            if (typePrefixOption.contains(ThingSearchCommand.TYPE_PREFIX)) {
                if (durationMs > SEARCH_WARN_TIMEOUT_MS) {
                    logger.warning("Encountered slow search which took over {}ms: {}ms",
                            (int) SEARCH_WARN_TIMEOUT_MS,
                            (int) durationMs);
                }
            } else if (durationMs > HTTP_WARN_TIMEOUT_MS) {
                logger.warning("Encountered slow HTTP request which took over {}ms: {}ms",
                        (int) HTTP_WARN_TIMEOUT_MS,
                        (int) durationMs);
            }
        }
        logger.clearMDC();
        // destroy ourself:
        getContext().stop(getSelf());
    }

    private void newTraceFor(final Command command, final String name) {
        final Optional<String> tokenOptional = command.getDittoHeaders().getCorrelationId();
        final Option<String> tokenScalaOption = tokenOptional
                .map(Option::<String>apply)
                .orElse(Option.apply(UUID.randomUUID().toString()));
        LogUtil.enhanceLogWithCorrelationId(logger, tokenOptional);
        traceContext = Kamon.tracer().newContext(name, tokenScalaOption);
        traceContext.addTag(TRACE_TAG_TYPE, command.getType());
        traceContext.addTag(TRACE_TAG_TYPE_PREFIX, command.getTypePrefix());
    }

    private static final class ServerRequestTimeoutMessage {

        private static final ServerRequestTimeoutMessage INSTANCE = new ServerRequestTimeoutMessage();

        private ServerRequestTimeoutMessage() {}

    }

}
