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
package org.eclipse.ditto.thingsearch.persistence.mongo.write.mapping;

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
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Invokes the full {@link EnforcedThingBsonMapper} mapping chain for a named golden fixture case, with the
 * {@link Metadata} computed exactly as production computes it.
 *
 * <p>Mirrored call site: {@code EnforcementFlow.computeWriteModel(Metadata, JsonObject)}
 * (thingsearch/service/.../persistence/write/streaming/EnforcementFlow.java), which calls
 * <pre>{@code EnforcedThingMapper.toWriteModel(thing, pair.first(), pair.second(), entry.getRevision(), metadata, maxArraySize)}</pre>
 * where {@code pair.first()}/{@code pair.second()} are the resolved {@link Policy} and its referenced-policy
 * tags, {@code entry.getRevision()} is the policy revision, {@code metadata} is the previous/"old"
 * {@link Metadata}, and {@code maxArraySize} is the configured index array-size limit.
 * {@code EnforcedThingMapper} lives in the thing-search service, which this module must not depend on, so
 * {@link #toMetadata(JsonObject, Policy, GoldenFixtures.Params)} is a test-local copy of the metadata computation of
 * {@code EnforcedThingMapper.toWriteModel}; the service's {@code EnforcedThingMapperTest} pins that method.
 * Shared by {@link EnforcedThingMapperGoldenGenerator} and {@code EnforcedThingBsonMapperGoldenTest} so both
 * exercise identical inputs.
 */
final class GoldenFixtureRunner {

    private GoldenFixtureRunner() {
        throw new AssertionError();
    }

    static BsonDocument run(final String caseName) {
        final JsonObject thing = GoldenFixtures.loadThing(caseName);
        final Policy policy = GoldenFixtures.loadPolicy(caseName);
        final GoldenFixtures.Params params = GoldenFixtures.loadParams(caseName);

        final Metadata metadata = toMetadata(thing, policy, params);

        return EnforcedThingBsonMapper.toBsonDocument(thing, policy, metadata, params.maxArraySize());
    }

    /**
     * Computes the {@link Metadata} of the write model like {@code EnforcedThingMapper.toWriteModel} does: the
     * referenced policies plus the old referenced policies that are still imported by {@code policy} and not
     * referenced again by id, the thing policy tag, and the carry-overs from the old metadata.
     */
    private static Metadata toMetadata(final JsonObject thing, final Policy policy,
            final GoldenFixtures.Params params) {

        final Set<PolicyTag> referencedPolicies = Set.copyOf(params.referencedPolicies());
        @Nullable final Metadata oldMetadata = toOldMetadata(thing, params);
        final ThingId thingId = ThingId.of(thing.getValueOrThrow(Thing.JsonFields.ID));
        final long thingRevision = thing.getValueOrThrow(Thing.JsonFields.REVISION);

        final Set<PolicyTag> allReferencedPolicies = new LinkedHashSet<>(referencedPolicies);
        Optional.ofNullable(oldMetadata).map(Metadata::getAllReferencedPolicyTags).orElseGet(Set::of).stream()
                .filter(oldTag -> policy.getPolicyImports().getPolicyImport(oldTag.getEntityId()).isPresent())
                .filter(oldTag -> referencedPolicies.stream()
                        .noneMatch(newTag -> newTag.getEntityId().equals(oldTag.getEntityId())))
                .forEach(allReferencedPolicies::add);

        final PolicyTag thingPolicyTag = thing.getValue(Thing.JsonFields.POLICY_ID)
                .map(PolicyId::of)
                .map(policyId -> PolicyTag.of(policyId, params.policyRevision()))
                .orElse(null);

        return Metadata.of(thingId, thingRevision, thingPolicyTag, null, allReferencedPolicies,
                Optional.ofNullable(oldMetadata).flatMap(Metadata::getModified).orElse(null),
                Optional.ofNullable(oldMetadata).map(Metadata::getEvents).orElse(List.of()),
                Optional.ofNullable(oldMetadata).map(Metadata::getTimers).orElse(List.of()),
                Optional.ofNullable(oldMetadata).map(Metadata::getAckRecipients).orElse(List.of()),
                Optional.ofNullable(oldMetadata).map(Metadata::getUpdateReasons)
                        .orElse(List.of(UpdateReason.UNKNOWN)));
    }

    /**
     * Builds the "old" {@link Metadata}, if the case defines {@code oldReferencedPolicies} (used to exercise the
     * "policy no longer directly referenced but still imported" retention branch). {@code null} otherwise,
     * matching how the very first update for a thing has no previous metadata.
     */
    @Nullable
    private static Metadata toOldMetadata(final JsonObject thing, final GoldenFixtures.Params params) {
        if (params.oldReferencedPolicies().isEmpty()) {
            return null;
        }
        final ThingId thingId = ThingId.of(thing.getValueOrThrow(Thing.JsonFields.ID));
        final long thingRevision = thing.getValueOrThrow(Thing.JsonFields.REVISION);
        // 7-arg overload: (thingId, thingRevision, thingPolicy, causingPolicyTag, allReferencedPolicies, modified, timer)
        return Metadata.of(thingId, thingRevision, null, null, params.oldReferencedPolicies(), null, null);
    }
}
