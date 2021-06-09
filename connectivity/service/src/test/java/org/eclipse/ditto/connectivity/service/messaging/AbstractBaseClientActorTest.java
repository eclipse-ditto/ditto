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

import java.util.Collections;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
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
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogs;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionLogsResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionStatus;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.model.Uri;
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
