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
package org.eclipse.ditto.things.service.utils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyBuilder;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
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
 * JMH micro-benchmark for {@link PartialAccessPathCalculator}, used to validate the
 * memoization + trie-rewrite optimizations against the original implementation.
 * <p>
 * Three steady-state ("warm") scenarios — each {@code @Benchmark} method calls
 * {@code calculatePartialAccessPaths} once. Per-invocation setup is avoided so the
 * memoized classification on {@link PolicyEnforcer} survives across invocations, which
 * matches production reality (an enforcer in the cache lives across many events).
 *
 * <h2>Scenarios</h2>
 * <ul>
 *   <li><b>noRestrictedSubjects</b> — single subject with unrestricted root READ. The
 *       enrichment short-circuits without doing any per-event tree walk. Demonstrates the
 *       memo win for the common production case.</li>
 *   <li><b>largeThingRestricted</b> — one restricted subject + a Thing with 50 features ×
 *       10 properties = 500 accessible leaves. Exercises the full collapse path.</li>
 *   <li><b>smallThingRestricted</b> — one restricted subject + a Thing with 2 features ×
 *       5 properties = 10 accessible leaves. Small-scale collapse.</li>
 * </ul>
 *
 * <h2>How to run</h2>
 * <pre>
 * mvn test-compile -pl things/service -am -Djapicmp.skip=true
 * java -cp "$(mvn -pl things/service dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q):things/service/target/classes:things/service/target/test-classes" \
 *      org.eclipse.ditto.things.service.utils.PartialAccessPathCalculatorBenchmark
 * </pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@Threads(1)
public class PartialAccessPathCalculatorBenchmark {

    private static final PolicyId POLICY_ID = PolicyId.of("bench:policy");
    private static final ThingId THING_ID = ThingId.of("bench:thing");

    // --- noRestrictedSubjects ---
    private Thing thingNoRestricted;
    private PolicyEnforcer enforcerNoRestricted;
    private JsonObject thingJsonNoRestricted;

    // --- largeThingRestricted ---
    private Thing thingLarge;
    private PolicyEnforcer enforcerLarge;
    private JsonObject thingJsonLarge;

    // --- smallThingRestricted ---
    private Thing thingSmall;
    private PolicyEnforcer enforcerSmall;
    private JsonObject thingJsonSmall;

    // --- manyRestrictedSubjects ---
    private Thing thingManySubjects;
    private PolicyEnforcer enforcerManySubjects;
    private JsonObject thingJsonManySubjects;

    @Setup
    public void setup() {
        // Scenario A — one subject with full root READ. No restricted subjects → fast path.
        thingNoRestricted = buildThing(5, 10);
        thingJsonNoRestricted = thingNoRestricted.toJson();
        enforcerNoRestricted = PolicyEnforcer.of(Policy.newBuilder(POLICY_ID)
                .setSubjectFor("alice", subject("user:alice"))
                .setGrantedPermissionsFor("alice", ResourceKey.newInstance("thing", "/"), "READ", "WRITE")
                .build());

        // Scenario B — alice unrestricted on root, bob restricted to first 5 features (50 leaves).
        thingLarge = buildThing(50, 10);
        thingJsonLarge = thingLarge.toJson();
        final PolicyBuilder largeBuilder = Policy.newBuilder(POLICY_ID)
                .setSubjectFor("alice", subject("user:alice"))
                .setGrantedPermissionsFor("alice", ResourceKey.newInstance("thing", "/"), "READ", "WRITE")
                .setSubjectFor("bob", subject("user:bob"));
        for (int i = 0; i < 5; i++) {
            largeBuilder.setGrantedPermissionsFor("bob",
                    ResourceKey.newInstance("thing", "/features/feature" + i), "READ");
        }
        enforcerLarge = PolicyEnforcer.of(largeBuilder.build());

        // Scenario C — alice unrestricted, bob restricted to /features/feature0 (5 leaves).
        thingSmall = buildThing(2, 5);
        thingJsonSmall = thingSmall.toJson();
        enforcerSmall = PolicyEnforcer.of(Policy.newBuilder(POLICY_ID)
                .setSubjectFor("alice", subject("user:alice"))
                .setGrantedPermissionsFor("alice", ResourceKey.newInstance("thing", "/"), "READ", "WRITE")
                .setSubjectFor("bob", subject("user:bob"))
                .setGrantedPermissionsFor("bob",
                        ResourceKey.newInstance("thing", "/features/feature0"), "READ")
                .build());

        // Scenario D — 10 restricted subjects, each granted READ on a different feature.
        // Exposes per-subject loop cost in getAccessiblePathsForSubjects.
        thingManySubjects = buildThing(50, 10);
        thingJsonManySubjects = thingManySubjects.toJson();
        final PolicyBuilder manyBuilder = Policy.newBuilder(POLICY_ID)
                .setSubjectFor("alice", subject("user:alice"))
                .setGrantedPermissionsFor("alice", ResourceKey.newInstance("thing", "/"), "READ", "WRITE");
        for (int i = 0; i < 10; i++) {
            final String label = "restricted" + i;
            manyBuilder.setSubjectFor(label, subject("user:restricted" + i));
            manyBuilder.setGrantedPermissionsFor(label,
                    ResourceKey.newInstance("thing", "/features/feature" + i), "READ");
        }
        enforcerManySubjects = PolicyEnforcer.of(manyBuilder.build());
    }

    @Benchmark
    public void noRestrictedSubjects(final Blackhole bh) {
        final Map<String, List<JsonPointer>> result = PartialAccessPathCalculator
                .calculatePartialAccessPaths(thingNoRestricted, enforcerNoRestricted, thingJsonNoRestricted);
        bh.consume(result);
    }

    @Benchmark
    public void largeThingRestricted(final Blackhole bh) {
        final Map<String, List<JsonPointer>> result = PartialAccessPathCalculator
                .calculatePartialAccessPaths(thingLarge, enforcerLarge, thingJsonLarge);
        bh.consume(result);
    }

    @Benchmark
    public void smallThingRestricted(final Blackhole bh) {
        final Map<String, List<JsonPointer>> result = PartialAccessPathCalculator
                .calculatePartialAccessPaths(thingSmall, enforcerSmall, thingJsonSmall);
        bh.consume(result);
    }

    @Benchmark
    public void manyRestrictedSubjects(final Blackhole bh) {
        final Map<String, List<JsonPointer>> result = PartialAccessPathCalculator
                .calculatePartialAccessPaths(thingManySubjects, enforcerManySubjects, thingJsonManySubjects);
        bh.consume(result);
    }

    private static Thing buildThing(final int featureCount, final int propsPerFeature) {
        final var builder = ThingsModelFactory.newThingBuilder().setId(THING_ID);
        for (int f = 0; f < featureCount; f++) {
            final JsonObjectBuilder props = JsonFactory.newObjectBuilder();
            for (int p = 0; p < propsPerFeature; p++) {
                props.set("prop" + p, p);
            }
            final FeatureProperties fp = ThingsModelFactory.newFeatureProperties(props.build());
            final Feature feature = ThingsModelFactory.newFeature("feature" + f, null, fp);
            builder.setFeature(feature);
        }
        return builder.build();
    }

    private static Subject subject(final String id) {
        return Subject.newInstance(SubjectId.newInstance(id), SubjectType.GENERATED);
    }

    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder()
                .include(PartialAccessPathCalculatorBenchmark.class.getSimpleName())
                .shouldFailOnError(true)
                .build();
        new Runner(opt).run();
    }
}
