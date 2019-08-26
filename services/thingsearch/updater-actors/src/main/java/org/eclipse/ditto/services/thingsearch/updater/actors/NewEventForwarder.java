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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.thingsearch.common.config.UpdaterConfig;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.namespaces.BlockNamespaceBehavior;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.services.utils.pubsub.DistributedSub;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.sharding.ShardRegion;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.FiniteDuration;

/**
 * This Actor forwards thing events belonging to inactive shard regions.
 */
final class NewEventForwarder extends AbstractActorWithTimers {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "newEventForwarder";

    private final DiagnosticLoggingAdapter log = Logging.apply(this);
    private final ActorRef shardRegion;
    private final DistributedSub thingEventSub;
    private final BlockNamespaceBehavior namespaceBlockingBehavior;
    private final ShardRegion.GetClusterShardingStats getClusterShardingStats;
    private final ShardRegionExtractor shardRegionExtractor;

    @SuppressWarnings("unused")
    private NewEventForwarder(final DistributedSub thingEventSub,
            final ActorRef thingUpdaterShardRegion,
            final UpdaterConfig updaterConfig,
            final ClusterConfig clusterConfig,
            final BlockedNamespaces blockedNamespaces) {

        this.thingEventSub = thingEventSub;

        shardRegion = thingUpdaterShardRegion;

        namespaceBlockingBehavior = BlockNamespaceBehavior.of(blockedNamespaces);

        getClusterShardingStats = new ShardRegion.GetClusterShardingStats(
                FiniteDuration.create(updaterConfig.getShardingStatePollInterval().toMillis(), TimeUnit.MILLISECONDS));

        shardRegionExtractor = ShardRegionExtractor.of(clusterConfig.getNumberOfShards(), getContext().getSystem());

        if (updaterConfig.isEventProcessingActive()) {
            // schedule regular updates of subscriptions
            getTimers().startPeriodicTimer(Clock.REBALANCE_TICK, Clock.REBALANCE_TICK,
                    updaterConfig.getShardingStatePollInterval());
            // subscribe for thing events immediately
            getSelf().tell(Clock.REBALANCE_TICK, getSelf());
        }
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param thingEventSub Ditto distributed-sub access for thing events.
     * @param thingUpdaterShardRegion shard region of thing-updaters
     * @param updaterConfig configuration for updaters.
     * @param clusterConfig configuration for the Ditto cluster.
     * @param blockedNamespaces cache of namespaces to block.
     * @return the Akka configuration Props object
     */
    static Props props(final DistributedSub thingEventSub,
            final ActorRef thingUpdaterShardRegion,
            final UpdaterConfig updaterConfig,
            final ClusterConfig clusterConfig,
            final BlockedNamespaces blockedNamespaces) {

        return Props.create(NewEventForwarder.class, thingEventSub, thingUpdaterShardRegion, updaterConfig,
                clusterConfig, blockedNamespaces);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ThingEvent.class, this::processThingEvent)
                .matchEquals(Clock.REBALANCE_TICK, this::retrieveClusterShardingStats)
                .match(ShardRegion.ClusterShardingStats.class, this::updateSubscriptions)
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void retrieveClusterShardingStats(final Clock rebalanceTick) {
        shardRegion.tell(getClusterShardingStats, getSelf());
    }

    private void updateSubscriptions(final ShardRegion.ClusterShardingStats stats) {
        final Collection<String> topics = shardRegionExtractor.getInactiveShardIds(getActiveShardIds(stats));
        log.debug("Updating event subscriptions: <{}>", topics);
        thingEventSub.removeSubscriber(getSelf());
        thingEventSub.subscribeWithoutAck(topics, getSelf());
    }

    private void processThingEvent(final ThingEvent<?> thingEvent) {
        LogUtil.enhanceLogWithCorrelationId(log, thingEvent);
        log.debug("Forwarding incoming ThingEvent for thingId '{}'", thingEvent.getThingId());
        namespaceBlockingBehavior.block(thingEvent).thenAccept(m -> shardRegion.tell(m, getSelf()));
    }

    private static Collection<String> getActiveShardIds(final ShardRegion.ClusterShardingStats stats) {
        return stats.getRegions()
                .values()
                .stream()
                .flatMap(shardRegionStats -> shardRegionStats.getStats().keySet().stream())
                .collect(Collectors.toList());
    }

    private enum Clock {
        REBALANCE_TICK
    }

}
