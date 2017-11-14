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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.when;

import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcers;
import org.eclipse.ditto.services.thingsearch.persistence.MongoClientWrapper;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;

import com.mongodb.bulk.BulkWriteResult;
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
public class MongoDBMockSearchUpdaterPersistenceTest {

    private MongoThingsSearchUpdaterPersistence persistence;
    private MongoCollection thingsCollection;

    private ActorSystem actorSystem;
    private ActorMaterializer actorMaterializer;

    @Before
    public void init() throws InterruptedException {
        actorSystem = ActorSystem.create("AkkaTestSystem");
        actorMaterializer = ActorMaterializer.create(actorSystem);

        final LoggingAdapter loggingAdapter = Mockito.mock(LoggingAdapter.class);
        final MongoClientWrapper clientWrapper = Mockito.mock(MongoClientWrapper.class);
        final MongoDatabase db = Mockito.mock(MongoDatabase.class);
        thingsCollection = Mockito.mock(MongoCollection.class);
        final MongoCollection policiesCollection = Mockito.mock(MongoCollection.class);

        when(clientWrapper.getDatabase())
                .thenReturn(db);
        when(db.getCollection(PersistenceConstants.THINGS_COLLECTION_NAME))
                .thenReturn(thingsCollection);
        when(db.getCollection(PersistenceConstants.POLICIES_BASED_SEARCH_INDEX_COLLECTION_NAME))
                .thenReturn(policiesCollection);

        persistence = new MongoThingsSearchUpdaterPersistence(clientWrapper, loggingAdapter);
    }

    @After
    public void shutdown() {
        actorSystem.terminate();
    }

    @Test
    public void testExceptionHandling() {

        final PolicyEnforcer policyEnforcer = PolicyEnforcers.defaultEvaluator(
                PoliciesModelFactory.newPolicyBuilder(":theThing").build());
        final CombinedThingWrites combinedThingWrites = CombinedThingWrites.newBuilder(Mockito.mock(LoggingAdapter.class),
                1L,
                policyEnforcer).build();

        when(thingsCollection.bulkWrite(anyList(), any(BulkWriteOptions.class)))
                .thenReturn((Publisher<BulkWriteResult>) subscriber
                        -> subscriber.onError(new RuntimeException("db error")));

        final Source<Boolean, NotUsed> source = persistence.executeCombinedWrites(":theThing",
                combinedThingWrites);

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> expectError(source))
                .withNoCause()
                .withMessage("db error");
    }

    private <T> void expectError(final Source<T, NotUsed> source) throws Throwable {
        throw source.runWith(TestSink.probe(actorSystem), actorMaterializer).expectSubscriptionAndError();
    }
}
