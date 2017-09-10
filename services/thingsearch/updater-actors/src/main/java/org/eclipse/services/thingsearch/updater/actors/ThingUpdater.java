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
package org.eclipse.services.thingsearch.updater.actors;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.bson.conversions.Bson;
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
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SyncThing;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ShardedMessageEnvelope;
import org.eclipse.ditto.services.utils.distributedcache.actors.CacheType;
import org.eclipse.ditto.services.utils.distributedcache.actors.ModifyCacheEntryResponse;
import org.eclipse.ditto.services.utils.distributedcache.actors.ReadConsistency;
import org.eclipse.ditto.services.utils.distributedcache.actors.RegisterForCacheUpdates;
import org.eclipse.ditto.services.utils.distributedcache.actors.RetrieveCacheEntry;
import org.eclipse.ditto.services.utils.distributedcache.actors.RetrieveCacheEntryResponse;
import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;
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
import org.eclipse.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.services.thingsearch.persistence.write.impl.CombinedThingWrites;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
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
 * This Actor initiates persistence updates related to 1 thing. It has 2 main state transition cycles:
 * event processing and synchronization. Methods are grouped into the sections {@code EVENT PROCESSING},
 * {@code SYNCHRONIZATION} and {@code HELPERS}. Either cycle interacts with the persistence through its own
 * methods: {@code executeWrites} for event processing, {@code updateThing} and {@code updatePolicy} for
 * synchronization.
 */
final class ThingUpdater extends AbstractActorWithDiscardOldStash implements
        RequiresMessageQueue<DequeBasedMessageQueueSemantics> {

    /**
     * Placeholder for the revision number of an unknown entity; smaller than all revision numbers used by Akka.
     */
    private static final long UNKNOWN_REVISION = 0L;

    /**
     * How long to wait for things and policies by default.
     */
    static final java.time.Duration DEFAULT_THINGS_TIMEOUT = java.time.Duration.of(20, ChronoUnit.SECONDS);

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
    private final ActorRef thingCacheFacade;
    private final ActorRef policyCacheFacade;
    private final Materializer materializer;

    // transducer state-transition table
    private final Receive eventProcessingBehavior = createEventProcessingBehavior();
    private final Receive initialBehaviour = createInitialBehavior();
    private final Receive awaitSyncResultBehavior = createAwaitSyncResultBehavior();

    // maintenance and book-keeping
    private boolean transactionActive;
    private int syncAttempts;
    private Cancellable activityChecker;
    private CombinedThingWrites.Builder intermediateCombinedWritesBuilder;
    private boolean checkThingTagOnly;

    // state of Thing and Policy
    private long sequenceNumber = -1L;
    private JsonSchemaVersion schemaVersion;
    private String policyId;
    private long policyRevision = -1L;
    private PolicyEnforcer policyEnforcer;

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
        this.thingCacheFacade = thingCacheFacade;
        this.policyCacheFacade = policyCacheFacade;

        thingId = tryToGetThingId(StandardCharsets.UTF_8);
        materializer = ActorMaterializer.create(getContext());
        transactionActive = false;
        syncAttempts = 0;

        thingCacheFacade.tell(new RegisterForCacheUpdates(thingId, getSelf()), getSelf());

        scheduleCheckForThingActivity();
        searchUpdaterPersistence.getThingMetadata(thingId)
                .runWith(Sink.last(), materializer)
                .whenComplete((retrievedThingMetadata, throwable) -> {
                    if (throwable instanceof NoSuchElementException) {
                        // Thing not present in search index
                        log.debug("Thing was not yet present in search index: {}", thingId);
                    } else if (throwable != null) {
                        log.error(throwable, "Unexpected exception when retrieving latest revision from search index");
                    } else {
                        sequenceNumber = retrievedThingMetadata.getThingRevision();
                        policyId = retrievedThingMetadata.getPolicyId();
                        policyRevision = retrievedThingMetadata.getPolicyRevision();
                    }
                    retrieveCacheEntry();

                    // initialization is complete, switch to normal ThingUpdater behavior.
                    getSelf().tell(new ActorInitializationComplete(), null);
                });
    }

    private void retrieveCacheEntry() {
        final Object cacheContext = CacheType.THING.getContext();
        thingCacheFacade.tell(new RetrieveCacheEntry(thingId, cacheContext, ReadConsistency.LOCAL), getSelf());
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
     * @param thingCacheFacade the {@link org.eclipse.ditto.services.utils.distributedcache.actors.CacheFacadeActor} for accessing
     * the Thing cache in cluster.
     * @param policyCacheFacade the {@link org.eclipse.ditto.services.utils.distributedcache.actors.CacheFacadeActor} for accessing
     * the Policy cache in cluster.
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
                .match(ActorInitializationComplete.class, msg -> becomeActorInitialized())
                .matchAny(msg -> stashWithErrorsIgnored())
                .build();
    }

    private void becomeActorInitialized() {
        getContext().become(initialBehaviour);
        unstashAll();
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
        getContext().stop(getSelf());
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();

        if (activityChecker != null) {
            activityChecker.cancel();
        }
    }

    ///////////////////////////
    ///// INITIALIZED /////
    ///////////////////////////

    // The initial state of this Actor and also switched to when triggerSynchronization() is invoked

    /**
     * Initial state of the synchronization cycle.
     */
    private void becomeSyncInitialized() {
        log.debug("Becoming 'initialized' ...");
        retrieveCacheEntry();
        getContext().become(initialBehaviour);
    }

    private Receive createInitialBehavior() {
        return ReceiveBuilder.create()
                .match(SyncThing.class, s -> {
                    LogUtil.enhanceLogWithCorrelationId(log, s);
                    log.info("Got external SyncThing request in 'initialized': {}", s);
                    triggerSynchronization();
                })
                .match(ThingTag.class, tt -> {
                    log.debug("Got ThingTag during 'initialBehaviour': {}", tt);
                    checkThingTagOnly = true;
                    stash();
                })
                .match(ThingCreated.class, this::processInitialThingEvent)
                .match(RetrieveCacheEntryResponse.class, this::processCacheEntryResponse)
                .matchAny(msg -> stashWithErrorsIgnored()) // stash all other messages
                .build();
    }

    private void processCacheEntryResponse(final RetrieveCacheEntryResponse cacheEntryResponse) {
        if (cacheEntryResponse.hasContextOfType(CacheType.THING)) {
            processThingCacheEntryResponse(cacheEntryResponse);
        } else if (cacheEntryResponse.hasContextOfType(CacheType.POLICY)) {
            processPolicyCacheEntryResponse(cacheEntryResponse);
        } else {
            log.warning("Received unknown CacheEntry Response: {}", cacheEntryResponse);
        }
    }

    private void processThingCacheEntryResponse(final RetrieveCacheEntryResponse thingCacheEntryResponse) {
        final Optional<CacheEntry> cacheEntryOpt = thingCacheEntryResponse.getCacheEntry();

        if (cacheEntryOpt.isPresent() && cacheEntryOpt.get().getRevision() <= sequenceNumber) {
            log.debug("CacheEntry has seqNo {}, metadata has seqNo {}. Start event processing.",
                    cacheEntryOpt.get().getRevision(), sequenceNumber);
            becomeEventProcessing();
        } else if (cacheEntryOpt.isPresent() && cacheEntryOpt.get().isDeleted()) {
            log.debug("CacheEntry is marked as deleted -> deleting Thing from search index");
            // thing is/was deleted
            final Cancellable timeout =
                    scheduleTimeoutForResponse(Timeout.durationToTimeout(thingsTimeout),
                            "Deleting Thing from search index failed.");
            deleteThingFromSearchIndex(timeout);
        } else if (cacheEntryOpt.isPresent()) {
            final CacheEntry cacheEntry = cacheEntryOpt.get();
            policyId = cacheEntry.getPolicyId().orElse(null);

            if (cacheEntry.getRevision() > sequenceNumber) {
                log.debug("CacheEntry has higher revision ({}) than locally known one {} - " +
                        "syncing the Thing as a result!", cacheEntry.getRevision(), sequenceNumber);
                syncThing();
            } else {
                log.debug("CacheEntry has equal or lower revision ({}) than locally known one {}",
                        cacheEntry.getRevision(), sequenceNumber);

                if (policyId != null) {
                    // Thing in V2 with a policyId --> also retrieve policy from cache
                    final Object cacheContext = CacheType.POLICY.getContext();
                    policyCacheFacade.tell(
                            new RetrieveCacheEntry(policyId, cacheContext, ReadConsistency.LOCAL),
                            getSelf());
                    // and subscribe for changes
                    policyCacheFacade.tell(new RegisterForCacheUpdates(policyId, getSelf()),
                            getSelf());
                    // don't become another behavior!
                } else {
                    // Thing in V1 - directly become eventProcessing
                    becomeEventProcessing();
                }
            }
        } else {
            log.debug("CacheEntry was absent or marked as deleted - syncing the Thing as a result!");
            syncThing();
        }
    }

    private void processPolicyCacheEntryResponse(final RetrieveCacheEntryResponse policyCacheResponse) {
        final Optional<CacheEntry> cacheEntryOpt = policyCacheResponse.getCacheEntry();
        if (cacheEntryOpt.isPresent() && !cacheEntryOpt.get().isDeleted()) {
            final CacheEntry cacheEntry = cacheEntryOpt.get();
            if (cacheEntry.getRevision() > policyRevision) {
                log.debug("PolicyCache has higher revision ({}) than locally known one {} - " +
                        "syncing the Policy as a result!", cacheEntry.getRevision(), policyRevision);
                syncPolicy(null);
            } else {
                log.debug("PolicyCache has equal or lower revision ({}) than locally known one {}",
                        cacheEntry.getRevision(), policyRevision);
                becomeEventProcessing();
            }
        } else {
            log.debug("Policy entry was absent or marked as deleted - syncing the Policy as a result!");
            syncPolicy(null);
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
                .match(SyncThing.class, s -> {
                    LogUtil.enhanceLogWithCorrelationId(log, s);
                    log.info("Got external SyncThing request in 'eventProcessing': {}", s);
                    triggerSynchronization();
                })
                .match(ThingEvent.class, this::processThingEvent)
                .match(PolicyEvent.class, this::processPolicyEvent)
                .match(ThingTag.class, this::processThingTag)
                .match(Replicator.Changed.class, this::processChangedCacheEntry)
                .match(CheckForActivity.class, this::checkActivity)
                .match(PersistenceWriteResult.class, this::handlePersistenceUpdateResult)
                .match(ModifyCacheEntryResponse.class,
                        r -> log.debug("Got ModifyCacheEntryResponse: {}", r))
                .match(ModifyCacheEntryResponse.class,
                        r -> log.debug("Got ModifyPolicyCacheEntryResponse: {}", r))
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

    private void processThingTag(final ThingTag thingTag) {
        log.debug("Received new Thing Tag for thing <{}> with revision <{}> - last known revision is <{}>",
                thingId, thingTag.getRevision(), sequenceNumber);

        if (thingTag.getRevision() > sequenceNumber) {
            log.info("The Thing Tag for the thing <{}> has the revision {} which is greater than the current actor's"
                    + " sequence number <{}>.", thingId, thingTag.getRevision(), sequenceNumber);
            checkThingTagOnly = false;
            triggerSynchronization();
        }

        if (checkThingTagOnly) {
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
                final Cancellable timeout =
                        scheduleTimeoutForResponse(Timeout.durationToTimeout(thingsTimeout),
                                "Deleting Thing from search index failed.");
                deleteThingFromSearchIndex(timeout);
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
                final Cancellable timeout =
                        scheduleTimeoutForResponse(Timeout.durationToTimeout(thingsTimeout),
                                "Deleting Thing from search index failed.");
                deleteThingFromSearchIndex(timeout);
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

        if (thingEvent.getRevision() <= sequenceNumber) {
            log.info("Dropped thing event for thing id <{}> with revision <{}> because it was older than or "
                            + "equal to the current sequence number <{}> of the update actor.", thingId,
                    thingEvent.getRevision(), sequenceNumber);
            return;
        }

        if (thingEvent.getRevision() > sequenceNumber + 1) {
            log.info("Triggering synchronization for thing <{}> because the received revision <{}> is higher than"
                    + " the expected sequence number <{}>.", thingId, thingEvent.getRevision(), sequenceNumber + 1);
            triggerSynchronization();
            return;
        }

        if (processInitialThingEvent(thingEvent)) {
            log.debug("Processed initial event: {}", thingEvent.getType());
        }
        //check if it is necessary to load the policy first before processing the event
        else if (needToReloadPolicy(thingEvent)) {
            //if the policy needs to be loaded first, drop everything and synchronize.
            triggerSynchronization();
        } else {
            // attempt to add event, trigger synchronization in case of failures
            try {
                if (transactionActive) {
                    log.info("The update actor for thing <{}> is at the moment already processing a MongoDB update"
                                    + " operation and therefore the the current event is added to the"
                                    + " intermediate write builder.",
                            thingId);
                    addEventToIntermediateBuilder(thingEvent);
                    log.debug("Intermediate CombinedWrites builder has <{}> entries.",
                            intermediateCombinedWritesBuilder.getSize());
                } else {
                    final CombinedThingWrites.Builder localCombinedWritesBuilder =
                            CombinedThingWrites.newBuilder(sequenceNumber, policyEnforcer);

                    addEventToBuilder(localCombinedWritesBuilder, thingEvent);
                    executeWrites(localCombinedWritesBuilder);
                }

                // Update state related to the Thing. Policy state is maintained by synchronization.
                sequenceNumber = thingEvent.getRevision();
            } catch (final RuntimeException e) {
                log.error("Sync is triggered because: Failed to add event {} due to exception {}", thingEvent, e);
                triggerSynchronization();
            }
        }
    }

    private static boolean needToReloadPolicy(final ThingEvent thingEvent) {
        return thingEvent instanceof PolicyIdCreated || thingEvent instanceof PolicyIdModified;
    }

    private boolean processInitialThingEvent(final ThingEvent thingEvent) {
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
        }
        return false;
    }

    private JsonSchemaVersion schemaVersionToCheck(final ThingEvent thingEvent) {
        if (thingEvent instanceof ThingCreated) {
            return ((ThingCreated) thingEvent).getThing().getImplementedSchemaVersion();
        } else if (thingEvent instanceof ThingModified) {
            // handle things migrating from v1 to v2.
            // v1 events always come with ACL.
            // v2 events has no ACL. if it has no policyId either, then sync will be triggered.
            return ((ThingModified) thingEvent).getThing().getImplementedSchemaVersion();
        } else {
            return Optional.ofNullable(schemaVersion)
                    .orElse(policyId == null ? JsonSchemaVersion.V_1 : JsonSchemaVersion.LATEST);
        }
    }

    private void executeWrites(final CombinedThingWrites.Builder combinedWritesBuilder) {
        final CombinedThingWrites combinedWrites = combinedWritesBuilder.build();
        final List<Bson> combinedWriteDocuments = combinedWrites.getCombinedWriteDocuments();
        log.debug("Executing bulk write operation with <{}> updates.", combinedWriteDocuments.size());
        transactionActive = true;

        final TraceContext traceContext = Kamon.tracer().newContext(TRACE_THING_BULK_UPDATE);
        circuitBreaker.callWithCircuitBreakerCS(() -> searchUpdaterPersistence
                .executeCombinedWrites(thingId, combinedWrites)
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
            if (intermediateCombinedWritesBuilder != null) {
                log.debug("Intermediate combined writes are now written ..");
                final CombinedThingWrites.Builder builder = intermediateCombinedWritesBuilder;
                resetIntermediateCombinedWritesBuilder();
                executeWrites(builder);
            }
        } else {
            log.warning("The update operation for thing <{}> failed due to an unexpected sequence number!", thingId);
            triggerSynchronization();
        }
    }

    private void addEventToIntermediateBuilder(final ThingEvent thingEvent) {
        if (intermediateCombinedWritesBuilder == null) {
            intermediateCombinedWritesBuilder = CombinedThingWrites.newBuilder(sequenceNumber, policyEnforcer);
        }
        addEventToBuilder(intermediateCombinedWritesBuilder, thingEvent);
    }

    private void addEventToBuilder(final CombinedThingWrites.Builder builder, final ThingEvent thingEvent) {
        final JsonSchemaVersion localSchemaVersion = schemaVersionToCheck(thingEvent);
        builder.addEvent(thingEvent, localSchemaVersion);
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
        becomeSyncInitialized();
        transactionActive = false;
        resetIntermediateCombinedWritesBuilder();
        if (syncAttempts <= 3) {
            log.info("Synchronization of thing <{}> is now triggered (attempt={}).", thingId, syncAttempts);
            syncThing();
        } else {
            log.error("Synchronization failed after <{}> attempts.", syncAttempts - 1);
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
     * Final state of the synchronization cycle.
     * Transitions to event processing on success and re-attempt synchronization on failure.
     */
    // stash all messages until actor state change triggered by ongoing future
    private void becomeSyncResultAwaiting() {
        log.debug("Becoming 'syncResultAwaiting' ...");
        getContext().become(awaitSyncResultBehavior);
    }

    private Receive createAwaitSyncResultBehavior() {
        return ReceiveBuilder.create()
                .match(SyncSuccess.class, s -> becomeEventProcessing())
                .match(SyncFailure.class, f -> triggerSynchronization())
                .matchAny(msg -> stashWithErrorsIgnored())
                .build();
    }

    private void handleSyncThingResponse(final Cancellable timeout, final SudoRetrieveThingResponse response) {
        log.debug("Retrieved thing response='{}' for thing ID='{}' (attempt={}).", response, thingId, syncAttempts);

        final Thing syncedThing = response.getThing();
        updateThingSearchIndex(timeout, syncedThing);
    }

    private void updateThingSearchIndex(final Cancellable timeout, final Thing withThing) {
        // reset schema version and revision number
        sequenceNumber = withThing.getRevision().map(ThingRevision::toLong).orElse(UNKNOWN_REVISION);
        schemaVersion = withThing.getImplementedSchemaVersion();

        final String policyIdOfThing = withThing.getPolicyId().orElse(null);
        if (policyIdOfThing != null) {
            if (!policyIdOfThing.equals(policyId)) {
                // policyId changed!
                policyRevision = -1L; // reset policyRevision
                policyEnforcer = null; // reset policyEnforcer
                policyId = policyIdOfThing;
                policyCacheFacade.tell(new RegisterForCacheUpdates(policyIdOfThing, getSelf()), getSelf());
            }
        } else if (withThing.getAccessControlList().isPresent() && !withThing.getAccessControlList().get().isEmpty()) {
            policyRevision = -1L; // reset policyRevision
            policyEnforcer = null; // reset policyEnforcer
            policyId = null;
        } else {
            log.error("Thing to update in search index had neither a policyId nor an ACL: {}", withThing);
            triggerSynchronization();
            return;
        }

        // always synchronize policy if the schema version calls for it
        if (schemaVersionHasPolicy(schemaVersion)) {
            if (policyEnforcer != null) {
                updateSearchIndexWithPolicy(timeout, withThing);
            } else {
                syncPolicy(withThing);
            }
        } else {
            // No policy to worry about; we're done.
            updateSearchIndexWithoutPolicy(timeout, withThing);
        }
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
            policyEnforcer = PolicyEnforcers.defaultEvaluator(policy);

            if (syncedThing != null) {
                // update search index:
                updateSearchIndexWithPolicy(timeout, syncedThing);
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
            //stop the actor as the thing was deleted
            stopThisActor();
        } else if (throwable != null) {
            log.error(throwable, "Deletion due to synchronization of thing <{}> was not successful as the"
                    + " update operation failed!", thingId);
            triggerSynchronization();
        } else {
            //the thing did not exist anyway in the search index -> stop the actor
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
    private void updateSearchIndexWithPolicy(final Cancellable timeout, final Thing newThing) {
        becomeSyncResultAwaiting();
        updateThing(newThing)
                .whenComplete((thingIndexChanged, thingError) ->
                        updatePolicy(newThing, policyEnforcer)
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
                getSelf().tell(new SyncSuccess(), null);
            } else {
                log.warning("The thing <{}> was not updated as the index was not changed by the upsert: {}",
                        thingId, entity);
                getSelf().tell(new SyncFailure(), null);
            }
        } else {
            final String msgTemplate =
                    "The thing <{}> was not successfully updated in the search index as the update " +
                            "operation failed: {}";
            log.error(throwable, msgTemplate, thingId, throwable.getMessage());
            getSelf().tell(new SyncFailure(), null);
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
            // ignore errors due to stashing a message.
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

    private void resetIntermediateCombinedWritesBuilder() {
        intermediateCombinedWritesBuilder = null;
    }

    private static final class SyncSuccess {}

    private static final class SyncFailure {}

    private static class ActorInitializationComplete {}

}
