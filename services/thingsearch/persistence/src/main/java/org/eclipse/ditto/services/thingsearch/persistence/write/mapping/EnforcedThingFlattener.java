/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.write.mapping;

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_GRANTED;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_KEY;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_VALUE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_REVOKED;
import static org.eclipse.ditto.model.policies.PoliciesResourceType.THING;
import static org.eclipse.ditto.services.models.policies.Permission.READ;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import org.bson.BsonArray;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonNumber;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.enforcers.EffectedSubjectIds;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.persistence.mongo.BsonUtil;

import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;

/**
 * Flattens a Thing with an enforcer into a list of pointer-value pairs for indexing.
 */
final class EnforcedThingFlattener implements JsonObjectVisitor<Stream<Document>> {

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
                .forEach(doc -> bsonArray.add(BsonUtil.toBsonDocument(doc)));
        return bsonArray;
    }

    @Override
    public Stream<Document> nullValue(final JsonPointer key) {
        return singleton(key, JsonValue.nullLiteral());
    }

    @Override
    public Stream<Document> bool(final JsonPointer key, final boolean value) {
        return singleton(key, JsonValue.of(value));
    }

    @Override
    public Stream<Document> string(final JsonPointer key, final String value) {
        return singleton(key, JsonValue.of(value));
    }

    @Override
    public Stream<Document> number(final JsonPointer key, final JsonNumber value) {
        return singleton(key, value);
    }

    @Override
    public Stream<Document> array(final JsonPointer key, final Stream<Stream<Document>> values) {
        return values
                .reduce(Stream::concat)
                .map(s -> maxArraySize < 0 ? s : s.limit(maxArraySize))
                .orElseGet(() -> singleton(key, JsonArray.empty()));
    }

    @Override
    public Stream<Document> object(final JsonPointer key, final Stream<Stream<Document>> values) {
        return values
                .reduce(Stream::concat)
                .orElseGet(() -> singleton(key, JsonObject.empty()));
    }

    private Stream<Document> singleton(final JsonPointer key, final JsonValue jsonValue) {
        final Optional<JsonValue> fixedJsonValue = indexLengthRestrictionEnforcer.enforce(key, jsonValue);
        if (fixedJsonValue.isPresent()) {
            final BsonValue bsonValue = JsonToBson.convert(fixedJsonValue.get());
            final EffectedSubjectIds subjectIds = computeEffectedSubjectIds(key);
            final BsonArray grants = toBsonArray(subjectIds.getGranted());
            final BsonArray revokes = toBsonArray(subjectIds.getRevoked());
            final Document document = assembleDocument(key, bsonValue, grants, revokes);
            return replaceFeatureIdByWildcard(key)
                    .map(replacedKey -> Stream.of(document, assembleDocument(replacedKey, bsonValue, grants, revokes)))
                    .orElse(Stream.of(document));
        } else {
            return Stream.empty();
        }
    }

    private EffectedSubjectIds computeEffectedSubjectIds(final JsonPointer key) {
        final ResourceKey resourceKey = ResourceKey.newInstance(THING, key.toString());
        return enforcer.getSubjectIdsWithPermission(resourceKey, READ);
    }

    private static Document assembleDocument(final CharSequence key, final BsonValue value, final BsonArray grants,
            final BsonArray revokes) {

        return new Document().append(FIELD_INTERNAL_KEY, key.toString())
                .append(FIELD_INTERNAL_VALUE, value)
                .append(FIELD_GRANTED, grants)
                .append(FIELD_REVOKED, revokes);
    }

    private static Optional<CharSequence> replaceFeatureIdByWildcard(final JsonPointer key) {
        return key.getRoot()
                .filter(FEATURES_KEY::equals)
                .flatMap(features -> key.getSubPointer(2)) // skip 'features' and <featureId>
                .map(WILDCARD_FEATURE_POINTER::append);
    }

    private static BsonArray toBsonArray(final Collection<String> strings) {
        final BsonArray bsonArray = new BsonArray();
        strings.forEach(string -> bsonArray.add(new BsonString(string)));
        return bsonArray;
    }
}
