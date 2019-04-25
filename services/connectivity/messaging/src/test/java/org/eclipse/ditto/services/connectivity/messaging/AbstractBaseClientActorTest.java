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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.signals.commands.connectivity.modify.EnableConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.modify.ResetConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogs;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionLogsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

/**
 * Abstract unit test for classes that extend {@link BaseClientActor}.
 */
public abstract class AbstractBaseClientActorTest {

    @Test
    public void retrieveConnectionStatus() {
        new TestKit(getActorSystem()) {{
            final ActorRef clientActor = childActorOf(createClientActor(getRef()));
            clientActor.tell(RetrieveConnectionStatus.of(getConnectionId(), DittoHeaders.empty()), getRef());

            expectMsgClass(ResourceStatus.class);
        }};
    }

    @Test
    public void retrieveConnectionMetrics() {
        new TestKit(getActorSystem()) {{
            final ActorRef clientActor = childActorOf(createClientActor(getRef()));
            clientActor.tell(RetrieveConnectionMetrics.of(getConnectionId(), DittoHeaders.empty()), getRef());

            expectMsgClass(RetrieveConnectionMetricsResponse.class);
        }};
    }

    @Test
    public void resetConnectionMetrics() {
        new TestKit(getActorSystem()) {{
            final ActorRef clientActor = childActorOf(createClientActor(getRef()));
            clientActor.tell(ResetConnectionMetrics.of(getConnectionId(), DittoHeaders.empty()), getRef());

            expectNoMessage();
        }};
    }

    @Test
    public void retrieveConnectionLogs() {
        new TestKit(getActorSystem()) {{
            final ActorRef clientActor = childActorOf(createClientActor(getRef()));
            clientActor.tell(RetrieveConnectionLogs.of(getConnectionId(), DittoHeaders.empty()), getRef());

            expectMsgClass(RetrieveConnectionLogsResponse.class);
        }};
    }

    @Test
    public void enableConnectionLogs() {
        new TestKit(getActorSystem()) {{
            final ActorRef clientActor = childActorOf(createClientActor(getRef()));
            clientActor.tell(EnableConnectionLogs.of(getConnectionId(), DittoHeaders.empty()), getRef());

            expectNoMessage();
        }};
    }

    @Test
    public void resetConnectionLogs() {
        new TestKit(getActorSystem()) {{
            final ActorRef clientActor = childActorOf(createClientActor(getRef()));
            clientActor.tell(ResetConnectionLogs.of(getConnectionId(), DittoHeaders.empty()), getRef());

            expectNoMessage();
        }};
    }

    protected String getConnectionId() {
        return getConnection().getId();
    }
    protected abstract Connection getConnection();
    protected abstract Props createClientActor(final ActorRef conciergeForwarder);
    protected abstract ActorSystem getActorSystem();
}
