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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import java.util.Objects;
import java.util.function.Function;

import org.eclipse.ditto.base.api.devops.signals.commands.RetrieveStatisticsDetails;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.ShardedMessageEnvelope;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.models.streaming.IdentifiableStreamingMessage;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.cluster.RetrieveStatisticsDetailsResponseSupplier;
import org.eclipse.ditto.internal.utils.namespaces.BlockNamespaceBehavior;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.thingsearch.api.PolicyReferenceTag;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.api.commands.sudo.SudoUpdateThing;
import org.eclipse.ditto.thingsearch.api.events.ThingsOutOfSync;
import org.eclipse.ditto.thingsearch.service.common.config.UpdaterConfig;

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

    @SuppressWarnings("unused")
    private ThingsUpdater(
            final ActorRef thingUpdaterShardRegion,
            final UpdaterConfig updaterConfig,
            final BlockedNamespaces blockedNamespaces,
            final ActorRef pubSubMediator) {

        shardRegion = thingUpdaterShardRegion;

        namespaceBlockingBehavior = BlockNamespaceBehavior.of(blockedNamespaces);

        retrieveStatisticsDetailsResponseSupplier =
                RetrieveStatisticsDetailsResponseSupplier.of(shardRegion, ShardRegionFactory.UPDATER_SHARD_REGION, log);

        pubSubMediator.tell(DistPubSubAccess.subscribeViaGroup(ThingsOutOfSync.TYPE, ACTOR_NAME, getSelf()), getSelf());
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param thingUpdaterShardRegion shard region of thing-updaters
     * @param updaterConfig configuration for updaters.
     * @param blockedNamespaces cache of namespaces to block.
     * @param pubSubMediator the pubsub mediator for subscription for SudoUpdateThing commands, or null if
     * the subscription is not wanted.
     * @return the Akka configuration Props object
     */
    static Props props(final ActorRef thingUpdaterShardRegion,
            final UpdaterConfig updaterConfig,
            final BlockedNamespaces blockedNamespaces,
            final ActorRef pubSubMediator) {
        return Props.create(ThingsUpdater.class, thingUpdaterShardRegion, updaterConfig, blockedNamespaces,
                pubSubMediator);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ThingEvent.class, this::processThingEvent)
                .match(PolicyReferenceTag.class, this::processPolicyReferenceTag)
                .matchEquals(ShardRegion.getShardRegionStateInstance(), getShardRegionState ->
                        shardRegion.forward(getShardRegionState, getContext()))
                .match(RetrieveStatisticsDetails.class, this::handleRetrieveStatisticsDetails)
                .match(ThingsOutOfSync.class, this::updateThings)
                .match(SudoUpdateThing.class, this::updateThing)
                .match(DistributedPubSubMediator.SubscribeAck.class, subscribeAck ->
                        log.debug("Got <{}>", subscribeAck))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handleRetrieveStatisticsDetails(final RetrieveStatisticsDetails command) {
        log.info("Sending the namespace stats of the search-updater shard as requested..");
        Patterns.pipe(retrieveStatisticsDetailsResponseSupplier
                .apply(command.getDittoHeaders()), getContext().dispatcher()).to(getSender());
    }

    private void updateThings(final ThingsOutOfSync updateThings) {
        // log all thing IDs because getting this command implies out-of-sync things.
        log.withCorrelationId(updateThings)
                .info("Out-of-sync things are reported: <{}>", updateThings);
        updateThings.getThingIds().forEach(thingId ->
                forwardToShardRegion(
                        SudoUpdateThing.of(ThingId.of(thingId), UpdateReason.BACKGROUND_SYNC,
                                updateThings.getDittoHeaders()),
                        SudoUpdateThing::getEntityId,
                        SudoUpdateThing::getType,
                        SudoUpdateThing::toJson,
                        SudoUpdateThing::getDittoHeaders
                )
        );
    }

    private void updateThing(final SudoUpdateThing sudoUpdateThing) {
        forwardToShardRegion(sudoUpdateThing, SudoUpdateThing::getEntityId, SudoUpdateThing::getType,
                SudoUpdateThing::toJson, SudoUpdateThing::getDittoHeaders);
    }

    private void processPolicyReferenceTag(final PolicyReferenceTag policyReferenceTag) {
        final String elementIdentifier = policyReferenceTag.asIdentifierString();
        log.withCorrelationId("policies-tags-sync-" + elementIdentifier)
                .debug("Forwarding PolicyReferenceTag '{}'", elementIdentifier);
        forwardJsonifiableToShardRegion(policyReferenceTag, unused -> policyReferenceTag.getThingId());
    }


    private void processThingEvent(final ThingEvent<?> thingEvent) {
        log.withCorrelationId(thingEvent)
                .debug("Forwarding incoming ThingEvent for thingId '{}'",
                        String.valueOf(thingEvent.getEntityId()));
        forwardEventToShardRegion(thingEvent, ThingEvent::getEntityId);
    }

    private <J extends Jsonifiable<?>> void forwardJsonifiableToShardRegion(final J message,
            final Function<J, ThingId> getId) {
        forwardToShardRegion(
                message,
                getId,
                jsonifiable -> jsonifiable.getClass().getSimpleName(),
                jsonifiable -> jsonifiable.toJson().asObject(),
                jsonifiable -> DittoHeaders.empty());
    }

    private <E extends Event<?>> void forwardEventToShardRegion(final E message, final Function<E, ThingId> getId) {
        forwardToShardRegion(
                message,
                getId,
                Event::getType,
                event -> event.toJson(event.getImplementedSchemaVersion(), FieldType.regularOrSpecial()),
                Event::getDittoHeaders);
    }

    private <M> void forwardToShardRegion(final M message,
            final Function<M, ThingId> getId,
            final Function<M, String> getType,
            final Function<M, JsonObject> toJson,
            final Function<M, DittoHeaders> getDittoHeaders) {

        final ThingId id = getId.apply(message);
        final DittoHeaders dittoHeaders = getDittoHeaders.apply(message);
        log.withCorrelationId(dittoHeaders)
                .debug("Forwarding incoming {} to shard region of {}", message.getClass().getSimpleName(), id);
        final String type = getType.apply(message);
        final JsonObject jsonObject = toJson.apply(message);
        final ShardedMessageEnvelope messageEnvelope = ShardedMessageEnvelope.of(id, type, jsonObject, dittoHeaders);

        final ActorRef sender = getSender();
        final ActorRef deadLetters = getContext().getSystem().deadLetters();

        namespaceBlockingBehavior.block(messageEnvelope)
                .thenAccept(m -> shardRegion.tell(m, sender))
                .exceptionally(throwable -> {
                    if (!Objects.equals(sender, deadLetters)) {
                        // Only acknowledge IdentifiableStreamingMessage. No other messages should be acknowledged.
                        if (message instanceof IdentifiableStreamingMessage identifiableStreamingMessage) {
                            final StreamAck streamAck =
                                    StreamAck.success(identifiableStreamingMessage.asIdentifierString());
                            sender.tell(streamAck, getSelf());
                        }
                    }
                    return null;
                });
    }

}
