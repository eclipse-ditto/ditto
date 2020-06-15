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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.AbstractPublisherActorTest;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.Uri;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.Attributes;
import akka.stream.javadsl.Flow;
import akka.testkit.TestProbe;
import scala.util.Try;

/**
 * Tests {@link HttpPublisherActor}.
 */
public final class HttpPublisherActorTest extends AbstractPublisherActorTest {

    private static final String BODY = "The quick brown fox jumps over the lazy dog.";

    private HttpPushFactory httpPushFactory;
    private final BlockingQueue<HttpRequest> received = new LinkedBlockingQueue<>();

    @Override
    protected String getOutboundAddress() {
        return "PATCH:/the/quick/brown/fox/jumps/over/the/lazy/dog?someQuery=foo";
    }

    @Override
    protected void setupMocks(final TestProbe probe) {
        httpPushFactory = new DummyHttpPushFactory("8.8.4.4", request -> {
            received.offer(request);
            return HttpResponse.create()
                    .withStatus(StatusCodes.OK)
                    .withEntity(BODY);
        });

        // activate debug log to show responses
        actorSystem.eventStream().setLogLevel(Attributes.logLevelDebug());
    }

    @Override
    protected Props getPublisherActorProps() {
        return HttpPublisherActor.props(TestConstants.createConnection(), httpPushFactory);
    }

    @Override
    protected Target decorateTarget(final Target target) {
        return target;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    protected void verifyPublishedMessage() throws Exception {
        final HttpRequest request = received.take();
        assertThat(received).hasSize(0);

        // method
        assertThat(request.method()).isEqualTo(HttpMethods.PATCH);

        // uri
        assertThat(request.getUri().host().address()).isEqualTo("8.8.4.4");
        assertThat(request.getUri().port()).isEqualTo(12345);

        // headers
        assertThat(request.getHeader("thing_id").get().value()).isEqualTo(TestConstants.Things.THING_ID.toString());
        assertThat(request.getHeader("suffixed_thing_id").get().value())
                .contains(TestConstants.Things.THING_ID + ".some.suffix");
        assertThat(request.getHeader("prefixed_thing_id").get().value())
                .isEqualTo("some.prefix." + TestConstants.Things.THING_ID);
        assertThat(request.getHeader("eclipse").get().value()).isEqualTo("ditto");
        assertThat(request.getHeader("device_id").get().value()).isEqualTo(TestConstants.Things.THING_ID.toString());

        final HttpEntity.Strict entity = request.entity()
                .toStrict(60_000L, ActorMaterializer.create(actorSystem))
                .toCompletableFuture()
                .join();
        assertThat(entity.getData().utf8String()).isEqualTo("payload");
        if (!entity.getContentType().toString().equals(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)) {
            // Ditto protocol content type is parsed as binary for some reason
            assertThat(entity.getContentType().binary()).isFalse();
        }
    }

    @Override
    protected void verifyAcknowledgements(final Supplier<Acknowledgements> ackSupplier) {
        final Acknowledgements acks = ackSupplier.get();
        assertThat(acks.getSize()).describedAs("Expect 1 acknowledgement in: " + acks).isEqualTo(1);
        final Acknowledgement ack = acks.stream().findAny().orElseThrow();
        assertThat(ack.getLabel().toString()).describedAs("Ack label").isEqualTo("please-verify");
        assertThat(ack.getStatusCode()).describedAs("Ack status").isEqualTo(HttpStatusCode.OK);
        assertThat(ack.getEntity()).contains(JsonValue.of(BODY));
    }

    @Override
    protected void verifyPublishedMessageToReplyTarget() throws Exception {
        final HttpRequest request = received.take();
        assertThat(received).hasSize(0);
        assertThat(request.method()).isEqualTo(HttpMethods.POST);
        assertThat(request.getUri().getPathString()).isEqualTo("/replyTarget/thing:id");
        assertThat(request.getHeader("correlation-id"))
                .contains(HttpHeader.parse("correlation-id", TestConstants.CORRELATION_ID));
        assertThat(request.getHeader("mappedHeader2"))
                .contains(HttpHeader.parse("mappedHeader2", "thing:id"));
    }

    private static final class DummyHttpPushFactory implements HttpPushFactory {

        private final String hostname;
        private final Function<HttpRequest, HttpResponse> mapper;

        private DummyHttpPushFactory(final String hostname, final Function<HttpRequest, HttpResponse> mapper) {
            this.hostname = hostname;
            this.mapper = mapper;
        }

        @Override
        public HttpRequest newRequest(final HttpPublishTarget httpPublishTarget) {
            final String separator = httpPublishTarget.getPathWithQuery().startsWith("/") ? "" : "/";
            final Uri uri =
                    Uri.create("http://" + hostname + ":12345" + separator + httpPublishTarget.getPathWithQuery());
            return HttpRequest.create().withMethod(httpPublishTarget.getMethod()).withUri(uri);
        }

        @Override
        public <T> Flow<Pair<HttpRequest, T>, Pair<Try<HttpResponse>, T>, ?> createFlow(final ActorSystem system,
                final LoggingAdapter log) {
            return Flow.<Pair<HttpRequest, T>>create()
                    .map(pair -> Pair.create(Try.apply(() -> mapper.apply(pair.first())), pair.second()));
        }
    }

}
