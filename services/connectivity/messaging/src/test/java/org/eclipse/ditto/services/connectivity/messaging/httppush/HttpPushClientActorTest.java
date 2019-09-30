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
import static org.eclipse.ditto.protocoladapter.TopicPath.Channel.TWIN;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.ssl.SSLContext;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.model.connectivity.credentials.ClientCertificateCredentials;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.connectivity.messaging.AbstractBaseClientActorTest;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.SSLContextCreator;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.messaging.httppush.HttpPushClientActor}.
 */
public final class HttpPushClientActorTest extends AbstractBaseClientActorTest {

    static final Target TARGET = ConnectivityModelFactory.newTargetBuilder()
            .address("POST:/target/address")
            .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
            .headerMapping(ConnectivityModelFactory.newHeaderMapping(
                    Collections.singletonMap("content-type", "application/json")
            ))
            .topics(Topic.TWIN_EVENTS, Topic.values())
            .build();

    private static final ProtocolAdapter ADAPTER = DittoProtocolAdapter.newInstance();

    private ActorSystem actorSystem;
    private ActorMaterializer mat;
    private Flow<HttpRequest, HttpResponse, NotUsed> handler;
    private ServerBinding binding;
    private Connection connection;
    private BlockingQueue<HttpRequest> requestQueue;
    private BlockingQueue<HttpResponse> responseQueue;

    @Before
    public void createActorSystem() {
        actorSystem = ActorSystem.create(getClass().getSimpleName(), TestConstants.CONFIG);
        mat = ActorMaterializer.create(actorSystem);
        requestQueue = new LinkedBlockingQueue<>();
        responseQueue = new LinkedBlockingQueue<>();
        handler = Flow.fromFunction(request -> {
            requestQueue.offer(request);
            return responseQueue.take();
        });
        binding = Http.get(actorSystem)
                .bindAndHandle(handler, ConnectHttp.toHost("127.0.0.1", 0), mat)
                .toCompletableFuture()
                .join();
        connection = getConnectionToLocalBinding(false);
    }

    @After
    public void stopActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Override
    protected Connection getConnection() {
        return connection;
    }

    @Override
    protected Props createClientActor(final ActorRef conciergeForwarder) {
        return HttpPushClientActor.props(connection);
    }

    @Override
    protected ActorSystem getActorSystem() {
        return actorSystem;
    }

    @Test
    public void connectAndDisconnect() {
        new TestKit(actorSystem) {{
            final Props props = createClientActor(getRef());
            final ActorRef underTest = actorSystem.actorOf(props);

            underTest.tell(OpenConnection.of(connection.getId(), DittoHeaders.empty()), getRef());
            expectMsg(new Status.Success(BaseClientState.CONNECTED));

            underTest.tell(CloseConnection.of(connection.getId(), DittoHeaders.empty()), getRef());
            expectMsg(new Status.Success(BaseClientState.DISCONNECTED));
        }};
    }

    @Test
    public void testTCPConnection() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = watch(actorSystem.actorOf(createClientActor(getRef())));
            underTest.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Success("successfully connected + initialized mapper"));
            expectTerminated(underTest);
        }};
    }

    @Test
    public void testTCPConnectionFails() {
        new TestKit(actorSystem) {{
            binding.terminate(Duration.ofMillis(1L)).toCompletableFuture().join();
            final ActorRef underTest = watch(actorSystem.actorOf(createClientActor(getRef())));
            underTest.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            final Status.Failure failure = expectMsgClass(Status.Failure.class);
            assertThat(failure.cause()).isInstanceOf(ConnectionFailedException.class);
            expectTerminated(underTest);
        }};
    }

    @Test
    public void testTLSConnectionFails() {
        // GIVEN: server has a self-signed certificate
        connection = getConnectionToLocalBinding(true);
        final ClientCertificateCredentials credentials = ClientCertificateCredentials.newBuilder()
                .clientKey(TestConstants.Certificates.CLIENT_SELF_SIGNED_KEY)
                .clientCertificate(TestConstants.Certificates.CLIENT_SELF_SIGNED_CRT)
                .build();
        final SSLContext sslContext = SSLContextCreator.fromConnection(connection, DittoHeaders.empty())
                .clientCertificate(credentials);
        final HttpsConnectionContext invalidHttpsContext = ConnectionContext.https(sslContext);

        final int port = binding.localAddress().getPort();
        binding.terminate(Duration.ofMillis(1L)).toCompletableFuture().join();
        binding = Http.get(actorSystem)
                .bindAndHandle(handler,
                        ConnectHttp.toHostHttps("127.0.0.1", port).withCustomHttpsContext(invalidHttpsContext),
                        mat)
                .toCompletableFuture()
                .join();

        new TestKit(actorSystem) {{
            // WHEN: the connection is tested
            final ActorRef underTest = watch(actorSystem.actorOf(createClientActor(getRef())));
            underTest.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());

            // THEN: the test fails
            final Status.Failure failure = expectMsgClass(Status.Failure.class);
            assertThat(failure.cause()).isInstanceOf(DittoRuntimeException.class);
            assertThat(((DittoRuntimeException) failure.cause()).getDescription().orElse(""))
                    .contains("unable to find valid certification path");
            expectTerminated(underTest);
        }};
    }

    @Test
    public void publishTwinEvent() throws Exception {
        new TestKit(actorSystem) {{
            // GIVEN: local HTTP connection is connected
            final ActorRef underTest = actorSystem.actorOf(createClientActor(getRef()));
            underTest.tell(OpenConnection.of(connection.getId(), DittoHeaders.empty()), getRef());
            expectMsg(new Status.Success(BaseClientState.CONNECTED));

            // WHEN: a thing event is sent to a target with header mapping content-type=application/json
            final ThingModifiedEvent thingModifiedEvent = TestConstants.thingModified(singletonList(""))
                    .setDittoHeaders(DittoHeaders.newBuilder()
                            .correlationId("internal-correlation-id")
                            .build());
            final OutboundSignal outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(thingModifiedEvent, singletonList(TARGET));
            underTest.tell(outboundSignal, getRef());

            // THEN: a POST-request is forwarded to the path defined in the target
            final HttpRequest thingModifiedRequest = requestQueue.take();
            responseQueue.offer(HttpResponse.create().withStatus(StatusCodes.OK));
            assertThat(thingModifiedRequest.method()).isEqualTo(HttpMethods.POST);
            assertThat(thingModifiedRequest.getUri().getPathString()).isEqualTo("/target/address");

            // THEN: only headers in the header mapping are retained
            assertThat(thingModifiedRequest.entity().getContentType()).isEqualTo(ContentTypes.APPLICATION_JSON);
            assertThat(thingModifiedRequest.getHeader("correlation-id")).isEmpty();

            // THEN: the payload is the JSON string of the event as a Ditto protocol message
            assertThat(thingModifiedRequest.entity()
                    .toStrict(10_000, mat)
                    .toCompletableFuture()
                    .join()
                    .getData()
                    .utf8String()).isEqualTo(toJsonString(ADAPTER.toAdaptable(thingModifiedEvent, TWIN)));
        }};
    }

    @Test
    public void placeholderReplacement() throws Exception {
        final Target target = TestConstants.Targets.TARGET_WITH_PLACEHOLDER
                        .withAddress("PATCH:" + TestConstants.Targets.TARGET_WITH_PLACEHOLDER.getAddress());
        connection = connection.toBuilder().setTargets(singletonList(target)).build();

        new TestKit(actorSystem) {{
            // GIVEN: local HTTP connection is connected
            final ActorRef underTest = actorSystem.actorOf(createClientActor(getRef()));
            underTest.tell(OpenConnection.of(connection.getId(), DittoHeaders.empty()), getRef());
            expectMsg(new Status.Success(BaseClientState.CONNECTED));

            // WHEN: a thing event is sent to a target with header mapping content-type=application/json
            final ThingModifiedEvent thingModifiedEvent = TestConstants.thingModified(singletonList(""));
            final OutboundSignal outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(thingModifiedEvent, singletonList(target));
            underTest.tell(outboundSignal, getRef());

            // THEN: a POST-request is forwarded to the path defined in the target
            final HttpRequest thingModifiedRequest = requestQueue.take();
            responseQueue.offer(HttpResponse.create().withStatus(StatusCodes.OK));
            assertThat(thingModifiedRequest.getUri().getPathString()).isEqualTo("/target:ditto/thing@twin");
        }};
    }

    private static String toJsonString(final Adaptable adaptable) {
        return ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable).toJsonString();
    }

    private Connection getConnectionToLocalBinding(final boolean isSecure) {
        return ConnectivityModelFactory.newConnectionBuilder(TestConstants.createRandomConnectionId(),
                ConnectionType.HTTP_PUSH,
                ConnectivityStatus.OPEN,
                (isSecure ? "https" : "http") + "://127.0.0.1:" + binding.localAddress().getPort())
                .targets(singletonList(TARGET))
                .validateCertificate(isSecure)
                .build();
    }
}
