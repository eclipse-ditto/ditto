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

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionFailedException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.service.messaging.AbstractBaseClientActorTest;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.SSLContextCreator;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.things.model.signals.events.ThingModifiedEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Flow;
import akka.testkit.javadsl.TestKit;
import akka.util.ByteString;

/**
 * Tests {@link HttpPushClientActor}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class HttpPushClientActorTest extends AbstractBaseClientActorTest {

    private static final ProtocolAdapter ADAPTER = DittoProtocolAdapter.newInstance();

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance(
            TestConstants.CONFIG.withValue(
                    "ditto.connectivity.connection.http-push.blocked-hostnames",
                    ConfigValueFactory.fromAnyRef("")
            )
    );

    private ActorSystem actorSystem;
    private Flow<HttpRequest, HttpResponse, NotUsed> handler;
    private ServerBinding binding;
    private Connection connection;
    private BlockingQueue<HttpRequest> requestQueue;
    private BlockingQueue<HttpResponse> responseQueue;
    @Mock
    private ConnectionLogger connectionLogger;

    @Before
    public void createActorSystem() {
        // create actor system with deactivated hostname blocklist to connect to localhost
        actorSystem = ActorSystem.create(getClass().getSimpleName(),
                TestConstants.CONFIG.withValue("ditto.connectivity.connection.http-push.blocked-hostnames",
                        ConfigValueFactory.fromAnyRef("")));
        requestQueue = new LinkedBlockingQueue<>();
        responseQueue = new LinkedBlockingQueue<>();
        handler = Flow.fromFunction(request -> {
            requestQueue.offer(request);
            return responseQueue.take();
        });
        binding = Http.get(actorSystem)
                .newServerAt("127.0.0.1", 0)
                .bindFlow(handler)
                .toCompletableFuture()
                .join();
        connection = getHttpConnectionBuilderToLocalBinding(false, binding.localAddress().getPort()).build();
    }

    @After
    public void stopActorSystem() {
        if (actorSystem != null) {
            actorSystem.terminate();
        }
    }

    @Override
    protected Connection getConnection(final boolean isSecure) {
        return isSecure ? setScheme(connection, "https") : connection;
    }

    @Override
    protected Props createClientActor(final ActorRef proxyActor, final Connection connection) {
        return HttpPushClientActor.props(connection, proxyActor, proxyActor, dittoHeaders, ConfigFactory.empty());
    }

    @Override
    protected ActorSystem getActorSystem() {
        return actorSystemResource.getActorSystem();
    }

    @Test
    public void connectAndDisconnect() {
        new TestKit(actorSystem) {{
            final Props props = createClientActor(getRef(), getConnection(false));
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
            final ActorRef underTest = watch(actorSystem.actorOf(createClientActor(getRef(), getConnection(false))));
            underTest.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Success("successfully connected + initialized mapper"));
            expectTerminated(underTest);
        }};
    }

    @Test
    public void testTCPConnectionFails() {
        new TestKit(actorSystem) {{
            binding.terminate(Duration.ofMillis(1L)).toCompletableFuture().join();
            final ActorRef underTest = watch(actorSystem.actorOf(createClientActor(getRef(), getConnection(false))));
            underTest.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            final Status.Failure failure = expectMsgClass(Status.Failure.class);
            assertThat(failure.cause()).isInstanceOf(ConnectionFailedException.class);
            expectTerminated(underTest);
        }};
    }

    @Test
    public void testTLSConnectionFails() {
        // GIVEN: server has a self-signed certificate
        connection = getHttpConnectionBuilderToLocalBinding(true, binding.localAddress().getPort()).build();
        final ClientCertificateCredentials credentials = ClientCertificateCredentials.newBuilder()
                .clientKey(TestConstants.Certificates.CLIENT_SELF_SIGNED_KEY)
                .clientCertificate(TestConstants.Certificates.CLIENT_SELF_SIGNED_CRT)
                .build();
        final SSLContext sslContext =
                SSLContextCreator.fromConnection(connection, DittoHeaders.empty(), connectionLogger)
                        .clientCertificate(credentials);
        final HttpsConnectionContext invalidHttpsContext = ConnectionContext.httpsServer(sslContext);

        final int port = binding.localAddress().getPort();
        binding.terminate(Duration.ofMillis(1L)).toCompletableFuture().join();
        binding = Http.get(actorSystem)
                .newServerAt("127.0.0.1", port)
                .enableHttps(invalidHttpsContext)
                .bindFlow(handler)
                .toCompletableFuture()
                .join();

        new TestKit(actorSystem) {{
            // WHEN: the connection is tested
            final ActorRef underTest = watch(actorSystem.actorOf(createClientActor(getRef(), getConnection(false))));
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
    public void testTLSConnection() {
        // GIVEN: server has a self-signed certificate
        final ClientCertificateCredentials credentials = ClientCertificateCredentials.newBuilder()
                .clientKey(TestConstants.Certificates.CLIENT_SELF_SIGNED_KEY)
                .clientCertificate(TestConstants.Certificates.CLIENT_SELF_SIGNED_CRT)
                .build();
        connection = getHttpConnectionBuilderToLocalBinding(true, binding.localAddress().getPort())
                .credentials(credentials)
                .trustedCertificates(TestConstants.Certificates.CLIENT_SELF_SIGNED_CRT)
                .build();
        final SSLContext sslContext =
                SSLContextCreator.fromConnection(connection, DittoHeaders.empty(), connectionLogger)
                        .clientCertificate(credentials);
        final HttpsConnectionContext httpsContext = ConnectionContext.httpsServer(sslContext);

        final int port = binding.localAddress().getPort();
        binding.terminate(Duration.ofMillis(1L)).toCompletableFuture().join();
        binding = Http.get(actorSystem)
                .newServerAt("127.0.0.1", port)
                .enableHttps(httpsContext)
                .bindFlow(handler)
                .toCompletableFuture()
                .join();

        new TestKit(actorSystem) {{
            // WHEN: the connection is tested
            final ActorRef underTest = watch(actorSystem.actorOf(createClientActor(getRef(), getConnection(false))));
            underTest.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());

            // THEN: the connection is connected successfully
            expectMsg(new Status.Success("successfully connected + initialized mapper"));
            expectTerminated(underTest);
        }};
    }

    @Test
    public void testInvalidTokenResponse() {
        new TestKit(actorSystem) {{
            // GIVEN: connection has oauth2 credentials
            final var originalConnection = getConnection(false);
            final var connection = originalConnection.toBuilder()
                    .connectionStatus(ConnectivityStatus.CLOSED)
                    .credentials(OAuthClientCredentials.newBuilder()
                            .tokenEndpoint(originalConnection.getUri())
                            .clientId("clientId")
                            .clientSecret("clientSecret")
                            .scope("scope")
                            .build())
                    .build();
            final ActorRef underTest = actorSystem.actorOf(createClientActor(getRef(), connection));

            // GIVEN: token endpoint delivers invalid token response
            responseQueue.add(HttpResponse.create().withStatus(404));

            // WHEN: a connection test is requested
            underTest.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());

            // THEN: test fails.
            final var failure = expectMsgClass(Status.Failure.class);
            assertThat(failure.cause()).isInstanceOf(ConnectionFailedException.class);
        }};
    }

    @Test
    public void testValidTokenResponse() {
        new TestKit(actorSystem) {{
            // GIVEN: connection has oauth2 credentials
            final var originalConnection = getConnection(false);
            final var connection = originalConnection.toBuilder()
                    .connectionStatus(ConnectivityStatus.CLOSED)
                    .credentials(OAuthClientCredentials.newBuilder()
                            .tokenEndpoint(originalConnection.getUri())
                            .clientId("clientId")
                            .clientSecret("clientSecret")
                            .scope("scope")
                            .build())
                    .build();
            final ActorRef underTest = actorSystem.actorOf(createClientActor(getRef(), connection));

            // GIVEN: token endpoint delivers valid token response
            final String accessToken = "ewogICJhbGciOiAiUlMyNTYiLAogICJ0eXAiOiAiSldUIgp9.ewogICJhdWQiOiBbXSwKICAiY2x" +
                    "pZW50X2lkIjogIm15LWNsaWVudC1pZCIsCiAgImV4cCI6IDMyNTAzNjgwMDAwLAogICJleHQiOiB7fSwKICAiaWF0IjogMC" +
                    "wKICAiaXNzIjogImh0dHBzOi8vbG9jYWxob3N0LyIsCiAgImp0aSI6ICI3ODVlODBjZC1lNmU2LTQ1MmEtYmU5Ny1hNTljN" +
                    "TNlZGI0ZDkiLAogICJuYmYiOiAwLAogICJzY3AiOiBbCiAgICAibXktc2NvcGUiCiAgXSwKICAic3ViIjogIm15LXN1Ympl" +
                    "Y3QiCn0.QUJD";
            responseQueue.add(HttpResponse.create()
                    .withStatus(200)
                    .withEntity(ContentTypes.APPLICATION_JSON, ByteString.fromString("{\n" +
                            "  \"access_token\": \"" + accessToken + "\",\n" +
                            "  \"expires_in\": 1048576,\n" +
                            "  \"scope\": \"my-scope\",\n" +
                            "  \"token_type\": \"bearer\"\n" +
                            "}"))
            );

            // WHEN: a connection test is requested
            underTest.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());

            // THEN: test succeeds
            expectMsgClass(Status.Success.class);
        }};
    }

    @Test
    public void publishTwinEvent() throws Exception {
        new TestKit(actorSystem) {{
            // GIVEN: local HTTP connection is connected
            final ActorRef underTest = actorSystem.actorOf(createClientActor(getRef(), getConnection(false)));
            underTest.tell(OpenConnection.of(connection.getId(), DittoHeaders.empty()), getRef());
            expectMsg(new Status.Success(BaseClientState.CONNECTED));
            final String customHeaderKey = "custom-header";
            final String customHeaderValue = "custom-value";
            // WHEN: a thing event is sent to a target with header mapping content-type=application/json
            final ThingModifiedEvent<?> thingModifiedEvent = TestConstants.thingModified(Collections.emptyList())
                    .setDittoHeaders(DittoHeaders.newBuilder()
                            .correlationId("internal-correlation-id")
                            .readGrantedSubjects(HTTP_TARGET.getAuthorizationContext().getAuthorizationSubjects())
                            .putHeader(customHeaderKey, customHeaderValue)
                            .build());
            underTest.tell(thingModifiedEvent, getRef());

            // THEN: a POST-request is forwarded to the path defined in the target
            final HttpRequest thingModifiedRequest = requestQueue.poll(10L, TimeUnit.SECONDS);
            assertThat(thingModifiedRequest).isNotNull();
            responseQueue.offer(HttpResponse.create().withStatus(StatusCodes.OK));
            assertThat(thingModifiedRequest.method()).isEqualTo(HttpMethods.POST);
            assertThat(thingModifiedRequest.getUri().getPathString()).isEqualTo("/target/address");

            // THEN: only headers in the header mapping are retained
            assertThat(thingModifiedRequest.entity().getContentType()).isEqualTo(ContentTypes.APPLICATION_JSON);
            assertThat(thingModifiedRequest.getHeader("correlation-id"))
                    .contains(HttpHeader.parse("correlation-id", "internal-correlation-id"));
            assertThat(thingModifiedRequest.getHeader(customHeaderKey)).isEmpty();

            // THEN: the payload is the JSON string of the event as a Ditto protocol message
            // Remove headers, since the header order can be different.
            assertThat(ProtocolFactory.jsonifiableAdaptableFromJson(JsonObject.of(thingModifiedRequest
                    .entity()
                    .toStrict(10_000, SystemMaterializer.get(actorSystem).materializer())
                    .toCompletableFuture()
                    .join()
                    .getData()
                    .utf8String())).setDittoHeaders(DittoHeaders.empty()).toJsonString()).isEqualTo(
                    ProtocolFactory.wrapAsJsonifiableAdaptable(ADAPTER.toAdaptable(thingModifiedEvent))
                            .setDittoHeaders(DittoHeaders.empty()).toJsonString());
        }};
    }

    @Test
    public void placeholderReplacement() throws Exception {
        final Target target = TestConstants.Targets.TARGET_WITH_PLACEHOLDER
                .withAddress("PATCH:" + TestConstants.Targets.TARGET_WITH_PLACEHOLDER.getAddress());
        connection = connection.toBuilder().setTargets(singletonList(target)).build();

        new TestKit(actorSystem) {{
            // GIVEN: local HTTP connection is connected
            final ActorRef underTest = actorSystem.actorOf(createClientActor(getRef(), connection));
            underTest.tell(OpenConnection.of(connection.getId(), DittoHeaders.empty()), getRef());
            expectMsg(new Status.Success(BaseClientState.CONNECTED));

            // WHEN: a thing event is sent to a target with header mapping content-type=application/json
            final ThingModifiedEvent<?> thingModifiedEvent =
                    TestConstants.thingModified(target.getAuthorizationContext().getAuthorizationSubjects());
            underTest.tell(thingModifiedEvent, getRef());

            // THEN: a POST-request is forwarded to the path defined in the target
            final HttpRequest thingModifiedRequest = requestQueue.poll(10, TimeUnit.SECONDS);
            responseQueue.offer(HttpResponse.create().withStatus(StatusCodes.OK));
            assertThat(thingModifiedRequest.getUri().getPathString()).isEqualTo("/target:ditto/thing@twin");
        }};
    }

}
