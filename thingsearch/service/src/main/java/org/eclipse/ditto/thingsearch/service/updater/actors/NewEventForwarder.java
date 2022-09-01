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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.namespaces.BlockNamespaceBehavior;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.pubsub.DistributedSub;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.common.config.UpdaterConfig;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.sharding.ShardRegion;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.Duration;

/**
 * This Actor forwards thing events belonging to inactive shard regions.
 */
final class NewEventForwarder extends AbstractActorWithTimers {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "newEventForwarder";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final ActorRef shardRegion;
    private final DistributedSub thingEventSub;
    private final BlockNamespaceBehavior namespaceBlockingBehavior;
    private final ShardRegion.GetClusterShardingStats getClusterShardingStats;
    private final ShardRegionExtractor shardRegionExtractor;

    private Set<String> previousShardIds = Collections.emptySet();

    @SuppressWarnings("unused")
    private NewEventForwarder(final DistributedSub thingEventSub,
            final ActorRef thingUpdaterShardRegion,
            final BlockedNamespaces blockedNamespaces) {

        this.thingEventSub = thingEventSub;

        final DittoSearchConfig searchConfig =
                DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()));
        final UpdaterConfig updaterConfig = searchConfig.getUpdaterConfig();
        final ClusterConfig clusterConfig = searchConfig.getClusterConfig();

        shardRegion = thingUpdaterShardRegion;

        namespaceBlockingBehavior = BlockNamespaceBehavior.of(blockedNamespaces);

        getClusterShardingStats = new ShardRegion.GetClusterShardingStats(
                Duration.create(updaterConfig.getShardingStatePollInterval().toMillis(), TimeUnit.MILLISECONDS));

        shardRegionExtractor = ShardRegionExtractor.of(clusterConfig.getNumberOfShards(), getContext().getSystem());

        if (updaterConfig.isEventProcessingActive()) {
            // schedule regular updates of subscriptions
            getTimers().startTimerAtFixedRate(Clock.REBALANCE_TICK, Clock.REBALANCE_TICK,
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
     * @param blockedNamespaces cache of namespaces to block.
     * @return the Akka configuration Props object
     */
    static Props props(final DistributedSub thingEventSub,
            final ActorRef thingUpdaterShardRegion,
            final BlockedNamespaces blockedNamespaces) {

        return Props.create(NewEventForwarder.class, thingEventSub, thingUpdaterShardRegion, blockedNamespaces);
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
        final Set<String> inactiveShardIds = shardRegionExtractor.getInactiveShardIds(getActiveShardIds(stats));
        log.debug("Updating event subscriptions for inactive shards: <{}> -> <{}>", previousShardIds, inactiveShardIds);
        final List<String> toSubscribe =
                inactiveShardIds.stream().filter(s -> !previousShardIds.contains(s)).toList();
        final List<String> toUnsubscribe =
                previousShardIds.stream().filter(s -> !inactiveShardIds.contains(s)).toList();
        thingEventSub.subscribeWithoutAck(toSubscribe, getSelf());
        thingEventSub.unsubscribeWithoutAck(toUnsubscribe, getSelf());
        previousShardIds = inactiveShardIds;
    }

    private void processThingEvent(final ThingEvent<?> thingEvent) {
        log.withCorrelationId(thingEvent)
                .debug("Forwarding incoming ThingEvent for thingId '{}'", thingEvent.getEntityId());
        final ActorRef sender = getSender();
        namespaceBlockingBehavior.block(thingEvent).thenAccept(m -> shardRegion.tell(m, sender));
    }

    private static Collection<String> getActiveShardIds(final ShardRegion.ClusterShardingStats stats) {
        return stats.getRegions()
                .values()
                .stream()
                .flatMap(shardRegionStats -> shardRegionStats.getStats().keySet().stream())
                .toList();
    }

    private enum Clock {
        REBALANCE_TICK
    }

}
