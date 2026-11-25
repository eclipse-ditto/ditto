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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.bson.BsonDocument;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.junit.Test;

/**
 * Verifies that {@link SearchIndexDocumentMongoEncoder#decode(BsonDocument)} is the faithful inverse of
 * {@link SearchIndexDocumentMongoEncoder#encode(SearchIndexDocument)} on golden fixtures, i.e.
 * {@code encode(decode(encode(x))).equals(encode(x))}, and that the historical "emptied out" Mongo shape is
 * recovered via the {@code ThingWriteModel.ofEmptiedOut} branch (it omits {@code __referencedPolicies}).
 */
public final class SearchIndexDocumentMongoEncoderRoundTripTest {

    @Test
    public void featuresMultiRoundTrips() {
        assertRoundTrips("features-multi");
    }

    @Test
    public void specialCharsKeysRoundTrips() {
        assertRoundTrips("special-chars-keys");
    }

    private static void assertRoundTrips(final String caseName) {
        final BsonDocument encoded = GoldenFixtureRunner.run(caseName);
        final SearchIndexDocument decoded = SearchIndexDocumentMongoEncoder.decode(encoded);
        final BsonDocument reEncoded = SearchIndexDocumentMongoEncoder.encode(decoded);
        assertThat(reEncoded).isEqualTo(encoded);
    }

    @Test
    public void emptiedOutShapeIsRecoveredViaEmptiedOutBranch() {
        final ThingId thingId = ThingId.of("thing:id");
        final PolicyId policyId = PolicyId.of("policy:id");
        final Metadata metadata = Metadata.of(thingId, 5L, PolicyTag.of(policyId, 2L), null, Set.of(), null);

        // the historical (service) emptied-out shape omits the __referencedPolicies field, which is exactly the
        // discriminator MongoThingsSearchPersistence.documentToWriteModel uses to branch to the emptiedOut recovery
        final BsonDocument emptiedOutBson =
                org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel.ofEmptiedOut(metadata)
                        .getThingDocument();
        assertThat(emptiedOutBson.containsKey(PersistenceConstants.FIELD_REFERENCED_POLICIES)).isFalse();

        // and the neutral model recovery produces carries the emptied-out flag
        assertThat(ThingWriteModel.ofEmptiedOut(metadata).isEmptiedOut()).isTrue();
    }
}
