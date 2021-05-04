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
package org.eclipse.ditto.policies.service.persistence.testhelper;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.BsonDocument;
import org.eclipse.ditto.policies.model.PolicyId;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.persistence.Persistence;
import akka.persistence.SelectedSnapshot;
import akka.persistence.SnapshotProtocol;
import akka.persistence.SnapshotSelectionCriteria;
import scala.Option;

/**
 * Helper class which provides functionality for testing with Akka persistence snapshots for the policies
 * services.
 * Requires akka-persistence-inmemory (by com.github.dnvriend).
 *
 * @param <S> the domain specific datatype stored as snapshot
 */
public final class PoliciesSnapshotTestHelper<S> {

    private static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-policies-snapshots";
    private static final int WAIT_TIMEOUT = 3;

    private final Function<PolicyId, String> domainIdToPersistenceId;
    private final BiFunction<BsonDocument, Long, S> snapshotToDomainObject;
    private final ActorRef snapshotPlugin;

    /**
     * Constructor.
     *
     * @param actorSystem the actor system to be used to find the persistence extension
     * @param snapshotToDomainObject a {@link java.util.function.BiFunction} providing the snapshot and its sequence number and expecting a
     * domain object
     * @param domainIdToPersistenceId a {@link java.util.function.Function} providing the domain ID and expecting the matching persistence
     * ID
     */
    public PoliciesSnapshotTestHelper(final ActorSystem actorSystem,
            final BiFunction<BsonDocument, Long, S> snapshotToDomainObject,
            final Function<PolicyId, String> domainIdToPersistenceId) {
        this.snapshotToDomainObject = requireNonNull(snapshotToDomainObject);
        this.domainIdToPersistenceId = requireNonNull(domainIdToPersistenceId);

        snapshotPlugin =
                Persistence.get(actorSystem).snapshotStoreFor(SNAPSHOT_PLUGIN_ID, ConfigFactory.empty());
    }


    /**
     * Gets the maximum snapshot, if any exists.
     *
     * @param domainId the domain ID of the snapshot
     * @return an Optional containing the maximum snapshot, if any exists; an empty Optional otherwise
     */
    public Optional<S> getMaxSnapshot(final PolicyId domainId) {
        requireNonNull(domainId);

        final String persistenceId = domainIdToPersistenceId.apply(domainId);
        final SelectedSnapshot maxSnapshotData = getMaxSnapshotData(persistenceId);

        return Optional.ofNullable(maxSnapshotData).map(this::convertSnapshotDataToDomainObject);
    }

    /**
     * Gets all snapshots in ascending order.
     *
     * @param domainId the domain ID of the snapshots
     * @return the snapshots in ascending orders
     */
    public List<S> getAllSnapshotsAscending(final PolicyId domainId) {
        requireNonNull(domainId);

        final String persistenceId = domainIdToPersistenceId.apply(domainId);
        final SelectedSnapshot maxSnapshotData = getMaxSnapshotData(persistenceId);

        return Optional.ofNullable(maxSnapshotData)
                .map(this::getAllSnapshotDataAscending)
                .map(allSnapshotData ->
                        allSnapshotData.stream()
                                .map(this::convertSnapshotDataToDomainObject)
                                .collect(Collectors.toList()))
                .map(Collections::unmodifiableList)
                .orElse(Collections.emptyList());
    }

    private S convertSnapshotDataToDomainObject(final SelectedSnapshot snapshotData) {
        final BsonDocument dbObject = (BsonDocument) snapshotData.snapshot();
        return snapshotToDomainObject.apply(dbObject, snapshotData.metadata().sequenceNr());
    }

    private List<SelectedSnapshot> getAllSnapshotDataAscending(final SelectedSnapshot maxSnapshotData) {
        // this method is a bit complicated, but I there currently is no easier way to get all snaphots
        final List<SelectedSnapshot> allSnapshotData = new ArrayList<>();
        allSnapshotData.add(maxSnapshotData);
        SelectedSnapshot lastSnapshotData = maxSnapshotData;
        while (lastSnapshotData != null) {
            final SelectedSnapshot previousSnapshotData =
                    getMaxSnapshotData(lastSnapshotData.metadata().persistenceId(),
                            lastSnapshotData.metadata().timestamp() - 1);
            if (previousSnapshotData != null) {
                allSnapshotData.add(previousSnapshotData);
            }
            lastSnapshotData = previousSnapshotData;
        }
        Collections.reverse(allSnapshotData);
        return allSnapshotData;
    }

    private SelectedSnapshot getMaxSnapshotData(final String persistenceId, final long maxTimestamp) {
        final SnapshotProtocol.LoadSnapshot loadSnapshot =
                new SnapshotProtocol.LoadSnapshot(persistenceId,
                        SnapshotSelectionCriteria.create(Long.MAX_VALUE, maxTimestamp), Long.MAX_VALUE);

        return convertScalaOpt(
                waitForFuture(
                        Patterns.ask(snapshotPlugin, loadSnapshot, Duration.ofSeconds(WAIT_TIMEOUT))
                                .thenApply(obj -> (SnapshotProtocol.LoadSnapshotResult) obj)
                                .thenApply(SnapshotProtocol.LoadSnapshotResult::snapshot)
                                .toCompletableFuture()
                ), null);
    }

    private SelectedSnapshot getMaxSnapshotData(final String persistenceId) {
        return getMaxSnapshotData(persistenceId, Long.MAX_VALUE);
    }

    private <T> T convertScalaOpt(final Option<T> opt, final T defaultValue) {
        return opt.isDefined() ? opt.get() : defaultValue;
    }

    private <T> T waitForFuture(
            final java.util.concurrent.Future<T> resultFuture) {
        try {
            return resultFuture.get(WAIT_TIMEOUT, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
