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
package org.eclipse.ditto.things.service.persistence.actors.enrichment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
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
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
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
 * JMH micro-benchmark quantifying the per-thing partial-access-paths cache in {@link ThingEventEnricher}.
 * <p>
 * Both benchmarks enrich the <em>same</em> Thing (stable structure) repeatedly — the production shape of a
 * value-only telemetry update stream. {@code cacheHit} uses a cache-enabled enricher (steady-state: every
 * invocation after the first is a cache hit); {@code recompute} uses a cache-disabled enricher (every
 * invocation recomputes). The delta is the cache win.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@Threads(1)
public class ThingEventEnricherCacheBenchmark {

    private static final PolicyId POLICY_ID = PolicyId.of("bench:policy");
    private static final ThingId THING_ID = ThingId.of("bench:thing");

    private ThingEventEnricher enricherCacheEnabled;
    private ThingEventEnricher enricherCacheDisabled;
    private Thing thing;
    private AttributeModified event;

    @Setup
    public void setup() {
        thing = buildThing(50, 10);

        // alice unrestricted on root; bob restricted to the first 5 features (50 accessible leaves).
        final PolicyBuilder builder = Policy.newBuilder(POLICY_ID)
                .setRevision(1L)
                .setSubjectFor("alice", subject("user:alice"))
                .setGrantedPermissionsFor("alice", ResourceKey.newInstance("thing", "/"), "READ", "WRITE")
                .setSubjectFor("bob", subject("user:bob"));
        for (int i = 0; i < 5; i++) {
            builder.setGrantedPermissionsFor("bob", ResourceKey.newInstance("thing", "/features/feature" + i), "READ");
        }
        final PolicyEnforcer enforcer = PolicyEnforcer.of(builder.build());
        final PolicyEnforcerProvider provider = policyId -> CompletableFuture.completedFuture(Optional.of(enforcer));

        enricherCacheEnabled = new ThingEventEnricher(provider, true, true);
        enricherCacheDisabled = new ThingEventEnricher(provider, true, false);

        event = AttributeModified.of(THING_ID, JsonPointer.of("someValue"), JsonValue.of(1), 4L,
                Instant.now(), DittoHeaders.empty(), null);
    }

    @Benchmark
    public void cacheHit(final Blackhole bh) {
        bh.consume(enricherCacheEnabled
                .enrichWithPredefinedExtraFields(List.of(), THING_ID, thing, POLICY_ID, event)
                .toCompletableFuture().join());
    }

    @Benchmark
    public void recompute(final Blackhole bh) {
        bh.consume(enricherCacheDisabled
                .enrichWithPredefinedExtraFields(List.of(), THING_ID, thing, POLICY_ID, event)
                .toCompletableFuture().join());
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
                .include(ThingEventEnricherCacheBenchmark.class.getSimpleName())
                .shouldFailOnError(true)
                .build();
        new Runner(opt).run();
    }
}
