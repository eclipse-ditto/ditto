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
package org.eclipse.ditto.internal.utils.pubsub.actors;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabelNotUniqueException;
import org.eclipse.ditto.base.model.acks.PubSubTerminatedException;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.internal.utils.pubsub.api.AcksDeclared;
import org.eclipse.ditto.internal.utils.pubsub.api.DeclareAcks;
import org.eclipse.ditto.internal.utils.pubsub.api.LocalAcksChanged;
import org.eclipse.ditto.internal.utils.pubsub.api.ReceiveLocalAcks;
import org.eclipse.ditto.internal.utils.pubsub.api.ReceiveRemoteAcks;
import org.eclipse.ditto.internal.utils.pubsub.api.RemoteAcksChanged;
import org.eclipse.ditto.internal.utils.pubsub.api.RemoveSubscriberAcks;
import org.eclipse.ditto.internal.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DData;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.internal.utils.pubsub.ddata.ack.Grouped;
import org.eclipse.ditto.internal.utils.pubsub.ddata.ack.GroupedRelation;
import org.eclipse.ditto.internal.utils.pubsub.ddata.literal.LiteralUpdate;
import org.eclipse.ditto.json.JsonValue;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.Cluster;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.ORMultiMap;
import akka.cluster.ddata.Replicator;
import akka.japi.pf.ReceiveBuilder;

/**
 * Manage the local and remote ternary relation between actors, group names and declared acknowledgement labels.
 * In case of conflict, prefer data from the cluster member with a smaller address.
 */
public final class AckUpdater extends AbstractActorWithTimers implements ClusterStateSyncBehavior<Address>,
        ClusterMemberRemovedAware {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "ackUpdater";

    private final ThreadSafeDittoLoggingAdapter log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

    private final GroupedRelation<ActorRef, String> localAckLabels;
    private final Address ownAddress;
    private final DData<Address, String, LiteralUpdate> ackDData;
    private final java.util.Set<ActorRef> ddataChangeRecipients;
    private final java.util.Set<ActorRef> localChangeRecipients;
    private final Gauge ackSizeMetric;
    private final Map<Key<?>, Map<Address, List<Grouped<String>>>> cachedRemoteAcks;
    private final Cluster cluster;
    private final double resetProbability;

    private Map<String, Set<String>> remoteAckLabels = Map.of();
    private Map<String, Set<String>> remoteGroups = Map.of();
    private LiteralUpdate previousUpdate = LiteralUpdate.empty();

    private AckUpdater(final PubSubConfig config,
            final Address ownAddress,
            final DData<Address, String, LiteralUpdate> ackDData) {
        this.ownAddress = ownAddress;
        this.ackDData = ackDData;
        localAckLabels = GroupedRelation.create();
        ddataChangeRecipients = new HashSet<>();
        localChangeRecipients = new HashSet<>();
        ackSizeMetric = DittoMetrics.gauge("pubsub-ack-size-bytes");
        cachedRemoteAcks = new HashMap<>();
        cluster = Cluster.get(getContext().getSystem());
        resetProbability = config.getResetProbability();
        subscribeForClusterMemberRemovedAware();
        ackDData.getReader().receiveChanges(getSelf());
        getTimers().startTimerAtFixedRate(Clock.TICK, Clock.TICK, config.getUpdateInterval());
        scheduleClusterStateSync(config);
    }

    /**
     * Create Props object for this actor.
     *
     * @param config the pub-sub config.
     * @param ownAddress address of the cluster member on which this actor lives.
     * @param ackDData access to the distributed data of declared acknowledgement labels.
     * @return the Props object.
     */
    public static Props props(final PubSubConfig config, final Address ownAddress,
            final DData<Address, String, LiteralUpdate> ackDData) {
        return Props.create(AckUpdater.class, config, ownAddress, ackDData);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(DeclareAcks.class, this::declare)
                .match(Terminated.class, this::terminated)
                .match(RemoveSubscriberAcks.class, this::removeSubscriber)
                .match(ReceiveRemoteAcks.class, this::onReceiveDDataChanges)
                .match(ReceiveLocalAcks.class, this::onReceiveLocalChanges)
                .matchEquals(Clock.TICK, this::tick)
                .match(Replicator.Changed.class, this::onChanged)
                .build()
                .orElse(receiveClusterMemberRemoved())
                .orElse(getClusterStateSyncBehavior())
                .orElse(ReceiveBuilder.create().matchAny(this::logUnhandled).build());
    }

    @Override
    public Cluster getCluster() {
        return cluster;
    }

    @Override
    public Address toAddress(final Address ddataKey) {
        return ddataKey;
    }

    @Override
    public ThreadSafeDittoLoggingAdapter log() {
        return log;
    }

    @Override
    public DData<Address, ?, ?> getDData() {
        return ackDData;
    }

    @Override
    public void verifyNoDDataForCurrentMember() {
        previousUpdate = LiteralUpdate.empty();
    }

    @Override
    public DDataWriter<Address, ?> getDDataWriter() {
        return ackDData.getWriter();
    }

    private void declare(final DeclareAcks request) {
        final ActorRef sender = getSender();
        final ActorRef subscriber = request.getSubscriber();
        final String group = request.getGroup().orElse(null);
        final Set<String> ackLabels = request.getAckLabels();
        if (isAllowedLocally(group, ackLabels) && isAllowedRemotely(group, ackLabels)) {
            localAckLabels.put(subscriber, group, ackLabels);
            getContext().watch(subscriber);
            getSender().tell(AcksDeclared.of(request, sender), getSelf());
        } else {
            failSubscribe(sender);
        }
    }

    private boolean isAllowedLocally(@Nullable final String group, final Set<String> ackLabels) {
        if (group != null) {
            final Optional<Set<String>> groupLabels = localAckLabels.getValuesOfGroup(group);
            if (groupLabels.isPresent()) {
                return groupLabels.get().equals(ackLabels);
            }
        }
        return noDeclaredLabelMatches(ackLabels, localAckLabels::containsValue);
    }

    private boolean isAllowedRemotely(@Nullable final String group, final Set<String> ackLabels) {
        return isAllowedRemotelyBy(group, ackLabels, remoteGroups,
                conflictWithOtherGroups(group, remoteAckLabels));
    }

    private boolean isAllowedRemotelyBy(final Grouped<String> groupedLabels,
            final Map<String, Set<String>> remoteGroups,
            final Predicate<String> isTakenRemotely) {
        return isAllowedRemotelyBy(groupedLabels.getGroup().orElse(null), groupedLabels.getValues(),
                remoteGroups, isTakenRemotely);
    }

    private boolean isAllowedRemotelyBy(@Nullable final String group, final Set<String> ackLabels,
            final Map<String, Set<String>> remoteGroups,
            final Predicate<String> isTakenRemotely) {
        final boolean noConflict = noDeclaredLabelMatches(ackLabels, isTakenRemotely);
        if (noConflict && group != null) {
            final Set<String> remoteGroup = remoteGroups.get(group);
            if (remoteGroup != null) {
                return remoteGroup.equals(ackLabels);
            }
        }
        return noConflict;
    }

    private boolean noDeclaredLabelMatches(final Set<String> ackLabels, final Predicate<String> contains) {
        return ackLabels.stream().noneMatch(contains);
    }

    private void tick(final Clock tick) {
        writeLocalDData();
        final var changed = LocalAcksChanged.of(localAckLabels.export());
        localChangeRecipients.forEach(recipient -> recipient.tell(changed, getSelf()));
        ackSizeMetric.set(changed.getSnapshot().estimateSize());
    }

    private void onChanged(final Replicator.Changed<?> event) {
        final Map<Address, List<Grouped<String>>> mmap = Grouped.deserializeORMultiMap(
                ((ORMultiMap<Address, String>) event.dataValue()), JsonValue::asString);

        cachedRemoteAcks.put(event.key(), mmap);
        // calculate all sharded maps:
        final Map<Address, List<Grouped<String>>> completeMmap = new HashMap<>();
        cachedRemoteAcks.values()
                .forEach(map -> map.forEach((address, groups) ->
                        completeMmap.merge(address, groups, (g1, g2) -> {
                            g1.addAll(g2);
                            return g1;
                        }))
                );

        final List<Grouped<String>> remoteGroupedAckLabels = getRemoteGroupedAckLabelsOrderByAddress(completeMmap);
        remoteGroups = getRemoteGroups(remoteGroupedAckLabels);
        remoteAckLabels = getRemoteAckLabels(remoteGroupedAckLabels);

        for (final ActorRef localLoser : getLocalLosers(completeMmap)) {
            doRemoveSubscriber(localLoser);
            failSubscribe(localLoser);
        }

        final var ddataChanged = RemoteAcksChanged.of(completeMmap);
        ddataChangeRecipients.forEach(recipient -> recipient.tell(ddataChanged, getSelf()));
    }

    private List<Grouped<String>> getRemoteGroupedAckLabelsOrderByAddress(
            final Map<Address, List<Grouped<String>>> mmap) {

        return mmap.entrySet()
                .stream()
                .filter(this::isNotOwnAddress)
                .sorted(entryKeyAddressComparator())
                .map(Map.Entry::getValue)
                .flatMap(List::stream)
                .toList();
    }

    private boolean isNotOwnAddress(final Map.Entry<Address, ?> entry) {
        return !ownAddress.equals(entry.getKey());
    }

    private Map<String, Set<String>> getRemoteGroups(final List<Grouped<String>> remoteGroupedAckLabels) {
        final Map<String, Set<String>> result = new HashMap<>();
        remoteGroupedAckLabels.stream()
                .flatMap(Grouped::streamAsGroupedPair)
                // do not set a group of ack labels if already set by a member of smaller address
                .forEach(pair -> result.computeIfAbsent(pair.first(), group -> pair.second()));
        return Collections.unmodifiableMap(result);
    }

    private Map<String, Set<String>> getRemoteAckLabels(final List<Grouped<String>> remoteGroupedAckLabels) {
        final Map<String, Set<String>> remoteAckLabelsToGroup = new HashMap<>();
        for (final Grouped<String> grouped : remoteGroupedAckLabels) {
            final String groupKey = grouped.getGroup().orElse("");
            for (final String label : grouped.getValues()) {
                remoteAckLabelsToGroup.compute(label, (k, groups) -> {
                    final Set<String> nonNullSet = groups == null ? new HashSet<>() : groups;
                    nonNullSet.add(groupKey);
                    return nonNullSet;
                });
            }
        }
        return remoteAckLabelsToGroup;
    }

    private void logUnhandled(final Object message) {
        log.warning("Unhandled: <{}>", message);
    }

    private void terminated(final Terminated terminated) {
        final ActorRef terminatedActor = terminated.actor();
        doRemoveSubscriber(terminatedActor);
        ddataChangeRecipients.remove(terminatedActor);
        if (localChangeRecipients.remove(terminatedActor)) {
            reportLocalDataLoss();
        }
    }

    private void reportLocalDataLoss() {
        // local SubUpdater terminated. Request all known subscribers to terminate.
        localAckLabels.entrySet()
                .forEach(entry -> entry.getKey().tell(PubSubTerminatedException.getInstance(), getSelf()));
        localAckLabels.clear();
    }

    private void removeSubscriber(final RemoveSubscriberAcks request) {
        doRemoveSubscriber(request.getSubscriber());
    }

    private void doRemoveSubscriber(final ActorRef subscriber) {
        localAckLabels.removeKey(subscriber);
        getContext().unwatch(subscriber);
    }

    // NOT thread-safe
    private void writeLocalDData() {
        final var writeConsistency = (Replicator.WriteConsistency) Replicator.writeLocal();
        if (resetProbability > 0 && Math.random() < resetProbability) {
            log().debug("Resetting ddata ack-labels: <{}>", getSelf());
            ackDData.getWriter().reset(ownAddress, exportNextUpdate(), writeConsistency).whenComplete(this::logError);
        } else {
            final LiteralUpdate diff = createAndSetDDataUpdate();
            if (!diff.isEmpty()) {
                ackDData.getWriter().put(ownAddress, diff, writeConsistency).whenComplete(this::logError);
            }
        }
    }

    private void logError(@Nullable final Object unused, @Nullable final Throwable error) {
        if (error != null) {
            log.error(error, "Failed to update local DData");
        }
    }

    private LiteralUpdate createAndSetDDataUpdate() {
        final var nextUpdate = exportNextUpdate();
        final LiteralUpdate diff = nextUpdate.diff(previousUpdate);
        previousUpdate = nextUpdate;
        return diff;
    }

    private LiteralUpdate createAndResetDDataUpdate() {
        final var nextUpdate = exportNextUpdate();
        previousUpdate = nextUpdate;
        return nextUpdate;
    }

    private LiteralUpdate exportNextUpdate() {
        final Set<String> groupedAckLabels = localAckLabels.exportValuesByGroup()
                .stream()
                .map(Grouped::toJsonString)
                .collect(Collectors.toSet());
        return LiteralUpdate.withInserts(groupedAckLabels);
    }

    private void failSubscribe(final ActorRef sender) {
        final Throwable error = AcknowledgementLabelNotUniqueException.getInstance();
        sender.tell(error, getSelf());
    }

    private List<ActorRef> getLocalLosers(final Map<Address, List<Grouped<String>>> mmap) {
        final Map<Address, List<Grouped<String>>> moreImportantEntries = mmap.entrySet()
                .stream()
                .filter(entry -> Address.addressOrdering().compare(entry.getKey(), ownAddress) < 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        final List<Grouped<String>> moreImportantGroupedAckLabels =
                getRemoteGroupedAckLabelsOrderByAddress(moreImportantEntries);
        final Map<String, Set<String>> moreImportantRemoteGroups = getRemoteGroups(moreImportantGroupedAckLabels);
        final Map<String, Set<String>> moreImportantAckLabels = getRemoteAckLabels(moreImportantGroupedAckLabels);
        return localAckLabels.entrySet()
                .stream()
                .filter(entry -> !isAllowedRemotelyBy(entry.getValue(), moreImportantRemoteGroups,
                        conflictWithOtherGroups(entry.getValue().getGroup().orElse(null),
                                moreImportantAckLabels)))
                .map(Map.Entry::getKey)
                .toList();
    }

    private void onReceiveDDataChanges(final ReceiveRemoteAcks request) {
        ddataChangeRecipients.add(request.getReceiver());
        getContext().watch(request.getReceiver());
    }

    private void onReceiveLocalChanges(final ReceiveLocalAcks request) {
        localChangeRecipients.add(request.getReceiver());
        getContext().watch(request.getReceiver());
    }

    private static <T> Comparator<Map.Entry<Address, T>> entryKeyAddressComparator() {
        return (left, right) -> Address.addressOrdering().compare(left.getKey(), right.getKey());
    }

    private static Predicate<String> conflictWithOtherGroups(@Nullable final String group,
            final Map<String, Set<String>> topicToGroup) {
        if (group == null) {
            return topicToGroup::containsKey;
        } else {
            return topic -> {
                final Set<String> groups = topicToGroup.getOrDefault(topic, Set.of());
                return !(groups.isEmpty() || groups.size() == 1 && groups.contains(group));
            };
        }
    }

    private enum Clock {
        TICK
    }

}
