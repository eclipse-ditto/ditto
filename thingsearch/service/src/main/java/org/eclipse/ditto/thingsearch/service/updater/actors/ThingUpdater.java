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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.BsonInvalidOperationException;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.service.actors.ShutdownBehaviour;
import org.eclipse.ditto.internal.models.streaming.IdentifiableStreamingMessage;
import org.eclipse.ditto.internal.utils.akka.actors.AbstractActorWithStashWithTimers;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.policies.api.PolicyReferenceTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.api.commands.sudo.UpdateThing;
import org.eclipse.ditto.thingsearch.api.commands.sudo.UpdateThingResponse;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.common.config.UpdaterConfig;
import org.eclipse.ditto.thingsearch.service.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.write.mapping.BsonDiff;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.ConsistencyLag;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.SearchUpdaterStream;
import org.eclipse.ditto.thingsearch.service.starter.actors.MongoClientExtension;

import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.cluster.sharding.ShardRegion;
import akka.pattern.Patterns;
import akka.stream.javadsl.Sink;

/**
 * This Actor initiates persistence updates related to 1 thing.
 */
final class ThingUpdater extends AbstractActorWithStashWithTimers {

    private static final String FORCE_UPDATE = "force-update";

    private static final AcknowledgementRequest SEARCH_PERSISTED_REQUEST =
            AcknowledgementRequest.of(DittoAcknowledgementLabel.SEARCH_PERSISTED);

    private static final String FORCE_UPDATE_AFTER_START = "FORCE_UPDATE_AFTER_START";

    private static final Counter INCORRECT_PATCH_UPDATE_COUNT = DittoMetrics.counter("search_incorrect_patch_updates");
    private static final Counter UPDATE_FAILURE_COUNT = DittoMetrics.counter("search_update_failures");
    private static final Counter PATCH_UPDATE_COUNT = DittoMetrics.counter("search_patch_updates");
    private static final Counter PATCH_SKIP_COUNT = DittoMetrics.counter("search_patch_skips");
    private static final Counter FULL_UPDATE_COUNT = DittoMetrics.counter("search_full_updates");

    private final DittoDiagnosticLoggingAdapter log;
    private final ThingId thingId;
    private final ShutdownBehaviour shutdownBehaviour;
    private final ActorRef changeQueueActor;
    private final double forceUpdateProbability;

    // state of Thing and Policy
    private long thingRevision = -1L;
    @Nullable private PolicyId policyId = null;
    private long policyRevision = -1L;

    // cache last update document for incremental updates
    @Nullable AbstractWriteModel lastWriteModel = null;
    private boolean forceNextUpdate = false;

    @SuppressWarnings("unused") //It is used via reflection. See props method.
    private ThingUpdater(final ActorRef pubSubMediator,
            final ActorRef changeQueueActor,
            final double forceUpdateProbability,
            final Duration forceUpdateAfterStartTimeout,
            final double forceUpdateAfterStartRandomFactor) {
        this(pubSubMediator, changeQueueActor, forceUpdateProbability, forceUpdateAfterStartTimeout,
                forceUpdateAfterStartRandomFactor, true, true);
    }

    ThingUpdater(final ActorRef pubSubMediator,
            final ActorRef changeQueueActor,
            final double forceUpdateProbability,
            final Duration forceUpdateAfterStartTimeout,
            final double forceUpdateAfterStartRandomFactor,
            final boolean loadPreviousState,
            final boolean awaitRecovery) {

        log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        final var dittoSearchConfig = DittoSearchConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        thingId = tryToGetThingId();
        shutdownBehaviour = ShutdownBehaviour.fromId(thingId, pubSubMediator, getSelf());
        this.changeQueueActor = changeQueueActor;
        this.forceUpdateProbability = forceUpdateProbability;

        getContext().setReceiveTimeout(dittoSearchConfig.getUpdaterConfig().getMaxIdleTime());

        if (loadPreviousState) {
            recoverLastWriteModel(thingId);
        } else if (!awaitRecovery) {
            // Not loading the previous model is equivalent to initializing via a delete-one model.
            final var noLastModel = ThingDeleteModel.of(Metadata.of(thingId, -1L, null, null, null));
            getSelf().tell(noLastModel, getSelf());
        }
        if (forceUpdateAfterStartTimeout.negated().isNegative()) {
            getTimers().startSingleTimer(FORCE_UPDATE_AFTER_START, FORCE_UPDATE_AFTER_START,
                    randomizeTimeout(forceUpdateAfterStartTimeout, forceUpdateAfterStartRandomFactor));
        }
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param changeQueueActor reference of the change queue actor.
     * @param updaterConfig the updater config.
     * @return the Akka configuration Props object
     */
    static Props props(final ActorRef pubSubMediator, final ActorRef changeQueueActor,
            final UpdaterConfig updaterConfig) {

        // Use duration 0 to disable force-update-after-start-timeout.
        final var effectiveForceUpdateAfterStartTimeout = updaterConfig.isForceUpdateAfterStartEnabled()
                ? updaterConfig.getForceUpdateAfterStartTimeout()
                : Duration.ZERO;

        return Props.create(ThingUpdater.class, pubSubMediator, changeQueueActor,
                updaterConfig.getForceUpdateProbability(),
                effectiveForceUpdateAfterStartTimeout,
                updaterConfig.getForceUpdateAfterStartRandomFactor());
    }

    @Override
    public Receive createReceive() {
        return shutdownBehaviour.createReceive()
                .match(AbstractWriteModel.class, this::recoveryComplete)
                .match(ReceiveTimeout.class, this::stopThisActor)
                .matchAny(this::matchAnyDuringRecovery)
                .build();
    }

    private void recoveryComplete(final AbstractWriteModel writeModel) {
        log.debug("Recovered: <{}>", writeModel);
        lastWriteModel = writeModel;
        getContext().become(recoveredBehavior());
        unstashAll();
    }

    private Receive recoveredBehavior() {
        return shutdownBehaviour.createReceive()
                .match(ThingEvent.class, this::processThingEvent)
                .match(AbstractWriteModel.class, this::onNextWriteModel)
                .match(PolicyReferenceTag.class, this::processPolicyReferenceTag)
                .match(UpdateThing.class, this::updateThing)
                .match(UpdateThingResponse.class, this::processUpdateThingResponse)
                .match(ReceiveTimeout.class, this::stopThisActor)
                .matchEquals(FORCE_UPDATE_AFTER_START, this::forceUpdateAfterStart)
                .matchAny(m -> {
                    log.warning("Unknown message in 'eventProcessing' behavior: {}", m);
                    unhandled(m);
                })
                .build();
    }

    private void matchAnyDuringRecovery(final Object message) {
        log.debug("Stashing during initialization: <{}>", message);
        stash();
    }

    private void forceUpdateAfterStart(final String trigger) {
        log.debug("Forcing the next update to be a full 'forceUpdate'");
        forceNextUpdate = true;
        enqueueMetadata(UpdateReason.FORCE_UPDATE_AFTER_START);
    }

    private void onNextWriteModel(final AbstractWriteModel nextWriteModel) {
        final WriteModel<BsonDocument> mongoWriteModel;
        final boolean forceUpdate = (forceUpdateProbability > 0 && Math.random() < forceUpdateProbability) ||
                forceNextUpdate;
        if (!forceUpdate && lastWriteModel instanceof ThingWriteModel && nextWriteModel instanceof ThingWriteModel) {
            final var last = (ThingWriteModel) lastWriteModel;
            final var next = (ThingWriteModel) nextWriteModel;
            final Optional<BsonDiff> diff = tryComputeDiff(next.getThingDocument(), last.getThingDocument());
            if (diff.isPresent() && diff.get().isDiffSmaller()) {
                final var aggregationPipeline = diff.get().consumeAndExport();
                if (aggregationPipeline.isEmpty()) {
                    log.debug("Skipping update due to empty diff <{}>", nextWriteModel);
                    getSender().tell(Done.getInstance(), getSelf());
                    PATCH_SKIP_COUNT.increment();

                    return;
                }
                final var filter = ((ThingWriteModel) nextWriteModel)
                        .asPatchUpdate(lastWriteModel.getMetadata().getThingRevision())
                        .getFilter();
                mongoWriteModel = new UpdateOneModel<>(filter, aggregationPipeline);
                log.debug("Using incremental update <{}>", mongoWriteModel);
                PATCH_UPDATE_COUNT.increment();
            } else {
                mongoWriteModel = nextWriteModel.toMongo();
                if (log.isDebugEnabled()) {
                    log.debug("Using replacement because diff is bigger or nonexistent. Diff=<{}>",
                            diff.map(BsonDiff::consumeAndExport));
                }
                FULL_UPDATE_COUNT.increment();
            }
        } else {
            mongoWriteModel = nextWriteModel.toMongo();
            if (forceUpdate) {
                log.debug("Using replacement (forceUpdate) <{}> - forceNextUpdate was: <{}>", mongoWriteModel,
                        forceNextUpdate);
                forceNextUpdate = false;
            } else {
                log.debug("Using replacement <{}>", mongoWriteModel);
            }
            FULL_UPDATE_COUNT.increment();
        }
        getSender().tell(mongoWriteModel, getSelf());
        lastWriteModel = nextWriteModel;
    }

    private Optional<BsonDiff> tryComputeDiff(final BsonDocument minuend, final BsonDocument subtrahend) {
        try {
            return Optional.of(BsonDiff.minusThingDocs(minuend, subtrahend));
        } catch (BsonInvalidOperationException e) {
            log.error(e, "Failed to compute BSON diff between <{}> and <{}>", minuend, subtrahend);

            return Optional.empty();
        }
    }

    private void stopThisActor(final ReceiveTimeout receiveTimeout) {
        log.debug("stopping ThingUpdater <{}> due to <{}>", thingId, receiveTimeout);
        getContext().getParent().tell(new ShardRegion.Passivate(PoisonPill.getInstance()), getSelf());
    }

    /**
     * Export the metadata of this updater.
     *
     * @param timer an optional timer measuring the search updater's consistency lag.
     */
    private Metadata exportMetadata(@Nullable final ThingEvent<?> event, @Nullable final StartedTimer timer) {
        return Metadata.of(thingId, thingRevision, policyId, policyRevision,
                event == null ? List.of() : List.of(event), timer, null);
    }

    private Metadata exportMetadataWithSender(final boolean shouldAcknowledge,
            final ThingEvent<?> event,
            final ActorRef sender,
            final StartedTimer consistencyLagTimer) {
        if (shouldAcknowledge) {
            return Metadata.of(thingId, thingRevision, policyId, policyRevision, List.of(event), consistencyLagTimer,
                    sender);
        } else {
            return exportMetadata(event, consistencyLagTimer);
        }
    }

    /**
     * Push metadata of this updater to the queue of thing-changes to be streamed into the persistence.
     *
     * @param updateReason the reason why the search index is updated.
     */
    private void enqueueMetadata(final UpdateReason updateReason) {
        enqueueMetadata(exportMetadata(null, null).withUpdateReason(updateReason));
    }

    private void enqueueMetadata(final Metadata metadata) {
        changeQueueActor.tell(metadata.withOrigin(getSelf()), getSelf());
    }

    private void updateThing(final UpdateThing updateThing) {
        log.withCorrelationId(updateThing)
                .info("Requested to update search index <{}> by <{}>", updateThing, getSender());
        if (updateThing.getDittoHeaders().containsKey(FORCE_UPDATE)) {
            lastWriteModel = null;
            forceNextUpdate = true;
        }
        final Metadata metadata = exportMetadata(null, null)
                .invalidateCaches(updateThing.shouldInvalidateThing(), updateThing.shouldInvalidatePolicy())
                .withUpdateReason(updateThing.getUpdateReason());
        if (updateThing.getDittoHeaders().getAcknowledgementRequests().contains(SEARCH_PERSISTED_REQUEST)) {
            enqueueMetadata(metadata.withSender(getSender()));
        } else {
            enqueueMetadata(metadata);
        }
    }

    private void processUpdateThingResponse(final UpdateThingResponse response) {
        final var isFailure = !response.isSuccess();
        final var isIncorrectPatch =
                response.getDittoHeaders().containsKey(SearchUpdaterStream.FORCE_UPDATE_INCORRECT_PATCH);
        if (isFailure || isIncorrectPatch) {
            // discard last write model: index document is not known
            lastWriteModel = null;
            final Metadata metadata =
                    exportMetadata(null, null).invalidateCaches(true, true);
            final String warningTemplate;
            // check first for incorrect patch update otherwise the else branch is never triggered.
            if (isIncorrectPatch) {
                warningTemplate = "Inconsistent patch update detected for <{}>; updating to <{}>.";
                INCORRECT_PATCH_UPDATE_COUNT.increment();
            } else {
                warningTemplate = "Got negative acknowledgement for <{}>; updating to <{}>.";
                UPDATE_FAILURE_COUNT.increment();
            }
            log.warning(warningTemplate, Metadata.fromResponse(response), metadata);
            enqueueMetadata(metadata.withUpdateReason(UpdateReason.RETRY));
        }
    }

    private void processPolicyReferenceTag(final PolicyReferenceTag policyReferenceTag) {
        if (log.isDebugEnabled()) {
            log.debug("Received new Policy-Reference-Tag for thing <{}> with revision <{}>,  policy-id <{}> and " +
                            "policy-revision <{}>: <{}>.",
                    thingId, thingRevision, policyId, policyRevision, policyReferenceTag.asIdentifierString());
        }

        final var policyTag = policyReferenceTag.getPolicyTag();
        final var policyIdOfTag = policyTag.getEntityId();
        if (!Objects.equals(policyId, policyIdOfTag) || policyRevision < policyTag.getRevision()) {
            this.policyId = policyIdOfTag;
            policyRevision = policyTag.getRevision();
            enqueueMetadata(UpdateReason.POLICY_UPDATE);
        } else {
            log.debug("Dropping <{}> because my policyId=<{}> and policyRevision=<{}>",
                    policyReferenceTag, policyId, policyRevision);
        }
        acknowledge(policyReferenceTag);
    }

    private void processThingEvent(final ThingEvent<?> thingEvent) {
        final DittoDiagnosticLoggingAdapter l = log.withCorrelationId(thingEvent);
        l.debug("Received new thing event for thing id <{}> with revision <{}>.", thingId, thingEvent.getRevision());
        final boolean shouldAcknowledge =
                thingEvent.getDittoHeaders().getAcknowledgementRequests().contains(SEARCH_PERSISTED_REQUEST);

        // check if the revision is valid (thingEvent.revision = 1 + sequenceNumber)
        if (thingEvent.getRevision() <= thingRevision && !shouldAcknowledge) {
            l.debug("Dropped thing event for thing id <{}> with revision <{}> because it was older than or "
                            + "equal to the current sequence number <{}> of the update actor.", thingId,
                    thingEvent.getRevision(), thingRevision);
        } else {
            l.debug("Applying thing event <{}>.", thingEvent);
            thingRevision = thingEvent.getRevision();
            final StartedTimer timer = DittoMetrics.timer(ConsistencyLag.TIMER_NAME)
                    .tag(ConsistencyLag.TAG_SHOULD_ACK, Boolean.toString(shouldAcknowledge))
                    .onExpiration(startedTimer ->
                            l.warning("Timer measuring consistency lag timed out for event <{}>", thingEvent))
                    .start();
            DittoTracing.wrapTimer(DittoTracing.extractTraceContext(thingEvent), timer);
            ConsistencyLag.startS0InUpdater(timer);
            enqueueMetadata(
                    exportMetadataWithSender(shouldAcknowledge, thingEvent, getSender(), timer).withUpdateReason(
                            UpdateReason.THING_UPDATE));
        }
    }

    private ThingId tryToGetThingId() {
        final Charset utf8 = StandardCharsets.UTF_8;
        try {
            return getThingId(utf8);
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(MessageFormat.format("Charset <{0}> is unsupported!", utf8.name()), e);
        }
    }

    private ThingId getThingId(final Charset charset) throws UnsupportedEncodingException {
        final String actorName = self().path().name();
        return ThingId.of(URLDecoder.decode(actorName, charset.name()));
    }

    private void acknowledge(final IdentifiableStreamingMessage message) {
        final ActorRef sender = getSender();
        if (!getContext().system().deadLetters().equals(sender)) {
            getSender().tell(StreamAck.success(message.asIdentifierString()), getSelf());
        }
    }

    private void recoverLastWriteModel(final ThingId thingId) {
        final var actorSystem = getContext().getSystem();
        // using search client instead of updater client for READ to ensure consistency in case of shard migration
        final var client = MongoClientExtension.get(actorSystem).getSearchClient();
        final var searchPersistence = new MongoThingsSearchPersistence(client, actorSystem);
        final var writeModelFuture = searchPersistence.recoverLastWriteModel(thingId).runWith(Sink.head(), actorSystem);
        Patterns.pipe(writeModelFuture, getContext().getDispatcher()).to(getSelf());
    }

    private static Duration randomizeTimeout(final Duration minTimeout, final double randomFactor) {
        final long randomDelayMillis = (long) (Math.random() * randomFactor * minTimeout.toMillis());
        return minTimeout.plus(Duration.ofMillis(randomDelayMillis));
    }
}
