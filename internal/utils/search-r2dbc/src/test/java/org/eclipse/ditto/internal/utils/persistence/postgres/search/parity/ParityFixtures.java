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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.mapping.EnforcedThingMapper;

/**
 * Loads the data-driven parity matrix from {@code src/test/resources/parity/}: the corpus (things + enforcing
 * policies), the RQL query battery, the named auth situations and the frozen divergence allowlist.
 *
 * <p>The corpus is turned into backend-neutral {@link ThingWriteModel}s through the REAL enforcement front —
 * {@link EnforcedThingMapper#toWriteModel} (a MAIN class of thingsearch/service, the exact production call chain
 * around {@code SearchIndexDocumentFactory}) — so both backends consume identical neutral documents, exactly like
 * the production updater stream.</p>
 */
final class ParityFixtures {

    private static final String RESOURCE_ROOT = "parity/";
    private static final long POLICY_REVISION = 1L;
    private static final int MAX_ARRAY_SIZE = -1;

    private final List<ThingWriteModel> writeModels;
    private final Map<String, List<String>> authSituations;
    private final List<QueryCase> queryCases;
    private final Map<String, String> allowlist;

    private ParityFixtures(final List<ThingWriteModel> writeModels,
            final Map<String, List<String>> authSituations,
            final List<QueryCase> queryCases,
            final Map<String, String> allowlist) {
        this.writeModels = writeModels;
        this.authSituations = authSituations;
        this.queryCases = queryCases;
        this.allowlist = allowlist;
    }

    static ParityFixtures load() {
        return new ParityFixtures(loadCorpus(), loadAuthSituations(), loadQueryCases(), loadAllowlist());
    }

    /** The corpus as neutral write models, in manifest order. */
    List<ThingWriteModel> writeModels() {
        return writeModels;
    }

    /**
     * Named auth situations in declaration order; a {@code null} value means sudo (no auth filter).
     */
    Map<String, List<String>> authSituations() {
        return authSituations;
    }

    List<QueryCase> queryCases() {
        return queryCases;
    }

    /** Allowlist entry id -> description. */
    Map<String, String> allowlist() {
        return allowlist;
    }

    private static List<ThingWriteModel> loadCorpus() {
        final JsonObject manifest = readJsonObject(RESOURCE_ROOT + "corpus.json");
        final List<ThingWriteModel> models = new ArrayList<>();
        final Map<String, Policy> policyCache = new LinkedHashMap<>();
        manifest.getValueOrThrow(org.eclipse.ditto.json.JsonFieldDefinition.ofJsonArray("things"))
                .forEach(nameValue -> {
                    final String name = nameValue.asString();
                    final JsonObject thing = readJsonObject(RESOURCE_ROOT + "things/" + name + ".json");
                    final String policyId = thing.getValue("policyId")
                            .map(JsonValue::asString)
                            .orElseThrow(() -> new IllegalStateException(
                                    "thing fixture <" + name + "> has no policyId"));
                    final Policy policy = policyCache.computeIfAbsent(policyId, ParityFixtures::loadPolicy);
                    models.add(EnforcedThingMapper.toWriteModel(thing, policy, Set.of(), POLICY_REVISION, null,
                            MAX_ARRAY_SIZE));
                });
        return List.copyOf(models);
    }

    private static Policy loadPolicy(final String policyId) {
        final String localName = policyId.substring(policyId.indexOf(':') + 1);
        return PoliciesModelFactory.newPolicy(readResource(RESOURCE_ROOT + "policies/" + localName + ".json"));
    }

    private static Map<String, List<String>> loadAuthSituations() {
        final JsonObject json = readJsonObject(RESOURCE_ROOT + "auth-subjects.json");
        final Map<String, List<String>> situations = new LinkedHashMap<>();
        json.getValueOrThrow(org.eclipse.ditto.json.JsonFieldDefinition.ofJsonObject("situations"))
                .forEach(field -> situations.put(field.getKeyName(), toSubjectsOrNull(field.getValue())));
        if (!situations.containsValue(null)) {
            throw new IllegalStateException("auth-subjects.json must declare a sudo (null) situation");
        }
        return situations;
    }

    @Nullable
    private static List<String> toSubjectsOrNull(final JsonValue value) {
        if (value.isNull()) {
            return null;
        }
        return value.asArray().stream().map(JsonValue::asString).toList();
    }

    private static List<QueryCase> loadQueryCases() {
        final JsonObject json = readJsonObject(RESOURCE_ROOT + "queries.json");
        final List<QueryCase> cases = new ArrayList<>();
        final Set<String> names = new LinkedHashSet<>();
        json.getValueOrThrow(org.eclipse.ditto.json.JsonFieldDefinition.ofJsonArray("cases"))
                .forEach(caseValue -> {
                    final QueryCase queryCase = QueryCase.fromJson(caseValue.asObject());
                    if (!names.add(queryCase.name())) {
                        throw new IllegalStateException("duplicate query case name <" + queryCase.name() + ">");
                    }
                    cases.add(queryCase);
                });
        return List.copyOf(cases);
    }

    private static Map<String, String> loadAllowlist() {
        final JsonObject json = readJsonObject(RESOURCE_ROOT + "divergence-allowlist.json");
        final Map<String, String> entries = new LinkedHashMap<>();
        json.getValueOrThrow(org.eclipse.ditto.json.JsonFieldDefinition.ofJsonArray("entries"))
                .forEach(entry -> {
                    final JsonObject entryObject = entry.asObject();
                    entries.put(entryObject.getValueOrThrow(
                                    org.eclipse.ditto.json.JsonFieldDefinition.ofString("id")),
                            entryObject.getValueOrThrow(
                                    org.eclipse.ditto.json.JsonFieldDefinition.ofString("description")));
                });
        return entries;
    }

    private static JsonObject readJsonObject(final String resourcePath) {
        return JsonFactory.newObject(readResource(resourcePath));
    }

    private static String readResource(final String resourcePath) {
        final InputStream in = ParityFixtures.class.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Missing parity fixture resource: " + resourcePath);
        }
        try (in) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
