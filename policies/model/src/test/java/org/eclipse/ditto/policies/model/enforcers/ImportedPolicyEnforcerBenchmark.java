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
package org.eclipse.ditto.policies.model.enforcers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImporter;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.SubjectId;
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
import org.openjdk.jmh.annotations.Warmup;

/**
 * Builds policy enforcers for thing policies with a realistic import graph, the way the enforcer cache loader does:
 * resolve the imports (incl. entry references and transitive imports), merge the implicit entries of the namespace
 * root policies and build the default evaluator.
 * <p>
 * The import graph comes from {@code policy-import-benchmark/policies.json}, an anonymized snapshot: thing policies
 * have no own entries, but import shared policies and per-group policies (created from templates for two levels of
 * groups) whose entries reference entries of other imported policies. A resolved thing policy has several dozen
 * entries with several hundred resources.
 * </p>
 * <ul>
 * <li>JMH ({@code -prof gc} for allocations): time to build one enforcer, and time of typical permission checks
 * which walk the policy tree (memoization disabled).</li>
 * <li>{@link #main(String[])}: heap retained by the resolved policies and enforcers of {@code n} thing policies,
 * i.e. what the enforcer cache holds.</li>
 * </ul>
 * The system property {@code ditto.devops.feature.policy-enforcement-use-throughput-optimized-evaluator-enabled}
 * selects the evaluator, e.g. {@code false} for the memory-optimized one.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class ImportedPolicyEnforcerBenchmark {

    private static final String FIXTURE = "/policy-import-benchmark/policies.json";
    private static final int THINGS_PER_GROUP_B = 5;
    private static final int GROUPS_B_PER_GROUP_A = 4;

    @Param({"1000"})
    public int things;

    private static final ResourceKey THING_ROOT = ResourceKey.newInstance("thing", JsonPointer.empty());
    private static final Permissions READ = Permissions.newInstance("READ");
    private static final int CHECKED_ENFORCERS = 200;

    private Fixture fixture;
    private int next;
    private final List<Enforcer> checkedEnforcers = new ArrayList<>();
    private final List<AuthorizationContext> checkedContexts = new ArrayList<>();
    private int nextCheck;

    @Setup
    public void setUp() throws IOException {
        fixture = Fixture.load(things);
        next = 0;
        nextCheck = 0;
        for (int i = 0; i < Math.min(CHECKED_ENFORCERS, fixture.thingPolicies.size()); i++) {
            final Policy resolved = fixture.build(fixture.thingPolicies.get(i)).policy();
            // memoization disabled: every check walks the policy tree
            checkedEnforcers.add(PolicyEnforcers.defaultEvaluator(resolved, 0));
            final SubjectId subjectId = resolved.getEntriesSet().iterator().next().getSubjects().iterator().next()
                    .getId();
            checkedContexts.add(AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationModelFactory.newAuthSubject(subjectId)));
        }
    }

    @Benchmark
    public BuiltEnforcer resolveImportsAndBuildEnforcer() {
        final Policy thingPolicy = fixture.thingPolicies.get(next);
        next = (next + 1) % fixture.thingPolicies.size();
        return fixture.build(thingPolicy);
    }

    @Benchmark
    public int checkPermissions() {
        final Enforcer enforcer = checkedEnforcers.get(nextCheck);
        final AuthorizationContext authorizationContext = checkedContexts.get(nextCheck);
        nextCheck = (nextCheck + 1) % checkedEnforcers.size();
        final boolean unrestricted = enforcer.hasUnrestrictedPermissions(THING_ROOT, authorizationContext, READ);
        return enforcer.getSubjectsWithPartialPermission(THING_ROOT, READ).size() + (unrestricted ? 1 : 0);
    }

    /**
     * Measures the heap retained by the resolved policies and enforcers of {@code n} distinct thing policies.
     *
     * @param args optionally the number of thing policies, default 5000.
     * @throws IOException if the fixture cannot be read.
     */
    public static void main(final String[] args) throws IOException {
        final int n = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        final Fixture fixture = Fixture.load(n);
        final BuiltEnforcer sample = fixture.build(fixture.thingPolicies.get(0));
        final List<BuiltEnforcer> retained = new ArrayList<>(n);

        final long before = usedHeapAfterGc();
        for (final Policy thingPolicy : fixture.thingPolicies) {
            retained.add(fixture.build(thingPolicy));
        }
        final long after = usedHeapAfterGc();

        System.out.printf("thing policies: %d, effective entries per resolved policy: %d, resources: %d%n", n,
                sample.policy().getEntriesSet().size(),
                sample.policy().getEntriesSet().stream().mapToInt(e -> e.getResources().getSize()).sum());
        System.out.printf("retained heap: %.1f MB total, %.1f KB per cached enforcer%n",
                (after - before) / 1e6, (after - before) / 1024.0 / n);
        // keeps the enforcers strongly reachable until after the measurement
        System.out.println("(retained " + retained.size() + " enforcers)");
    }

    private static long usedHeapAfterGc() {
        for (int i = 0; i < 4; i++) {
            System.gc();
            try {
                Thread.sleep(200);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    /**
     * What the enforcer cache keeps per policy: the resolved policy and its enforcer.
     */
    public static final class BuiltEnforcer {

        private final Policy policy;
        private final Enforcer enforcer;

        private BuiltEnforcer(final Policy policy, final Enforcer enforcer) {
            this.policy = policy;
            this.enforcer = enforcer;
        }

        /**
         * @return the policy with resolved imports and merged namespace root entries.
         */
        public Policy policy() {
            return policy;
        }

        /**
         * @return the enforcer built from the policy.
         */
        public Enforcer enforcer() {
            return enforcer;
        }
    }

    private static final class Fixture {

        private final Map<PolicyId, Policy> policies = new HashMap<>();
        private final Map<String, List<PolicyId>> namespaceRootPolicies = new HashMap<>();
        private final List<Policy> thingPolicies = new ArrayList<>();

        static Fixture load(final int things) throws IOException {
            final JsonObject json;
            try (final InputStream in = ImportedPolicyEnforcerBenchmark.class.getResourceAsStream(FIXTURE)) {
                if (in == null) {
                    throw new IOException("Missing fixture " + FIXTURE);
                }
                final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                final byte[] buffer = new byte[8192];
                for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                    bytes.write(buffer, 0, read);
                }
                json = JsonFactory.readFrom(new String(bytes.toByteArray(), StandardCharsets.UTF_8)).asObject();
            }
            final Fixture fixture = new Fixture();
            final String root = json.getValueOrThrow(JsonFactory.newStringFieldDefinition("rootNamespace"));
            final String thingNamespace =
                    json.getValueOrThrow(JsonFactory.newStringFieldDefinition("thingNamespace"));
            json.getValueOrThrow(JsonFactory.newJsonObjectFieldDefinition("namespaceRootPolicies"))
                    .forEach(field -> fixture.namespaceRootPolicies.put(field.getKeyName(),
                            field.getValue().asArray().stream()
                                    .map(v -> PolicyId.of(v.asString()))
                                    .collect(Collectors.toList())));
            json.getValueOrThrow(JsonFactory.newJsonArrayFieldDefinition("policies"))
                    .forEach(policy -> fixture.add(policy.asObject()));

            final JsonObject templates =
                    json.getValueOrThrow(JsonFactory.newJsonObjectFieldDefinition("templates"));
            final String thing = template(templates, "thing");
            final String groupA = template(templates, "groupA");
            final String groupA2 = template(templates, "groupA2");
            final String groupB = template(templates, "groupB");

            final int groupsB = (things + THINGS_PER_GROUP_B - 1) / THINGS_PER_GROUP_B;
            final int groupsA = (groupsB + GROUPS_B_PER_GROUP_A - 1) / GROUPS_B_PER_GROUP_A;
            for (int a = 0; a < groupsA; a++) {
                final String groupAId = "group-a-" + a;
                fixture.add(withSubjects(render(groupA, root, groupAId, "", ""), "subject:" + groupAId + "-", 2));
                fixture.add(withSubjects(render(groupA2, root, groupAId, "", ""), "subject:" + groupAId + "-2-", 2));
            }
            for (int b = 0; b < groupsB; b++) {
                final String groupBId = "group-b-" + b;
                fixture.add(withSubjects(render(groupB, root, "", groupBId, ""), "subject:" + groupBId + "-", 1));
            }
            for (int t = 0; t < things; t++) {
                final int groupBIndex = t / THINGS_PER_GROUP_B;
                final String thingPolicy = render(thing, root, "group-a-" + (groupBIndex / GROUPS_B_PER_GROUP_A),
                        "group-b-" + groupBIndex, thingNamespace + ":thing-" + t);
                fixture.thingPolicies.add(PoliciesModelFactory.newPolicy(thingPolicy));
            }
            return fixture;
        }

        private static String template(final JsonObject templates, final String name) {
            return templates.getValueOrThrow(JsonFactory.newJsonObjectFieldDefinition(name)).toString();
        }

        private static String render(final String template, final String root, final String groupAId,
                final String groupBId, final String thingId) {
            return template.replace("{{ROOT}}", root)
                    .replace("{{GROUP_A_ID}}", groupAId)
                    .replace("{{GROUP_B_ID}}", groupBId)
                    .replace("{{THING_ID}}", thingId);
        }

        // the per-group templates leave the subjects of their group specific entries empty
        private static JsonObject withSubjects(final String policyJson, final String subjectPrefix, final int count) {
            final JsonObject policy = JsonFactory.readFrom(policyJson).asObject();
            final JsonObjectBuilder entries = JsonFactory.newObjectBuilder();
            for (final JsonField entry : policy.getValueOrThrow(Policy.JsonFields.ENTRIES)) {
                JsonObject entryJson = entry.getValue().asObject();
                if (entryJson.getValue("subjects").filter(JsonValue::isObject).map(JsonValue::asObject)
                        .filter(JsonObject::isEmpty).isPresent()) {
                    final JsonObjectBuilder subjects = JsonFactory.newObjectBuilder();
                    for (int i = 1; i <= count; i++) {
                        subjects.set(subjectPrefix + i, JsonFactory.newObjectBuilder().set("type", "generated").build());
                    }
                    entryJson = entryJson.setValue("subjects", subjects.build());
                }
                entries.set(entry.getKey(), entryJson);
            }
            return policy.set(Policy.JsonFields.ENTRIES, entries.build());
        }

        private void add(final JsonObject policyJson) {
            final Policy policy = PoliciesModelFactory.newPolicy(policyJson);
            policies.put(policy.getEntityId().get(), policy);
        }

        private CompletionStage<Optional<Policy>> resolve(final PolicyId policyId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(policies.get(policyId)));
        }

        BuiltEnforcer build(final Policy policy) {
            Policy resolved = policy.withResolvedImports(this::resolve).toCompletableFuture().join();
            final String namespace = policy.getNamespace().orElse("");
            for (final Map.Entry<String, List<PolicyId>> pattern : namespaceRootPolicies.entrySet()) {
                if (matches(pattern.getKey(), namespace)) {
                    for (final PolicyId rootPolicyId : pattern.getValue()) {
                        final Policy rootPolicy = policies.get(rootPolicyId).withResolvedImports(this::resolve)
                                .toCompletableFuture().join();
                        resolved = PolicyImporter.mergeImplicitNamespaceRootEntries(rootPolicy, rootPolicyId,
                                resolved);
                    }
                }
            }
            return new BuiltEnforcer(resolved, PolicyEnforcers.defaultEvaluator(resolved));
        }

        private static boolean matches(final String pattern, final String namespace) {
            if ("*".equals(pattern)) {
                return true;
            }
            if (pattern.endsWith(".*")) {
                return namespace.startsWith(pattern.substring(0, pattern.length() - 1));
            }
            return pattern.equals(namespace);
        }
    }

}
