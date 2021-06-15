/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import akka.actor.ActorContext;
import akka.actor.ActorInitializationException;
import akka.actor.ActorPath;
import akka.actor.ChildRestartStats;
import akka.actor.InternalActorRef;
import scala.collection.Iterable;
import scala.collection.immutable.List;

/**
 * Unit test for {@link OneForOneEscalateStrategy}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class OneForOneEscalateStrategyTest {

    private final Iterable<ChildRestartStats> children = List.<ChildRestartStats>newBuilder().result();
    @Mock
    private ActorContext actorContext;
    @Mock
    private InternalActorRef child;
    @Mock
    private ChildRestartStats stats;
    @Mock
    private ActorPath path;

    @Before
    public void setUp() {
        when(child.path()).thenReturn(path);
        when(path.toString()).thenReturn("the-mocked-path");
    }

    @Test
    public void restartsWhenChildHasFailure() {
        final Throwable cause =
                new ActorInitializationException(child, "failed for the test", new IllegalArgumentException());
        final int retriesUntilEscalate = 3;

        final OneForOneEscalateStrategy strategy = OneForOneEscalateStrategy.withRetries(retriesUntilEscalate);
        for (int i = 0; i < retriesUntilEscalate; i++) {
            assertThat(strategy.handleFailure(actorContext, child, cause, stats, children))
                    .isTrue();
        }
        Mockito.verify(child, Mockito.times(retriesUntilEscalate)).restart(cause);
    }

    @Test
    public void escalatesWhenChildHasTooManyFailures() {
        final Throwable cause =
                new ActorInitializationException(child, "failed for the test", new IllegalArgumentException());
        final int retriesUntilEscalate = 3;
        final OneForOneEscalateStrategy strategy = OneForOneEscalateStrategy.withRetries(retriesUntilEscalate);
        for (int i = 0; i < retriesUntilEscalate; i++) {
            assertThat(strategy.handleFailure(actorContext, child, cause, stats, children))
                    .isTrue();
        }

        // the (retriesUntilEscalate + 1)-th call to #handleFailure should return 'false' (which means escalate)
        assertThat(strategy.handleFailure(actorContext, child, cause, stats, children))
                .isFalse();
        // should only have called #restart retriesUntilEscalate times
        Mockito.verify(child, Mockito.times(retriesUntilEscalate)).restart(cause);
    }

    @Test
    public void escalateStrategyEscalatesOnTheFirstFailure() {
        final Throwable cause =
                new ActorInitializationException(child, "failed for the test", new IllegalArgumentException());
        final OneForOneEscalateStrategy strategy = OneForOneEscalateStrategy.escalateStrategy();

        assertThat(strategy.handleFailure(actorContext, child, cause, stats, children))
                .isFalse();

        Mockito.verify(child, never()).restart(cause);
    }

    @Test
    public void instantiateWithNegativeRetriesFails() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> OneForOneEscalateStrategy.withRetries(-1));
    }

}
