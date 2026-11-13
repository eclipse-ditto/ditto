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
package org.eclipse.ditto.thingsearch.persistence.api.mapping;

import static org.eclipse.ditto.thingsearch.persistence.api.mapping.SearchIndexFields.FIELD_FEATURES;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.internal.models.streaming.AbstractEntityIdWithRevision;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument.FeatureEntry;

/**
 * Builds the backend-neutral {@link SearchIndexDocument} from the same inputs the search-update pipeline
 * consumes today: a thing JSON, its enforcing policy, the update {@link Metadata} and the configured maximum
 * indexed array size.
 * <p>
 * This is the single source of truth shared by all persistence backends. Index-length restriction (value
 * truncation and array capping) is applied here, so the {@link SearchIndexDocument#thing() thing} carried by
 * the produced document is the already-restricted payload. Keys are kept <em>raw</em> (not encoder-escaped);
 * backend-specific key encoding is applied by the respective backend encoder.
 */
public final class SearchIndexDocumentFactory {

    private SearchIndexDocumentFactory() {
        throw new AssertionError();
    }

    /**
     * Build the neutral search-index document.
     *
     * @param thing the thing in JSON format.
     * @param policy the enforcing policy of the thing.
     * @param metadata the metadata describing the update (thing/policy IDs, revisions, referenced policies).
     * @param maxArraySize only arrays smaller than this are indexed; a negative value indexes all arrays.
     * @return the neutral search-index document.
     */
    public static SearchIndexDocument create(final JsonObject thing,
            final Policy policy,
            final Metadata metadata,
            final int maxArraySize) {

        final JsonObject enforced = IndexLengthRestrictionEnforcerVisitor.enforce(thing, maxArraySize);
        final ThingId thingId = metadata.getThingId();
        final long policyRevision = metadata.getThingPolicyTag()
                .map(AbstractEntityIdWithRevision::getRevision)
                .orElse(0L);
        final PolicyId policyId = metadata.getThingPolicyTag()
                .map(PolicyTag::getEntityId)
                .orElse(null);

        final EvaluatedPolicy evaluatedPolicy = EvaluatedPolicy.of(policy, thing, thingId.getNamespace());

        return SearchIndexDocument.newBuilder(thingId)
                .namespace(thingId.getNamespace())
                .revision(metadata.getThingRevision())
                .policyId(policyId)
                .policyRevision(policyRevision)
                .referencedPolicies(metadata.getAllReferencedPolicyTags())
                .globalRead(evaluatedPolicy.getGlobalRead())
                .thing(enforced)
                .policyAuth(evaluatedPolicy.forThing())
                .features(buildFeatures(thing, evaluatedPolicy))
                .build();
    }

    private static List<FeatureEntry> buildFeatures(final JsonObject thing, final EvaluatedPolicy evaluatedPolicy) {
        final JsonObject features = thing.getValue(FIELD_FEATURES)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonObject.empty());

        final List<FeatureEntry> entries = new ArrayList<>();
        for (final JsonField field : features) {
            final String featureId = field.getKeyName();
            final JsonObject content = Optional.of(field.getValue())
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .orElse(JsonObject.empty());
            entries.add(FeatureEntry.of(featureId, content, evaluatedPolicy.forFeature(featureId)));
        }
        return entries;
    }

}
