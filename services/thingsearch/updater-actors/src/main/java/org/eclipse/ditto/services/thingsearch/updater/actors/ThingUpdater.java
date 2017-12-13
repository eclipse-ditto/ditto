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
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyRevision;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcers;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.services.models.policies.PolicyCacheEntry;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.models.things.ThingCacheEntry;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.services.thingsearch.persistence.write.EventToPersistenceStrategyFactory;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingMetadata;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.services.utils.cluster.ShardedMessageEnvelope;
import org.eclipse.ditto.services.utils.distributedcache.actors.ModifyCacheEntryResponse;
import org.eclipse.ditto.services.utils.distributedcache.actors.RegisterForCacheUpdates;
import org.eclipse.ditto.services.utils.distributedcache.actors.RetrieveCacheEntryResponse;
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
import akka.cluster.ddata.LWWRegister;
import akka.cluster.ddata.ReplicatedData;
import akka.cluster.ddata.Replicator;
import akka.dispatch.DequeBasedMessageQueueSemantics;
import akka.dispatch.RequiresMessageQueue;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.pattern.CircuitBreaker;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.util.Timeout;
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
     * Max attempts when trying to sync a thing.
     */
    private static final int MAX_SYNC_ATTEMPTS = 3;

    private static final String TRACE_THING_MODIFIED = "thing.modified";
    private static final String TRACE_THING_BULK_UPDATE = "thing.bulkUpdate";
    private static final String TRACE_THING_DELETE = "thing.delete";
    private static final String TRACE_POLICY_UPDATE = "policy.update";

    private final DiagnosticLoggingAdapter log = Logging.apply(this);

    // configuration
    private final String thingId;
    private final FiniteDuration thingsTimeout;
    private final ActorRef thingsShardRegion;
    private final ActorRef policiesShardRegion;
    private final java.time.Duration activityCheckInterval;
    private final ThingsSearchUpdaterPersistence searchUpdaterPersistence;
    private final CircuitBreaker circuitBreaker;
    private final ActorRef policyCacheFacade;
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
    private PolicyEnforcer policyEnforcer;

    // required for acking of synchronization
    private SyncMetadata activeSyncMetadata = null;

    private ThingUpdater(final java.time.Duration thingsTimeout,
            final ActorRef thingsShardRegion,
            final ActorRef policiesShardRegion,
            final ThingsSearchUpdaterPersistence searchUpdaterPersistence,
            final CircuitBreaker circuitBreaker,
            final java.time.Duration activityCheckInterval,
            final ActorRef thingCacheFacade,
            final ActorRef policyCacheFacade) {

        this.thingsTimeout = Duration.create(thingsTimeout.toNanos(), TimeUnit.NANOSECONDS);
        this.thingsShardRegion = thingsShardRegion;
        this.policiesShardRegion = policiesShardRegion;
        this.activityCheckInterval = activityCheckInterval;
        this.searchUpdaterPersistence = searchUpdaterPersistence;
        this.circuitBreaker = circuitBreaker;
        this.policyCacheFacade = policyCacheFacade;
        this.gatheredEvents = new ArrayList<>();

        thingId = tryToGetThingId(StandardCharsets.UTF_8);
        materializer = ActorMaterializer.create(getContext());
        transactionActive = false;
        syncAttempts = 0;

        thingCacheFacade.tell(new RegisterForCacheUpdates(thingId, getSelf()), getSelf());

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
     * @param thingCacheFacade the {@link org.eclipse.ditto.services.utils.distributedcache.actors.CacheFacadeActor} for
     * accessing the Thing cache in cluster.
     * @param policyCacheFacade the {@link org.eclipse.ditto.services.utils.distributedcache.actors.CacheFacadeActor}
     * for accessing the Policy cache in cluster.
     * @return the Akka configuration Props object
     */
    static Props props(final ThingsSearchUpdaterPersistence searchUpdaterPersistence,
            final CircuitBreaker circuitBreaker,
            final ActorRef thingsShardRegion,
            final ActorRef policiesShardRegion,
            final java.time.Duration activityCheckInterval,
            final java.time.Duration thingsTimeout,
            final ActorRef thingCacheFacade,
            final ActorRef policyCacheFacade) {

        return Props.create(ThingUpdater.class, new Creator<ThingUpdater>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingUpdater create() throws Exception {
                return new ThingUpdater(thingsTimeout, thingsShardRegion, policiesShardRegion,
                        searchUpdaterPersistence, circuitBreaker, activityCheckInterval, thingCacheFacade,
                        policyCacheFacade);
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
                    if (Objects.nonNull(policyId)) {
                        policyCacheFacade.tell(new RegisterForCacheUpdates(policyId, getSelf()), getSelf());
                    }
                    becomeInitialMessageHandling();
                })
                .match(ActorInitializationComplete.class, msg -> becomeInitialMessageHandling())
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
        super.postStop();

        if (activityChecker != null) {
            activityChecker.cancel();
        }
    }

    /**
     * Shortcut to handle creation due to stale ThingTag or ThingEvent. If this actor is created by a message whose
     * corresponding Thing is up-to-date in the search index, then this actor terminates itself immediately.
     */
    private void becomeInitialMessageHandling() {
        final Receive behavior = ReceiveBuilder.create()
                .match(ThingEvent.class, this::processInitialThingEvent)
                /* TODO CR-4696: with the new tags-streaming approach, checkThingTagOnly-param seems not to make sense
                   anymore, cause we don't know which and how many events will arrive in the future and we
                   might get lots of unnecessary actor restarts
                 */
                .match(ThingTag.class, thingTag -> processThingTag(thingTag, false))
                .matchAny(message -> {
                    // this actor is not created due to a ThingTag message; handle current message by event processing
                    becomeEventProcessing();
                    getSelf().forward(message, getContext());
                })
                .build();
        getContext().become(behavior);
        unstashAll();
    }

    /**
     * Handles the first message sent to this actor if it is a Thing event. Terminates self if the event is stale, takes
     * shortcut if the event contains the corresponding Thing, and triggers synchronization otherwise.
     *
     * @param thingEvent The first message sent to this actor.
     */
    private void processInitialThingEvent(final ThingEvent thingEvent) {
        LogUtil.enhanceLogWithCorrelationId(log, thingEvent);

        log.debug("Received initial thing event for thing id <{}> with revision <{}>.", thingId,
                thingEvent.getRevision());

        if (thingEvent.getRevision() <= sequenceNumber) {
            log.info("Dropped initial thing event for thing id <{}> with revision <{}> because it was older than or "
                            + "equal to the current sequence number <{}> of the update actor. Terminating.", thingId,
                    thingEvent.getRevision(), sequenceNumber);
            stopThisActor();
        } else if (shortcutTakenForThingEvent(thingEvent)) {
            log.debug("Shortcut taken for initial thing event <{}>.", thingEvent);
        } else {
            log.debug("Synchronization is triggered for initial thing event <{}>.", thingEvent);
            triggerSynchronization();
        }
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
        getContext().become(eventProcessingBehavior);
        unstashAll();
    }

    private Receive createEventProcessingBehavior() {
        return ReceiveBuilder.create()
                .match(ThingEvent.class, this::processThingEvent)
                .match(PolicyEvent.class, this::processPolicyEvent)
                .match(ThingTag.class, thingTag -> processThingTag(thingTag, false))
                .match(Replicator.Changed.class, this::processChangedCacheEntry)
                .match(CheckForActivity.class, this::checkActivity)
                .match(PersistenceWriteResult.class, this::handlePersistenceUpdateResult)
                .match(ModifyCacheEntryResponse.class,
                        r -> log.debug("Got ModifyCacheEntryResponse: {}", r))
                .match(RetrieveCacheEntryResponse.class,
                        resp -> log.debug("Got RetrieveCacheEntryResponse: {}", resp))
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

    private void processThingTag(final ThingTag thingTag, final boolean checkThingTagOnly) {
        log.debug("Received new Thing Tag for thing <{}> with revision <{}> - last known revision is <{}>",
                thingId, thingTag.getRevision(), sequenceNumber);

        activeSyncMetadata = new SyncMetadata(getSender(), thingTag);

        final boolean shouldStopThisActor;
        if (thingTag.getRevision() > sequenceNumber) {
            log.info("The Thing Tag for the thing <{}> has the revision {} which is greater than the current actor's"
                    + " sequence number <{}>.", thingId, thingTag.getRevision(), sequenceNumber);
            shouldStopThisActor = false;
            triggerSynchronization();
        } else {
            shouldStopThisActor = checkThingTagOnly;
            ackSync(true);
        }

        if (shouldStopThisActor) {
            // the actor was only started to check the received tag, if nothing has to be done, we can shutdown immediately
            stopThisActor();
        }
    }

    private void processChangedCacheEntry(final Replicator.Changed changed) {
        final ReplicatedData replicatedData = changed.get(changed.key());
        if (replicatedData instanceof LWWRegister) {
            final LWWRegister<?> lwwRegister = (LWWRegister<?>) replicatedData;
            final Object value = lwwRegister.getValue();
            if (value instanceof ThingCacheEntry) {
                processThingCacheEntry((ThingCacheEntry) value);
            } else if (value instanceof PolicyCacheEntry) {
                processPolicyCacheEntry((PolicyCacheEntry) value);
            } else {
                log.warning("Received unknown changed CacheEntry: {}", value);
            }
        }
    }

    private void processThingCacheEntry(final ThingCacheEntry cacheEntry) {
        log.debug("Received new ThingCacheEntry for thing <{}> with revision <{}>.", thingId,
                cacheEntry.getRevision());

        if (cacheEntry.getRevision() > sequenceNumber) {
            if (cacheEntry.isDeleted()) {
                log.info("ThingCacheEntry was deleted, therefore deleting the Thing from search index: {}", cacheEntry);
                deleteThingFromSearchIndex(thingsTimeout);
            } else {
                log.info(
                        "The ThingCacheEntry for the thing <{}> has the revision {} with is greater than the current actor's"
                                + " sequence number <{}>.", thingId, cacheEntry.getRevision(), sequenceNumber);
                triggerSynchronization();
            }
        }
    }

    private void processPolicyCacheEntry(final PolicyCacheEntry cacheEntry) {
        log.debug("Received new PolicyCacheEntry for policy <{}> with revision <{}>.", policyId,
                cacheEntry.getRevision());

        if (cacheEntry.getRevision() > policyRevision) {
            if (cacheEntry.isDeleted()) {
                log.info("PolicyCacheEntry was deleted, therefore deleting the Thing from search index: {}",
                        cacheEntry);
                policyEnforcer = null;
                policyRevision = cacheEntry.getRevision();
                deleteThingFromSearchIndex(thingsTimeout);
            } else {
                log.info(
                        "The PolicyCacheEntry for the policy <{}> has the revision {} with is greater than the current actor's"
                                + " sequence number <{}>.", policyId, cacheEntry.getRevision(), policyRevision);
                triggerSynchronization();
            }
        }
    }

    private void processThingEvent(final ThingEvent thingEvent) {
        LogUtil.enhanceLogWithCorrelationId(log, thingEvent);
        log.debug("Received new thing event for thing id <{}> with revision <{}>.", thingId,
                thingEvent.getRevision());

        // check if the revision is valid (thingEvent.revision = 1 + sequenceNumber)
        if (thingEvent.getRevision() <= sequenceNumber) {
            log.info("Dropped thing event for thing id <{}> with revision <{}> because it was older than or "
                            + "equal to the current sequence number <{}> of the update actor.", thingId,
                    thingEvent.getRevision(), sequenceNumber);
        } else if (shortcutTakenForThingEvent(thingEvent)) {
            log.debug("Shortcut taken for thing event <{}>.", thingEvent);
        } else if (thingEvent.getRevision() > sequenceNumber + 1) {
            log.info("Triggering synchronization for thing <{}> because the received revision <{}> is higher than"
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
                    log.info("The update actor for thing <{}> is currently busy. The current event will be " +
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
                log.error("Sync is triggered because: Failed to add event {} due to exception {}", thingEvent, e);
                triggerSynchronization();
            }
        }
    }

    private void addEventToGatheredEvents(final ThingEvent thingEvent) {
        gatheredEvents.add(thingEvent);
    }

    private static boolean needToReloadPolicy(final ThingEvent thingEvent) {
        return thingEvent instanceof PolicyIdCreated || thingEvent instanceof PolicyIdModified;
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
            updateThingSearchIndex(null, createdThing);
            return true;
        } else if (thingEvent instanceof ThingModified) {
            // if the very first event in initial phase is the "ThingModified", we can shortcut a lot ..
            final ThingModified tm = (ThingModified) thingEvent;
            log.debug("Got ThingModified: {}", tm);
            final Thing modifiedThing = tm.getThing().toBuilder().setRevision(tm.getRevision()).build();
            updateThingSearchIndex(null, modifiedThing);
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
            final TraceContext traceContext = Kamon.tracer().newContext(TRACE_THING_BULK_UPDATE);
            circuitBreaker.callWithCircuitBreakerCS(() -> searchUpdaterPersistence
                    .executeCombinedWrites(thingId, thingEvents, policyEnforcer, targetRevision)
                    .via(finishTrace(traceContext))
                    .runWith(Sink.last(), materializer)
                    .whenComplete(this::processWriteResult))
                    .exceptionally(t -> {
                        if (t != null) {
                            log.error(t, "There occurred an error while processing a write operation within the"
                                    + " circuit breaker for thing <{}>.", thingId);
                            //e.g. in case of a circuit breaker timeout, the running transaction must be stopped
                            processWriteResult(false, t);
                        }
                        return null;
                    });
        }

    }

    private void processWriteResult(final boolean success, final Throwable throwable) {
        // send result as message to process the result in the actor context
        getSelf().tell(new PersistenceWriteResult(success, throwable), null);
    }

    private void handlePersistenceUpdateResult(final PersistenceWriteResult result) {
        transactionActive = false;
        if (result.getError() != null) {
            log.error(result.getError(), "The MongoDB operation for thing <{}> failed with an error!", thingId);
            triggerSynchronization();
        } else if (result.isSuccess()) {
            if (!gatheredEvents.isEmpty()) {
                log.info("<{}> gathered events will now be persisted.", gatheredEvents.size());
                final List<ThingEvent> eventsToPersist = gatheredEvents;
                // reset the gathered events
                resetGatheredEvents();
                persistThingEvents(eventsToPersist);
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
        policyEnforcer = null; // reset policyEnforcer

        syncAttempts++;
        transactionActive = false;
        resetGatheredEvents();
        if (syncAttempts <= MAX_SYNC_ATTEMPTS) {
            log.info("Synchronization of thing <{}> is now triggered (attempt={}).", thingId, syncAttempts);
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

        // schedule a message that indicates a timeout so we do not wait forever
        final String timeoutExceptionMessage = MessageFormat.format("The synchronization of thing <{0}> " +
                "did not complete in time!", thingId);
        final Cancellable timeout =
                scheduleTimeoutForResponse(Timeout.durationToTimeout(thingsTimeout), timeoutExceptionMessage);

        getContext().become(createAwaitSyncThingBehavior(timeout));
    }

    private Receive createAwaitSyncThingBehavior(final Cancellable timeout) {
        log.debug("Becoming 'awaitSyncThingBehavior' for thing <{}> ...", thingId);
        return ReceiveBuilder.create()
                .match(AskTimeoutException.class, e -> {
                    log.error(e, "Waiting for SudoRetrieveThingResponse timed out!");
                    triggerSynchronization();
                })
                .match(SudoRetrieveThingResponse.class, response -> handleSyncThingResponse(timeout, response))
                .match(ThingErrorResponse.class, response -> handleErrorResponse(timeout, response))
                .match(DittoRuntimeException.class, exception -> handleException(timeout, exception))
                .match(CheckForActivity.class, this::checkActivity)
                .matchAny(msg -> stashWithErrorsIgnored()) // stash all other messages
                .build();
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

        final String timeoutExceptionMessage = String.format("Synchronization of thing <%s> did not complete. Reason:" +
                " its policy <%s> was not retrieved in time.", thingId, policyId);
        final Cancellable timeout =
                scheduleTimeoutForResponse(Timeout.durationToTimeout(thingsTimeout), timeoutExceptionMessage);
        getContext().become(createAwaitSyncPolicyBehavior(timeout, syncedThing));
    }

    private Receive createAwaitSyncPolicyBehavior(final Cancellable timeout, final Thing syncedThing) {
        log.debug("Becoming 'awaitSyncPolicyBehavior' for thing <{}> ...", thingId);
        return ReceiveBuilder.create()
                .match(AskTimeoutException.class, e -> {
                    log.error(e, "Waiting for SudoRetrievePolicyResponse timed out!");
                    triggerSynchronization();
                })
                .match(SudoRetrievePolicyResponse.class,
                        response -> handleSyncPolicyResponse(timeout, syncedThing, response))
                .match(PolicyErrorResponse.class, response -> handleErrorResponse(timeout, response))
                .match(DittoRuntimeException.class, exception -> handleException(timeout, exception))
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
                    ackSync(true);
                    becomeEventProcessing();
                })
                .match(SyncFailure.class, f -> triggerSynchronization())
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
            log.debug("Cannot ack sync, cause no recipient is available.");
            return;
        }

        final ActorRef syncAckRecipient = activeSyncMetadata.getAckRecipient();
        final ActorRef deadLetters = getContext().getSystem().deadLetters();
        if (Objects.equals(syncAckRecipient, deadLetters)) {
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

    private void handleSyncThingResponse(final Cancellable timeout, final SudoRetrieveThingResponse response) {
        log.debug("Retrieved thing response='{}' for thing ID='{}' (attempt={}).", response, thingId, syncAttempts);

        final Thing syncedThing = response.getThing();
        updateThingSearchIndex(timeout, syncedThing);
    }

    private void updateThingSearchIndex(final Cancellable timeout, final Thing thing) {
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
                policyCacheFacade.tell(new RegisterForCacheUpdates(policyIdOfThing, getSelf()), getSelf());
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
                updateSearchIndexWithPolicy(timeout, thing, null);
            } else {
                syncPolicy(thing);
            }
        } else {
            // No policy to worry about; we're done.
            updateSearchIndexWithoutPolicy(timeout, thing);
        }
    }

    private static boolean hasNonEmptyAcl(final Thing thing) {
        return thing.getAccessControlList()
                .filter(acl -> !acl.isEmpty())
                .isPresent();
    }

    private void handleSyncPolicyResponse(final Cancellable timeout, final Thing syncedThing,
            final SudoRetrievePolicyResponse response) {
        log.debug("Retrieved policy response='{}' for thing ID='{}' and policyId='{}' (attempt={}).",
                response, thingId, policyId, syncAttempts);
        log.debug("Policy from retrieved policy response is: {}", response.getPolicy());

        final Policy policy = response.getPolicy();

        final boolean isExpectedPolicyId = policy.getId()
                .filter(policyId::equals)
                .isPresent();
        if (isExpectedPolicyId) {
            policyRevision = policy.getRevision().map(PolicyRevision::toLong).orElse(UNKNOWN_REVISION);
            final PolicyEnforcer thePolicyEnforcer = PolicyEnforcers.defaultEvaluator(policy);
            this.policyEnforcer = thePolicyEnforcer;

            if (syncedThing != null) {
                // update search index:
                updateSearchIndexWithPolicy(timeout, syncedThing, thePolicyEnforcer);
            } else {
                // Thing was not synced before
                syncThing();
            }
        } else {
            log.info("Received policy ID <{0}> is not expected ID <{1}>!", policy.getId(), policyId);
        }
    }

    private void handleException(final Cancellable timeout, final DittoRuntimeException exception) {

        if (exception instanceof ThingNotAccessibleException) {
            log.info("Thing no longer accessible - deleting the Thing from search index: {}", exception.getMessage());
            deleteThingFromSearchIndex(timeout);
        } else if (exception instanceof PolicyNotAccessibleException) {
            log.info("Policy no longer accessible - deleting the Thing from search index: {}", exception.getMessage());
            deleteThingFromSearchIndex(timeout);
        } else {
            timeout.cancel();
            log.error("Received exception while trying to sync thing <{}>: {} {}", thingId,
                    exception.getClass().getSimpleName(), exception.getMessage());
            triggerSynchronization();
        }
    }

    private void deleteThingFromSearchIndex(final FiniteDuration timeout) {
        deleteThingFromSearchIndex(scheduleTimeoutForResponse(Timeout.durationToTimeout(timeout),
                "Deleting Thing from search index failed."));
    }

    private void deleteThingFromSearchIndex(final Cancellable timeout) {
        final TraceContext traceContext = Kamon.tracer().newContext(TRACE_THING_DELETE);
        circuitBreaker.callWithCircuitBreakerCS(() -> searchUpdaterPersistence
                .delete(thingId)
                .via(finishTrace(traceContext))
                .runWith(Sink.last(), materializer)
                .whenComplete((success, throwable) -> handleDeletion(timeout, success, throwable)));
    }

    private void handleDeletion(final Cancellable timeout, final Boolean success, final Throwable throwable) {
        timeout.cancel();

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

    private void handleErrorResponse(final Cancellable timeout, final ErrorResponse<?> errorResponse) {
        final DittoRuntimeException ex = errorResponse.getDittoRuntimeException();
        handleException(timeout, ex);
    }

    // eventually writes the Thing to the persistence and ends the synchronization cycle.
    // keeps stashing messages in the mean time.
    private void updateSearchIndexWithoutPolicy(final Cancellable timeout, final Thing newThing) {
        becomeSyncResultAwaiting();
        updateThing(newThing)
                .whenComplete((thingIndexChanged, throwable) ->
                        handleInsertOrUpdateResult(thingIndexChanged, newThing, timeout, throwable));
    }

    // eventually writes the Thing to the persistence, updates policy, then ends the synchronization cycle.
    // keeps stashing messages in the mean time.
    private void updateSearchIndexWithPolicy(final Cancellable timeout, final Thing newThing,
            final PolicyEnforcer thePolicyEnforcer) {
        becomeSyncResultAwaiting();
        updateThing(newThing)
                .whenComplete((thingIndexChanged, thingError) ->
                        updatePolicy(newThing, thePolicyEnforcer != null ? thePolicyEnforcer : policyEnforcer)
                                .whenComplete(
                                        (policyIndexChanged, policyError) -> {
                                            final Throwable error = thingError == null ? policyError : thingError;
                                            handleInsertOrUpdateResult(thingIndexChanged, newThing, timeout, error);
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

    private CompletionStage<Boolean> updatePolicy(final Thing thing, final PolicyEnforcer policyEnforcer) {

        if (policyEnforcer == null) {
            log.warning("PolicyEnforcer was null when trying to update Policy search index - resyncing Policy!");
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
                        log.error("Failed to update policy because of an unknown reason!");
                    }
                }));
    }

    private void handleInsertOrUpdateResult(final boolean indexChanged, final Thing entity, final Cancellable timeout,
            final Throwable throwable) {

        // cancel the timeout
        if (timeout != null) {
            timeout.cancel();
        }

        if (throwable == null) {
            if (indexChanged) {
                log.debug("The thing <{}> was successfully updated in search index", thingId);
                syncAttempts = 0;
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
     * Schedules a message that indicates a timeout so we do not wait forever for a response to arrive.
     */
    private Cancellable scheduleTimeoutForResponse(final Timeout timeout, final String timeoutExceptionMessage) {
        final ActorContext actorContext = getContext();
        final ActorSystem actorSystem = actorContext.system();
        final Scheduler scheduler = actorSystem.scheduler();
        final ActorRef receiver = getSelf();
        final AskTimeoutException askTimeoutException = new AskTimeoutException(timeoutExceptionMessage);
        final ExecutionContextExecutor executor = actorContext.dispatcher();
        final ActorRef sender = null;

        return scheduler.scheduleOnce(timeout.duration(), receiver, askTimeoutException, executor, sender);
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

    private Flow<Boolean, Boolean, NotUsed> finishTrace(final TraceContext traceContext) {
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

        private SyncMetadata(final ActorRef ackRecipient, final ThingTag thingTag) {
            this.ackRecipient = ackRecipient;
            thingIdentifier = thingTag.asIdentifierString();
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
