/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.persistence.api.mapping;

import static org.eclipse.ditto.thingsearch.persistence.api.mapping.SearchIndexFields.FIELD_FEATURES;
import static org.eclipse.ditto.thingsearch.persistence.api.mapping.SearchIndexFields.FIELD_FEATURE_ID;
import static org.eclipse.ditto.thingsearch.persistence.api.mapping.SearchIndexFields.FIELD_GRANTED;
import static org.eclipse.ditto.thingsearch.persistence.api.mapping.SearchIndexFields.FIELD_REVOKED;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.pekko.japi.Pair;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.Resources;

/**
 * Policy evaluated for a thing.
 * <p>
 * Backend-neutral: the auth-tree projections are emitted as {@link JsonObject}s with <em>raw</em> keys and the
 * global-read set as a {@link List} of subject IDs. Backend-specific key encoding (such as the Mongo
 * {@code KeyNameReviser} escaping of {@code "."}/{@code "$"}) is applied by the backend encoder when
 * serializing these neutral structures - it is intentionally NOT applied here.
 */
final class EvaluatedPolicy {

    private static final JsonPointer FEATURE_ID_POINTER = JsonPointer.of(FIELD_FEATURE_ID);
    private static final JsonPointer FEATURES_POINTER = JsonPointer.of(FIELD_FEATURES);

    private final Map<JsonPointer, Pair<Set<String>, Set<String>>> thingPermissions;
    private final Map<String, Map<JsonPointer, Pair<Set<String>, Set<String>>>> featurePermissions;

    private EvaluatedPolicy(final Map<JsonPointer, Pair<Set<String>, Set<String>>> thingPermissions,
            final Map<String, Map<JsonPointer, Pair<Set<String>, Set<String>>>> featurePermissions) {
        this.thingPermissions = thingPermissions;
        this.featurePermissions = featurePermissions;
    }

    static EvaluatedPolicy of(final Policy policy, final JsonObject thing, final String thingNamespace) {
        final Map<JsonPointer, Pair<Set<String>, Set<String>>> thingPermissions = new HashMap<>();
        final Map<String, Map<JsonPointer, Pair<Set<String>, Set<String>>>> featurePermissions = new HashMap<>();
        for (final var entry : policy) {
            if (!entry.appliesToNamespace(thingNamespace)) {
                continue;
            }
            final Set<String> subjects = getSubjects(entry);
            final Map<JsonPointer, Boolean> paths = getPaths(entry.getResources());
            paths.forEach((path, isGrant) -> {
                if (thing.contains(path) || path.isEmpty()) {
                    addPathToPermissions(thingPermissions, path, isGrant, subjects);
                    addPathToFeaturePermissions(featurePermissions, path, isGrant, subjects);
                }
            });
        }
        return new EvaluatedPolicy(thingPermissions, featurePermissions);
    }

    JsonObject forThing() {
        final Map<String, Object> doc = new LinkedHashMap<>();
        thingPermissions.forEach((path, permissions) -> addPermissions(doc, path, permissions));
        return toJsonObject(doc);
    }

    JsonObject forFeature(final String featureId) {
        final Map<String, Object> doc = new LinkedHashMap<>();
        if (thingPermissions.containsKey(JsonPointer.empty())) {
            addPermissions(doc, JsonPointer.empty(), thingPermissions.get(JsonPointer.empty()));
        }
        if (thingPermissions.containsKey(FEATURES_POINTER)) {
            addPermissions(doc, FEATURES_POINTER, thingPermissions.get(FEATURES_POINTER));
        }
        if (featurePermissions.containsKey(featureId)) {
            featurePermissions.get(featureId)
                    .forEach((path, permissions) -> addPermissions(doc, path, permissions));
        }
        return toJsonObject(doc);
    }

    List<String> getGlobalRead() {
        final var globalReadSubjects = thingPermissions.values()
                .stream()
                .flatMap(permissions -> permissions.first().stream())
                .collect(Collectors.toSet());
        return new ArrayList<>(globalReadSubjects);
    }

    private static void addPermissions(final Map<String, Object> doc,
            final JsonPointer path,
            final Pair<Set<String>, Set<String>> permissions) {

        final Map<String, Object> innerDoc = new LinkedHashMap<>();
        if (!permissions.first().isEmpty()) {
            innerDoc.put(FIELD_GRANTED, new ArrayList<>(permissions.first()));
        }
        if (!permissions.second().isEmpty()) {
            innerDoc.put(FIELD_REVOKED, new ArrayList<>(permissions.second()));
        }
        setDocumentAtPath(doc, path, innerDoc);
    }

    @SuppressWarnings("unchecked")
    private static void setDocumentAtPath(final Map<String, Object> doc, final JsonPointer path,
            final Map<String, Object> innerDoc) {

        // find/add the document where to append innerDoc; keys are RAW (the encoder applies key escaping later)
        Map<String, Object> docAtPath = doc;
        if (!path.isEmpty()) {
            for (final JsonKey jsonKey : path) {
                final String key = jsonKey.toString();
                final Object child = docAtPath.get(key);
                if (child == null) {
                    final Map<String, Object> newChild = new LinkedHashMap<>();
                    docAtPath.put(key, newChild);
                    docAtPath = newChild;
                } else {
                    docAtPath = (Map<String, Object>) child;
                }
            }
        }
        // append all fields of innerDoc
        docAtPath.putAll(innerDoc);
    }

    private static JsonObject toJsonObject(final Map<String, Object> map) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        map.forEach((key, value) -> builder.set(JsonField.newInstance(key, toJsonValue(value))));
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static JsonValue toJsonValue(final Object value) {
        if (value instanceof Map) {
            return toJsonObject((Map<String, Object>) value);
        } else {
            return ((List<String>) value).stream()
                    .map(JsonValue::of)
                    .collect(JsonCollectors.valuesToArray());
        }
    }

    private static Set<String> getSubjects(final PolicyEntry entry) {
        return entry.getSubjects()
                .stream()
                .map(subject -> subject.getId().toString())
                .collect(Collectors.toSet());
    }

    private static Map<JsonPointer, Boolean> getPaths(final Resources resources) {
        final Map<JsonPointer, Boolean> map = new HashMap<>();
        resources.stream()
                .filter(resource -> PoliciesResourceType.THING.equals(resource.getResourceKey().getResourceType()))
                .forEach(resource -> {
                    final var permissions = resource.getEffectedPermissions();
                    if (permissions.getRevokedPermissions().contains(Permission.READ)) {
                        map.put(resource.getPath(), false);
                    } else if (permissions.getGrantedPermissions().contains(Permission.READ)) {
                        map.put(resource.getPath(), true);
                    }
                });
        return map;
    }

    private static void addPathToPermissions(
            final Map<JsonPointer, Pair<Set<String>, Set<String>>> thingPermissions,
            final JsonPointer path,
            final boolean isGrant,
            final Set<String> subjects) {

        final Pair<Set<String>, Set<String>> subjectsAtPath =
                thingPermissions.computeIfAbsent(path, k -> Pair.create(new HashSet<>(), new HashSet<>()));

        if (isGrant) {
            final var revokedSubjects = subjectsAtPath.second();
            final var notRevokedSubjects = subjects.stream()
                    .filter(subject -> !revokedSubjects.contains(subject))
                    .toList();
            subjectsAtPath.first().addAll(notRevokedSubjects);
        } else {
            subjectsAtPath.first().removeAll(subjects);
            subjectsAtPath.second().addAll(subjects);
        }
    }

    private static void addPathToFeaturePermissions(
            final Map<String, Map<JsonPointer, Pair<Set<String>, Set<String>>>> featurePermissions,
            final JsonPointer path,
            final boolean isGrant,
            final Set<String> subjects) {

        final var isFeaturesPath = path.getRoot().filter(key -> FIELD_FEATURES.equals(key.toString())).isPresent();
        final var featureIdOptional = path.get(1);
        if (isFeaturesPath && featureIdOptional.isPresent()) {
            final var featureId = featureIdOptional.get().toString();
            final var map = featurePermissions.computeIfAbsent(featureId, k -> new HashMap<>());
            final var innerPointer = path.getSubPointer(2).filter(pointer -> !pointer.isEmpty());
            final var featureLevelPointer = innerPointer.orElse(FEATURE_ID_POINTER);
            addPathToPermissions(map, featureLevelPointer, isGrant, subjects);
        }
    }

}
