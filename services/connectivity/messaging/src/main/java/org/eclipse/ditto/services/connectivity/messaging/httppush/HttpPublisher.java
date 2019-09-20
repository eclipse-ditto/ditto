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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

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
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.QueueOfferResult;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueue;
import scala.util.Try;

final class HttpPublisher extends BasePublisherActor<HttpPublishTarget> {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final HttpPushFactory factory;
    private final HttpPushConfig config;

    private final SourceQueue<Pair<HttpRequest, ExternalMessage>> sourceQueue;

    private HttpPublisher(final ConnectionId connectionId, final List<Target> targets, final HttpPushFactory factory) {
        super(connectionId, targets);
        this.factory = factory;

        final ActorSystem system = getContext().getSystem();
        config = DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()))
                .getConnectionConfig()
                .getHttpPushConfig();

        sourceQueue =
                Source.<Pair<HttpRequest, ExternalMessage>>queue(config.getMaxQueueSize(), OverflowStrategy.dropNew())
                        .viaMat(factory.createFlow(system, log), Keep.left())
                        .toMat(Sink.foreach(this::processResponse), Keep.left())
                        .run(ActorMaterializer.create(getContext()));
    }

    static Props props(final ConnectionId connectionId, final List<Target> targets, final HttpPushFactory factory) {
        return Props.create(HttpPublisher.class, connectionId, targets, factory);
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
        sourceQueue.offer(Pair.create(request, message)).handle(handleQueueOfferResult(message));
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
        final HttpRequest requestWithoutEntity = factory.newRequest(publishTarget).addHeaders(getHttpHeaders(message));
        final Optional<akka.http.javadsl.model.headers.ContentType> contentTypeHeader =
                requestWithoutEntity.getHeader(akka.http.javadsl.model.headers.ContentType.class);
        if (contentTypeHeader.isPresent()) {
            final akka.http.javadsl.model.headers.ContentType header = contentTypeHeader.get();
            final HttpEntity.Strict httpEntity = HttpEntities.create(header.contentType(), getPayloadAsBytes(message));
            return requestWithoutEntity.removeHeader(header.name()).withEntity(httpEntity);
        } else if (message.isTextMessage()) {
            return requestWithoutEntity.withEntity(getTextPayload(message));
        } else {
            return requestWithoutEntity.withEntity(getBytePayload(message));
        }
    }

    private Iterable<HttpHeader> getHttpHeaders(final ExternalMessage message) {
        return message.getHeaders()
                .entrySet()
                .stream()
                .map(entry -> HttpHeader.parse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    // Async callback. Must be thread-safe.
    private BiFunction<QueueOfferResult, Throwable, Void> handleQueueOfferResult(final ExternalMessage message) {
        return (queueOfferResult, error) -> {
            if (error != null) {
                final String errorDescription = "Source queue failure";
                log.error(error, errorDescription);
                responseDroppedMonitor.failure(message,
                        "Message dropped because the connection failed",
                        config.getMaxQueueSize());
                escalate(error, errorDescription);
            } else if (queueOfferResult == QueueOfferResult.dropped()) {
                log.debug("HTTP request dropped due to full queue");
                responseDroppedMonitor.failure(message,
                        "Message dropped because the number of ongoing requests exceeded {0}.",
                        config.getMaxQueueSize());
            }
            return null;
        };
    }

    // Async callback. Must be thread-safe.
    private void processResponse(final Pair<Try<HttpResponse>, ExternalMessage> responseWithMessage) {
        final Try<HttpResponse> tryResponse = responseWithMessage.first();
        final ExternalMessage message = responseWithMessage.second();
        if (tryResponse.isFailure()) {
            final Throwable error = tryResponse.toEither().left().get();
            final String errorDescription = "Failed to send HTTP request.";
            log.debug("Failed to send message <{}> due to <{}>", message, error);
            responsePublishedMonitor.failure(message, errorDescription);
            escalate(error, errorDescription);
        } else {
            final HttpResponse response = tryResponse.toEither().right().get();
            log.debug("Sent message <{}>. Got response <{} {}>", message, response.status(), response.getHeaders());
            if (response.status().isSuccess()) {
                responsePublishedMonitor.success(message);
            } else {
                responsePublishedMonitor.failure(message, "Server responded with status {0}.", response.status());
            }
        }
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
}
