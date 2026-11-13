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

import java.util.Set;

import org.bson.BsonDocument;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Invokes TODAY'S (pre-refactor) full {@link EnforcedThingMapper} mapping chain for a named golden fixture
 * case, exactly as production wires it.
 *
 * <p>Mirrored call site: {@code EnforcementFlow.computeWriteModel(Metadata, JsonObject)}
 * (thingsearch/service/.../persistence/write/streaming/EnforcementFlow.java), which calls
 * <pre>{@code EnforcedThingMapper.toWriteModel(thing, pair.first(), pair.second(), entry.getRevision(), metadata, maxArraySize)}</pre>
 * where {@code pair.first()}/{@code pair.second()} are the resolved {@link Policy} and its referenced-policy
 * tags, {@code entry.getRevision()} is the policy revision, {@code metadata} is the previous/"old"
 * {@link Metadata} (used only for its {@code allReferencedPolicyTags}, per
 * {@code EnforcedThingMapper.toWriteModel} lines 85-95), and {@code maxArraySize} is the configured index
 * array-size limit. Shared by {@link EnforcedThingMapperGoldenGenerator} and
 * {@code EnforcedThingMapperGoldenTest} so both exercise identical inputs.
 */
final class GoldenFixtureRunner {

    private GoldenFixtureRunner() {
        throw new AssertionError();
    }

    static BsonDocument run(final String caseName) {
        final JsonObject thing = GoldenFixtures.loadThing(caseName);
        final Policy policy = GoldenFixtures.loadPolicy(caseName);
        final GoldenFixtures.Params params = GoldenFixtures.loadParams(caseName);

        final Metadata oldMetadata = toOldMetadata(thing, params);

        final Set<PolicyTag> referencedPolicies = Set.copyOf(params.referencedPolicies());

        return SearchIndexDocumentMongoEncoder.encode(
                EnforcedThingMapper.toWriteModel(thing, policy, referencedPolicies, params.policyRevision(),
                        oldMetadata, params.maxArraySize()).getDocument());
    }

    /**
     * Builds the "old" {@link Metadata} passed to {@code EnforcedThingMapper.toWriteModel}, if the case
     * defines {@code oldReferencedPolicies} (used to exercise the "policy no longer directly referenced
     * but still imported" retention branch). {@code null} otherwise, matching how the very first update for
     * a thing has no previous metadata.
     */
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
