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
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.eclipse.ditto.internal.models.streaming.AbstractEntityIdWithRevision;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument.FeatureEntry;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;

/**
 * Encodes the backend-neutral {@link SearchIndexDocument} into the Mongo {@link BsonDocument} written to the
 * search index (and decodes it back), reproducing the exact field order, {@code _id}, key escaping
 * ({@code KeyNameReviser} via {@link DittoBsonJson}) and structure historically produced by
 * {@code EnforcedThingMapper}.
 */
public final class SearchIndexDocumentMongoEncoder {

    private SearchIndexDocumentMongoEncoder() {
        throw new AssertionError();
    }

    public static BsonDocument encode(final SearchIndexDocument document) {
        return new BsonDocument()
                .append(PersistenceConstants.FIELD_ID, new BsonString(document.thingId().toString()))
                .append(FIELD_NAMESPACE, new BsonString(document.namespace()))
                .append(FIELD_GLOBAL_READ, globalRead(document.globalRead()))
                .append(FIELD_REVISION, new BsonInt64(document.revision()))
                .append(FIELD_POLICY_ID, new BsonString(policyIdInPersistence(document)))
                .append(FIELD_POLICY_REVISION, new BsonInt64(document.policyRevision()))
                .append(FIELD_REFERENCED_POLICIES, referencedPolicies(document.referencedPolicies()))
                .append(FIELD_THING, DittoBsonJson.getInstance().parse(document.thing()))
                .append(FIELD_POLICY, DittoBsonJson.getInstance().parse(document.policyAuth()))
                .append(FIELD_F_ARRAY, featureArray(document.features()));
    }

    /**
     * Decode a stored Mongo {@link BsonDocument} back into the backend-neutral {@link SearchIndexDocument}. This is
     * the faithful inverse of {@link #encode(SearchIndexDocument)}: it satisfies
     * {@code encode(decode(encode(x))).equals(encode(x))}.
     *
     * @param stored the stored Mongo document.
     * @return the decoded neutral document.
     */
    public static SearchIndexDocument decode(final BsonDocument stored) {
        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();

        final ThingId thingId = ThingId.of(stored.getString(PersistenceConstants.FIELD_ID).getValue());
        final String namespace = stored.getString(FIELD_NAMESPACE).getValue();
        final List<String> globalRead = new ArrayList<>();
        for (final BsonValue subject : stored.getArray(FIELD_GLOBAL_READ)) {
            globalRead.add(subject.asString().getValue());
        }
        final long revision = stored.getNumber(FIELD_REVISION).longValue();
        final String policyIdString = stored.getString(FIELD_POLICY_ID).getValue();
        final PolicyId policyId = policyIdString.isEmpty() ? null : PolicyId.of(policyIdString);
        final long policyRevision = stored.getNumber(FIELD_POLICY_REVISION).longValue();

        final Set<PolicyTag> referencedPolicies = new LinkedHashSet<>();
        for (final BsonValue referencedPolicy : stored.getArray(FIELD_REFERENCED_POLICIES)) {
            referencedPolicies.add(PolicyTag.fromJson(dittoBsonJson.serialize(referencedPolicy.asDocument())));
        }

        final JsonObject thing = dittoBsonJson.serialize(stored.getDocument(FIELD_THING));
        final JsonObject policyAuth = dittoBsonJson.serialize(stored.getDocument(FIELD_POLICY));

        final List<FeatureEntry> features = new ArrayList<>();
        for (final BsonValue featureValue : stored.getArray(FIELD_F_ARRAY)) {
            final BsonDocument featureDoc = featureValue.asDocument();
            final String featureId = featureDoc.getString(FIELD_FEATURE_ID).getValue();
            final JsonObject auth = dittoBsonJson.serialize(featureDoc.getDocument(FIELD_POLICY));
            final JsonObjectBuilder contentBuilder = JsonObject.newBuilder();
            for (final Map.Entry<String, BsonValue> entry : featureDoc.entrySet()) {
                final String key = entry.getKey();
                if (!key.equals(FIELD_FEATURE_ID) && !key.equals(FIELD_POLICY)) {
                    contentBuilder.set(key, dittoBsonJson.serialize(entry.getValue()));
                }
            }
            features.add(FeatureEntry.of(featureId, contentBuilder.build(), auth));
        }

        return SearchIndexDocument.newBuilder(thingId)
                .namespace(namespace)
                .globalRead(globalRead)
                .revision(revision)
                .policyId(policyId)
                .policyRevision(policyRevision)
                .referencedPolicies(referencedPolicies)
                .thing(thing)
                .policyAuth(policyAuth)
                .features(features)
                .build();
    }

    private static String policyIdInPersistence(final SearchIndexDocument document) {
        return document.policyId().map(PolicyId::toString).orElse("");
    }

    private static BsonArray globalRead(final List<String> subjects) {
        final var array = new BsonArray();
        for (final String subject : subjects) {
            array.add(new BsonString(subject));
        }
        return array;
    }

    private static BsonArray referencedPolicies(final Set<PolicyTag> referencedPolicyTags) {
        final List<BsonDocument> referencedPolicyDocuments = referencedPolicyTags.stream()
                .map(AbstractEntityIdWithRevision::toJson)
                .map(policyTagJson -> DittoBsonJson.getInstance().parse(policyTagJson))
                .toList();
        return new BsonArray(referencedPolicyDocuments);
    }

    private static BsonArray featureArray(final List<FeatureEntry> features) {
        final var array = new BsonArray();
        for (final FeatureEntry feature : features) {
            array.add(featureArrayElement(feature));
        }
        return array;
    }

    private static BsonDocument featureArrayElement(final FeatureEntry feature) {
        final BsonDocument doc = new BsonDocument();
        doc.put(FIELD_FEATURE_ID, new BsonString(feature.featureId()));
        for (final JsonField field : feature.content()) {
            doc.put(field.getKeyName(), DittoBsonJson.getInstance().parseValue(field.getValue()));
        }
        doc.put(FIELD_POLICY, DittoBsonJson.getInstance().parse(feature.auth()));
        return doc;
    }

}
