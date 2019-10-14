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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.HttpPushConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.internal.ImmutableConnectionFailure;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.LoggingAdapter;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.headers.ContentType;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.QueueOfferResult;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueue;
import akka.util.ByteString;
import scala.util.Try;

/**
 * Actor responsible for publishing messages to an HTTP endpoint.
 */
final class HttpPublisherActor extends BasePublisherActor<HttpPublishTarget> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "httpPublisherActor";

    private static final long READ_BODY_TIMEOUT_MS = 1000L;

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final HttpPushFactory factory;
    private final HttpPushConfig config;

    private final ActorMaterializer materializer;
    private final SourceQueue<Pair<HttpRequest, HttpPushContext>> sourceQueue;
    private final Collection<String> blacklistedHostsAndIps;

    @SuppressWarnings("unused")
    private HttpPublisherActor(final ConnectionId connectionId, final List<Target> targets,
            final HttpPushFactory factory) {
        super(connectionId, targets);
        this.factory = factory;

        final ActorSystem system = getContext().getSystem();
        config = DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()))
                .getConnectionConfig()
                .getHttpPushConfig();
        blacklistedHostsAndIps = calculateBlacklistedHostnames(config.getBlacklistedHostnames(), log);

        materializer = ActorMaterializer.create(getContext());
        sourceQueue =
                Source.<Pair<HttpRequest, HttpPushContext>>queue(config.getMaxQueueSize(), OverflowStrategy.dropNew())
                        .viaMat(factory.createFlow(system, log), Keep.left())
                        .toMat(Sink.foreach(this::processResponse), Keep.left())
                        .run(materializer);
    }

    static Collection<String> calculateBlacklistedHostnames(final Collection<String> configuredBlacklistedHostnames,
            final LoggingAdapter log) {
        final Set<String> blacklistedHostsAndIps = new HashSet<>(configuredBlacklistedHostnames);
        configuredBlacklistedHostnames.stream()
                .filter(host -> !host.isEmpty())
                .forEach(host -> {
                    try {
                        Arrays.asList(InetAddress.getAllByName(host)).forEach(inetAddress -> {
                            blacklistedHostsAndIps.add(inetAddress.getHostName());
                            blacklistedHostsAndIps.add(inetAddress.getHostAddress());
                        });
                    } catch (final UnknownHostException e) {
                        log.info("Could not resolve hostname during building blacklisted hostnames set: <{}>",
                                host);
                    }
                }
        );
        return blacklistedHostsAndIps;
    }

    static Props props(final ConnectionId connectionId, final List<Target> targets, final HttpPushFactory factory) {
        return Props.create(HttpPublisherActor.class, connectionId, targets, factory);
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected HttpPublishTarget toPublishTarget(final String address) {
        return HttpPublishTarget.of(address);
    }

    @Override
    protected HttpPublishTarget toReplyTarget(final String replyToAddress) {
        return HttpPublishTarget.of(replyToAddress);
    }

    @Override
    protected void publishMessage(@Nullable final Target target, final HttpPublishTarget publishTarget,
            final ExternalMessage message, final ConnectionMonitor publishedMonitor) {

        final HttpRequest request = createRequest(publishTarget, message);
        final String requestAddress = request.getUri().host().address();
        if (blacklistedHostsAndIps.contains(requestAddress)) {
            log.warning("Tried to publish HTTP message to blacklisted host: <{}> - dropping!", requestAddress);
            responseDroppedMonitor.failure(message, "Message dropped as the target address <{0}> is blacklisted " +
                            "and may not be used", requestAddress);
        } else {
            sourceQueue.offer(Pair.create(request, new HttpPushContext(message, request.getUri())))
                    .handle(handleQueueOfferResult(message));
        }
    }

    @Override
    protected DiagnosticLoggingAdapter log() {
        return log;
    }

    @Override
    protected ExternalMessageBuilder withMappedHeaders(final ExternalMessageBuilder builder,
            final Map<String, String> mappedHeaders) {
        // instead of appending mapped headers, remove all but the mapped headers.
        return builder.withHeaders(mappedHeaders);
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
    private BiFunction<QueueOfferResult, Throwable, Void> handleQueueOfferResult(final ExternalMessage message) {
        return (queueOfferResult, error) -> {
            if (error != null) {
                final String errorDescription = "Source queue failure";
                log.error(error, errorDescription);
                responseDroppedMonitor.failure(message, "Message dropped because the connection failed");
                escalate(error, errorDescription);
            } else if (queueOfferResult == QueueOfferResult.dropped()) {
                log.debug("HTTP request dropped due to full queue");
                responseDroppedMonitor.failure(message,
                        "Message dropped because the number of ongoing requests exceeded <{0}>",
                        config.getMaxQueueSize());
            }
            return null;
        };
    }

    // Async callback. Must be thread-safe.
    private void processResponse(final Pair<Try<HttpResponse>, HttpPushContext> responseWithMessage) {
        final Try<HttpResponse> tryResponse = responseWithMessage.first();
        final HttpPushContext context = responseWithMessage.second();
        final ExternalMessage message = context.getExternalMessage();
        final Uri requestUri = context.getRequestUri();
        if (tryResponse.isFailure()) {
            final Throwable error = tryResponse.toEither().left().get();
            final String errorDescription = MessageFormat.format("Failed to send HTTP request to <{0}>.",
                    stripUserInfo(requestUri));
            log.debug("Failed to send message <{}> due to <{}>", message, error);
            responsePublishedMonitor.failure(message, errorDescription);
            escalate(error, errorDescription);
        } else {
            final HttpResponse response = tryResponse.toEither().right().get();
            log.debug("Sent message <{}>. Got response <{} {}>", message, response.status(), response.getHeaders());
            if (response.status().isSuccess()) {
                responsePublishedMonitor.success(message, "HTTP call to <{0}> successfully responded with status <{1}>.",
                        stripUserInfo(requestUri), response.status());
            } else {
                getResponseBody(response, materializer)
                        .thenAccept(body -> responsePublishedMonitor.failure(message,
                                "HTTP call to <{0}> responded with status <{1}> and body: {2}.",
                                stripUserInfo(requestUri),
                                response.status(), body)
                        )
                        .exceptionally(bodyReadError -> {
                            responsePublishedMonitor.failure(message,
                                    "HTTP call to <{0}> responded with status <{1}>. Failed to read body within {2} ms",
                                    stripUserInfo(requestUri), response.status(), READ_BODY_TIMEOUT_MS);
                            LogUtil.enhanceLogWithCorrelationId(log, message.getInternalHeaders());
                            log.info("Got <{}> when reading body of publish response to <{}>", bodyReadError,
                                    message);
                            return null;
                        });
            }
        }
    }

    private Uri stripUserInfo(final Uri requestUri) {
        return requestUri.userInfo("");
    }

    private void escalate(final Throwable error, final String description) {
        final ConnectionFailure failure = new ImmutableConnectionFailure(getSelf(), error, description);
        getContext().getParent().tell(failure, getSelf());
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

    private static CompletionStage<String> getResponseBody(final HttpResponse response,
            final ActorMaterializer materializer) {
        return response.entity()
                .toStrict(READ_BODY_TIMEOUT_MS, materializer)
                .thenApply(HttpEntity.Strict::getData)
                .thenApply(ByteString::utf8String);
    }
}
