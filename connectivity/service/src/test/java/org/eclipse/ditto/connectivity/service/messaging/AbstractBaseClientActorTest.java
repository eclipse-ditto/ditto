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
package org.eclipse.ditto.connectivity.service.messaging;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Collections;

import javax.net.ssl.SSLContext;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionBuilder;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.EnableConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ResetConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ResetConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogsResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionStatus;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.SSLContextCreator;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.Uri;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Abstract unit test for classes that extend {@link BaseClientActor}.
 */
public abstract class AbstractBaseClientActorTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    protected final DittoHeaders dittoHeaders = DittoHeaders.empty();
    private ServerBinding binding;

    @After
    public void closeServerBinding() {
        if (binding != null) {
            binding.terminate(Duration.ofSeconds(10L));
        }
    }

    public static final Target HTTP_TARGET = ConnectivityModelFactory.newTargetBuilder()
            .address("POST:/target/address")
            .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
            .headerMapping(ConnectivityModelFactory.newHeaderMapping(
                    Collections.singletonMap("content-type", "application/json")
            ))
            .topics(Topic.TWIN_EVENTS)
            .build();

    protected static ConnectionBuilder getHttpConnectionBuilderToLocalBinding(final boolean isSecure, final int port) {
        return ConnectivityModelFactory.newConnectionBuilder(TestConstants.createRandomConnectionId(),
                        ConnectionType.HTTP_PUSH,
                        ConnectivityStatus.CLOSED,
                        (isSecure ? "https" : "http") + "://127.0.0.1:" + port)
                .targets(singletonList(HTTP_TARGET))
                .validateCertificate(isSecure);
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
            getActorSystem().stop(clientActor);
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
            getActorSystem().stop(clientActor);
        }};
    }

    @Test
    public void testTLSConnectionWithoutCertificateCheck() {
        // GIVEN: server has a self-signed certificate (bind port number is random; connection port number is ignored)
        final Connection serverConnection = getHttpConnectionBuilderToLocalBinding(true, 443).build();
        final ClientCertificateCredentials credentials = ClientCertificateCredentials.newBuilder()
                .clientKey(TestConstants.Certificates.CLIENT_SELF_SIGNED_KEY)
                .clientCertificate(TestConstants.Certificates.CLIENT_SELF_SIGNED_CRT)
                .build();
        final ConnectionLogger connectionLogger = mock(ConnectionLogger.class);
        final SSLContext sslContext =
                SSLContextCreator.fromConnection(serverConnection, DittoHeaders.empty(), connectionLogger)
                        .clientCertificate(credentials);

        final ActorSystem actorSystem = getActorSystem();
        binding = Http.get(actorSystem)
                .newServerAt("127.0.0.1", 0)
                .enableHttps(HttpsConnectionContext.httpsServer(sslContext))
                .bindFlow(Flow.fromSinkAndSource(Sink.ignore(), Source.empty()))
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
                    ClientActorPropsFactory.get(actorSystem,
                                    ScopedConfig.dittoExtension(actorSystem.settings().config()))
                            .getActorPropsForType(insecureConnection, getRef(), getRef(), actorSystem,
                                    DittoHeaders.empty(), ConfigFactory.empty())
            ));
            underTest.tell(TestConnection.of(insecureConnection, DittoHeaders.empty()), getRef());

            // THEN: the test should succeed, or it should fail with a different reason than SSL validation
            final Object response = expectMsgClass(Duration.ofSeconds(30), Object.class);
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
            expectTerminated(Duration.ofSeconds(30L), underTest);
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
            getActorSystem().stop(clientActor);
        }};
    }

    @Test
    public void resetConnectionLogs() {
        new TestKit(getActorSystem()) {{
            final ActorRef clientActor = childActorOf(createClientActor(getRef(), getConnection(false)));
            clientActor.tell(ResetConnectionLogs.of(getConnectionId(), DittoHeaders.empty()), getRef());

            expectNoMessage();
            getActorSystem().stop(clientActor);
        }};
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

    protected abstract Props createClientActor(final ActorRef proxyActor, final Connection connection);

    protected abstract ActorSystem getActorSystem();
}
