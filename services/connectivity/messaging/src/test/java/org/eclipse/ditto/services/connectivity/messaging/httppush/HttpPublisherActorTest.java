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

import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.AbstractPublisherActorTest;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.Uri;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.testkit.TestProbe;
import scala.util.Try;

public class HttpPublisherActorTest extends AbstractPublisherActorTest {

    private HttpPushFactory httpPushFactory;
    private BlockingQueue<HttpRequest> received = new LinkedBlockingQueue<>();

    @Override
    protected String getOutboundAddress() {
        return "the/quick/brown/fox/jumps/over/the/lazy/dog";
    }

    @Override
    protected void setupMocks(final TestProbe probe) {
        httpPushFactory = new DummyHttpPushFactory(request -> {
            received.offer(request);
            return HttpResponse.create().withStatus(StatusCodes.OK);
        });
    }

    @Override
    protected Props getPublisherActorProps() {
        return HttpPublisher.props(TestConstants.createRandomConnectionId(), Collections.emptyList(), httpPushFactory);
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

        // uri
        assertThat(request.getUri().host().address()).isEqualTo("127.1.2.7");
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
        assertThat(entity.getContentType().binary()).isFalse();
        assertThat(entity.getData().utf8String()).isEqualTo("payload");
    }

    private static final class DummyHttpPushFactory implements HttpPushFactory {

        private final Function<HttpRequest, HttpResponse> mapper;

        private DummyHttpPushFactory(final Function<HttpRequest, HttpResponse> mapper) {
            this.mapper = mapper;
        }

        @Override
        public HttpRequest newRequest(final HttpPublishTarget httpPublishTarget) {
            final Uri uri =
                    DefaultHttpPushFactory.appendPath(Uri.create("http://127.1.2.7:12345"),
                            httpPublishTarget.getPathSegments());
            return HttpRequest.create().withMethod(HttpMethods.GET).withUri(uri);
        }

        @Override
        public <T> Flow<Pair<HttpRequest, T>, Pair<Try<HttpResponse>, T>, ?> createFlow(final ActorSystem system,
                final LoggingAdapter log) {
            return Flow.<Pair<HttpRequest, T>>create()
                    .map(pair -> Pair.create(Try.apply(() -> mapper.apply(pair.first())), pair.second()));
        }
    }
}
