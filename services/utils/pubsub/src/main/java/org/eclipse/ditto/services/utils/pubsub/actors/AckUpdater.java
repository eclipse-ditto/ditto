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
package org.eclipse.ditto.services.utils.pubsub.actors;

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

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabelNotUniqueException;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.services.utils.pubsub.api.AcksDeclared;
import org.eclipse.ditto.services.utils.pubsub.api.DeclareAcks;
import org.eclipse.ditto.services.utils.pubsub.api.LocalAcksChanged;
import org.eclipse.ditto.services.utils.pubsub.api.ReceiveLocalAcks;
import org.eclipse.ditto.services.utils.pubsub.api.ReceiveRemoteAcks;
import org.eclipse.ditto.services.utils.pubsub.api.RemoteAcksChanged;
import org.eclipse.ditto.services.utils.pubsub.api.RemoveSubscriberAcks;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.services.utils.pubsub.ddata.ack.Grouped;
import org.eclipse.ditto.services.utils.pubsub.ddata.ack.GroupedRelation;
import org.eclipse.ditto.services.utils.pubsub.ddata.literal.LiteralUpdate;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.ddata.Replicator;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Manage the local and remote ternary relation between actors, group names and declared acknowledgement labels.
 * In case of conflict, prefer data from the cluster member with a smaller address.
 */
public final class AckUpdater extends AbstractActorWithTimers implements ClusterMemberRemovedAware {

    /**
     * Prefix of this actor's name.
     */
    public static final String ACTOR_NAME_PREFIX = "ackUpdater";

    protected final ThreadSafeDittoLoggingAdapter log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

    private final GroupedRelation<ActorRef, String> localAckLabels;
    private final Address ownAddress;
    private final DData<Address, String, LiteralUpdate> ackDData;
    private final java.util.Set<ActorRef> ddataChangeRecipients;
    private final java.util.Set<ActorRef> localChangeRecipients;

    private Set<String> remoteAckLabels = Set.of();
    private Map<String, Set<String>> remoteGroups = Map.of();

    // TODO: add metrics

    protected AckUpdater(final PubSubConfig config,
            final Address ownAddress,
            final DData<Address, String, LiteralUpdate> ackDData) {
        this.ownAddress = ownAddress;
        this.ackDData = ackDData;
        localAckLabels = GroupedRelation.create();
        ddataChangeRecipients = new HashSet<>();
        localChangeRecipients = new HashSet<>();
        subscribeForClusterMemberRemovedAware();
        ackDData.getReader().receiveChanges(getSelf());
        getTimers().startTimerAtFixedRate(Clock.TICK, Clock.TICK, config.getUpdateInterval());
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
                .matchAny(this::logUnhandled)
                .build()
                .orElse(receiveClusterMemberRemoved());
    }

    @Override
    public LoggingAdapter log() {
        return log;
    }

    @Override
    public DDataWriter<?, ?> getDDataWriter() {
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
        return isAllowedRemotelyBy(group, ackLabels, remoteGroups, remoteAckLabels::contains);
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

    private void tick(Clock tick) {
        writeLocalDData();
        final LocalAcksChanged changed = LocalAcksChanged.of(localAckLabels.export());
        localChangeRecipients.forEach(recipient -> recipient.tell(changed, getSelf()));
    }

    private void onChanged(final Replicator.Changed<?> event) {
        final Map<Address, List<Grouped<String>>> mmap =
                Grouped.deserializeORMultiMap(event.get(ackDData.getReader().getKey()), JsonValue::asString);
        final List<Grouped<String>> remoteGroupedAckLabels = getRemoteGroupedAckLabelsOrderByAddress(mmap);
        remoteGroups = getRemoteGroups(remoteGroupedAckLabels);
        remoteAckLabels = getRemoteAckLabels(remoteGroupedAckLabels);

        for (final ActorRef localLoser : getLocalLosers(mmap)) {
            doRemoveSubscriber(localLoser);
            failSubscribe(localLoser);
        }

        final RemoteAcksChanged ddataChanged = RemoteAcksChanged.of(mmap);
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
                .collect(Collectors.toList());
    }

    private boolean isNotOwnAddress(final Map.Entry<Address, ?> entry) {
        return !ownAddress.equals(entry.getKey());
    }

    private Map<String, Set<String>> getRemoteGroups(final List<Grouped<String>> remoteGroupedAckLabels) {
        final Map<String, Set<String>> result = new HashMap<>();
        remoteGroupedAckLabels.stream()
                .flatMap(Grouped<String>::streamAsGroupedPair)
                // do not set a group of ack labels if already set by a member of smaller address
                .forEach(pair -> result.computeIfAbsent(pair.first(), group -> pair.second()));
        return Collections.unmodifiableMap(result);
    }

    private Set<String> getRemoteAckLabels(final List<Grouped<String>> remoteGroupedAckLabels) {
        return remoteGroupedAckLabels.stream()
                .flatMap(Grouped<String>::streamValues)
                .collect(Collectors.toSet());
    }

    private void logUnhandled(final Object message) {
        log.warning("Unhandled: <{}>", message);
    }

    private void terminated(final Terminated terminated) {
        final ActorRef terminatedActor = terminated.actor();
        doRemoveSubscriber(terminatedActor);
        ddataChangeRecipients.remove(terminatedActor);
        localChangeRecipients.remove(terminatedActor);
    }

    private void removeSubscriber(final RemoveSubscriberAcks request) {
        doRemoveSubscriber(request.getSubscriber());
    }

    private void doRemoveSubscriber(final ActorRef subscriber) {
        localAckLabels.removeKey(subscriber);
        getContext().unwatch(subscriber);
    }

    private void writeLocalDData() {
        ackDData.getWriter()
                .put(ownAddress, createDDataUpdate(), (Replicator.WriteConsistency) Replicator.writeLocal())
                .whenComplete((_void, error) -> {
                    if (error != null) {
                        log.error(error, "Failed to update local DData");
                    }
                });
    }

    private LiteralUpdate createDDataUpdate() {
        final Set<String> groupedAckLabels = localAckLabels.streamGroupedValues()
                .map(Grouped::toJsonString)
                .collect(Collectors.toSet());
        return LiteralUpdate.replaceAll(groupedAckLabels);
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
        final Set<String> moreImportantAckLabels = getRemoteAckLabels(moreImportantGroupedAckLabels);
        return localAckLabels.entrySet()
                .stream()
                .filter(entry -> !isAllowedRemotelyBy(entry.getValue(), moreImportantRemoteGroups,
                        moreImportantAckLabels::contains))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
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

    private enum Clock {
        TICK
    }

}
