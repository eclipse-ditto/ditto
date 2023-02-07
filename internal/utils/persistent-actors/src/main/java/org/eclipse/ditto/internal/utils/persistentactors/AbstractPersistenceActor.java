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
package org.eclipse.ditto.internal.utils.persistentactors;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.bson.BsonDocument;
import org.eclipse.ditto.base.api.commands.sudo.SudoCommand;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.internal.utils.akka.PingCommand;
import org.eclipse.ditto.internal.utils.akka.PingCommandResponse;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.AbstractMongoEventAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.internal.utils.tracing.span.SpanTagKey;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.RecoveryCompleted;
import akka.persistence.RecoveryTimedOut;
import akka.persistence.SaveSnapshotFailure;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SnapshotOffer;
import akka.persistence.SnapshotProtocol;
import akka.persistence.SnapshotSelectionCriteria;
import akka.persistence.query.EventEnvelope;
import akka.stream.javadsl.Sink;
import scala.Option;

/**
 * PersistentActor which "knows" the state of a single entity supervised by a sharded
 * {@code AbstractPersistenceSupervisor}.
 *
 * @param <C> the base type of the Commands this actor handles
 * @param <S> the entity type this actor manages
 * @param <I> the type of the EntityId this actor manages
 * @param <K> the type of the state of the {@link CommandStrategy.Context}
 * @param <E> the base type of the Events this actor emits
 */
public abstract class AbstractPersistenceActor<
        C extends Command<?>,
        S extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField>,
        I extends EntityId,
        K,
        E extends EventsourcedEvent<? extends E>>
        extends AbstractPersistentActorWithTimersAndCleanup implements ResultVisitor<E> {

    /**
     * An event journal {@code Tag} used to tag journal entries managed by a PersistenceActor as "always alive" meaning
     * that those entities should be always kept in-memory and re-started on a cold-start of the cluster.
     */
    public static final String JOURNAL_TAG_ALWAYS_ALIVE = "always-alive";

    private final SnapshotAdapter<S> snapshotAdapter;
    private final Receive handleEvents;
    private final Receive handleCleanups;
    private final MongoReadJournal mongoReadJournal;
    private long lastSnapshotRevision;
    private long confirmedSnapshotRevision;

    /**
     * The current entity, or null if it was never created.
     */
    @Nullable
    protected S entity;

    /**
     * The entity ID.
     */
    protected final I entityId;

    private long accessCounter = 0L;
    private final BlockedNamespaces blockedNamespaces;

    /**
     * Instantiate the actor.
     *
     * @param entityId the entity ID.
     * @param mongoReadJournal the ReadJournal used for gaining access to historical values of the entity.
     */
    @SuppressWarnings("unchecked")
    protected AbstractPersistenceActor(final I entityId, final MongoReadJournal mongoReadJournal) {
        this.entityId = entityId;
        this.mongoReadJournal = mongoReadJournal;
        final var actorSystem = context().system();
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(actorSystem.settings().config());
        this.snapshotAdapter = SnapshotAdapter.get(actorSystem, dittoExtensionsConfig);
        entity = null;

        lastSnapshotRevision = 0L;
        confirmedSnapshotRevision = 0L;

        handleEvents = ReceiveBuilder.create()
                .match(getEventClass(), event ->
                        entity = getEventStrategy().handle((E) event, entity, getRevisionNumber()))
                .match(EmptyEvent.class, event ->
                        log.withCorrelationId(event).debug("Recovered EmptyEvent: <{}>", event))
                .build();

        handleCleanups = super.createReceive();
        blockedNamespaces = BlockedNamespaces.of(actorSystem);
    }

    /**
     * Invoked whenever the locally cached entity by this PersistenceActor was modified.
     */
    protected void onEntityModified() {
        // default: no-op
    }

    @Override
    public abstract String persistenceId();

    @Override
    public abstract String journalPluginId();

    @Override
    public abstract String snapshotPluginId();

    /**
     * @return class of the events persisted by this actor.
     */
    protected abstract Class<?> getEventClass();

    /**
     * @return the context for handling commands based on the actor's current state.
     */
    protected abstract CommandStrategy.Context<K> getStrategyContext();

    /**
     * @return strategies to handle commands when the entity exists.
     */
    protected abstract CommandStrategy<C, S, K, E> getCreatedStrategy();

    /**
     * @return strategies to handle commands when the entity does not exist.
     */
    protected abstract CommandStrategy<? extends C, S, K, E> getDeletedStrategy();

    /**
     * @return strategies to modify the entity by events.
     */
    protected abstract EventStrategy<E, S> getEventStrategy();

    /**
     * @return configuration for activity check.
     */
    protected abstract ActivityCheckConfig getActivityCheckConfig();

    /**
     * @return configuration for automatic snapshotting.
     */
    protected abstract SnapshotConfig getSnapshotConfig();

    /**
     * Check if the entity exists and is deleted. This is a sufficient condition to make a snapshot before stopping.
     *
     * @return whether the entity exists as deleted.
     */
    protected abstract boolean entityExistsAsDeleted();

    /**
     * @return An exception builder to respond to unexpected commands addressed to a nonexistent entity.
     */
    protected abstract DittoRuntimeExceptionBuilder<?> newNotAccessibleExceptionBuilder();

    /**
     * @param revision the revision which could not be resolved in the entity history.
     * @return An exception builder to respond to unexpected commands addressed to a nonexistent historical entity at a
     * given {@code revision}.
     */
    protected abstract DittoRuntimeExceptionBuilder<?> newHistoryNotAccessibleExceptionBuilder(long revision);

    /**
     * @param timestamp the timestamp which could not be resolved in the entity history.
     * @return An exception builder to respond to unexpected commands addressed to a nonexistent historical entity at a
     * given {@code timestamp}.
     */
    protected abstract DittoRuntimeExceptionBuilder<?> newHistoryNotAccessibleExceptionBuilder(Instant timestamp);

    /**
     * Publish an event.
     *
     * @param previousEntity the previous state of the entity before the event was applied.
     * @param event          the event which was applied.
     */
    protected abstract void publishEvent(@Nullable S previousEntity, E event);

    /**
     * Get the implemented schema version of an entity.
     *
     * @param entity the entity.
     * @return its schema version according to content.
     */
    protected abstract JsonSchemaVersion getEntitySchemaVersion(S entity);

    /**
     * Check whether the headers indicate the necessity of a response for a mutation command.
     *
     * @param dittoHeaders headers of the mutation command.
     * @return whether a response should be sent.
     */
    protected abstract boolean shouldSendResponse(DittoHeaders dittoHeaders);

    /**
     * Check if the present entity requires this actor to stay alive forever.
     *
     * @return whether this actor should always be alive.
     */
    protected abstract boolean isEntityAlwaysAlive();

    /**
     * Callback at the end of recovery. Overridable in subclasses.
     *
     * @param event the event that recovery completed.
     */
    protected void recoveryCompleted(final RecoveryCompleted event) {
        // override to introduce additional logging and other side effects
        becomeCreatedOrDeletedHandler();
    }

    /**
     * Apply the created or deleted behavior according to the current state of the entity.
     */
    protected final void becomeCreatedOrDeletedHandler() {
        // accessible for subclasses; not an extension point.
        if (isEntityActive()) {
            becomeCreatedHandler();
        } else {
            becomeDeletedHandler();
        }
    }

    /**
     * @return the current revision number for event handling.
     */
    protected long getRevisionNumber() {
        return lastSequenceNr();
    }

    @Override
    public void postStop() throws Exception {
        log.debug("Stopping PersistenceActor for entity with ID <{}>.", entityId);
        super.postStop();
    }

    @Override
    public Receive createReceive() {
        return createDeletedBehavior();
    }

    @Override
    public void onRecoveryFailure(final Throwable cause, final Option<Object> event) {
        log.error(cause, "Recovery Failure for entity with ID <{}>", entityId);
    }

    @Override
    public Receive createReceiveRecover() {
        // defines how state is updated during recovery
        return handleEvents.orElse(ReceiveBuilder.create()
                // # Snapshot handling
                .match(SnapshotOffer.class, ss -> {
                    log.debug("Got SnapshotOffer: {}", ss);
                    recoverFromSnapshotOffer(ss);
                })
                // # Recovery timeout
                .match(RecoveryTimedOut.class, rto ->
                        log.warning("RecoveryTimeout occurred during recovery for entity with ID {}", entityId)
                )
                // # Recovery handling
                .match(RecoveryCompleted.class, this::recoveryCompleted)
                .matchAny(m -> log.warning("Unknown recover message: {}", m))
                .build());
    }

    /**
     * Start handling messages for an existing entity and schedule maintenance messages to self.
     */
    protected void becomeCreatedHandler() {
        final CommandStrategy<C, S, K, E> commandStrategy = getCreatedStrategy();

        final Receive receive = handleCleanups.orElse(ReceiveBuilder.create()
                        .match(commandStrategy.getMatchingClass(), this::isHistoricalRetrieveCommand,
                                this::handleHistoricalRetrieveCommand)
                        .match(commandStrategy.getMatchingClass(), commandStrategy::isDefined, this::handleByCommandStrategy)
                        .match(PersistEmptyEvent.class, this::handlePersistEmptyEvent)
                        .match(CheckForActivity.class, this::checkForActivity)
                        .match(PingCommand.class, this::processPingCommand)
                        .matchEquals(Control.TAKE_SNAPSHOT, this::takeSnapshotByInterval)
                        .match(SaveSnapshotSuccess.class, this::saveSnapshotSuccess)
                        .match(SaveSnapshotFailure.class, this::saveSnapshotFailure)
                        .build())
                .orElse(matchAnyAfterInitialization());

        getContext().become(receive);

        scheduleCheckForActivity(getActivityCheckConfig().getInactiveInterval());
        scheduleSnapshot();
    }

    private boolean isHistoricalRetrieveCommand(final C command) {
        final DittoHeaders headers = command.getDittoHeaders();
        return command.getCategory().equals(Command.Category.QUERY) && (
                headers.containsKey(DittoHeaderDefinition.AT_HISTORICAL_REVISION.getKey()) ||
                        headers.containsKey(DittoHeaderDefinition.AT_HISTORICAL_TIMESTAMP.getKey())
        );
    }

    private void handleHistoricalRetrieveCommand(final C command) {

        try {
            FeatureToggle.checkHistoricalApiAccessFeatureEnabled(command.getType(), command.getDittoHeaders());
        } catch (final DittoRuntimeException dre) {
            getSender().tell(dre, getSelf());
            return;
        }

        final CommandStrategy<C, S, K, E> commandStrategy = getCreatedStrategy();
        final EventStrategy<E, S> eventStrategy = getEventStrategy();
        final ActorRef sender = getSender();
        final ActorRef self = getSelf();
        final long atHistoricalRevision = Optional
                .ofNullable(command.getDittoHeaders().get(DittoHeaderDefinition.AT_HISTORICAL_REVISION.getKey()))
                .map(Long::parseLong)
                .orElseGet(this::lastSequenceNr);
        final Instant atHistoricalTimestamp = Optional
                .ofNullable(command.getDittoHeaders().get(DittoHeaderDefinition.AT_HISTORICAL_TIMESTAMP.getKey()))
                .map(Instant::parse)
                .orElse(Instant.EPOCH);

        loadSnapshot(persistenceId(), SnapshotSelectionCriteria.create(
                atHistoricalRevision,
                atHistoricalTimestamp.equals(Instant.EPOCH) ? Long.MAX_VALUE : atHistoricalTimestamp.toEpochMilli(),
                0L,
                0L
        ), getLatestSnapshotSequenceNumber());

        final Duration waitTimeout = Duration.ofSeconds(5);
        final Cancellable cancellableSnapshotLoadTimeout =
                getContext().getSystem().getScheduler().scheduleOnce(waitTimeout, getSelf(), waitTimeout,
                        getContext().getDispatcher(), getSelf());
        getContext().become(ReceiveBuilder.create()
                .match(SnapshotProtocol.LoadSnapshotResult.class, loadSnapshotResult ->
                        historicalRetrieveHandleLoadSnapshotResult(command,
                                commandStrategy,
                                eventStrategy,
                                sender,
                                self,
                                atHistoricalRevision,
                                atHistoricalTimestamp,
                                cancellableSnapshotLoadTimeout,
                                loadSnapshotResult
                        )
                )
                .match(SnapshotProtocol.LoadSnapshotFailed.class, loadSnapshotFailed ->
                        log.warning(loadSnapshotFailed.cause(), "Loading snapshot failed")
                )
                .matchEquals(waitTimeout, wt -> {
                    log.withCorrelationId(command)
                            .warning("Timed out waiting for receiving snapshot result!");
                    becomeCreatedOrDeletedHandler();
                    unstashAll();
                })
                .matchAny(any -> stash())
                .build());
    }

    private void historicalRetrieveHandleLoadSnapshotResult(final C command,
            final CommandStrategy<C, S, K, E> commandStrategy,
            final EventStrategy<E, S> eventStrategy,
            final ActorRef sender,
            final ActorRef self,
            final long atHistoricalRevision,
            final Instant atHistoricalTimestamp,
            final Cancellable cancellableSnapshotLoadTimeout,
            final SnapshotProtocol.LoadSnapshotResult loadSnapshotResult) {

        final Option<S> snapshotEntity = loadSnapshotResult.snapshot()
                .map(snapshotAdapter::fromSnapshotStore);
        final boolean snapshotIsPresent = snapshotEntity.isDefined();

        if (snapshotIsPresent || getLatestSnapshotSequenceNumber() == 0) {
            final long snapshotEntityRevision = snapshotIsPresent ?
                    loadSnapshotResult.snapshot().get().metadata().sequenceNr() : 0L;

            final long fromSequenceNr;
            if (atHistoricalRevision == snapshotEntityRevision) {
                fromSequenceNr = snapshotEntityRevision;
            } else {
                fromSequenceNr = snapshotEntityRevision + 1;
            }

            @Nullable final S entityFromSnapshot = snapshotIsPresent ? snapshotEntity.get() : null;
            mongoReadJournal.currentEventsByPersistenceId(persistenceId(),
                            fromSequenceNr,
                            atHistoricalRevision
                    )
                    .map(AbstractPersistenceActor::mapJournalEntryToEvent)
                    .map(journalEntryEvent -> new EntityWithEvent(
                            eventStrategy.handle((E) journalEntryEvent, entityFromSnapshot, journalEntryEvent.getRevision()),
                            (E) journalEntryEvent
                    ))
                    .takeWhile(entityWithEvent -> {
                        if (atHistoricalTimestamp.equals(Instant.EPOCH)) {
                            // no at-historical-timestamp was specified, so take all up to "at-historical-revision":
                            return true;
                        } else {
                            // take while the timestamps of the events are before the specified "at-historical-timestamp":
                            return entityWithEvent.event.getTimestamp()
                                    .filter(ts -> ts.isBefore(atHistoricalTimestamp))
                                    .isPresent();
                        }
                    })
                    .reduce((ewe1, ewe2) -> new EntityWithEvent(
                            eventStrategy.handle(ewe2.event, ewe2.entity, ewe2.revision),
                            ewe2.event
                    ))
                    .runWith(Sink.foreach(entityWithEvent ->
                                    commandStrategy.apply(getStrategyContext(),
                                            entityWithEvent.entity,
                                            entityWithEvent.revision,
                                            command
                                    ).accept(new HistoricalResultListener(sender,
                                            entityWithEvent.event.getDittoHeaders()))
                            ),
                            getContext().getSystem());
        } else {
            if (!atHistoricalTimestamp.equals(Instant.EPOCH)) {
                sender.tell(newHistoryNotAccessibleExceptionBuilder(atHistoricalTimestamp).build(), self);
            } else {
                sender.tell(newHistoryNotAccessibleExceptionBuilder(atHistoricalRevision).build(), self);
            }
        }

        cancellableSnapshotLoadTimeout.cancel();
        becomeCreatedOrDeletedHandler();
        unstashAll();
    }

    /**
     * Processes a received {@link PingCommand}.
     * May be overwritten in order to hook into processing ping commands with additional functionality.
     *
     * @param ping the ping command.
     */
    protected void processPingCommand(final PingCommand ping) {

        final String journalTag = ping.getPayload()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .orElse(null);
        final String correlationId = ping.getCorrelationId().orElse(null);
        log.withCorrelationId(correlationId)
                .debug("Received ping for this actor with tag <{}>", journalTag);
        getSender().tell(PingCommandResponse.of(correlationId, JsonValue.nullLiteral()), getSelf());
    }

    protected void becomeDeletedHandler() {
        getContext().become(createDeletedBehavior());

        /* check in the next X minutes and therefore
         * - stay in-memory for a short amount of minutes after deletion
         * - get a Snapshot when removed from memory
         */
        scheduleCheckForActivity(getActivityCheckConfig().getDeletedInterval());
        cancelSnapshot();
    }

    /**
     * Persist an event, modify actor state by the event strategy, then invoke the handler.
     *
     * @param event   the event to persist and apply.
     * @param handler what happens afterwards.
     */
    protected void persistAndApplyEvent(final E event, final BiConsumer<E, S> handler) {

        final E modifiedEvent = modifyEventBeforePersist(event);
        if (modifiedEvent.getDittoHeaders().isDryRun()) {
            handler.accept(modifiedEvent, entity);
        } else {
            persistEvent(modifiedEvent, persistedEvent -> {
                // after the event was persisted, apply the event on the current actor state
                applyEvent(persistedEvent);
                handler.accept(persistedEvent, entity);
            });
        }
    }

    /**
     * Allows to modify the passed in {@code event} before {@link #persistEvent(EventsourcedEvent, Consumer)} is invoked.
     * Overwrite this method and call the super method in order to additionally modify the event before persisting it.
     *
     * @param event the event to potentially modify.
     * @return the modified or unmodified event to persist.
     */
    protected E modifyEventBeforePersist(final E event) {
        final E modifiedEvent;
        if (null != entity) {
            // set version of event to the version of the entity
            final DittoHeaders newHeaders = event.getDittoHeaders().toBuilder()
                    .schemaVersion(getEntitySchemaVersion(entity))
                    .build();
            modifiedEvent = event.setDittoHeaders(newHeaders);
        } else {
            modifiedEvent = event;
        }
        return modifiedEvent;
    }

    /**
     * Check for activity. Shutdown actor if it is lacking.
     *
     * @param message the check-for-activity message.
     */
    protected void checkForActivity(final CheckForActivity message) {
        scheduleCheckForActivity(getActivityCheckConfig().getDeletedInterval());
        if (entityExistsAsDeleted() && lastSnapshotRevision < getRevisionNumber()) {
            // take a snapshot after a period of inactivity if:
            // - entity is deleted,
            // - the latest snapshot is out of date or is still ongoing.
            takeSnapshot("the entity is deleted and has no up-to-date snapshot");
        } else if (accessCounter > message.accessCounter) {
            log.debug("Entity <{}> was accessed since last activity check, preventing Actor shutdown.", entityId);
        } else if (isEntityActive() && isEntityAlwaysAlive()) {
            log.debug("Entity <{}> is active and marked as 'always-alive', preventing Actor shutdown.", entityId);
        } else {
            // safe to shutdown after a period of inactivity if:
            // - entity is active (and taking regular snapshots of itself), or
            // - entity is deleted and the latest snapshot is up to date
            if (isEntityActive()) {
                shutdown("Entity <{}> was not accessed in a while. Shutting Actor down ...", entityId);
            } else {
                shutdown("Entity <{}> was deleted recently. Shutting Actor down ...", entityId);
            }
        }
    }

    private void handlePersistEmptyEvent(final PersistEmptyEvent persistEmptyEvent) {
        log.debug("Received PersistEmptyEvent: <{}>", persistEmptyEvent);
        persist(persistEmptyEvent.getEmptyEvent(), event -> log.debug("Persisted EmptyEvent: <{}>", event));
    }

    /**
     * Request parent to shutdown this actor gracefully in a thread-safe manner.
     */
    protected void passivate() {
        getContext().getParent().tell(AbstractPersistenceSupervisor.Control.PASSIVATE, getSelf());
    }

    private Receive createDeletedBehavior() {
        return handleCleanups.orElse(handleByDeletedStrategyReceiveBuilder()
                        .match(CheckForActivity.class, this::checkForActivity)
                        .matchEquals(Control.TAKE_SNAPSHOT, this::takeSnapshotByInterval)
                        .match(SaveSnapshotSuccess.class, this::saveSnapshotSuccess)
                        .match(SaveSnapshotFailure.class, this::saveSnapshotFailure)
                        .build())
                .orElse(matchAnyWhenDeleted());
    }

    /**
     * Schedule the next check for activity.
     *
     * @param interval when to check again.
     */
    protected void scheduleCheckForActivity(final Duration interval) {
        if (interval.isNegative() || interval.isZero()) {
            log.debug("Activity check is disabled: <{}>", interval);
        } else {
            log.debug("Scheduling for Activity Check in <{}>", interval);
            timers().startSingleTimer("activityCheck", new CheckForActivity(accessCounter), interval);
        }
    }

    private void scheduleSnapshot() {
        final Duration snapshotInterval = getSnapshotConfig().getInterval();
        timers().startTimerAtFixedRate("takeSnapshot", Control.TAKE_SNAPSHOT, snapshotInterval);
    }

    private void cancelSnapshot() {
        timers().cancel("takeSnapshot");
    }

    protected void handleByCommandStrategy(final C command) {
        handleByStrategy(command, entity, getCreatedStrategy());
    }

    @SuppressWarnings("unchecked")
    private ReceiveBuilder handleByDeletedStrategyReceiveBuilder() {
        final var deletedStrategy = (CommandStrategy<C, S, K, E>) getDeletedStrategy();
        return ReceiveBuilder.create()
                .match(deletedStrategy.getMatchingClass(), deletedStrategy::isDefined,
                        // get the current deletedStrategy during "matching time" to allow implementing classes
                        // to update the strategy during runtime
                        command -> handleByStrategy(command, entity, (CommandStrategy<C, S, K, E>) getDeletedStrategy()));
    }

    @SuppressWarnings("unchecked")
    private <T extends Command<?>> void handleByStrategy(final T command, @Nullable final S workEntity,
            final CommandStrategy<T, S, K, E> strategy) {
        log.debug("Handling by strategy: <{}>", command);

        final var startedSpan = DittoTracing.newPreparedSpan(
                        command.getDittoHeaders(),
                        SpanOperationName.of("apply_command_strategy")
                )
                .start();

        final var tracedCommand =
                command.setDittoHeaders(DittoHeaders.of(startedSpan.propagateContext(command.getDittoHeaders())));

        accessCounter++;
        Result<E> result;
        try {
            result = strategy.apply(getStrategyContext(), workEntity, getNextRevisionNumber(), (T) tracedCommand);
            result.accept(this);
        } catch (final DittoRuntimeException e) {
            startedSpan.tagAsFailed(e);
            result = ResultFactory.newErrorResult(e, tracedCommand);
            result.accept(this);
        } finally {
            startedSpan.finish();
        }
        reportSudoCommandDone(command);
    }

    @Override
    public void onMutation(final Command<?> command, final E event, final WithDittoHeaders response,
                           final boolean becomeCreated, final boolean becomeDeleted) {

        persistAndApplyEvent(event, (persistedEvent, resultingEntity) -> {
            if (shouldSendResponse(command.getDittoHeaders())) {
                notifySender(response);
            }
            if (becomeDeleted) {
                becomeDeletedHandler();
            }
            if (becomeCreated) {
                becomeCreatedHandler();
            }
        });
    }

    @Override
    public void onQuery(final Command<?> command, final WithDittoHeaders response) {
        if (command.getDittoHeaders().isResponseRequired()) {
            notifySender(response);
        }
    }

    @Override
    public void onError(final DittoRuntimeException error, final Command<?> errorCausingCommand) {
        if (shouldSendResponse(errorCausingCommand.getDittoHeaders())) {
            notifySender(error);
        }
    }

    /**
     * Send a reply and increment access counter.
     *
     * @param sender  recipient of the message.
     * @param message the message.
     */
    protected void notifySender(final ActorRef sender, final WithDittoHeaders message) {
        accessCounter++;
        sender.tell(message, getSelf());
    }

    private long getNextRevisionNumber() {
        return getRevisionNumber() + 1;
    }

    @SuppressWarnings("unchecked")
    private void persistEvent(final E event, final Consumer<E> handler) {
        final var l = log.withCorrelationId(event);
        l.debug("Persisting Event <{}>.", event.getType());

        final var persistOperationSpan = DittoTracing.newPreparedSpan(
                        event.getDittoHeaders(),
                        SpanOperationName.of("persist_event")
                )
                .tag(SpanTagKey.SIGNAL_TYPE.getTagForValue(event.getType()))
                .start();

        persist(
                event.setDittoHeaders(DittoHeaders.of(persistOperationSpan.propagateContext(event.getDittoHeaders()))),
                persistedEvent -> {
                    l.info("Successfully persisted Event <{}> w/ rev: <{}>.",
                            persistedEvent.getType(),
                            getRevisionNumber());
                    persistOperationSpan.finish();

                    /*
                     * The event has to be applied before creating the snapshot, otherwise a snapshot with new
                     * sequence no (e.g. 2), but old entity revision no (e.g. 1) will be created -> can lead to serious
                     * aftereffects.
                     */
                    handler.accept(persistedEvent);
                    onEntityModified();

                    // save a snapshot if there were too many changes since the last snapshot
                    if (snapshotThresholdPassed()) {
                        takeSnapshot("snapshot threshold is reached");
                    }
                }
        );
    }

    private void takeSnapshot(final String reason) {

        if (entityId instanceof NamespacedEntityId namespacedEntityId) {
            final String namespace = namespacedEntityId.getNamespace();
            blockedNamespaces.contains(namespace).thenAccept(namespaceIsBlocked -> {
                if (namespaceIsBlocked) {
                    log.debug("Not taking snapshot for entity <{}> even if {}, because namespace is blocked.",
                            entityId, reason);
                } else {
                    doTakeSnapshot(reason);
                }
            });
        } else {
            doTakeSnapshot(reason);
        }

    }

    private void doTakeSnapshot(final String reason) {
        final long revision = getRevisionNumber();
        if (entity != null && lastSnapshotRevision != revision) {
            log.debug("Taking snapshot for entity with ID <{}> and sequence number <{}> because {}.", entityId,
                    revision,
                    reason);

            final Object snapshotSubject = snapshotAdapter.toSnapshotStore(entity);
            saveSnapshot(snapshotSubject);

            lastSnapshotRevision = revision;
        } else if (lastSnapshotRevision == revision) {
            log.debug("Not taking duplicate snapshot for entity <{}> with revision <{}> even if {}.", entity, revision,
                    reason);
        } else {
            log.debug("Not taking snapshot for nonexistent entity <{}> even if {}.", entityId, reason);
        }
    }

    private boolean snapshotThresholdPassed() {
        return getRevisionNumber() - lastSnapshotRevision >= getSnapshotConfig().getThreshold();
    }

    private void applyEvent(final E event) {
        final S previousEntity = entity;
        handleEvents.onMessage().apply(event);
        publishEvent(previousEntity, event);
    }

    private void notifySender(final WithDittoHeaders message) {
        notifySender(getSender(), message);
    }

    private void takeSnapshotByInterval(final Control takeSnapshot) {
        takeSnapshot("snapshot interval has passed");
    }

    private void saveSnapshotSuccess(final SaveSnapshotSuccess s) {
        log.debug("Got {}", s);
        confirmedSnapshotRevision = s.metadata().sequenceNr();
    }

    private void saveSnapshotFailure(final SaveSnapshotFailure s) {
        log.error(s.cause(), "Got {}", s);
    }

    private void recoverFromSnapshotOffer(final SnapshotOffer snapshotOffer) {
        entity = snapshotAdapter.fromSnapshotStore(snapshotOffer);
        lastSnapshotRevision = confirmedSnapshotRevision = snapshotOffer.metadata().sequenceNr();
    }

    @Override
    protected long getLatestSnapshotSequenceNumber() {
        return confirmedSnapshotRevision;
    }

    private void notAccessible(final WithDittoHeaders withDittoHeaders) {
        final DittoRuntimeExceptionBuilder<?> builder = newNotAccessibleExceptionBuilder()
                .dittoHeaders(withDittoHeaders.getDittoHeaders());
        notifySender(builder.build());
        reportSudoCommandDone(withDittoHeaders);
    }

    private void shutdown(final String shutdownLogTemplate, final I entityId) {
        log.info(shutdownLogTemplate, String.valueOf(entityId));
        passivate();
    }

    private boolean isEntityActive() {
        return entity != null && !entityExistsAsDeleted();
    }

    private void reportSudoCommandDone(final WithDittoHeaders command) {
        if (command instanceof SudoCommand || command.getDittoHeaders().isSudo()) {
            getSudoCommandDoneRecipient().tell(AbstractPersistenceSupervisor.Control.SUDO_COMMAND_DONE, getSelf());
        }
    }

    /**
     * Return the recipient to notify after processing a sudo command.
     *
     * @return The recipient.
     */
    protected ActorRef getSudoCommandDoneRecipient() {
        return getContext().getParent();
    }

    /**
     * Default is to log a warning, may be overwritten by implementations in order to handle additional messages.
     *
     * @return the match-all Receive object.
     */
    protected Receive matchAnyAfterInitialization() {
        return ReceiveBuilder.create()
                .matchAny(message -> log.warning("Unknown message: {}", message))
                .build();
    }

    /**
     * Default is to reply with a not-accessible exception.
     *
     * @return the match-all Receive object.
     */
    protected Receive matchAnyWhenDeleted() {
        return ReceiveBuilder.create()
                .match(WithDittoHeaders.class, this::notAccessible)
                .matchAny(m -> log.info("Received message in 'deleted' state - ignoring!: {}", m))
                .build();
    }

    /**
     * Create a private {@code CheckForActivity} message for unit tests.
     *
     * @param accessCounter the access counter of this message.
     * @return the check-for-activity message.
     */
    public static Object checkForActivity(final long accessCounter) {
        return new CheckForActivity(accessCounter);
    }

    private static EventsourcedEvent<?> mapJournalEntryToEvent(final EventEnvelope eventEnvelope) {

        final BsonDocument event = (BsonDocument) eventEnvelope.event();
        final JsonObject eventAsJsonObject = DittoBsonJson.getInstance()
                .serialize(event);

        final DittoHeaders dittoHeaders = eventAsJsonObject.getValue(AbstractMongoEventAdapter.HISTORICAL_EVENT_HEADERS)
                .map(obj -> DittoHeaders.newBuilder(obj).build())
                .orElseGet(DittoHeaders::empty);
        return (EventsourcedEvent<?>) GlobalEventRegistry.getInstance().parse(eventAsJsonObject, dittoHeaders);
    }

    /**
     * Check if any command is processed.
     */
    protected static final class CheckForActivity {

        private final long accessCounter;

        private CheckForActivity(final long accessCounter) {
            this.accessCounter = accessCounter;
        }

    }

    private enum Control {
        TAKE_SNAPSHOT
    }


    /**
     * Local message this actor may send to itself in order to persist an {@link EmptyEvent} to the event journal,
     * e.g. in order to save a specific journal tag with that event to the event journal.
     */
    @Immutable
    protected static final class PersistEmptyEvent {

        private final EmptyEvent emptyEvent;

        public PersistEmptyEvent(final EmptyEvent emptyEvent) {
            this.emptyEvent = emptyEvent;
        }

        EmptyEvent getEmptyEvent() {
            return emptyEvent;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "emptyEvent=" + emptyEvent +
                    "]";
        }

    }

    @Immutable
    private final class EntityWithEvent {
        @Nullable private final S entity;
        private final long revision;
        private final E event;

        private EntityWithEvent(@Nullable final S entity, final E event) {
            this.entity = entity;
            this.revision = event.getRevision();
            this.event = event;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "entity=" + entity +
                    ", revision=" + revision +
                    ", event=" + event +
                    ']';
        }
    }

    private final class HistoricalResultListener implements ResultVisitor<E> {

        private final ActorRef sender;
        private final DittoHeaders historicalDittoHeaders;

        private HistoricalResultListener(final ActorRef sender, final DittoHeaders historicalDittoHeaders) {
            this.sender = sender;
            this.historicalDittoHeaders = historicalDittoHeaders.toBuilder()
                    .removeHeader(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey())
                    .build();
        }

        @Override
        public void onMutation(final Command<?> command, final E event,
                final WithDittoHeaders response,
                final boolean becomeCreated, final boolean becomeDeleted) {
            throw new UnsupportedOperationException("Mutating historical entity not supported.");
        }

        @Override
        public void onQuery(final Command<?> command, final WithDittoHeaders response) {
            if (command.getDittoHeaders().isResponseRequired()) {
                final WithDittoHeaders theResponseToSend;
                if (response instanceof DittoHeadersSettable<?> dittoHeadersSettable) {
                    final DittoHeaders queryCommandHeaders = response.getDittoHeaders();
                    final DittoHeaders adjustedHeaders = queryCommandHeaders.toBuilder()
                            .putHeader(DittoHeaderDefinition.HISTORICAL_HEADERS.getKey(),
                                    historicalDittoHeaders.toJson().toString())
                            .build();
                    theResponseToSend = dittoHeadersSettable.setDittoHeaders(adjustedHeaders);
                } else {
                    theResponseToSend = response;
                }
                notifySender(sender, theResponseToSend);
            }
        }

        @Override
        public void onError(final DittoRuntimeException error,
                final Command<?> errorCausingCommand) {
            if (shouldSendResponse(errorCausingCommand.getDittoHeaders())) {
                notifySender(sender, error);
            }
        }
    }
}
