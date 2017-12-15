/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcers;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.write.EventToPersistenceStrategyFactory;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Source;
import akka.stream.testkit.javadsl.TestSink;

/**
 * Mocks the connection to MongoDB to be able to test handling of exceptions and other stuff not possible with real
 * MongoDB client.
 */
public final class MongoDBMockSearchUpdaterPersistenceTest {

    private MongoThingsSearchUpdaterPersistence persistence;
    private MongoCollection<Document> thingsCollection;

    private ActorSystem actorSystem;
    private ActorMaterializer actorMaterializer;

    private final EventToPersistenceStrategyFactory<Bson, PolicyUpdate> eventToPersistenceStrategyFactory =
            MongoEventToPersistenceStrategyFactory.getInstance();

    @Before
    public void init() {
        actorSystem = ActorSystem.create("AkkaTestSystem");
        actorMaterializer = ActorMaterializer.create(actorSystem);

        final LoggingAdapter loggingAdapter = mock(LoggingAdapter.class);
        final MongoClientWrapper clientWrapper = mock(MongoClientWrapper.class);
        final MongoDatabase db = mock(MongoDatabase.class);

        thingsCollection = createMockCollection();
        final MongoCollection<Document> policiesCollection = createMockCollection();

        when(clientWrapper.getDatabase())
                .thenReturn(db);
        when(db.getCollection(PersistenceConstants.THINGS_COLLECTION_NAME))
                .thenReturn(thingsCollection);
        when(db.getCollection(PersistenceConstants.POLICIES_BASED_SEARCH_INDEX_COLLECTION_NAME))
                .thenReturn(policiesCollection);

        persistence =
                new MongoThingsSearchUpdaterPersistence(clientWrapper, loggingAdapter, eventToPersistenceStrategyFactory);
    }

    @SuppressWarnings("unchecked")
    private static MongoCollection<Document> createMockCollection() {
        return mock(MongoCollection.class);
    }

    @After
    public void shutdown() {
        actorSystem.terminate();
    }

    @Test
    public void testExceptionHandling() {
        final PolicyEnforcer policyEnforcer = PolicyEnforcers.defaultEvaluator(
                PoliciesModelFactory.newPolicyBuilder(":theThing").build());
        final ThingEvent attributeCreated = AttributeCreated.of(":t", JsonPointer.empty(), JsonValue.of(1), 1L,
                DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.LATEST).build());
        final List<ThingEvent> thingEvents = Collections.singletonList(attributeCreated);

        final String mockErrorMessage = "db mock error";
        when(thingsCollection.bulkWrite(anyList(), any(BulkWriteOptions.class)))
                .thenReturn(subscriber -> subscriber.onError(new RuntimeException(mockErrorMessage)));

        final Source<Boolean, NotUsed> source = persistence.executeCombinedWrites(":theThing",
                thingEvents, policyEnforcer, 1L);

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> expectError(source))
                .withNoCause()
                .withMessage(mockErrorMessage);
    }

    private <T> void expectError(final Source<T, NotUsed> source) throws Throwable {
        throw source.runWith(TestSink.probe(actorSystem), actorMaterializer).expectSubscriptionAndError();
    }
}
