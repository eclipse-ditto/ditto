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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.MessageSendingFailedException;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.HttpPushConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.model.HttpCharset;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaType;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.headers.ContentType;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
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

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final HttpPushFactory factory;

    private final ActorMaterializer materializer;
    private final SourceQueue<Pair<HttpRequest, HttpPushContext>> sourceQueue;
    private final KillSwitch killSwitch;

    @SuppressWarnings("unused")
    private HttpPublisherActor(final Connection connection, final HttpPushFactory factory) {
        super(connection);
        this.factory = factory;

        final ActorSystem system = getContext().getSystem();
        final ConnectionConfig connectionConfig =
                DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()))
                        .getConnectionConfig();
        final HttpPushConfig config = connectionConfig.getHttpPushConfig();

        materializer = ActorMaterializer.create(getContext());
        final Pair<Pair<SourceQueueWithComplete<Pair<HttpRequest, HttpPushContext>>, UniqueKillSwitch>,
                CompletionStage<Done>> materialized =
                Source.<Pair<HttpRequest, HttpPushContext>>queue(config.getMaxQueueSize(), OverflowStrategy.dropNew())
                        .viaMat(factory.createFlow(system, log), Keep.left())
                        .viaMat(KillSwitches.single(), Keep.both())
                        .toMat(Sink.foreach(this::processResponse), Keep.both())
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
    protected CompletionStage<Acknowledgement> publishMessage(final Signal<?> signal,
            @Nullable final Target autoAckTarget, final HttpPublishTarget publishTarget,
            final ExternalMessage message, int ackSizeQuota) {

        final CompletableFuture<Acknowledgement> resultFuture = new CompletableFuture<>();
        final HttpRequest request = createRequest(publishTarget, message);
        final HttpPushContext context = newContext(signal, autoAckTarget, request, message, ackSizeQuota, resultFuture);
        sourceQueue.offer(Pair.create(request, context))
                .handle(handleQueueOfferResult(message, resultFuture));
        return resultFuture;
    }

    @Override
    protected DittoDiagnosticLoggingAdapter log() {
        return log;
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

    private Pair<Iterable<HttpHeader>, ContentType> getHttpHeadersPair(final ExternalMessage message) {
        final List<HttpHeader> headers = new ArrayList<>(message.getHeaders().size());
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
                log.error(error, errorDescription);
                resultFuture.completeExceptionally(error);
                escalate(error, errorDescription);
            } else if (queueOfferResult == QueueOfferResult.dropped()) {
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
    private void processResponse(final Pair<Try<HttpResponse>, HttpPushContext> responseWithContext) {
        responseWithContext.second().onResponse(responseWithContext.first());
    }

    private HttpPushContext newContext(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final HttpRequest request,
            final ExternalMessage message,
            final int ackSizeQuota,
            final CompletableFuture<Acknowledgement> resultFuture) {

        return tryResponse -> {
            final Uri requestUri = stripUserInfo(request.getUri());
            if (tryResponse.isFailure()) {
                final Throwable error = tryResponse.toEither().left().get();
                final String errorDescription = MessageFormat.format("Failed to send HTTP request to <{0}>.",
                        requestUri);
                log.debug("Failed to send message <{}> due to <{}>", message, error);
                resultFuture.completeExceptionally(error);
                escalate(error, errorDescription);
            } else {
                final HttpResponse response = tryResponse.toEither().right().get();
                log.debug("Sent message <{}>. Got response <{} {}>", message, response.status(), response.getHeaders());
                toAcknowledgement(signal, autoAckTarget, response, ackSizeQuota).thenAccept(resultFuture::complete)
                        .exceptionally(e -> {
                            resultFuture.completeExceptionally(e);
                            return null;
                        });
            }

        };
    }

    private CompletionStage<Acknowledgement> toAcknowledgement(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final HttpResponse response,
            final int ackSizeQuota) {

        // acks for non-thing-signals are for local diagnostics only, therefore it is safe to fix entity type to Thing.
        final EntityIdWithType entityIdWithType = ThingId.of(signal.getEntityId());
        final DittoHeaders dittoHeaders = setDittoHeaders(signal.getDittoHeaders(), response);
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
            return getResponseBody(response, ackSizeQuota, materializer).thenApply(body ->
                    Acknowledgement.of(label, entityIdWithType, statusCode, dittoHeaders, body)
            );
        }
    }

    private ConnectionFailure toConnectionFailure(@Nullable final Done done, @Nullable final Throwable error) {
        return new ImmutableConnectionFailure(getSelf(), error, "HttpPublisherActor stream terminated");
    }

    private static DittoHeaders setDittoHeaders(final DittoHeaders dittoHeaders, final HttpResponse response) {
        // Special handling of content-type because it needs to be extracted from the entity instead of the headers.
        final DittoHeadersBuilder dittoHeadersBuilder =
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

    private static CompletionStage<JsonValue> getResponseBody(final HttpResponse response, final int maxBytes,
            final ActorMaterializer materializer) {

        return response.entity()
                .withSizeLimit(maxBytes)
                .toStrict(READ_BODY_TIMEOUT_MS, materializer)
                .thenApply(strictEntity -> {
                    final akka.http.javadsl.model.ContentType contentType = strictEntity.getContentType();
                    final Charset charset = contentType.getCharsetOption()
                            .map(HttpCharset::nioCharset)
                            .orElse(StandardCharsets.UTF_8);
                    final byte[] bytes = strictEntity.getData().toArray();
                    if (isApplicationJson(contentType.mediaType())) {
                        // check for application/.*json first: vendor JSON types are classified incorrectly as binary
                        final String bodyString = new String(bytes, charset);
                        try {
                            return JsonFactory.readFrom(bodyString);
                        } catch (Exception e) {
                            return JsonValue.of(bodyString);
                        }
                    } else if (contentType.binary()) {
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

    /**
     * Test if a media type is application/json or application/vnd.xxx+json.
     * Akka HTTP converts subtype to lower case, why case-insensitive comparison is unnecessary.
     *
     * @param mediaType the media type to test.
     * @return whether the media type indicates a JSON content type.
     */
    private static boolean isApplicationJson(final MediaType mediaType) {
        if (mediaType.isApplication()) {
            final String subType = mediaType.subType();
            return subType.equals("json") || subType.startsWith("vnd.") && subType.endsWith("+json");
        } else {
            return false;
        }
    }
}
