/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.things.persistence.actors.strategies.AbstractReceiveStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.AbstractThingCommandStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.ReceiveStrategy;
import org.eclipse.ditto.services.things.persistence.snapshotting.DittoThingSnapshotter;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.services.things.starter.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.WithThingId;
import org.eclipse.ditto.signals.base.WithType;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatures;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatures;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionCreated;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionDeleted;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionModified;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertyCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;
import org.eclipse.ditto.signals.events.things.FeaturesCreated;
import org.eclipse.ditto.signals.events.things.FeaturesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

import com.typesafe.config.Config;

import akka.ConfigurationException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.sharding.ClusterSharding;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import akka.persistence.SnapshotOffer;
import scala.concurrent.duration.Duration;

/**
 * PersistentActor which "knows" the state of a single {@link Thing}.
 */
public final class ThingPersistenceActor extends AbstractPersistentActor implements ThingPersistenceActorInterface {

    /**
     * The prefix of the persistenceId for Things.
     */
    public static final String PERSISTENCE_ID_PREFIX = "thing:";

    /**
     * The ID of the journal plugin this persistence actor uses.
     */
    public static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-things-journal";

    /**
     * The ID of the snapshot plugin this persistence actor uses.
     */
    public static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-things-snapshots";

    public static final String UNHANDLED_MESSAGE_TEMPLATE =
            "This Thing Actor did not handle the requested Thing with ID <{0}>!";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private static final ThingEventHandlers thingEventHandlers = new ThingEventHandlers();

    private final String thingId;
    private final ActorRef pubSubMediator;
    private final java.time.Duration activityCheckInterval;
    private final java.time.Duration activityCheckDeletedInterval;
    private final Receive handleThingEvents;
    private final ThingSnapshotter<?, ?> thingSnapshotter;
    private final long snapshotThreshold;
    private long accessCounter;
    private Cancellable activityChecker;
    private final AtomicReference<Thing> thing = new AtomicReference<>();

    ThingPersistenceActor(final String thingId,
            final ActorRef pubSubMediator,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        this.thingId = thingId;
        this.pubSubMediator = pubSubMediator;

        final Config config = getContext().system().settings().config();
        activityCheckInterval = config.getDuration(ConfigKeys.Thing.ACTIVITY_CHECK_INTERVAL);
        activityCheckDeletedInterval = config.getDuration(ConfigKeys.Thing.ACTIVITY_CHECK_DELETED_INTERVAL);

        // Activity checking
        final long configuredSnapshotThreshold = config.getLong(ConfigKeys.Thing.SNAPSHOT_THRESHOLD);
        if (configuredSnapshotThreshold < 0) {
            throw new ConfigurationException(String.format("Config setting '%s' must be positive, but is: %d.",
                    ConfigKeys.Thing.SNAPSHOT_THRESHOLD, configuredSnapshotThreshold));
        }
        snapshotThreshold = configuredSnapshotThreshold;

        // Snapshotting
        final java.time.Duration snapshotInterval = config.getDuration(ConfigKeys.Thing.SNAPSHOT_INTERVAL);
        final boolean snapshotDeleteOld = config.getBoolean(ConfigKeys.Thing.SNAPSHOT_DELETE_OLD);
        final boolean eventsDeleteOld = config.getBoolean(ConfigKeys.Thing.EVENTS_DELETE_OLD);
        thingSnapshotter =
                thingSnapshotterCreate.apply(this, pubSubMediator, snapshotDeleteOld, eventsDeleteOld, log,
                        snapshotInterval);

        handleThingEvents = ReceiveBuilder.create().matchAny(event -> {
            final Thing modified = thingEventHandlers.handle(event, thing(), getRevisionNumber());
            if (modified != null) {
                thing.set(modified);
            }
        }).build();
    }

    private Thing thing() {
        return thing.get();
    }

    private void setThing(final Thing thing) {
        this.thing.set(thing);
    }

    /**
     * Creates Akka configuration object {@link Props} for this ThingPersistenceActor.
     *
     * @param thingId the Thing ID this Actor manages.
     * @param pubSubMediator the PubSub mediator actor.
     * @param thingSnapshotterCreate creator of {@code ThingSnapshotter} objects.
     * @return the Akka configuration Props object
     */
    public static Props props(final String thingId,
            final ActorRef pubSubMediator,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        return Props.create(ThingPersistenceActor.class, new Creator<ThingPersistenceActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingPersistenceActor create() {
                return new ThingPersistenceActor(thingId, pubSubMediator, thingSnapshotterCreate);
            }
        });
    }

    /**
     * Creates a default Akka configuration object {@link Props} for this ThingPersistenceActor using sudo commands
     * for external snapshot requests.
     *
     * @param thingId the Thing ID this Actor manages.
     * @param pubSubMediator the PubSub mediator actor.
     * @return the Akka configuration Props object
     */
    public static Props props(final String thingId, final ActorRef pubSubMediator) {
        return Props.create(ThingPersistenceActor.class, new Creator<ThingPersistenceActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingPersistenceActor create() {
                return new ThingPersistenceActor(thingId, pubSubMediator, DittoThingSnapshotter::getInstance);
            }
        });
    }

    /**
     * Retrieves the ShardRegion of "Things". ThingCommands can be sent to this region which handles dispatching them
     * in the cluster (onto the cluster node containing the shard).
     *
     * @param system the ActorSystem in which to lookup the ShardRegion.
     * @return the ActorRef to the ShardRegion.
     */
    public static ActorRef getShardRegion(final ActorSystem system) {
        return ClusterSharding.get(system).shardRegion(ThingsMessagingConstants.SHARD_REGION);
    }

    private static Instant eventTimestamp() {
        return Instant.now();
    }

    private static Thing enhanceThingWithLifecycle(final Thing thing) {
        final ThingBuilder.FromCopy thingBuilder = ThingsModelFactory.newThingBuilder(thing);
        if (!thing.getLifecycle().isPresent()) {
            thingBuilder.setLifecycle(ThingLifecycle.ACTIVE);
        }

        return thingBuilder.build();
    }

    private long getRevisionNumber() {
        return lastSequenceNr();
    }

    @Nonnull
    @Override
    public Thing getThing() {
        return thing();
    }

    @Nonnull
    @Override
    public String getThingId() {
        return thingId;
    }

    private void scheduleCheckForThingActivity(final long intervalInSeconds) {
        log.debug("Scheduling for Activity Check in <{}> seconds.", intervalInSeconds);
        // if there is a previous activity checker, cancel it
        if (activityChecker != null) {
            activityChecker.cancel();
        }
        // send a message to ourselves:
        activityChecker = getContext()
                .system()
                .scheduler()
                .scheduleOnce(Duration.apply(intervalInSeconds, TimeUnit.SECONDS), getSelf(),
                        new CheckForActivity(getRevisionNumber(), accessCounter), getContext().dispatcher(), null);
    }

    @Override
    public String persistenceId() {
        return PERSISTENCE_ID_PREFIX + thingId;
    }

    @Override
    public String journalPluginId() {
        return JOURNAL_PLUGIN_ID;
    }

    @Override
    public String snapshotPluginId() {
        return SNAPSHOT_PLUGIN_ID;
    }

    @Override
    public void postStop() {
        super.postStop();
        thingSnapshotter.postStop();
        if (activityChecker != null) {
            activityChecker.cancel();
        }
    }

    @Override
    public Receive createReceive() {
        /*
         * First no Thing for the ID exists at all. Thus the only command this Actor reacts to is CreateThing.
         * This behaviour changes as soon as a Thing was created.
         */
        return new StrategyAwareReceiveBuilder()
                .match(new CreateThingStrategy())
                .matchAny(new MatchAnyDuringInitializeStrategy())
                .setPeekConsumer(getIncomingMessagesLoggerOrNull())
                .build();
    }

    @Override
    public Receive createReceiveRecover() {
        // defines how state is updated during recovery
        return ReceiveBuilder.create()

                // # Snapshot handling
                .match(SnapshotOffer.class, ss -> {
                    log.debug("Got SnapshotOffer: {}", ss);
                    setThing(thingSnapshotter.recoverThingFromSnapshotOffer(ss));
                })

                // # Recovery handling
                .match(RecoveryCompleted.class, rc -> {
                    if (thing() != null) {
                        setThing(enhanceThingWithLifecycle(thing()));
                        log.debug("Thing <{}> was recovered.", thingId);

                        if (isThingActive()) {
                            becomeThingCreatedHandler();
                        } else {
                            // expect life cycle to be DELETED. if it's not, then act as if this thing is deleted.
                            if (!isThingDeleted()) {
                                // life cycle isn't known, act as
                                log.error("Unknown lifecycle state <{}> for Thing <{}>.", thing().getLifecycle(),
                                        thingId);
                            }
                            becomeThingDeletedHandler();
                        }

                    }
                })

//                // # Handle unknown
//                .matchAny(m -> log.warning("Unknown recover message: {}", m))

                .build().orElse(handleThingEvents);
    }

    /*
     * Now as the {@code thing} reference is not {@code null} the strategies which act on this reference can
     * be activated. In return the strategy for the CreateThing command is not needed anymore.
     */
    private void becomeThingCreatedHandler() {
//        final Collection<ReceiveStrategy<?>> thingCreatedStrategies = initThingCreatedStrategies();
//        final Receive receive = new StrategyAwareReceiveBuilder()
//                .matchEach(thingCreatedStrategies)
//                .matchAny(new MatchAnyAfterInitializeStrategy())
//                .setPeekConsumer(getIncomingMessagesLoggerOrNull())
//                .build();
//        final LazyStrategyLoader lazyStrategyLoader = new LazyStrategyLoader();
//        final Receive receive = ReceiveBuilder.create()
//                .match(ThingCommand.class, )
//                .build();
        getContext().become(receive, true);
        getContext().getParent().tell(ThingSupervisorActor.ManualReset.INSTANCE, getSelf());

        scheduleCheckForThingActivity(activityCheckInterval.getSeconds());
        thingSnapshotter.startMaintenanceSnapshots();
    }

    private void becomeThingDeletedHandler() {
        final Collection<ReceiveStrategy<?>> thingDeletedStrategies = initThingDeletedStrategies();
        final Receive receive = new StrategyAwareReceiveBuilder()
                .matchEach(thingDeletedStrategies)
                .matchAny(new ThingNotFoundStrategy())
                .setPeekConsumer(getIncomingMessagesLoggerOrNull())
                .build();

        getContext().become(receive, true);
        getContext().getParent().tell(ThingSupervisorActor.ManualReset.INSTANCE, getSelf());

        /* check in the next X minutes and therefore
         * - stay in-memory for a short amount of minutes after deletion
         * - get a Snapshot when removed from memory
         */
        scheduleCheckForThingActivity(activityCheckDeletedInterval.getSeconds());
        thingSnapshotter.stopMaintenanceSnapshots();
    }

    private Collection<ReceiveStrategy<?>> initThingDeletedStrategies() {
        final Collection<ReceiveStrategy<?>> result = new ArrayList<>();
        result.add(new CreateThingStrategy());

        // TakeSnapshot
        result.addAll(thingSnapshotter.strategies());

        // Persistence specific
        result.add(new CheckForActivityStrategy());

        return result;
    }

    private <A extends ThingModifiedEvent> void persistAndApplyEvent(final A event, final Consumer<A> handler) {

        final A modifiedEvent;
        if (thing() != null) {
            // set version of event to the version of the thing
            final DittoHeaders newHeaders = event.getDittoHeaders().toBuilder()
                    .schemaVersion(thing().getImplementedSchemaVersion())
                    .build();
            modifiedEvent = (A) event.setDittoHeaders(newHeaders);
        } else {
            modifiedEvent = event;
        }

        if (modifiedEvent.getDittoHeaders().isDryRun()) {
            handler.accept(modifiedEvent);
        } else {
            persistEvent(modifiedEvent, persistedEvent -> {
                // after the event was persisted, apply the event on the current actor state
                applyEvent(persistedEvent);

                handler.accept(persistedEvent);
            });
        }
    }

    private <A extends ThingModifiedEvent> void persistEvent(final A event, final Consumer<A> handler) {
        LogUtil.enhanceLogWithCorrelationId(log, event.getDittoHeaders().getCorrelationId());
        log.debug("About to persist Event <{}>", event.getType());

        persist(event, persistedEvent -> {
            LogUtil.enhanceLogWithCorrelationId(log, event.getDittoHeaders().getCorrelationId());
            log.info("Successfully persisted Event <{}>", event.getType());

            /* the event has to be applied before creating the snapshot, otherwise a snapshot with new
               sequence no (e.g. 2), but old thing revision no (e.g. 1) will be created -> can lead to serious
               aftereffects.
             */
            handler.accept(persistedEvent);

            // save a snapshot if there were too many changes since the last snapshot
            if (snapshotThresholdPassed()) {
                thingSnapshotter.takeSnapshotInternal();
            }
        });
    }

    private boolean snapshotThresholdPassed() {
        if (thingSnapshotter.getLatestSnapshotSequenceNr() > 0) {
            return (getRevisionNumber() - thingSnapshotter.getLatestSnapshotSequenceNr()) > snapshotThreshold;
        } else {
            // there is no snapshot; count the sequence numbers from 0.
            return (getRevisionNumber() + 1) > snapshotThreshold;
        }
    }

    private <A extends ThingModifiedEvent> void applyEvent(final A event) {
        handleThingEvents.onMessage().apply(event);
        notifySubscribers(event);
    }

    private long nextRevision() {
        return getRevisionNumber() + 1;
    }

    /**
     * @return Whether the lifecycle of the Thing is active.
     */
    public boolean isThingActive() {
        return thing().hasLifecycle(ThingLifecycle.ACTIVE);
    }

    @Override
    public boolean isThingDeleted() {
        return null == thing() || thing().hasLifecycle(ThingLifecycle.DELETED);
    }

    private void notifySubscribers(final ThingEvent event) {
        // publish the event in the cluster
        // publish via cluster pubSub (as we cannot expect that Websocket sessions interested in this event
        // are running on the same cluster node):
        pubSubMediator.tell(new DistributedPubSubMediator.Publish(ThingEvent.TYPE_PREFIX, event, true), getSelf());
    }


    private void notifySender(final WithDittoHeaders message) {
        notifySender(getSender(), message);
    }

    private void notifySender(final ActorRef sender, final WithDittoHeaders message) {
        accessCounter++;
        sender.tell(message, getSelf());
    }

    private Consumer<Object> getIncomingMessagesLoggerOrNull() {
        if (isLogIncomingMessages()) {
            return new LogIncomingMessagesConsumer();
        }
        return null;
    }

    /**
     * Indicates whether the logging of incoming messages is enabled by config or not.
     *
     * @return {@code true} if information about incoming messages should be logged, {@code false} else.
     */
    private boolean isLogIncomingMessages() {
        final ActorSystem actorSystem = getContext().getSystem();
        final Config config = actorSystem.settings().config();

        return config.hasPath(ConfigKeys.THINGS_LOG_INCOMING_MESSAGES) &&
                config.getBoolean(ConfigKeys.THINGS_LOG_INCOMING_MESSAGES);
    }

    /**
     * Message the ThingPersistenceActor can send to itself to check for activity of the Actor and terminate itself
     * if there was no activity since the last check.
     */
    static final class CheckForActivity {

        private final long currentSequenceNr;
        private final long currentAccessCounter;

        /**
         * Constructs a new {@code CheckForActivity} message containing the current "lastSequenceNo" of the
         * ThingPersistenceActor.
         *
         * @param currentSequenceNr the current {@code lastSequenceNr()} of the ThingPersistenceActor.
         * @param currentAccessCounter the current {@code accessCounter} of the ThingPersistenceActor.
         */
        public CheckForActivity(final long currentSequenceNr, final long currentAccessCounter) {
            this.currentSequenceNr = currentSequenceNr;
            this.currentAccessCounter = currentAccessCounter;
        }

        /**
         * Returns the current {@code ThingsModelFactory.lastSequenceNr()} of the ThingPersistenceActor.
         *
         * @return the current {@code ThingsModelFactory.lastSequenceNr()} of the ThingPersistenceActor.
         */
        public long getCurrentSequenceNr() {
            return currentSequenceNr;
        }

        /**
         * Returns the current {@code accessCounter} of the ThingPersistenceActor.
         *
         * @return the current {@code accessCounter} of the ThingPersistenceActor.
         */
        public long getCurrentAccessCounter() {
            return currentAccessCounter;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final CheckForActivity that = (CheckForActivity) o;
            return Objects.equals(currentSequenceNr, that.currentSequenceNr) &&
                    Objects.equals(currentAccessCounter, that.currentAccessCounter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(currentSequenceNr, currentAccessCounter);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" + "currentSequenceNr=" + currentSequenceNr +
                    ", currentAccessCounter=" + currentAccessCounter + "]";
        }

    }

    /**
     * This strategy handles the {@link RetrieveAttributes} command.
     */
    @NotThreadSafe
    private final class RetrieveAttributesStrategy extends AbstractThingCommandStrategy<RetrieveAttributes> {

        /**
         * Constructs a new {@code RetrieveAttributesStrategy} object.
         */
        public RetrieveAttributesStrategy() {
            super(RetrieveAttributes.class, log);
        }

        @Override
        protected void doApply(final RetrieveAttributes command) {
            final Optional<Attributes> optionalAttributes = thing().getAttributes();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            if (optionalAttributes.isPresent()) {
                final Attributes attributes = optionalAttributes.get();
                final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
                final JsonObject attributesJson = selectedFields
                        .map(sf -> attributes.toJson(command.getImplementedSchemaVersion(), sf))
                        .orElseGet(() -> attributes.toJson(command.getImplementedSchemaVersion()));
                notifySender(RetrieveAttributesResponse.of(thingId, attributesJson, dittoHeaders));
            } else {
                attributesNotFound(dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link RetrieveAttribute} command.
     */
    @NotThreadSafe
    private final class RetrieveAttributeStrategy extends AbstractThingCommandStrategy<RetrieveAttribute> {

        /**
         * Constructs a new {@code RetrieveAttributeStrategy} object.
         */
        public RetrieveAttributeStrategy() {
            super(RetrieveAttribute.class, log);
        }

        @Override
        protected void doApply(final RetrieveAttribute command) {
            final Optional<Attributes> optionalAttributes = thing().getAttributes();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            if (optionalAttributes.isPresent()) {
                final Attributes attributes = optionalAttributes.get();
                final JsonPointer jsonPointer = command.getAttributePointer();
                final Optional<JsonValue> jsonValue = attributes.getValue(jsonPointer);
                if (jsonValue.isPresent()) {
                    notifySender(RetrieveAttributeResponse.of(thingId, jsonPointer, jsonValue.get(), dittoHeaders));
                } else {
                    attributeNotFound(jsonPointer, dittoHeaders);
                }
            } else {
                attributesNotFound(dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link ModifyFeatures} command.
     */
    @NotThreadSafe
    private final class ModifyFeaturesStrategy extends AbstractThingCommandStrategy<ModifyFeatures> {

        /**
         * Constructs a new {@code ModifyFeaturesStrategy} object.
         */
        public ModifyFeaturesStrategy() {
            super(ModifyFeatures.class, log);
        }

        @Override
        protected void doApply(final ModifyFeatures command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final ThingModifiedEvent eventToPersist;
            final ThingModifyCommandResponse response;

            if (thing().getFeatures().isPresent()) {
                eventToPersist = FeaturesModified.of(command.getId(), command.getFeatures(), nextRevision(),
                        eventTimestamp(), dittoHeaders);
                response = ModifyFeaturesResponse.modified(thingId, dittoHeaders);
            } else {
                eventToPersist = FeaturesCreated.of(command.getId(), command.getFeatures(), nextRevision(),
                        eventTimestamp(), dittoHeaders);
                response = ModifyFeaturesResponse.created(thingId, command.getFeatures(), dittoHeaders);
            }

            persistAndApplyEvent(eventToPersist, event -> notifySender(response));
        }

    }

    /**
     * This strategy handles the {@link ModifyFeature} command.
     */
    @NotThreadSafe
    private final class ModifyFeatureStrategy extends AbstractThingCommandStrategy<ModifyFeature> {

        /**
         * Constructs a new {@code ModifyFeatureStrategy} object.
         */
        public ModifyFeatureStrategy() {
            super(ModifyFeature.class, log);
        }

        @Override
        protected void doApply(final ModifyFeature command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Features> features = thing().getFeatures();
            final ThingModifiedEvent eventToPersist;
            final ThingModifyCommandResponse response;

            if (features.isPresent() && features.get().getFeature(command.getFeatureId()).isPresent()) {
                eventToPersist = FeatureModified.of(command.getId(), command.getFeature(), nextRevision(),
                        eventTimestamp(), dittoHeaders);
                response = ModifyFeatureResponse.modified(thingId, command.getFeatureId(), dittoHeaders);
            } else {
                eventToPersist = FeatureCreated.of(command.getId(), command.getFeature(), nextRevision(),
                        eventTimestamp(), dittoHeaders);
                response = ModifyFeatureResponse.created(thingId, command.getFeature(), dittoHeaders);
            }

            persistAndApplyEvent(eventToPersist, event -> notifySender(response));
        }

    }

    /**
     * This strategy handles the {@link DeleteFeatures} command.
     */
    @NotThreadSafe
    private final class DeleteFeaturesStrategy extends AbstractThingCommandStrategy<DeleteFeatures> {

        /**
         * Constructs a new {@code DeleteFeaturesStrategy} object.
         */
        public DeleteFeaturesStrategy() {
            super(DeleteFeatures.class, log);
        }

        @Override
        protected void doApply(final DeleteFeatures command) {
            if (thing().getFeatures().isPresent()) {
                final DittoHeaders dittoHeaders = command.getDittoHeaders();
                final FeaturesDeleted featuresDeleted =
                        FeaturesDeleted.of(thingId, nextRevision(), eventTimestamp(), dittoHeaders);

                persistAndApplyEvent(featuresDeleted,
                        event -> notifySender(DeleteFeaturesResponse.of(thingId, dittoHeaders)));
            } else {
                featuresNotFound(command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link DeleteFeature} command.
     */
    @NotThreadSafe
    private final class DeleteFeatureStrategy extends AbstractThingCommandStrategy<DeleteFeature> {

        /**
         * Constructs a new {@code DeleteFeatureStrategy} object.
         */
        public DeleteFeatureStrategy() {
            super(DeleteFeature.class, log);
        }

        @Override
        protected void doApply(final DeleteFeature command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<String> featureIdOptional = thing().getFeatures()
                    .flatMap(features -> features.getFeature(command.getFeatureId()))
                    .map(Feature::getId);

            if (featureIdOptional.isPresent()) {
                final FeatureDeleted featureDeleted = FeatureDeleted.of(thingId, featureIdOptional.get(),
                        nextRevision(), eventTimestamp(), dittoHeaders);
                persistAndApplyEvent(featureDeleted,
                        event -> notifySender(DeleteFeatureResponse.of(thingId, command.getFeatureId(), dittoHeaders)));
            } else {
                featureNotFound(command.getFeatureId(), dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link RetrieveFeatures} command.
     */
    @NotThreadSafe
    private final class RetrieveFeaturesStrategy extends AbstractThingCommandStrategy<RetrieveFeatures> {

        /**
         * Constructs a new {@code RetrieveFeaturesStrategy} object.
         */
        public RetrieveFeaturesStrategy() {
            super(RetrieveFeatures.class, log);
        }

        @Override
        protected void doApply(final RetrieveFeatures command) {
            final Optional<Features> optionalFeatures = thing().getFeatures();
            if (optionalFeatures.isPresent()) {
                final Features features = optionalFeatures.get();
                final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
                final JsonObject featuresJson = selectedFields
                        .map(sf -> features.toJson(command.getImplementedSchemaVersion(), sf))
                        .orElseGet(() -> features.toJson(command.getImplementedSchemaVersion()));
                notifySender(getSender(),
                        RetrieveFeaturesResponse.of(thingId, featuresJson, command.getDittoHeaders()));
            } else {
                featuresNotFound(command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link RetrieveFeature} command.
     */
    @NotThreadSafe
    private final class RetrieveFeatureStrategy extends AbstractThingCommandStrategy<RetrieveFeature> {

        /**
         * Constructs a new {@code RetrieveFeatureStrategy} object.
         */
        public RetrieveFeatureStrategy() {
            super(RetrieveFeature.class, log);
        }

        @Override
        protected void doApply(final RetrieveFeature command) {
            final Optional<Feature> feature =
                    thing().getFeatures().flatMap(fs -> fs.getFeature(command.getFeatureId()));
            if (feature.isPresent()) {
                final Feature f = feature.get();
                final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
                final JsonObject featureJson = selectedFields
                        .map(sf -> f.toJson(command.getImplementedSchemaVersion(), sf))
                        .orElseGet(() -> f.toJson(command.getImplementedSchemaVersion()));
                notifySender(getSender(), RetrieveFeatureResponse.of(thingId, command.getFeatureId(), featureJson,
                        command.getDittoHeaders()));
            } else {
                featureNotFound(command.getFeatureId(), command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link ModifyFeatureDefinition} command.
     */
    @NotThreadSafe
    private final class ModifyFeatureDefinitionStrategy extends AbstractThingCommandStrategy<ModifyFeatureDefinition> {

        /**
         * Constructs a new {@code ModifyFeatureDefinitionStrategy} object.
         */
        public ModifyFeatureDefinitionStrategy() {
            super(ModifyFeatureDefinition.class, log);
        }

        @Override
        protected void doApply(final ModifyFeatureDefinition command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Features> features = thing().getFeatures();

            if (features.isPresent()) {
                final Optional<Feature> feature = features.get().getFeature(command.getFeatureId());

                if (feature.isPresent()) {
                    final ThingModifiedEvent eventToPersist;
                    final ThingModifyCommandResponse response;

                    if (feature.get().getDefinition().isPresent()) {
                        eventToPersist = FeatureDefinitionModified.of(command.getId(), command.getFeatureId(),
                                command.getDefinition(), nextRevision(), eventTimestamp(), dittoHeaders);
                        response =
                                ModifyFeatureDefinitionResponse.modified(thingId, command.getFeatureId(), dittoHeaders);
                    } else {
                        eventToPersist = FeatureDefinitionCreated.of(command.getId(), command.getFeatureId(),
                                command.getDefinition(), nextRevision(), eventTimestamp(), dittoHeaders);
                        response = ModifyFeatureDefinitionResponse.created(thingId, command.getFeatureId(),
                                command.getDefinition(), dittoHeaders);
                    }

                    persistAndApplyEvent(eventToPersist, event -> notifySender(response));
                } else {
                    featureNotFound(command.getFeatureId(), command.getDittoHeaders());
                }
            } else {
                featureNotFound(command.getFeatureId(), command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link DeleteFeatureDefinition} command.
     */
    @NotThreadSafe
    private final class DeleteFeatureDefinitionStrategy extends AbstractThingCommandStrategy<DeleteFeatureDefinition> {

        /**
         * Constructs a new {@code DeleteFeatureDefinitionStrategy} object.
         */
        public DeleteFeatureDefinitionStrategy() {
            super(DeleteFeatureDefinition.class, log);
        }

        @Override
        protected void doApply(final DeleteFeatureDefinition command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Features> features = thing().getFeatures();

            if (features.isPresent()) {
                final Optional<Feature> feature = features.get().getFeature(command.getFeatureId());

                if (feature.isPresent()) {
                    if (feature.get().getDefinition().isPresent()) {
                        final FeatureDefinitionDeleted definitionDeleted =
                                FeatureDefinitionDeleted.of(command.getThingId(), command.getFeatureId(),
                                        nextRevision(), eventTimestamp(), dittoHeaders);
                        persistAndApplyEvent(definitionDeleted, event -> notifySender(
                                DeleteFeatureDefinitionResponse.of(thingId, command.getFeatureId(), dittoHeaders)));
                    } else {
                        featureDefinitionNotFound(command.getFeatureId(), dittoHeaders);
                    }
                } else {
                    featureNotFound(command.getFeatureId(), dittoHeaders);
                }
            } else {
                featureNotFound(command.getFeatureId(), dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link RetrieveFeatureDefinition} command.
     */
    @NotThreadSafe
    private final class RetrieveFeatureDefinitionStrategy
            extends AbstractThingCommandStrategy<RetrieveFeatureDefinition> {

        /**
         * Constructs a new {@code RetrieveFeatureDefinitionStrategy} object.
         */
        public RetrieveFeatureDefinitionStrategy() {
            super(RetrieveFeatureDefinition.class, log);
        }

        @Override
        protected void doApply(final RetrieveFeatureDefinition command) {
            final Optional<Features> optionalFeatures = thing().getFeatures();

            if (optionalFeatures.isPresent()) {
                final Optional<FeatureDefinition> optionalDefinition = optionalFeatures.flatMap(features -> features
                        .getFeature(command.getFeatureId()))
                        .flatMap(Feature::getDefinition);
                if (optionalDefinition.isPresent()) {
                    final FeatureDefinition definition = optionalDefinition.get();
                    notifySender(RetrieveFeatureDefinitionResponse.of(thingId, command.getFeatureId(), definition,
                            command.getDittoHeaders()));
                } else {
                    featureDefinitionNotFound(command.getFeatureId(), command.getDittoHeaders());
                }
            } else {
                featureNotFound(command.getFeatureId(), command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link ModifyFeatureProperties} command.
     */
    @NotThreadSafe
    private final class ModifyFeaturePropertiesStrategy extends AbstractThingCommandStrategy<ModifyFeatureProperties> {

        /**
         * Constructs a new {@code ModifyFeaturePropertiesStrategy} object.
         */
        public ModifyFeaturePropertiesStrategy() {
            super(ModifyFeatureProperties.class, log);
        }

        @Override
        protected void doApply(final ModifyFeatureProperties command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Features> features = thing().getFeatures();

            final String featureId = command.getFeatureId();
            if (features.isPresent()) {
                final Optional<Feature> feature = features.get().getFeature(featureId);

                if (feature.isPresent()) {
                    final ThingModifiedEvent eventToPersist;
                    final ThingModifyCommandResponse response;

                    final FeatureProperties featureProperties = command.getProperties();
                    if (feature.get().getProperties().isPresent()) {
                        eventToPersist = FeaturePropertiesModified.of(command.getId(), featureId, featureProperties,
                                nextRevision(), eventTimestamp(), dittoHeaders);
                        response = ModifyFeaturePropertiesResponse.modified(thingId, featureId, dittoHeaders);
                    } else {
                        eventToPersist = FeaturePropertiesCreated.of(command.getId(), featureId, featureProperties,
                                nextRevision(), eventTimestamp(), dittoHeaders);
                        response = ModifyFeaturePropertiesResponse.created(thingId, featureId, featureProperties,
                                dittoHeaders);
                    }

                    persistAndApplyEvent(eventToPersist, event -> notifySender(response));
                } else {
                    featureNotFound(featureId, command.getDittoHeaders());
                }
            } else {
                featureNotFound(featureId, command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link ModifyFeatureProperty} command.
     */
    @NotThreadSafe
    private final class ModifyFeaturePropertyStrategy extends AbstractThingCommandStrategy<ModifyFeatureProperty> {

        /**
         * Constructs a new {@code ModifyFeaturePropertyStrategy} object.
         */
        public ModifyFeaturePropertyStrategy() {
            super(ModifyFeatureProperty.class, log);
        }

        @Override
        protected void doApply(final ModifyFeatureProperty command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Features> features = thing().getFeatures();

            final String featureId = command.getFeatureId();
            if (features.isPresent()) {
                final Optional<Feature> feature = features.get().getFeature(featureId);

                if (feature.isPresent()) {
                    final Optional<FeatureProperties> optionalProperties = feature.get().getProperties();
                    final ThingModifiedEvent eventToPersist;
                    final ThingModifyCommandResponse response;

                    final JsonPointer propertyJsonPointer = command.getPropertyPointer();
                    final JsonValue propertyValue = command.getPropertyValue();
                    if (optionalProperties.isPresent() && optionalProperties.get().contains(propertyJsonPointer)) {
                        eventToPersist = FeaturePropertyModified.of(command.getId(), featureId, propertyJsonPointer,
                                propertyValue, nextRevision(), eventTimestamp(), dittoHeaders);
                        response = ModifyFeaturePropertyResponse.modified(thingId, featureId, propertyJsonPointer,
                                dittoHeaders);
                    } else {
                        eventToPersist = FeaturePropertyCreated.of(command.getId(), featureId, propertyJsonPointer,
                                propertyValue, nextRevision(), eventTimestamp(), dittoHeaders);
                        response = ModifyFeaturePropertyResponse.created(thingId, featureId, propertyJsonPointer,
                                propertyValue, dittoHeaders);
                    }

                    persistAndApplyEvent(eventToPersist, event -> notifySender(response));
                } else {
                    featureNotFound(featureId, command.getDittoHeaders());
                }
            } else {
                featureNotFound(featureId, command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link DeleteFeatureProperties} command.
     */
    @NotThreadSafe
    private final class DeleteFeaturePropertiesStrategy extends AbstractThingCommandStrategy<DeleteFeatureProperties> {

        /**
         * Constructs a new {@code DeleteFeaturePropertiesStrategy} object.
         */
        public DeleteFeaturePropertiesStrategy() {
            super(DeleteFeatureProperties.class, log);
        }

        @Override
        protected void doApply(final DeleteFeatureProperties command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Features> features = thing().getFeatures();

            final String featureId = command.getFeatureId();
            if (features.isPresent()) {
                final Optional<Feature> feature = features.get().getFeature(featureId);

                if (feature.isPresent()) {
                    if (feature.get().getProperties().isPresent()) {
                        final FeaturePropertiesDeleted propertiesDeleted =
                                FeaturePropertiesDeleted.of(command.getThingId(), featureId, nextRevision(),
                                        eventTimestamp(), dittoHeaders);
                        persistAndApplyEvent(propertiesDeleted, event -> notifySender(
                                DeleteFeaturePropertiesResponse.of(thingId, featureId, dittoHeaders)));
                    } else {
                        featurePropertiesNotFound(featureId, dittoHeaders);
                    }
                } else {
                    featureNotFound(featureId, dittoHeaders);
                }
            } else {
                featureNotFound(featureId, dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link DeleteFeatureProperty} command.
     */
    @NotThreadSafe
    private final class DeleteFeaturePropertyStrategy extends AbstractThingCommandStrategy<DeleteFeatureProperty> {

        /**
         * Constructs a new {@code DeleteFeaturePropertyStrategy} object.
         */
        public DeleteFeaturePropertyStrategy() {
            super(DeleteFeatureProperty.class, log);
        }

        @Override
        protected void doApply(final DeleteFeatureProperty command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Features> featuresOptional = thing().getFeatures();

            final String featureId = command.getFeatureId();
            if (featuresOptional.isPresent()) {
                final Optional<Feature> featureOptional =
                        featuresOptional.flatMap(features -> features.getFeature(featureId));

                if (featureOptional.isPresent()) {
                    final JsonPointer propertyJsonPointer = command.getPropertyPointer();
                    final Feature feature = featureOptional.get();
                    final boolean containsProperty = feature.getProperties()
                            .filter(featureProperties -> featureProperties.contains(propertyJsonPointer))
                            .isPresent();

                    if (containsProperty) {
                        final FeaturePropertyDeleted propertyDeleted = FeaturePropertyDeleted.of(command.getThingId(),
                                featureId, propertyJsonPointer, nextRevision(), eventTimestamp(), dittoHeaders);

                        persistAndApplyEvent(propertyDeleted, event -> notifySender(
                                DeleteFeaturePropertyResponse.of(thingId, featureId, propertyJsonPointer,
                                        dittoHeaders)));
                    } else {
                        featurePropertyNotFound(featureId, propertyJsonPointer, dittoHeaders);
                    }
                } else {
                    featureNotFound(featureId, dittoHeaders);
                }
            } else {
                featureNotFound(featureId, dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link RetrieveFeatureProperties} command.
     */
    @NotThreadSafe
    private final class RetrieveFeaturePropertiesStrategy
            extends AbstractThingCommandStrategy<RetrieveFeatureProperties> {

        /**
         * Constructs a new {@code RetrieveFeaturePropertiesStrategy} object.
         */
        public RetrieveFeaturePropertiesStrategy() {
            super(RetrieveFeatureProperties.class, log);
        }

        @Override
        protected Result doApply(final RetrieveFeatureProperties command) {
            final Optional<Features> optionalFeatures = thing().getFeatures();

            final String featureId = command.getFeatureId();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            if (optionalFeatures.isPresent()) {
                final Optional<FeatureProperties> optionalProperties = optionalFeatures.flatMap(features -> features
                        .getFeature(featureId))
                        .flatMap(Feature::getProperties);
                if (optionalProperties.isPresent()) {
                    final FeatureProperties properties = optionalProperties.get();
                    final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
                    final JsonObject propertiesJson = selectedFields
                            .map(sf -> properties.toJson(command.getImplementedSchemaVersion(), sf))
                            .orElseGet(() -> properties.toJson(command.getImplementedSchemaVersion()));
                    notifySender(
                            RetrieveFeaturePropertiesResponse.of(thingId, featureId, propertiesJson, dittoHeaders));
                } else {
                    featurePropertiesNotFound(featureId, dittoHeaders);
                }
            } else {
                featureNotFound(featureId, dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link RetrieveFeatureProperty} command.
     */
    @NotThreadSafe
    private final class RetrieveFeaturePropertyStrategy extends AbstractThingCommandStrategy<RetrieveFeatureProperty> {

        /**
         * Constructs a new {@code RetrieveFeaturePropertyStrategy} object.
         */
        public RetrieveFeaturePropertyStrategy() {
            super(RetrieveFeatureProperty.class, log);
        }

        @Override
        protected void doApply(final RetrieveFeatureProperty command) {
            final Optional<Feature> featureOptional = thing().getFeatures()
                    .flatMap(features -> features.getFeature(command.getFeatureId()));
            if (featureOptional.isPresent()) {
                final DittoHeaders dittoHeaders = command.getDittoHeaders();
                final Optional<FeatureProperties> optionalProperties = featureOptional.flatMap(Feature::getProperties);
                if (optionalProperties.isPresent()) {
                    final FeatureProperties properties = optionalProperties.get();
                    final JsonPointer jsonPointer = command.getPropertyPointer();
                    final Optional<JsonValue> propertyJson = properties.getValue(jsonPointer);
                    if (propertyJson.isPresent()) {
                        notifySender(RetrieveFeaturePropertyResponse.of(thingId, command.getFeatureId(), jsonPointer,
                                propertyJson.get(), dittoHeaders));
                    } else {
                        featurePropertyNotFound(command.getFeatureId(), jsonPointer, dittoHeaders);
                    }
                } else {
                    featurePropertiesNotFound(command.getFeatureId(), command.getDittoHeaders());
                }
            } else {
                featureNotFound(command.getFeatureId(), command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles all commands which were not explicitly handled beforehand. Those commands are logged as
     * unknown messages and are marked as unhandled.
     */
    @NotThreadSafe
    private final class MatchAnyAfterInitializeStrategy extends AbstractReceiveStrategy<Object> {

        /**
         * Constructs a new {@code MatchAnyAfterInitializeStrategy} object.
         */
        public MatchAnyAfterInitializeStrategy() {
            super(Object.class, log);
        }

        @Override
        protected void doApply(final Object message) {
            log.warning("Unknown message: {}", message);
            unhandled(message);
        }

    }

    /**
     * This strategy handles all messages which were received before the Thing was initialized. Those messages are
     * logged as unexpected messages and cause the actor to be stopped.
     */
    @NotThreadSafe
    private final class MatchAnyDuringInitializeStrategy extends AbstractReceiveStrategy<Object> {

        /**
         * Constructs a new {@code MatchAnyDuringInitializeStrategy} object.
         */
        public MatchAnyDuringInitializeStrategy() {
            super(Object.class);
        }

        @Override
        protected void doApply(final Object message) {
            log.debug("Unexpected message after initialization of actor received: {} - "
                            + "Terminating this actor and sending <{}> to requester ...", message,
                    ThingNotAccessibleException.class.getName());
            final ThingNotAccessibleException.Builder builder = ThingNotAccessibleException.newBuilder(thingId);
            if (message instanceof WithDittoHeaders) {
                builder.dittoHeaders(((WithDittoHeaders) message).getDittoHeaders());
            }
            notifySender(builder.build());
            scheduleCheckForThingActivity(activityCheckInterval.getSeconds());
        }

    }

    /**
     * This strategy handles any messages for a previous deleted Thing.
     */
    @NotThreadSafe
    private final class ThingNotFoundStrategy extends AbstractReceiveStrategy<Object> {

        /**
         * Constructs a new {@code ThingNotFoundStrategy} object.
         */
        public ThingNotFoundStrategy() {
            super(Object.class, log);
        }

        @Override
        protected void doApply(final Object message) {
            final ThingNotAccessibleException.Builder builder = ThingNotAccessibleException.newBuilder(thingId);
            if (message instanceof WithDittoHeaders) {
                builder.dittoHeaders(((WithDittoHeaders) message).getDittoHeaders());
            }
            notifySender(builder.build());
        }

    }

    /**
     * This strategy handles the {@link CheckForActivity} message which checks for activity of the Actor and
     * terminates itself if there was no activity since the last check.
     */
    @NotThreadSafe
    private final class CheckForActivityStrategy extends AbstractReceiveStrategy<CheckForActivity> {

        /**
         * Constructs a new {@code CheckForActivityStrategy} object.
         */
        public CheckForActivityStrategy() {
            super(CheckForActivity.class, log);
        }

        @Override
        protected void doApply(final CheckForActivity message) {
            if (isThingDeleted() && !thingSnapshotter.lastSnapshotCompletedAndUpToDate()) {
                // take a snapshot after a period of inactivity if:
                // - thing is deleted,
                // - the latest snapshot is out of date or is still ongoing.
                thingSnapshotter.takeSnapshotInternal();
                scheduleCheckForThingActivity(activityCheckDeletedInterval.getSeconds());
            } else if (accessCounter > message.getCurrentAccessCounter()) {
                // if the Thing was accessed in any way since the last check
                scheduleCheckForThingActivity(activityCheckInterval.getSeconds());
            } else {
                // safe to shutdown after a period of inactivity if:
                // - thing is active (and taking regular snapshots of itself), or
                // - thing is deleted and the latest snapshot is up to date
                if (isThingActive()) {
                    shutdown("Thing <{}> was not accessed in a while. Shutting Actor down ...", thingId);
                } else {
                    shutdown("Thing <{}> was deleted recently. Shutting Actor down ...", thingId);
                }
            }
        }

        private void shutdown(final String shutdownLogTemplate, final String thingId) {
            log.debug(shutdownLogTemplate, thingId);
            // stop the supervisor (otherwise it'd restart this actor) which causes this actor to stop, too.
            getContext().getParent().tell(PoisonPill.getInstance(), getSelf());
        }

    }

    /**
     * This consumer logs the correlation ID, the thing ID as well as the type of any incoming message.
     */
    private final class LogIncomingMessagesConsumer implements Consumer<Object> {

        @Override
        public void accept(final Object message) {
            if (message instanceof WithDittoHeaders) {
                LogUtil.enhanceLogWithCorrelationId(log, (WithDittoHeaders) message);
            }

            final String messageType = getMessageType(message);
            if (isWithThingId(message)) {
                final String messageThingId = getMessageThingId((WithThingId) message);
                if (isEqualToActorThingId(messageThingId)) {
                    logInfoAboutIncomingMessage(messageType);
                } else {
                    log.warning("<{} got <{}> with different thing ID <{}>!", thingId, messageType, messageThingId);
                }
            } else {
                logInfoAboutIncomingMessage(messageType);
            }
        }

        private String getMessageType(final Object message) {
            if (isCommand(message)) {
                return ((WithType) message).getType();
            } else {
                return message.getClass().getSimpleName();
            }
        }

        private boolean isCommand(final Object message) {
            return message instanceof Command<?>;
        }

        private boolean isWithThingId(final Object message) {
            return message instanceof WithThingId;
        }

        private boolean isEqualToActorThingId(final String messageThingId) {
            return Objects.equals(thingId, messageThingId);
        }

        private String getMessageThingId(final WithThingId withThingId) {
            return withThingId.getThingId();
        }

        private void logInfoAboutIncomingMessage(final String messageType) {
            log.debug("<{}> got <{}>.", thingId, messageType);
        }

    }

}
