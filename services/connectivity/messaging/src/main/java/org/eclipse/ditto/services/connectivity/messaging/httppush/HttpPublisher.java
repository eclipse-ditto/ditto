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
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.BasePublisherActor;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.HttpPushConfig;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
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
    private final boolean dryRun;

    private final SourceQueue<Pair<HttpRequest, ExternalMessage>> sourceQueue;

    private HttpPublisher(final ConnectionId connectionId, final List<Target> targets,
            final HttpPushFactory factory, final boolean dryRun) {
        super(connectionId, targets);
        this.factory = factory;
        this.dryRun = dryRun;

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

    static Props props(final ConnectionId connectionId, final List<Target> targets, final HttpPushFactory factory,
            final boolean isDryRun) {

        return Props.create(HttpPublisher.class, connectionId, targets, factory, isDryRun);
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.match(OutboundSignal.WithExternalMessage.class, this::isDryRun,
                outbound -> log().info("Message dropped in dry run mode: {}", outbound));
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

    private boolean isDryRun() {
        return dryRun;
    }

    private HttpRequest createRequest(final HttpPublishTarget publishTarget, final ExternalMessage message) {
        final HttpRequest requestWithoutEntity = factory.newRequest(publishTarget).addHeaders(getHttpHeaders(message));
        if (message.isTextMessage()) {
            return requestWithoutEntity.withEntity(message.getTextPayload().orElse(""));
        } else {
            return requestWithoutEntity.withEntity(message.getBytePayload().map(ByteBuffer::array).orElse(new byte[0]));
        }
    }

    // TODO: add only mapped headers
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
                log.error(error, "Source queue failure");
            }
            if (error != null || queueOfferResult == QueueOfferResult.dropped()) {
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
            log.debug("Failed to send message <{}> due to <{}>", message, error);
            responsePublishedMonitor.failure(message, "Failed to send HTTP request.");
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
}
