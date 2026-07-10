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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.internal.utils.cluster.CborJsonifiableSerializer;
import org.eclipse.ditto.internal.utils.pubsub.TestMappingStrategies;
import org.eclipse.ditto.internal.utils.pubsub.api.PreSerializedPublishSignal;
import org.eclipse.ditto.internal.utils.pubsub.api.PublishSignal;
import org.eclipse.ditto.internal.utils.pubsub.api.SignalBytesHolder;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.junit.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import com.typesafe.config.ConfigFactory;

/**
 * JMH benchmark digging into the latency cost of the pre-serialize pub/sub fan-out ({@code SignalBytesHolder}) vs the
 * current per-destination serialization.
 * <p>
 * Crucial methodology point that the earlier hand-rolled benchmark got wrong: with the flag <em>off</em>, Pekko Artery
 * serializes each {@link PublishSignal} straight into <em>its own outbound direct ByteBuffer</em> via the
 * {@code ByteBufferSerializer.toBinary(Object, ByteBuffer)} path — a zero-copy write that never touches Ditto's own
 * {@code DirectByteBufferPool}. This benchmark therefore models the baseline with that same {@code (Object, ByteBuffer)}
 * path, so the comparison against the flag-on path (which routes through {@code SignalBytesHolder.getOrCompute} →
 * {@code innerSerializer.toBinary(signal)} — the pooled {@code byte[]} path + an extra copy) is faithful.
 * <p>
 * Not a CI test (no {@code *Test} class name). Run:
 * <pre>
 *   mvn test -pl internal/utils/pubsub -am -Djapicmp.skip=true \
 *       -Dtest=SignalFanoutSerializationJmh -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 * The {@link #runBenchmarks()} entry point runs it single-threaded and again with 8 threads (to expose contention on
 * the shared serializer buffer pool), with the GC profiler enabled to show allocation-per-op.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class SignalFanoutSerializationJmh {

    /** Number of remote subscriber nodes a published signal fans out to. Ditto's real fan-out degree is small. */
    @Param({"1", "2", "3", "5", "10"})
    public int fanout;

    /** Size proxy: number of read-granted subjects in the headers (dominant serialized content for enriched events). */
    @Param({"2", "50"})
    public int subjects;

    private ExtendedActorSystem system;
    private CborJsonifiableSerializer cbor;
    private PreSerializedPublishSignalSerializer envelopeSerializer;
    private Acknowledgement signal;
    private Map<String, Integer> groups;
    private static final String INDEX_KEY = "index-key";

    // pre-serialized wire forms for the receiver-side (deserialize) benchmarks
    private byte[] publishSignalWire;
    private String publishSignalManifest;
    private byte[] envelopeWire;
    private String envelopeManifest;

    @State(Scope.Thread)
    public static class Buf {
        // Mirrors Artery's per-lane outbound frame buffer (maximum-frame-size = 256 KiB in ditto-pekko-config).
        final ByteBuffer arteryBuf = ByteBuffer.allocateDirect(256 * 1024);
    }

    @Setup(Level.Trial)
    public void setUp() {
        // The JUnit @ClassRule that normally disables tracing does not run in the forked JMH JVM, so init it here
        // (otherwise the serializer's startTracingSpanForSerialization throws "uninitialized state").
        DittoTracing.init(DittoTracingInitResource.TracingConfigBuilder.defaultValues().withTracingDisabled().build());
        system = (ExtendedActorSystem) ExtendedActorSystem.create("jmh", ConfigFactory.parseMap(
                Map.of("ditto.mapping-strategy.implementation", TestMappingStrategies.class.getName())));
        // shared across threads on purpose: exposes real DirectByteBufferPool contention on the flag-on path
        cbor = new CborJsonifiableSerializer(system);
        envelopeSerializer = new PreSerializedPublishSignalSerializer(system);
        final List<AuthorizationSubject> subs = IntStream.range(0, subjects)
                .mapToObj(i -> AuthorizationSubject.newInstance("ditto:subject-" + i))
                .collect(Collectors.toList());
        signal = Acknowledgement.of(AcknowledgementLabel.of("bench-ack"),
                EntityId.of(EntityType.of("thing"), "pub.sub.test:thing-id"),
                HttpStatus.OK,
                DittoHeaders.newBuilder().correlationId("correlation-id").readGrantedSubjects(subs).build());
        groups = Map.of("group-a", 3);

        // pre-serialize both wire forms once, for the receiver-side (deserialize) benchmarks
        final PublishSignal publishSignal = PublishSignal.of(signal, groups, INDEX_KEY);
        publishSignalWire = cbor.toBinary(publishSignal);
        publishSignalManifest = cbor.manifest(publishSignal);
        final PreSerializedPublishSignal envelope =
                PreSerializedPublishSignal.of(new SignalBytesHolder(signal), groups, INDEX_KEY);
        envelopeWire = envelopeSerializer.toBinary(envelope);
        envelopeManifest = envelopeSerializer.manifest(envelope);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (system != null) {
            system.terminate();
        }
        DittoTracing.reset();
    }

    /**
     * Flag OFF (baseline): Artery serializes one {@link PublishSignal} per destination straight into its outbound
     * buffer. Zero-copy, no Ditto pool involvement.
     */
    @Benchmark
    public void flagOff_perDestinationSerialize(final Buf buf, final Blackhole bh) {
        for (int d = 0; d < fanout; d++) {
            buf.arteryBuf.clear();
            cbor.toBinary(PublishSignal.of(signal, groups, INDEX_KEY), buf.arteryBuf);
            bh.consume(buf.arteryBuf.position());
        }
    }

    /**
     * Flag ON (current impl): one shared {@link SignalBytesHolder}; the first destination triggers
     * {@code getOrCompute} (pooled {@code byte[]} serialize + copy), each destination then writes the envelope frame
     * (a second copy of the shared bytes) into Artery's buffer.
     */
    @Benchmark
    public void flagOn_sharedHolderEnvelope(final Buf buf, final Blackhole bh) {
        final SignalBytesHolder holder = new SignalBytesHolder(signal); // fresh per publication, as in prod
        for (int d = 0; d < fanout; d++) {
            buf.arteryBuf.clear();
            envelopeSerializer.toBinary(PreSerializedPublishSignal.of(holder, groups, INDEX_KEY), buf.arteryBuf);
            bh.consume(buf.arteryBuf.position());
        }
    }

    /** Flag OFF receiver: each of the {@code fanout} nodes deserializes a {@link PublishSignal}. */
    @Benchmark
    public void flagOff_perDestinationDeserialize(final Blackhole bh) {
        for (int d = 0; d < fanout; d++) {
            bh.consume(cbor.fromBinary(publishSignalWire, publishSignalManifest));
        }
    }

    /** Flag ON receiver: each node deserializes the envelope, which reconstructs a {@link PublishSignal}. */
    @Benchmark
    public void flagOn_perDestinationDeserialize(final Blackhole bh) {
        for (int d = 0; d < fanout; d++) {
            bh.consume(envelopeSerializer.fromBinary(envelopeWire, envelopeManifest));
        }
    }

    @Test
    public void runBenchmarks() throws RunnerException {
        new Runner(new OptionsBuilder()
                .include(SignalFanoutSerializationJmh.class.getSimpleName())
                .warmupIterations(3).warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
                .measurementIterations(4).measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
                .forks(1)
                .threads(1)
                .addProfiler("gc")
                .build()).run();
    }

    @Test
    public void runBenchmarksContended() throws RunnerException {
        // 8 threads sharing the serializer -> exposes DirectByteBufferPool lock contention on the flag-on path
        new Runner(new OptionsBuilder()
                .include(SignalFanoutSerializationJmh.class.getSimpleName())
                .param("subjects", "50")
                .warmupIterations(3).warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
                .measurementIterations(4).measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(1))
                .forks(1)
                .threads(8)
                .addProfiler("gc")
                .build()).run();
    }

    public static void main(final String[] args) throws RunnerException {
        new SignalFanoutSerializationJmh().runBenchmarks();
    }
}
