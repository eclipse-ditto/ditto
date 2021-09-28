/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.persistence.SelectedSnapshot;
import akka.persistence.SnapshotMetadata;
import akka.persistence.SnapshotSelectionCriteria;
import akka.persistence.snapshot.japi.SnapshotStore;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

/**
 * Mock implementation of {@link SnapshotStore} that logs invocations and forwards invocations to delete* methods to
 * another implementation e.g. a Mockito mock to verify invocations.
 */
public class MockSnapshotStorePlugin extends SnapshotStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockSnapshotStorePlugin.class);
    static final String FAIL_DELETE_SNAPSHOT = "thing:namespace:failDeleteSnapshot";

    private static SnapshotStore snapshotStore = Mockito.mock(SnapshotStore.class);

    @Override
    public Future<Optional<SelectedSnapshot>> doLoadAsync(final String persistenceId,
            final SnapshotSelectionCriteria criteria) {
        // return something - not relevant for current tests
        final SnapshotMetadata metadata = new SnapshotMetadata(persistenceId, 0, 0);
        final SelectedSnapshot selectedSnapshot = SelectedSnapshot.apply(metadata, 1L);
        return Future.successful(Optional.of(selectedSnapshot));
    }

    @Override
    public Future<Void> doSaveAsync(final SnapshotMetadata metadata, final Object snapshot) {
        LOGGER.debug("[doSaveAsync]: {} -> {}", metadata, snapshot);
        return Future.successful(null);
    }

    @Override
    public Future<Void> doDeleteAsync(final SnapshotMetadata metadata) {
        LOGGER.debug("[doDeleteAsync]: {}", metadata);
        snapshotStore.doDeleteAsync(metadata);
        if (FAIL_DELETE_SNAPSHOT.equals(metadata.persistenceId())) {
            final CompletableFuture<Void> failDeleteSnapshot = new CompletableFuture<>();
            failDeleteSnapshot.completeExceptionally(new IllegalStateException(FAIL_DELETE_SNAPSHOT));
            return FutureConverters.toScala(failDeleteSnapshot);
        } else {
            return Future.successful(null);
        }
    }

    @Override
    public Future<Void> doDeleteAsync(final String persistenceId, final SnapshotSelectionCriteria criteria) {
        LOGGER.debug("[doDeleteAsync]: {} -> {}", persistenceId, criteria);
        snapshotStore.doDeleteAsync(persistenceId, criteria);
        if (FAIL_DELETE_SNAPSHOT.equals(persistenceId)) {
            final CompletableFuture<Void> failDeleteSnapshot = new CompletableFuture<>();
            failDeleteSnapshot.completeExceptionally(new IllegalStateException(FAIL_DELETE_SNAPSHOT));
            return FutureConverters.toScala(failDeleteSnapshot);
        } else {
            return Future.successful(null);
        }
    }

    static void verify(final String persistenceId, final int toSequenceNr) {
        Mockito.verify(snapshotStore, Mockito.timeout(10000L).times(1))
                .doDeleteAsync(eq(persistenceId), argThat(matchesCriteria(toSequenceNr)));
    }

    static void reset() {
        Mockito.reset(snapshotStore);
    }

    private static ArgumentMatcher<SnapshotSelectionCriteria> matchesCriteria(final long maxSequenceNumber) {
        return arg -> arg != null && maxSequenceNumber == arg.maxSequenceNr();
    }
}
