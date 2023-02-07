/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.events.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link StreamingSubscriptionCreated}.
 */
public final class StreamingSubscriptionCreatedTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(StreamingSubscriptionCreated.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(StreamingSubscriptionCreated.class).withRedefinedSuperclass().verify();
    }

    @Test
    public void serialization() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final StreamingSubscriptionCreated underTest = StreamingSubscriptionCreated.of(UUID.randomUUID().toString(),
                NamespacedEntityId.of(EntityType.of("thing"), "foo:bar"), dittoHeaders);
        final StreamingSubscriptionCreated deserialized = StreamingSubscriptionCreated.fromJson(underTest.toJson(), dittoHeaders);
        assertThat(deserialized).isEqualTo(underTest);
    }
}
