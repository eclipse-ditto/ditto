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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.junit.Before;
import org.junit.Test;

import akka.actor.Status;

/**
 * Unit tests for {@link ConnectivityStatusResolver}.
 */
public final class ConnectivityStatusResolverTest {

    private ConnectivityStatusResolver underTest;
    private UserIndicatedErrors userIndicatedErrors;

    @Before
    public void setup() {
        userIndicatedErrors = mock(UserIndicatedErrors.class);
        underTest = ConnectivityStatusResolver.of(userIndicatedErrors);
    }

    @Test
    public void resolvesToExplicitlySpecifiedConnectivityStatus() {
        final ConnectionFailure connectionFailure = mock(ConnectionFailure.class);
        when(connectionFailure.getStatus()).thenReturn(Optional.of(ConnectivityStatus.FAILED));
        assertThat((Object) underTest.resolve(connectionFailure)).isEqualTo(ConnectivityStatus.FAILED);
    }

    @Test
    public void resolvesToMisconfiguredIfExceptionIsDefinedInList() {
        final ConnectionFailure connectionFailure = mock(ConnectionFailure.class);
        when(connectionFailure.getStatus()).thenReturn(Optional.empty());
        final Status.Failure failure = mock(Status.Failure.class);
        final IllegalStateException expectedException = new IllegalStateException();
        when(failure.cause()).thenReturn(expectedException);
        when(connectionFailure.getFailure()).thenReturn(failure);
        when(userIndicatedErrors.matches(expectedException)).thenReturn(true);

        assertThat((Object) underTest.resolve(connectionFailure)).isEqualTo(ConnectivityStatus.MISCONFIGURED);
    }

    @Test
    public void resolvesToFailedIfExceptionIsNotDefinedInList() {
        final ConnectionFailure connectionFailure = mock(ConnectionFailure.class);
        when(connectionFailure.getStatus()).thenReturn(Optional.empty());
        final Status.Failure failure = mock(Status.Failure.class);
        final IllegalStateException expectedException = new IllegalStateException();
        when(failure.cause()).thenReturn(expectedException);
        when(connectionFailure.getFailure()).thenReturn(failure);
        when(userIndicatedErrors.matches(expectedException)).thenReturn(false);

        assertThat((Object) underTest.resolve(connectionFailure)).isEqualTo(ConnectivityStatus.FAILED);
    }

}
