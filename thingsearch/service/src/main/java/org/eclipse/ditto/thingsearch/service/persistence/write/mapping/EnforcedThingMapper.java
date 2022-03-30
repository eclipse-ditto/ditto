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

import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_FEATURES;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_FEATURE_ID;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_F_ARRAY;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_GLOBAL_READ;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_NAMESPACE;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_POLICY;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_POLICY_ID;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_POLICY_REVISION;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_REVISION;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_THING;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
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
     * @param policy the policy of the Thing.
     * @param policyRevision revision of the policy.
     * @return BSON document to write into the search index.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if Thing ID or revision is missing.
     */
    public static BsonDocument mapThing(final JsonObject thing, final Policy policy, final long policyRevision) {
        return toWriteModel(thing, policy, policyRevision).getThingDocument();
    }

    /**
     * Map a Thing JSON into a search index write model.
     *
     * @param thing the Thing in JSON format.
     * @param policy the policy of the Thing.
     * @param policyRevision revision of the policy.
     * @return BSON document to write into the search index.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if Thing ID or revision is missing.
     */
    private static ThingWriteModel toWriteModel(final JsonObject thing,
            final Policy policy,
            final long policyRevision) {

        return toWriteModel(thing, policy, policyRevision, -1, null);
    }

    /**
     * Map a Thing JSON into a search index write model.
     *
     * @param thing the Thing in JSON format.
     * @param policy the policy-enforcer of the Thing.
     * @param policyRevision revision of the policy for an policy enforcer.
     * @param maxArraySize only arrays smaller than this are indexed.
     * @param oldMetadata the metadata that triggered the search update, possibly containing sender information.
     * @return BSON document to write into the search index.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if Thing ID or revision is missing.
     */
    public static ThingWriteModel toWriteModel(final JsonObject thing,
            final Policy policy,
            final long policyRevision,
            final int maxArraySize,
            @Nullable final Metadata oldMetadata) {

        final String extractedThing = thing.getValueOrThrow(Thing.JsonFields.ID);
        final var thingId = ThingId.of(extractedThing);
        final long thingRevision = thing.getValueOrThrow(Thing.JsonFields.REVISION);
        final var nullablePolicyId = thing.getValue(Thing.JsonFields.POLICY_ID).map(PolicyId::of).orElse(null);
        final var metadata = Metadata.of(thingId, thingRevision, nullablePolicyId, policyRevision,
                        Optional.ofNullable(oldMetadata).flatMap(Metadata::getModified).orElse(null),
                        Optional.ofNullable(oldMetadata).map(Metadata::getEvents).orElse(List.of()),
                        Optional.ofNullable(oldMetadata).map(Metadata::getTimers).orElse(List.of()),
                        Optional.ofNullable(oldMetadata).map(Metadata::getSenders).orElse(List.of()))
                .withOrigin(Optional.ofNullable(oldMetadata).flatMap(Metadata::getOrigin).orElse(null));

        return ThingWriteModel.of(metadata, toBsonDocument(thing, policy, metadata));
    }

    static BsonDocument toBsonDocument(final JsonObject thing,
            final Policy policy,
            final Metadata metadata) {

        final var thingId = metadata.getThingId();
        final var thingRevision = metadata.getThingRevision();
        final var policyRevision = metadata.getPolicyRevision().orElse(0L);
        final var thingBson = DittoBsonJson.getInstance().parse(thing);
        final var evaluatedPolicy = EvaluatedPolicy.of(policy, thing);
        final var featureArray = getFeatureArray(thing, evaluatedPolicy);

        return new BsonDocument().append(PersistenceConstants.FIELD_ID, new BsonString(thingId.toString()))
                .append(FIELD_NAMESPACE, new BsonString(thingId.getNamespace()))
                .append(FIELD_GLOBAL_READ, evaluatedPolicy.getGlobalRead())
                .append(FIELD_REVISION, new BsonInt64(thingRevision))
                .append(FIELD_POLICY_ID, new BsonString(metadata.getPolicyIdInPersistence()))
                .append(FIELD_POLICY_REVISION, new BsonInt64(policyRevision))
                .append(FIELD_THING, thingBson)
                .append(FIELD_POLICY, evaluatedPolicy.forThing())
                .append(FIELD_F_ARRAY, featureArray);
    }

    private static BsonArray getFeatureArray(final JsonObject thing, final EvaluatedPolicy evaluatedPolicy) {
        final JsonObject features = thing.getValue(FIELD_FEATURES)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonObject.empty());

        final var array = new BsonArray();
        for (final var field : features) {
            array.add(getFeatureArrayElement(field, evaluatedPolicy));
        }
        return array;
    }

    private static BsonDocument getFeatureArrayElement(final JsonField featureField,
            final EvaluatedPolicy evaluatedPolicy) {

        final BsonDocument doc = new BsonDocument();
        final var featureId = featureField.getKeyName();
        doc.put(FIELD_FEATURE_ID, new BsonString(featureId));

        final JsonObject featureContent = Optional.of(featureField.getValue())
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonObject.empty());
        for (final var field : featureContent) {
            doc.put(field.getKeyName(), DittoBsonJson.getInstance().parseValue(field.getValue()));
        }

        doc.put(FIELD_POLICY, evaluatedPolicy.forFeature(featureId));
        return doc;
    }
}
