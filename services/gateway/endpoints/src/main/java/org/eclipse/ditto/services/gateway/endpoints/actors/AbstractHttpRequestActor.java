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
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.entity.id.WithEntityId;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.contenttype.ContentType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.protocol.HeaderTranslator;
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
import org.eclipse.ditto.services.utils.cluster.JsonValueSourceRef;
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
        httpRequest = request;
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
                        completeWithResult(createHttpResponse(HttpStatus.REQUEST_ENTITY_TOO_LARGE));
                    } else {
                        logger.error(cause, "Got unknown Status.Failure when a 'Command' was expected.");
                        completeWithResult(createHttpResponse(HttpStatus.INTERNAL_SERVER_ERROR));
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
                    completeWithResult(createHttpResponse(HttpStatus.INTERNAL_SERVER_ERROR));
                })
                .build();
    }

    private static HttpResponse createHttpResponse(final HttpStatus httpStatus) {
        return HttpResponse.create().withStatus(httpStatus.getCode());
    }

    private void handleCommand(final Command<?> command) {
        try {
            logger.setCorrelationId(command);
            incomingCommandHeaders = command.getDittoHeaders();
            ackregatorStarter.start(command,
                    this::onAggregatedResponseOrError,
                    this::handleCommandWithAckregator,
                    this::handleCommandWithoutAckregator
            );
            final Receive responseBehavior = ReceiveBuilder.create()
                    .match(Acknowledgements.class, this::completeAcknowledgements)
                    .build();
            getContext().become(
                    responseBehavior.orElse(getResponseAwaitingBehavior(getTimeoutExceptionSupplier(command))));
        } catch (final DittoRuntimeException e) {
            handleDittoRuntimeException(e);
        }
    }

    private Void onAggregatedResponseOrError(final Object responseOrError) {
        getSelf().tell(responseOrError, ActorRef.noSender());
        return null;
    }

    private Void handleCommandWithAckregator(final Signal<?> command, final ActorRef aggregator) {
        logger.debug("Got <{}>. Telling the target actor about it.", command);
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
        completeWithResult(createCommandResponse(acks.getDittoHeaders(), acks.getHttpStatus(),
                mapAcknowledgementsForHttp(acks)));
    }

    private void handleCommandAndAcceptImmediately(final Signal<?> command) {
        logger.debug("Received <{}> that doesn't expect a response. Answering with status code 202 ...", command);
        proxyActor.tell(command, getSelf());
        completeWithResult(createHttpResponse(HttpStatus.ACCEPTED));
    }

    private Supplier<DittoRuntimeException> getTimeoutExceptionSupplier(final WithDittoHeaders command) {
        return () -> {
            final ActorContext context = getContext();
            final Duration receiveTimeout = context.getReceiveTimeout();
            return GatewayCommandTimeoutException.newBuilder(receiveTimeout)
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        };
    }

    private void rememberResponseLocationUri(final CommandResponse<?> commandResponse) {
        final Optional<EntityId> optionalEntityId = WithEntityId.getEntityIdOfType(EntityId.class, commandResponse);
        if (HttpStatus.CREATED.equals(commandResponse.getHttpStatus()) && optionalEntityId.isPresent()) {
            responseLocationUri =
                    getUriForLocationHeader(httpRequest, optionalEntityId.get(), commandResponse.getResourcePath());
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
        final UserInformation userInformation = DefaultUserInformation.fromAuthorizationContext(
                dittoHeaders.getAuthorizationContext());
        return WhoamiResponse.of(userInformation, dittoHeaders);
    }

    private void handleCommandWithResponse(final Signal<?> command, final Receive awaitCommandResponseBehavior) {
        logger.debug("Got <{}>. Telling the target actor about it.", command);
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

                    final var responseWithoutHeaders = createHttpResponse(commandResponse.getHttpStatus());
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
                                withEntity.getEntity(commandResponse.getImplementedSchemaVersion()).toString(),
                                contentType);
                    }
                    completeWithResult(response);
                })
                .match(CommandResponse.class, cR -> cR instanceof WithOptionalEntity, commandResponse -> {
                    logger.withCorrelationId(commandResponse).debug("Got <{}> message.", commandResponse.getType());
                    rememberResponseLocationUri(commandResponse);
                    completeWithResult(
                            createCommandResponse(commandResponse.getDittoHeaders(), commandResponse.getHttpStatus(),
                                    (WithOptionalEntity) commandResponse));
                })
                .match(ErrorResponse.class,
                        errorResponse -> handleDittoRuntimeException(errorResponse.getDittoRuntimeException()))
                .match(CommandResponse.class, commandResponse -> {
                    logger.withCorrelationId(commandResponse)
                            .error("Got 'CommandResponse' message which did neither implement 'WithEntity' nor" +
                                    " 'WithOptionalEntity': <{}>!", commandResponse);
                    completeWithResult(createHttpResponse(HttpStatus.INTERNAL_SERVER_ERROR));
                })
                .match(JsonValueSourceRef.class, this::handleJsonValueSourceRef)
                .match(Status.Failure.class, f -> f.cause() instanceof AskTimeoutException, failure -> {
                    final Throwable cause = failure.cause();
                    logger.error(cause, "Got <{}> when a command response was expected: <{}>!",
                            cause.getClass().getSimpleName(), cause.getMessage());
                    completeWithResult(createHttpResponse(HttpStatus.INTERNAL_SERVER_ERROR));
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
                    completeWithResult(createHttpResponse(HttpStatus.INTERNAL_SERVER_ERROR));
                })
                .matchAny(m -> {
                    logger.error("Got unknown message when a command response was expected: <{}>!", m);
                    completeWithResult(createHttpResponse(HttpStatus.INTERNAL_SERVER_ERROR));
                })
                .build();
    }

    private HttpResponse handleMessageResponseMessage(final MessageCommandResponse<?, ?> messageCommandResponse) {
        HttpResponse httpResponse;

        final Message<?> message = messageCommandResponse.getMessage();
        final Optional<?> optionalPayload = message.getPayload();
        final Optional<ByteBuffer> optionalRawPayload = message.getRawPayload();
        final var responseStatus = Optional.of(messageCommandResponse.getHttpStatus())
                .filter(httpStatus -> StatusCodes.lookup(httpStatus.getCode()).isPresent())
                // only allow HTTP status which are known to akka-http
                .filter(httpStatus -> !HttpStatus.BAD_GATEWAY.equals(httpStatus));
        // filter "bad gateway" 502 from being used as this is used Ditto internally for graceful HTTP shutdown

        // if statusCode is != NO_CONTENT
        if (responseStatus.map(status -> !HttpStatus.NO_CONTENT.equals(status)).orElse(true)) {
            // this is on purpose not .map(ContentTypes:parse) as this would throw an exception:
            final Optional<akka.http.scaladsl.model.ContentType> optionalContentType =
                    message.getContentType().map(ContentType$.MODULE$::parse)
                            .filter(Either::isRight)
                            .map(Either::right)
                            .map(Either.RightProjection::get);

            final boolean isBinary = optionalContentType
                    .map(akka.http.scaladsl.model.ContentType::value)
                    .map(ContentType::of)
                    .filter(ContentType::isBinary)
                    .isPresent();

            httpResponse = createHttpResponse(responseStatus.orElse(HttpStatus.OK));

            if (optionalPayload.isPresent() && optionalContentType.isPresent() && !isBinary) {
                final akka.http.scaladsl.model.ContentType contentType = optionalContentType.get();
                final Object payload = optionalPayload.get();
                final ByteString responsePayload = ByteString.fromString(payload.toString());
                httpResponse = httpResponse.withEntity(HttpEntities.create(contentType, responsePayload));
            } else if (optionalRawPayload.isPresent() && optionalContentType.isPresent() && isBinary) {
                final akka.http.scaladsl.model.ContentType contentType = optionalContentType.get();
                final ByteBuffer rawPayload = optionalRawPayload.get();
                httpResponse = httpResponse.withEntity(HttpEntities.create(contentType, rawPayload.array()));
            } else if (optionalRawPayload.isPresent()) {
                final ByteBuffer rawPayload = optionalRawPayload.get();
                httpResponse = httpResponse.withEntity(HttpEntities.create(rawPayload.array()));
            }
        } else {
            // if payload was missing OR HTTP status was NO_CONTENT:
            optionalRawPayload.ifPresent(byteBuffer -> logger.withCorrelationId(messageCommandResponse)
                    .info("Response payload was set but response status code was also set to <{}>." +
                                    " Ignoring the response payload. Command=<{}>", responseStatus,
                            messageCommandResponse));
            httpResponse = createHttpResponse(HttpStatus.NO_CONTENT);
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

        final var httpStatus = exception.getHttpStatus();
        final var responseWithoutHeaders = createHttpResponse(httpStatus);
        if (HttpStatus.NOT_MODIFIED.equals(httpStatus)) {
            return responseWithoutHeaders;
        }
        return responseWithoutHeaders.withEntity(CONTENT_TYPE_JSON, ByteString.fromString(exception.toJsonString()));
    }

    private HttpResponse enhanceResponseWithExternalDittoHeaders(final HttpResponse response,
            final DittoHeaders allDittoHeaders) {

        final DittoDiagnosticLoggingAdapter l = logger.withCorrelationId(allDittoHeaders);
        final Map<String, String> externalHeaders = getExternalHeaders(allDittoHeaders);

        if (externalHeaders.isEmpty()) {
            l.debug("No external headers for enhancing the response, returning it as-is.");
            return response;
        }

        l.debug("Enhancing response with external headers <{}>.", externalHeaders);
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

        return response.withHeaders(externalHttpHeaders);
    }

    private void completeWithResult(final HttpResponse response) {
        final HttpResponse completionResponse;
        if (isResponseRequired() || !response.status().isSuccess()) {
            // if either response was required or the response was not a success, respond with the custom response:
            completionResponse = response;
        } else {
            // when no response was required but response would have been successful, respond with 202 "Accepted":
            completionResponse = createHttpResponse(HttpStatus.ACCEPTED);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Responding with HTTP response code <{}>.", completionResponse.status().intValue());
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

    private static ContentType getContentType(final DittoHeaders dittoHeaders) {
        return dittoHeaders.getDittoContentType().orElse(ContentType.APPLICATION_JSON);
    }

    private HttpResponse createCommandResponse(final DittoHeaders dittoHeaders, final HttpStatus httpStatus,
            final WithOptionalEntity withOptionalEntity) {

        final UnaryOperator<HttpResponse> addExternalDittoHeaders =
                response -> enhanceResponseWithExternalDittoHeaders(response, dittoHeaders);
        final UnaryOperator<HttpResponse> modifyResponseOperator = this::modifyResponse;
        final var addHeaders = addExternalDittoHeaders.andThen(modifyResponseOperator);
        final var addBodyIfEntityExists = createBodyAddingResponseMapper(dittoHeaders, withOptionalEntity);
        return addBodyIfEntityExists.apply(addHeaders.apply(createHttpResponse(httpStatus)));
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
                    .map(entity -> addEntityAccordingToContentType(response, entity.toString(),
                            getContentType(dittoHeaders)))
                    .orElse(response);
        };
    }

    /**
     * Modify an HTTP response according to the HTTP response's status, add the {@code Location} header when the status
     * was {@link HttpStatus#CREATED}.
     *
     * @param response the candidate HTTP response.
     * @return the modified HTTP response.
     */
    protected HttpResponse modifyResponse(final HttpResponse response) {
        final var status = response.status();
        if (HttpStatus.CREATED.getCode() == status.intValue() && null != responseLocationUri) {
            return response.addHeader(Location.create(responseLocationUri));
        } else {
            return response;
        }
    }

    protected Uri getUriForLocationHeader(final HttpRequest request, final EntityId entityId,
            final JsonPointer resourcePath) {
        final UriForLocationHeaderSupplier supplier = new UriForLocationHeaderSupplier(request, entityId, resourcePath);
        return supplier.get();
    }

    private Acknowledgements mapAcknowledgementsForHttp(final Acknowledgements acks) {
        if (!isResponseRequired()) {
            if (acks.getHttpStatus().isSuccess()) {
                // no need to minimize payload because the response will have no body
                return acks;
            } else {
                // minimize payload to status codes

                final var acknowledgementList = acks.stream()
                        .map(ack -> Acknowledgement.of(ack.getLabel(), ack.getEntityId(), ack.getHttpStatus(),
                                DittoHeaders.empty()))
                        .collect(Collectors.toList());
                return Acknowledgements.of(acknowledgementList, acks.getDittoHeaders());
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

    private static boolean shallAcceptImmediately(final WithDittoHeaders withDittoHeaders) {
        final DittoHeaders dittoHeaders = withDittoHeaders.getDittoHeaders();
        return !dittoHeaders.isResponseRequired() && dittoHeaders.getAcknowledgementRequests().isEmpty();
    }

    private void handleJsonValueSourceRef(final JsonValueSourceRef jsonValueSourceRef) {
        logger.debug("Received <{}> from <{}>.", jsonValueSourceRef.getClass().getSimpleName(), getSender());
        final var jsonValueSourceToHttpResponse = JsonValueSourceToHttpResponse.getInstance();
        final var httpResponse = jsonValueSourceToHttpResponse.apply(jsonValueSourceRef.getSource());
        enhanceResponseWithExternalDittoHeaders(httpResponse, incomingCommandHeaders);
        completeWithResult(httpResponse);
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
