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
package org.eclipse.ditto.base.model.signals.commands.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link RequestFromStreamingSubscription}.
 */
public final class RequestFromStreamingSubscriptionTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(RequestFromStreamingSubscription.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RequestFromStreamingSubscription.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void serialization() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final RequestFromStreamingSubscription
                underTest = RequestFromStreamingSubscription.of(NamespacedEntityId.of(EntityType.of("thing"), "foo:bar"),
                JsonPointer.of("/"), UUID.randomUUID().toString(), 9L, dittoHeaders);
        final RequestFromStreamingSubscription deserialized = RequestFromStreamingSubscription.fromJson(underTest.toJson(), dittoHeaders);
        assertThat(deserialized).isEqualTo(underTest);
    }

}
