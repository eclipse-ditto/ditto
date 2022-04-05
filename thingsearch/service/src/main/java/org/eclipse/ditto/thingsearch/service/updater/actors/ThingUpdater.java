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
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.internal.models.streaming.IdentifiableStreamingMessage;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
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

import akka.NotUsed;
import akka.actor.AbstractFSMWithStash;
import akka.actor.ActorRef;
import akka.actor.FSM;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.pattern.Patterns;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * This Actor initiates persistence updates related to 1 thing.
 */
public final class ThingUpdater extends AbstractFSMWithStash<ThingUpdater.State, ThingUpdater.Data> {

    // TODO: use circuit breaker
    private static final Duration RETRY_DELAY = Duration.ofSeconds(10L);

    // logger for "trace" statements
    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(ThingUpdaterOld.class);

    private static final AcknowledgementRequest SEARCH_PERSISTED_REQUEST =
            AcknowledgementRequest.of(DittoAcknowledgementLabel.SEARCH_PERSISTED);

    private static final Duration THING_DELETION_TIMEOUT = Duration.ofMinutes(5);
    private static final String FORCE_UPDATE = "force-update";

    private final DittoDiagnosticLoggingAdapter log;
    private final ThingId thingId;
    private final Flow<Data, Result, NotUsed> flow;
    private final Materializer materializer;
    private final Duration writeInterval;
    private boolean shuttingDown = false;

    // TODO
    public record Data(Metadata metadata, AbstractWriteModel lastWriteModel) {}

    // TODO
    public record Result(MongoWriteModel mongoWriteModel, WriteResultAndErrors resultAndErrors) {}

    enum State {
        RECOVERING,
        READY,
        PERSISTING,
        RETRYING
    }

    enum Control {
        TICK
    }

    // TODO: add shutdown behavior
    private ThingUpdater(final Flow<Data, Result, NotUsed> flow,
            final Function<ThingId, Source<AbstractWriteModel, NotUsed>> recoveryFunction,
            final SearchConfig config) {

        log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        thingId = tryToGetThingId();
        this.flow = flow;
        materializer = Materializer.createMaterializer(getContext());
        writeInterval = config.getUpdaterConfig().getStreamConfig().getWriteInterval();

        getContext().setReceiveTimeout(config.getUpdaterConfig().getMaxIdleTime());

        final var deletedMetadata = Metadata.ofDeleted(thingId);
        startWith(State.RECOVERING, new Data(deletedMetadata, ThingDeleteModel.of(deletedMetadata)));
        when(State.RECOVERING, recovering());
        when(State.READY, ready());
        when(State.PERSISTING, persisting());
        when(State.RETRYING, retrying());
        onTransition(this::handleTransition);
        initialize();

        recoverLastWriteModel(thingId, recoveryFunction);
    }

    public static Props props(final Flow<Data, Result, NotUsed> flow,
            final Function<ThingId, Source<AbstractWriteModel, NotUsed>> recoveryFunction,
            final SearchConfig config) {

        return Props.create(ThingUpdater.class, flow, recoveryFunction, config);
    }

    @Override
    public void postStop() {
        switch (stateName()) {
            case PERSISTING, RETRYING -> log().warning("Shut down during <{}>", stateName());
        }
        try {
            super.postStop();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private FSMStateFunctionBuilder<State, Data> recovering() {
        return matchEvent(AbstractWriteModel.class, this::recoveryComplete)
                .event(ReceiveTimeout.class, this::shutdown)
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
                .event(ReceiveTimeout.class, this::shutdown);
    }

    private FSMStateFunctionBuilder<State, Data> persisting() {
        return matchEvent(Result.class, this::onResult)
                .event(ThingEvent.class, this::onEventWhenPersisting)
                .event(ReceiveTimeout.class, this::shutdown)
                .anyEvent(this::stashAndStay);
    }

    private FSM.State<State, Data> shutdown(final Object trigger, final Data data) {
        log().info("Shutting down due to <{}> during <{}>", trigger, stateName());
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
                final var delay = nextState == State.READY ? writeInterval : RETRY_DELAY;
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
        final var writeResultAndErrors = result.resultAndErrors();
        final var pair = BulkWriteResultAckFlow.checkBulkWriteResult(writeResultAndErrors, null);
        pair.second().forEach(log::info);
        if (shuttingDown) {
            log().debug("Shutting down after completing persistence operation");
            return stop();
        }
        return switch (pair.first()) {
            case UNACKNOWLEDGED, CONSISTENCY_ERROR, INCORRECT_PATCH -> {
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

    private FSM.State<State, Data> tick(final Control tick, final Data data) {
        if (shouldPersist(data.metadata(), data.lastWriteModel().getMetadata())) {
            final var future = Source.single(data)
                    .via(flow)
                    .toMat(Sink.head(), Keep.right())
                    .run(materializer);

            final var resultFuture = future.handle((result, error) -> {
                if (error != null || result == null) {
                    final var mockWriteModel = MongoWriteModel.of(
                            ThingDeleteModel.of(data.metadata()),
                            new DeleteOneModel<>(new BsonDocument()),
                            false);
                    final var errorToReport =
                            error != null ? error : new IllegalStateException("Persistence produced no result");
                    return new Result(mockWriteModel, WriteResultAndErrors.failure(errorToReport));
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
        }

        acknowledge(policyReferenceTag);

        final var policyTag = policyReferenceTag.getPolicyTag();
        final var policyIdOfTag = policyTag.getEntityId();
        if (!Objects.equals(policyId, policyIdOfTag) || policyRevision < policyTag.getRevision()) {
            final var newMetadata = Metadata.of(thingId, thingRevision, policyIdOfTag, policyTag.getRevision(), null)
                    .withUpdateReason(UpdateReason.POLICY_UPDATE);
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
        ConsistencyLag.startS0InUpdater(timer); // TODO: rename segments--delay during persistence measurable?
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
        return goTo(State.READY).using(new Data(lastWriteModel.getMetadata(), lastWriteModel));
    }

    private FSM.State<State, Data> stashAndStay(final Object message, final Data initialData) {
        stash();
        return stay();
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

    private void recoverLastWriteModel(final ThingId thingId,
            final Function<ThingId, Source<AbstractWriteModel, NotUsed>> recoveryFunction) {

        final var writeModelFuture = recoveryFunction.apply(thingId).runWith(Sink.head(), materializer);
        Patterns.pipe(writeModelFuture, getContext().getDispatcher()).to(getSelf());
    }

    private void tickNow() {
        startTimerWithFixedDelay(Control.TICK.name(), Control.TICK, writeInterval);
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
}
