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
package org.eclipse.ditto.connectivity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ConnectionIdTest {

    @Test
    public void testImmutability() {
        assertInstancesOf(ConnectionId.class, areImmutable(), provided(EntityId.class).isAlsoImmutable());
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(ConnectionId.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void instantiationFromEntityTypeCreatesConnectionId() {
        final EntityId entityId = EntityId.of(ConnectivityConstants.ENTITY_TYPE, "connection");
        assertThat((CharSequence) entityId).isInstanceOf(ConnectionId.class);
    }

    @Test
    public void randomlyGeneratedIsNotEmpty() {
        assertThat(ConnectionId.generateRandom().toString()).isNotEmpty();
    }

    @Test
    public void connectionIdOfConnectionIdIsSameInstance() {
        final ConnectionId connectionIdOne = ConnectionId.generateRandom();
        final ConnectionId connectionIdTwo = ConnectionId.of(connectionIdOne);
        assertThat((CharSequence) connectionIdOne).isSameAs(connectionIdTwo);
    }

    @Test
    public void toStringEqualsInput() {
        final ConnectionId connectionId = ConnectionId.of("myConnection");
        assertThat(connectionId.toString()).hasToString("myConnection");
    }

}
