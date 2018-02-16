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
package org.eclipse.ditto.services.utils.persistence.mongo.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.utils.persistence.mongo.indices.Index;
import org.eclipse.ditto.services.utils.persistence.mongo.indices.IndexOperations;

import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.stream.Materializer;
import akka.stream.javadsl.Sink;

/**
 * Assertion methods for Mongo indices.
 */
public class MongoIndexAssertions {

    private static final Comparator<Index> INDEX_INFO_SORTER =
            Comparator.comparing(Index::getName);

    private static final String INDEX_NAME_ID = "_id_";

    /**
     * Asserts that the specified Mongo collection contains the expected indices. Ignores the default index (on field
     * "_id").
     *
     * @param db the Mongo database
     * @param materializer the materializer for the akka actor system.
     * @param expectedIndices the expected indices
     * @see #assertIndices(MongoDatabase, String, Materializer, Collection, boolean)
     */
    public static void assertIndices(final MongoDatabase db, final String collectionName,
            final Materializer materializer, final Collection<Index> expectedIndices) {
        assertIndices(db, collectionName, materializer, expectedIndices, true);
    }

    /**
     * Asserts that the specified Mongo collection contains the expected indices.
     *
     * @param db the Mongo database
     * @param materializer the materializer for the akka actor system.
     * @param expectedIndices the expected indices
     * @param ignoreDefaultIndex defines whether the default index (on field "_id") should be excluded from the
     * comparison
     */
    public static void assertIndices(final MongoDatabase db, final String collectionName,
            final Materializer materializer, final Collection<Index> expectedIndices,
            final boolean ignoreDefaultIndex) {
        final IndexOperations indexOperations = IndexOperations.of(db);

        final Supplier<List<Index>> actualIndicesSupplier;
        final Collection<Index> expectedIndicesToCheck;
        if (ignoreDefaultIndex) {
            actualIndicesSupplier = () -> {
                final CompletionStage<List<Index>> completionStage = indexOperations
                        .getIndicesExceptDefaultIndex(collectionName).runWith(Sink.head(), materializer);
                return runBlocking(completionStage);
            };
            expectedIndicesToCheck = removeDefaultIndex(expectedIndices);
        } else {
            actualIndicesSupplier = () -> {
                final CompletionStage<List<Index>> completionStage = indexOperations
                        .getIndices(collectionName).runWith(Sink.head(), materializer);
                return runBlocking(completionStage);
            };
            expectedIndicesToCheck = expectedIndices;
        }

        assertIndices(actualIndicesSupplier, expectedIndicesToCheck);
    }

    private static void assertIndices(final Supplier<List<Index>> indicesSupplier, final Collection<Index>
            expectedIndices) {
        final List<Index> actualIndices = indicesSupplier.get();
        // sort by name to be able to compare the lists
        final List<Index> actualIndexModelsSorted = new ArrayList<>(actualIndices);
        actualIndexModelsSorted.sort(INDEX_INFO_SORTER);
        final List<Index> expectedIndexModelsSorted = new ArrayList<>(expectedIndices);
        expectedIndexModelsSorted.sort(INDEX_INFO_SORTER);

        assertThat(actualIndexModelsSorted).isEqualTo(expectedIndexModelsSorted);
    }

    private static List<Index> removeDefaultIndex(final Collection<Index> indices) {
        return indices.stream()
                .filter(index -> !INDEX_NAME_ID.equals(index.getName()))
                .collect(Collectors.toList());
    }

    private static <T> T runBlocking(final CompletionStage<T> completionStage) {
        try {
            return completionStage.toCompletableFuture().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

}
