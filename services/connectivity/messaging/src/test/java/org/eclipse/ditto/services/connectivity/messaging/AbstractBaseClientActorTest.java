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

package org.eclipse.ditto.services.connectivity.messaging;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ClientCertificateCredentials;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.SSLContextCreator;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.commands.connectivity.modify.EnableConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.Uri;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Abstract unit test for classes that extend {@link BaseClientActor}.
 */
public abstract class AbstractBaseClientActorTest {

    public static final Target HTTP_TARGET = ConnectivityModelFactory.newTargetBuilder()
            .address("POST:/target/address")
            .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
            .headerMapping(ConnectivityModelFactory.newHeaderMapping(
                    Collections.singletonMap("content-type", "application/json")
            ))
            .topics(Topic.TWIN_EVENTS, Topic.values())
            .build();

    protected static Connection getHttpConnectionToLocalBinding(final boolean isSecure, final int port) {
        return ConnectivityModelFactory.newConnectionBuilder(TestConstants.createRandomConnectionId(),
                ConnectionType.HTTP_PUSH,
                ConnectivityStatus.CLOSED,
                (isSecure ? "https" : "http") + "://127.0.0.1:" + port)
                .targets(singletonList(HTTP_TARGET))
                .validateCertificate(isSecure)
                .build();
    }

    private static Throwable getEventualCause(final Throwable error) {
        if (error instanceof DittoRuntimeException || error.getCause() == null) {
            return error;
        } else {
            return getEventualCause(error.getCause());
        }
    }

    @Test
    public void retrieveConnectionStatus() {
        new TestKit(getActorSystem()) {{
            final ActorRef clientActor = childActorOf(createClientActor(getRef(), getConnection(false)));
            clientActor.tell(RetrieveConnectionStatus.of(getConnectionId(), DittoHeaders.empty()), getRef());

            expectMsgClass(ResourceStatus.class);
        }};
    }

    @Test
    public void retrieveConnectionMetrics() {
        new TestKit(getActorSystem()) {{
            final ActorRef clientActor = childActorOf(createClientActor(getRef(), getConnection(false)));
            clientActor.tell(RetrieveConnectionMetrics.of(getConnectionId(), DittoHeaders.empty()), getRef());

            expectMsgClass(RetrieveConnectionMetricsResponse.class);
        }};
    }

    @Test
    public void resetConnectionMetrics() {
        new TestKit(getActorSystem()) {{
            final ActorRef clientActor = childActorOf(createClientActor(getRef(), getConnection(false)));
            clientActor.tell(ResetConnectionMetrics.of(getConnectionId(), DittoHeaders.empty()), getRef());

            expectNoMessage();
        }};
    }

    @Test
    public void retrieveConnectionLogs() {
        new TestKit(getActorSystem()) {{
            final ActorRef clientActor = childActorOf(createClientActor(getRef(), getConnection(false)));
            clientActor.tell(RetrieveConnectionLogs.of(getConnectionId(), DittoHeaders.empty()), getRef());

            expectMsgClass(RetrieveConnectionLogsResponse.class);
        }};
    }

    @Test
    public void enableConnectionLogs() {
        new TestKit(getActorSystem()) {{
            final ActorRef clientActor = childActorOf(createClientActor(getRef(), getConnection(false)));
            clientActor.tell(EnableConnectionLogs.of(getConnectionId(), DittoHeaders.empty()), getRef());

            expectNoMessage();
        }};
    }

    @Test
    public void resetConnectionLogs() {
        new TestKit(getActorSystem()) {{
            final ActorRef clientActor = childActorOf(createClientActor(getRef(), getConnection(false)));
            clientActor.tell(ResetConnectionLogs.of(getConnectionId(), DittoHeaders.empty()), getRef());

            expectNoMessage();
        }};
    }

    @Test
    public void testConnectionIdAppending() {

        final Connection connection = getConnection(true);
        testAckAppending(null, connection);
    }

    @Test
    public void testConnectionIdAppendingWithPayload() {

        final Connection connection = getConnection(true);
        final JsonValue testPayload = JsonValue.of("this is a test payload");
        testAckAppending(testPayload, connection);
    }

    @Test
    public void testConnectionIdAppendingWithAcknowledgements() {

        final Connection connection = getConnection(true);
        testAcksAppending(null, connection);
    }

    @Test
    public void testTLSConnectionWithoutCertificateCheck() {
        // GIVEN: server has a self-signed certificate (bind port number is random; connection port number is ignored)
        final Connection serverConnection = getHttpConnectionToLocalBinding(true, 443);
        final ClientCertificateCredentials credentials = ClientCertificateCredentials.newBuilder()
                .clientKey(TestConstants.Certificates.CLIENT_SELF_SIGNED_KEY)
                .clientCertificate(TestConstants.Certificates.CLIENT_SELF_SIGNED_CRT)
                .build();
        final SSLContext sslContext = SSLContextCreator.fromConnection(serverConnection, DittoHeaders.empty())
                .clientCertificate(credentials);
        final HttpsConnectionContext invalidHttpsContext = ConnectionContext.https(sslContext);

        final ActorSystem actorSystem = getActorSystem();
        final ServerBinding binding = Http.get(actorSystem)
                .bindAndHandle(Flow.fromSinkAndSource(Sink.ignore(), Source.empty()),
                        ConnectHttp.toHostHttps("127.0.0.1", 0).withCustomHttpsContext(invalidHttpsContext),
                        ActorMaterializer.create(actorSystem))
                .toCompletableFuture()
                .join();

        new TestKit(actorSystem) {{
            // WHEN: the connection is tested against a client actor that really tries to connect to the local port
            final Connection secureConnection = getConnection(true);
            final Connection insecureConnection = secureConnection.toBuilder()
                    .uri(Uri.create(secureConnection.getUri()).port(binding.localAddress().getPort()).toString())
                    .validateCertificate(false)
                    .failoverEnabled(false)
                    .build();
            final ActorRef underTest = watch(actorSystem.actorOf(
                    DefaultClientActorPropsFactory.getInstance()
                            .getActorPropsForType(insecureConnection, getRef(), getRef())
            ));
            underTest.tell(TestConnection.of(insecureConnection, DittoHeaders.empty()), getRef());

            // THEN: the test should succeed, or it should fail with a different reason than SSL validation
            final Object response = expectMsgClass(Object.class);
            if (response instanceof Status.Failure) {
                final DittoRuntimeException error =
                        (DittoRuntimeException) getEventualCause(((Status.Failure) response).cause());
                assertThat(error.getMessage())
                        .describedAs("error message")
                        .doesNotContain("unable to find valid certification path");
                assertThat(error.getDescription().orElse(""))
                        .describedAs("error description")
                        .doesNotContain("unable to find valid certification path");
            } else {
                assertThat(response).isInstanceOf(Status.Success.class);
            }
            expectTerminated(underTest);
        }};
    }

    protected void testAckAppending(@Nullable final JsonValue ackPayload, final Connection con) {
        final ActorSystem actorSystem = getActorSystem();
        new TestKit(actorSystem) {
            {
                final TestProbe connectionActor = TestProbe.apply(actorSystem);
                final ActorRef underTest = watch(actorSystem.actorOf(
                        DefaultClientActorPropsFactory.getInstance()
                                .getActorPropsForType(con, getRef(), connectionActor.ref())
                ));
                underTest.tell(
                        Acknowledgement.of(AcknowledgementLabel.of("test-ack"), ThingId.dummy(), HttpStatusCode.OK,
                                DittoHeaders.empty(), ackPayload), getRef());

                final Acknowledgement ack = connectionActor.expectMsgClass(Acknowledgement.class);
                assertThat(ack.getDittoHeaders().get(DittoHeaderDefinition.CONNECTION_ID.getKey()))
                        .isEqualTo(con.getId().toString());
            }
        };
    }

    protected void testAcksAppending(@Nullable final JsonValue ackPayload, final Connection con) {
        final ActorSystem actorSystem = getActorSystem();
        new TestKit(actorSystem) {
            {
                final TestProbe connectionActor = TestProbe.apply(actorSystem);
                final ActorRef underTest = watch(actorSystem.actorOf(
                        DefaultClientActorPropsFactory.getInstance()
                                .getActorPropsForType(con, getRef(), connectionActor.ref())
                ));
                List<Acknowledgement> ackList = Arrays.asList(
                        Acknowledgement.of(AcknowledgementLabel.of("test-ack"), ThingId.dummy(), HttpStatusCode.OK,
                                DittoHeaders.empty(), ackPayload),
                        Acknowledgement.of(AcknowledgementLabel.of("test-ack-2"), ThingId.dummy(), HttpStatusCode.OK,
                                DittoHeaders.empty(), ackPayload));

                underTest.tell(Acknowledgements.of(ackList, DittoHeaders.empty()), getRef());

                final Acknowledgements acks = connectionActor.expectMsgClass(Acknowledgements.class);
                for (Acknowledgement ack : acks) {
                    assertThat(ack.getDittoHeaders().get(DittoHeaderDefinition.CONNECTION_ID.getKey()))
                            .isEqualTo(con.getId().toString());
                }
            }
        };
    }

    protected ConnectionId getConnectionId() {
        return getConnection(false).getId();
    }

    protected Connection setScheme(final Connection connection, final String scheme) {
        return connection.toBuilder()
                .uri(Uri.create(connection.getUri()).scheme(scheme).toString())
                .build();
    }

    protected abstract Connection getConnection(final boolean isSecure);

    protected abstract Props createClientActor(final ActorRef conciergeForwarder, final Connection connection);

    protected abstract ActorSystem getActorSystem();
}
