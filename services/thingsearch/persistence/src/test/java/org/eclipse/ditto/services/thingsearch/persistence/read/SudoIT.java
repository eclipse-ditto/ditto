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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.query.QueryBuilderFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.FieldExpressionFactory;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.models.thingsearch.SearchNamespaceReportResult;
import org.eclipse.ditto.services.thingsearch.persistence.TestConstants;
import org.eclipse.ditto.services.thingsearch.persistence.read.query.MongoQueryBuilderFactory;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.Metadata;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import akka.stream.javadsl.Sink;

/**
 * Test sudo methods.
 */
public final class SudoIT extends AbstractReadPersistenceITBase {

    private static final ThingId THING1_ID = TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thing1");
    private static final ThingId THING2_ID = TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thing2");
    private static final Instant TIMESTAMP1 = Instant.ofEpochSecond(1L);
    private static final Instant TIMESTAMP2 = Instant.ofEpochSecond(2L);

    private final FieldExpressionFactory ef =
            new ThingsFieldExpressionFactoryImpl(Map.of("policyId", "policyId", "_modified", "_modified"));
    private final CriteriaFactory cf = new CriteriaFactoryImpl();
    private final QueryBuilderFactory qbf = new MongoQueryBuilderFactory(Mockito.mock(LimitsConfig.class));

    @Before
    public void createTestData() {
        insertThings();
    }

    @Test
    public void generateNamespaceCountReport() {
        final SearchNamespaceReportResult namespaceReportResult =
                readPersistence.generateNamespaceCountReport()
                        .runWith(Sink.head(), actorMaterializer)
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
        final Metadata metadata1 = Metadata.of(THING1_ID, 1L, THING1_ID.toString(), 0L, TIMESTAMP1);
        final Metadata metadata2 = Metadata.of(THING2_ID, 2L, THING2_ID.toString(), 0L, TIMESTAMP2);
        assertThat(waitFor(readPersistence.sudoStreamMetadata(ThingId.dummy())))
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
