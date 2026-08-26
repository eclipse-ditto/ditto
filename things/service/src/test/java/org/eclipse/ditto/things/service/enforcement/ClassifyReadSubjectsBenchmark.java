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
package org.eclipse.ditto.things.service.enforcement;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyBuilder;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.SubjectClassification;
import org.eclipse.ditto.things.model.ThingId;
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
 * JMH micro-benchmark quantifying the per-resource {@code classifySubjects(resource, READ)} memo added to
 * {@link PolicyEnforcer#classifyReadSubjects}. This is the walk that the things-service per-event
 * read-authorization ({@link ThingCommandEnforcement#addEffectedReadSubjectsToThingSignal}) and read-grant
 * collection perform for every emitted ThingEvent.
 * <p>
 * All benchmarks classify against the <em>same</em> policy and resource — the production shape of a value-only
 * telemetry update stream that repeatedly touches the same resource paths.
 * <ul>
 *   <li>{@code memoizedHit}: reused PolicyEnforcer; every call after the first is a cache hit (the new path).</li>
 *   <li>{@code baselineRecompute}: raw {@code Enforcer.classifySubjects} — a full tree walk every call (old path).</li>
 *   <li>{@code memoizedMiss}: bounded cache (size 1) with two alternating keys so (almost) every call misses —
 *       demonstrates the memo is not slower than the baseline on the pathological no-repeat workload.</li>
 *   <li>{@code endToEndMemoized}: the real {@code addEffectedReadSubjectsToThingSignal} per-event path.</li>
 * </ul>
 * Expectation: {@code memoizedHit} &gg; {@code baselineRecompute} (large win), and
 * {@code memoizedMiss} &ge; {@code baselineRecompute} within noise (no regression).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@Threads(1)
public class ClassifyReadSubjectsBenchmark {

    private static final PolicyId POLICY_ID = PolicyId.of("bench:policy");
    private static final ThingId THING_ID = ThingId.of("bench:thing");
    private static final Permissions READ = Permissions.newInstance(Permission.READ);
    private static final ResourceKey EVENT_KEY =
            ResourceKey.newInstance("thing", "/features/feature3/properties/prop2");
    /** Pool of distinct resource keys, far larger than the bounded cache below, so cycling through them
     * yields an (almost) 100 % miss rate — the pathological no-repeat workload. */
    private static final int MISS_POOL = 64;
    private static final int BOUNDED_MAX = 4;
    private static final ResourceKey[] MISS_KEYS = new ResourceKey[MISS_POOL];

    static {
        for (int i = 0; i < MISS_POOL; i++) {
            MISS_KEYS[i] = ResourceKey.newInstance("thing", "/features/feature" + i);
        }
    }

    /**
     * Subject count of a production policy after the authz-format migration, where every human role is granted to
     * both a tenant-wildcard and a tenant-qualified subject. The rendering cost this benchmark measures scales with
     * this number, so a two-subject fixture would understate it by an order of magnitude.
     */
    private static final int WIDE_SUBJECT_COUNT = 22;

    private PolicyEnforcer memoizedEnforcer;
    private PolicyEnforcer wideEnforcer;
    private PolicyEnforcer boundedEnforcer;
    private Enforcer rawEnforcer;
    private AttributeModified event;
    private int missIdx;

    @Setup
    public void setup() {
        // alice unrestricted on root; bob restricted READ to the first 10 features → a non-trivial tree walk.
        final PolicyBuilder builder = Policy.newBuilder(POLICY_ID)
                .setRevision(1L)
                .setSubjectFor("alice", subject("user:alice"))
                .setGrantedPermissionsFor("alice", ResourceKey.newInstance("thing", "/"), Permission.READ)
                .setSubjectFor("bob", subject("user:bob"));
        for (int i = 0; i < 10; i++) {
            builder.setGrantedPermissionsFor("bob", ResourceKey.newInstance("thing", "/features/feature" + i),
                    Permission.READ);
        }
        final Policy policy = builder.build();

        memoizedEnforcer = PolicyEnforcer.of(policy);
        boundedEnforcer = PolicyEnforcer.of(policy, 100, BOUNDED_MAX);
        rawEnforcer = PolicyEnforcer.of(policy).getEnforcer();

        // Production-shaped policy: every role granted to both subject variants the authz format emits, with
        // realistically long subject IDs.
        final PolicyBuilder wideBuilder = Policy.newBuilder(POLICY_ID).setRevision(1L);
        for (int i = 0; i < WIDE_SUBJECT_COUNT / 2; i++) {
            final String role = "ddm-smartheating:grid-operator-" + i;
            wideBuilder.setSubjectFor("wildcard" + i, subject("dex:authz:###prod:" + role))
                    .setGrantedPermissionsFor("wildcard" + i, ResourceKey.newInstance("thing", "/"), Permission.READ)
                    .setSubjectFor("tenant" + i, subject("dex:authz:io.beyonnex#kalo##prod:" + role))
                    .setGrantedPermissionsFor("tenant" + i, ResourceKey.newInstance("thing", "/"), Permission.READ);
        }
        wideEnforcer = PolicyEnforcer.of(wideBuilder.build());

        event = AttributeModified.of(THING_ID, JsonPointer.of("x"), JsonValue.of(1), 2L,
                Instant.now(), DittoHeaders.empty(), null);
    }

    @Benchmark
    public void memoizedHit(final Blackhole bh) {
        bh.consume(memoizedEnforcer.classifyReadSubjects(EVENT_KEY));
    }

    @Benchmark
    public void baselineRecompute(final Blackhole bh) {
        bh.consume(rawEnforcer.classifySubjects(EVENT_KEY, READ));
    }

    @Benchmark
    public void memoizedMiss(final Blackhole bh) {
        missIdx = (missIdx + 1) % MISS_POOL;
        bh.consume(boundedEnforcer.classifyReadSubjects(MISS_KEYS[missIdx]));
    }

    @Benchmark
    public void endToEndMemoized(final Blackhole bh) {
        bh.consume(ThingCommandEnforcement.addEffectedReadSubjectsToThingSignal(event, memoizedEnforcer, true));
    }

    /**
     * Frozen baseline of {@link #endToEndMemoized}: the body the enforcement had before the rendered header value
     * was memoized on the PolicyEnforcer — merge the classifications into a fresh HashSet and re-serialize the
     * subject IDs into the header on every event.
     */
    @Benchmark
    public void endToEndRenderPerEvent(final Blackhole bh) {
        bh.consume(renderPerEvent(memoizedEnforcer));
    }

    /** The new path on a production-shaped subject set -- the case the memo actually targets. */
    @Benchmark
    public void endToEndMemoizedWideSubjects(final Blackhole bh) {
        bh.consume(ThingCommandEnforcement.addEffectedReadSubjectsToThingSignal(event, wideEnforcer, true));
    }

    /** The frozen baseline on the same production-shaped subject set. */
    @Benchmark
    public void endToEndRenderPerEventWideSubjects(final Blackhole bh) {
        bh.consume(renderPerEvent(wideEnforcer));
    }

    private AttributeModified renderPerEvent(final PolicyEnforcer policyEnforcer) {
        final SubjectClassification rootClassification = policyEnforcer.getRootResourceReadClassification();
        final Set<AuthorizationSubject> allReadSubjects =
                new HashSet<>(policyEnforcer.classifyReadSubjects(EVENT_KEY).getUnrestricted());
        allReadSubjects.addAll(rootClassification.getPartialOnly());
        allReadSubjects.addAll(rootClassification.getUnrestricted());
        return event.setDittoHeaders(DittoHeaders.newBuilder(event.getDittoHeaders())
                .readGrantedSubjects(allReadSubjects)
                .build());
    }

    private static Subject subject(final String id) {
        return Subject.newInstance(SubjectId.newInstance(id), SubjectType.GENERATED);
    }

    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder()
                .include(ClassifyReadSubjectsBenchmark.class.getSimpleName())
                .shouldFailOnError(true)
                .build();
        new Runner(opt).run();
    }
}
