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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyRevision;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.services.models.policies.PolicyReferenceTag;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.models.streaming.IdentifiableStreamingMessage;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.services.thingsearch.persistence.write.EventToPersistenceStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingMetadata;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.things.PolicyIdCreated;
import org.eclipse.ditto.signals.events.things.PolicyIdModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Scheduler;
import akka.dispatch.DequeBasedMessageQueueSemantics;
import akka.dispatch.RequiresMessageQueue;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;
import akka.japi.Creator;
import akka.japi.pf.FI;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.pattern.CircuitBreaker;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import kamon.Kamon;
import kamon.trace.TraceContext;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

/**
 * This Actor initiates persistence updates related to 1 thing. It has 2 main state transition cycles: event processing
 * and synchronization. Methods are grouped into the sections {@code EVENT PROCESSING}, {@code SYNCHRONIZATION} and
 * {@code HELPERS}. Either cycle interacts with the persistence through its own methods: {@code executeWrites} for event
 * processing, {@code updateThing} and {@code updatePolicy} for synchronization.
 */
final class ThingUpdater extends AbstractActorWithDiscardOldStash
        implements RequiresMessageQueue<DequeBasedMessageQueueSemantics> {

    /**
     * Placeholder for the revision number of an unknown entity; smaller than all revision numbers used by Akka.
     */
    private static final long UNKNOWN_REVISION = 0L;

    /**
     * How long to wait for things and policies by default.
     */
    static final java.time.Duration DEFAULT_THINGS_TIMEOUT = java.time.Duration.of(20, ChronoUnit.SECONDS);

    /**
     * (Effectively) Unlimited max bulk size.
     */
    static final int UNLIMITED_MAX_BULK_SIZE = Integer.MAX_VALUE;
    /**
     * Max attempts when trying to sync a thing.
     */
    private static final int MAX_SYNC_ATTEMPTS = 3;

    /**
     * Sync session ID for sync-like behavior not triggered during any synchronization session
     */
    private static final String NO_SYNC_SESSION_ID = "<no-sync-session-id>";

    private static final String TRACE_THING_MODIFIED = "thing.modified";
    private static final String TRACE_THING_BULK_UPDATE = "thing.bulkUpdate";
    private static final String COUNT_THING_BULK_UPDATE = "thing.bulkUpdate.count";
    private static final String TRACE_THING_DELETE = "thing.delete";
    private static final String TRACE_POLICY_UPDATE = "policy.update";

    private final DiagnosticLoggingAdapter log = Logging.apply(this);

    // configuration
    private final int maxBulkSize;
    private final String thingId;
    private final FiniteDuration thingsTimeout;
    private final ActorRef thingsShardRegion;
    private final ActorRef policiesShardRegion;
    private final java.time.Duration activityCheckInterval;
    private final ThingsSearchUpdaterPersistence searchUpdaterPersistence;
    private final CircuitBreaker circuitBreaker;
    private final Materializer materializer;

    // transducer state-transition table
    private final Receive eventProcessingBehavior = createEventProcessingBehavior();
    private final Receive awaitSyncResultBehavior = createAwaitSyncResultBehavior();

    // maintenance and book-keeping
    private boolean transactionActive;
    private int syncAttempts;
    private Cancellable activityChecker;
    private List<ThingEvent> gatheredEvents;

    // state of Thing and Policy
    private long sequenceNumber = -1L;
    private JsonSchemaVersion schemaVersion;
    private String policyId;
    private long policyRevision = -1L;
    private Enforcer policyEnforcer;

    // required for acking of synchronization
    private SyncMetadata activeSyncMetadata = null;

    // ID of a synchronization session
    private String syncSessionId = NO_SYNC_SESSION_ID;
    private Cancellable syncTimeout = null;

    private ThingUpdater(final java.time.Duration thingsTimeout,
            final ActorRef thingsShardRegion,
            final ActorRef policiesShardRegion,
            final ThingsSearchUpdaterPersistence searchUpdaterPersistence,
            final CircuitBreaker circuitBreaker,
            final java.time.Duration activityCheckInterval,
            final int maxBulkSize) {

        this.maxBulkSize = maxBulkSize;
        this.thingsTimeout = Duration.create(thingsTimeout.toNanos(), TimeUnit.NANOSECONDS);
        this.thingsShardRegion = thingsShardRegion;
        this.policiesShardRegion = policiesShardRegion;
        this.activityCheckInterval = activityCheckInterval;
        this.searchUpdaterPersistence = searchUpdaterPersistence;
        this.circuitBreaker = circuitBreaker;
        this.gatheredEvents = new ArrayList<>();

        thingId = tryToGetThingId(StandardCharsets.UTF_8);
        materializer = ActorMaterializer.create(getContext());
        transactionActive = false;
        syncAttempts = 0;

        scheduleCheckForThingActivity();
        searchUpdaterPersistence.getThingMetadata(thingId)
                .runWith(Sink.last(), materializer)
                .whenComplete((retrievedThingMetadata, throwable) -> {
                    if (throwable == null) {
                        getSelf().tell(retrievedThingMetadata, null);
                    } else {
                        if (throwable instanceof NoSuchElementException) {
                            // Thing not present in search index
                            log.debug("Thing was not yet present in search index: {}", thingId);
                        } else {
                            log.error(throwable,
                                    "Unexpected exception when retrieving latest revision from search index");
                        }
                        // metadata retrieval failed, try to initialize by event update or synchronization
                        getSelf().tell(ActorInitializationComplete.INSTANCE, null);
                    }
                });
    }

    private String tryToGetThingId(final Charset charset) {
        try {
            return getThingId(charset);
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(MessageFormat.format("Charset <{0}> is unsupported!", charset.name()), e);
        }
    }

    private String getThingId(final Charset charset) throws UnsupportedEncodingException {
        final String actorName = self().path().name();
        return URLDecoder.decode(actorName, charset.name());
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param searchUpdaterPersistence persistence to write thing- and policy-updates to.
     * @param circuitBreaker circuit breaker to protect {@code searchUpdaterPersistence} from excessive load.
     * @param thingsShardRegion the {@link ActorRef} of the Things shard region.
     * @param policiesShardRegion the ActorRef to the Policies shard region.
     * @param activityCheckInterval the interval at which is checked, if the corresponding Thing is still actively
     * updated.
     * @param thingsTimeout how long to wait for Things and Policies service.
     * @param maxBulkSize maximum number of events to update in a bulk.
     * @return the Akka configuration Props object
     */
    static Props props(final ThingsSearchUpdaterPersistence searchUpdaterPersistence,
            final CircuitBreaker circuitBreaker,
            final ActorRef thingsShardRegion,
            final ActorRef policiesShardRegion,
            final java.time.Duration activityCheckInterval,
            final java.time.Duration thingsTimeout,
            final int maxBulkSize) {

        return Props.create(ThingUpdater.class, new Creator<ThingUpdater>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingUpdater create() {
                return new ThingUpdater(thingsTimeout, thingsShardRegion, policiesShardRegion,
                        searchUpdaterPersistence, circuitBreaker, activityCheckInterval, maxBulkSize);
            }
        });
    }


    ////////////////////////
    ///// BOOK-KEEPING /////
    ////////////////////////

    // the behavior before the actor is initialized.
    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ThingMetadata.class, retrievedThingMetadata -> {
                    sequenceNumber = retrievedThingMetadata.getThingRevision();
                    policyId = retrievedThingMetadata.getPolicyId();
                    policyRevision = retrievedThingMetadata.getPolicyRevision();

                    becomeEventProcessing();
                })
                .match(ActorInitializationComplete.class, msg -> becomeEventProcessing())
                .match(CheckForActivity.class, this::checkActivity)
                .matchAny(msg -> stashWithErrorsIgnored())
                .build();
    }

    private void scheduleCheckForThingActivity() {
        log.debug("Scheduling for activity check in <{}> seconds.", activityCheckInterval.getSeconds());
        // send a message to ourself:
        activityChecker = context().system().scheduler()
                .scheduleOnce(Duration.create(activityCheckInterval.getSeconds(), TimeUnit.SECONDS), self(),
                        new CheckForActivity(sequenceNumber), context().dispatcher(), null);
    }

    private void checkActivity(final CheckForActivity checkForActivity) {
        if (checkForActivity.getSequenceNr() == sequenceNumber) {
            log.debug("Thing <{}> was not updated in a while. Shutting Actor down ...", thingId);
            stopThisActor();
        } else {
            scheduleCheckForThingActivity();
        }
    }

    private void stopThisActor() {
        getSelf().tell(PoisonPill.getInstance(), ActorRef.noSender());
    }

    @Override
    public void postStop() throws Exception {
        cancelActivityCheck();
        cancelSyncTimeoutAndResetSessionId();
        super.postStop();
    }

    private void cancelActivityCheck() {
        if (activityChecker != null) {
            activityChecker.cancel();
            activityChecker = null;
        }
    }

    private void cancelSyncTimeoutAndResetSessionId() {
        if (syncTimeout != null) {
            syncTimeout.cancel();
            syncTimeout = null;
        }
        syncSessionId = NO_SYNC_SESSION_ID;
    }

    ///////////////////////////
    ///// EVENT PROCESSING ////
    ///////////////////////////

    // Collect incremental changes signified by events and perform bulk updates on the persistence.
    // If incremental update is not possible for any reason, transition to the synchronization cycle
    // and discard all pending writes.

    /**
     * Start event processing. Requires {@code this.schemaVersion} and {@code this.sequenceNumber} to be initialized.
     */
    private void becomeEventProcessing() {
        log.debug("Becoming 'eventProcessing' ...");
        cancelSyncTimeoutAndResetSessionId();
        getContext().become(eventProcessingBehavior);
        unstashAll();
    }

    private Receive createEventProcessingBehavior() {
        return ReceiveBuilder.create()
                .match(ThingEvent.class, this::processThingEvent)
                .match(PolicyEvent.class, this::processPolicyEvent)
                .match(ThingTag.class, this::processThingTag)
                .match(PolicyReferenceTag.class, this::processPolicyReferenceTag)
                .match(CheckForActivity.class, this::checkActivity)
                .match(PersistenceWriteResult.class, this::handlePersistenceUpdateResult)
                .matchAny(m -> {
                    log.warning("Unknown message in 'eventProcessing' behavior: {}", m);
                    unhandled(m);
                })
                .build();
    }

    private void processPolicyEvent(final PolicyEvent<?> policyEvent) {
        LogUtil.enhanceLogWithCorrelationId(log, policyEvent);
        if (Objects.equals(policyId, policyEvent.getPolicyId()) && policyEvent.getRevision() > policyRevision) {
            log.debug("Processing relevant PolicyChange with revision <{}>", policyEvent.getRevision());
            triggerSynchronization();
        }
    }

    private void processThingTag(final ThingTag thingTag) {
        LogUtil.enhanceLogWithCorrelationId(log, "things-tags-sync-" + thingTag.asIdentifierString());

        log.debug("Received new Thing Tag for thing <{}> with revision <{}>: <{}>.",
                thingId, sequenceNumber, thingTag.asIdentifierString());

        activeSyncMetadata = new SyncMetadata(getSender(), thingTag);

        if (thingTag.getRevision() > sequenceNumber) {
            log.info("The Thing Tag for the thing <{}> has the revision {} which is greater than the current actor's"
                    + " sequence number <{}>.", thingId, thingTag.getRevision(), sequenceNumber);
            triggerSynchronization();
        } else {
            ackSync(true);
        }
    }

    private void processPolicyReferenceTag(final PolicyReferenceTag policyReferenceTag) {
        LogUtil.enhanceLogWithCorrelationId(log, "policies-tags-sync-" + policyReferenceTag.asIdentifierString());

        if (log.isDebugEnabled()) {
            log.debug("Received new Policy-Reference-Tag for thing <{}> with revision <{}>,  policy-id <{}> and " +
                            "policy-revision <{}>: <{}>.",
                    new Object[]{thingId, sequenceNumber, policyId, policyRevision,
                            policyReferenceTag.asIdentifierString()});
        }

        activeSyncMetadata = new SyncMetadata(getSender(), policyReferenceTag);

        boolean triggerSync;
        if (policyId == null) {
            log.debug("Currently no policy-id is available for the thing <{}>.", thingId);
            triggerSync = true;
        } else if (!policyReferenceTag.getPolicyTag().getId().equals(policyId)) {
            // may happen sometimes due to timing: when the Policy of the Thing has changed after
            // the ThingsUpdater has correlated this thing with the Policy referenced by this PolicyTag
            if (log.isDebugEnabled()) {
                log.debug("Policy-Reference-Tag has different policy-id than the current policy-id <{}> for the " +
                                "thing <{}>: <{}>.",
                        policyId, thingId, policyReferenceTag.asIdentifierString());
            }
            triggerSync = false;
        } else if (policyReferenceTag.getPolicyTag().getRevision() > policyRevision) {
            log.info("The Policy-Reference-Tag has a revision which is greater " +
                            "than the current policy-revision <{}> for thing <{}>: <{}>.", policyRevision, thingId,
                    policyReferenceTag.asIdentifierString());
            triggerSync = true;
        } else {
            if (log.isDebugEnabled()) {
                log.debug("The Policy-Reference-Tag has a revision which is less than or equal to " +
                                "the current policy-revision <{}> for thing <{}>: <{}>.", policyRevision, thingId,
                        policyReferenceTag.asIdentifierString());
            }
            triggerSync = false;
        }

        if (triggerSync) {
            triggerSynchronization();
        } else {
            ackSync(true);
        }
    }

    private void processThingEvent(final ThingEvent thingEvent) {
        LogUtil.enhanceLogWithCorrelationId(log, thingEvent);

        log.debug("Received new thing event for thing id <{}> with revision <{}>.", thingId,
                thingEvent.getRevision());

        // check if the revision is valid (thingEvent.revision = 1 + sequenceNumber)
        if (thingEvent.getRevision() <= sequenceNumber) {
            log.debug("Dropped thing event for thing id <{}> with revision <{}> because it was older than or "
                            + "equal to the current sequence number <{}> of the update actor.", thingId,
                    thingEvent.getRevision(), sequenceNumber);
        } else if (shortcutTakenForThingEvent(thingEvent)) {
            log.debug("Shortcut taken for thing event <{}>.", thingEvent);
        } else if (thingEvent.getRevision() > sequenceNumber + 1) {
            log.debug("Triggering synchronization for thing <{}> because the received revision <{}> is higher than"
                    + " the expected sequence number <{}>.", thingId, thingEvent.getRevision(), sequenceNumber + 1);
            triggerSynchronization();
        } else if (needToReloadPolicy(thingEvent)) {
            //if the policy needs to be loaded first, drop everything and synchronize.
            triggerSynchronization();
        } else {
            // attempt to add event, trigger synchronization in case of failures
            try {
                // verify the type is one of the 'allowed' types
                if (!EventToPersistenceStrategyFactory.isTypeAllowed(thingEvent.getType())) {
                    final String pattern = "Not processing Thing Event since its Type <{0}> is not allowed";
                    throw new IllegalStateException(MessageFormat.format(pattern, thingEvent.getType()));
                }

                // TODO: explicitly setting the schema version here feels wrong...
                final DittoHeaders versionedHeaders = thingEvent.getDittoHeaders().toBuilder()
                        .schemaVersion(schemaVersionToCheck())
                        .build();
                final ThingEvent versionedThingEvent = thingEvent.setDittoHeaders(versionedHeaders);
                if (transactionActive) {
                    log.debug("The update actor for thing <{}> is currently busy. The current event will be " +
                                    "processed after the actor is.",
                            thingId);
                    addEventToGatheredEvents(versionedThingEvent);
                    log.debug("Currently gathered <{}> events to be processed after actor has finished being busy",
                            gatheredEvents.size());
                } else {
                    // do not increment sequenceNumber before this point, since we don't want to increase sequenceNumber
                    // in case of persistence failure
                    persistThingEvents(Collections.singletonList(versionedThingEvent), thingEvent.getRevision());
                }

                // Update state related to the Thing. Policy state is maintained by synchronization.
                sequenceNumber = thingEvent.getRevision();
            } catch (final RuntimeException e) {
                log.error(e, "Failed to process event <{}>. Triggering sync.", thingEvent);
                triggerSynchronization();
            }
        }
    }

    private void addEventToGatheredEvents(final ThingEvent thingEvent) {
        gatheredEvents.add(thingEvent);
    }

    private boolean needToReloadPolicy(final ThingEvent thingEvent) {
        return thingEvent instanceof PolicyIdCreated || thingEvent instanceof PolicyIdModified
                // check if Thing has a policy but the policy enforcer is not instantiated
                || (schemaVersionHasPolicy(thingEvent.getImplementedSchemaVersion()) && Objects.isNull(policyEnforcer));
    }

    /**
     * Takes shortcut for ThingCreated and ThingModified events.
     *
     * @return true if shortcut for ThingCreated and ThingModified events are taken and no further action is needed for
     * the thing event; false if the thing event remains to be processed.
     */
    private boolean shortcutTakenForThingEvent(final ThingEvent thingEvent) {
        if (thingEvent instanceof ThingCreated) {
            // if the very first event in initial phase is the "ThingCreated", we can shortcut a lot ..
            final ThingCreated tc = (ThingCreated) thingEvent;
            log.debug("Got ThingCreated: {}", tc);
            final Thing createdThing = tc.getThing().toBuilder().setRevision(tc.getRevision()).build();
            updateThingSearchIndex(createdThing);
            return true;
        } else if (thingEvent instanceof ThingModified) {
            // if the very first event in initial phase is the "ThingModified", we can shortcut a lot ..
            final ThingModified tm = (ThingModified) thingEvent;
            log.debug("Got ThingModified: {}", tm);
            final Thing modifiedThing = tm.getThing().toBuilder().setRevision(tm.getRevision()).build();
            updateThingSearchIndex(modifiedThing);
            return true;
        } else {
            return false;
        }
    }

    private JsonSchemaVersion schemaVersionToCheck() {
        return Optional.ofNullable(schemaVersion)
                .orElse(policyId == null ? JsonSchemaVersion.V_1 : JsonSchemaVersion.LATEST);
    }

    private void persistThingEvents(final List<ThingEvent> thingEvents) {
        persistThingEvents(thingEvents, sequenceNumber);
    }

    private void persistThingEvents(final List<ThingEvent> thingEvents, final long targetRevision) {
        log.debug("Executing bulk write operation with <{}> updates.", thingEvents.size());
        if (!thingEvents.isEmpty()) {
            transactionActive = true;
            Kamon.metrics().histogram(COUNT_THING_BULK_UPDATE).record(thingEvents.size());
            final TraceContext traceContext = Kamon.tracer().newContext(TRACE_THING_BULK_UPDATE);
            circuitBreaker.callWithCircuitBreakerCS(() -> searchUpdaterPersistence
                    .executeCombinedWrites(thingId, thingEvents, policyEnforcer, targetRevision)
                    .via(finishTrace(traceContext))
                    .runWith(Sink.last(), materializer)
                    .whenComplete(this::processWriteResult))
                    .exceptionally(t -> {
                        log.error(t, "There occurred an error while processing a write operation within the"
                                + " circuit breaker for thing <{}>.", thingId);
                        //e.g. in case of a circuit breaker timeout, the running transaction must be stopped
                        processWriteResult(false, t);
                        return null;
                    });
        }

    }

    private void processWriteResult(final Boolean boxedSuccess, final Throwable throwable) {
        // send result as message to process the result in the actor context
        final boolean success = isTrue(boxedSuccess);
        getSelf().tell(new PersistenceWriteResult(success, throwable), null);
    }

    private void handlePersistenceUpdateResult(final PersistenceWriteResult result) {
        transactionActive = false;
        if (result.getError() != null) {
            log.error(result.getError(), "The MongoDB operation for thing <{}> failed with an error!", thingId);
            triggerSynchronization();
        } else if (result.isSuccess()) {
            if (!gatheredEvents.isEmpty()) {
                log.debug("<{}> gathered events will now be persisted.", gatheredEvents.size());
                final List<ThingEvent> eventsToPersist = gatheredEvents;
                // reset the gathered events
                resetGatheredEvents();
                final int numberOfEvents = eventsToPersist.size();
                if (numberOfEvents <= maxBulkSize) {
                    persistThingEvents(eventsToPersist);
                } else {
                    log.info("Triggering synchronization because <{}> events were accumulated since last bulk update," +
                            " which exceeded the limit of <{}> events per bulk update.", numberOfEvents, maxBulkSize);
                    triggerSynchronization();
                }
            }
        } else {
            log.warning("The update operation for thing <{}> failed due to an unexpected sequence number!", thingId);
            triggerSynchronization();
        }
    }

    ///////////////////////////
    ///// SYNCHRONIZATION /////
    ///////////////////////////

    // Load the thing as a whole, load the policy if needed, and then write both into the persistence.
    // Transition to event processing on success; restart synchronization on failure.

    /**
     * Transition into the synchronization cycle.
     */
    private void triggerSynchronization() {
        beginNewSyncSession();

        policyEnforcer = null; // reset policyEnforcer
        syncAttempts++;
        transactionActive = false;
        resetGatheredEvents();
        if (syncAttempts <= MAX_SYNC_ATTEMPTS) {
            log.debug("Synchronization of thing <{}> is now triggered (attempt={}).", thingId, syncAttempts);
            syncThing();
        } else {
            log.error("Synchronization failed after <{}> attempts.", syncAttempts - 1);
            ackSync(false);
            stopThisActor();
        }
    }

    private void syncThing() {
        log.debug("Trying to synchronize thing <{}>.", thingId);

        retrieveThing();

        // Change behavior that waits for the response or the timeout and stashes all other incoming messages.
        becomeAwaitingSyncThingResponse();
    }

    private void becomeAwaitingSyncThingResponse() {
        log.debug("Becoming 'awaitingSyncThingResponse' for thing <{}> ...", thingId);
        getContext().become(createAwaitSyncThingBehavior(syncSessionId));
    }

    private Receive createAwaitSyncThingBehavior(final String sessionId) {
        log.debug("Becoming 'awaitSyncThingBehavior' for thing <{}> ...", thingId);
        return ReceiveBuilder.create()
                .match(AskTimeoutException.class, handleSyncTimeout(sessionId, "Timeout after SudoRetrieveThing"))
                .match(SudoRetrieveThingResponse.class, this::handleSyncThingResponse)
                .match(ThingErrorResponse.class, this::handleErrorResponse)
                .match(DittoRuntimeException.class, this::handleException)
                .match(CheckForActivity.class, this::checkActivity)
                .matchAny(msg -> stashWithErrorsIgnored()) // stash all other messages
                .build();
    }

    private FI.UnitApply<AskTimeoutException> handleSyncTimeout(final String expectedSessionId, final String message) {

        return askTimeoutException -> {
            final String actualSessionId = askTimeoutException.getMessage();
            final boolean isSyncSessionActive = !Objects.equals(NO_SYNC_SESSION_ID, expectedSessionId);
            if (isSyncSessionActive && Objects.equals(actualSessionId, expectedSessionId)) {
                log.error(message);
                triggerSynchronization();
            } else {
                log.warning("Ignoring AskTimeoutException from session <{}>. Current session is <{}>.",
                        actualSessionId, expectedSessionId);
            }
        };
    }

    private void syncPolicy(final Thing syncedThing) {
        log.debug("Trying to synchronize policy <{}>.", policyId);

        if (policyId != null) {
            retrievePolicy(policyId);

            // Change behavior that waits for the response or the timeout and stashes all other incoming messages.
            becomeAwaitingSyncPolicyResponse(syncedThing);
        } else {
            syncThing();
        }
    }

    private void becomeAwaitingSyncPolicyResponse(final Thing syncedThing) {
        log.debug("Becoming 'awaitingSyncPolicyResponse' for policy <{}> ...", policyId);
        getContext().become(createAwaitSyncPolicyBehavior(syncSessionId, syncedThing));
    }

    private Receive createAwaitSyncPolicyBehavior(final String sessionId, final Thing syncedThing) {
        log.debug("Becoming 'awaitSyncPolicyBehavior' for thing <{}> ...", thingId);
        return ReceiveBuilder.create()
                .match(AskTimeoutException.class, handleSyncTimeout(sessionId, "Timeout after SudoRetrievePolicy"))
                .match(SudoRetrievePolicyResponse.class, response -> handleSyncPolicyResponse(syncedThing, response))
                .match(PolicyErrorResponse.class, this::handleErrorResponse)
                .match(DittoRuntimeException.class, this::handleException)
                .match(CheckForActivity.class, this::checkActivity)
                .matchAny(message -> stashWithErrorsIgnored())
                .build();
    }

    /**
     * Final state of the synchronization cycle. Transitions to event processing on success and re-attempt
     * synchronization on failure.
     */
    // stash all messages until actor state change triggered by ongoing future
    private void becomeSyncResultAwaiting() {
        log.debug("Becoming 'syncResultAwaiting' ...");
        getContext().become(awaitSyncResultBehavior);
    }

    private Receive createAwaitSyncResultBehavior() {
        return ReceiveBuilder.create()
                .match(SyncSuccess.class, s -> {
                    syncAttempts = 0;
                    ackSync(true);
                    becomeEventProcessing();
                })
                .match(SyncFailure.class, f -> triggerSynchronization())
                .match(CheckForActivity.class, this::checkActivity)
                .matchAny(msg -> stashWithErrorsIgnored())
                .build();
    }

    /**
     * Sends acknowledgement after a synchronization if acknowledgement was requested, by a ThingTag message with
     * nonempty sender for example.
     *
     * @param success If the sync was successful.
     */
    private void ackSync(final boolean success) {
        if (activeSyncMetadata == null) {
            // This branch is expected for synchronization not triggered by any ThingTag message from a stream
            // supervisor, hence the log level DEBUG.
            log.debug("Cannot ack sync, cause no recipient is available.");
        } else {
            final ActorRef syncAckRecipient = activeSyncMetadata.getAckRecipient();
            final ActorRef deadLetters = getContext().getSystem().deadLetters();
            if (Objects.equals(syncAckRecipient, deadLetters)) {
                // This branch is expected for ThingTags messages sent with ActorRef.noSender() as sender address,
                // hence the log level DEBUG.
                log.debug("Cannot ack sync, cause recipient is deadletters.");
            } else {
                final Object message;
                if (success) {
                    message = activeSyncMetadata.getSuccess();
                } else {
                    message = activeSyncMetadata.getFailure();
                }
                log.debug("Acking sync with result <{}> to <{}>", message, syncAckRecipient);
                syncAckRecipient.tell(message, getSelf());
            }

            activeSyncMetadata = null;
        }
    }

    private void handleSyncThingResponse(final SudoRetrieveThingResponse response) {
        log.debug("Retrieved thing response='{}' for thing ID='{}' (attempt={}).", response, thingId, syncAttempts);

        final Thing syncedThing = response.getThing();
        updateThingSearchIndex(syncedThing);
    }

    private void updateThingSearchIndex(final Thing thing) {
        // reset schema version and revision number
        sequenceNumber = thing.getRevision().map(ThingRevision::toLong).orElse(UNKNOWN_REVISION);
        schemaVersion = thing.getImplementedSchemaVersion();

        final String policyIdOfThing = thing.getPolicyId().orElse(null);
        if (policyIdOfThing != null) {
            if (!policyIdOfThing.equals(policyId)) {
                // policyId changed!
                policyRevision = -1L; // reset policyRevision
                policyEnforcer = null; // reset policyEnforcer
                policyId = policyIdOfThing;
            }
        } else if (hasNonEmptyAcl(thing)) {
            policyRevision = -1L; // reset policyRevision
            policyEnforcer = null; // reset policyEnforcer
            policyId = null;
        } else {
            log.error("Thing to update in search index had neither a policyId nor an ACL: {}", thing);
            triggerSynchronization();
            return;
        }

        if (policyIdOfThing == null) {
            schemaVersion = JsonSchemaVersion.V_1;
        }

        // always synchronize policy if the schema version calls for it
        if (schemaVersionHasPolicy(schemaVersion)) {
            if (policyEnforcer != null) {
                updateSearchIndexWithPolicy(thing, null);
            } else {
                syncPolicy(thing);
            }
        } else {
            // No policy to worry about; we're done.
            updateSearchIndexWithoutPolicy(thing);
        }
    }

    private static boolean hasNonEmptyAcl(final Thing thing) {
        return thing.getAccessControlList()
                .filter(acl -> !acl.isEmpty())
                .isPresent();
    }

    private void handleSyncPolicyResponse(final Thing syncedThing, final SudoRetrievePolicyResponse response) {
        log.debug("Retrieved policy response='{}' for thing ID='{}' and policyId='{}' (attempt={}).",
                response, thingId, policyId, syncAttempts);
        log.debug("Policy from retrieved policy response is: {}", response.getPolicy());

        final Policy policy = response.getPolicy();

        final boolean isExpectedPolicyId = policy.getId()
                .filter(policyId::equals)
                .isPresent();
        if (isExpectedPolicyId) {
            policyRevision = policy.getRevision().map(PolicyRevision::toLong).orElse(UNKNOWN_REVISION);
            final Enforcer thePolicyEnforcer = PolicyEnforcers.defaultEvaluator(policy);
            this.policyEnforcer = thePolicyEnforcer;
            updateSearchIndexWithPolicy(syncedThing, thePolicyEnforcer);
        } else {
            log.warning("Received policy ID <{0}> is not expected ID <{1}>!", policy.getId(), policyId);
        }
    }

    private void handleException(final DittoRuntimeException exception) {
        if (exception instanceof ThingNotAccessibleException) {
            log.info("Thing no longer accessible - deleting the Thing from search index: {}", exception.getMessage());
            deleteThingFromSearchIndex();
        } else if (exception instanceof PolicyNotAccessibleException) {
            log.info("Policy no longer accessible - deleting the Thing from search index: {}", exception.getMessage());
            deleteThingFromSearchIndex();
        } else {
            log.error("Received exception while trying to sync thing <{}>: {} {}", thingId,
                    exception.getClass().getSimpleName(), exception.getMessage());
            triggerSynchronization();
        }
    }

    private void deleteThingFromSearchIndex() {
        final TraceContext traceContext = Kamon.tracer().newContext(TRACE_THING_DELETE);
        circuitBreaker.callWithCircuitBreakerCS(() -> searchUpdaterPersistence
                .delete(thingId)
                .via(finishTrace(traceContext))
                .runWith(Sink.last(), materializer)
                .whenComplete(this::handleDeletion));
    }

    private void handleDeletion(final Boolean success, final Throwable throwable) {
        if (isTrue(success)) {
            log.info("Thing <{}> was deleted from search index due to synchronization. The actor will be stopped now.",
                    thingId);
            ackSync(true);
            //stop the actor as the thing was deleted
            stopThisActor();
        } else if (throwable != null) {
            log.error(throwable, "Deletion due to synchronization of thing <{}> was not successful as the"
                    + " update operation failed!", thingId);
            triggerSynchronization();
        } else {
            //the thing did not exist anyway in the search index -> stop the actor
            ackSync(true);
            stopThisActor();
        }
    }

    private void handleErrorResponse(final ErrorResponse<?> errorResponse) {
        final DittoRuntimeException ex = errorResponse.getDittoRuntimeException();
        handleException(ex);
    }

    // eventually writes the Thing to the persistence and ends the synchronization cycle.
    // keeps stashing messages in the mean time.
    private void updateSearchIndexWithoutPolicy(final Thing newThing) {
        becomeSyncResultAwaiting();
        updateThing(newThing)
                .whenComplete((thingIndexChanged, throwable) ->
                        handleInsertOrUpdateResult(thingIndexChanged, newThing, throwable));
    }

    // eventually writes the Thing to the persistence, updates policy, then ends the synchronization cycle.
    // keeps stashing messages in the mean time.
    private void updateSearchIndexWithPolicy(final Thing newThing, final Enforcer thePolicyEnforcer) {
        becomeSyncResultAwaiting();
        updateThing(newThing)
                .whenComplete((thingIndexChanged, thingError) ->
                        updatePolicy(newThing, thePolicyEnforcer != null ? thePolicyEnforcer : policyEnforcer)
                                .whenComplete(
                                        (policyIndexChanged, policyError) -> {
                                            final Throwable error = thingError == null ? policyError : thingError;
                                            handleInsertOrUpdateResult(thingIndexChanged, newThing, error);
                                        })
                );
    }

    // update the entire thing. should only be called during synchronization. will interfere with bulk writes
    // if called during event processing.
    private CompletionStage<Boolean> updateThing(final Thing thing) {
        final long currentSequenceNumber = thing.getRevision()
                .map(ThingRevision::toLong)
                .orElseThrow(() -> {
                    final String message = MessageFormat.format("The Thing <{0}> has no revision!", thingId);
                    return new IllegalArgumentException(message);
                });

        final TraceContext traceContext = Kamon.tracer().newContext(TRACE_THING_MODIFIED);

        return circuitBreaker.callWithCircuitBreakerCS(
                () -> searchUpdaterPersistence
                        .insertOrUpdate(thing, currentSequenceNumber, policyRevision)
                        .via(finishTrace(traceContext))
                        .runWith(Sink.last(), materializer));
    }

    private CompletionStage<Boolean> updatePolicy(final Thing thing, final Enforcer policyEnforcer) {

        if (policyEnforcer == null) {
            log.warning("Enforcer was null when trying to update Policy search index - resyncing Policy!");
            syncPolicy(thing);
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }

        final TraceContext traceContext = Kamon.tracer().newContext(TRACE_POLICY_UPDATE);
        return circuitBreaker.callWithCircuitBreakerCS(() -> searchUpdaterPersistence
                .updatePolicy(thing, policyEnforcer)
                .via(finishTrace(traceContext))
                .runWith(Sink.last(), materializer)
                .whenComplete((isPolicyUpdated, throwable) -> {
                    if (null != throwable) {
                        log.error(throwable, "Failed to update policy because of an exception!");
                    } else if (!isPolicyUpdated) {
                        log.debug("The update operation for the policy of Thing <{}> did not have an effect, " +
                                "probably because it does not contain fine-grained policies!", thingId);
                    } else {
                        log.debug("Successfully updated policy.");
                    }
                }));
    }

    private void handleInsertOrUpdateResult(final Boolean indexChanged, final Thing entity, final Throwable throwable) {

        if (throwable == null) {
            if (isTrue(indexChanged)) {
                log.debug("The thing <{}> was successfully updated in search index", thingId);
                getSelf().tell(SyncSuccess.INSTANCE, null);
            } else {
                log.warning("The thing <{}> was not updated as the index was not changed by the upsert: {}",
                        thingId, entity);
                getSelf().tell(SyncFailure.INSTANCE, null);
            }
        } else {
            final String msgTemplate =
                    "The thing <{}> was not successfully updated in the search index as the update " +
                            "operation failed: {}";
            log.error(throwable, msgTemplate, thingId, throwable.getMessage());
            getSelf().tell(SyncFailure.INSTANCE, null);
        }
    }

    /////////////////////
    ////// HELPERS //////
    /////////////////////

    private static boolean schemaVersionHasPolicy(final JsonSchemaVersion schemaVersion) {
        return schemaVersion.toInt() > JsonSchemaVersion.V_1.toInt();
    }

    /**
     * Generate sync session ID & set as correlation ID, schedule timeout for this sync cycle.
     */
    private void beginNewSyncSession() {
        cancelSyncTimeoutAndResetSessionId();
        final String sessionId = UUID.randomUUID().toString();
        LogUtil.enhanceLogWithCorrelationId(log, sessionId);

        final ActorContext actorContext = getContext();
        final ActorSystem actorSystem = actorContext.system();
        final Scheduler scheduler = actorSystem.scheduler();
        final ActorRef receiver = getSelf();
        final AskTimeoutException askTimeoutException = new AskTimeoutException(sessionId);
        final ExecutionContextExecutor executor = actorContext.dispatcher();
        final ActorRef sender = null;

        syncSessionId = sessionId;
        syncTimeout = scheduler.scheduleOnce(thingsTimeout, receiver, askTimeoutException, executor, sender);
    }

    private void retrievePolicy(final String thePolicyId) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId("thingUpdater-sudoRetrievePolicy-" + UUID.randomUUID())
                .build();
        final SudoRetrievePolicy retrievePolicyCmd = SudoRetrievePolicy.of(thePolicyId, dittoHeaders);
        policiesShardRegion.tell(retrievePolicyCmd, getSelf());
    }

    private void retrieveThing() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId("thingUpdater-sudoRetrieveThing-" + UUID.randomUUID())
                .build();
        final SudoRetrieveThing sudoRetrieveThingCmd = SudoRetrieveThing.withOriginalSchemaVersion(thingId,
                dittoHeaders);
        final String cmdType = sudoRetrieveThingCmd.getType();
        final JsonSchemaVersion implementedSchemaVersion = sudoRetrieveThingCmd.getImplementedSchemaVersion();
        final Predicate<JsonField> regularOrSpecialFields = FieldType.regularOrSpecial();
        final JsonObject cmdJsonObject = sudoRetrieveThingCmd.toJson(implementedSchemaVersion, regularOrSpecialFields);
        final Object messageEnvelope = ShardedMessageEnvelope.of(thingId, cmdType, cmdJsonObject, dittoHeaders);

        // Send a message directly to the Things Shard Region.
        thingsShardRegion.tell(messageEnvelope, getSelf());
    }

    private void stashWithErrorsIgnored() {
        try {
            stash();
        } catch (final IllegalStateException e) {
            // ignore errors due to stashing a message more than once.
        }
    }

    private static Flow<Boolean, Boolean, NotUsed> finishTrace(final TraceContext traceContext) {
        return Flow.fromFunction(foo -> {
            traceContext.finish(); // finish kamon trace
            return foo;
        });
    }

    private static boolean isTrue(final Boolean success) {
        return success != null && success;
    }

    private void resetGatheredEvents() {
        gatheredEvents = new ArrayList<>();
    }

    private static final class SyncSuccess {

        static final SyncSuccess INSTANCE = new SyncSuccess();

        private SyncSuccess() {
            // no-op
        }

    }

    private static final class SyncFailure {

        static final SyncFailure INSTANCE = new SyncFailure();

        private SyncFailure() {
            // no-op
        }

    }

    private static class ActorInitializationComplete {

        static final ActorInitializationComplete INSTANCE = new ActorInitializationComplete();

        private ActorInitializationComplete() {
            // no-op
        }

    }

    private static final class SyncMetadata {

        private final ActorRef ackRecipient;
        private final String thingIdentifier;

        private SyncMetadata(final ActorRef ackRecipient, final IdentifiableStreamingMessage message) {
            this.ackRecipient = ackRecipient;
            thingIdentifier = message.asIdentifierString();
        }

        /**
         * Get the recipient of the ack message.
         *
         * @return ActorRef to the recipient of the ack message.
         */
        private ActorRef getAckRecipient() {
            return ackRecipient;
        }

        /**
         * Get an instance of a success message.
         *
         * @return The success message.
         */
        private StreamAck getSuccess() {
            return StreamAck.success(thingIdentifier);
        }

        /**
         * Get an instance of a failure message.
         *
         * @return The failure message.
         */
        private StreamAck getFailure() {
            return StreamAck.failure(thingIdentifier);
        }
    }

}
