/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.updater.actors;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SyncThing;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.sharding.ShardRegion;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.CircuitBreaker;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;

/**
 * This Actor subscribes to messages the Things service emits, when it starts a new ThingActor (a Thing becomes "hot").
 * If we receive such a message, we start a corresponding ThingUpdater actor that itself consumes the events the
 * ThingActor emits and thus handles the specific events for that Thing.
 */
final class ThingsUpdater extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "thingsUpdater";

    private static final String UPDATER_GROUP = "thingsUpdaterGroup";
    private final DiagnosticLoggingAdapter log = Logging.apply(this);
    private final ActorRef shardRegion;
    private final ThingsSearchUpdaterPersistence searchUpdaterPersistence;
    private final Materializer materializer;

    private ThingsUpdater(final int numberOfShards,
            final ThingsSearchUpdaterPersistence searchUpdaterPersistence,
            final CircuitBreaker circuitBreaker,
            final boolean eventProcessingActive,
            final boolean thingTagsProcessingActive,
            final Duration activityCheckInterval,
            final ActorRef thingCacheFacade,
            final ActorRef policyCacheFacade) {

        final ActorSystem actorSystem = context().system();
        final ShardRegionFactory shardRegionFactory = ShardRegionFactory.getInstance(actorSystem);

        // Start the proxy for the Things and Policies sharding, too.
        final ActorRef thingsShardRegion = shardRegionFactory.getThingsShardRegion(numberOfShards);
        final ActorRef policiesShardRegion = shardRegionFactory.getPoliciesShardRegion(numberOfShards);

        final ActorRef pubSubMediator = DistributedPubSub.get(actorSystem).mediator();

        final Props thingUpdaterProps =
                ThingUpdater.props(searchUpdaterPersistence, circuitBreaker, thingsShardRegion, policiesShardRegion,
                        activityCheckInterval, ThingUpdater.DEFAULT_THINGS_TIMEOUT, thingCacheFacade, policyCacheFacade)
                        .withMailbox("akka.actor.custom-updater-mailbox");

        shardRegion = shardRegionFactory.getSearchUpdaterShardRegion(numberOfShards, thingUpdaterProps);
        this.searchUpdaterPersistence = searchUpdaterPersistence;
        materializer = ActorMaterializer.create(getContext());

        if (eventProcessingActive) {
            pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(ThingEvent.TYPE_PREFIX, UPDATER_GROUP, self()),
                    self());
            pubSubMediator.tell(new DistributedPubSubMediator.Subscribe(PolicyEvent.TYPE_PREFIX, UPDATER_GROUP, self()),
                    self());
        } else {
            log.warning("Event processing is not active");
        }

        if (thingTagsProcessingActive) {
            pubSubMediator.tell(
                    new DistributedPubSubMediator.Subscribe(ThingsMessagingConstants.THING_TAGS_TOPIC, UPDATER_GROUP,
                            self()), self());
            pubSubMediator.tell(
                    new DistributedPubSubMediator.Subscribe(PolicyTag.TOPIC, UPDATER_GROUP, self()), self());
        } else {
            log.warning("Thing tags processing is not active");
        }
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param numberOfShards the number of shards the "search-updater" shardRegion should be started with.
     * @param activityCheckInterval the interval at which is checked, if the corresponding Thing is still actively
     * updated
     * @param thingCacheFacade the {@link org.eclipse.ditto.services.utils.distributedcache.actors.CacheFacadeActor} for
     * accessing the Thing cache in cluster.
     * @param policyCacheFacade the {@link org.eclipse.ditto.services.utils.distributedcache.actors.CacheFacadeActor} for
     * accessing the Policy cache in cluster.
     * @return the Akka configuration Props object
     */
    static Props props(final int numberOfShards,
            final ThingsSearchUpdaterPersistence searchUpdaterPersistence,
            final CircuitBreaker circuitBreaker,
            final boolean eventProcessingActive,
            final boolean thingTagsProcessingActive,
            final Duration activityCheckInterval,
            final ActorRef thingCacheFacade,
            final ActorRef policyCacheFacade) {

        return Props.create(ThingsUpdater.class, new Creator<ThingsUpdater>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingsUpdater create() throws Exception {
                return new ThingsUpdater(numberOfShards, searchUpdaterPersistence, circuitBreaker,
                        eventProcessingActive, thingTagsProcessingActive, activityCheckInterval, thingCacheFacade,
                        policyCacheFacade);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchEquals(ShardRegion.getShardRegionStateInstance(), getShardRegionState ->
                        shardRegion.forward(getShardRegionState, getContext()))
                .match(ThingEvent.class, this::processThingEvent)
                .match(PolicyEvent.class, this::processPolicyEvent)
                .match(ThingTag.class, this::processThingTag)
                .match(SyncThing.class, this::syncThing)
                .match(DistributedPubSubMediator.SubscribeAck.class, this::subscribeAck)
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void processThingTag(final ThingTag thingTag) {
        log.debug("Forwarding incoming ThingTag for thingId '{}'", thingTag.getThingId());
        forwardJsonifiableToShardRegion(thingTag, ThingTag::getThingId);
    }

    private void processThingEvent(final ThingEvent<?> thingEvent) {
        LogUtil.enhanceLogWithCorrelationId(log, thingEvent);
        log.debug("Forwarding incoming ThingEvent for thingId '{}'", thingEvent.getThingId());
        forwardEventToShardRegion(thingEvent, ThingEvent::getId);
    }

    private void processPolicyEvent(final PolicyEvent<?> policyEvent) {
        LogUtil.enhanceLogWithCorrelationId(log, policyEvent);
        thingIdsForPolicy(policyEvent.getPolicyId())
                .thenAccept(thingIds ->
                        thingIds.forEach(id -> forwardPolicyEventToShardRegion(policyEvent, id))
                );
    }

    private CompletionStage<Set<String>> thingIdsForPolicy(final String policyId) {
        return searchUpdaterPersistence.getThingIdsForPolicy(policyId).runWith(Sink.last(), materializer);
    }

    private void syncThing(final SyncThing message) {
        LogUtil.enhanceLogWithCorrelationId(log, message);
        log.debug("Forwarding incoming SyncThing message for thingId '{}'", message.getThingId());
        forwardToShardRegion(
                message,
                SyncThing::getThingId,
                SyncThing::getType,
                syncThing -> syncThing.toJson(syncThing.getImplementedSchemaVersion(), FieldType.regularOrSpecial()),
                SyncThing::getDittoHeaders);
    }

    private <J extends Jsonifiable<?>> void forwardJsonifiableToShardRegion(final J message,
            final Function<J, String> getId) {
        forwardToShardRegion(
                message,
                getId,
                jsonifiable -> jsonifiable.getClass().getSimpleName(),
                jsonifiable -> jsonifiable.toJson().asObject(),
                jsonifiable -> DittoHeaders.empty());
    }

    private <E extends Event<?>> void forwardEventToShardRegion(final E message, final Function<E, String> getId) {
        forwardToShardRegion(
                message,
                getId,
                Event::getType,
                event -> event.toJson(event.getImplementedSchemaVersion(), FieldType.regularOrSpecial()),
                Event::getDittoHeaders);
    }

    private <M> void forwardToShardRegion(final M message,
            final Function<M, String> getId,
            final Function<M, String> getType,
            final Function<M, JsonObject> toJson,
            final Function<M, DittoHeaders> getDittoHeaders) {

        final String id = getId.apply(message);
        log.debug("Forwarding incoming {} to shard region of {}", message.getClass().getSimpleName(), id);
        final String type = getType.apply(message);
        final JsonObject jsonObject = toJson.apply(message);
        final DittoHeaders dittoHeaders = getDittoHeaders.apply(message);
        final ShardedMessageEnvelope messageEnvelope = ShardedMessageEnvelope.of(id, type, jsonObject, dittoHeaders);
        shardRegion.forward(messageEnvelope, context());
    }

    private void forwardPolicyEventToShardRegion(final PolicyEvent<?> policyEvent, final String thingId) {
        log.debug("Will forward incoming event message for policyId '{}' to update actor '{}':",
                policyEvent.getPolicyId(), thingId);
        forwardEventToShardRegion(policyEvent, e -> thingId);
    }

    private void subscribeAck(final DistributedPubSubMediator.SubscribeAck subscribeAck) {
        log.debug("Successfully subscribed to distributed pub/sub on topic '{}'", subscribeAck.subscribe().topic());
    }

}
