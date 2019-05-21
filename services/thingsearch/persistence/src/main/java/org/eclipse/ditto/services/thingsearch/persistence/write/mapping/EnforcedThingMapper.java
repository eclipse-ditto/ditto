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
package org.eclipse.ditto.services.thingsearch.persistence.write.mapping;

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_GLOBAL_READ;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_NAMESPACE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_POLICY_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_POLICY_REVISION;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_REVISION;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_SORTING;

import java.util.Map;
import java.util.stream.Stream;

import org.bson.BsonArray;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonNumber;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.Metadata;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.ThingWriteModel;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

/**
 * Map Thing with Enforcer to Document.
 */
public final class EnforcedThingMapper {

    private static final ResourceKey THING_ROOT_RESOURCE_KEY = ResourceKey.newInstance(ThingCommand.RESOURCE_TYPE, "/");

    /**
     * Map a Thing JSON into a search index entry with synthesized metadata.
     *
     * @param thing the Thing in JSON format.
     * @param enforcer the policy- or ACL-enforcer of the Thing.
     * @param policyRevision revision of the policy for an policy enforcer, or any number for an ACL enforcer.
     * @return BSON document to write into the search index.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if Thing ID or revision is missing.
     */
    public static Document mapThing(final JsonObject thing, final Enforcer enforcer, final long policyRevision) {
        return toWriteModel(thing, enforcer, policyRevision).getThingDocument();
    }

    /**
     * Map a Thing JSON into a search index write model.
     *
     * @param thing the Thing in JSON format.
     * @param enforcer the policy- or ACL-enforcer of the Thing.
     * @param policyRevision revision of the policy for an policy enforcer, or any number for an ACL enforcer.
     * @return BSON document to write into the search index.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if Thing ID or revision is missing.
     */
    public static ThingWriteModel toWriteModel(final JsonObject thing,
            final Enforcer enforcer,
            final long policyRevision) {

        return toWriteModel(thing, enforcer, policyRevision, -1);
    }

    /**
     * Map a Thing JSON into a search index write model.
     *
     * @param thing the Thing in JSON format.
     * @param enforcer the policy- or ACL-enforcer of the Thing.
     * @param policyRevision revision of the policy for an policy enforcer, or any number for an ACL enforcer.
     * @param maxArraySize only arrays smaller than this are indexed.
     * @return BSON document to write into the search index.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if Thing ID or revision is missing.
     */
    public static ThingWriteModel toWriteModel(final JsonObject thing,
            final Enforcer enforcer,
            final long policyRevision,
            final int maxArraySize) {

        final String thingId = thing.getValueOrThrow(Thing.JsonFields.ID);
        final long thingRevision = thing.getValueOrThrow(Thing.JsonFields.REVISION);
        final String nullablePolicyId = thing.getValue(Thing.JsonFields.POLICY_ID).orElse(null);
        final Metadata metadata = Metadata.of(thingId, thingRevision, nullablePolicyId, policyRevision);

        // hierarchical values for sorting
        final BsonValue thingCopyForSorting = JsonToBson.convert(pruneArrays(thing, maxArraySize));

        // flattened values for querying with special handling for thingId and namespace
        final BsonArray flattenedValues = EnforcedThingFlattener.flattenJson(thing, enforcer, maxArraySize);

        final Document thingDocument =
                new Document().append(FIELD_ID, thingId)
                        .append(FIELD_REVISION, thingRevision)
                        .append(FIELD_NAMESPACE, metadata.getNamespaceInPersistence())
                        .append(FIELD_GLOBAL_READ, getGlobalRead(enforcer))
                        .append(FIELD_POLICY_ID, metadata.getPolicyIdInPersistence())
                        .append(FIELD_POLICY_REVISION, policyRevision)
                        .append(FIELD_SORTING, thingCopyForSorting)
                        .append(FIELD_INTERNAL, flattenedValues);

        return ThingWriteModel.of(metadata, thingDocument);
    }

    private static BsonArray getGlobalRead(final Enforcer enforcer) {

        final BsonArray bsonArray = new BsonArray();

        enforcer.getSubjectIdsWithPartialPermission(THING_ROOT_RESOURCE_KEY, Permission.READ)
                .stream()
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
