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
import java.time.Instant;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.entity.metadata.Metadata;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableEvent;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.utils.akka.PingCommand;
import org.eclipse.ditto.services.utils.akka.PingCommandResponse;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.persistence.SnapshotAdapter;
import org.eclipse.ditto.services.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.base.EventJsonDeserializer;

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
        C extends Command<?>,
        S,
        I extends EntityId,
        K,
        E extends Event<? extends E>> extends AbstractPersistentActorWithTimersAndCleanup implements ResultVisitor<E> {

    /**
     * An event journal {@code Tag} used to tag journal entries managed by a PersistenceActor as "always alive" meaning
     * that those entities should be always kept in-memory and re-started on a cold-start of the cluster.
     */
    public static final String JOURNAL_TAG_ALWAYS_ALIVE = "always-alive";

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
    protected boolean alwaysAlive = false;

    private long accessCounter = 0L;

    /**
     * Instantiate the actor.
     *
     * @param entityId the entity ID.
     * @param snapshotAdapter the entity's snapshot adapter.
     */
    @SuppressWarnings("unchecked")
    protected AbstractShardedPersistenceActor(final I entityId, final SnapshotAdapter<S> snapshotAdapter) {
        this.entityId = entityId;
        this.snapshotAdapter = snapshotAdapter;
        entity = null;

        lastSnapshotRevision = 0L;
        confirmedSnapshotRevision = 0L;

        handleEvents = ReceiveBuilder.create()
                .match(getEventClass(), event -> {
                    entity = getEventStrategy().handle((E) event, entity, getRevisionNumber());
                    alwaysAlive |= ((E) event).getDittoHeaders().getJournalTags().contains(JOURNAL_TAG_ALWAYS_ALIVE);
                    onEntityModified();
                })
                .match(EmptyEvent.class, event -> {
                    log.withCorrelationId(event).debug("Recovered EmptyEvent: <{}>", event);
                    alwaysAlive |= event.getEffect().equals(EmptyEvent.EFFECT_ALWAYS_ALIVE);
                })
                .build();

        handleCleanups = super.createReceive();
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
     * Check whether the headers indicate the necessity of a response for a mutation command.
     *
     * @param dittoHeaders headers of the mutation command.
     * @return whether a response should be sent.
     */
    protected abstract boolean shouldSendResponse(DittoHeaders dittoHeaders);

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
     * @param event the event to persist and apply.
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
     * Allows to modify the passed in {@code event} before {@link #persistEvent(Event, Consumer)} is invoked.
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
        if (entityExistsAsDeleted() && lastSnapshotRevision < getRevisionNumber()) {
            // take a snapshot after a period of inactivity if:
            // - entity is deleted,
            // - the latest snapshot is out of date or is still ongoing.
            takeSnapshot("the entity is deleted and has no up-to-date snapshot");
            scheduleCheckForActivity(getActivityCheckConfig().getDeletedInterval());
        } else if (accessCounter > message.accessCounter) {
            // if the entity was accessed in any way since the last check
            scheduleCheckForActivity(getActivityCheckConfig().getInactiveInterval());
        } else if (isEntityActive() && alwaysAlive) {
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
        final CommandStrategy<? extends C, S, K, E> deleteStrategy = getDeletedStrategy();
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
            log.debug("Scheduling for Activity Check in <{}>", interval);
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

    private <T extends Command<?>> ReceiveBuilder handleByStrategyReceiveBuilder(
            final CommandStrategy<T, S, K, E> strategy) {
        return ReceiveBuilder.create()
                .match(strategy.getMatchingClass(), command -> handleByStrategy(command, strategy));
    }

    private <T extends Command<?>> void handleByStrategy(final T command,
            final CommandStrategy<T, S, K, E> strategy) {
        log.debug("Handling by strategy: <{}>", command);
        accessCounter++;
        Result<E> result;
        try {
            result = strategy.apply(getStrategyContext(), entity, getNextRevisionNumber(), command);
        } catch (final DittoRuntimeException e) {
            result = ResultFactory.newErrorResult(e, command);
        }
        result.accept(this);
    }

    @Override
    public void onMutation(final Command<?> command, final E event, final WithDittoHeaders<?> response,
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
    public void onQuery(final Command<?> command, final WithDittoHeaders<?> response) {
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
     * @param sender recipient of the message.
     * @param message the message.
     */
    protected void notifySender(final ActorRef sender, final WithDittoHeaders<?> message) {
        accessCounter++;
        sender.tell(message, getSelf());
    }

    private long getNextRevisionNumber() {
        return getRevisionNumber() + 1;
    }

    private void persistEvent(final E event, final Consumer<E> handler) {
        final DittoDiagnosticLoggingAdapter l = log.withCorrelationId(event);
        l.debug("Persisting Event <{}>.", event.getType());

        persist(event, persistedEvent -> {
            l.info("Successfully persisted Event <{}>.", event.getType());

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
        handleEvents.onMessage().apply(event);
        publishEvent(event);
    }

    private void notifySender(final WithDittoHeaders<?> message) {
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

    private void notAccessible(final Object message) {
        final DittoRuntimeExceptionBuilder<?> builder = newNotAccessibleExceptionBuilder();
        if (message instanceof WithDittoHeaders) {
            builder.dittoHeaders(((WithDittoHeaders<?>) message).getDittoHeaders());
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
     * @return the match-all Receive object.
     */
    protected Receive matchAnyAfterInitialization() {
        return ReceiveBuilder.create()
                .matchAny(message -> log.warning("Unknown message: {}", message))
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


    /**
     * Local message this actor may sent to itself in order to persist an {@link EmptyEvent} to the event journal,
     * e.g. in order to save a specific journal tag with that event to the event journal.
     */
    @Immutable
    protected static final class PersistEmptyEvent {

        private final EmptyEvent emptyEvent;

        public PersistEmptyEvent(final EmptyEvent emptyEvent) {
            this.emptyEvent = emptyEvent;
        }

        protected EmptyEvent getEmptyEvent() {
            return emptyEvent;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "emptyEvent=" + emptyEvent +
                    "]";
        }
    }

    /**
     * An event which can be persisted to the backing event journal which does not contain any change "instruction" to
     * alter the managed entity's state.
     */
    @Immutable
    @JsonParsableEvent(name = EmptyEvent.NAME, typePrefix= EmptyEvent.TYPE_PREFIX)
    protected static final class EmptyEvent implements Event<EmptyEvent> {

        /**
         * Known effect of the "empty event" which shall keep an persistence actor always alive.
         */
        public static final JsonValue EFFECT_ALWAYS_ALIVE = JsonValue.of("alwaysAlive");

        private static final String TYPE_PREFIX = "persistence-actor-internal:";

        private static final String NAME = "empty-event";

        private static final String TYPE = TYPE_PREFIX + NAME;

        private static final JsonFieldDefinition<String> JSON_ENTITY_ID =
                JsonFactory.newStringFieldDefinition("entityId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private static final JsonFieldDefinition<JsonValue> JSON_EFFECT =
                JsonFactory.newJsonValueFieldDefinition("effect", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private final EntityId entityId;
        private final JsonValue effect;
        private final long revision;
        private final DittoHeaders dittoHeaders;

        public EmptyEvent(final EntityId entityId, final JsonValue effect, final long revision,
                final DittoHeaders dittoHeaders) {
            this.entityId = entityId;
            this.revision = revision;
            this.effect = effect;
            this.dittoHeaders = dittoHeaders;
        }

        /**
         * Creates a {@code EmptyEvent} event from a JSON object.
         *
         * @param jsonObject the JSON object of which the event is to be created.
         * @param dittoHeaders the headers of the command which was the cause of this event.
         * @return the event.
         * @throws NullPointerException if any argument is {@code null}.
         * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
         * format.
         */
        @SuppressWarnings("unused") // used via reflection
        public static EmptyEvent fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
            return new EventJsonDeserializer<EmptyEvent>(TYPE, jsonObject)
                    .deserialize((revision, timestamp, metadata) -> {
                        final EntityId readEntityId = DefaultEntityId.of(jsonObject.getValueOrThrow(JSON_ENTITY_ID));
                        final JsonValue readEffect = jsonObject.getValueOrThrow(JSON_EFFECT);
                        return new EmptyEvent(readEntityId, readEffect, revision, dittoHeaders);
                    });
        }

        /**
         * Returns the effect of the empty event - might also be a Json {@code null} if no effect is provided.
         *
         * @return the effect of the empty event.
         */
        public JsonValue getEffect() {
            return effect;
        }

        @Override
        public DittoHeaders getDittoHeaders() {
            return dittoHeaders;
        }

        @Override
        public EmptyEvent setDittoHeaders(final DittoHeaders dittoHeaders) {
            return new EmptyEvent(entityId, effect, revision, dittoHeaders);
        }

        @Override
        public String getType() {
            return TYPE;
        }

        @Nonnull
        @Override
        public String getManifest() {
            return getType();
        }

        @Override
        public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
            final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder()
                    .set(Event.JsonFields.TYPE, getType())
                    .set(JSON_ENTITY_ID, entityId.toString())
                    .set(JSON_EFFECT, effect);
            return jsonObjectBuilder.build();
        }

        @Override
        public EntityId getEntityId() {
            return entityId;
        }

        @Override
        public JsonPointer getResourcePath() {
            return JsonPointer.empty();
        }

        @Override
        public String getResourceType() {
            return "empty";
        }

        @Override
        public long getRevision() {
            return revision;
        }

        @Override
        public EmptyEvent setRevision(final long revision) {
            return new EmptyEvent(entityId, effect, revision, dittoHeaders);
        }

        @Override
        public Optional<Instant> getTimestamp() {
            return Optional.empty();
        }

        @Override
        public Optional<Metadata> getMetadata() {
            return Optional.empty();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "entityId=" + entityId +
                    ", effect=" + effect +
                    ", revision=" + revision +
                    ", dittoHeaders=" + dittoHeaders +
                    "]";
        }
    }

}
