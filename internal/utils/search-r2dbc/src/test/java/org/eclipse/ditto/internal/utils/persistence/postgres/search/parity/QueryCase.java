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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.parity;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * One entry of the data-driven parity battery ({@code parity/queries.json}). The engine
 * ({@code SearchBackendParityIT}) executes each case against BOTH backends and compares the results.
 *
 * @param name unique case name (used in assertion messages).
 * @param filter the RQL filter string, or {@code null} for the match-all query.
 * @param options RQL option strings (e.g. {@code sort(+attributes/rank,+thingId)}, {@code size(2)}) applied via the
 * real {@code RqlOptionParser} + {@code ParameterOptionVisitor} front door.
 * @param namespaces namespaces restriction applied via {@code filterCriteriaRestrictedByNamespaces}, or {@code null}.
 * @param mode {@code find}, {@code count} or {@code cursorWalk}.
 * @param authKeys the auth situations to run under; empty = ALL situations from {@code parity/auth-subjects.json}.
 * @param compareAsSet when {@code true}, compare result IDs as sets (used with per-backend order pins for
 * allow-listed order divergences); default is strict ordered comparison.
 * @param divergenceId the divergence-allowlist entry this case exercises, or {@code null} for a strict-parity case.
 * @param expectedMongo per-auth-situation pinned Mongo result (required for allow-listed result divergences).
 * @param expectedPostgres per-auth-situation pinned Postgres result (required for allow-listed result divergences).
 * @param expectedCountMongo per-auth-situation pinned Mongo count (for allow-listed COUNT divergences).
 * @param expectedCountPostgres per-auth-situation pinned Postgres count (for allow-listed COUNT divergences).
 * @param expectPostgresRejection when {@code true}, Postgres must REJECT the case with an
 * {@code IllegalArgumentException} while Mongo answers it (the wildcard-sort allowlist entry).
 * @param expectNonEmptySudo when {@code true}, the sudo run must return at least one thing on both backends
 * (anti-vacuousness guard).
 * @param expectNonEmptyAuth an auth-situation key under which the run must return at least one thing
 * (anti-vacuousness guard for cases that exclude sudo), or {@code null}.
 */
record QueryCase(String name,
        @Nullable String filter,
        List<String> options,
        @Nullable Set<String> namespaces,
        String mode,
        List<String> authKeys,
        boolean compareAsSet,
        @Nullable String divergenceId,
        Map<String, List<String>> expectedMongo,
        Map<String, List<String>> expectedPostgres,
        Map<String, Long> expectedCountMongo,
        Map<String, Long> expectedCountPostgres,
        boolean expectPostgresRejection,
        boolean expectNonEmptySudo,
        @Nullable String expectNonEmptyAuth) {

    static final String MODE_FIND = "find";
    static final String MODE_COUNT = "count";
    static final String MODE_CURSOR_WALK = "cursorWalk";

    static QueryCase fromJson(final JsonObject json) {
        final String name = json.getValueOrThrow(org.eclipse.ditto.json.JsonFieldDefinition.ofString("name"));
        final String filter = json.getValue("filter").filter(v -> !v.isNull()).map(JsonValue::asString).orElse(null);
        final List<String> options = json.getValue("options")
                .map(JsonValue::asArray)
                .map(array -> array.stream().map(JsonValue::asString).toList())
                .orElse(List.of());
        final Set<String> namespaces = json.getValue("namespaces")
                .map(JsonValue::asArray)
                .map(array -> {
                    final Set<String> result = new LinkedHashSet<String>();
                    array.forEach(v -> result.add(v.asString()));
                    return (Set<String>) result;
                })
                .orElse(null);
        final String mode = json.getValue("mode").map(JsonValue::asString).orElse(MODE_FIND);
        if (!MODE_FIND.equals(mode) && !MODE_COUNT.equals(mode) && !MODE_CURSOR_WALK.equals(mode)) {
            throw new IllegalArgumentException("Unknown mode <" + mode + "> in query case <" + name + ">");
        }
        final List<String> authKeys = json.getValue("auth")
                .map(JsonValue::asArray)
                .map(array -> array.stream().map(JsonValue::asString).toList())
                .orElse(List.of());
        final boolean compareAsSet = "set".equals(json.getValue("compare").map(JsonValue::asString).orElse("ordered"));
        final String divergenceId =
                json.getValue("divergence").filter(v -> !v.isNull()).map(JsonValue::asString).orElse(null);
        final JsonObject expected = json.getValue("expected")
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonObject.empty());
        final Map<String, List<String>> expectedMongo = readExpected(expected, "mongo");
        final Map<String, List<String>> expectedPostgres = readExpected(expected, "postgres");
        final JsonObject expectedCount = json.getValue("expectedCount")
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonObject.empty());
        final Map<String, Long> expectedCountMongo = readExpectedCount(expectedCount, "mongo");
        final Map<String, Long> expectedCountPostgres = readExpectedCount(expectedCount, "postgres");
        final boolean expectPostgresRejection =
                json.getValue("expectPostgresRejection").map(JsonValue::asBoolean).orElse(false);
        final boolean expectNonEmptySudo =
                json.getValue("expectNonEmptySudo").map(JsonValue::asBoolean).orElse(false);
        final String expectNonEmptyAuth =
                json.getValue("expectNonEmptyAuth").filter(v -> !v.isNull()).map(JsonValue::asString).orElse(null);
        return new QueryCase(name, filter, options, namespaces, mode, authKeys, compareAsSet, divergenceId,
                expectedMongo, expectedPostgres, expectedCountMongo, expectedCountPostgres, expectPostgresRejection,
                expectNonEmptySudo, expectNonEmptyAuth);
    }

    /** True when the case pins ANY per-backend behavior (find results, counts or a rejection). */
    boolean pinsDivergentBehavior() {
        return !expectedMongo.isEmpty() || !expectedPostgres.isEmpty()
                || !expectedCountMongo.isEmpty() || !expectedCountPostgres.isEmpty()
                || expectPostgresRejection;
    }

    private static Map<String, List<String>> readExpected(final JsonObject expected, final String backend) {
        final Map<String, List<String>> result = new LinkedHashMap<>();
        expected.getValue(backend)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .ifPresent(perAuth -> perAuth.forEach(field -> result.put(field.getKeyName(),
                        field.getValue().asArray().stream().map(JsonValue::asString).toList())));
        return result;
    }

    private static Map<String, Long> readExpectedCount(final JsonObject expectedCount, final String backend) {
        final Map<String, Long> result = new LinkedHashMap<>();
        expectedCount.getValue(backend)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .ifPresent(perAuth -> perAuth.forEach(field ->
                        result.put(field.getKeyName(), field.getValue().asLong())));
        return result;
    }
}
