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

import java.io.NotSerializableException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.internal.utils.cluster.CborJsonifiableSerializer;
import org.eclipse.ditto.internal.utils.pubsub.TestMappingStrategies;
import org.eclipse.ditto.internal.utils.pubsub.api.PreSerializedPublishSignal;
import org.eclipse.ditto.internal.utils.pubsub.api.PublishSignal;
import org.eclipse.ditto.internal.utils.pubsub.api.SignalBytesHolder;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Round-trip tests for {@link PreSerializedPublishSignalSerializer}: a {@link PreSerializedPublishSignal} envelope
 * must serialize and deserialize back into an equivalent {@link PublishSignal} (same signal, groups, groupIndexKey),
 * and the shared {@link SignalBytesHolder} must serialize the signal payload only once.
 */
public final class PreSerializedPublishSignalSerializerTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static ExtendedActorSystem system;

    private PreSerializedPublishSignalSerializer underTest;

    @BeforeClass
    public static void setUpClass() {
        system = (ExtendedActorSystem) ExtendedActorSystem.create("test", ConfigFactory.parseMap(
                Map.of("ditto.mapping-strategy.implementation", TestMappingStrategies.class.getName())));
    }

    @AfterClass
    public static void tearDownClass() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Before
    public void setUp() {
        underTest = new PreSerializedPublishSignalSerializer(system);
    }

    @Test
    public void byteArrayRoundTripReconstructsPublishSignal() {
        final Map<String, Integer> groups = Map.of("group-a", 3, "group-b", 1);
        final PreSerializedPublishSignal envelope = sampleEnvelope(groups, "index-key");

        final byte[] bytes = underTest.toBinary(envelope);
        final Object result = underTest.fromBinary(bytes, underTest.manifest(envelope));

        assertThat(result).isInstanceOf(PublishSignal.class);
        final PublishSignal publishSignal = (PublishSignal) result;
        assertThat(publishSignal.getSignal()).isEqualTo(envelope.getSignal());
        assertThat(publishSignal.getGroups()).isEqualTo(groups);
        assertThat(publishSignal.getGroupIndexKey().toString()).isEqualTo("index-key");
    }

    @Test
    public void byteBufferRoundTripReconstructsPublishSignal() {
        final Map<String, Integer> groups = Map.of("only-group", 2);
        final PreSerializedPublishSignal envelope = sampleEnvelope(groups, "the-key");

        final ByteBuffer buffer = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN);
        underTest.toBinary(envelope, buffer);
        buffer.flip();
        final Object result = underTest.fromBinary(buffer, underTest.manifest(envelope));

        assertThat(result).isInstanceOf(PublishSignal.class);
        final PublishSignal publishSignal = (PublishSignal) result;
        assertThat(publishSignal.getSignal()).isEqualTo(envelope.getSignal());
        assertThat(publishSignal.getGroups()).isEqualTo(groups);
        assertThat(publishSignal.getGroupIndexKey().toString()).isEqualTo("the-key");
    }

    @Test
    public void byteBufferAndByteArrayPathsProduceIdenticalBytes() {
        final PreSerializedPublishSignal envelope = sampleEnvelope(Map.of("g", 5), "k");

        final byte[] fromArrayPath = underTest.toBinary(envelope);
        final ByteBuffer buffer = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN);
        underTest.toBinary(envelope, buffer);
        buffer.flip();
        final byte[] fromBufferPath = new byte[buffer.remaining()];
        buffer.get(fromBufferPath);

        assertThat(fromBufferPath).isEqualTo(fromArrayPath);
    }

    @Test
    public void readGrantedSubjectsAndHeadersSurviveRoundTrip() {
        final PreSerializedPublishSignal envelope = sampleEnvelope(Map.of("group", 1), "key");

        final byte[] bytes = underTest.toBinary(envelope);
        final PublishSignal result = (PublishSignal) underTest.fromBinary(bytes, underTest.manifest(envelope));

        final DittoHeaders headers = result.getSignal().getDittoHeaders();
        assertThat(headers.getCorrelationId()).contains("correlation-id");
        assertThat(headers.getReadGrantedSubjects()).containsExactlyInAnyOrder(
                AuthorizationSubject.newInstance("ditto:subject-one"),
                AuthorizationSubject.newInstance("ditto:subject-two"),
                AuthorizationSubject.newInstance("ditto:subject-three"));
    }

    @Test
    public void unknownInnerManifestYieldsNotSerializableExceptionNotCrash() {
        final PreSerializedPublishSignal envelope = sampleEnvelope(Map.of("group", 1), "key");
        final byte[] bytes = underTest.toBinary(envelope);

        // The signal manifest is the first length-prefixed field; overwrite its bytes (keeping the length, so all
        // downstream offsets stay valid) with an unknown manifest, mimicking a newer signal type reaching a
        // not-yet-upgraded node. The inner serializer returns a NotSerializableException, which must be propagated
        // rather than turned into an uncaught error on the decoder thread.
        final int manifestLength = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        Arrays.fill(bytes, Integer.BYTES, Integer.BYTES + manifestLength, (byte) 'X');

        final Object result = underTest.fromBinary(bytes, underTest.manifest(envelope));

        assertThat(result).isInstanceOf(NotSerializableException.class);
    }

    @Test
    public void truncatedFrameYieldsNotSerializableExceptionNotCrash() {
        final PreSerializedPublishSignal envelope = sampleEnvelope(Map.of("group", 1), "key");
        final byte[] full = underTest.toBinary(envelope);
        final byte[] truncated = Arrays.copyOf(full, full.length / 2);

        final Object result = underTest.fromBinary(truncated, underTest.manifest(envelope));

        assertThat(result).isInstanceOf(NotSerializableException.class);
    }

    @Test
    public void corruptLengthPrefixDoesNotAllocateHugeBufferAndFailsCleanly() {
        // A garbage/misrouted frame whose first length prefix decodes to Integer.MAX_VALUE must not trigger a huge
        // allocation (OutOfMemoryError); it must fail cleanly as a NotSerializableException.
        final byte[] bytes = ByteBuffer.allocate(2 * Integer.BYTES).putInt(Integer.MAX_VALUE).putInt(0).array();

        final Object result = underTest.fromBinary(bytes, underTest.manifest(sampleEnvelope(Map.of(), "k")));

        assertThat(result).isInstanceOf(NotSerializableException.class);
    }

    @Test
    public void sharedHolderIsThreadSafeUnderConcurrentAccess() throws Exception {
        final SignalBytesHolder holder = new SignalBytesHolder(sampleAcknowledgement());
        final int threadCount = 8;
        final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            final CyclicBarrier barrier = new CyclicBarrier(threadCount);
            final List<Future<byte[]>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                // each thread uses its own serializer instance to maximise the chance of a compute race
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return holder.getOrCompute(new CborJsonifiableSerializer(system)).bytes();
                }));
            }
            final byte[] expected = futures.get(0).get();
            for (final Future<byte[]> future : futures) {
                // CBOR is deterministic, so even under a benign compute race all threads observe equal bytes
                assertThat(future.get()).isEqualTo(expected);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void sharedHolderSerializesSignalOnlyOnce() {
        final SignalBytesHolder holder = new SignalBytesHolder(sampleAcknowledgement());

        final SignalBytesHolder.Serialized first = holder.getOrCompute(new CborJsonifiableSerializer(system));
        final SignalBytesHolder.Serialized second = holder.getOrCompute(new CborJsonifiableSerializer(system));

        assertThat(second).isSameAs(first);
    }

    private static PreSerializedPublishSignal sampleEnvelope(final Map<String, Integer> groups,
            final String groupIndexKey) {
        return PreSerializedPublishSignal.of(new SignalBytesHolder(sampleAcknowledgement()), groups, groupIndexKey);
    }

    private static Acknowledgement sampleAcknowledgement() {
        // headers carry a readGrantedSubjects set, mirroring the enrichment headers whose repeated serialization
        // this optimization removes.
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId("correlation-id")
                .readGrantedSubjects(List.of(
                        AuthorizationSubject.newInstance("ditto:subject-one"),
                        AuthorizationSubject.newInstance("ditto:subject-two"),
                        AuthorizationSubject.newInstance("ditto:subject-three")))
                .build();
        return Acknowledgement.of(AcknowledgementLabel.of("test-ack"),
                EntityId.of(EntityType.of("thing"), "pub.sub.test:thing-id"),
                HttpStatus.OK,
                dittoHeaders);
    }
}
