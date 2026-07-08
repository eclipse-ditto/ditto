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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
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
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Manual micro-benchmark (NOT run in CI — no {@code Test} suffix; run with
 * {@code mvn test -pl internal/utils/pubsub -Dtest=PreSerializeFanoutBenchmark}). Compares, per published event
 * fanned out to {@code FANOUT} destinations, the current path (serialize the full signal once per destination) with
 * the pre-serialized path (serialize the signal once, then write the lightweight envelope frame per destination).
 * A warmed timing loop — indicative, not a JMH-grade measurement.
 */
public final class PreSerializeFanoutBenchmark {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final int FANOUT = 5;              // ~ prod avg 4.74 subscriber nodes per event
    private static final int READ_GRANTED_SUBJECTS = 50; // large enrichment header (the dominant serialized content)
    private static final int WARMUP = 50_000;
    private static final int ITERATIONS = 200_000;

    private static ExtendedActorSystem system;

    @BeforeClass
    public static void setUpClass() {
        system = (ExtendedActorSystem) ExtendedActorSystem.create("bench", ConfigFactory.parseMap(
                Map.of("ditto.mapping-strategy.implementation", TestMappingStrategies.class.getName())));
    }

    @AfterClass
    public static void tearDownClass() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void compareSerializationCostPerPublication() {
        final CborJsonifiableSerializer cbor = new CborJsonifiableSerializer(system);
        final PreSerializedPublishSignalSerializer envelopeSerializer =
                new PreSerializedPublishSignalSerializer(system);
        final Acknowledgement signal = sampleSignal();
        final Map<String, Integer> groups = Map.of("group-a", 3);

        // warmup
        for (int i = 0; i < WARMUP; i++) {
            blackhole += currentPathBytes(cbor, signal, groups);
            blackhole += preSerializedPathBytes(envelopeSerializer, signal, groups);
        }

        final long currentNanos = measure(() -> currentPathBytes(cbor, signal, groups));
        final long preSerializedNanos = measure(() -> preSerializedPathBytes(envelopeSerializer, signal, groups));

        final double currentUs = currentNanos / 1000.0 / ITERATIONS;
        final double preUs = preSerializedNanos / 1000.0 / ITERATIONS;
        System.out.printf("%n=== pre-serialize fan-out micro-benchmark (FANOUT=%d, readGrantedSubjects=%d) ===%n",
                FANOUT, READ_GRANTED_SUBJECTS);
        System.out.printf("current   (serialize signal x%d):        %.2f us/publication%n", FANOUT, currentUs);
        System.out.printf("optimized (serialize once + %d copies):  %.2f us/publication%n", FANOUT, preUs);
        System.out.printf("speedup: %.2fx   (blackhole=%d)%n", currentUs / preUs, blackhole);
    }

    private static long measure(final Runnable runnable) {
        final long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            runnable.run();
        }
        return System.nanoTime() - start;
    }

    /** Current path: one full PublishSignal serialization per destination (Artery re-serializes per association). */
    private static int currentPathBytes(final CborJsonifiableSerializer cbor, final Acknowledgement signal,
            final Map<String, Integer> groups) {
        int bytes = 0;
        for (int d = 0; d < FANOUT; d++) {
            bytes += cbor.toBinary(PublishSignal.of(signal, groups, "index-key")).length;
        }
        return bytes;
    }

    /** Optimized path: serialize the signal once (shared holder), then write the envelope frame per destination. */
    private static int preSerializedPathBytes(final PreSerializedPublishSignalSerializer serializer,
            final Acknowledgement signal, final Map<String, Integer> groups) {
        final SignalBytesHolder holder = new SignalBytesHolder(signal); // fresh per publication, as in prod
        int bytes = 0;
        for (int d = 0; d < FANOUT; d++) {
            bytes += serializer.toBinary(PreSerializedPublishSignal.of(holder, groups, "index-key")).length;
        }
        return bytes;
    }

    private static Acknowledgement sampleSignal() {
        final List<AuthorizationSubject> subjects = IntStream.range(0, READ_GRANTED_SUBJECTS)
                .mapToObj(i -> AuthorizationSubject.newInstance("ditto:subject-" + i))
                .collect(Collectors.toList());
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId("correlation-id")
                .readGrantedSubjects(subjects)
                .build();
        return Acknowledgement.of(AcknowledgementLabel.of("bench-ack"),
                EntityId.of(EntityType.of("thing"), "pub.sub.test:thing-id"),
                HttpStatus.OK,
                dittoHeaders);
    }

    // prevent dead-code elimination of the serialization work
    @SuppressWarnings("java:S1104")
    public static long blackhole = 0;
}
