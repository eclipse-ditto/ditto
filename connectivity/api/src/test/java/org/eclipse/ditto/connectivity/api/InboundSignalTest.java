/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;

import java.time.Instant;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.junit.ClassRule;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.serialization.Serialization;
import akka.serialization.SerializationExtension;
import akka.serialization.Serializer;
import akka.serialization.Serializers;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link InboundSignal}.
 */
public final class InboundSignalTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(InboundSignal.class, MutabilityMatchers.areImmutable(),
                provided(Signal.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(InboundSignal.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void serialization() {
        ActorSystem actorSystem = null;
        try {
            actorSystem = ActorSystem.create(getClass().getSimpleName(), ConfigFactory.load("test"));
            final ThingDeleted thingDeleted = ThingDeleted.of(ThingId.of("thing:id"), 9L, Instant.now(),
                    DittoHeaders.newBuilder().randomCorrelationId().build(), null);
            final InboundSignal underTest = InboundSignal.of(thingDeleted);

            final Serialization serialization = SerializationExtension.get(actorSystem);
            final Serializer serializer = serialization.findSerializerFor(underTest);
            final String manifest = Serializers.manifestFor(serializer, underTest);
            assertThat(manifest).isEqualTo(underTest.getClass().getSimpleName());

            final byte[] bytes = serialization.serialize(underTest).get();
            final Object deserialized = serialization.deserialize(bytes, serializer.identifier(), manifest).get();
            assertThat(deserialized).isEqualTo(underTest);
        } finally {
            if (actorSystem != null) {
                actorSystem.terminate();
            }
        }
    }

}
