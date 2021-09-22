/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.policies.api.Permission.READ;
import static org.eclipse.ditto.policies.model.PoliciesResourceType.THING;

import java.util.Optional;
import java.util.stream.Stream;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonNumber;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.EffectedSubjects;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.write.IndexLengthRestrictionEnforcer;

/**
 * Flattens a Thing with an enforcer into a list of pointer-value pairs for indexing.
 */
final class EnforcedThingFlattener implements JsonObjectVisitor<Stream<BsonDocument>> {

    private static final JsonKey FEATURES_KEY =
            Thing.JsonFields.FEATURES.getPointer().getRoot().orElseThrow(() ->
                    new IllegalStateException("Impossible: Thing JSON field of features have no root!"));

    private static final JsonPointer WILDCARD_FEATURE_POINTER = JsonFactory.newPointer(FEATURES_KEY, JsonKey.of("*"));

    private final Enforcer enforcer;
    private final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer;
    private final int maxArraySize;

    EnforcedThingFlattener(final String thingId, final Enforcer enforcer, final int maxArraySize) {
        this.enforcer = enforcer;
        indexLengthRestrictionEnforcer = IndexLengthRestrictionEnforcer.newInstance(thingId);
        this.maxArraySize = maxArraySize;
    }

    static BsonArray flattenJson(final JsonObject thingJson, final Enforcer enforcer, final int maxArraySize) {
        final BsonArray bsonArray = new BsonArray();
        final String thingId = thingJson.getValueOrThrow(Thing.JsonFields.ID);
        new EnforcedThingFlattener(thingId, enforcer, maxArraySize).eval(thingJson)
                .forEach(bsonArray::add);
        return bsonArray;
    }

    @Override
    public Stream<BsonDocument> nullValue(final JsonPointer key) {
        return singleton(key, JsonValue.nullLiteral());
    }

    @Override
    public Stream<BsonDocument> bool(final JsonPointer key, final boolean value) {
        return singleton(key, JsonValue.of(value));
    }

    @Override
    public Stream<BsonDocument> string(final JsonPointer key, final String value) {
        return singleton(key, JsonValue.of(value));
    }

    @Override
    public Stream<BsonDocument> number(final JsonPointer key, final JsonNumber value) {
        return singleton(key, value);
    }

    @Override
    public Stream<BsonDocument> array(final JsonPointer key, final Stream<Stream<BsonDocument>> values) {
        // step 1: flatten flattened value from array elements
        return values.reduce(Stream::concat)
                // step 2: limit the number of flattened elements
                .map(s -> maxArraySize < 0 ? s : s.limit(maxArraySize))
                // step 3: distinguish between empty and non-empty streams no matter what the cause
                .flatMap(s -> s.map(Stream::of).reduce(Stream::concat))
                .orElseGet(() -> singleton(key, JsonObject.empty()));
    }

    @Override
    public Stream<BsonDocument> object(final JsonPointer key, final Stream<Stream<BsonDocument>> values) {
        return values
                .reduce(Stream::concat)
                .orElseGet(() -> singleton(key, JsonObject.empty()));
    }

    private Stream<BsonDocument> singleton(final JsonPointer key, final JsonValue jsonValue) {
        final Optional<JsonValue> fixedJsonValue = indexLengthRestrictionEnforcer.enforce(key, jsonValue);
        if (fixedJsonValue.isPresent()) {
            final BsonValue bsonValue = JsonToBson.convert(fixedJsonValue.get());
            final EffectedSubjects subjects = computeEffectedSubjectIds(key);
            final BsonArray grants = toBsonArray(subjects.getGranted());
            final BsonArray revokes = toBsonArray(subjects.getRevoked());
            final BsonDocument document = assembleBsonDocument(key, bsonValue, grants, revokes);
            return replaceFeatureIdByWildcard(key)
                    .map(replacedKey -> Stream.of(document,
                            assembleBsonDocument(replacedKey, bsonValue, grants, revokes)))
                    .orElse(Stream.of(document));
        } else {
            // Impossible to restrict length of this key-value pair; do not index it.
            return Stream.empty();
        }
    }

    private EffectedSubjects computeEffectedSubjectIds(final JsonPointer key) {
        final ResourceKey resourceKey = ResourceKey.newInstance(THING, key.toString());
        return enforcer.getSubjectsWithPermission(resourceKey, READ);
    }

    private static BsonArray toBsonArray(final Iterable<AuthorizationSubject> authorizationSubjects) {
        final BsonArray bsonArray = new BsonArray();
        authorizationSubjects.forEach(subject -> bsonArray.add(new BsonString(subject.getId())));
        return bsonArray;
    }

    private static BsonDocument assembleBsonDocument(final CharSequence key, final BsonValue value,
            final BsonArray grants, final BsonArray revokes) {

        return new BsonDocument().append(PersistenceConstants.FIELD_INTERNAL_KEY, new BsonString(key.toString()))
                .append(PersistenceConstants.FIELD_INTERNAL_VALUE, value)
                .append(PersistenceConstants.FIELD_GRANTED, grants)
                .append(PersistenceConstants.FIELD_REVOKED, revokes);
    }

    private static Optional<CharSequence> replaceFeatureIdByWildcard(final JsonPointer key) {
        return key.getRoot()
                .filter(FEATURES_KEY::equals)
                .flatMap(features -> key.getSubPointer(2)) // skip 'features' and <featureId>
                .map(WILDCARD_FEATURE_POINTER::append);
    }

}
