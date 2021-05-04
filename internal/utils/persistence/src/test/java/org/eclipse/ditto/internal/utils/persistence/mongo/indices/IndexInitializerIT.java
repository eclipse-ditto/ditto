/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo.indices;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.persistence.mongo.assertions.MongoIndexAssertions;
import org.eclipse.ditto.internal.utils.test.mongo.MongoDbResource;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.mongodb.MongoCommandException;

import akka.Done;
import akka.actor.ActorSystem;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * MongoDB integration test for {@link IndexInitializer}.
 */
public final class IndexInitializerIT {

    @ClassRule
    public static final MongoDbResource MONGO_RESOURCE = new MongoDbResource();

    private static final int CONNECTION_POOL_MAX_SIZE = 5;
    private static final long CONNECTION_POOL_MAX_WAIT_TIME_SECS = 3L;

    private static final int MONGO_INDEX_OPTIONS_CONFLICT_ERROR_CODE = 85;

    private static final String FOO_FIELD = "foo";
    private static final String BAR_FIELD = "bar";
    private static final String BAZ_FIELD = "baz";

    private static final String FOO_KEY = "foo_key";
    private static final Index INDEX_FOO = IndexFactory.newInstanceWithCustomKeys(FOO_KEY,
            Collections.singletonList(DefaultIndexKey.of(FOO_FIELD, IndexDirection.ASCENDING)), false);
    private static final String FOO_DESCENDING_KEY = "foo_descending_key";
    private static final Index INDEX_FOO_DESCENDING =
            IndexFactory.newInstanceWithCustomKeys(FOO_DESCENDING_KEY,
                    Collections.singletonList(DefaultIndexKey.of(FOO_FIELD, IndexDirection.DESCENDING)), false);
    private static final Index INDEX_FOO_CONFLICTING_UNIQUE_OPTION =
            IndexFactory.newInstance(FOO_KEY, Collections.singletonList(FOO_FIELD), true);

    private static final String FOO_CONFLICTING_NAME = "foo_conflicting_name";
    private static final Index INDEX_FOO_CONFLICTING_NAME_OPTION =
            IndexFactory.newInstance(FOO_CONFLICTING_NAME, Collections.singletonList(FOO_FIELD), true);

    private static final String BAR_KEY = "bar_key";
    private static final Index INDEX_BAR = IndexFactory.newInstance(BAR_KEY,
            Collections.singletonList(BAR_FIELD), false);

    private static final String BAZ_KEY = "baz_key";
    private static final Index INDEX_BAZ = IndexFactory.newInstance(BAZ_KEY,
            Collections.singletonList(BAZ_FIELD), false);

    private static final String FOO_BAR_KEY = "foo_bar_key";
    private static final Index INDEX_FOO_BAR = IndexFactory.newInstanceWithCustomKeys(FOO_BAR_KEY,
            Arrays.asList(DefaultIndexKey.of(FOO_FIELD, IndexDirection.ASCENDING),
                    DefaultIndexKey.of(BAR_FIELD, IndexDirection.ASCENDING)), false);

    private ActorSystem system;
    private Materializer materializer;
    private DittoMongoClient mongoClient;
    private IndexInitializer indexInitializerUnderTest;
    private IndexOperations indexOperations;

    @Before
    public void before() {
        system = ActorSystem.create("AkkaTestSystem");
        materializer = SystemMaterializer.get(system).materializer();

        requireNonNull(MONGO_RESOURCE);
        requireNonNull(materializer);

        mongoClient = MongoClientWrapper.getBuilder()
                .hostnameAndPort(MONGO_RESOURCE.getBindIp(), MONGO_RESOURCE.getPort())
                .defaultDatabaseName(getClass().getSimpleName() + "-" + UUID.randomUUID().toString())
                .connectionPoolMaxSize(CONNECTION_POOL_MAX_SIZE)
                .connectionPoolMaxWaitTime(Duration.ofSeconds(CONNECTION_POOL_MAX_WAIT_TIME_SECS))
                .build();

        indexInitializerUnderTest = IndexInitializer.of(mongoClient.getDefaultDatabase(), materializer);
        indexOperations = IndexOperations.of(mongoClient.getDefaultDatabase());
    }

    @SuppressWarnings("ConstantConditions")
    @After
    public void after() {
        if (system != null) {
            materializer.shutdown();
            TestKit.shutdownActorSystem(system);
            system = null;
            materializer = null;
        }
        if (mongoClient != null) {
            mongoClient.getDefaultDatabase().drop();
        }
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @Test
    public void initializeSucceedsWhenNoIndicesExistYet() {
        final String collectionName = "noIndicesExistYet";
        final List<Index> indices = Arrays.asList(INDEX_FOO, INDEX_BAR);

        initialize(collectionName, indices);

        assertIndices(collectionName, indices);
    }

    @Test
    public void initializeSucceedsWhenNoIndicesAreDefined() {
        final String collectionName = "noIndicesAreDefined";
        final List<Index> noIndices = Collections.emptyList();

        initialize(collectionName, noIndices);

        assertIndices(collectionName, noIndices);
    }

    @Test
    public void initializeSucceedsWhenIndexWithDifferentIndexOrderIsCreated() {
        final String collectionName = "differentIndexOrder";
        final List<Index> indices = Collections.singletonList(INDEX_FOO);
        createIndices(collectionName, indices);
        assertIndices(collectionName, indices);

        // WHEN
        final List<Index> newIndices = Arrays.asList(INDEX_FOO, INDEX_FOO_DESCENDING);
        initialize(collectionName, newIndices);

        // THEN
        assertIndices(collectionName, newIndices);
    }

    @Test
    public void initializeSucceedsWhenIndexWithAdditionalFieldIsCreated() {
        final String collectionName = "additionalField";
        final List<Index> indices = Collections.singletonList(INDEX_FOO);
        createIndices(collectionName, indices);
        assertIndices(collectionName, indices);

        // WHEN
        final List<Index> newIndices = Arrays.asList(INDEX_FOO, INDEX_FOO_BAR);
        initialize(collectionName, newIndices);

        // THEN
        assertIndices(collectionName, newIndices);
    }

    @Test
    public void initializeIgnoresIndexCreateWhenConflictingIndexWithSameUniqueAlreadyExists() {
        // GIVEN
        final String collectionName = "conflictingIndexWithSameUnique";
        final List<Index> indices = Collections.singletonList(INDEX_FOO);
        createIndices(collectionName, indices);
        assertIndices(collectionName, indices);

        // WHEN / THEN
        final List<Index> newIndices = Arrays.asList(INDEX_BAR,
                INDEX_FOO_CONFLICTING_UNIQUE_OPTION, INDEX_BAZ);
        initialize(collectionName, newIndices);

        assertIndices(collectionName, Arrays.asList(INDEX_BAR,
                INDEX_FOO, INDEX_BAZ));
    }

    @Test
    public void initializeFailsWhenConflictingIndexWithSameNameAlreadyExists() {
        // GIVEN
        final String collectionName = "conflictingIndexWithSameName";
        final List<Index> indices = Collections.singletonList(INDEX_FOO);
        initialize(collectionName, indices);
        assertIndices(collectionName, indices);

        // WHEN / THEN
        final List<Index> newIndices = Arrays.asList(INDEX_BAR,
                INDEX_FOO_CONFLICTING_NAME_OPTION, INDEX_BAZ);
        assertThatExceptionOfType(CompletionException.class).isThrownBy(() -> initialize(collectionName, newIndices))
                .withCauseInstanceOf(MongoCommandException.class)
                .satisfies(e -> assertThat(((MongoCommandException) e.getCause()).getErrorCode())
                        .isEqualTo(MONGO_INDEX_OPTIONS_CONFLICT_ERROR_CODE));
        // verify that bar has been created nevertheless (cause it has been initialized before the error), in
        // contrast to baz
        assertIndices(collectionName, Arrays.asList(INDEX_BAR, INDEX_FOO));
    }

    @Test
    public void initializeSucceedsWhenNoConflictingIndicesExist() {
        // GIVEN
        final String collectionName = "noConflictingIndicesExist";
        final List<Index> indices = Collections.singletonList(INDEX_FOO);
        createIndices(collectionName, indices);
        assertIndices(collectionName, indices);

        // WHEN
        final List<Index> newIndices = Arrays.asList(INDEX_FOO, INDEX_BAR);
        initialize(collectionName, newIndices);

        // THEN
        final List<Index> expectedIndices = Arrays.asList(INDEX_FOO, INDEX_BAR);
        assertIndices(collectionName, expectedIndices);
    }

    @Test
    public void initializeDeletesUndefinedIndices() {
        // GIVEN
        final String collectionName = "deleteUndefinedIndices";
        final List<Index> indices = Collections.singletonList(INDEX_FOO);
        createIndices(collectionName, indices);
        assertIndices(collectionName, indices);

        // WHEN
        final List<Index> newIndices = Collections.singletonList(INDEX_BAR);
        initialize(collectionName, newIndices);

        // THEN
        assertIndices(collectionName, newIndices);
    }

    private void createIndices(final String collectionName, final Iterable<Index> indices) {
        requireNonNull(indexOperations);

        final CompletionStage<Done> completionStage = Source.from(indices)
                .flatMapConcat(index -> indexOperations.createIndex(collectionName, index))
                .runWith(Sink.ignore(), materializer);

        runBlocking(completionStage);
    }

    private void initialize(final String collectionName, final List<Index> indices) {
        requireNonNull(indexInitializerUnderTest);

        final CompletionStage<Void> completionStage =
                indexInitializerUnderTest.initialize(collectionName, indices);

        runBlocking(completionStage);
    }

    private void assertIndices(final String collectionName, final Collection<Index> expectedIndices) {
        MongoIndexAssertions.assertIndices(mongoClient.getDefaultDatabase(), collectionName, materializer,
                expectedIndices, true);
    }

    private static <T> void runBlocking(final CompletionStage<T> completionStage) {
        completionStage.toCompletableFuture().join();
    }

    private static RuntimeException mapToRuntimeException(final Throwable t) {
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        throw new IllegalStateException(t);
    }

}
