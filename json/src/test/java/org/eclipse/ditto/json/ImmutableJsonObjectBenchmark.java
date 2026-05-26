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
package org.eclipse.ditto.json;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH micro-benchmark for {@link ImmutableJsonObject} construction, used to validate the
 * lazy-encoding refactor in {@code SoftReferencedFieldMap}.
 *
 * <h2>Scenarios</h2>
 * <p>Each {@code @Benchmark} exercises the programmatic-build path
 * ({@link JsonObjectBuilder}) that is the hot site in production for
 * {@code Thing.toJson}, {@code Feature.toJson}, {@code AbstractCommandResponse.toJson}
 * and similar emit-side flows.</p>
 *
 * <ul>
 *   <li><b>buildSmallNoSerialize</b> &mdash; build a 5-field object, discard. Measures
 *       pure construction cost; this is the maximum-saving case once eager pre-encoding is
 *       deferred (short-lived JsonObjects that never get serialised).</li>
 *   <li><b>buildLargeNoSerialize</b> &mdash; same, but 50 fields. Larger field map = more
 *       eager work being eliminated.</li>
 *   <li><b>buildSmallThenToString</b> &mdash; build then call {@code toString()}. After
 *       the refactor this should match the baseline (string is materialised lazily on
 *       first call) &mdash; serves as a no-regression check for the case where the cache
 *       <em>is</em> consumed.</li>
 *   <li><b>buildLargeThenToString</b> &mdash; same, 50 fields.</li>
 *   <li><b>buildSmallAccessFields</b> &mdash; build then call {@code getField} 5 times.
 *       Should be substantially faster after the refactor since no encoding happens.</li>
 * </ul>
 *
 * <h2>How to run</h2>
 * <pre>
 * mvn test-compile -pl json -am -Djapicmp.skip=true
 * java -cp "$(mvn -pl json dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q):json/target/classes:json/target/test-classes" \
 *      org.eclipse.ditto.json.ImmutableJsonObjectBenchmark
 * </pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@Threads(1)
public class ImmutableJsonObjectBenchmark {

    private static final int LARGE_FIELD_COUNT = 50;

    private String[] smallKeys;
    private JsonValue[] smallValues;
    private String[] largeKeys;
    private JsonValue[] largeValues;

    @Setup
    public void setup() {
        smallKeys = new String[]{"thingId", "policyId", "_revision", "definition", "_modified"};
        smallValues = new JsonValue[]{
                JsonValue.of("ns:thing-1"),
                JsonValue.of("ns:policy-1"),
                JsonValue.of(42L),
                JsonValue.of("ditto:robot:1.0.0"),
                JsonValue.of("2026-05-26T07:11:46Z"),
        };

        largeKeys = new String[LARGE_FIELD_COUNT];
        largeValues = new JsonValue[LARGE_FIELD_COUNT];
        for (int i = 0; i < LARGE_FIELD_COUNT; i++) {
            largeKeys[i] = "feature_" + i;
            largeValues[i] = JsonValue.of("value_" + i);
        }
    }

    @Benchmark
    public void buildSmallNoSerialize(final Blackhole bh) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        for (int i = 0; i < smallKeys.length; i++) {
            builder.set(smallKeys[i], smallValues[i]);
        }
        bh.consume(builder.build());
    }

    @Benchmark
    public void buildLargeNoSerialize(final Blackhole bh) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        for (int i = 0; i < largeKeys.length; i++) {
            builder.set(largeKeys[i], largeValues[i]);
        }
        bh.consume(builder.build());
    }

    @Benchmark
    public void buildSmallThenToString(final Blackhole bh) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        for (int i = 0; i < smallKeys.length; i++) {
            builder.set(smallKeys[i], smallValues[i]);
        }
        bh.consume(builder.build().toString());
    }

    @Benchmark
    public void buildLargeThenToString(final Blackhole bh) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        for (int i = 0; i < largeKeys.length; i++) {
            builder.set(largeKeys[i], largeValues[i]);
        }
        bh.consume(builder.build().toString());
    }

    @Benchmark
    public void buildSmallAccessFields(final Blackhole bh) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        for (int i = 0; i < smallKeys.length; i++) {
            builder.set(smallKeys[i], smallValues[i]);
        }
        final JsonObject obj = builder.build();
        for (int i = 0; i < smallKeys.length; i++) {
            bh.consume(obj.getValue(smallKeys[i]));
        }
    }

    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder()
                .include(ImmutableJsonObjectBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
