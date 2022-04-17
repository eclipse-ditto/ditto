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
import java.util.function.Function;

import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.eclipse.ditto.base.api.common.ShutdownReasonType;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.service.actors.ShutdownBehaviour;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOff;
import org.eclipse.ditto.internal.models.streaming.IdentifiableStreamingMessage;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.PolicyReferenceTag;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.api.commands.sudo.UpdateThing;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.WriteResultAndErrors;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.BulkWriteResultAckFlow;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.ConsistencyLag;

import com.mongodb.client.model.DeleteOneModel;

import akka.Done;
import akka.NotUsed;
import akka.actor.AbstractFSMWithStash;
import akka.actor.ActorRef;
import akka.actor.FSM;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.japi.pf.PFBuilder;
import akka.pattern.Patterns;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * This Actor initiates persistence updates related to 1 thing.
 */
public final class ThingUpdater extends AbstractFSMWithStash<ThingUpdater.State, ThingUpdater.Data> {

    private static final Counter INCORRECT_PATCH_UPDATE_COUNT =
            DittoMetrics.counter("wildcard_search_incorrect_patch_updates");
    private static final Counter UPDATE_FAILURE_COUNT = DittoMetrics.counter("wildcard_search_update_failures");

    private static final Duration BLOCK_NAMESPACE_SHUTDOWN_DELAY = Duration.ofMinutes(2);

    // alias Ditto Shutdown class because FSM shadows it
    private static final Class<org.eclipse.ditto.base.api.common.Shutdown> SHUTDOWN_CLASS =
            org.eclipse.ditto.base.api.common.Shutdown.class;

    // logger for "trace" statements
    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(ThingUpdater.class);

    private static final AcknowledgementRequest SEARCH_PERSISTED_REQUEST =
            AcknowledgementRequest.of(DittoAcknowledgementLabel.SEARCH_PERSISTED);

    private static final Duration THING_DELETION_TIMEOUT = Duration.ofMinutes(5);
    private static final String FORCE_UPDATE = "force-update";

    private final DittoDiagnosticLoggingAdapter log;
    private final ThingId thingId;
    private final Flow<Data, Result, NotUsed> flow;
    private final Materializer materializer;
    private final Duration writeInterval;
    private ExponentialBackOff backOff;
    private boolean shuttingDown = false;
    @Nullable private UniqueKillSwitch killSwitch;

    /**
     * Data of the thing-updater.
     *
     * @param metadata The most up-to-date metadata known to this actor.
     * @param lastWriteModel The last write model confirmed to be written to the persistence.
     */
    public record Data(Metadata metadata, AbstractWriteModel lastWriteModel) {}

    /**
     * The result of a persistence operation.
     *
     * @param mongoWriteModel The write model describing the persistence operation.
     * @param resultAndErrors The result of the persistence operation.
     */
    public record Result(MongoWriteModel mongoWriteModel, WriteResultAndErrors resultAndErrors) {

        public static Result fromError(final Metadata metadata, final Throwable error) {
            final var mockWriteModel = MongoWriteModel.of(
                    ThingDeleteModel.of(metadata),
                    new DeleteOneModel<>(new BsonDocument()),
                    false);
            return new Result(mockWriteModel, WriteResultAndErrors.failure(error));
        }
    }

    enum State {
        RECOVERING,
        READY,
        PERSISTING,
        RETRYING
    }

    enum Control {
        TICK
    }

    private ThingUpdater(final Flow<Data, Result, NotUsed> flow,
            final Function<ThingId, Source<AbstractWriteModel, NotUsed>> recoveryFunction,
            final SearchConfig config,
            final ActorRef pubSubMediator) {

        log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        thingId = tryToGetThingId();
        this.flow = flow;
        materializer = Materializer.createMaterializer(getContext());
        writeInterval = config.getUpdaterConfig().getStreamConfig().getWriteInterval();
        backOff = ExponentialBackOff.initial(
                config.getUpdaterConfig().getStreamConfig().getPersistenceConfig().getExponentialBackOffConfig());

        getContext().setReceiveTimeout(config.getUpdaterConfig().getMaxIdleTime());

        startWith(State.RECOVERING, getInitialData(thingId));
        when(State.RECOVERING, recovering());
        when(State.READY, ready());
        when(State.PERSISTING, persisting());
        when(State.RETRYING, retrying());
        onTransition(this::handleTransition);
        whenUnhandled(unhandled());
        initialize();

        killSwitch = recoverLastWriteModel(thingId, recoveryFunction);

        // subscribe for Shutdown commands
        ShutdownBehaviour.fromId(thingId, pubSubMediator, getSelf());
    }

    /**
     * Create props of this actor.
     *
     * @param flow Flow to perform persistence operations.
     * @param recoveryFunction The function to recover the previous write model on start up.
     * @param config Configuration of search service.
     * @param pubSubMediator The pubsub mediator.
     * @return The Props object.
     */
    public static Props props(final Flow<Data, Result, NotUsed> flow,
            final Function<ThingId, Source<AbstractWriteModel, NotUsed>> recoveryFunction,
            final SearchConfig config,
            final ActorRef pubSubMediator) {

        return Props.create(ThingUpdater.class, flow, recoveryFunction, config, pubSubMediator);
    }

    @Override
    public void postStop() {
        if (killSwitch != null) {
            killSwitch.shutdown();
        }
        switch (stateName()) {
            case PERSISTING, RETRYING -> log.warning("Shut down during <{}>", stateName());
        }
        try {
            super.postStop();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private FSMStateFunctionBuilder<State, Data> unhandled() {
        return matchAnyEvent((message, data) -> {
            log.warning("Unknown message in <{}>: <{}>", stateName(), message);
            return stay();
        });
    }

    private FSMStateFunctionBuilder<State, Data> recovering() {
        return matchEvent(AbstractWriteModel.class, this::recoveryComplete)
                .event(Throwable.class, this::recoveryFailed)
                .event(ReceiveTimeout.class, this::shutdown)
                .event(SHUTDOWN_CLASS, this::shutdownNow)
                .event(DistributedPubSubMediator.SubscribeAck.class, this::subscribeAck)
                .anyEvent(this::stashAndStay);
    }

    private FSMStateFunctionBuilder<State, Data> retrying() {
        return ready();
    }

    private FSMStateFunctionBuilder<State, Data> ready() {
        return matchEvent(ThingEvent.class, this::onThingEvent)
                .event(Metadata.class, this::onEventMetadata)
                .event(PolicyReferenceTag.class, this::onPolicyReferenceTag)
                .event(UpdateThing.class, this::updateThing)
                .eventEquals(Control.TICK, this::tick)
                .event(ReceiveTimeout.class, this::shutdown)
                .event(SHUTDOWN_CLASS, this::shutdownNow)
                .event(DistributedPubSubMediator.SubscribeAck.class, this::subscribeAck);
    }

    private FSMStateFunctionBuilder<State, Data> persisting() {
        return matchEvent(Result.class, this::onResult)
                .event(Done.class, this::onDone)
                .event(ThingEvent.class, this::onEventWhenPersisting)
                .event(ReceiveTimeout.class, this::shutdown)
                .event(SHUTDOWN_CLASS, this::shutdownNow)
                .event(DistributedPubSubMediator.SubscribeAck.class, this::subscribeAck)
                .anyEvent(this::stashAndStay);
    }

    private FSM.State<State, Data> subscribeAck(final DistributedPubSubMediator.SubscribeAck trigger, final Data data) {
        log.debug("Received <{}> in state <{}>", trigger, stateName());
        return stay();
    }

    private FSM.State<State, Data> shutdownNow(final org.eclipse.ditto.base.api.common.Shutdown shutdown,
            final Data data) {
        log.info("Shutting down now due to <{}> during <{}>", shutdown, stateName());
        data.metadata().sendWeakAck(getDescription(shutdown));
        return stop();
    }

    private FSM.State<State, Data> shutdown(final Object trigger, final Data data) {
        log.info("Shutting down due to <{}> during <{}>", trigger, stateName());
        shuttingDown = true;
        return switch (stateName()) {
            case RECOVERING -> stop();
            case READY, RETRYING -> {
                getSelf().tell(Control.TICK, ActorRef.noSender());
                yield stay();
            }
            default -> stay();
        };
    }

    private void handleTransition(final State previousState, final State nextState) {
        switch (nextState) {
            case READY, RETRYING -> {
                final Duration delay;
                if (nextState == State.READY) {
                    delay = writeInterval;
                } else {
                    backOff = backOff.calculateNextBackOff();
                    delay = backOff.getRestartDelay();
                }
                startSingleTimer(Control.TICK.name(), Control.TICK, delay);
                unstashAll();
            }
            default -> cancelTimer(Control.TICK.name());
        }
    }

    private FSM.State<State, Data> onEventWhenPersisting(final ThingEvent<?> event, final Data data) {
        computeEventMetadata(event, data).ifPresent(eventMetadata -> getSelf().tell(eventMetadata, getSelf()));
        return stay();
    }

    private FSM.State<State, Data> onResult(final Result result, final Data data) {
        killSwitch = null;
        final var writeResultAndErrors = result.resultAndErrors();
        final var pair = BulkWriteResultAckFlow.checkBulkWriteResult(writeResultAndErrors);
        pair.second().forEach(log::info);
        if (shuttingDown) {
            log.info("Shutting down after completing persistence operation");
            return stop();
        } else if (result.resultAndErrors().isNamespaceBlockedException()) {
            log.info("Disabling actor because namespace is blocked");
            getContext().setReceiveTimeout(BLOCK_NAMESPACE_SHUTDOWN_DELAY);
            return goTo(State.RECOVERING).using(getInitialData(thingId));
        }
        switch (pair.first()) {
            case INCORRECT_PATCH -> INCORRECT_PATCH_UPDATE_COUNT.increment();
            case UNACKNOWLEDGED, CONSISTENCY_ERROR, WRITE_ERROR -> UPDATE_FAILURE_COUNT.increment();
        }
        return switch (pair.first()) {
            case UNACKNOWLEDGED, CONSISTENCY_ERROR, INCORRECT_PATCH, WRITE_ERROR -> {
                final var metadata = data.metadata().export();
                yield goTo(State.RETRYING).using(new Data(metadata, ThingDeleteModel.of(Metadata.ofDeleted(thingId))));
            }
            case OK -> {
                final var writeModel = result.mongoWriteModel().getDitto();
                final var nextMetadata = writeModel.getMetadata().export();
                yield goTo(State.READY).using(new Data(nextMetadata, writeModel));
            }
        };
    }

    private FSM.State<State, Data> onDone(final Done done, final Data data) {
        killSwitch = null;
        log.debug("Update skipped");
        return goTo(State.READY).using(new Data(data.metadata().export(), data.lastWriteModel()));
    }

    private FSM.State<State, Data> tick(final Control tick, final Data data) {
        if (shouldPersist(data.metadata(), data.lastWriteModel().getMetadata())) {
            ConsistencyLag.startS2WaitForDemand(data.metadata());
            final var pair = Source.single(data)
                    .viaMat(KillSwitches.single(), Keep.right())
                    .via(flow)
                    .<Object>map(result -> result)
                    .orElse(Source.single(Done.done()))
                    .toMat(Sink.head(), Keep.both())
                    .run(materializer);

            killSwitch = pair.first();
            final var resultFuture = pair.second().handle((result, error) -> {
                if (error != null || result == null) {
                    final var errorToReport = error != null
                            ? error
                            : new IllegalStateException("Got no persistence result");
                    return Result.fromError(data.metadata(), errorToReport);
                } else {
                    return result;
                }
            });

            Patterns.pipe(resultFuture, getContext().getDispatcher()).to(getSelf());
            return goTo(State.PERSISTING);
        } else if (shuttingDown) {
            // shutting down during READY without pending updates
            return stop();
        } else {
            return stay();
        }
    }

    private boolean shouldPersist(final Metadata metadata, final Metadata lastMetadata) {
        return !metadata.equals(lastMetadata.export()) || lastMetadata.getThingRevision() <= 0;
    }

    private FSM.State<State, Data> updateThing(final UpdateThing updateThing, final Data data) {
        log.withCorrelationId(updateThing)
                .info("Requested to update search index <{}> by <{}>", updateThing, getSender());
        final AbstractWriteModel lastWriteModel;
        if (updateThing.getDittoHeaders().containsKey(FORCE_UPDATE)) {
            lastWriteModel = ThingDeleteModel.of(data.metadata());
        } else {
            lastWriteModel = data.lastWriteModel();
        }
        final Metadata metadata = data.metadata()
                .invalidateCaches(updateThing.shouldInvalidateThing(), updateThing.shouldInvalidatePolicy())
                .withUpdateReason(updateThing.getUpdateReason());
        final Metadata nextMetadata =
                updateThing.getDittoHeaders().getAcknowledgementRequests().contains(SEARCH_PERSISTED_REQUEST)
                        ? metadata.withSender(getSender())
                        : metadata;
        return stay().using(new Data(nextMetadata, lastWriteModel));
    }

    private FSM.State<State, Data> onPolicyReferenceTag(final PolicyReferenceTag policyReferenceTag, final Data data) {
        final var thingRevision = data.metadata().getThingRevision();
        final var policyId = data.metadata().getPolicyId().orElse(null);
        final var policyRevision = data.metadata().getPolicyRevision().orElse(-1L);
        if (log.isDebugEnabled()) {
            log.debug("Received new Policy-Reference-Tag for thing <{}> with revision <{}>,  policy-id <{}> and " +
                            "policy-revision <{}>: <{}>.",
                    thingId, thingRevision, policyId, policyRevision, policyReferenceTag.asIdentifierString());
        } else {
            log.info("Got policy update <{}> at revision <{}>. Previous known policy is <{}> at <{}>.",
                    policyReferenceTag.getPolicyTag().getEntityId(), policyReferenceTag.getPolicyTag().getRevision(),
                    policyId, policyRevision);
        }

        acknowledge(policyReferenceTag);

        final var policyTag = policyReferenceTag.getPolicyTag();
        final var policyIdOfTag = policyTag.getEntityId();
        if (!Objects.equals(policyId, policyIdOfTag) || policyRevision < policyTag.getRevision()) {
            final var newMetadata = Metadata.of(thingId, thingRevision, policyIdOfTag, policyTag.getRevision(), null)
                    .withUpdateReason(UpdateReason.POLICY_UPDATE)
                    .invalidateCaches(false, true);
            return enqueue(newMetadata, data);
        } else {
            log.debug("Dropping <{}> because my policyId=<{}> and policyRevision=<{}>",
                    policyReferenceTag, policyId, policyRevision);
            return stay();
        }
    }

    private FSM.State<State, Data> onEventMetadata(final Metadata eventMetadata, final Data data) {
        return enqueue(eventMetadata, data);
    }

    private FSM.State<State, Data> onThingEvent(final ThingEvent<?> thingEvent, final Data data) {
        return computeEventMetadata(thingEvent, data).map(eventMetadata -> onEventMetadata(eventMetadata, data))
                .orElseGet(this::stay);
    }

    private Optional<Metadata> computeEventMetadata(final ThingEvent<?> thingEvent, final Data data) {
        final DittoDiagnosticLoggingAdapter l = log.withCorrelationId(thingEvent);
        l.debug("Received new thing event for thing id <{}> with revision <{}>.", thingId, thingEvent.getRevision());
        final boolean shouldAcknowledge =
                thingEvent.getDittoHeaders().getAcknowledgementRequests().contains(SEARCH_PERSISTED_REQUEST);

        // check if the revision is valid (thingEvent.revision = 1 + sequenceNumber)
        if (thingEvent.getRevision() <= data.metadata().getThingRevision() && !shouldAcknowledge) {
            l.debug("Dropped thing event for thing id <{}> with revision <{}> because it was older than or "
                            + "equal to the current sequence number <{}> of the update actor.", thingId,
                    thingEvent.getRevision(), data.metadata().getThingRevision());
            return Optional.empty();
        }

        l.debug("Applying thing event <{}>.", thingEvent);
        if (thingEvent instanceof ThingDeleted) {
            // will stop this actor after 5 minutes (finishing up updating the index):
            // this time should be longer than the consistency lag, otherwise the actor will be stopped before the
            // actual "delete" is applied to the search index:
            getContext().setReceiveTimeout(THING_DELETION_TIMEOUT);
        }
        if (shouldAcknowledge) {
            tickNow();
        }

        final StartedTimer timer = DittoMetrics.timer(ConsistencyLag.TIMER_NAME)
                .tag(ConsistencyLag.TAG_SHOULD_ACK, Boolean.toString(shouldAcknowledge))
                .onExpiration(startedTimer ->
                        l.warning("Timer measuring consistency lag timed out for event <{}>", thingEvent))
                .start();
        DittoTracing.wrapTimer(DittoTracing.extractTraceContext(thingEvent), timer);
        ConsistencyLag.startS1InUpdater(timer);
        final var metadata = exportMetadataWithSender(shouldAcknowledge, thingEvent, getSender(), timer, data)
                .withUpdateReason(UpdateReason.THING_UPDATE);
        return Optional.of(metadata);
    }

    private FSM.State<State, Data> enqueue(final Metadata newMetadata, final Data data) {
        return stay().using(new Data(data.metadata().append(newMetadata), data.lastWriteModel()));
    }

    private FSM.State<State, Data> recoveryComplete(final AbstractWriteModel lastWriteModel,
            final Data initialData) {

        log.debug("Recovered: <{}>", lastWriteModel.getClass().getSimpleName());
        LOGGER.trace("Recovered: <{}>", lastWriteModel);
        killSwitch = null;
        return goTo(State.READY).using(new Data(lastWriteModel.getMetadata(), lastWriteModel));
    }

    private FSM.State<State, Data> recoveryFailed(final Throwable error, final Data initialData) {
        log.error(error, "Recovery failed");
        return stop();
    }

    private FSM.State<State, Data> stashAndStay(final Object message, final Data initialData) {
        stash();
        return stay();
    }

    private ThingId tryToGetThingId() {
        final Charset utf8 = StandardCharsets.UTF_8;
        try {
            final String actorName = self().path().name();
            return ThingId.of(URLDecoder.decode(actorName, utf8.name()));
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(MessageFormat.format("Charset <{0}> is unsupported!", utf8.name()), e);
        }
    }

    private UniqueKillSwitch recoverLastWriteModel(final ThingId thingId,
            final Function<ThingId, Source<AbstractWriteModel, NotUsed>> recoveryFunction) {

        final var pair = recoveryFunction.apply(thingId)
                .<Object>map(writeModel -> writeModel)
                .recover(new PFBuilder<Throwable, Object>().matchAny(x -> x).build())
                .viaMat(KillSwitches.single(), Keep.right())
                .toMat(Sink.head(), Keep.both())
                .run(materializer);
        Patterns.pipe(pair.second(), getContext().getDispatcher()).to(getSelf());
        return pair.first();
    }

    private void tickNow() {
        cancelTimer(Control.TICK.name());
        getSelf().tell(Control.TICK, ActorRef.noSender());
    }

    private Metadata exportMetadataWithSender(final boolean shouldAcknowledge,
            final ThingEvent<?> event,
            final ActorRef sender,
            final StartedTimer consistencyLagTimer,
            final Data data) {
        final long thingRevision = event.getRevision();
        if (shouldAcknowledge) {
            return Metadata.of(thingId, thingRevision, data.metadata().getPolicyId().orElse(null),
                    data.metadata().getPolicyRevision().orElse(null), List.of(event), consistencyLagTimer, sender);
        } else {
            return exportMetadata(event, thingRevision, consistencyLagTimer, data);
        }
    }

    private Metadata exportMetadata(@Nullable final ThingEvent<?> event, final long thingRevision,
            @Nullable final StartedTimer timer, final Data data) {

        return Metadata.of(thingId, thingRevision, data.metadata().getPolicyId().orElse(null),
                data.metadata().getPolicyRevision().orElse(null),
                event == null ? List.of() : List.of(event), timer, null);
    }

    private void acknowledge(final IdentifiableStreamingMessage message) {
        final ActorRef sender = getSender();
        if (!getContext().system().deadLetters().equals(sender)) {
            getSender().tell(StreamAck.success(message.asIdentifierString()), getSelf());
        }
    }

    private static Data getInitialData(final ThingId thingId) {
        final var deletedMetadata = Metadata.ofDeleted(thingId);
        return new Data(deletedMetadata, ThingDeleteModel.of(deletedMetadata));
    }

    private static JsonValue getDescription(final org.eclipse.ditto.base.api.common.Shutdown shutdown) {
        final var type = shutdown.getReason().getType();
        if (type instanceof ShutdownReasonType.Known knownType) {
            return JsonValue.of(switch (knownType) {
                case PURGE_NAMESPACE -> "The namespace is being purged.";
                case PURGE_ENTITIES -> "The entities are being purged.";
            });
        } else {
            return JsonValue.of(type.toString());
        }
    }
}
