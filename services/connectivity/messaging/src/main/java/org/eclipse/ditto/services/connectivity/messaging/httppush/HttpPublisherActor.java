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
package org.eclipse.ditto.services.connectivity.messaging.httppush;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.MessageSendingFailedException;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.messages.MessageHeadersBuilder;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.config.HttpPushConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessageResponse;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessage;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessageResponse;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;
import org.eclipse.ditto.signals.commands.messages.SendThingMessageResponse;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.http.javadsl.model.HttpCharset;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.headers.ContentType;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.QueueOfferResult;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueue;
import akka.stream.javadsl.SourceQueueWithComplete;
import scala.util.Try;

/**
 * Actor responsible for publishing messages to an HTTP endpoint.
 */
final class HttpPublisherActor extends BasePublisherActor<HttpPublishTarget> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "httpPublisherActor";

    private static final long READ_BODY_TIMEOUT_MS = 10000L;

    private static final AcknowledgementLabel NO_ACK_LABEL = AcknowledgementLabel.of("ditto-http-diagnostic");
    private static final DittoProtocolAdapter DITTO_PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final String LIVE_RESPONSE_NOT_OF_EXPECTED_TYPE =
            "Live response of type <%s> is not of expected type <%s>.";

    private final HttpPushFactory factory;

    private final Materializer materializer;
    private final SourceQueue<Pair<HttpRequest, HttpPushContext>> sourceQueue;
    private final KillSwitch killSwitch;

    @SuppressWarnings("unused")
    private HttpPublisherActor(final Connection connection, final HttpPushFactory factory) {
        super(connection);
        this.factory = factory;

        final HttpPushConfig config = connectionConfig.getHttpPushConfig();

        materializer = Materializer.createMaterializer(this::getContext);
        final Pair<Pair<SourceQueueWithComplete<Pair<HttpRequest, HttpPushContext>>, UniqueKillSwitch>,
                CompletionStage<Done>> materialized =
                Source.<Pair<HttpRequest, HttpPushContext>>queue(config.getMaxQueueSize(), OverflowStrategy.dropNew())
                        .viaMat(factory.createFlow(getContext().getSystem(), logger), Keep.left())
                        .viaMat(KillSwitches.single(), Keep.both())
                        .toMat(Sink.foreach(HttpPublisherActor::processResponse), Keep.both())
                        .run(materializer);
        sourceQueue = materialized.first().first();
        killSwitch = materialized.first().second();

        // Inform self of stream termination.
        // If self is alive, the error should be escalated.
        materialized.second()
                .whenComplete((done, error) -> getSelf().tell(toConnectionFailure(done, error), ActorRef.noSender()));
    }

    static Props props(final Connection connection, final HttpPushFactory factory) {
        return Props.create(HttpPublisherActor.class, connection, factory);
    }

    @Override
    public void postStop() throws Exception {
        killSwitch.shutdown();
        super.postStop();
    }

    @Override
    protected boolean shouldPublishAcknowledgement(final Acknowledgement acknowledgement) {
        return !NO_ACK_LABEL.equals(acknowledgement.getLabel());
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(ConnectionFailure.class, failure -> getContext().getParent().tell(failure, getSelf()));
    }

    @Override
    protected HttpPublishTarget toPublishTarget(final String address) {
        return HttpPublishTarget.of(address);
    }

    @Override
    protected CompletionStage<CommandResponse<?>> publishMessage(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final HttpPublishTarget publishTarget,
            final ExternalMessage message,
            final int maxTotalMessageSize,
            final int ackSizeQuota) {

        final CompletableFuture<CommandResponse<?>> resultFuture = new CompletableFuture<>();
        final HttpRequest request = createRequest(publishTarget, message);
        final HttpPushContext context = newContext(signal, autoAckTarget, request, message, maxTotalMessageSize,
                ackSizeQuota, resultFuture);
        sourceQueue.offer(Pair.create(request, context))
                .handle(handleQueueOfferResult(message, resultFuture));
        return resultFuture;
    }

    private HttpRequest createRequest(final HttpPublishTarget publishTarget, final ExternalMessage message) {
        final Pair<Iterable<HttpHeader>, ContentType> headersPair = getHttpHeadersPair(message);
        final HttpRequest requestWithoutEntity = factory.newRequest(publishTarget).addHeaders(headersPair.first());
        final ContentType contentTypeHeader = headersPair.second();
        if (contentTypeHeader != null) {
            final HttpEntity.Strict httpEntity =
                    HttpEntities.create(contentTypeHeader.contentType(), getPayloadAsBytes(message));
            return requestWithoutEntity.withEntity(httpEntity);
        } else if (message.isTextMessage()) {
            return requestWithoutEntity.withEntity(getTextPayload(message));
        } else {
            return requestWithoutEntity.withEntity(getBytePayload(message));
        }
    }

    private static Pair<Iterable<HttpHeader>, ContentType> getHttpHeadersPair(final ExternalMessage message) {
        final Collection<HttpHeader> headers = new ArrayList<>(message.getHeaders().size());
        ContentType contentType = null;
        for (final Map.Entry<String, String> entry : message.getHeaders().entrySet()) {
            final HttpHeader httpHeader = HttpHeader.parse(entry.getKey(), entry.getValue());
            if (httpHeader instanceof ContentType) {
                contentType = (ContentType) httpHeader;
            } else {
                headers.add(httpHeader);
            }
        }
        return Pair.create(headers, contentType);
    }

    // Async callback. Must be thread-safe.
    private BiFunction<QueueOfferResult, Throwable, Void> handleQueueOfferResult(final ExternalMessage message,
            final CompletableFuture<?> resultFuture) {

        return (queueOfferResult, error) -> {
            if (error != null) {
                final String errorDescription = "Source queue failure";
                logger.error(error, errorDescription);
                resultFuture.completeExceptionally(error);
                escalate(error, errorDescription);
            } else if (Objects.equals(queueOfferResult, QueueOfferResult.dropped())) {
                resultFuture.completeExceptionally(MessageSendingFailedException.newBuilder()
                        .message("Outgoing HTTP request aborted: There are too many in-flight requests.")
                        .description("Please improve the performance of the HTTP server " +
                                "or reduce the rate of outgoing signals.")
                        .dittoHeaders(message.getInternalHeaders())
                        .build());
            }
            return null;
        };
    }

    // Async callback. Must be thread-safe.
    private static void processResponse(final Pair<Try<HttpResponse>, HttpPushContext> responseWithContext) {
        responseWithContext.second().onResponse(responseWithContext.first());
    }

    private HttpPushContext newContext(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final HttpRequest request,
            final ExternalMessage message,
            final int maxTotalMessageSize,
            final int ackSizeQuota,
            final CompletableFuture<CommandResponse<?>> resultFuture) {

        return tryResponse -> {
            final Uri requestUri = stripUserInfo(request.getUri());

            final ThreadSafeDittoLoggingAdapter l;
            if (logger.isDebugEnabled()) {
                l = logger.withCorrelationId(message.getInternalHeaders());
            } else {
                l = logger;
            }

            if (tryResponse.isFailure()) {
                final Throwable error = tryResponse.toEither().left().get();
                final String errorDescription = MessageFormat.format("Failed to send HTTP request to <{0}>.",
                        requestUri);
                l.debug("Failed to send message <{}> due to <{}>", message, error);
                resultFuture.completeExceptionally(error);
                escalate(error, errorDescription);
            } else {
                final HttpResponse response = tryResponse.toEither().right().get();
                l.debug("Sent message <{}>. Got response <{} {}>", message, response.status(), response.getHeaders());

                toCommandResponseOrAcknowledgement(signal, autoAckTarget, response, maxTotalMessageSize, ackSizeQuota)
                        .thenAccept(resultFuture::complete)
                        .exceptionally(e -> {
                            resultFuture.completeExceptionally(e);
                            return null;
                        });
            }

        };
    }

    private CompletionStage<CommandResponse<?>> toCommandResponseOrAcknowledgement(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final HttpResponse response,
            final int maxTotalMessageSize,
            final int ackSizeQuota) {

        // acks for non-thing-signals are for local diagnostics only, therefore it is safe to fix entity type to Thing.
        final EntityIdWithType entityIdWithType = ThingId.of(signal.getEntityId());
        final AcknowledgementLabel label = getAcknowledgementLabel(autoAckTarget).orElse(NO_ACK_LABEL);
        final Optional<HttpStatusCode> statusOptional = HttpStatusCode.forInt(response.status().intValue());
        if (statusOptional.isEmpty()) {
            response.discardEntityBytes(materializer);
            final MessageSendingFailedException error = MessageSendingFailedException.newBuilder()
                    .message(String.format("Remote server delivers unknown HTTP status code <%d>",
                            response.status().intValue()))
                    .build();
            return CompletableFuture.failedFuture(error);
        } else {
            final HttpStatusCode statusCode = statusOptional.orElseThrow();
            final boolean isMessageCommand = signal instanceof MessageCommand;
            final int maxResponseSize = isMessageCommand ? maxTotalMessageSize : ackSizeQuota;
            return getResponseBody(response, maxResponseSize, materializer).thenApply(body -> {
                @Nullable final CommandResponse<?> result;
                final DittoHeaders dittoHeaders = setDittoHeaders(signal.getDittoHeaders(), response);
                if (DittoAcknowledgementLabel.LIVE_RESPONSE.equals(label)) {
                    // Live-Response is declared as issued ack => parse live response from response
                    if (isMessageCommand) {
                        result =
                                toMessageCommandResponse((MessageCommand<?, ?>) signal, dittoHeaders, body, statusCode);
                    } else {
                        result = null;
                    }
                } else if (NO_ACK_LABEL.equals(label)) {
                    // No Acks declared as issued acks => Handle response either as live response or as acknowledgement.
                    final boolean isDittoProtocolMessage = dittoHeaders.getDittoContentType()
                            .filter(org.eclipse.ditto.model.base.headers.contenttype.ContentType::isDittoProtocol)
                            .isPresent();
                    if (isDittoProtocolMessage && body.isObject()) {
                        final CommandResponse<?> parsedResponse = toCommandResponse(body.asObject());
                        if (parsedResponse instanceof Acknowledgement) {
                            result = parsedResponse;
                        } else if (parsedResponse instanceof MessageCommandResponse) {
                            result = parsedResponse;
                        } else {
                            result = null;
                        }
                    } else {
                        result = null;
                    }
                } else {
                    // There is an issued ack declared but its not live-response => handle response as acknowledgement.
                    result = Acknowledgement.of(label, entityIdWithType, statusCode, dittoHeaders, body);
                }

                if (result != null && isMessageCommand) {
                    // Do only add command response for live commands with a correct response.
                    return validateLiveResponse(result, (MessageCommand<?, ?>) signal);
                }
                return result;
            });
        }
    }

    private CommandResponse<?> validateLiveResponse(final CommandResponse<?> commandResponse,
            final MessageCommand<?, ?> messageCommand) {

        final ThingId messageThingId = messageCommand.getEntityId();
        final EntityId responseThingId = commandResponse.getEntityId();
        if (!responseThingId.equals(messageThingId)) {
            final String message = String.format(
                    "Live response does not target the correct thing. Expected thing ID <%s>, but was <%s>.",
                    messageThingId, responseThingId);
            handleInvalidResponse(message, commandResponse);
        }

        final String messageCorrelationId = messageCommand.getDittoHeaders().getCorrelationId().orElse(null);
        final String responseCorrelationId = commandResponse.getDittoHeaders().getCorrelationId().orElse(null);
        if (!Objects.equals(messageCorrelationId, responseCorrelationId)) {
            final String message = String.format(
                    "Correlation ID of response <%s> does not match correlation ID of message command <%s>. ",
                    responseCorrelationId, messageCorrelationId
            );
            handleInvalidResponse(message, commandResponse);
        }

        switch (messageCommand.getType()) {
            case SendClaimMessage.TYPE:
                if (!SendClaimMessageResponse.TYPE.equalsIgnoreCase(commandResponse.getType())) {
                    final String message = String.format(LIVE_RESPONSE_NOT_OF_EXPECTED_TYPE, commandResponse.getType(),
                            SendClaimMessageResponse.TYPE);
                    handleInvalidResponse(message, commandResponse);
                }
                break;
            case SendThingMessage.TYPE:
                if (!SendThingMessageResponse.TYPE.equalsIgnoreCase(commandResponse.getType())) {
                    final String message = String.format(LIVE_RESPONSE_NOT_OF_EXPECTED_TYPE, commandResponse.getType(),
                            SendThingMessageResponse.TYPE);
                    handleInvalidResponse(message, commandResponse);
                }
                break;
            case SendFeatureMessage.TYPE:
                if (!SendFeatureMessageResponse.TYPE.equalsIgnoreCase(commandResponse.getType())) {
                    final String message = String.format(LIVE_RESPONSE_NOT_OF_EXPECTED_TYPE, commandResponse.getType(),
                            SendFeatureMessageResponse.TYPE);
                    handleInvalidResponse(message, commandResponse);
                }
                final String messageFeatureId = ((SendFeatureMessage<?>) messageCommand).getFeatureId();
                final String responseFeatureId = ((SendFeatureMessageResponse<?>) commandResponse).getFeatureId();
                if (!messageFeatureId.equalsIgnoreCase(responseFeatureId)) {
                    final String message = String.format("Live response does not target the correct feature. " +
                                    "Expected feature ID <%s>, but was <%s>.",
                            messageThingId, responseThingId);
                    handleInvalidResponse(message, commandResponse);
                }
                break;
            default:
                handleInvalidResponse("Initial message command type <{}> is unknown.", commandResponse);
        }
        return commandResponse;
    }

    private void handleInvalidResponse(final String message, final CommandResponse<?> commandResponse) {
        final MessageSendingFailedException exception = MessageSendingFailedException.newBuilder()
                .statusCode(HttpStatusCode.BAD_REQUEST)
                .description(message)
                .build();
        connectionLogger.failure(commandResponse, exception);
        throw exception;
    }

    @Nullable
    private MessageCommandResponse<?, ?> toMessageCommandResponse(final MessageCommand<?, ?> messageCommand,
            final DittoHeaders dittoHeaders,
            final JsonValue jsonValue,
            final HttpStatusCode status) {

        final boolean isDittoProtocolMessage = dittoHeaders.getDittoContentType()
                .filter(org.eclipse.ditto.model.base.headers.contenttype.ContentType::isDittoProtocol)
                .isPresent();
        if (isDittoProtocolMessage && jsonValue.isObject()) {
            final CommandResponse<?> commandResponse = toCommandResponse(jsonValue.asObject());
            if (commandResponse == null) {
                return null;
            } else if (commandResponse instanceof MessageCommandResponse) {
                return (MessageCommandResponse<?, ?>) commandResponse;
            } else {
                connectionLogger.failure("Expected <{}> to be of type <{}> but was of type <{}>.",
                        commandResponse, MessageCommandResponse.class.getSimpleName(),
                        commandResponse.getClass().getSimpleName());
                return null;
            }
        } else {
            final MessageHeadersBuilder responseMessageBuilder = messageCommand.getMessage().getHeaders().toBuilder();
            final MessageHeaders messageHeaders = responseMessageBuilder.statusCode(status)
                    .putHeaders(dittoHeaders)
                    .build();
            final Message<Object> message = Message.newBuilder(messageHeaders)
                    .payload(jsonValue)
                    .build();

            switch (messageCommand.getType()) {
                case SendClaimMessage.TYPE:
                    return SendClaimMessageResponse.of(messageCommand.getThingEntityId(), message, status,
                            dittoHeaders);
                case SendThingMessage.TYPE:
                    return SendThingMessageResponse.of(messageCommand.getThingEntityId(), message, status,
                            dittoHeaders);
                case SendFeatureMessage.TYPE:
                    final SendFeatureMessage<?> sendFeatureMessage = (SendFeatureMessage<?>) messageCommand;
                    return SendFeatureMessageResponse.of(messageCommand.getThingEntityId(),
                            sendFeatureMessage.getFeatureId(), message, status, dittoHeaders);
                default:
                    connectionLogger.failure("Initial message command type <{}> is unknown.", messageCommand.getType());
                    return null;
            }
        }
    }

    @Nullable
    private CommandResponse<?> toCommandResponse(final JsonObject jsonObject) {
        final JsonifiableAdaptable jsonifiableAdaptable = ProtocolFactory.jsonifiableAdaptableFromJson(jsonObject);
        final Signal<?> signal = DITTO_PROTOCOL_ADAPTER.fromAdaptable(jsonifiableAdaptable);
        if (signal instanceof CommandResponse) {
            return (CommandResponse<?>) signal;
        } else {
            connectionLogger.exception("Expected <{}> to be of type <{}> but was of type <{}>.",
                    jsonObject, CommandResponse.class.getSimpleName(), signal.getClass().getSimpleName());
            return null;
        }

    }

    private ConnectionFailure toConnectionFailure(@Nullable final Done done, @Nullable final Throwable error) {
        return new ImmutableConnectionFailure(getSelf(), error, "HttpPublisherActor stream terminated");
    }

    private static DittoHeaders setDittoHeaders(final DittoHeaders dittoHeaders, final HttpResponse response) {
        // Special handling of content-type because it needs to be extracted from the entity instead of the headers.
        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder =
                dittoHeaders.toBuilder().contentType(response.entity().getContentType().toString());

        response.getHeaders().forEach(header -> dittoHeadersBuilder.putHeader(header.name(), header.value()));

        return dittoHeadersBuilder.build();
    }

    private static byte[] getPayloadAsBytes(final ExternalMessage message) {
        return message.isTextMessage()
                ? getTextPayload(message).getBytes()
                : getBytePayload(message);
    }

    private static String getTextPayload(final ExternalMessage message) {
        return message.getTextPayload().orElse("");
    }

    private static byte[] getBytePayload(final ExternalMessage message) {
        return message.getBytePayload().map(ByteBuffer::array).orElse(new byte[0]);
    }

    private static CompletionStage<JsonValue> getResponseBody(final HttpResponse response,
            final int maxBytes,
            final Materializer materializer) {

        return response.entity()
                .withSizeLimit(maxBytes)
                .toStrict(READ_BODY_TIMEOUT_MS, materializer)
                .thenApply(strictEntity -> {
                    final akka.http.javadsl.model.ContentType contentType = strictEntity.getContentType();
                    final Charset charset = contentType.getCharsetOption()
                            .map(HttpCharset::nioCharset)
                            .orElse(StandardCharsets.UTF_8);
                    final byte[] bytes = strictEntity.getData().toArray();
                    final org.eclipse.ditto.model.base.headers.contenttype.ContentType dittoContentType =
                            org.eclipse.ditto.model.base.headers.contenttype.ContentType.of(contentType.toString());
                    if (dittoContentType.isJson()) {
                        final String bodyString = new String(bytes, charset);
                        try {
                            return JsonFactory.readFrom(bodyString);
                        } catch (final Exception e) {
                            return JsonValue.of(bodyString);
                        }
                    } else if (dittoContentType.isBinary()) {
                        final String base64bytes = Base64.getEncoder().encodeToString(bytes);
                        return JsonFactory.newValue(base64bytes);
                    } else {
                        // add text payload as JSON string
                        return JsonFactory.newValue(new String(bytes, charset));
                    }
                });
    }

    private static Uri stripUserInfo(final Uri requestUri) {
        return requestUri.userInfo("");
    }

}

