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

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyBuilder;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.enforcers.tree.TreeBasedPolicyEnforcer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH micro-benchmark for the per-instance authorization-verdict memo on {@link TreeBasedPolicyEnforcer}: the
 * policy-tree walk ({@code visitTree}) that every authorization check performed on each call, which production
 * JFR showed as a dominant things-service CPU hotspot.
 * <p>
 * Run with {@code -prof gc} to also get the allocation figures — the memo-hit path is allocation-dominated, so
 * {@code gc.alloc.rate.norm} (B/op) is the more stable signal of the two:
 * <pre>
 * mvn install -DskipTests -pl policies/model,policies/enforcement,things/service -am
 * mvn exec:exec -pl things/service -Dexec.classpathScope=test -Dexec.executable=java \
 *   -Dexec.args="-classpath %classpath \
 *     org.eclipse.ditto.things.service.enforcement.AuthorizationVerdictMemoBenchmark -prof gc"
 * </pre>
 * Use {@code exec:exec} rather than {@code exec:java}: the latter runs in-process, so JMH's forked VM does not
 * inherit the classpath and every benchmark fails with {@code ClassNotFoundException: ForkedMain}. The
 * {@code install} step matters too — {@code -pl things/service} alone resolves {@code ditto-policies-model}
 * from the local repository, which will not have this branch's enforcer.
 * Benchmarks, all against the <em>same</em> policy and resource (the production shape: the same subjects
 * repeatedly accessing the same resources):
 * <ul>
 *   <li>{@code hasUnrestrictedPermissionsMemoHit} / {@code getSubjectsWithPermissionMemoHit}: warm enforcer,
 *       every call a memo hit — the fast path.</li>
 *   <li>{@code hasUnrestrictedPermissionsNoMemo} / {@code getSubjectsWithPermissionNoMemo}: enforcer built with
 *       {@code maxMemoSize = 0}, so no memo is allocated and every call walks the whole tree — the baseline the
 *       memo removes.</li>
 *   <li>{@code hasUnrestrictedPermissionsMemoMiss} vs {@code hasUnrestrictedPermissionsNoMemoSameKeys}: a tiny
 *       cap plus a pool of distinct resource keys, so (almost) every call misses. Both cycle the <em>same</em>
 *       key pool, so the pair is directly comparable and shows the memo adds no measurable overhead on the
 *       pathological no-repeat workload.</li>
 *   <li>{@code formerlyEagerPerHitWork}: the work a memo <em>hit</em> used to pay before it consulted the map —
 *       building the absolute resource pointer and rebuilding the caller subject-id {@link Set} by streaming
 *       the {@link AuthorizationContext}. Neither is part of the memo key and neither is used on a hit, so both
 *       now happen inside the memo supplier (miss-only). Keeping this measurable guards against reintroducing
 *       eager work on the hit path: {@code hasUnrestrictedPermissionsMemoHit} must stay well below
 *       {@code hasUnrestrictedPermissionsMemoHit + formerlyEagerPerHitWork}.</li>
 * </ul>
 * Expectations: {@code memoHit} &lll; {@code noMemo} (the win), {@code memoMiss} ≈ {@code noMemoSameKeys}
 * within noise (no regression), and {@code formerlyEagerPerHitWork} costing several times {@code memoHit}
 * itself in allocation — which is why paying it eagerly, before the map lookup, dominated the hit path.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 2)
@Fork(1)
@Threads(1)
public class AuthorizationVerdictMemoBenchmark {

    private static final PolicyId POLICY_ID = PolicyId.of("bench:policy");
    private static final Permissions READ = Permissions.newInstance(Permission.READ);
    private static final ResourceKey THING_ROOT = ResourceKey.newInstance("thing", "/");
    private static final String CALLER = "user:caller-0";

    /** Distinct resource keys, far more than {@link #BOUNDED_MAX}, so cycling them (almost) always misses. */
    private static final int MISS_POOL = 64;
    private static final int BOUNDED_MAX = 4;
    private static final ResourceKey[] MISS_KEYS = new ResourceKey[MISS_POOL];

    static {
        for (int i = 0; i < MISS_POOL; i++) {
            MISS_KEYS[i] = ResourceKey.newInstance("thing", "/features/feature" + i);
        }
    }

    /** Number of policy labels, i.e. how much tree there is to walk on a miss. */
    @Param({"10", "100"})
    public String policyLabels;

    /** Number of subjects in the calling AuthorizationContext. */
    @Param({"1", "5"})
    public String callerSubjects;

    private TreeBasedPolicyEnforcer warmEnforcer;
    private TreeBasedPolicyEnforcer noMemoEnforcer;
    private TreeBasedPolicyEnforcer boundedEnforcer;
    private AuthorizationContext callerContext;
    private int missIdx;

    @Setup
    public void setup() {
        final Policy policy = buildPolicy(Integer.parseInt(policyLabels));
        warmEnforcer = TreeBasedPolicyEnforcer.createInstance(policy);
        noMemoEnforcer = TreeBasedPolicyEnforcer.createInstance(policy, 0);
        boundedEnforcer = TreeBasedPolicyEnforcer.createInstance(policy, BOUNDED_MAX);

        callerContext = context(Integer.parseInt(callerSubjects));
        // Prime the warm enforcer so every measured hit-path call really is a hit.
        warmEnforcer.hasUnrestrictedPermissions(THING_ROOT, callerContext, READ);
        warmEnforcer.getSubjectsWithPermission(THING_ROOT, READ);

    }

    @Benchmark
    public void hasUnrestrictedPermissionsMemoHit(final Blackhole bh) {
        bh.consume(warmEnforcer.hasUnrestrictedPermissions(THING_ROOT, callerContext, READ));
    }

    @Benchmark
    public void hasUnrestrictedPermissionsNoMemo(final Blackhole bh) {
        bh.consume(noMemoEnforcer.hasUnrestrictedPermissions(THING_ROOT, callerContext, READ));
    }

    @Benchmark
    public void hasUnrestrictedPermissionsMemoMiss(final Blackhole bh) {
        missIdx = (missIdx + 1) % MISS_POOL;
        bh.consume(boundedEnforcer.hasUnrestrictedPermissions(MISS_KEYS[missIdx], callerContext, READ));
    }

    /** Baseline for {@link #hasUnrestrictedPermissionsMemoMiss}: same key pool, no memo at all. */
    @Benchmark
    public void hasUnrestrictedPermissionsNoMemoSameKeys(final Blackhole bh) {
        missIdx = (missIdx + 1) % MISS_POOL;
        bh.consume(noMemoEnforcer.hasUnrestrictedPermissions(MISS_KEYS[missIdx], callerContext, READ));
    }

    @Benchmark
    public void getSubjectsWithPermissionMemoHit(final Blackhole bh) {
        bh.consume(warmEnforcer.getSubjectsWithPermission(THING_ROOT, READ));
    }

    @Benchmark
    public void getSubjectsWithPermissionNoMemo(final Blackhole bh) {
        bh.consume(noMemoEnforcer.getSubjectsWithPermission(THING_ROOT, READ));
    }

    @Benchmark
    public void formerlyEagerPerHitWork(final Blackhole bh) {
        // Mirrors what hasUnrestrictedPermissions used to do before consulting the memo.
        bh.consume(JsonFactory.newPointer(THING_ROOT.getResourceType()).append(THING_ROOT.getResourcePath()));
        bh.consume(callerContext.stream()
                .map(AuthorizationSubject::getId)
                .collect(Collectors.toSet()));
    }

    private static Policy buildPolicy(final int labels) {
        // The caller is granted unrestricted READ on thing:/ ; the filler labels grant READ on distinct
        // features, giving the tree walk realistic width without changing the caller's verdict.
        final PolicyBuilder builder = Policy.newBuilder(POLICY_ID)
                .setRevision(1L)
                .setSubjectFor("caller", subject(CALLER))
                .setGrantedPermissionsFor("caller", THING_ROOT, Permission.READ);
        for (int i = 0; i < labels; i++) {
            builder.setSubjectFor("filler-" + i, subject("user:filler-" + i))
                    .setGrantedPermissionsFor("filler-" + i,
                            ResourceKey.newInstance("thing", "/features/feature" + i), Permission.READ);
        }
        return builder.build();
    }

    private static AuthorizationContext context(final int subjects) {
        // The granted CALLER is always the primary subject; the extras have no grants and only widen the
        // subject-id set the visitor probes per tree node.
        final AuthorizationSubject[] additional = new AuthorizationSubject[subjects - 1];
        for (int i = 1; i < subjects; i++) {
            additional[i - 1] = AuthorizationSubject.newInstance("user:extra-" + i);
        }
        return AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                AuthorizationSubject.newInstance(CALLER), additional);
    }

    private static Subject subject(final String id) {
        return Subject.newInstance(SubjectId.newInstance(id), SubjectType.GENERATED);
    }

    public static void main(final String[] args) throws RunnerException, CommandLineOptionException, IOException {
        // Command-line arguments are honoured (so e.g. "-prof gc" works) but the include pattern is fixed.
        final Options opt = new OptionsBuilder()
                .parent(new CommandLineOptions(args))
                .include(AuthorizationVerdictMemoBenchmark.class.getSimpleName())
                .shouldFailOnError(true)
                .build();
        new Runner(opt).run();
    }
}
