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
package org.eclipse.ditto.thingsearch.service.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.bson.BsonArray;
import org.bson.Document;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.thingsearch.service.persistence.read.criteria.visitors.CreateBsonVisitor;
import org.eclipse.ditto.thingsearch.service.persistence.write.mapping.JsonToBson;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.scala.MongoClient;

import com.typesafe.config.ConfigFactory;

/**
 * Tests {@link MongoSearchPersistenceProvider#compositeSortResumeValue(JsonValue)}: the resume value of an object or
 * array cursor sort value is its BSON form, so MongoDB's resume criteria stay byte-identical.
 */
public final class MongoCursorResumeValueTest {

    private ActorSystem system;
    private MongoSearchPersistenceProvider underTest;

    @Before
    public void init() {
        system = ActorSystem.create(MongoCursorResumeValueTest.class.getSimpleName());
        underTest = new MongoSearchPersistenceProvider(system, ConfigFactory.empty());
    }

    @After
    public void stop() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Test
    public void objectResumeValueRendersAsNestedGt() {
        final CriteriaFactory cf = CriteriaFactory.getInstance();
        final ThingsFieldExpressionFactory fef = ThingsFieldExpressionFactory.of(Map.of("thingId", "_id"));
        final Object resumeValue = underTest.compositeSortResumeValue(JsonObject.of("{\"key\":\"value\"}"));

        final Criteria criteria = cf.fieldCriteria(fef.sortByAttribute("sortKey"), cf.gt(resumeValue));
        final var bson = CreateBsonVisitor.sudoApply(criteria)
                .toBsonDocument(Document.class, MongoClient.DEFAULT_CODEC_REGISTRY());

        assertThat(bson.toJson().replaceAll("\\s", ""))
                .contains("{\"t.attributes.sortKey\":{\"$gt\":{\"key\":\"value\"}}}");
    }

    @Test
    public void arrayResumeValueIsBsonArray() {
        final JsonArray array = JsonArray.of(1, "a");

        final Object resumeValue = underTest.compositeSortResumeValue(array);

        assertThat(resumeValue).isInstanceOf(BsonArray.class).isEqualTo(JsonToBson.convert(array));
    }

}
