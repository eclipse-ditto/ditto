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
package org.eclipse.ditto.gateway.service.endpoints.actors;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.api.devops.signals.commands.DevOpsCommand;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;
import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayCommandTimeoutException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayServiceUnavailableException;
import org.eclipse.ditto.connectivity.api.messaging.monitoring.logs.AddConnectionLogEntry;
import org.eclipse.ditto.connectivity.api.messaging.monitoring.logs.LogEntryFactory;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.gateway.service.endpoints.routes.whoami.DefaultUserInformation;
import org.eclipse.ditto.gateway.service.endpoints.routes.whoami.Whoami;
import org.eclipse.ditto.gateway.service.endpoints.routes.whoami.WhoamiResponse;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;
import org.eclipse.ditto.internal.models.acks.AcknowledgementAggregatorActorStarter;
import org.eclipse.ditto.internal.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.internal.models.signal.SignalInformationPoint;
import org.eclipse.ditto.internal.models.signal.correlation.MatchingValidationResult;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.JsonValueSourceRef;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.messages.model.signals.commands.acks.MessageCommandAckRequestSetter;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.things.model.signals.commands.acks.ThingLiveCommandAckRequestSetter;
import org.eclipse.ditto.things.model.signals.commands.acks.ThingModifyCommandAckRequestSetter;

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
import scala.Option;
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

    private final ActorRef proxyActor;
    private final HeaderTranslator headerTranslator;
    private final CompletableFuture<HttpResponse> httpResponseFuture;
    private final HttpRequest httpRequest;
    private final CommandConfig commandConfig;
    private final AcknowledgementAggregatorActorStarter ackregatorStarter;
    @Nullable private Uri responseLocationUri;
    private Command<?> receivedCommand;
    private final DittoDiagnosticLoggingAdapter logger;
    private Supplier<DittoRuntimeException> timeoutExceptionSupplier;

    protected AbstractHttpRequestActor(final ActorRef proxyActor,
            final HeaderTranslator headerTranslator,
            final HttpRequest request,
            final CompletableFuture<HttpResponse> httpResponseFuture,
            final HttpConfig httpConfig,
            final CommandConfig commandConfig,
            final ActorRef connectivityShardRegionProxy) {

        this.proxyActor = proxyActor;
        this.headerTranslator = headerTranslator;
        this.httpResponseFuture = httpResponseFuture;
        httpRequest = request;
        this.commandConfig = commandConfig;
        ackregatorStarter = AcknowledgementAggregatorActorStarter.of(getContext(),
                HttpAcknowledgementConfig.of(httpConfig),
                headerTranslator,
                getResponseValidationFailureConsumer(connectivityShardRegionProxy),
                ThingModifyCommandAckRequestSetter.getInstance(),
                ThingLiveCommandAckRequestSetter.getInstance(),
                MessageCommandAckRequestSetter.getInstance());

        responseLocationUri = null;
        receivedCommand = null;
        timeoutExceptionSupplier = null;
        logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        setReceiveTimeout(httpConfig.getRequestTimeout());
    }

    private Consumer<MatchingValidationResult.Failure> getResponseValidationFailureConsumer(
            final ActorRef connectivityShardRegionProxy
    ) {
        return failure -> {
            final Consumer<AddConnectionLogEntry> addConnectionLogEntry =
                    msg -> connectivityShardRegionProxy.tell(msg, ActorRef.noSender());
            final Runnable logMissingConnectionId =
                    () -> logger.withCorrelationId(failure.getCommand())
                            .warning("Discarding invalid response as connection ID of sender could not be determined.");
            failure.getConnectionId()
                    .map(connectionId -> getAddConnectionLogEntry(ConnectionId.of(connectionId), failure))
                    .ifPresentOrElse(addConnectionLogEntry, logMissingConnectionId);
        };
    }

    private static AddConnectionLogEntry getAddConnectionLogEntry(final ConnectionId connectionId,
            final MatchingValidationResult.Failure failure) {

        final var logEntry = LogEntryFactory.getLogEntryForFailedCommandResponseRoundTrip(failure.getCommand(),
                failure.getCommandResponse(),
                failure.getDetailMessage());

        return AddConnectionLogEntry.newInstance(connectionId, logEntry);
    }

    private void setReceiveTimeout(final Duration receiveTimeout) {
        final var actorContext = getContext();
        actorContext.setReceiveTimeout(receiveTimeout);
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
            receivedCommand = command;
            setDefaultTimeoutExceptionSupplier(command);
            final var timeoutOverride = getReceiveTimeout(command, commandConfig);
            ackregatorStarter.start(command,
                    timeoutOverride,
                    this::onAggregatedResponseOrError,
                    this::handleCommandWithAckregator,
                    command1 -> handleCommandWithoutAckregator(command1, timeoutOverride)
            );
            final var responseBehavior = ReceiveBuilder.create()
                    .match(Acknowledgements.class, this::completeAcknowledgements)
                    .build();
            final var context = getContext();
            context.become(responseBehavior.orElse(getResponseAwaitingBehavior()));
        } catch (final DittoRuntimeException e) {
            handleDittoRuntimeException(e);
        }
    }

    private void setDefaultTimeoutExceptionSupplier(final WithDittoHeaders command) {
        timeoutExceptionSupplier = () -> {
            final var actorContext = getContext();

            return GatewayCommandTimeoutException.newBuilder(actorContext.getReceiveTimeout())
                    .dittoHeaders(command.getDittoHeaders()
                            .toBuilder()
                            .responseRequired(false)
                            .build())
                    .build();
        };
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

    private Void handleCommandWithoutAckregator(final Signal<?> command, final Duration timeoutOverride) {
        if (isDevOpsCommand(command) || !shallAcceptImmediately(command)) {
            handleCommandWithResponse(command, getResponseAwaitingBehavior(), timeoutOverride);
            setDefaultTimeoutExceptionSupplier(command);
        } else {
            handleCommandAndAcceptImmediately(command);
        }

        return null;
    }

    private void completeAcknowledgements(final Acknowledgements acks) {
        completeWithResult(createCommandResponse(acks.getDittoHeaders(),
                acks.getHttpStatus(),
                mapAcknowledgementsForHttp(acks)));
    }

    private void handleCommandAndAcceptImmediately(final Signal<?> command) {
        logger.debug("Received <{}> that doesn't expect a response. Answering with status code 202 ...", command);
        proxyActor.tell(command, getSelf());
        completeWithResult(createHttpResponse(HttpStatus.ACCEPTED));
    }

    private void rememberResponseLocationUri(final CommandResponse<?> commandResponse) {
        final var optionalEntityId = SignalInformationPoint.getEntityId(commandResponse);
        if (HttpStatus.CREATED.equals(commandResponse.getHttpStatus()) && optionalEntityId.isPresent()) {
            responseLocationUri =
                    getUriForLocationHeader(httpRequest, optionalEntityId.get(), commandResponse.getResourcePath());
            logger.debug("Setting responseLocationUri=<{}> from request <{}>", responseLocationUri, httpRequest);
        }
    }

    private void handleWhoami(final Whoami command) {
        logger.withCorrelationId(command).debug("Got <{}>.", command);

        final var context = getContext();
        context.become(getResponseAwaitingBehavior());

        setDefaultTimeoutExceptionSupplier(command);

        final var self = getSelf();
        self.tell(createWhoamiResponse(command), getSender());
    }

    /**
     * Provide an adequate {@link org.eclipse.ditto.gateway.service.endpoints.routes.whoami.WhoamiResponse} as answer
     * for an {@link org.eclipse.ditto.gateway.service.endpoints.routes.whoami.Whoami}.
     *
     * @param request the request which should be answered.
     * @return the correct {@link org.eclipse.ditto.gateway.service.endpoints.routes.whoami.WhoamiResponse} for
     * the {@code request}.
     */
    // intentionally protected to allow overwriting this in extensions
    protected WhoamiResponse createWhoamiResponse(final Whoami request) {
        final var dittoHeaders = request.getDittoHeaders();
        final var userInformation =
                DefaultUserInformation.fromAuthorizationContext(dittoHeaders.getAuthorizationContext());

        return WhoamiResponse.of(userInformation, dittoHeaders);
    }

    private void handleCommandWithResponse(final Signal<?> command, final Receive awaitCommandResponseBehavior,
            final Duration timeoutOverride) {
        logger.debug("Got <{}>. Telling the target actor about it.", command);
        proxyActor.tell(command, getSelf());

        final ActorContext context = getContext();
        if (!isDevOpsCommand(command)) {
            // DevOpsCommands do have their own timeout mechanism, don't reply with a command timeout for the user
            //  for DevOps commands, so only set the receiveTimeout for non-DevOps commands:
            context.setReceiveTimeout(timeoutOverride);
        }

        // After a Command was received, this Actor can only receive the correlating CommandResponse:
        context.become(awaitCommandResponseBehavior);
    }

    private static boolean isDevOpsCommand(final Signal<?> command) {
        return command instanceof DevOpsCommand;
    }

    private Receive getResponseAwaitingBehavior() {
        return ReceiveBuilder.create()
                .matchEquals(COMPLETE_MESSAGE, s -> logger.debug("Got stream's <{}> message.", COMPLETE_MESSAGE))

                // If an actor downstream replies with an HTTP response, simply forward it.
                .match(HttpResponse.class, this::completeWithResult)
                .match(MessageCommandResponse.class,
                        messageCommandResponse -> completeWithResult(
                                handleMessageResponseMessage(messageCommandResponse)))
                .match(CommandResponse.class, WithEntity.class::isInstance, commandResponse -> {
                    logger.withCorrelationId(commandResponse).debug("Got <{}> message.", commandResponse.getType());
                    handleCommandResponseWithEntity(commandResponse);
                })
                .match(CommandResponse.class, WithOptionalEntity.class::isInstance, commandResponse -> {
                    logger.withCorrelationId(commandResponse).debug("Got <{}> message.", commandResponse.getType());
                    handleCommandResponseWithOptionalEntity(commandResponse);
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
                    final var cause = failure.cause();
                    logger.error(cause, "Got <{}> when a command response was expected: <{}>!",
                            cause.getClass().getSimpleName(), cause.getMessage());
                    completeWithResult(createHttpResponse(HttpStatus.INTERNAL_SERVER_ERROR));
                })

                // wrap JsonRuntimeExceptions
                .match(JsonRuntimeException.class, jre -> handleDittoRuntimeException(new DittoJsonException(jre)))
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(ReceiveTimeout.class, receiveTimeout -> handleReceiveTimeout())
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

    private void handleCommandResponseWithEntity(final CommandResponse<?> commandResponse) {
        rememberResponseLocationUri(commandResponse);

        final var withEntity = (WithEntity<?>) commandResponse;

        final var responseWithoutHeaders = createHttpResponse(commandResponse.getHttpStatus());
        final var responseWithoutBody = enhanceResponseWithExternalDittoHeaders(responseWithoutHeaders,
                commandResponse.getDittoHeaders());

        final var contentType = getContentType(commandResponse.getDittoHeaders());
        final var response = withEntity.getEntityPlainString()
                .map(s -> addEntityAccordingToContentType(responseWithoutBody, s, contentType))
                .orElseGet(() -> addEntityAccordingToContentType(responseWithoutBody,
                        withEntity.getEntity(commandResponse.getImplementedSchemaVersion()).toString(),
                        contentType));
        completeWithResult(response);
    }

    private void handleCommandResponseWithOptionalEntity(final CommandResponse<?> commandResponse) {
        rememberResponseLocationUri(commandResponse);
        completeWithResult(createCommandResponse(commandResponse.getDittoHeaders(),
                commandResponse.getHttpStatus(),
                (WithOptionalEntity) commandResponse));
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
            final Optional<akka.http.scaladsl.model.ContentType> optionalContentType = message.getContentType()
                    .map(ContentType$.MODULE$::parse)
                    .filter(Either::isRight)
                    .map(Either::toOption)
                    .map(Option::get);

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

    private void handleReceiveTimeout() {
        final var actorContext = getContext();
        final var receiveTimeout = actorContext.getReceiveTimeout();

        logger.setCorrelationId(SignalInformationPoint.getCorrelationId(receivedCommand).orElse(null));
        logger.info("Got <{}> after <{}> before an appropriate response arrived.",
                ReceiveTimeout.class.getSimpleName(),
                receiveTimeout);

        if (null != timeoutExceptionSupplier) {
            final var timeoutException = timeoutExceptionSupplier.get();
            handleDittoRuntimeException(timeoutException);
        } else {

            // This case is a programming error that should not happen at all.
            logger.error("Actor does not have a timeout exception supplier." +
                    " Thus, no DittoRuntimeException could be handled.");
        }
    }

    private void handleDittoRuntimeException(final DittoRuntimeException exception) {
        logger.withCorrelationId(exception)
                .info("DittoRuntimeException <{}>: <{}>.", exception.getErrorCode(), exception.getMessage());

        final var responseWithoutHeaders = buildResponseWithoutHeadersFromDittoRuntimeException(exception);
        final var response =
                enhanceResponseWithExternalDittoHeaders(responseWithoutHeaders, exception.getDittoHeaders());

        completeWithResult(response);
    }

    private static HttpResponse buildResponseWithoutHeadersFromDittoRuntimeException(
            final DittoRuntimeException exception) {

        final HttpResponse result;
        final var httpStatus = exception.getHttpStatus();
        final var responseWithoutHeaders = createHttpResponse(httpStatus);
        if (HttpStatus.NOT_MODIFIED.equals(httpStatus)) {
            result = responseWithoutHeaders;
        } else {
            result = responseWithoutHeaders.withEntity(CONTENT_TYPE_JSON,
                    ByteString.fromString(exception.toJsonString()));
        }

        return result;
    }

    private HttpResponse enhanceResponseWithExternalDittoHeaders(final HttpResponse response,
            final DittoHeaders allDittoHeaders) {

        final HttpResponse result;

        final var externalHeaders = headerTranslator.toExternalAndRetainKnownHeaders(allDittoHeaders);
        final var l = logger.withCorrelationId(allDittoHeaders);
        if (externalHeaders.isEmpty()) {
            l.debug("No external headers for enhancing the response, returning it as-is.");
            result = response;
        } else {
            l.debug("Enhancing response with external headers <{}>.", externalHeaders);
            final var externalHeadersEntries = externalHeaders.entrySet();

            /*
             * Content type is set by the entity.
             * See response.entity().getContentType().
             * If we set it here this will cause a WARN log.
             */
            final Predicate<Map.Entry<String, String>> isContentType = headerEntry -> {
                final var headerName = headerEntry.getKey();
                return headerName.equalsIgnoreCase(DittoHeaderDefinition.CONTENT_TYPE.getKey());
            };
            final List<HttpHeader> externalHttpHeaders = externalHeadersEntries.stream()
                    .filter(Predicate.not(isContentType))
                    .map(entry -> RawHeader.create(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
            result = response.withHeaders(externalHttpHeaders);
        }

        return result;
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

        logger.debug("Responding with HTTP response <{}>.", completionResponse);
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
        final var addBodyIfEntityExists =
                createBodyAddingResponseMapper(dittoHeaders, withOptionalEntity);

        return addBodyIfEntityExists.apply(addHeaders.apply(createHttpResponse(httpStatus)));
    }

    private static UnaryOperator<HttpResponse> createBodyAddingResponseMapper(final DittoHeaders dittoHeaders,
            final WithOptionalEntity withOptionalEntity) {

        return response -> {
            if (StatusCodes.NO_CONTENT.equals(response.status())) {
                return response;
            }
            final var schemaVersion = dittoHeaders.getSchemaVersion()
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

    protected Uri getUriForLocationHeader(final HttpRequest request,
            final EntityId entityId,
            final JsonPointer resourcePath) {
        final var supplier = new UriForLocationHeaderSupplier(request, entityId, resourcePath);

        return supplier.get();
    }

    private Acknowledgements mapAcknowledgementsForHttp(final Acknowledgements acks) {
        final Acknowledgements result;
        if (!isResponseRequired()) {
            if (acks.getHttpStatus().isSuccess()) {
                // no need to minimize payload because the response will have no body
                result = acks;
            } else {
                // minimize payload to status codes

                final var acknowledgementList = acks.stream()
                        .map(ack -> Acknowledgement.of(ack.getLabel(),
                                ack.getEntityId(),
                                ack.getHttpStatus(),
                                DittoHeaders.empty()))
                        .collect(Collectors.toList());
                result = Acknowledgements.of(acknowledgementList, acks.getDittoHeaders());
            }
        } else {
            result = Acknowledgements.of(
                    acks.stream().map(this::setResponseLocationForAcknowledgement).collect(Collectors.toList()),
                    acks.getDittoHeaders()
            );
        }

        return result;
    }

    private boolean isResponseRequired() {
        final boolean result;
        if (null != receivedCommand) {
            final var commandDittoHeaders = receivedCommand.getDittoHeaders();
            result = commandDittoHeaders.isResponseRequired();
        } else {
            result = true;
        }

        return result;
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
        enhanceResponseWithExternalDittoHeaders(httpResponse, receivedCommand.getDittoHeaders());
        completeWithResult(httpResponse);
    }

    private static Duration getReceiveTimeout(final Signal<?> originatingSignal, final CommandConfig commandConfig) {

        final var defaultTimeout = commandConfig.getDefaultTimeout();
        final var maxTimeout = commandConfig.getMaxTimeout();
        final var headers = originatingSignal.getDittoHeaders();
        final var candidateTimeout = headers.getTimeout()
                .or(() -> Optional.of(defaultTimeout))
                .filter(timeout -> timeout.minus(maxTimeout).isNegative())
                .orElse(maxTimeout);

        if (SignalInformationPoint.isChannelSmart(originatingSignal)) {
            return candidateTimeout.plus(commandConfig.getSmartChannelBuffer());
        } else {
            return candidateTimeout;
        }
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
