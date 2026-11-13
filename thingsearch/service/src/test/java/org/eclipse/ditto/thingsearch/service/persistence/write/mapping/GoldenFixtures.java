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
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Shared fixture corpus for the {@link EnforcedThingMapper} golden-file baseline. Used by both the
 * (manually-run) golden-file generator and {@code EnforcedThingMapperGoldenTest}, so that generation and
 * verification exercise the exact same fixture inputs.
 *
 * <p>Each case lives under {@code src/test/resources/golden/<case>/} as:
 * <ul>
 *     <li>{@code thing.json} - the Thing JSON, in the shape read off the Things persistence
 *     (including special fields like {@code thingId}, {@code _namespace}, {@code _revision}).</li>
 *     <li>{@code policy.json} - the enforcing Policy JSON.</li>
 *     <li>{@code params.json} - <em>optional</em>; overrides for {@code policyRevision} (default {@code 1}),
 *     {@code maxArraySize} (default {@code -1}, i.e. unlimited), {@code referencedPolicies} and
 *     {@code oldReferencedPolicies} (both default to an empty list). All parameters are fixed constants -
 *     no wall-clock or random input is ever used, so re-running the generator is fully deterministic.</li>
 * </ul>
 */
final class GoldenFixtures {

    /**
     * The canonical, deterministic BSON-as-JSON rendering shared by the generator
     * ({@link EnforcedThingMapperGoldenGenerator}) and the regression test ({@link EnforcedThingMapperGoldenTest})
     * so that generation and verification can never drift apart on formatting.
     */
    static final JsonWriterSettings CANONICAL_EXTENDED_JSON = JsonWriterSettings.builder()
            .outputMode(JsonMode.EXTENDED)
            .indent(true)
            .build();

    /**
     * The full coverage-matrix case corpus (see {@code task-a1-brief.md} for the matrix this satisfies).
     */
    static final List<String> CASE_NAMES = List.of(
            "nested-scalars",
            "arrays-mixed",
            "features-multi",
            "special-chars-keys",
            "unicode-keys-values",
            "index-length-restriction",
            "auth-grant-revoke-multi-level",
            "auth-revoke-below-grant",
            "auth-feature-level-grants",
            "auth-multiple-subjects",
            "imported-referenced-policies",
            "auth-namespace-scoped-entries",
            "thing-without-policy"
    );

    private GoldenFixtures() {
        throw new AssertionError();
    }

    static JsonObject loadThing(final String caseName) {
        return JsonFactory.newObject(readResource(caseName, "thing.json"));
    }

    static Policy loadPolicy(final String caseName) {
        return PoliciesModelFactory.newPolicy(readResource(caseName, "policy.json"));
    }

    static Params loadParams(final String caseName) {
        final String resourcePath = "golden/" + caseName + "/params.json";
        final InputStream in = GoldenFixtures.class.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            return Params.defaults();
        }
        final JsonObject json = JsonFactory.newObject(readAll(in));
        final long policyRevision = json.getValue("policyRevision").map(JsonValue::asLong).orElse(1L);
        final int maxArraySize = json.getValue("maxArraySize").map(JsonValue::asInt).orElse(-1);
        final List<PolicyTag> referencedPolicies = readPolicyTags(json, "referencedPolicies");
        final List<PolicyTag> oldReferencedPolicies = readPolicyTags(json, "oldReferencedPolicies");
        return new Params(policyRevision, maxArraySize, referencedPolicies, oldReferencedPolicies);
    }

    private static List<PolicyTag> readPolicyTags(final JsonObject json, final String field) {
        final List<PolicyTag> tags = new ArrayList<>();
        json.getValue(field)
                .map(JsonValue::asArray)
                .ifPresent(array -> array.forEach(value -> {
                    final JsonObject tagJson = value.asObject();
                    final PolicyId id = PolicyId.of(tagJson.getValue("id").orElseThrow().asString());
                    final long revision = tagJson.getValue("revision").orElseThrow().asLong();
                    tags.add(PolicyTag.of(id, revision));
                }));
        return tags;
    }

    private static String readResource(final String caseName, final String fileName) {
        final String resourcePath = "golden/" + caseName + "/" + fileName;
        final InputStream in = GoldenFixtures.class.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Missing golden fixture resource: " + resourcePath);
        }
        return readAll(in);
    }

    private static String readAll(final InputStream in) {
        try (in) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Parameters for a single golden-file case, all fixed constants (never derived from wall-clock time).
     *
     * @param policyRevision the policy revision passed to {@code EnforcedThingMapper.toWriteModel}.
     * @param maxArraySize the max-array-size passed to {@code EnforcedThingMapper.toWriteModel}.
     * @param referencedPolicies the "new" referenced-policy tags (mirrors what the enforcement flow resolves
     * from the policy cache).
     * @param oldReferencedPolicies referenced-policy tags of the <em>old</em> {@code Metadata}, used to
     * exercise the "deleted but still imported" policy retention in
     * {@code EnforcedThingMapper.toWriteModel}. Empty unless the case builds an old {@code Metadata}.
     */
    record Params(long policyRevision, int maxArraySize, List<PolicyTag> referencedPolicies,
            List<PolicyTag> oldReferencedPolicies) {

        static Params defaults() {
            return new Params(1L, -1, List.of(), List.of());
        }

        Params {
            Objects.requireNonNull(referencedPolicies);
            Objects.requireNonNull(oldReferencedPolicies);
        }
    }
}
