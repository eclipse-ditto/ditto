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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.persistence.api.mapping.SearchIndexDocumentFactory;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;

/**
 * Map Thing with Enforcer to Document.
 * <p>
 * The mapping first produces a backend-neutral {@link org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument}
 * via {@link SearchIndexDocumentFactory} and then encodes it into the Mongo {@link BsonDocument} via
 * {@link SearchIndexDocumentMongoEncoder}. The neutral document is the shared source of truth for all
 * persistence backends; the Mongo encoder reproduces the exact historical BSON wire format.
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
     * @return backend-neutral write model to store in the search index.
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

        final Set<PolicyTag> allReferencedPolicies = new LinkedHashSet<>(referencedPolicies);
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
                Metadata.of(thingId, thingRevision, thingPolicyTag, null, allReferencedPolicies,
                        Optional.ofNullable(oldMetadata).flatMap(Metadata::getModified).orElse(null),
                        Optional.ofNullable(oldMetadata).map(Metadata::getEvents).orElse(List.of()),
                        Optional.ofNullable(oldMetadata).map(Metadata::getTimers).orElse(List.of()),
                        Optional.ofNullable(oldMetadata).map(Metadata::getAckRecipients).orElse(List.of()),
                        Optional.ofNullable(oldMetadata).map(Metadata::getUpdateReasons)
                                .orElse(List.of(UpdateReason.UNKNOWN))
                );

        return ThingWriteModel.of(metadata,
                SearchIndexDocumentFactory.create(thing, policy, metadata, maxArraySize));
    }

    static BsonDocument toBsonDocument(final JsonObject thing, final Policy policy, final Metadata metadata) {
        return toBsonDocument(thing, policy, metadata, -1);
    }

    static BsonDocument toBsonDocument(final JsonObject thing, final Policy policy, final Metadata metadata,
            final int maxArraySize) {

        return SearchIndexDocumentMongoEncoder.encode(
                SearchIndexDocumentFactory.create(thing, policy, metadata, maxArraySize));
    }

}
