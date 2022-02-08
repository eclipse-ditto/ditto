/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.actors;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DData;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Timers;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import akka.cluster.ddata.ORMultiMap;
import akka.cluster.ddata.Replicator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * Mixin to subscribe for cluster events.
 */
interface ClusterStateSyncBehavior<T> extends Actor, Timers {

    /**
     * Get the cluster object of the actor system.
     *
     * @return the cluster object.
     */
    Cluster getCluster();

    /**
     * Convert the distributed data key into an address.
     *
     * @param ddataKey the key.
     * @return the address.
     */
    Address toAddress(T ddataKey);

    /**
     * @return the logging adapter of this actor. Must be thread-safe.
     */
    ThreadSafeDittoLoggingAdapter log();

    /**
     * @return the distributed data to maintain. Must be thread-safe.
     */
    DData<T, ?, ?> getDData();

    /**
     * Verify that no distributed data entry is expected for the current member and trigger an update if it is expected.
     * Called if the distributed data entry for the current member is missing.
     */
    void verifyNoDDataForCurrentMember();

    /**
     * Schedule periodic sync between the distributed data and the cluster state.
     *
     * @param config the pub-sub config.
     */
    default void scheduleClusterStateSync(final PubSubConfig config) {
        final var syncInterval = config.getSyncInterval();
        final var randomizedSyncInterval =
                syncInterval.plus(Duration.ofMillis((long) (Math.random() * syncInterval.toMillis())));
        log().info("Scheduling cluster state sync at <{}> interval (min=<{}>)", randomizedSyncInterval, syncInterval);
        final var trigger = Control.SYNC_CLUSTER_STATE;
        timers().startTimerWithFixedDelay(trigger, trigger, randomizedSyncInterval);
    }

    /**
     * Create behavior related to cluster state sync.
     *
     * @return The behavior.
     */
    default AbstractActor.Receive getClusterStateSyncBehavior() {
        return ReceiveBuilder.create()
                .matchEquals(Control.SYNC_CLUSTER_STATE, this::syncClusterState)
                .match(SyncResult.class, this::handleSyncResult)
                .match(SyncError.class, this::handleSyncError)
                .build();
    }

    /**
     * Start cluster state sync.
     *
     * @param trigger The trigger.
     */
    default void syncClusterState(final Control trigger) {
        log().info("Start to sync cluster state");

        final var self = self();
        final var resultFuture = getDData().getReader()
                .getAllShards((Replicator.ReadConsistency) Replicator.readLocal())
                .thenApply(maps -> checkClusterState(maps, self))
                .handle((result, error) -> result != null ? result : new SyncError(error));

        Patterns.pipe(resultFuture, context().dispatcher()).to(self());
    }

    /**
     * Handle sync error.
     *
     * @param syncError The error that aborted cluster state sync.
     */
    default void handleSyncError(final SyncError syncError) {
        log().error(syncError.error, "Failed to sync cluster state");
    }

    /**
     * Handle the result of a successful sync.
     *
     * @param syncResult The sync result containing stale and missing addresses.
     */
    default void handleSyncResult(final SyncResult syncResult) {
        if (syncResult.isInSync()) {
            log().info("DData is in sync with cluster state");
        } else {
            log().info("Sync result: <{}>", syncResult);
            if (syncResult.myAddressMissing) {
                // This can happen during normal operation if no subscription is present for the current member.
                log().info("Checking missing info of current member <{}>", getCluster().selfMember());
                verifyNoDDataForCurrentMember();
            }
            if (!syncResult.staleAddresses.isEmpty()) {
                log().warning("Removing stale addresses <{}>", syncResult.staleAddresses);
                removeStaleAddresses(syncResult.staleAddresses);
            }
        }
    }

    /**
     * Remove stale addresses from the distributed data.
     *
     * @param staleAddresses the stale address.
     */
    default void removeStaleAddresses(final Set<Address> staleAddresses) {
        final var writer = getDData().getWriter();
        for (final var address : staleAddresses) {
            writer.removeAddress(address, writeLocal());
        }
    }

    /**
     * Compare distributed data addresses against the current cluster state.
     *
     * @param maps The content of the distributed data.
     * @param self the self actor reference.
     * @return result of comparing distributed data addresses against the cluster state.
     */
    default SyncResult checkClusterState(final List<? extends ORMultiMap<T, ?>> maps, final ActorRef self) {
        final var clusterState = getCluster().state();
        final var clusterAddresses = getClusterMemberAddresses(clusterState, self);
        final var ddataAddresses = getDDataAddresses(maps);
        final var isSelfMemberInCluster = isMemberStayingInCluster(getCluster().selfMember());
        if (isSelfMemberInCluster) {
            final boolean isMyAddressMissing = !ddataAddresses.contains(getCluster().selfAddress()) &&
                    !ddataAddresses.contains(self.path().address());
            final Set<Address> staleAddresses = ddataAddresses.stream()
                    .filter(address -> !clusterAddresses.contains(address))
                    .collect(Collectors.toSet());
            return new SyncResult(isMyAddressMissing, staleAddresses);
        } else {
            log().info("This member is leaving the cluster. Skipping sync.");
            return new SyncResult(false, Set.of());
        }
    }

    /**
     * Retrieve addresses saved in distributed data.
     *
     * @param maps Maps stored in the distributed data.
     * @return Address saved in distributed data.
     */
    default Set<Address> getDDataAddresses(final List<? extends ORMultiMap<T, ?>> maps) {
        return maps.stream()
                .flatMap(orMultiMap -> orMultiMap.getEntries().keySet().stream())
                .map(this::toAddress)
                .collect(Collectors.toSet());
    }

    /**
     * Get the local write consistency.
     *
     * @return the local write consistency.
     */
    default Replicator.WriteConsistency writeLocal() {
        return (Replicator.WriteConsistency) Replicator.writeLocal();
    }

    /**
     * Check if a cluster member is staying in the cluster by its status.
     *
     * @param member The cluster member.
     * @return Whether it is staying in the cluster.
     */
    static boolean isMemberStayingInCluster(final Member member) {
        final var status = member.status();
        return status != MemberStatus.leaving() && status != MemberStatus.exiting() && status != MemberStatus.down() &&
                status != MemberStatus.removed();
    }

    /**
     * Retrieve a set of addresses of cluster members that will stay in the cluster.
     * Enhance the address set with the unserialized self address.
     *
     * @param clusterState The cluster state.
     * @param self reference of this actor.
     * @return The addresses of members staying in the cluster.
     */
    static Set<Address> getClusterMemberAddresses(final ClusterEvent.CurrentClusterState clusterState,
            final ActorRef self) {
        final var clusterMemberStream = StreamSupport.stream(clusterState.getMembers().spliterator(), false)
                .filter(ClusterStateSyncBehavior::isMemberStayingInCluster)
                .map(Member::address);
        return Stream.concat(clusterMemberStream, Stream.of(self.path().address())).collect(Collectors.toSet());
    }

    /**
     * Message to inform this actor of the result of syncing against the cluster state.
     */
    final class SyncResult {

        private final boolean myAddressMissing;
        private final Set<Address> staleAddresses;

        private SyncResult(final boolean myAddressMissing, final Set<Address> staleAddresses) {
            this.myAddressMissing = myAddressMissing;
            this.staleAddresses = staleAddresses;
        }

        private boolean isInSync() {
            return !myAddressMissing && staleAddresses.isEmpty();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "myAddressMissing=" + myAddressMissing +
                    ", staleAddresses=" + staleAddresses +
                    "]";
        }
    }

    /**
     * Message indicating error during syncing against the cluster state containing the cause.
     */
    final class SyncError {

        private final Throwable error;

        private SyncError(final Throwable error) {
            this.error = error;
        }
    }

    /**
     * Internal messages of this behavior.
     */
    enum Control {

        /**
         * Message to trigger sync of the distributed data state against the cluster state.
         */
        SYNC_CLUSTER_STATE
    }
}
