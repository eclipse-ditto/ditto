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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.things.persistence.actors.strategies.AbstractReceiveStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.CommandReceiveStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.CreateThingStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.ImmutableContext;
import org.eclipse.ditto.services.things.persistence.actors.strategies.ReceiveStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.ThingNotFoundStrategy;
import org.eclipse.ditto.services.things.persistence.snapshotting.DittoThingSnapshotter;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.services.things.starter.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.WithThingId;
import org.eclipse.ditto.signals.base.WithType;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

import com.typesafe.config.Config;

import akka.ConfigurationException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
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
    private Thing thing;

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

        handleThingEvents = ReceiveBuilder.create().match(ThingEvent.class, event -> {
            final Thing modified = thingEventHandlers.handle(event, thing, getRevisionNumber());
            if (modified != null) {
                thing = modified;
            }
        }).build();
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

//    /**
//     * Retrieves the ShardRegion of "Things". ThingCommands can be sent to this region which handles dispatching them
//     * in the cluster (onto the cluster node containing the shard).
//     *
//     * @param system the ActorSystem in which to lookup the ShardRegion.
//     * @return the ActorRef to the ShardRegion.
//     */
//    public static ActorRef getShardRegion(final ActorSystem system) {
//        return ClusterSharding.get(system).shardRegion(ThingsMessagingConstants.SHARD_REGION);
//    }

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
        return thing;
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
        return handleThingEvents.orElse(ReceiveBuilder.create()

                // # Snapshot handling
                .match(SnapshotOffer.class, ss -> {
                    log.debug("Got SnapshotOffer: {}", ss);
                    thing = thingSnapshotter.recoverThingFromSnapshotOffer(ss);
                })

                // # Recovery handling
                .match(RecoveryCompleted.class, rc -> {
                    if (thing != null) {
                        thing = enhanceThingWithLifecycle(thing);
                        log.debug("Thing <{}> was recovered.", thingId);

                        if (isThingActive()) {
                            becomeThingCreatedHandler();
                        } else {
                            // expect life cycle to be DELETED. if it's not, then act as if this thing is deleted.
                            if (!isThingDeleted()) {
                                // life cycle isn't known, act as
                                log.error("Unknown lifecycle state <{}> for Thing <{}>.", thing.getLifecycle(),
                                        thingId);
                            }
                            becomeThingDeletedHandler();
                        }

                    }
                })
                .matchAny(m -> log.warning("Unknown recover message: {}", m))
                .build());
    }

    /*
     * Now as the {@code thing} reference is not {@code null} the strategies which act on this reference can
     * be activated. In return the strategy for the CreateThing command is not needed anymore.
     */
    private void becomeThingCreatedHandler() {

        final CommandReceiveStrategy commandReceiveStrategy = new CommandReceiveStrategy();

        final Receive receive = ReceiveBuilder
                .create().match(Command.class, command -> {
                    final ImmutableContext context = new ImmutableContext(thingId, thing, nextRevision(), log);
                    final ReceiveStrategy.Result result = commandReceiveStrategy.handle(context, command);

                    if (result.getEventToPersist().isPresent() && result.getResponse().isPresent()) {
                        final ThingModifiedEvent eventToPersist = result.getEventToPersist().get();
                        final WithDittoHeaders response = result.getResponse().get();
                        persistAndApplyEvent(eventToPersist, event -> notifySender(response));
                    }
                    if (result.getResponse().isPresent()) {
                        notifySender(result.getResponse().get());
                    }
                    if (result.getException().isPresent()) {
                        notifySender(result.getException().get());
                    }
                    if (result.isBecomeCreated()) {
                        becomeThingCreatedHandler();
                    }
                    if (result.isBecomeDeleted()) {
                        becomeThingDeletedHandler();
                    }
                })
                .matchAny(unhandled -> {
                    log.warning("Unknown message: {}", unhandled);
                    unhandled(unhandled);
                })
                .build();
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
        if (thing != null) {
            // set version of event to the version of the thing
            final DittoHeaders newHeaders = event.getDittoHeaders().toBuilder()
                    .schemaVersion(thing.getImplementedSchemaVersion())
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
        return thing.hasLifecycle(ThingLifecycle.ACTIVE);
    }

    @Override
    public boolean isThingDeleted() {
        return null == thing || thing.hasLifecycle(ThingLifecycle.DELETED);
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

    /**
     * This strategy handles the {@link CheckForActivity} message
     * which checks for activity of the Actor and
     * terminates itself if there was no activity since the last check.
     */
    @NotThreadSafe
    private final class CheckForActivityStrategy extends AbstractReceiveStrategy<CheckForActivity> {

        /**
         * Constructs a new {@code CheckForActivityStrategy} object.
         */
        CheckForActivityStrategy() {
            super(CheckForActivity.class);
        }

        @Override
        protected Result doApply(final Context context, final CheckForActivity message) {
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
            return ReceiveStrategy.Result.empty();
        }

        private void shutdown(final String shutdownLogTemplate, final String thingId) {
            log.debug(shutdownLogTemplate, thingId);
            // stop the supervisor (otherwise it'd restart this actor) which causes this actor to stop, too.
            getContext().getParent().tell(PoisonPill.getInstance(), getSelf());
        }

    }

//    /**
//     * This strategy handles all commands which were not explicitly handled beforehand. Those commands are logged as
//     * unknown messages and are marked as unhandled.
//     */
//    @NotThreadSafe
//    private final class MatchAnyAfterInitializeStrategy extends AbstractReceiveStrategy<Object> {
//
//        /**
//         * Constructs a new {@code MatchAnyAfterInitializeStrategy} object.
//         */
//        MatchAnyAfterInitializeStrategy() {
//            super(Object.class);
//        }
//
//        @Override
//        protected Result doApply(final Context context, final Object message) {
//            context.log().warning("Unknown message: {}", message);
//            unhandled(message);
//            return ReceiveStrategy.Result.empty();
//        }
//
//    }

    /**
     * This strategy handles all messages which were received before the Thing was initialized. Those messages are
     * logged as unexpected messages and cause the actor to be stopped.
     */
    @NotThreadSafe
    private final class MatchAnyDuringInitializeStrategy extends AbstractReceiveStrategy<Object> {

        /**
         * Constructs a new {@code MatchAnyDuringInitializeStrategy} object.
         */
        MatchAnyDuringInitializeStrategy() {
            super(Object.class);
        }

        @Override
        protected Result doApply(final Context context, final Object message) {
            context.log().debug("Unexpected message after initialization of actor received: {} - "
                            + "Terminating this actor and sending <{}> to requester ...", message,
                    ThingNotAccessibleException.class.getName());
            final ThingNotAccessibleException.Builder builder =
                    ThingNotAccessibleException.newBuilder(context.getThingId());
            if (message instanceof WithDittoHeaders) {
                builder.dittoHeaders(((WithDittoHeaders) message).getDittoHeaders());
            }
            scheduleCheckForThingActivity(activityCheckInterval.getSeconds());
            return ReceiveStrategy.Result.empty();
        }

    }

}
