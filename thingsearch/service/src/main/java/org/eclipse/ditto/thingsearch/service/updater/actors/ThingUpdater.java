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
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.policies.api.PolicyReferenceTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.api.ThingTag;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
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
import org.eclipse.ditto.thingsearch.service.starter.actors.MongoClientExtension;

import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;

import akka.Done;
import akka.actor.AbstractActorWithStash;
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
final class ThingUpdater extends AbstractActorWithStash {

    private static final String FORCE_UPDATE = "force-update";

    private static final AcknowledgementRequest SEARCH_PERSISTED_REQUEST =
            AcknowledgementRequest.of(DittoAcknowledgementLabel.SEARCH_PERSISTED);

    private final DittoDiagnosticLoggingAdapter log;
    private final ThingId thingId;
    private final ShutdownBehaviour shutdownBehaviour;
    private final ActorRef changeQueueActor;
    private final ThingEventObserver thingEventObserver;
    private final double forceUpdateProbability;

    // state of Thing and Policy
    private long thingRevision = -1L;
    @Nullable private PolicyId policyId = null;
    private long policyRevision = -1L;

    // cache last update document for incremental updates
    @Nullable AbstractWriteModel lastWriteModel = null;

    @SuppressWarnings("unused") //It is used via reflection. See props method.
    private ThingUpdater(final ActorRef pubSubMediator,
            final ActorRef changeQueueActor,
            final ThingEventObserver thingEventObserver,
            final double forceUpdateProbability) {
        this(pubSubMediator, changeQueueActor, thingEventObserver, forceUpdateProbability, true, true);
    }

    ThingUpdater(final ActorRef pubSubMediator,
            final ActorRef changeQueueActor,
            final ThingEventObserver thingEventObserver,
            final double forceUpdateProbability,
            final boolean loadPreviousState,
            final boolean awaitRecovery) {

        log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        final var dittoSearchConfig = DittoSearchConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        thingId = tryToGetThingId();
        shutdownBehaviour = ShutdownBehaviour.fromId(thingId, pubSubMediator, getSelf());
        this.changeQueueActor = changeQueueActor;
        this.thingEventObserver = thingEventObserver;
        this.forceUpdateProbability = forceUpdateProbability;

        getContext().setReceiveTimeout(dittoSearchConfig.getUpdaterConfig().getMaxIdleTime());

        if (loadPreviousState) {
            recoverLastWriteModel(thingId);
        } else if (!awaitRecovery) {
            // Not loading the previous model is equivalent to initializing via a delete-one model.
            final var noLastModel = ThingDeleteModel.of(Metadata.of(thingId, -1L, null, null, null));
            getSelf().tell(noLastModel, getSelf());
        }
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param pubSubMediator Akka pub-sub mediator.
     * @param changeQueueActor reference of the change queue actor.
     * @param thingEventObserver the thing event observer
     * @param updaterConfig the updater config.
     * @return the Akka configuration Props object
     */
    static Props props(final ActorRef pubSubMediator, final ActorRef changeQueueActor,
            final ThingEventObserver thingEventObserver, final UpdaterConfig updaterConfig) {

        return Props.create(ThingUpdater.class, pubSubMediator, changeQueueActor, thingEventObserver,
                updaterConfig.getForceUpdateProbability());
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
                .match(ThingTag.class, this::processThingTag)
                .match(PolicyReferenceTag.class, this::processPolicyReferenceTag)
                .match(UpdateThing.class, this::updateThing)
                .match(UpdateThingResponse.class, this::processUpdateThingResponse)
                .match(ReceiveTimeout.class, this::stopThisActor)
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

    private void onNextWriteModel(final AbstractWriteModel nextWriteModel) {
        final WriteModel<BsonDocument> mongoWriteModel;
        final boolean forceUpdate = forceUpdateProbability > 0 && Math.random() < forceUpdateProbability;
        if (!forceUpdate && lastWriteModel instanceof ThingWriteModel && nextWriteModel instanceof ThingWriteModel) {
            final var last = (ThingWriteModel) lastWriteModel;
            final var next = (ThingWriteModel) nextWriteModel;
            final Optional<BsonDiff> diff = tryComputeDiff(next.getThingDocument(), last.getThingDocument());
            if (diff.isPresent() && diff.get().isDiffSmaller()) {
                final var aggregationPipeline = diff.get().consumeAndExport();
                if (aggregationPipeline.isEmpty()) {
                    log.debug("Skipping update due to empty diff <{}>", nextWriteModel);
                    getSender().tell(Done.getInstance(), getSelf());
                    return;
                }
                mongoWriteModel = new UpdateOneModel<>(nextWriteModel.getFilter(), aggregationPipeline);
                log.debug("Using incremental update <{}>", mongoWriteModel);
            } else {
                mongoWriteModel = nextWriteModel.toMongo();
                if (log.isDebugEnabled()) {
                    log.debug("Using replacement because diff is bigger or nonexistent. Diff=<{}>",
                            diff.map(BsonDiff::consumeAndExport));
                }
            }
        } else {
            mongoWriteModel = nextWriteModel.toMongo();
            if (forceUpdate) {
                log.debug("Using replacement (forceUpdate) <{}>", mongoWriteModel);
            } else {
                log.debug("Using replacement <{}>", mongoWriteModel);
            }
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
     */
    private void enqueueMetadata() {
        enqueueMetadata(exportMetadata(null, null));
    }

    private void enqueueMetadata(final Metadata metadata) {
        changeQueueActor.tell(metadata.withOrigin(getSelf()), getSelf());
    }

    private void processThingTag(final ThingTag thingTag) {
        log.debug("Received new Thing Tag for thing <{}> with revision <{}>: <{}>.",
                thingId, thingRevision, thingTag.asIdentifierString());

        if (thingTag.getRevision() > thingRevision) {
            log.debug("The Thing Tag for the thing <{}> has the revision {} which is greater than the current actor's"
                    + " sequence number <{}>.", thingId, thingTag.getRevision(), thingRevision);
            thingRevision = thingTag.getRevision();
            enqueueMetadata();
        } else {
            log.debug("Dropping <{}> because my thingRevision=<{}>", thingTag, thingRevision);
        }
        acknowledge(thingTag);
    }

    private void updateThing(final UpdateThing updateThing) {
        log.withCorrelationId(updateThing)
                .info("Requested to update search index <{}> by <{}>", updateThing, getSender());
        if (updateThing.getDittoHeaders().containsKey(FORCE_UPDATE)) {
            lastWriteModel = null;
        }
        final Metadata metadata = exportMetadata(null, null)
                .invalidateCaches(updateThing.shouldInvalidateThing(), updateThing.shouldInvalidatePolicy());
        if (updateThing.getDittoHeaders().getAcknowledgementRequests().contains(SEARCH_PERSISTED_REQUEST)) {
            enqueueMetadata(metadata.withSender(getSender()));
        } else {
            enqueueMetadata(metadata);
        }
    }

    private void processUpdateThingResponse(final UpdateThingResponse response) {
        if (!response.isSuccess()) {
            // discard last write model: index document is not known
            lastWriteModel = null;
            final Metadata metadata = exportMetadata(null, null).invalidateCaches(true, true);
            log.warning("Got negative acknowledgement for <{}>; updating to <{}>.",
                    Metadata.fromResponse(response),
                    metadata);
            enqueueMetadata(metadata);
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
            enqueueMetadata();
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
            enqueueMetadata(exportMetadataWithSender(shouldAcknowledge, thingEvent, getSender(), timer));
            thingEventObserver.processThingEvent(thingEvent);
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

}
