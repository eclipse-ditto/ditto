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

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.SinkQueueWithCancel;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;
import akka.testkit.javadsl.TestKit;
import scala.util.Try;

/**
 * Tests {@link HttpPushFactory}.
 */
public final class HttpPushFactoryTest {

    private ActorSystem actorSystem;
    private ActorMaterializer mat;
    private ServerBinding binding;
    private Connection connection;
    private BlockingQueue<HttpRequest> requestQueue;
    private BlockingQueue<HttpResponse> responseQueue;

    @Before
    public void createActorSystem() {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), TestConstants.CONFIG);
        mat = ActorMaterializer.create(actorSystem);
        newBinding(0);
        connection = createHttpPushConnection(binding);
    }

    @After
    public void stopActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void appendPathToUri() {
        connection = connection.toBuilder()
                .uri("http://127.0.0.1:" + binding.localAddress().getPort() + "/path/prefix/")
                .build();
        final HttpPushFactory underTest = HttpPushFactory.of(connection);
        final HttpRequest request = underTest.newRequest(HttpPublishTarget.of("PUT:/path/appendage/"));
        assertThat(request.method()).isEqualTo(HttpMethods.PUT);
        assertThat(request.getUri().getPathString()).isEqualTo("/path/prefix/path/appendage");
    }

    @Test
    public void handleFailure() throws Exception {
        new TestKit(actorSystem) {{
            // GIVEN: An HTTP-push connection is established against localhost.
            final HttpPushFactory underTest = HttpPushFactory.of(connection);
            final Pair<SourceQueueWithComplete<HttpRequest>, SinkQueueWithCancel<Try<HttpResponse>>> pair =
                    Source.<HttpRequest>queue(10, OverflowStrategy.dropNew())
                            .map(r -> Pair.create(r, null))
                            .viaMat(underTest.createFlow(actorSystem, actorSystem.log()), Keep.left())
                            .map(Pair::first)
                            .toMat(Sink.queue(), Keep.both())
                            .run(mat);
            final SourceQueueWithComplete<HttpRequest> sourceQueue = pair.first();
            final SinkQueueWithCancel<Try<HttpResponse>> sinkQueue = pair.second();
            final HttpRequest request1 = underTest.newRequest(HttpPublishTarget.of("1"));
            final HttpRequest request2 = underTest.newRequest(HttpPublishTarget.of("2"));
            final HttpRequest request3 = underTest.newRequest(HttpPublishTarget.of("3"));
            final HttpResponse response1 = HttpResponse.create().withStatus(StatusCodes.IM_A_TEAPOT);
            final HttpResponse response3 = HttpResponse.create().withStatus(StatusCodes.BLOCKED_BY_PARENTAL_CONTROLS);

            // GIVEN: The connection is working.
            sourceQueue.offer(request1);
            assertThat(requestQueue.take().getUri()).isEqualTo(request1.getUri());
            responseQueue.offer(response1);
            final Try<HttpResponse> responseOrError1 = pullResponse(sinkQueue);
            assertThat(responseOrError1.isSuccess()).isTrue();
            assertThat(responseOrError1.get().status()).isEqualTo(response1.status());

            // WHEN: HTTP server becomes unavailable.
            binding.terminate(Duration.ofMillis(1L)).toCompletableFuture().join();
            // THEN: Request fails.
            sourceQueue.offer(request2);
            final Try<HttpResponse> responseOrError2 = pullResponse(sinkQueue);
            assertThat(responseOrError2.isSuccess()).isFalse();

            // WHEN: HTTP server becomes available again.
            refreshBinding();
            // THEN: A new request resumes and the previously failed request is discarded.
            sourceQueue.offer(request3);
            assertThat(requestQueue.take().getUri()).isEqualTo(request3.getUri());
            responseQueue.offer(response3);
            final Try<HttpResponse> responseOrError3 = pullResponse(sinkQueue);
            assertThat(responseOrError3.isSuccess()).isTrue();
            assertThat(responseOrError3.get().status()).isEqualTo(response3.status());
        }};
    }

    private void refreshBinding() {
        final int port = binding.localAddress().getPort();
        binding.terminate(Duration.ofMillis(1L)).toCompletableFuture().join();
        newBinding(port);
    }

    private void newBinding(final int port) {
        requestQueue = new LinkedBlockingQueue<>();
        responseQueue = new LinkedBlockingQueue<>();
        final Flow<HttpRequest, HttpResponse, NotUsed> handler =
                Flow.fromFunction(request -> {
                    requestQueue.offer(request);
                    return responseQueue.take();
                });
        binding = Http.get(actorSystem).bindAndHandle(handler, ConnectHttp.toHost("127.0.0.1", port), mat)
                .toCompletableFuture()
                .join();
    }

    private static Try<HttpResponse> pullResponse(final SinkQueueWithCancel<Try<HttpResponse>> responseQueue) {
        return responseQueue.pull()
                .toCompletableFuture()
                .join()
                .orElseThrow(() -> new AssertionError("Response expected"));
    }

    private static Connection createHttpPushConnection(final ServerBinding binding) {
        return ConnectivityModelFactory.newConnectionBuilder(TestConstants.createRandomConnectionId(),
                ConnectionType.HTTP_PUSH,
                ConnectivityStatus.OPEN,
                "http://127.0.0.1:" + binding.localAddress().getPort())
                .targets(singletonList(HttpPushClientActorTest.TARGET))
                .build();
    }
}
