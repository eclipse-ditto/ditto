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
package org.eclipse.ditto.services.things.persistence.actors;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.model.things.WithThingId;
import org.eclipse.ditto.services.things.common.config.DittoThingsConfig;
import org.eclipse.ditto.services.things.common.config.ThingConfig;
import org.eclipse.ditto.services.utils.persistentactors.commands.AbstractReceiveStrategy;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.commands.CreateThingStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.commands.DefaultContext;
import org.eclipse.ditto.services.things.persistence.actors.strategies.commands.ThingReceiveStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.events.EventHandleStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.events.EventStrategy;
import org.eclipse.ditto.services.things.persistence.serializer.ThingMongoSnapshotAdapter;
import org.eclipse.ditto.services.things.persistence.strategies.ReceiveStrategy;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.services.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.services.utils.persistentactors.AbstractPersistentActorWithTimersAndCleanup;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.services.utils.pubsub.DistributedPub;
import org.eclipse.ditto.signals.base.WithType;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.FI;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.RecoveryCompleted;
import akka.persistence.RecoveryTimedOut;
import akka.persistence.SaveSnapshotFailure;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SnapshotOffer;
import scala.Option;

/**
 * PersistentActor which "knows" the state of a single {@link Thing}.
 */
public final class ThingPersistenceActor extends AbstractPersistentActorWithTimersAndCleanup
        implements ResultVisitor<ThingEvent> {

    /**
     * The prefix of the persistenceId for Things.
     */
    static final String PERSISTENCE_ID_PREFIX = "thing:";

    /**
     * The ID of the journal plugin this persistence actor uses.
     */
    static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-things-journal";

    /**
     * The ID of the snapshot plugin this persistence actor uses.
     */
    static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-things-snapshots";

    private static final AbstractReceiveStrategy COMMAND_RECEIVE_STRATEGY = ThingReceiveStrategy.getInstance();
    private static final CreateThingStrategy CREATE_THING_STRATEGY = CreateThingStrategy.getInstance();

    private final DiagnosticLoggingAdapter log;
    private final ThingId thingId;
    private final DistributedPub<ThingEvent> distributedPub;
    private final SnapshotAdapter<Thing> snapshotAdapter;
    private final Receive handleThingEvents;
    private final ThingConfig thingConfig;
    private final boolean logIncomingMessages;
    private long lastSnapshotRevision;
    private long confirmedSnapshotRevision;

    /**
     * Context for all {@link org.eclipse.ditto.services.utils.persistentactors.commands.AbstractReceiveStrategy} strategies - contains references to fields of {@code this}
     * PersistenceActor.
     */
    private final CommandStrategy.Context<ThingId> defaultContext;

    private long accessCounter;

    @Nullable
    private Thing thing;

    @SuppressWarnings("unused")
    private ThingPersistenceActor(final ThingId thingId, final DistributedPub<ThingEvent> distributedPub,
            final SnapshotAdapter<Thing> snapshotAdapter) {

        this.thingId = thingId;
        this.distributedPub = distributedPub;
        this.snapshotAdapter = snapshotAdapter;
        log = LogUtil.obtain(this);
        thing = null;
        accessCounter = 0L;

        final DittoThingsConfig thingsConfig = DittoThingsConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        thingConfig = thingsConfig.getThingConfig();
        logIncomingMessages = thingsConfig.isLogIncomingMessages();

        lastSnapshotRevision = 0L;
        confirmedSnapshotRevision = 0L;

        final Runnable becomeCreatedRunnable = this::becomeThingCreatedHandler;
        final Runnable becomeDeletedRunnable = this::becomeThingDeletedHandler;
        defaultContext = DefaultContext.getInstance(thingId, log);

        handleThingEvents = ReceiveBuilder.create()
                .match(ThingEvent.class, event -> {
                    final EventStrategy<ThingEvent> eventHandleStrategy = EventHandleStrategy.getInstance();
                    thing = eventHandleStrategy.handle(event, thing, getRevisionNumber());
                }).build();
    }

    /**
     * Creates Akka configuration object {@link Props} for this ThingPersistenceActor.
     *
     * @param thingId the Thing ID this Actor manages.
     * @param distributedPub the distributed-pub access to publish thing events.
     * @param snapshotAdapter the snapshot adapter.
     * @return the Akka configuration Props object
     */
    public static Props props(final ThingId thingId, final DistributedPub<ThingEvent> distributedPub,
            final SnapshotAdapter<Thing> snapshotAdapter) {

        return Props.create(ThingPersistenceActor.class, thingId, distributedPub, snapshotAdapter);
    }

    /**
     * Creates Akka configuration object {@link Props} for this ThingPersistenceActor.
     *
     * @param thingId the Thing ID this Actor manages.
     * @param distributedPub the distributed-pub access to publish thing events.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ThingId thingId, final DistributedPub<ThingEvent> distributedPub) {
        return props(thingId, distributedPub, new ThingMongoSnapshotAdapter());
    }

    private static Thing enhanceThingWithLifecycle(final Thing thing) {
        final ThingBuilder.FromCopy thingBuilder = ThingsModelFactory.newThingBuilder(thing);
        if (!thing.getLifecycle().isPresent()) {
            thingBuilder.setLifecycle(ThingLifecycle.ACTIVE);
        }

        return thingBuilder.build();
    }

    private void scheduleCheckForThingActivity(final java.time.Duration interval) {
        log.debug("Scheduling for Activity Check in <{}> seconds.", interval);
        final Object message = new CheckForActivity(getRevisionNumber(), accessCounter);
        timers().startSingleTimer("activityCheck", message, interval);
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
        log.debug("Stopping PersistenceActor for Thing with ID <{}>.", thingId);
        super.postStop();
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
        if (logIncomingMessages) {
            return new LogIncomingMessagesConsumer();
        }
        return null;
    }

    @Override
    public void onRecoveryFailure(final Throwable cause, final Option<Object> event) {
        super.onRecoveryFailure(cause, event);
        log.error("Recovery Failure for Thing with ID <{}> and cause <{}>.", thingId, cause.getMessage());
    }

    @Override
    public Receive createReceiveRecover() {
        // defines how state is updated during recovery
        return handleThingEvents.orElse(ReceiveBuilder.create()
                // # Snapshot handling
                .match(SnapshotOffer.class, ss -> {
                    log.debug("Got SnapshotOffer: {}", ss);
                    recoverFromSnapshotOffer(ss);
                })

                // # Recovery timeout
                .match(RecoveryTimedOut.class, rto ->
                        log.warning("RecoveryTimeout occurred during recovery for Thing with ID {}", thingId)
                )
                // # Recovery handling
                .match(RecoveryCompleted.class, rc -> {
                    if (thing != null) {
                        thing = enhanceThingWithLifecycle(thing);
                        log.info("Thing <{}> was recovered.", thingId);

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
                .withReceiveFromSuperClass(super.createReceive())
                .matchEach(getTakeSnapshotStrategies())
                .match(new CheckForActivityStrategy())
                .matchAny(new MatchAnyAfterInitializeStrategy())
                .build();

        getContext().become(receive, true);

        scheduleCheckForThingActivity(thingConfig.getActivityCheckConfig().getInactiveInterval());
        scheduleSnapshot();
    }

    private void scheduleSnapshot() {
        final Duration snapshotInterval = thingConfig.getSnapshotConfig().getInterval();
        timers().startPeriodicTimer("takeSnapshot", new TakeSnapshot(), snapshotInterval);
    }

    private void cancelSnapshot() {
        timers().cancel("takeSnapshot");
    }

    @SuppressWarnings("unchecked")
    private void handleCommand(final Command command, final CommandStrategy commandStrategy) {
        final Result result;
        try {
            result = commandStrategy.apply(defaultContext, thing, getNextRevisionNumber(), command);
        } catch (final DittoRuntimeException e) {
            getSender().tell(e, getSelf());
            return;
        }

        // TODO migrate all results
        //result.apply(defaultContext, persistAndApplyConsumer, asyncNotifySender());
        result.accept(this);
    }

    @Override
    public void onMutation(final Command command, final ThingEvent event, final WithDittoHeaders response,
            final boolean becomeCreated, final boolean becomeDeleted) {

        persistAndApplyEvent(event, (persistedEvent, resultingThing) -> {
            notifySender(response);
            if (becomeDeleted) {
                becomeThingDeletedHandler();
            }
            if (becomeCreated) {
                becomeThingCreatedHandler();
            }
        });
    }

    @Override
    public void onQuery(final Command command, final WithDittoHeaders response) {
        notifySender(response);
    }

    @Override
    public void onError(final DittoRuntimeException error) {
        notifySender(error);
    }

    private long getNextRevisionNumber() {
        return getRevisionNumber() + 1;
    }

    private void becomeThingDeletedHandler() {
        final FI.UnitApply<CreateThing> commandHandler = command -> handleCommand(command, CREATE_THING_STRATEGY);
        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create()
                .match(CreateThing.class, CREATE_THING_STRATEGY::isDefined, commandHandler);

        final Receive receive = new StrategyAwareReceiveBuilder(receiveBuilder, log)
                .withReceiveFromSuperClass(super.createReceive())
                .matchEach(getTakeSnapshotStrategies())
                .match(new CheckForActivityStrategy())
                .matchAny(new ThingNotFoundStrategy())
                .setPeekConsumer(getIncomingMessagesLoggerOrNull())
                .build();

        getContext().become(receive, true);

        /* check in the next X minutes and therefore
         * - stay in-memory for a short amount of minutes after deletion
         * - get a Snapshot when removed from memory
         */
        final ActivityCheckConfig activityCheckConfig = thingConfig.getActivityCheckConfig();
        scheduleCheckForThingActivity(activityCheckConfig.getDeletedInterval());
        cancelSnapshot();
    }

    private void persistAndApplyEvent(final ThingEvent event, final BiConsumer<ThingEvent, Thing> handler) {

        final ThingEvent modifiedEvent;
        if (null != thing) {
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

    private void persistEvent(final ThingEvent event, final Consumer<ThingEvent> handler) {
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
                takeSnapshot("snapshot threshold is reached");
            }
        });
    }

    private void takeSnapshot(final String reason) {
        final long revision = getRevisionNumber();
        if (thing != null && lastSnapshotRevision != revision) {
            log.info("Taking snapshot for Thing with ID <{}> and sequence number <{}> because {}.", thingId, revision,
                    reason);

            final Object snapshotSubject = snapshotAdapter.toSnapshotStore(thing);
            saveSnapshot(snapshotSubject);

            lastSnapshotRevision = revision;
        } else if (lastSnapshotRevision == revision) {
            log.debug("Not taking duplicate snapshot for thing <{}> with revision <{}> even if {}.", thing, revision,
                    reason);
        } else {
            log.debug("Not taking snapshot for nonexistent thing <{}> even if {}.", thingId, reason);
        }
    }

    private boolean snapshotThresholdPassed() {
        return getRevisionNumber() - lastSnapshotRevision >= thingConfig.getSnapshotConfig().getThreshold();
    }

    private void applyEvent(final ThingEvent event) {
        handleThingEvents.onMessage().apply(event);
        notifySubscribers(event);
    }

    /**
     * @return Whether the lifecycle of the Thing is active.
     */
    private boolean isThingActive() {
        return null != thing && thing.hasLifecycle(ThingLifecycle.ACTIVE);
    }

    private boolean isThingDeleted() {
        return null == thing || thing.hasLifecycle(ThingLifecycle.DELETED);
    }

    private void notifySubscribers(final ThingEvent event) {
        // publish the event in the cluster
        // sender is not given because the persistence actor expects no reply
        distributedPub.publish(event, ActorRef.noSender());
    }

    private void notifySender(final WithDittoHeaders message) {
        notifySender(getSender(), message);
    }

    private void notifySender(final ActorRef sender, final WithDittoHeaders message) {
        accessCounter++;
        sender.tell(message, getSelf());
    }

    // stop the supervisor (otherwise it'd restart this actor) which causes this actor to stop, too.
    private void stopThisActor() {
        getContext().getParent().tell(ThingSupervisorActor.Control.PASSIVATE, getSelf());
    }

    private Iterable<ReceiveStrategy<?>> getTakeSnapshotStrategies() {
        return Arrays.asList(
                ReceiveStrategy.simple(TakeSnapshot.class, t -> takeSnapshot("snapshot interval has passed")),
                ReceiveStrategy.simple(SaveSnapshotSuccess.class, s -> {
                    log.info("Got {}", s);
                    confirmedSnapshotRevision = s.metadata().sequenceNr();
                }),
                ReceiveStrategy.simple(SaveSnapshotFailure.class, s -> log.error("Got {}", s, s.cause()))
        );
    }

    private void recoverFromSnapshotOffer(final SnapshotOffer snapshotOffer) {
        thing = snapshotAdapter.fromSnapshotStore(snapshotOffer);
        lastSnapshotRevision = confirmedSnapshotRevision = snapshotOffer.metadata().sequenceNr();
    }

    @Override
    protected long getLatestSnapshotSequenceNumber() {
        return confirmedSnapshotRevision;
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
                final ThingId messageThingId = ((WithThingId) message).getThingEntityId();
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
            }
            return message.getClass().getSimpleName();
        }

        private boolean isCommand(final Object message) {
            return message instanceof Command<?>;
        }

        private boolean isWithThingId(final Object message) {
            return message instanceof WithThingId;
        }

        private boolean isEqualToActorThingId(final ThingId messageThingId) {
            return Objects.equals(thingId, messageThingId);
        }

        private void logInfoAboutIncomingMessage(final String messageType) {
            log.debug("<{}> got <{}>.", thingId, messageType);
        }

    }

    /**
     * This strategy handles any messages for a previous deleted Thing.
     */
    @NotThreadSafe
    private final class ThingNotFoundStrategy extends
            org.eclipse.ditto.services.things.persistence.strategies.AbstractReceiveStrategy<Object> {

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
    private final class CheckForActivityStrategy extends
            org.eclipse.ditto.services.things.persistence.strategies.AbstractReceiveStrategy<CheckForActivity> {

        /**
         * Constructs a new {@code CheckForActivityStrategy} object.
         */
        CheckForActivityStrategy() {
            super(CheckForActivity.class, log);
        }

        @Override
        protected void doApply(final CheckForActivity message) {
            if (thingExistsAsDeleted() && lastSnapshotRevision < getRevisionNumber()) {
                // take a snapshot after a period of inactivity if:
                // - thing is deleted,
                // - the latest snapshot is out of date or is still ongoing.
                takeSnapshot("the thing is deleted and has no up-to-date snapshot");
                final ActivityCheckConfig activityCheckConfig = thingConfig.getActivityCheckConfig();
                scheduleCheckForThingActivity(activityCheckConfig.getDeletedInterval());
            } else if (accessCounter > message.getCurrentAccessCounter()) {
                // if the Thing was accessed in any way since the last check
                final ActivityCheckConfig activityCheckConfig = thingConfig.getActivityCheckConfig();
                scheduleCheckForThingActivity(activityCheckConfig.getInactiveInterval());
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

        private void shutdown(final String shutdownLogTemplate, final ThingId thingId) {
            log.debug(shutdownLogTemplate, String.valueOf(thingId));
            stopThisActor();
        }

    }

    /**
     * This strategy handles all commands which were not explicitly handled beforehand. Those commands are logged as
     * unknown messages and are marked as unhandled.
     */
    @NotThreadSafe
    private final class MatchAnyAfterInitializeStrategy extends
            org.eclipse.ditto.services.things.persistence.strategies.AbstractReceiveStrategy<Object> {

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
    private final class MatchAnyDuringInitializeStrategy extends
            org.eclipse.ditto.services.things.persistence.strategies.AbstractReceiveStrategy<Object> {

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
            final ActivityCheckConfig activityCheckConfig = thingConfig.getActivityCheckConfig();
            scheduleCheckForThingActivity(activityCheckConfig.getInactiveInterval());
        }

    }

    private static final class TakeSnapshot {}

}
