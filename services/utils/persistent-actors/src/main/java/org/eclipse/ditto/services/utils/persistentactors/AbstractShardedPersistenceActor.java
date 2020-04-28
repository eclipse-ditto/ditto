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
package org.eclipse.ditto.services.utils.persistentactors;

import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.services.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.events.base.Event;

import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.RecoveryCompleted;
import akka.persistence.RecoveryTimedOut;
import akka.persistence.SaveSnapshotFailure;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SnapshotOffer;
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
public abstract class AbstractShardedPersistenceActor<
        C extends Command,
        S,
        I extends EntityId,
        K,
        E extends Event> extends AbstractPersistentActorWithTimersAndCleanup implements ResultVisitor<E> {

    private final SnapshotAdapter<S> snapshotAdapter;
    private final Receive handleEvents;
    private final Receive handleCleanups;
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

    /**
     * Instantiate the actor.
     *
     * @param entityId the entity ID.
     * @param snapshotAdapter the entity's snapshot adapter.
     */
    protected AbstractShardedPersistenceActor(final I entityId, final SnapshotAdapter<S> snapshotAdapter) {
        this.entityId = entityId;
        this.snapshotAdapter = snapshotAdapter;
        entity = null;

        lastSnapshotRevision = 0L;
        confirmedSnapshotRevision = 0L;

        handleEvents = ReceiveBuilder.create()
                .match(getEventClass(), event -> entity = getEventStrategy().handle(event, entity, getRevisionNumber()))
                .build();

        handleCleanups = super.createReceive();
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
    protected abstract Class<E> getEventClass();

    /**
     * @return the context for handling commands based on the actor's current state.
     */
    protected abstract CommandStrategy.Context<K> getStrategyContext();

    /**
     * @return strategies to handle commands when the entity exists.
     */
    protected abstract CommandStrategy<C, S, K, Result<E>> getCreatedStrategy();

    /**
     * @return strategies to handle commands when the entity does not exist.
     */
    protected abstract CommandStrategy<? extends C, S, K, Result<E>> getDeletedStrategy();

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
    protected abstract DittoRuntimeExceptionBuilder newNotAccessibleExceptionBuilder();

    /**
     * Publish an event.
     *
     * @param event the event.
     */
    protected abstract void publishEvent(E event);

    /**
     * Get the implemented schema version of an entity.
     *
     * @param entity the entity.
     * @return its schema version according to content.
     */
    protected abstract JsonSchemaVersion getEntitySchemaVersion(S entity);

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
        final CommandStrategy<C, S, K, Result<E>> commandStrategy = getCreatedStrategy();

        final Receive receive = handleCleanups.orElse(ReceiveBuilder.create()
                .match(commandStrategy.getMatchingClass(), commandStrategy::isDefined, this::handleByCommandStrategy)
                .match(CheckForActivity.class, this::checkForActivity)
                .matchEquals(Control.TAKE_SNAPSHOT, this::takeSnapshotByInterval)
                .match(SaveSnapshotSuccess.class, this::saveSnapshotSuccess)
                .match(SaveSnapshotFailure.class, this::saveSnapshotFailure)
                .matchAny(this::matchAnyAfterInitialization)
                .build());

        getContext().become(receive);

        scheduleCheckForActivity(getActivityCheckConfig().getInactiveInterval());
        scheduleSnapshot();
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
     * @param event the event to persist and apply.
     * @param handler what happens afterwards.
     */
    protected void persistAndApplyEvent(final E event, final BiConsumer<E, S> handler) {

        final E modifiedEvent;
        if (null != entity) {
            // set version of event to the version of the entity
            final DittoHeaders newHeaders = event.getDittoHeaders().toBuilder()
                    .schemaVersion(getEntitySchemaVersion(entity))
                    .build();
            modifiedEvent = (E) event.setDittoHeaders(newHeaders);
        } else {
            modifiedEvent = event;
        }

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
     * Check for activity. Shutdown actor if it is lacking.
     *
     * @param message the check-for-activity message.
     */
    protected void checkForActivity(final CheckForActivity message) {
        if (entityExistsAsDeleted() && lastSnapshotRevision < getRevisionNumber()) {
            // take a snapshot after a period of inactivity if:
            // - entity is deleted,
            // - the latest snapshot is out of date or is still ongoing.
            takeSnapshot("the entity is deleted and has no up-to-date snapshot");
            scheduleCheckForActivity(getActivityCheckConfig().getDeletedInterval());
        } else if (accessCounter > message.accessCounter) {
            // if the entity was accessed in any way since the last check
            scheduleCheckForActivity(getActivityCheckConfig().getInactiveInterval());
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

    /**
     * Request parent to shutdown this actor gracefully in a thread-safe manner.
     */
    protected void passivate() {
        getContext().getParent().tell(AbstractPersistenceSupervisor.Control.PASSIVATE, getSelf());
    }

    private Receive createDeletedBehavior() {
        final CommandStrategy<? extends C, S, K, Result<E>> deleteStrategy = getDeletedStrategy();
        return handleCleanups.orElse(handleByStrategyReceiveBuilder(deleteStrategy)
                .match(CheckForActivity.class, this::checkForActivity)
                .matchEquals(Control.TAKE_SNAPSHOT, this::takeSnapshotByInterval)
                .match(SaveSnapshotSuccess.class, this::saveSnapshotSuccess)
                .match(SaveSnapshotFailure.class, this::saveSnapshotFailure)
                .matchAny(this::notAccessible)
                .build());
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
            log.debug("Scheduling for Activity Check in <{}> seconds.", interval);
            timers().startSingleTimer("activityCheck", new CheckForActivity(accessCounter), interval);
        }
    }

    private void scheduleSnapshot() {
        final Duration snapshotInterval = getSnapshotConfig().getInterval();
        timers().startPeriodicTimer("takeSnapshot", Control.TAKE_SNAPSHOT, snapshotInterval);
    }

    private void cancelSnapshot() {
        timers().cancel("takeSnapshot");
    }

    private void handleByCommandStrategy(final C command) {
        handleByStrategy(command, getCreatedStrategy());
    }

    private <T extends Command> ReceiveBuilder handleByStrategyReceiveBuilder(
            final CommandStrategy<T, S, K, Result<E>> strategy) {
        return ReceiveBuilder.create()
                .match(strategy.getMatchingClass(), command -> handleByStrategy(command, strategy));
    }

    private <T extends Command> void handleByStrategy(final T command,
            final CommandStrategy<T, S, K, Result<E>> strategy) {
        log.debug("Handling by strategy: <{}>", command);
        accessCounter++;
        final Result<E> result;
        try {
            result = strategy.apply(getStrategyContext(), entity, getNextRevisionNumber(), command);
        } catch (final DittoRuntimeException e) {
            getSender().tell(e, getSelf());
            return;
        }
        result.accept(this);
    }

    @Override
    public void onMutation(final Command command, final E event, final WithDittoHeaders response,
            final boolean becomeCreated, final boolean becomeDeleted) {

        persistAndApplyEvent(event, (persistedEvent, resultingEntity) -> {
            notifySender(response);
            if (becomeDeleted) {
                becomeDeletedHandler();
            }
            if (becomeCreated) {
                becomeCreatedHandler();
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

    private void persistEvent(final E event, final Consumer<E> handler) {
        LogUtil.enhanceLogWithCorrelationId(log, event);
        log.debug("Persisting Event <{}>.", event.getType());

        persist(event, persistedEvent -> {
            LogUtil.enhanceLogWithCorrelationId(log, event.getDittoHeaders().getCorrelationId());
            log.info("Successfully persisted Event <{}>.", event.getType());

            /* the event has to be applied before creating the snapshot, otherwise a snapshot with new
               sequence no (e.g. 2), but old entity revision no (e.g. 1) will be created -> can lead to serious
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
        if (entity != null && lastSnapshotRevision != revision) {
            log.debug("Taking snapshot for entity with ID <{}> and sequence number <{}> because {}.", entityId, revision,
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
        handleEvents.onMessage().apply(event);
        publishEvent(event);
    }

    private void notifySender(final WithDittoHeaders message) {
        notifySender(getSender(), message);
    }

    private void notifySender(final ActorRef sender, final WithDittoHeaders message) {
        accessCounter++;
        sender.tell(message, getSelf());
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

    private void notAccessible(final Object message) {
        final DittoRuntimeExceptionBuilder builder = newNotAccessibleExceptionBuilder();
        if (message instanceof WithDittoHeaders) {
            builder.dittoHeaders(((WithDittoHeaders) message).getDittoHeaders());
        }
        notifySender(builder.build());
    }

    private void shutdown(final String shutdownLogTemplate, final I entityId) {
        log.debug(shutdownLogTemplate, String.valueOf(entityId));
        passivate();
    }

    private boolean isEntityActive() {
        return entity != null && !entityExistsAsDeleted();
    }

    /**
     * Default is to log a warning, may be overwritten by implementations in order to handle additional messages.
     *
     * @param message the message to handle after initialization.
     */
    protected void matchAnyAfterInitialization(final Object message) {
        log.warning("Unknown message: {}", message);
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

    /**
     * Check if any command is processed.
     */
    protected static final class CheckForActivity {

        private final long accessCounter;

        private CheckForActivity(final long accessCounter) {
            this.accessCounter = accessCounter;
        }

        /**
         * @return Access counter at the previous activity check.
         */
        public long getAccessCounter() {
            return accessCounter;
        }
    }

    private enum Control {
        TAKE_SNAPSHOT
    }

}
