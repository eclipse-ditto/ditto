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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.base.service.config.http.DefaultHttpProxyConfig;
import org.eclipse.ditto.base.service.config.http.HttpProxyConfig;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.service.config.DefaultConnectionConfig;
import org.eclipse.ditto.connectivity.service.config.HttpPushConfig;
import org.eclipse.ditto.connectivity.service.config.OAuth2Config;
import org.eclipse.ditto.connectivity.service.messaging.AbstractBaseClientActorTest;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.InfoProviderFactory;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.impl.engine.client.ProxyConnectionFailedException;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.Authorization;
import akka.japi.Pair;
import akka.stream.KillSwitches;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.SinkQueueWithCancel;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;
import akka.testkit.javadsl.TestKit;
import scala.util.Failure;
import scala.util.Try;

/**
 * Tests {@link HttpPushFactory}.
 */
public final class HttpPushFactoryTest {

    private final Queue<CompletableFuture<Void>> killSwitchTrigger = new ConcurrentLinkedQueue<>();

    private ActorSystem actorSystem;
    private ServerBinding binding;
    private Connection connection;
    private BlockingQueue<HttpRequest> requestQueue;
    private BlockingQueue<CompletableFuture<HttpResponse>> responseQueue;
    private DefaultConnectionConfig connectionConfig;

    public HttpPushFactoryTest() {
        killSwitchTrigger.offer(new CompletableFuture<>());
    }

    @Before
    public void createActorSystem() {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), TestConstants.CONFIG);
        connectionConfig = DefaultConnectionConfig.of(
                DittoServiceConfig.of(
                        DefaultScopedConfig.dittoScoped(TestConstants.CONFIG), "connectivity"));
        newBinding();
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
        final HttpPushFactory underTest = HttpPushFactory.of(connection, connectionConfig.getHttpPushConfig(),
                mock(ConnectionLogger.class), SshTunnelState::disabled);
        final HttpRequest request = underTest.newRequest(HttpPublishTarget.of("PUT:/path/appendage/"));
        assertThat(request.method()).isEqualTo(HttpMethods.PUT);
        assertThat(request.getUri().getPathString()).isEqualTo("/path/prefix/path/appendage/");
    }

    @Test
    public void basicAuth() throws Exception {
        // GIVEN: the connection has plain credentials in the URI
        connection = connection.toBuilder()
                .uri("http://username:password@127.0.0.1:" + binding.localAddress().getPort() + "/path/prefix/")
                .build();
        final HttpPushFactory underTest = HttpPushFactory.of(connection, connectionConfig.getHttpPushConfig(),
                mock(ConnectionLogger.class), SshTunnelState::disabled);
        final Pair<SourceQueueWithComplete<HttpRequest>, SinkQueueWithCancel<Try<HttpResponse>>> pair =
                newSourceSinkQueues(underTest);
        final SourceQueueWithComplete<HttpRequest> sourceQueue = pair.first();
        final SinkQueueWithCancel<Try<HttpResponse>> sinkQueue = pair.second();
        final HttpRequest request = underTest.newRequest(HttpPublishTarget.of("PUT:/path/appendage/"));
        final HttpResponse response = HttpResponse.create().withStatus(StatusCodes.OK);

        // WHEN: request-response cycle is carried out
        responseQueue.offer(CompletableFuture.completedFuture(response));
        sourceQueue.offer(request);
        final HttpRequest actualRequest = requestQueue.take();

        // THEN: actual received request has a basic auth header
        assertThat(actualRequest.getHeader(Authorization.class))
                .contains(Authorization.basic("username", "password"));
    }

    @Test
    public void withHttpProxyConfig() throws Exception {
        // GIVEN: the connection's URI points to an unreachable host
        connection = connection.toBuilder()
                .uri("http://no.such.host.example:42/path/prefix/")
                .build();

        // GIVEN: the HTTP-push factory has the proxy configured to the test server binding
        final HttpPushFactory underTest = HttpPushFactory.of(connection, new HttpPushConfig() {
            @Override
            public int getMaxQueueSize() {
                return 0;
            }

            @Override
            public Duration getRequestTimeout() {
                return Duration.ofSeconds(10);
            }

            @Override
            public HttpProxyConfig getHttpProxyConfig() {
                return getEnabledProxyConfig(binding);
            }

            @Override
            public Map<String, String> getHmacAlgorithms() {
                return Map.of();
            }

            @Override
            public OAuth2Config getOAuth2Config() {
                return OAuth2Config.of(ConfigFactory.empty());
            }

            @Override
            public List<String> getOmitRequestBodyMethods() {
                return Collections.emptyList();
            }
        }, mock(ConnectionLogger.class), SshTunnelState::disabled);
        final Pair<SourceQueueWithComplete<HttpRequest>, SinkQueueWithCancel<Try<HttpResponse>>> pair =
                newSourceSinkQueues(underTest);
        final SourceQueueWithComplete<HttpRequest> sourceQueue = pair.first();
        final SinkQueueWithCancel<Try<HttpResponse>> sinkQueue = pair.second();
        final HttpRequest request = underTest.newRequest(HttpPublishTarget.of("PUT:/path/appendage/"));

        // WHEN: request is made
        sourceQueue.offer(request);

        // THEN: CONNECT request is made to the Akka HTTP test server.
        // THEN: Akka HTTP server rejects CONNECT request, creating a failed response
        final Optional<Try<HttpResponse>> optionalTryResponse = sinkQueue.pull().toCompletableFuture().join();
        assertThat(optionalTryResponse).isNotEmpty();
        final Try<HttpResponse> tryResponse = optionalTryResponse.get();
        assertThat(tryResponse).isInstanceOf(Failure.class);
        assertThat(tryResponse.failed().get()).isInstanceOf(ProxyConnectionFailedException.class);
        assertThat(tryResponse.failed().get().getMessage())
                .contains("proxy rejected to open a connection to no.such.host.example:42 with status code: 400");
    }

    @Test
    public void handleFailure() throws Exception {
        new TestKit(actorSystem) {{
            // GIVEN: An HTTP-push connection is established against localhost.
            final HttpPushFactory underTest =
                    HttpPushFactory.of(connection, connectionConfig.getHttpPushConfig(), mock(ConnectionLogger.class),
                            SshTunnelState::disabled);
            final Pair<SourceQueueWithComplete<HttpRequest>, SinkQueueWithCancel<Try<HttpResponse>>> pair =
                    newSourceSinkQueues(underTest);
            final SourceQueueWithComplete<HttpRequest> sourceQueue = pair.first();
            final SinkQueueWithCancel<Try<HttpResponse>> sinkQueue = pair.second();
            final HttpRequest request1 = underTest.newRequest(HttpPublishTarget.of("1"));
            final HttpRequest request2 = underTest.newRequest(HttpPublishTarget.of("2"));
            final HttpRequest request3 = underTest.newRequest(HttpPublishTarget.of("3"));
            final HttpResponse response1 = HttpResponse.create().withStatus(StatusCodes.IM_A_TEAPOT);
            final HttpResponse response3 = HttpResponse.create().withStatus(StatusCodes.BLOCKED_BY_PARENTAL_CONTROLS);

            // GIVEN: The connection is working.
            responseQueue.offer(CompletableFuture.completedFuture(response1));
            sourceQueue.offer(request1);
            assertThat(requestQueue.take().getUri()).isEqualTo(request1.getUri());
            final Try<HttpResponse> responseOrError1 = pullResponse(sinkQueue);
            assertThat(responseOrError1.isSuccess()).isTrue();
            assertThat(responseOrError1.get().status()).isEqualTo(response1.status());

            // WHEN: In-flight request is killed
            // THEN: Akka HTTP responds with status 500
            responseQueue.offer(new CompletableFuture<>());
            sourceQueue.offer(request2);
            assertThat(requestQueue.take().getUri()).isEqualTo(request2.getUri());
            shutdownAllServerStreams();
            final Try<HttpResponse> responseOrError2 = pullResponse(sinkQueue);
            assertThat(responseOrError2.isSuccess()).isTrue();
            assertThat(responseOrError2.get().status()).isEqualTo(StatusCodes.INTERNAL_SERVER_ERROR);

            // WHEN: HTTP server becomes available again.
            // THEN: A new request resumes and the previously failed request is discarded.
            responseQueue.offer(CompletableFuture.completedFuture(response3));
            sourceQueue.offer(request3);
            assertThat(requestQueue.take().getUri()).isEqualTo(request3.getUri());
            final Try<HttpResponse> responseOrError3 = pullResponse(sinkQueue);
            assertThat(responseOrError3.isSuccess()).isTrue();
            assertThat(responseOrError3.get().status()).isEqualTo(response3.status());
        }};
    }

    @Test
    public void sendRequestsInParallel() throws Exception {
        // GIVEN: the connection has the specific config parallelism=4
        connection = connection.toBuilder()
                .uri("http://127.0.0.1:" + binding.localAddress().getPort())
                .specificConfig(Map.of("parallelism", "4")) // must be a power of 2!
                .build();
        final HttpPushFactory underTest = HttpPushFactory.of(connection, connectionConfig.getHttpPushConfig(),
                mock(ConnectionLogger.class), SshTunnelState::disabled);
        final Pair<SourceQueueWithComplete<HttpRequest>, SinkQueueWithCancel<Try<HttpResponse>>> pair =
                newSourceSinkQueues(underTest);
        final SourceQueueWithComplete<HttpRequest> sourceQueue = pair.first();
        final SinkQueueWithCancel<Try<HttpResponse>> sinkQueue = pair.second();
        final HttpRequest request = underTest.newRequest(HttpPublishTarget.of("PUT:/path/appendage/"));
        final HttpResponse response = HttpResponse.create().withStatus(StatusCodes.OK);

        // WHEN: 3 requests are sent in parallel
        sourceQueue.offer(request);
        sourceQueue.offer(request);
        sourceQueue.offer(request);

        // THEN: all 3 requests are sent to the server before the first response comes back
        assertThat(requestQueue.poll(10L, TimeUnit.SECONDS)).isNotNull();
        assertThat(requestQueue.poll(10L, TimeUnit.SECONDS)).isNotNull();
        assertThat(requestQueue.poll(10L, TimeUnit.SECONDS)).isNotNull();
        responseQueue.offer(CompletableFuture.completedFuture(response));
        responseQueue.offer(CompletableFuture.completedFuture(response));
        responseQueue.offer(CompletableFuture.completedFuture(response));
        sinkQueue.pull();
        sinkQueue.pull();
        sinkQueue.pull();
    }

    @Test
    public void emitFailureOnTimeout() {
        connection = connection.toBuilder().uri("http://127.0.0.1:" + binding.localAddress().getPort()).build();
        final HttpPushFactory underTest = HttpPushFactory.of(connection, connectionConfig.getHttpPushConfig(),
                mock(ConnectionLogger.class), SshTunnelState::disabled);

        // GIVEN: the HTTP client flow is configured with very short timeout
        final Pair<SourceQueueWithComplete<HttpRequest>, SinkQueueWithCancel<Try<HttpResponse>>> pair =
                newSourceSinkQueues(underTest.createFlow(actorSystem, actorSystem.log(), Duration.ofMillis(10)));
        final SourceQueueWithComplete<HttpRequest> sourceQueue = pair.first();
        final SinkQueueWithCancel<Try<HttpResponse>> sinkQueue = pair.second();
        final HttpRequest request = underTest.newRequest(HttpPublishTarget.of("PUT:/path/appendage/"));

        for (int i = 0; i < 3; ++i) {
            // WHEN: A request is made
            sourceQueue.offer(request);

            // THEN: A timeout failure is emitted
            final Try<HttpResponse> tryResponse = sinkQueue.pull().toCompletableFuture().join().orElseThrow();

            assertThat(tryResponse.isFailure()).describedAs("expect timeout response").isTrue();
            assertThat(tryResponse.failed().get()).isInstanceOf(TimeoutException.class);

            // Offer a response to unblock the test server stream.
            responseQueue.offer(CompletableFuture.completedFuture(HttpResponse.create()));
        }
        sinkQueue.cancel();
    }

    private void newBinding() {
        requestQueue = new LinkedBlockingQueue<>();
        responseQueue = new LinkedBlockingQueue<>();

        final Flow<HttpRequest, HttpResponse, NotUsed> handler =
                Flow.fromGraph(KillSwitches.<HttpRequest>single())
                        .mapAsync(1, request -> {
                            requestQueue.offer(request);
                            return responseQueue.take();
                        })
                        .mapMaterializedValue(killSwitch -> {
                            Objects.requireNonNull(killSwitchTrigger.peek())
                                    .thenAccept(unused -> killSwitch.shutdown());
                            return NotUsed.getInstance();
                        });
        binding = Http.get(actorSystem)
                .newServerAt("127.0.0.1", 0)
                .bindFlow(handler)
                .toCompletableFuture()
                .join();
    }

    private void shutdownAllServerStreams() {
        killSwitchTrigger.offer(new CompletableFuture<>());
        Objects.requireNonNull(killSwitchTrigger.poll()).complete(null);
    }

    private Pair<SourceQueueWithComplete<HttpRequest>, SinkQueueWithCancel<Try<HttpResponse>>> newSourceSinkQueues(
            final Flow<Pair<HttpRequest, HttpPushContext>, Pair<Try<HttpResponse>, HttpPushContext>, ?> flow) {

        return Source.<HttpRequest>queue(10, OverflowStrategy.dropNew())
                .<Pair<HttpRequest, HttpPushContext>>map(r -> Pair.create(r, new TestHttpPushContext()))
                .viaMat(flow, Keep.left())
                .map(Pair::first)
                .toMat(Sink.queue(), Keep.both())
                .run(actorSystem);
    }

    static class TestHttpPushContext implements HttpPushContext {
        @Override
        public ConnectionMonitor.InfoProvider getInfoProvider() {
            return InfoProviderFactory.empty();
        }

        @Override
        public void onResponse(final Try<HttpResponse> response) {
            // no-op
        }
    }

    private Pair<SourceQueueWithComplete<HttpRequest>, SinkQueueWithCancel<Try<HttpResponse>>> newSourceSinkQueues(
            final HttpPushFactory underTest) {

        final Duration requestTimeout = Duration.ofMinutes(2);
        return newSourceSinkQueues(underTest.createFlow(actorSystem, actorSystem.log(), requestTimeout));
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
                .targets(singletonList(AbstractBaseClientActorTest.HTTP_TARGET))
                .build();
    }

    private static HttpProxyConfig getEnabledProxyConfig(final ServerBinding binding) {
        final Config config = ConfigFactory.parseString("proxy {\n" +
                "  enabled = true\n" +
                "  hostname = \"127.0.0.1\"\n" +
                "  port = " + binding.localAddress().getPort() + "\n" +
                "  username = \"username\"\n" +
                "  password = \"password\"\n" +
                "}");

        return DefaultHttpProxyConfig.ofProxy(config);
    }
}
