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
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_REFERENCED_POLICIES;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_REVISION;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_THING;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.eclipse.ditto.internal.models.streaming.AbstractEntityIdWithRevision;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;

/**
 * Map Thing with Enforcer to Document.
 */
public final class EnforcedThingMapper {

    private EnforcedThingMapper() {
        throw new AssertionError();
    }

    /**
     * Map a Thing JSON into a search index write model.
     *
     * @param thing the Thing in JSON format.
     * @param policy the policy-enforcer of the Thing.
     * @param policyRevision revision of the policy for a policy enforcer.
     * @param referencedPolicies all policies referenced by the policy.
     * @param oldMetadata the metadata that triggered the search update, possibly containing sender information.
     * @param maxArraySize only arrays smaller than this are indexed.
     * @return BSON document to write into the search index.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if Thing ID or revision is missing.
     */
    public static ThingWriteModel toWriteModel(final JsonObject thing,
            final Policy policy,
            final Set<PolicyTag> referencedPolicies,
            final long policyRevision,
            @Nullable final Metadata oldMetadata, final int maxArraySize) {

        final String extractedThing = thing.getValueOrThrow(Thing.JsonFields.ID);
        final var thingId = ThingId.of(extractedThing);
        final long thingRevision = thing.getValueOrThrow(Thing.JsonFields.REVISION);
        final var optionalPolicyId = thing.getValue(Thing.JsonFields.POLICY_ID).map(PolicyId::of);

        final HashSet<PolicyTag> allReferencedPolicies = new HashSet<>(referencedPolicies);
        final List<PolicyTag> policyTagsOfDeletedButStillImportedPolicies =
                Optional.ofNullable(oldMetadata).map(Metadata::getAllReferencedPolicyTags).orElseGet(Set::of).stream()
                        .filter(oldReferencedPolicyTag -> policy.getPolicyImports()
                                .getPolicyImport(oldReferencedPolicyTag.getEntityId())
                                .isPresent())
                        .filter(oldReferencedPolicyTag -> referencedPolicies.stream()
                                .noneMatch(newReferencedPolicyTag -> newReferencedPolicyTag.getEntityId()
                                        .equals(oldReferencedPolicyTag.getEntityId())))
                        .toList();
        allReferencedPolicies.addAll(policyTagsOfDeletedButStillImportedPolicies);

        final PolicyTag thingPolicyTag = optionalPolicyId
                .map(policyId -> PolicyTag.of(policyId, policyRevision))
                .orElse(null);

        final var metadata =
                Metadata.of(thingId, thingRevision, thingPolicyTag, allReferencedPolicies,
                        Optional.ofNullable(oldMetadata).flatMap(Metadata::getModified).orElse(null),
                        Optional.ofNullable(oldMetadata).map(Metadata::getEvents).orElse(List.of()),
                        Optional.ofNullable(oldMetadata).map(Metadata::getTimers).orElse(List.of()),
                        Optional.ofNullable(oldMetadata).map(Metadata::getAckRecipients).orElse(List.of()),
                        Optional.ofNullable(oldMetadata).map(Metadata::getUpdateReasons)
                                .orElse(List.of(UpdateReason.UNKNOWN))
                );

        return ThingWriteModel.of(metadata, toBsonDocument(thing, policy, metadata, maxArraySize));
    }

    static BsonDocument toBsonDocument(final JsonObject thing, final Policy policy, final Metadata metadata) {
        return toBsonDocument(thing, policy, metadata, -1);
    }

    static BsonDocument toBsonDocument(final JsonObject thing, final Policy policy, final Metadata metadata,
            final int maxArraySize) {

        final var enforced = IndexLengthRestrictionEnforcerVisitor.enforce(thing, maxArraySize);
        final var thingId = metadata.getThingId();
        final var thingRevision = metadata.getThingRevision();
        final var policyRevision =
                metadata.getThingPolicyTag().map(AbstractEntityIdWithRevision::getRevision).orElse(0L);
        final var thingBson = DittoBsonJson.getInstance().parse(enforced);
        final var evaluatedPolicy = EvaluatedPolicy.of(policy, thing);
        final var featureArray = getFeatureArray(thing, evaluatedPolicy);
        final BsonArray referencedPolicies = getReferencedPolicies(metadata.getAllReferencedPolicyTags());

        return new BsonDocument().append(PersistenceConstants.FIELD_ID, new BsonString(thingId.toString()))
                .append(FIELD_NAMESPACE, new BsonString(thingId.getNamespace()))
                .append(FIELD_GLOBAL_READ, evaluatedPolicy.getGlobalRead())
                .append(FIELD_REVISION, new BsonInt64(thingRevision))
                .append(FIELD_POLICY_ID, new BsonString(metadata.getPolicyIdInPersistence()))
                .append(FIELD_POLICY_REVISION, new BsonInt64(policyRevision))
                .append(FIELD_REFERENCED_POLICIES, referencedPolicies)
                .append(FIELD_THING, thingBson)
                .append(FIELD_POLICY, evaluatedPolicy.forThing())
                .append(FIELD_F_ARRAY, featureArray);
    }

    private static BsonArray getReferencedPolicies(final Set<PolicyTag> referencedPolicyTags) {
        final List<BsonDocument> referencedPolicyDocuments = referencedPolicyTags.stream()
                .map(AbstractEntityIdWithRevision::toJson)
                .map(policyTagJson -> DittoBsonJson.getInstance().parse(policyTagJson))
                .toList();
        return new BsonArray(referencedPolicyDocuments);
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
