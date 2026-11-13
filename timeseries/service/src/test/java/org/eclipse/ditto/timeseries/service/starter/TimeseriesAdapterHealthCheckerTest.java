/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.service.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.health.RetrieveHealth;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public final class TimeseriesAdapterHealthCheckerTest {

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("TimeseriesAdapterHealthCheckerTest");
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void reportsTheCheckedHealthRatherThanTheLastKnownState() {
        final TimeseriesAdapter adapter = mock(TimeseriesAdapter.class);
        when(adapter.getHealth()).thenReturn(HealthStatus.UP);
        when(adapter.checkHealth()).thenReturn(CompletableFuture.completedFuture(HealthStatus.DOWN));

        new TestKit(actorSystem) {{
            final ActorRef underTest = actorSystem.actorOf(TimeseriesAdapterHealthChecker.props(adapter));

            underTest.tell(RetrieveHealth.newInstance(), getRef());

            assertThat(expectMsgClass(StatusInfo.class).getStatus()).isEqualTo(StatusInfo.Status.DOWN);
        }};
    }

    @Test
    public void reportsDownWhenTheCheckFails() {
        final TimeseriesAdapter adapter = mock(TimeseriesAdapter.class);
        when(adapter.checkHealth()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broken")));

        new TestKit(actorSystem) {{
            final ActorRef underTest = actorSystem.actorOf(TimeseriesAdapterHealthChecker.props(adapter));

            underTest.tell(RetrieveHealth.newInstance(), getRef());

            assertThat(expectMsgClass(StatusInfo.class).getStatus()).isEqualTo(StatusInfo.Status.DOWN);
        }};
    }

    @Test
    public void reportsDownWhenTheCheckDoesNotAnswer() {
        final TimeseriesAdapter adapter = mock(TimeseriesAdapter.class);
        when(adapter.checkHealth()).thenReturn(new CompletableFuture<>());

        new TestKit(actorSystem) {{
            final ActorRef underTest = actorSystem.actorOf(TimeseriesAdapterHealthChecker.props(adapter));

            underTest.tell(RetrieveHealth.newInstance(), getRef());

            final StatusInfo status = expectMsgClass(
                    TimeseriesAdapterHealthChecker.CHECK_TIMEOUT.plusSeconds(5), StatusInfo.class);
            assertThat(status.getStatus()).isEqualTo(StatusInfo.Status.DOWN);
        }};
    }

    @Test
    public void reportsDownWhenTheCheckThrows() {
        final TimeseriesAdapter adapter = mock(TimeseriesAdapter.class);
        when(adapter.checkHealth()).thenThrow(new IllegalStateException("broken"));

        new TestKit(actorSystem) {{
            final ActorRef underTest = actorSystem.actorOf(TimeseriesAdapterHealthChecker.props(adapter));

            underTest.tell(RetrieveHealth.newInstance(), getRef());

            assertThat(expectMsgClass(StatusInfo.class).getStatus()).isEqualTo(StatusInfo.Status.DOWN);
        }};
    }

}
