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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonNumber;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;

/**
 * Map Thing with Enforcer to Document.
 */
public final class EnforcedThingMapper {

    private static final ResourceKey THING_ROOT_RESOURCE_KEY = ResourceKey.newInstance(ThingCommand.RESOURCE_TYPE, "/");

    private EnforcedThingMapper() {
        throw new AssertionError();
    }

    /**
     * Map a Thing JSON into a search index entry with synthesized metadata.
     *
     * @param thing the Thing in JSON format.
     * @param enforcer the policy-enforcer of the Thing.
     * @param policyRevision revision of the policy for an policy enforcer.
     * @return BSON document to write into the search index.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if Thing ID or revision is missing.
     */
    public static BsonDocument mapThing(final JsonObject thing, final Enforcer enforcer, final long policyRevision) {
        return toWriteModel(thing, enforcer, policyRevision).getThingDocument();
    }

    /**
     * Map a Thing JSON into a search index write model.
     *
     * @param thing the Thing in JSON format.
     * @param enforcer the policy-enforcer of the Thing.
     * @param policyRevision revision of the policy for an policy enforcer.
     * @return BSON document to write into the search index.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if Thing ID or revision is missing.
     */
    private static ThingWriteModel toWriteModel(final JsonObject thing,
            final Enforcer enforcer,
            final long policyRevision) {

        return toWriteModel(thing, enforcer, policyRevision, -1, null);
    }

    /**
     * Map a Thing JSON into a search index write model.
     *
     * @param thing the Thing in JSON format.
     * @param enforcer the policy-enforcer of the Thing.
     * @param policyRevision revision of the policy for an policy enforcer.
     * @param maxArraySize only arrays smaller than this are indexed.
     * @param oldMetadata the metadata that triggered the search update, possibly containing sender information.
     * @return BSON document to write into the search index.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if Thing ID or revision is missing.
     */
    public static ThingWriteModel toWriteModel(final JsonObject thing,
            final Enforcer enforcer,
            final long policyRevision,
            final int maxArraySize,
            @Nullable final Metadata oldMetadata) {

        final String extractedThing = thing.getValueOrThrow(Thing.JsonFields.ID);
        final var thingId = ThingId.of(extractedThing);
        final long thingRevision = thing.getValueOrThrow(Thing.JsonFields.REVISION);
        final var nullablePolicyId = thing.getValue(Thing.JsonFields.POLICY_ID).map(PolicyId::of).orElse(null);
        final var metadata = Metadata.of(thingId, thingRevision, nullablePolicyId, policyRevision,
                        Optional.ofNullable(oldMetadata).flatMap(Metadata::getModified).orElse(null),
                        Optional.ofNullable(oldMetadata).map(Metadata::getTimers).orElse(List.of()),
                        Optional.ofNullable(oldMetadata).map(Metadata::getSenders).orElse(List.of()))
                .withOrigin(Optional.ofNullable(oldMetadata).flatMap(Metadata::getOrigin).orElse(null));

        return ThingWriteModel.of(metadata, toBsonDocument(thing, enforcer, maxArraySize, metadata));
    }

    static BsonDocument toBsonDocument(final JsonObject thing,
            final Enforcer enforcer,
            final int maxArraySize,
            final Metadata metadata) {

        final var thingId = metadata.getThingId();
        final var thingRevision = metadata.getThingRevision();
        final var policyRevision = metadata.getPolicyRevision().orElse(0L);
        final BsonValue thingCopyForSorting = JsonToBson.convert(pruneArrays(thing, maxArraySize));
        final BsonArray flattenedValues = EnforcedThingFlattener.flattenJson(thing, enforcer, maxArraySize);
        return new BsonDocument().append(PersistenceConstants.FIELD_ID, new BsonString(thingId.toString()))
                .append(PersistenceConstants.FIELD_REVISION, new BsonInt64(thingRevision))
                .append(PersistenceConstants.FIELD_NAMESPACE,
                        new BsonString(metadata.getNamespaceInPersistence()))
                .append(PersistenceConstants.FIELD_GLOBAL_READ, getGlobalRead(enforcer))
                .append(PersistenceConstants.FIELD_POLICY_ID,
                        new BsonString(metadata.getPolicyIdInPersistence()))
                .append(PersistenceConstants.FIELD_POLICY_REVISION, new BsonInt64(policyRevision))
                .append(PersistenceConstants.FIELD_SORTING, thingCopyForSorting)
                .append(PersistenceConstants.FIELD_INTERNAL, flattenedValues);
    }

    private static BsonArray getGlobalRead(final Enforcer enforcer) {

        final BsonArray bsonArray = new BsonArray();

        enforcer.getSubjectsWithPartialPermission(THING_ROOT_RESOURCE_KEY, Permissions.newInstance(Permission.READ))
                .stream()
                .map(Object::toString)
                .map(BsonString::new)
                .forEach(bsonArray::add);

        return bsonArray;
    }

    /**
     * Truncate large arrays from the sort field.
     *
     * @param thing JSON representation of a thing.
     * @param maxArraySize how large arrays may be in the search index.
     * @return thing with large arrays truncated.
     */
    private static JsonObject pruneArrays(final JsonObject thing, final long maxArraySize) {
        return maxArraySize < 0 ? thing : new ArrayPruner(maxArraySize).eval(thing).asObject();
    }

    private static final class ArrayPruner implements JsonInternalVisitor<JsonValue> {

        private final long maxArraySize;

        private ArrayPruner(final long maxArraySize) {
            this.maxArraySize = maxArraySize;
        }

        @Override
        public JsonValue nullValue() {
            return JsonValue.nullLiteral();
        }

        @Override
        public JsonValue bool(final boolean value) {
            return JsonValue.of(value);
        }

        @Override
        public JsonValue string(final String value) {
            return JsonValue.of(value);
        }

        @Override
        public JsonValue number(final JsonNumber value) {
            return value;
        }

        @Override
        public JsonValue array(final Stream<JsonValue> values) {
            return values.limit(maxArraySize).collect(JsonCollectors.valuesToArray());
        }

        @Override
        public JsonValue object(final Stream<Map.Entry<String, JsonValue>> values) {
            return values.map(entry -> JsonFactory.newField(JsonKey.of(entry.getKey()), entry.getValue()))
                    .collect(JsonCollectors.fieldsToObject());
        }
    }

}
