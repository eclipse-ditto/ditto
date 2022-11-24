/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.DefaultConnectionConfig;
import org.eclipse.ditto.connectivity.service.messaging.AbstractBaseClientActorTest;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.KillSwitches;
import akka.stream.javadsl.Flow;
import akka.testkit.javadsl.TestKit;

/**
 * Tests error handling of {@link HttpPublisherActor} against {@link HttpPushFactory}.
 */
public final class HttpPublisherErrorTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private final Queue<CompletableFuture<Void>> killSwitchTrigger = new ConcurrentLinkedQueue<>();

    private ActorSystem actorSystem;
    private ServerBinding binding;
    private Connection connection;
    private BlockingQueue<HttpRequest> requestQueue;
    private BlockingQueue<CompletableFuture<HttpResponse>> responseQueue;
    private DefaultConnectionConfig connectionConfig;

    public HttpPublisherErrorTest() {
        killSwitchTrigger.offer(new CompletableFuture<>());
    }

    private void createActorSystem(final Config config) {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), config);
        connectionConfig = DefaultConnectionConfig.of(
                DittoServiceConfig.of(
                        DefaultScopedConfig.dittoScoped(config), "connectivity"));
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
    public void connectionPoolIdleTimeoutShouldNotDisturbEventPublishing() throws Exception {
        // GIVEN: HTTP publisher actor created with extremely short connection pool timeout
        createActorSystem(ConfigFactory.load("test-timeout"));
        new TestKit(actorSystem) {{
            final HttpPushFactory factory = HttpPushFactory.of(connection, connectionConfig.getHttpPushConfig(),
                    mock(ConnectionLogger.class), SshTunnelState::disabled);
            final Props props = HttpPublisherActor.props(connection,
                    factory,
                    mock(ConnectivityStatusResolver.class),
                    ConnectivityConfig.of(actorSystem.settings().config()));
            final ActorRef underTest = watch(childActorOf(props));

            // WHEN: it is asked to publish events with delay between them larger than connection pool timeout
            final int numberOfRequests = 3;

            for (int i = 0; i < numberOfRequests; ++i) {
                responseQueue.add(CompletableFuture.completedFuture(HttpResponse.create().withStatus(200)));
            }

            for (int i = 0; i < numberOfRequests; ++i) {
                Thread.sleep(100L);
                underTest.tell(multiMapped(getRef()), getRef());

                // THEN: all events published successfully
                assertThat(requestQueue.poll(3, TimeUnit.SECONDS)).isNotNull();
            }

            // THEN: there should be no connection failure escalated to parent.
            actorSystem.stop(underTest);
            expectTerminated(underTest);
        }};
    }

    @Test
    @Ignore("TODO unignore! this test fails because the embedded test server somehow on longer reachable after it was manually recreated")
    public void closingConnectionFromServerSideShouldNotDisturbEventPublishing() throws Exception {
        createActorSystem(TestConstants.CONFIG);
        new TestKit(actorSystem) {{
            // GIVEN: An HTTP-push connection is established against localhost.
            final HttpPushFactory factory = HttpPushFactory.of(connection, connectionConfig.getHttpPushConfig(),
                    mock(ConnectionLogger.class), SshTunnelState::disabled);
            final Props props = HttpPublisherActor.props(connection, factory,
                    mock(ConnectivityStatusResolver.class), ConnectivityConfig.of(actorSystem.settings().config()));
            final ActorRef underTest = watch(childActorOf(props));

            // GIVEN: The connection is working.
            responseQueue.offer(CompletableFuture.completedFuture(HttpResponse.create().withStatus(200)));
            underTest.tell(multiMapped(ActorRef.noSender()), ActorRef.noSender());
            assertThat(requestQueue.poll(3, TimeUnit.SECONDS)).isNotNull();

            // WHEN: Server is killed
            final int port = binding.localAddress().getPort();
            binding.terminate(Duration.ofSeconds(3L)).toCompletableFuture().join();
            // THEN: event publishing should fail
            underTest.tell(multiMapped(ActorRef.noSender()), ActorRef.noSender());
            expectMsgClass(ConnectionFailure.class);
            assertThat(requestQueue.poll()).isNull();

            // WHEN: Server is available again
            newBinding(port);
            TimeUnit.SECONDS.sleep(2);
            // THEN: event publishing should succeed
            responseQueue.offer(CompletableFuture.completedFuture(HttpResponse.create().withStatus(200)));
            underTest.tell(multiMapped(ActorRef.noSender()), ActorRef.noSender());
            assertThat(requestQueue.poll(3, TimeUnit.SECONDS)).isNotNull();

            actorSystem.stop(underTest);
            expectTerminated(underTest);
        }};
    }

    private void newBinding() {
        newBinding(0);
    }

    private void newBinding(final int port) {
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
                .newServerAt("127.0.0.1", port)
                .bindFlow(handler)
                .toCompletableFuture()
                .join();
    }

    private OutboundSignal.MultiMapped multiMapped(final ActorRef sender) {
        final Signal<?> source = ThingDeleted.of(TestConstants.Things.THING_ID, 99L, Instant.now(),
                DittoHeaders.empty(), null);
        final OutboundSignal outboundSignal =
                OutboundSignalFactory.newOutboundSignal(source, List.of(TestConstants.Targets.TWIN_TARGET));
        final ExternalMessage externalMessage =
                ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap()).withText("payload").build();
        final Adaptable adaptable =
                DittoProtocolAdapter.newInstance().toAdaptable(source);
        final OutboundSignal.Mapped mapped =
                OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);
        return OutboundSignalFactory.newMultiMappedOutboundSignal(List.of(mapped), sender);
    }

    private static Connection createHttpPushConnection(final ServerBinding binding) {
        return ConnectivityModelFactory.newConnectionBuilder(TestConstants.createRandomConnectionId(),
                ConnectionType.HTTP_PUSH,
                ConnectivityStatus.OPEN,
                "http://127.0.0.1:" + binding.localAddress().getPort())
                .targets(singletonList(AbstractBaseClientActorTest.HTTP_TARGET))
                .build();
    }

}
