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

import static org.eclipse.ditto.services.thingsearch.updater.actors.ShardRegionFactory.UPDATER_SHARD_REGION;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.policies.PolicyReferenceTag;
import org.eclipse.ditto.services.models.streaming.IdentifiableStreamingMessage;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.UpdateThing;
import org.eclipse.ditto.services.thingsearch.common.config.UpdaterConfig;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.services.utils.cluster.RetrieveStatisticsDetailsResponseSupplier;
import org.eclipse.ditto.services.utils.namespaces.BlockNamespaceBehavior;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.services.utils.pubsub.DistributedSub;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.commands.devops.RetrieveStatisticsDetails;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.thingsearch.ThingsOutOfSync;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.sharding.ShardRegion;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;

/**
 * This Actor subscribes to messages the Things service emits, when it starts a new ThingActor (a Thing becomes "hot").
 * If we receive such a message, we start a corresponding ThingUpdater actor that itself consumes the events the
 * ThingActor emits and thus handles the specific events for that Thing.
 */
final class ThingsUpdater extends AbstractActorWithTimers {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "thingsUpdater";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
    private final ActorRef shardRegion;
    private final BlockNamespaceBehavior namespaceBlockingBehavior;
    private final RetrieveStatisticsDetailsResponseSupplier retrieveStatisticsDetailsResponseSupplier;
    private final DistributedSub thingEventSub;

    private Set<String> previousShardIds = Collections.emptySet();

    @SuppressWarnings("unused")
    private ThingsUpdater(final DistributedSub thingEventSub,
            final ActorRef thingUpdaterShardRegion,
            final UpdaterConfig updaterConfig,
            final BlockedNamespaces blockedNamespaces,
            final ActorRef pubSubMediator) {

        this.thingEventSub = thingEventSub;

        shardRegion = thingUpdaterShardRegion;

        namespaceBlockingBehavior = BlockNamespaceBehavior.of(blockedNamespaces);

        retrieveStatisticsDetailsResponseSupplier =
                RetrieveStatisticsDetailsResponseSupplier.of(shardRegion, UPDATER_SHARD_REGION, log);

        if (updaterConfig.isEventProcessingActive()) {
            // schedule regular updates of subscriptions
            getTimers().startPeriodicTimer(Clock.REBALANCE_TICK, Clock.REBALANCE_TICK,
                    updaterConfig.getShardingStatePollInterval());
            // subscribe for thing events immediately
            getSelf().tell(Clock.REBALANCE_TICK, getSelf());
        }

        pubSubMediator.tell(DistPubSubAccess.subscribeViaGroup(ThingsOutOfSync.TYPE, ACTOR_NAME, getSelf()), getSelf());
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param thingEventSub Ditto distributed-sub access for thing events.
     * @param thingUpdaterShardRegion shard region of thing-updaters
     * @param updaterConfig configuration for updaters.
     * @param blockedNamespaces cache of namespaces to block.
     * @param pubSubMediator the pubsub mediator for subscription for UpdateThing commands, or null if
     * the subscription is not wanted.
     * @return the Akka configuration Props object
     */
    static Props props(final DistributedSub thingEventSub,
            final ActorRef thingUpdaterShardRegion,
            final UpdaterConfig updaterConfig,
            final BlockedNamespaces blockedNamespaces,
            final ActorRef pubSubMediator) {

        return Props.create(ThingsUpdater.class, thingEventSub, thingUpdaterShardRegion, updaterConfig,
                blockedNamespaces, pubSubMediator);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ThingEvent.class, this::processThingEvent)
                .match(ThingTag.class, this::processThingTag)
                .match(PolicyReferenceTag.class, this::processPolicyReferenceTag)
                .matchEquals(ShardRegion.getShardRegionStateInstance(), getShardRegionState ->
                        shardRegion.forward(getShardRegionState, getContext()))
                .match(RetrieveStatisticsDetails.class, this::handleRetrieveStatisticsDetails)
                .matchEquals(Clock.REBALANCE_TICK, this::retrieveShardIds)
                .match(ShardRegion.ShardRegionStats.class, this::updateSubscriptions)
                .match(ThingsOutOfSync.class, this::updateThings)
                .match(UpdateThing.class, this::updateThing)
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                        log.debug("Got <{}>", subscribeAck))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void retrieveShardIds(final Clock rebalanceTick) {
        shardRegion.tell(ShardRegion.getRegionStatsInstance(), getSelf());
    }

    private void updateSubscriptions(final ShardRegion.ShardRegionStats stats) {
        final Set<String> currentShardIds = stats.getStats().keySet();
        log.debug("Updating event subscriptions: <{}> -> <{}>", previousShardIds, currentShardIds);
        final List<String> toSubscribe =
                currentShardIds.stream().filter(s -> !previousShardIds.contains(s)).collect(Collectors.toList());
        final List<String> toUnsubscribe =
                previousShardIds.stream().filter(s -> !currentShardIds.contains(s)).collect(Collectors.toList());
        thingEventSub.subscribeWithoutAck(toSubscribe, getSelf());
        thingEventSub.unsubscribeWithoutAck(toUnsubscribe, getSelf());
        previousShardIds = currentShardIds;
    }

    private void handleRetrieveStatisticsDetails(final RetrieveStatisticsDetails command) {
        log.info("Sending the namespace stats of the search-updater shard as requested..");
        Patterns.pipe(retrieveStatisticsDetailsResponseSupplier
                .apply(command.getDittoHeaders()), getContext().dispatcher()).to(getSender());
    }

    private void processThingTag(final ThingTag thingTag) {
        final String elementIdentifier = thingTag.asIdentifierString();
        LogUtil.enhanceLogWithCorrelationId(log, "things-tags-sync-" + elementIdentifier);
        log.debug("Forwarding incoming ThingTag '{}'", elementIdentifier);
        forwardJsonifiableToShardRegion(thingTag, ThingTag::getEntityId);
    }

    private void updateThings(final ThingsOutOfSync updateThings) {
        // log all thing IDs because getting this command implies out-of-sync things.
        log.withCorrelationId(updateThings)
                .warning("Out-of-sync things are reported: <{}>", updateThings);
        updateThings.getThingIds().forEach(thingId ->
                forwardToShardRegion(
                        UpdateThing.of(ThingId.of(thingId), updateThings.getDittoHeaders()),
                        UpdateThing::getEntityId,
                        UpdateThing::getType,
                        UpdateThing::toJson,
                        UpdateThing::getDittoHeaders
                )
        );
    }

    private void updateThing(final UpdateThing updateThing) {
        log.withCorrelationId(updateThing)
                .warning("Out-of-sync thing is reported: <{}>", updateThing);
        forwardToShardRegion(updateThing, UpdateThing::getEntityId, UpdateThing::getType, UpdateThing::toJson,
                UpdateThing::getDittoHeaders);
    }

    private void processPolicyReferenceTag(final PolicyReferenceTag policyReferenceTag) {
        final String elementIdentifier = policyReferenceTag.asIdentifierString();
        log.withCorrelationId("policies-tags-sync-" + elementIdentifier)
                .debug("Forwarding PolicyReferenceTag '{}'", elementIdentifier);
        forwardJsonifiableToShardRegion(policyReferenceTag, unused -> policyReferenceTag.getEntityId());
    }


    private void processThingEvent(final ThingEvent<?> thingEvent) {
        LogUtil.enhanceLogWithCorrelationId(log, thingEvent);
        log.withCorrelationId(thingEvent)
                .debug("Forwarding incoming ThingEvent for thingId '{}'",
                        String.valueOf(thingEvent.getThingEntityId()));
        forwardEventToShardRegion(thingEvent, ThingEvent::getThingEntityId);
    }

    private <J extends Jsonifiable<?>> void forwardJsonifiableToShardRegion(final J message,
            final Function<J, EntityId> getId) {
        forwardToShardRegion(
                message,
                getId,
                jsonifiable -> jsonifiable.getClass().getSimpleName(),
                jsonifiable -> jsonifiable.toJson().asObject(),
                jsonifiable -> DittoHeaders.empty());
    }

    private <E extends Event<?>> void forwardEventToShardRegion(final E message, final Function<E, EntityId> getId) {
        forwardToShardRegion(
                message,
                getId,
                Event::getType,
                event -> event.toJson(event.getImplementedSchemaVersion(), FieldType.regularOrSpecial()),
                Event::getDittoHeaders);
    }

    private <M> void forwardToShardRegion(final M message,
            final Function<M, EntityId> getId,
            final Function<M, String> getType,
            final Function<M, JsonObject> toJson,
            final Function<M, DittoHeaders> getDittoHeaders) {

        final EntityId id = getId.apply(message);
        log.debug("Forwarding incoming {} to shard region of {}", message.getClass().getSimpleName(), id);
        final String type = getType.apply(message);
        final JsonObject jsonObject = toJson.apply(message);
        final DittoHeaders dittoHeaders = getDittoHeaders.apply(message);
        final ShardedMessageEnvelope messageEnvelope = ShardedMessageEnvelope.of(id, type, jsonObject, dittoHeaders);

        final ActorRef sender = getSender();
        final ActorRef deadLetters = getContext().getSystem().deadLetters();

        namespaceBlockingBehavior.block(messageEnvelope)
                .thenAccept(m -> shardRegion.tell(m, sender))
                .exceptionally(throwable -> {
                    if (!Objects.equals(sender, deadLetters)) {
                        // Only acknowledge IdentifiableStreamingMessage. No other messages should be acknowledged.
                        if (message instanceof IdentifiableStreamingMessage) {
                            final StreamAck streamAck =
                                    StreamAck.success(((IdentifiableStreamingMessage) message).asIdentifierString());
                            sender.tell(streamAck, getSelf());
                        }
                    }
                    return null;
                });
    }

    private enum Clock {
        REBALANCE_TICK
    }

}
