/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.model.amqpbridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ConnectionTypeTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ConnectionType.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ConnectionType.class, areImmutable());
    }

    @Test
    public void testFromConnectionId() {
        final Optional<ConnectionType> actual = ConnectionType.fromConnectionId(ConnectionType.AMQP_10 + ":123");
        assertThat(actual).containsSame(ConnectionType.AMQP_10);
    }

    @Test
    public void testFromInvalidConnectionId() {
        final Optional<ConnectionType> actual = ConnectionType.fromConnectionId("amqp-010:123");
        assertThat(actual).isEmpty();
    }
}