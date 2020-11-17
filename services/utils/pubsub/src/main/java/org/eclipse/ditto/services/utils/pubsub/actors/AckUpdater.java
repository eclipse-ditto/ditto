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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotEmpty;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabelNotUniqueException;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.services.utils.pubsub.ddata.ack.GroupedAckLabels;
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
 * TODO
 */
public final class AckUpdater extends AbstractActorWithTimers implements ClusterMemberRemovedAware {

    protected final ThreadSafeDittoLoggingAdapter log = DittoLoggerFactory.getThreadSafeDittoLoggingAdapter(this);

    private final GroupedRelation<ActorRef, String> localAckLabels;
    private final Address ownAddress;
    private final DData<Address, String, LiteralUpdate> ackDData;

    private Set<String> remoteAckLabels = Set.of();
    private Map<String, Set<String>> remoteGroups = Map.of();

    // TODO: add metrics

    protected AckUpdater(final PubSubConfig config,
            final Address ownAddress,
            final DData<Address, String, LiteralUpdate> ackDData) {
        this.ownAddress = ownAddress;
        this.ackDData = ackDData;
        this.localAckLabels = GroupedRelation.create();

        subscribeForClusterMemberRemovedAware();
        ackDData.getReader().receiveChanges(getSelf());
        getTimers().startTimerAtFixedRate(Clock.TICK, Clock.TICK, config.getUpdateInterval());
    }

    // TODO: javadoc
    public static Props props(final PubSubConfig config, final Address ownAddress,
            final DData<Address, String, LiteralUpdate> ackDData) {
        return Props.create(AckUpdater.class, config, ownAddress, ackDData);
    }

    // TODO: javadoc
    public static Request declareAckLabels(final ActorRef subscriber,
            @Nullable final String group,
            final Set<String> ackLabels) {
        return new DeclareAckLabels(subscriber, group, ackLabels);
    }

    public static Request removeSubscriber(final ActorRef subscriber) {
        return new RemoveSubscriber(subscriber);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(DeclareAckLabels.class, this::declare)
                .match(Terminated.class, this::terminated)
                .match(RemoveSubscriber.class, this::removeSubscriber)
                .matchEquals(Clock.TICK, this::tick)
                .match(Replicator.Changed.class, this::onChanged)
                .matchAny(this::logUnhandled)
                .build()
                .orElse(receiveClusterMemberRemoved());
    }

    private void declare(final DeclareAckLabels request) {
        final ActorRef sender = getSender();
        if (isAllowedLocally(request) && isAllowedRemotely(request)) {
            localAckLabels.put(request.subscriber, request.group, request.ackLabels);
            getContext().watch(request.subscriber);
            getSender().tell(SubAck.of(request, sender), getSelf());
        } else {
            failSubscribe(sender);
        }
    }

    private boolean isAllowedLocally(final DeclareAckLabels request) {
        if (request.group != null) {
            final Optional<Set<String>> groupLabels = localAckLabels.getValuesOfGroup(request.group);
            if (groupLabels.isPresent()) {
                return groupLabels.get().equals(request.ackLabels);
            }
        }
        return noDeclaredLabelMatches(request, localAckLabels::containsValue);
    }

    private boolean isAllowedRemotely(final DeclareAckLabels request) {
        if (request.group != null) {
            final Set<String> remoteGroup = remoteGroups.get(request.group);
            if (remoteGroup != null) {
                return remoteGroup.equals(request.ackLabels);
            }
        }
        return noDeclaredLabelMatches(request, remoteAckLabels::contains);
    }

    private boolean noDeclaredLabelMatches(final DeclareAckLabels request, final Predicate<String> contains) {
        return request.ackLabels.stream().noneMatch(contains);
    }

    private void tick(Clock tick) {
        writeLocalDData();
        // TODO: broadcast changes
    }

    private void onChanged(final Replicator.Changed<?> event) {
        final Map<Address, List<GroupedAckLabels>> mmap =
                GroupedAckLabels.deserializeORMultiMap(event.get(ackDData.getReader().getKey()), this::isNotOwnAddress);
        remoteGroups = getRemoteGroups(mmap);
        remoteAckLabels = getRemoteAckLabels(mmap);
        // TODO: deal with local losers of races
    }

    private boolean isNotOwnAddress(final Address address) {
        return !ownAddress.equals(address);
    }

    private Map<String, Set<String>> getRemoteGroups(final Map<Address, List<GroupedAckLabels>> mmap) {
        final Map<String, Set<String>> result = new HashMap<>();
        mmap.entrySet()
                .stream()
                // order by address to prefer group definitions by member with smaller address
                .sorted(entryKeyAddressComparator())
                .forEach(entry -> entry.getValue()
                        .stream()
                        .flatMap(GroupedAckLabels::streamAsGroupedPair)
                        // do not set a group of ack labels if already set by a member of smaller address
                        .forEach(pair -> result.computeIfAbsent(pair.first(), group -> pair.second()))
                );
        return Collections.unmodifiableMap(result);
    }

    private Set<String> getRemoteAckLabels(final Map<Address, List<GroupedAckLabels>> mmap) {
        return mmap.values()
                .stream()
                .flatMap(List::stream)
                .flatMap(GroupedAckLabels::streamAckLabels)
                .collect(Collectors.toSet());
    }

    private void logUnhandled(final Object message) {
        log.warning("Unhandled: <{}>", message);
    }

    private void terminated(final Terminated terminated) {
        doRemoveSubscriber(terminated.actor());
    }

    private void removeSubscriber(final RemoveSubscriber request) {
        doRemoveSubscriber(request.subscriber);
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
                .map(GroupedAckLabels::fromGrouped)
                .map(GroupedAckLabels::toJsonString)
                .collect(Collectors.toSet());
        return LiteralUpdate.replaceAll(groupedAckLabels);
    }

    private void failSubscribe(final ActorRef sender) {
        // TODO: explain other reasons of declaration failure
        final Throwable error = AcknowledgementLabelNotUniqueException.getInstance();
        sender.tell(error, getSelf());
    }

    @Override
    public LoggingAdapter log() {
        return log;
    }

    @Override
    public DDataWriter<?, ?> getDDataWriter() {
        return ackDData.getWriter();
    }

    private static <T> Comparator<Map.Entry<Address, T>> entryKeyAddressComparator() {
        return (left, right) -> Address.addressOrdering().compare(left.getKey(), right.getKey());
    }

    private enum Clock {
        TICK
    }

    // TODO: javadoc
    public interface Request {
    }

    private static final class DeclareAckLabels implements Request {

        private final ActorRef subscriber;
        @Nullable private final String group;
        private final Set<String> ackLabels;

        private DeclareAckLabels(final ActorRef subscriber,
                @Nullable final String group,
                final Set<String> ackLabels) {
            this.subscriber = subscriber;
            this.group = group;
            this.ackLabels = checkNotEmpty(ackLabels, "ackLabels");
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "[subscriber=" + subscriber +
                    ",group=" + group +
                    ",ackLabels=" + ackLabels +
                    "]";
        }
    }

    public static final class RemoveSubscriber implements Request {

        private final ActorRef subscriber;

        private RemoveSubscriber(final ActorRef subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "[subscriber=" + subscriber +
                    "]";
        }
    }

    /**
     * Acknowledgement for requests.
     */
    public static final class SubAck {

        private final Request request;
        private final ActorRef sender;

        private SubAck(final Request request, final ActorRef sender) {
            this.request = request;
            this.sender = sender;
        }

        static SubAck of(final Request request, final ActorRef sender) {
            return new SubAck(request, sender);
        }

        /**
         * @return the request this object is acknowledging.
         */
        public Request getRequest() {
            return request;
        }

        /**
         * @return sender of the request.
         */
        public ActorRef getSender() {
            return sender;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "[request=" + request +
                    ",sender=" + sender +
                    "]";
        }
    }
}
