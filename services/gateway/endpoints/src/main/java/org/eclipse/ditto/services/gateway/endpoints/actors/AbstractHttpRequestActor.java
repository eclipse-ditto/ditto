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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.routes.whoami.DefaultUserInformation;
import org.eclipse.ditto.services.gateway.endpoints.routes.whoami.UserInformation;
import org.eclipse.ditto.services.gateway.endpoints.routes.whoami.Whoami;
import org.eclipse.ditto.services.gateway.endpoints.routes.whoami.WhoamiResponse;
import org.eclipse.ditto.services.gateway.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.services.gateway.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.services.models.acks.AcknowledgementAggregatorActorStarter;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.model.base.headers.contenttype.ContentType;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithOptionalEntity;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.base.WithEntity;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayCommandTimeoutException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceUnavailableException;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.messages.acks.MessageCommandAckRequestSetter;
import org.eclipse.ditto.signals.commands.things.acks.ThingLiveCommandAckRequestSetter;
import org.eclipse.ditto.signals.commands.things.acks.ThingModifyCommandAckRequestSetter;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ReceiveTimeout;
import akka.actor.Status;
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

    private static final akka.http.javadsl.model.ContentType CONTENT_TYPE_JSON = ContentTypes.APPLICATION_JSON;
    private static final akka.http.javadsl.model.ContentType CONTENT_TYPE_TEXT = ContentTypes.TEXT_PLAIN_UTF8;

    private final DittoDiagnosticLoggingAdapter logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ActorRef proxyActor;
    private final HeaderTranslator headerTranslator;
    private final CompletableFuture<HttpResponse> httpResponseFuture;
    private final HttpRequest httpRequest;
    private final CommandConfig commandConfig;
    private final AcknowledgementAggregatorActorStarter ackregatorStarter;
    @Nullable private Uri responseLocationUri;

    @Nullable private DittoHeaders incomingCommandHeaders = null;

    protected AbstractHttpRequestActor(final ActorRef proxyActor,
            final HeaderTranslator headerTranslator,
            final HttpRequest request,
            final CompletableFuture<HttpResponse> httpResponseFuture,
            final HttpConfig httpConfig,
            final CommandConfig commandConfig) {

        this.proxyActor = proxyActor;
        this.headerTranslator = headerTranslator;
        this.httpResponseFuture = httpResponseFuture;
        this.httpRequest = request;
        this.commandConfig = commandConfig;
        ackregatorStarter = AcknowledgementAggregatorActorStarter.of(getContext(),
                HttpAcknowledgementConfig.of(httpConfig),
                headerTranslator,
                ThingModifyCommandAckRequestSetter.getInstance(),
                ThingLiveCommandAckRequestSetter.getInstance(),
                MessageCommandAckRequestSetter.getInstance());

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
                .match(Whoami.class, this::handleWhoami)
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(ReceiveTimeout.class,
                        receiveTimeout -> handleDittoRuntimeException(GatewayServiceUnavailableException.newBuilder()
                                .dittoHeaders(DittoHeaders.empty())
                                .build()))
                .match(Command.class, this::handleCommand)
                .matchAny(m -> {
                    logger.warning("Got unknown message, expected a 'Command': {}", m);
                    completeWithResult(HttpResponse.create().withStatus(HttpStatusCode.INTERNAL_SERVER_ERROR.toInt()));
                })
                .build();
    }

    private void handleCommand(final Command<?> command) {
        incomingCommandHeaders = command.getDittoHeaders();
        ackregatorStarter.start(command,
                this::onAggregatedResponseOrError,
                this::handleCommandWithAckregator,
                this::handleCommandWithoutAckregator
        );
        final Receive responseBehavior = ReceiveBuilder.create()
                .match(Acknowledgements.class, this::completeAcknowledgements)
                .build();
        getContext().become(responseBehavior.orElse(getResponseAwaitingBehavior(getTimeoutExceptionSupplier(command))));
    }

    private Void onAggregatedResponseOrError(final Object responseOrError) {
        getSelf().tell(responseOrError, ActorRef.noSender());
        return null;
    }

    private Void handleCommandWithAckregator(final Signal<?> command, final ActorRef aggregator) {
        logger.withCorrelationId(command).debug("Got <{}>. Telling the target actor about it.", command);
        proxyActor.tell(command, aggregator);
        return null;
    }

    private Void handleCommandWithoutAckregator(final Signal<?> command) {
        if (isDevOpsCommand(command) || !shallAcceptImmediately(command)) {
            handleCommandWithResponse(command, getResponseAwaitingBehavior(getTimeoutExceptionSupplier(command)));
        } else {
            handleCommandAndAcceptImmediately(command);
        }
        return null;
    }

    private void completeAcknowledgements(final Acknowledgements acks) {
        completeWithResult(createCommandResponse(acks.getDittoHeaders(), acks.getStatusCode(),
                mapAcknowledgementsForHttp(acks)));
    }

    private void handleCommandAndAcceptImmediately(final Signal<?> command) {
        logger.withCorrelationId(command)
                .debug("Received <{}> that doesn't expect a response. Answering with status code 202 ..", command);
        proxyActor.tell(command, getSelf());
        completeWithResult(HttpResponse.create().withStatus(StatusCodes.ACCEPTED));
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

    private void rememberResponseLocationUri(final CommandResponse<?> commandResponse) {
        if (HttpStatusCode.CREATED == commandResponse.getStatusCode()) {
            responseLocationUri = getUriForLocationHeader(httpRequest, commandResponse);
        }
    }

    private DittoHeaders getExternalHeaders(final DittoHeaders dittoHeaders) {
        return DittoHeaders.of(headerTranslator.toExternalAndRetainKnownHeaders(dittoHeaders));
    }

    private void handleWhoami(final Whoami command) {
        logger.withCorrelationId(command).debug("Got Whoami.", command);
        final ActorContext context = getContext();
        final WhoamiResponse response = createWhoamiResponse(command);
        context.become(getResponseAwaitingBehavior(getTimeoutExceptionSupplier(command)));
        getSelf().tell(response, getSender());
    }

    /**
     * Provide an adequate {@link WhoamiResponse} as answer for an {@link Whoami}.
     *
     * @param request the request which should be answered.
     * @return the correct {@link WhoamiResponse} for the {@code request}.
     */
    // intentionally protected to allow overwriting this in extensions
    protected WhoamiResponse createWhoamiResponse(final Whoami request) {
        final DittoHeaders dittoHeaders = request.getDittoHeaders();
        final AuthorizationContext authContext = getAuthContextWithPrefixedSubjectsFromHeaders(dittoHeaders);
        final UserInformation userInformation = DefaultUserInformation.fromAuthorizationContext(authContext);
        return WhoamiResponse.of(userInformation, dittoHeaders);
    }

    /**
     * Gets the authorization context from ditto headers with out all non prefixed subjects.
     * This is temporarily required because {@link DittoHeaders#getAuthorizationContext} returns and auth context
     * with duplicated subjects without prefix. We can replace this method with
     * {@link DittoHeaders#getAuthorizationContext} when API 1 is removed.
     *
     * @param headers the headers to extract the auth context from.
     * @return the auth context.
     */
    @Deprecated
    private static AuthorizationContext getAuthContextWithPrefixedSubjectsFromHeaders(final DittoHeaders headers) {
        final String authContextString = headers.get(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey());
        final JsonObject authContextJson = authContextString == null ?
                JsonObject.empty() :
                JsonObject.of(authContextString);
        return AuthorizationModelFactory.newAuthContext(authContextJson);
    }

    private void handleCommandWithResponse(final Signal<?> command, final Receive awaitCommandResponseBehavior) {
        logger.withCorrelationId(command).debug("Got <{}>. Telling the target actor about it.", command);
        proxyActor.tell(command, getSelf());

        final ActorContext context = getContext();
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        if (!isDevOpsCommand(command)) {
            // DevOpsCommands do have their own timeout mechanism, don't reply with a command timeout for the user
            //  for DevOps commands, so only set the receiveTimeout for non-DevOps commands:
            context.setReceiveTimeout(
                    dittoHeaders.getTimeout()
                            // if no specific timeout was configured, use the default command timeout
                            .orElse(commandConfig.getDefaultTimeout())
            );
        }

        // After a Command was received, this Actor can only receive the correlating CommandResponse:
        context.become(awaitCommandResponseBehavior);
    }

    private static boolean isDevOpsCommand(final Signal<?> command) {
        return command instanceof DevOpsCommand;
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
                    final ContentType contentType = getContentType(commandResponse.getDittoHeaders());
                    final HttpResponse response;
                    if (entityPlainStringOptional.isPresent()) {
                        response = addEntityAccordingToContentType(responseWithoutBody,
                                entityPlainStringOptional.get(), contentType);
                    } else {
                        response = addEntityAccordingToContentType(responseWithoutBody,
                                withEntity.getEntity(commandResponse.getImplementedSchemaVersion()),
                                contentType);
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
            final Optional<akka.http.scaladsl.model.ContentType> optionalContentType =
                    message.getContentType().map(ContentType$.MODULE$::parse)
                            .filter(Either::isRight)
                            .map(Either::right)
                            .map(Either.RightProjection::get);

            httpResponse = HttpResponse.create().withStatus(responseStatusCode.orElse(HttpStatusCode.OK).toInt());

            if (optionalPayload.isPresent() && optionalContentType.isPresent()) {
                final Object payload = optionalPayload.get();
                httpResponse = httpResponse.withEntity(
                        HttpEntities.create(optionalContentType.get(), ByteString.fromString(payload.toString())));
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
        final List<HttpHeader> externalHttpHeaders = externalHeaders
                .entrySet()
                .stream()
                /*
                 * Content type is set by the entity. See response.entity().getContentType().
                 * If we set it here this will cause a WARN log.
                 */
                .filter(entry -> !entry.getKey().equalsIgnoreCase(DittoHeaderDefinition.CONTENT_TYPE.getKey()))
                .map(entry -> RawHeader.create(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        logger.discardCorrelationId();

        return response.withHeaders(externalHttpHeaders);
    }

    private void completeWithResult(final HttpResponse response) {
        final HttpResponse completionResponse;
        if (isResponseRequired() || !response.status().isSuccess()) {
            // if either response was required or the response was not a success, respond with the custom response:
            completionResponse = response;
        } else {
            // when no response was required but response would have been successful, respond with 202 "Accepted":
            completionResponse = HttpResponse.create().withStatus(StatusCodes.ACCEPTED);
        }

        final int statusCode = completionResponse.status().intValue();
        if (logger.isDebugEnabled()) {
            logger.debug("Responding with HTTP response code <{}>.", statusCode);
            logger.debug("Responding with entity <{}>.", completionResponse.entity());
        }
        httpResponseFuture.complete(completionResponse);

        stop();
    }

    private void stop() {
        logger.clearMDC();
        // destroy ourselves:
        getContext().stop(getSelf());
    }

    private static HttpResponse addEntityAccordingToContentType(final HttpResponse response, final String entityPlain,
            final ContentType contentType) {

        final ByteString byteString;

        if (contentType.isBinary()) {
            byteString = ByteString.fromArray(Base64.getDecoder().decode(entityPlain));
        } else {
            byteString = ByteString.fromString(entityPlain);
        }

        return response.withEntity(ContentTypes.parse(contentType.getValue()), byteString);
    }

    private static HttpResponse addEntityAccordingToContentType(final HttpResponse response, final JsonValue entity,
            final ContentType contentType) {

        final String entityString;

        if (contentType.isJson()) {
            entityString = entity.toString();
        } else {
            entityString = entity.asString();
        }

        return addEntityAccordingToContentType(response, entityString, contentType);
    }

    private static ContentType getContentType(final DittoHeaders dittoHeaders) {
        return dittoHeaders.getDittoContentType().orElse(ContentType.APPLICATION_JSON);
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
                    .map(entity -> addEntityAccordingToContentType(response, entity, getContentType(dittoHeaders)))
                    .orElse(response);
        };
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

    private Acknowledgements mapAcknowledgementsForHttp(final Acknowledgements acks) {
        if (!isResponseRequired()) {
            if (acks.getStatusCode().isSuccess()) {
                // no need to minimize payload because the response will have no body
                return acks;
            } else {
                // minimize payload to status codes
                return Acknowledgements.of(
                        acks.stream().map(ack ->
                                Acknowledgement.of(ack.getLabel(), ack.getEntityId(), ack.getStatusCode(),
                                        DittoHeaders.empty())
                        ).collect(Collectors.toList()),
                        acks.getDittoHeaders()
                );
            }
        } else {
            return Acknowledgements.of(
                    acks.stream().map(this::setResponseLocationForAcknowledgement).collect(Collectors.toList()),
                    acks.getDittoHeaders()
            );
        }
    }

    private boolean isResponseRequired() {
        return incomingCommandHeaders == null || incomingCommandHeaders.isResponseRequired();
    }

    private Acknowledgement setResponseLocationForAcknowledgement(final Acknowledgement acknowledgement) {
        if (DittoAcknowledgementLabel.TWIN_PERSISTED.equals(acknowledgement.getLabel())) {
            rememberResponseLocationUri(acknowledgement);
            if (responseLocationUri != null) {
                final Location location = Location.create(responseLocationUri);
                return acknowledgement.setDittoHeaders(acknowledgement.getDittoHeaders()
                        .toBuilder()
                        .putHeader(location.lowercaseName(), location.value())
                        .build());
            }
        }
        return acknowledgement;
    }

    private static boolean shallAcceptImmediately(final WithDittoHeaders<?> withDittoHeaders) {
        final DittoHeaders dittoHeaders = withDittoHeaders.getDittoHeaders();
        return !dittoHeaders.isResponseRequired() && dittoHeaders.getAcknowledgementRequests().isEmpty();
    }

    private static final class HttpAcknowledgementConfig implements AcknowledgementConfig {

        private final HttpConfig httpConfig;

        private HttpAcknowledgementConfig(final HttpConfig httpConfig) {
            this.httpConfig = httpConfig;
        }

        private static AcknowledgementConfig of(final HttpConfig httpConfig) {
            return new HttpAcknowledgementConfig(httpConfig);
        }

        @Override
        public Duration getForwarderFallbackTimeout() {
            return httpConfig.getRequestTimeout();
        }

        @Override
        public Duration getCollectorFallbackLifetime() {
            return httpConfig.getRequestTimeout();
        }

        @Override
        public Duration getCollectorFallbackAskTimeout() {
            return httpConfig.getRequestTimeout();
        }

        @Override
        public int getIssuedMaxBytes() {
            return 0;
        }
    }
}
