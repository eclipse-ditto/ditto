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
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_FEATURES;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_FEATURE_ID;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_GRANTED;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_REVOKED;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.ditto.internal.utils.persistence.mongo.KeyNameReviser;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.Resources;

import akka.japi.Pair;

/**
 * Policy evaluated for a thing.
 */
final class EvaluatedPolicy {

    private static final KeyNameReviser KEY_NAME_REVISER = KeyNameReviser.escapeProblematicPlainChars();
    private static final JsonPointer FEATURE_ID_POINTER = JsonPointer.of(FIELD_FEATURE_ID);
    private static final JsonPointer FEATURES_POINTER = JsonPointer.of(FIELD_FEATURES);

    private final Map<JsonPointer, Pair<Set<String>, Set<String>>> thingPermissions;
    private final Map<String, Map<JsonPointer, Pair<Set<String>, Set<String>>>> featurePermissions;

    private EvaluatedPolicy(final Map<JsonPointer, Pair<Set<String>, Set<String>>> thingPermissions,
            final Map<String, Map<JsonPointer, Pair<Set<String>, Set<String>>>> featurePermissions) {
        this.thingPermissions = thingPermissions;
        this.featurePermissions = featurePermissions;
    }

    static EvaluatedPolicy of(final Policy policy, final JsonObject thing) {
        final Map<JsonPointer, Pair<Set<String>, Set<String>>> thingPermissions = new HashMap<>();
        final Map<String, Map<JsonPointer, Pair<Set<String>, Set<String>>>> featurePermissions = new HashMap<>();
        for (final var entry : policy) {
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

    BsonDocument forThing() {
        final var doc = new BsonDocument();
        thingPermissions.forEach((path, permissions) -> addPermissions(doc, path, permissions));
        return doc;
    }

    BsonDocument forFeature(final String featureId) {
        final var doc = new BsonDocument();
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
        return doc;
    }

    BsonArray getGlobalRead() {
        final var globalReadSubjects = thingPermissions.values()
                .stream()
                .flatMap(permissions -> permissions.first().stream())
                .collect(Collectors.toSet());
        final var array = new BsonArray();
        for (final var subject : globalReadSubjects) {
            array.add(new BsonString(subject));
        }
        return array;
    }

    private static void addPermissions(final BsonDocument doc,
            final JsonPointer path,
            final Pair<Set<String>, Set<String>> permissions) {

        final var innerDoc = new BsonDocument();
        if (!permissions.first().isEmpty()) {
            innerDoc.put(FIELD_GRANTED, toSubjectsBson(permissions.first()));
        }
        if (!permissions.second().isEmpty()) {
            innerDoc.put(FIELD_REVOKED, toSubjectsBson(permissions.second()));
        }
        setDocumentAtPath(doc, path, innerDoc);
    }

    private static void setDocumentAtPath(final BsonDocument doc, final JsonPointer path, final BsonDocument innerDoc) {
        final BsonDocument docAtPath;
        // find/add the document where to append innerDoc
        if (path.isEmpty()) {
            docAtPath = doc;
        } else {
            docAtPath = StreamSupport.stream(path.spliterator(), false)
                    .reduce(doc, (d, jsonKey) -> {
                        final String bsonKey = toBsonKey(jsonKey);
                        final BsonValue child = d.get(bsonKey);
                        if (child == null) {
                            final BsonDocument newChild = new BsonDocument();
                            d.append(bsonKey, newChild);
                            return newChild;
                        } else {
                            return child.asDocument();
                        }
                    }, EvaluatedPolicy::merge);
        }
        // append all fields of innerDoc
        innerDoc.forEach(docAtPath::append);
    }

    private static String toBsonKey(final JsonKey key) {
        return KEY_NAME_REVISER.apply(key.toString());
    }

    private static BsonArray toSubjectsBson(final Set<String> subjects) {
        final var array = new BsonArray();
        subjects.forEach(subject -> array.add(new BsonString(subject)));
        return array;
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

    private static BsonDocument merge(final BsonDocument b1, final BsonDocument b2) {
        b1.forEach(b2::append);
        return b1;
    }
}
