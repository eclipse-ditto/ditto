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
package org.eclipse.ditto.services.utils.cleanup;

import java.util.Optional;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.persistence.SelectedSnapshot;
import akka.persistence.SnapshotMetadata;
import akka.persistence.SnapshotSelectionCriteria;
import akka.persistence.snapshot.japi.SnapshotStore;
import scala.concurrent.Future;

/**
 * Mock implementation of {@link SnapshotStore} that logs invocations and forwards invocations to delete* methods to
 * another implementation e.g. a Mockito mock to verify invocations.
 */
public class MockSnapshotStorePlugin extends SnapshotStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockSnapshotStorePlugin.class);

    @Nullable private static SnapshotStore mock = null;

    static void setMock(final SnapshotStore journal) {
        mock = journal;
    }

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
        return getMock().doDeleteAsync(metadata);
    }

    @Override
    public Future<Void> doDeleteAsync(final String persistenceId, final SnapshotSelectionCriteria criteria) {
        LOGGER.debug("[doDeleteAsync]: {} -> {}", persistenceId, criteria);
        return getMock().doDeleteAsync(persistenceId, criteria);
    }

    private SnapshotStore getMock() {
        return Optional.ofNullable(mock).orElseThrow(() -> new IllegalStateException("Forgot to set mock?"));
    }
}
