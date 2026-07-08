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
package org.eclipse.ditto.internal.utils.pubsub.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.serialization.Serialization;
import org.apache.pekko.serialization.SerializationExtension;
import org.apache.pekko.serialization.Serializer;
import org.apache.pekko.serialization.SerializerWithStringManifest;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.internal.utils.pubsub.TestMappingStrategies;
import org.eclipse.ditto.internal.utils.pubsub.api.PreSerializedPublishSignal;
import org.eclipse.ditto.internal.utils.pubsub.api.PublishSignal;
import org.eclipse.ditto.internal.utils.pubsub.api.SignalBytesHolder;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Verifies that {@link PreSerializedPublishSignalSerializer} is correctly wired into Pekko's
 * {@link Serialization} extension (as configured in {@code ditto-pekko-config.conf}): the serializer resolves for
 * {@link PreSerializedPublishSignal}, and a full serialize/deserialize <em>through the extension</em> (the exact path
 * Artery uses across the wire: {@code findSerializerFor} → {@code toBinary} → {@code deserialize(bytes, id, manifest)})
 * reconstructs a plain {@link PublishSignal}. This also proves the serializer class instantiates eagerly during
 * {@code SerializationExtension} initialization (i.e. no ClassNotFound on startup).
 */
public final class PreSerializedPublishSignalSerializationExtensionTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final String CONFIG = """
            pekko.actor {
              provider = "local"
              serializers {
                cbor = "org.eclipse.ditto.internal.utils.cluster.CborJsonifiableSerializer"
                pubsub-preserialized = "org.eclipse.ditto.internal.utils.pubsub.serialization.PreSerializedPublishSignalSerializer"
              }
              serialization-bindings {
                "org.eclipse.ditto.base.model.json.Jsonifiable" = cbor
                "org.eclipse.ditto.internal.utils.pubsub.api.PreSerializedPublishSignal" = pubsub-preserialized
              }
            }
            ditto.mapping-strategy.implementation = "org.eclipse.ditto.internal.utils.pubsub.TestMappingStrategies"
            """;

    private static ExtendedActorSystem system;

    @BeforeClass
    public static void setUpClass() {
        // referencing TestMappingStrategies here keeps the (reflectively-loaded) class from being flagged as unused.
        assertThat(TestMappingStrategies.class).isNotNull();
        system = (ExtendedActorSystem) ExtendedActorSystem.create("test", ConfigFactory.parseString(CONFIG)
                .withFallback(ConfigFactory.load()));
    }

    @AfterClass
    public static void tearDownClass() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void serializerIsResolvedForEnvelope() {
        final Serialization serialization = SerializationExtension.get(system);
        final PreSerializedPublishSignal envelope = sampleEnvelope();

        final Serializer serializer = serialization.findSerializerFor(envelope);

        assertThat(serializer).isInstanceOf(PreSerializedPublishSignalSerializer.class);
        assertThat(serializer.identifier()).isEqualTo(PreSerializedPublishSignalSerializer.UNIQUE_IDENTIFIER);
    }

    @Test
    public void roundTripThroughSerializationExtensionYieldsPublishSignal() {
        final Serialization serialization = SerializationExtension.get(system);
        final PreSerializedPublishSignal envelope = sampleEnvelope();

        // Mirror the Artery wire path exactly.
        final Serializer serializer = serialization.findSerializerFor(envelope);
        final byte[] bytes = serializer.toBinary(envelope);
        final String manifest = ((SerializerWithStringManifest) serializer).manifest(envelope);
        final Object deserialized = serialization.deserialize(bytes, serializer.identifier(), manifest).get();

        assertThat(deserialized).isInstanceOf(PublishSignal.class);
        final PublishSignal publishSignal = (PublishSignal) deserialized;
        assertThat(publishSignal.getSignal()).isEqualTo(envelope.getSignal());
        assertThat(publishSignal.getGroups()).isEqualTo(envelope.getGroups());
        assertThat(publishSignal.getGroupIndexKey().toString()).isEqualTo(envelope.getGroupIndexKey());
    }

    private static PreSerializedPublishSignal sampleEnvelope() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId("correlation-id")
                .readGrantedSubjects(List.of(
                        AuthorizationSubject.newInstance("ditto:subject-one"),
                        AuthorizationSubject.newInstance("ditto:subject-two")))
                .build();
        final Acknowledgement acknowledgement = Acknowledgement.of(AcknowledgementLabel.of("test-ack"),
                EntityId.of(EntityType.of("thing"), "pub.sub.test:thing-id"),
                HttpStatus.OK,
                dittoHeaders);
        return PreSerializedPublishSignal.of(new SignalBytesHolder(acknowledgement),
                Map.of("group-a", 3, "group-b", 1), "index-key");
    }
}
