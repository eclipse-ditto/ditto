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

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.things.persistence.actors.strategies.commands.CommandReceiveStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.commands.CommandStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.commands.CreateThingStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.commands.DefaultContext;
import org.eclipse.ditto.services.things.persistence.actors.strategies.events.EventHandleStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.events.EventStrategy;
import org.eclipse.ditto.services.things.persistence.snapshotting.DittoThingSnapshotter;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.services.things.persistence.strategies.AbstractReceiveStrategy;
import org.eclipse.ditto.services.things.starter.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.WithThingId;
import org.eclipse.ditto.signals.base.WithType;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
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
import akka.japi.pf.FI;
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
    static final String PERSISTENCE_ID_PREFIX = "thing:";

    /**
     * The ID of the journal plugin this persistence actor uses.
     */
    private static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-things-journal";

    /**
     * The ID of the snapshot plugin this persistence actor uses.
     */
    private static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-things-snapshots";

    private static final CommandReceiveStrategy COMMAND_RECEIVE_STRATEGY = CommandReceiveStrategy.getInstance();
    private static final CreateThingStrategy CREATE_THING_STRATEGY = CreateThingStrategy.getInstance();

    private final String thingId;
    private final ActorRef pubSubMediator;
    private final ThingSnapshotter<?, ?> thingSnapshotter;
    private final DiagnosticLoggingAdapter log;
    private final java.time.Duration activityCheckInterval;
    private final java.time.Duration activityCheckDeletedInterval;
    private final Receive handleThingEvents;
    private final long snapshotThreshold;

    /**
     * Context for all {@link CommandReceiveStrategy} strategies - contains references to fields of {@code this}
     * PersistenceActor.
     */
    private final CommandStrategy.Context defaultContext;

    private long accessCounter;
    private Cancellable activityChecker;
    private Thing thing;

    ThingPersistenceActor(final String thingId, final ActorRef pubSubMediator,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        this.thingId = thingId;
        this.pubSubMediator = pubSubMediator;
        log = LogUtil.obtain(this);
        thing = null;

        final Config config = getContext().system().settings().config();
        activityCheckInterval = config.getDuration(ConfigKeys.Thing.ACTIVITY_CHECK_INTERVAL);
        activityCheckDeletedInterval = config.getDuration(ConfigKeys.Thing.ACTIVITY_CHECK_DELETED_INTERVAL);

        // Activity checking
        snapshotThreshold = getSnapshotThreshold(config);

        // Snapshotting
        thingSnapshotter = getSnapshotter(config, thingSnapshotterCreate);

        final Runnable becomeCreatedRunnable = this::becomeThingCreatedHandler;
        final Runnable becomeDeletedRunnable = this::becomeThingDeletedHandler;
        defaultContext =
                DefaultContext.getInstance(thingId, log, thingSnapshotter, becomeCreatedRunnable,
                        becomeDeletedRunnable);

        handleThingEvents = ReceiveBuilder.create()
                .match(ThingEvent.class, event -> {
                    final EventStrategy<ThingEvent> eventHandleStrategy = EventHandleStrategy.getInstance();
                    thing = eventHandleStrategy.handle(event, thing, getRevisionNumber());
                }).build();
    }

    private static long getSnapshotThreshold(final Config config) {
        final long result = config.getLong(ConfigKeys.Thing.SNAPSHOT_THRESHOLD);
        if (result < 0) {
            throw new ConfigurationException(String.format("Config setting <%s> must be positive but is <%d>!",
                    ConfigKeys.Thing.SNAPSHOT_THRESHOLD, result));
        }
        return result;
    }

    private ThingSnapshotter<?, ?> getSnapshotter(final Config config,
            final ThingSnapshotter.Create snapshotterCreate) {

        final java.time.Duration snapshotInterval = config.getDuration(ConfigKeys.Thing.SNAPSHOT_INTERVAL);
        final boolean snapshotDeleteOld = config.getBoolean(ConfigKeys.Thing.SNAPSHOT_DELETE_OLD);
        final boolean eventsDeleteOld = config.getBoolean(ConfigKeys.Thing.EVENTS_DELETE_OLD);
        return snapshotterCreate.apply(this, pubSubMediator, snapshotDeleteOld, eventsDeleteOld, log, snapshotInterval);
    }

    /**
     * Creates Akka configuration object {@link Props} for this ThingPersistenceActor.
     *
     * @param thingId the Thing ID this Actor manages.
     * @param pubSubMediator the PubSub mediator actor.
     * @param thingSnapshotterCreate creator of {@code ThingSnapshotter} objects.
     * @return the Akka configuration Props object
     */
    public static Props props(final String thingId, final ActorRef pubSubMediator,
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
    static Props props(final String thingId, final ActorRef pubSubMediator) {
        return Props.create(ThingPersistenceActor.class, new Creator<ThingPersistenceActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingPersistenceActor create() {
                return new ThingPersistenceActor(thingId, pubSubMediator, DittoThingSnapshotter::getInstance);
            }
        });
    }

    private static Thing enhanceThingWithLifecycle(final Thing thing) {
        final ThingBuilder.FromCopy thingBuilder = ThingsModelFactory.newThingBuilder(thing);
        if (!thing.getLifecycle().isPresent()) {
            thingBuilder.setLifecycle(ThingLifecycle.ACTIVE);
        }

        return thingBuilder.build();
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

    private long getRevisionNumber() {
        return lastSequenceNr();
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
        final FI.UnitApply<CreateThing> commandHandler = command -> handleCommand(command, CREATE_THING_STRATEGY);
        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create()
                .match(CreateThing.class, CREATE_THING_STRATEGY::isDefined, commandHandler);

        return new StrategyAwareReceiveBuilder(receiveBuilder, log)
                .match(new CheckForActivityStrategy())
                .matchAny(new MatchAnyDuringInitializeStrategy())
                .setPeekConsumer(getIncomingMessagesLoggerOrNull())
                .build();
    }

    @Nullable
    private Consumer<Object> getIncomingMessagesLoggerOrNull() {
        if (isLogIncomingMessages()) {
            return new LogIncomingMessagesConsumer();
        }
        return null;
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
        final FI.UnitApply<Command> commandHandler = command -> handleCommand(command, COMMAND_RECEIVE_STRATEGY);
        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create()
                .match(Command.class, COMMAND_RECEIVE_STRATEGY::isDefined, commandHandler);

        final Receive receive = new StrategyAwareReceiveBuilder(receiveBuilder, log)
                .matchEach(thingSnapshotter.strategies())
                .match(new CheckForActivityStrategy())
                .matchAny(new MatchAnyAfterInitializeStrategy())
                .build();

        getContext().become(receive, true);
        getContext().getParent().tell(ThingSupervisorActor.ManualReset.INSTANCE, getSelf());

        scheduleCheckForThingActivity(activityCheckInterval.getSeconds());
        thingSnapshotter.startMaintenanceSnapshots();
    }

    @SuppressWarnings("unchecked")
    private void handleCommand(final Command command, final CommandStrategy commandStrategy) {
        final CommandStrategy.Result result;
        try {
            result = commandStrategy.apply(defaultContext, thing,
                    getNextRevisionNumber(), command);
        } catch (final DittoRuntimeException e) {
            getSender().tell(e, getSelf());
            return;
        }

        // Unchecked warning suppressed for `persistAndApplyConsumer`.
        // It is actually type-safe with the (infinitely-big) type parameter
        // this.<ThingModifiedEvent<? extends ThingModifiedEvent<? extends ThingModifiedEvent<... ad nauseam ...>>>>
        final BiConsumer<ThingModifiedEvent, BiConsumer<ThingModifiedEvent, Thing>> persistAndApplyConsumer =
                this::persistAndApplyEvent;

        result.apply(defaultContext, persistAndApplyConsumer, asyncNotifySender());
    }

    private long getNextRevisionNumber() {
        return getRevisionNumber() + 1;
    }

    private void becomeThingDeletedHandler() {
        final FI.UnitApply<CreateThing> commandHandler = command -> handleCommand(command, CREATE_THING_STRATEGY);
        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create()
                .match(CreateThing.class, CREATE_THING_STRATEGY::isDefined, commandHandler);

        final Receive receive = new StrategyAwareReceiveBuilder(receiveBuilder, log)
                .matchEach(thingSnapshotter.strategies())
                .match(new CheckForActivityStrategy())
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

    private <A extends ThingModifiedEvent<? extends A>> void persistAndApplyEvent(
            final A event,
            final BiConsumer<A, Thing> handler) {

        final A modifiedEvent;
        if (thing != null) {
            // set version of event to the version of the thing
            final DittoHeaders newHeaders = event.getDittoHeaders().toBuilder()
                    .schemaVersion(thing.getImplementedSchemaVersion())
                    .build();
            modifiedEvent = event.setDittoHeaders(newHeaders);
        } else {
            modifiedEvent = event;
        }

        if (modifiedEvent.getDittoHeaders().isDryRun()) {
            handler.accept(modifiedEvent, thing);
        } else {
            persistEvent(modifiedEvent, persistedEvent -> {
                // after the event was persisted, apply the event on the current actor state
                applyEvent(persistedEvent);

                handler.accept(persistedEvent, thing);
            });
        }
    }

    private <A extends ThingModifiedEvent> void persistEvent(final A event, final Consumer<A> handler) {
        LogUtil.enhanceLogWithCorrelationId(log, event.getDittoHeaders().getCorrelationId());
        log.debug("Persisting Event <{}>.", event.getType());

        persist(event, persistedEvent -> {
            LogUtil.enhanceLogWithCorrelationId(log, event.getDittoHeaders().getCorrelationId());
            log.info("Successfully persisted Event <{}>.", event.getType());

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

    /**
     * @return Whether the lifecycle of the Thing is active.
     */
    private boolean isThingActive() {
        return null != thing && thing.hasLifecycle(ThingLifecycle.ACTIVE);
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

    private Consumer<WithDittoHeaders> asyncNotifySender() {
        accessCounter++;
        final ActorRef sender = getSender();
        final ActorRef self = getSelf();
        return message -> sender.tell(message, self);
    }

    private void notifySender(final ActorRef sender, final WithDittoHeaders message) {
        accessCounter++;
        sender.tell(message, getSelf());
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
     * This strategy handles any messages for a previous deleted Thing.
     */
    @NotThreadSafe
    private final class ThingNotFoundStrategy extends AbstractReceiveStrategy<Object> {

        /**
         * Constructs a new {@code ThingNotFoundStrategy} object.
         */
        ThingNotFoundStrategy() {
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
            super(CheckForActivity.class, log);
        }

        @Override
        protected void doApply(final CheckForActivity message) {
            if (thingExistsAsDeleted() && !thingSnapshotter.lastSnapshotCompletedAndUpToDate()) {
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

        private boolean thingExistsAsDeleted() {
            return null != thing && thing.hasLifecycle(ThingLifecycle.DELETED);
        }

        private void shutdown(final String shutdownLogTemplate, final String thingId) {
            log.debug(shutdownLogTemplate, thingId);
            // stop the supervisor (otherwise it'd restart this actor) which causes this actor to stop, too.
            getContext().getParent().tell(PoisonPill.getInstance(), getSelf());
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
        MatchAnyAfterInitializeStrategy() {
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
        MatchAnyDuringInitializeStrategy() {
            super(Object.class, log);
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

}
