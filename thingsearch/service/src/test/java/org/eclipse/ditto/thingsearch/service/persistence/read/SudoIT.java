/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.read;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.internal.models.streaming.LowerBound;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.FieldExpressionFactory;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.SearchNamespaceReportResult;
import org.eclipse.ditto.thingsearch.service.persistence.TestConstants;
import org.eclipse.ditto.thingsearch.service.persistence.read.query.MongoQueryBuilderFactory;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import akka.stream.javadsl.Sink;

/**
 * Test sudo methods.
 */
public final class SudoIT extends AbstractReadPersistenceITBase {

    private static final ThingId THING1_ID =
            TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thing1");
    private static final ThingId THING2_ID =
            TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thing2");
    private static final Instant TIMESTAMP1 = Instant.ofEpochSecond(1L);
    private static final Instant TIMESTAMP2 = Instant.ofEpochSecond(2L);

    private final FieldExpressionFactory ef =
            ThingsFieldExpressionFactory.of(Map.of("policyId", "policyId", "_modified", "_modified"));
    private final CriteriaFactory cf = CriteriaFactory.getInstance();
    private final QueryBuilderFactory qbf = new MongoQueryBuilderFactory(Mockito.mock(LimitsConfig.class));

    @Before
    public void createTestData() {
        insertThings();
    }

    @Test
    public void generateNamespaceCountReport() {
        final SearchNamespaceReportResult namespaceReportResult =
                readPersistence.generateNamespaceCountReport()
                        .runWith(Sink.head(), actorSystem)
                        .toCompletableFuture()
                        .join();

        assertThat(namespaceReportResult.toJson())
                .containsExactly(JsonField.newInstance("namespaces", JsonArray.of(JsonObject.newBuilder()
                        .set("namespace", TestConstants.Thing.NAMESPACE)
                        .set("count", 2)
                        .build()
                )));
    }

    @Test
    public void sudoCount() {
        final Query anyQuery = qbf.newUnlimitedBuilder(cf.any()).build();
        assertThat(waitFor(readPersistence.sudoCount(anyQuery))).containsExactly(2L);

        final Query queryByPolicyId =
                qbf.newUnlimitedBuilder(
                        cf.fieldCriteria(ef.filterBy("policyId"), cf.eq(THING2_ID.toString())))
                        .build();
        assertThat(waitFor(readPersistence.sudoCount(queryByPolicyId))).containsExactly(1L);

        final Query matchNothingQuery =
                qbf.newUnlimitedBuilder(cf.existsCriteria(ef.existsBy("attributes/nonexistent"))).build();
        assertThat(waitFor(readPersistence.sudoCount(matchNothingQuery))).containsExactly(0L);
    }

    @Test
    public void sudoStreamMetadata() {
        final Metadata metadata1 =
                Metadata.of(THING1_ID, 1L, PolicyTag.of(PolicyId.of(THING1_ID), 0L), Set.of(), TIMESTAMP1, null);
        final Metadata metadata2 =
                Metadata.of(THING2_ID, 2L, PolicyTag.of(PolicyId.of(THING2_ID), 0L), Set.of(), TIMESTAMP2, null);
        assertThat(waitFor(readPersistence.sudoStreamMetadata(LowerBound.emptyEntityId(ThingConstants.ENTITY_TYPE))))
                .containsExactly(metadata1, metadata2);

        assertThat(waitFor(readPersistence.sudoStreamMetadata(THING1_ID)))
                .containsExactly(metadata2);

        assertThat(waitFor(readPersistence.sudoStreamMetadata(THING2_ID)))
                .isEmpty();
    }

    private void insertThings() {
        persistThing(createThing(THING1_ID).toBuilder()
                .setModified(TIMESTAMP1)
                .setRevision(1L)
                .setPolicyId(PolicyId.of(THING1_ID))
                .build());
        persistThing(createThing(THING2_ID).toBuilder()
                .setModified(TIMESTAMP2)
                .setRevision(2L)
                .setPolicyId(PolicyId.of(THING2_ID))
                .build());
    }

}
