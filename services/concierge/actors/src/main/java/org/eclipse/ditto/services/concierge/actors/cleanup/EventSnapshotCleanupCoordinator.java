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
package org.eclipse.ditto.services.concierge.actors.cleanup;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.concierge.actors.ShardRegions;
import org.eclipse.ditto.services.concierge.actors.cleanup.credits.CreditDecisionSource;
import org.eclipse.ditto.services.concierge.actors.cleanup.messages.CreditDecision;
import org.eclipse.ditto.services.concierge.actors.cleanup.persistenceids.PersistenceIdSource;
import org.eclipse.ditto.services.concierge.common.PersistenceCleanupConfig;
import org.eclipse.ditto.services.models.connectivity.ConnectionTag;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.models.streaming.EntityIdWithRevision;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.actors.ModifyConfigBehavior;
import org.eclipse.ditto.services.utils.akka.actors.RetrieveConfigBehavior;
import org.eclipse.ditto.services.utils.akka.controlflow.Transistor;
import org.eclipse.ditto.services.utils.config.DittoConfigError;
import org.eclipse.ditto.services.utils.health.RetrieveHealth;
import org.eclipse.ditto.services.utils.health.RetrieveHealthResponse;
import org.eclipse.ditto.services.utils.health.StatusDetailMessage;
import org.eclipse.ditto.services.utils.health.StatusInfo;
import org.eclipse.ditto.signals.commands.cleanup.Cleanup;
import org.eclipse.ditto.signals.commands.cleanup.CleanupResponse;
import org.eclipse.ditto.signals.commands.common.Shutdown;
import org.eclipse.ditto.signals.commands.common.ShutdownResponse;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;

import akka.Done;
import akka.NotUsed;
import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.FanInShape2;
import akka.stream.Graph;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.SourceShape;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import scala.PartialFunction;

/**
 * Cluster singleton actor to assembles the stream to cleanup old snapshots and events and report on its status.
 *
 * <pre>{@code
 *
 *    Report failures with timestamp
 *                 ^
 *                 |
 *                 |
 *                 |
 *             +---+---------------+
 *             |All persistence IDs+-------------------------------+
 *             +-------------------+                               |
 *                                                                 v            +------------------+
 *                                                              +--+--+         |Forward to        |
 *                                                              |Merge+-------->+PersistenceActor  |
 *                                                              +--+--+         |with back pressure|
 *                                                                 ^            +--------+---------+
 *                                                                 |                     |
 *                                                                 |                     |
 *             +-------+         +---------------+                 |                     |
 *             |Metrics+-------->+Credit decision+-----------------+                     |
 *             +-------+         +------+--------+                                       |
 *                                      |                                                |
 *                                      |                                                |
 *                                      v                                                v
 *                      Report metrics and credit decision                     Report round trip time
 *
 *
 * }</pre>
 */
public final class EventSnapshotCleanupCoordinator extends AbstractActorWithTimers
        implements RetrieveConfigBehavior, ModifyConfigBehavior {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "eventSnapshotCleanupCoordinator";

    /**
     * Shutdown-reason type to restart stream after non-default time.
     */
    private static final String RESTART_AFTER = "restartAfter";

    private static final JsonFieldDefinition<Boolean> ENABLED =
            JsonFactory.newBooleanFieldDefinition("enabled");

    private static final JsonFieldDefinition<JsonArray> CREDIT_DECISIONS =
            JsonFactory.newJsonArrayFieldDefinition("credit-decisions");

    private static final JsonFieldDefinition<JsonArray> ACTIONS =
            JsonFactory.newJsonArrayFieldDefinition("actions");

    private static final JsonFieldDefinition<JsonArray> EVENTS =
            JsonFactory.newJsonArrayFieldDefinition("events");

    private static final String START = "start";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    // config may change.
    private PersistenceCleanupConfig config;

    private final ActorRef pubSubMediator;
    private final ShardRegions shardRegions;
    private final ActorMaterializer materializer;

    // logs for status reporting
    private final Deque<Pair<Instant, CreditDecision>> creditDecisions;
    private final Deque<Pair<Instant, CleanupResponse>> actions;
    private final Deque<Pair<Instant, Event>> events;

    @Nullable
    private KillSwitch killSwitch;

    private EventSnapshotCleanupCoordinator(final PersistenceCleanupConfig config, final ActorRef pubSubMediator,
            final ShardRegions shardRegions) {

        this.config = config;
        this.pubSubMediator = pubSubMediator;
        this.shardRegions = shardRegions;

        materializer = ActorMaterializer.create(getContext());

        if (config.isEnabled()) {
            scheduleWakeUp();
        }

        creditDecisions = new ArrayDeque<>(config.getKeptCreditDecisions() + 1);
        actions = new ArrayDeque<>(config.getKeptActions() + 1);
        events = new ArrayDeque<>(config.getKeptEvents() + 1);
    }

    /**
     * Create Akka Props object for this actor.
     *
     * @param config configuration for persistence cleanup.
     * @param pubSubMediator the pub-sub-mediator.
     * @param shardRegions shard regions of persistence actors.
     * @return Props to create this actor with.
     */
    public static Props props(final PersistenceCleanupConfig config, final ActorRef pubSubMediator,
            final ShardRegions shardRegions) {

        return Props.create(EventSnapshotCleanupCoordinator.class, config, pubSubMediator, shardRegions);
    }

    @Override
    public Receive createReceive() {
        return sleeping();
    }

    @Override
    public Config getConfig() {
        return config.getConfig();
    }

    @Override
    public Config setConfig(final Config config) {
        final PersistenceCleanupConfig previousConfig = this.config;
        // TODO: replace ConfigWithFallback - it breaks AbstractConfigValue.withFallback!
        // Workaround: re-parse my config
        final Config fallback = ConfigFactory.parseString(getConfig().root().render(ConfigRenderOptions.concise()));
        try {
            this.config = PersistenceCleanupConfig.fromConfig(config.withFallback(fallback));
        } catch (final DittoConfigError | ConfigException e) {
            log.error(e, "Failed to set config");
        }
        if (!previousConfig.isEnabled() && this.config.isEnabled()) {
            scheduleWakeUp();
        }
        return this.config.getConfig();
    }

    private Receive sleeping() {
        return ReceiveBuilder.create()
                .match(WokeUp.class, this::wokeUp)
                .match(RetrieveHealth.class, this::retrieveHealth)
                .match(Shutdown.class, this::shutdownStream)
                .build()
                .orElse(retrieveConfigBehavior())
                .orElse(modifyConfigBehavior());
    }

    private Receive streaming() {
        return ReceiveBuilder.create()
                .match(CreditDecision.class, creditDecision ->
                        enqueue(creditDecisions, creditDecision, config.getKeptCreditDecisions()))
                .match(CleanupResponse.class, cleanupResponse ->
                        enqueue(actions, cleanupResponse, config.getKeptActions()))
                .match(StreamTerminated.class, this::streamTerminated)
                .match(RetrieveHealth.class, this::retrieveHealth)
                .match(Shutdown.class, this::shutdownStream)
                .build()
                .orElse(retrieveConfigBehavior())
                .orElse(modifyConfigBehavior());
    }

    private void shutdownStream(final Shutdown shutdown) {
        log.info("Terminating stream on demand: <{}>", shutdown);
        shutdownKillSwitch();

        final Event streamTerminated = new StreamTerminated("Got " + shutdown);
        enqueue(events, streamTerminated, config.getKeptEvents());
        getContext().become(sleeping());

        if (config.isEnabled()) {
            final Duration wakeUpDelay = config.getQuietPeriod();
            final String message = String.format("Restarting in <%s>.", wakeUpDelay);
            scheduleWakeUp(wakeUpDelay);
            getSender().tell(ShutdownResponse.of(message, shutdown.getDittoHeaders()), getSelf());
        } else {
            final String message = "Not restarting stream because background cleanup is disabled.";
            getSender().tell(ShutdownResponse.of(message, shutdown.getDittoHeaders()), getSelf());
        }
    }

    private void wokeUp(final WokeUp wokeUp) {
        log.info("Woke up.");
        enqueue(events, wokeUp.enable(config.isEnabled()), config.getKeptEvents());
        if (config.isEnabled()) {
            restartStream();
            getContext().become(streaming());
        } else {
            log.warning("Not waking up because disabled.");
        }
    }

    private void streamTerminated(final Event streamTerminated) {
        enqueue(events, streamTerminated, config.getKeptEvents());
        if (config.isEnabled()) {
            log.info("Stream terminated. Will restart after quiet period.");
            scheduleWakeUp();
        } else {
            log.warning("Stream terminated while disabled.");
        }
        getContext().become(sleeping());
    }

    private <T> Flow<T, T, NotUsed> reportToSelf() {
        return Flow.fromFunction(x -> {
            getSelf().tell(x, getSelf());
            return x;
        });
    }

    private void shutdownKillSwitch() {
        if (killSwitch != null) {
            killSwitch.shutdown();
            killSwitch = null;
        }
    }

    private void restartStream() {
        shutdownKillSwitch();

        Pair<UniqueKillSwitch, CompletionStage<Done>> materializedValues =
                assembleSource().viaMat(KillSwitches.single(), Keep.right())
                        .toMat(forwarderSink(), Keep.both())
                        .run(materializer);

        killSwitch = materializedValues.first();

        materializedValues.second()
                .<Void>handle((result, error) -> {
                    final String description = String.format("Stream terminated. Result=<%s> Error=<%s>",
                            Objects.toString(result), Objects.toString(error));
                    log.info(description);
                    getSelf().tell(new StreamTerminated(description), getSelf());
                    return null;
                });
    }

    private void scheduleWakeUp() {
        scheduleWakeUp(config.getQuietPeriod());
    }

    private void scheduleWakeUp(final Duration when) {
        getTimers().startSingleTimer(WokeUp.class, WokeUp.ENABLED, when);
    }

    private Source<EntityIdWithRevision, NotUsed> assembleSource() {
        final Graph<SourceShape<EntityIdWithRevision>, NotUsed> graph = GraphDSL.create(builder -> {
            final SourceShape<EntityIdWithRevision> persistenceIds = builder.add(persistenceIdSource());
            final SourceShape<Integer> credit = builder.add(creditSource());
            final FanInShape2<EntityIdWithRevision, Integer, EntityIdWithRevision> transistor =
                    builder.add(Transistor.of());

            builder.from(persistenceIds.out()).toInlet(transistor.in0());
            builder.from(credit.out()).toInlet(transistor.in1());

            return SourceShape.of(transistor.out());
        });

        return Source.fromGraph(graph).log("pid-source", log);
    }

    private Source<Integer, NotUsed> creditSource() {
        final Graph<SourceShape<CreditDecision>, NotUsed> creditDecisionSource =
                CreditDecisionSource.create(config.getCreditDecisionConfig(), getContext(), pubSubMediator, log);

        return Source.fromGraph(creditDecisionSource).via(reportToSelf()).map(CreditDecision::getCredit);
    }

    private Graph<SourceShape<EntityIdWithRevision>, NotUsed> persistenceIdSource() {
        return PersistenceIdSource.create(config.getPersistenceIdsConfig(), pubSubMediator);
    }

    // include self-reporting for acknowledged
    private Sink<EntityIdWithRevision, CompletionStage<Done>> forwarderSink() {

        final PartialFunction<EntityIdWithRevision, CompletionStage<CleanupResponse>> askShardRegionByTagType =
                new PFBuilder<EntityIdWithRevision, CompletionStage<CleanupResponse>>()
                        .match(ThingTag.class, thingTag ->
                                askShardRegion(shardRegions.things(), ThingCommand.RESOURCE_TYPE, thingTag))
                        .match(PolicyTag.class, policyTag ->
                                askShardRegion(shardRegions.policies(), PolicyCommand.RESOURCE_TYPE, policyTag))
                        .match(ConnectionTag.class, connTag ->
                                askShardRegion(shardRegions.connections(), ConnectivityCommand.RESOURCE_TYPE, connTag))
                        .matchAny(e -> {
                            final CleanupResponse error =
                                    CleanupResponse.failure("Unexpected entity ID type: ", DittoHeaders.empty());
                            return CompletableFuture.completedFuture(error);
                        })
                        .build();

        return Flow.<EntityIdWithRevision>create()
                .mapAsync(config.getParallelism(), askShardRegionByTagType::apply)
                .via(reportToSelf())
                .log(EventSnapshotCleanupCoordinator.class.getSimpleName(), log)
                .toMat(Sink.ignore(), Keep.right());
    }

    private CompletionStage<CleanupResponse> askShardRegion(final ActorRef shardRegion, final String resourceType,
            final EntityIdWithRevision tag) {
        final String id = tag.getId();
        final Cleanup command = getCommand(id);
        return Patterns.ask(shardRegion, command, config.getCleanupTimeout())
                .handle((result, error) -> {
                    if (result instanceof CleanupResponse) {
                        final CleanupResponse response = ((CleanupResponse) result);
                        final DittoHeaders headers =
                                command.getDittoHeaders().toBuilder().putHeaders(response.getDittoHeaders()).build();
                        return response.setDittoHeaders(headers);
                    } else {
                        final String msg = String.format("Unexpected response from shard <%s>: result=<%s> error=<%s>",
                                resourceType, Objects.toString(result), Objects.toString(error));
                        return CleanupResponse.failure(msg, command.getDittoHeaders());
                    }
                });
    }

    private void retrieveHealth(final RetrieveHealth trigger) {
        getSender().tell(RetrieveHealthResponse.of(renderStatusInfo(), trigger.getDittoHeaders()), getSelf());
    }

    private StatusInfo renderStatusInfo() {
        return StatusInfo.fromStatus(StatusInfo.Status.UP,
                Collections.singletonList(StatusDetailMessage.of(StatusDetailMessage.Level.INFO, render())));
    }

    private JsonObject render() {
        return JsonObject.newBuilder()
                .set(ENABLED, config.isEnabled())
                .set(EVENTS, events.stream()
                        .map(EventSnapshotCleanupCoordinator::renderEvent)
                        .collect(JsonCollectors.valuesToArray()))
                .set(CREDIT_DECISIONS, creditDecisions.stream()
                        .map(EventSnapshotCleanupCoordinator::renderCreditDecision)
                        .collect(JsonCollectors.valuesToArray()))
                .set(ACTIONS, actions.stream()
                        .map(EventSnapshotCleanupCoordinator::renderAction)
                        .collect(JsonCollectors.valuesToArray()))
                .build();
    }

    private static JsonObject renderEvent(final Pair<Instant, Event> element) {
        return JsonObject.newBuilder()
                .set(element.first().toString(), element.second().name())
                .build();
    }

    private static JsonObject renderCreditDecision(final Pair<Instant, CreditDecision> element) {
        return JsonObject.newBuilder()
                .set(element.first().toString(), element.second().toString())
                .build();
    }

    private static JsonObject renderAction(final Pair<Instant, CleanupResponse> element) {
        final CleanupResponse response = element.second();
        final DittoHeaders headers = response.getDittoHeaders();
        final int status = response.getStatusCodeValue();
        final String start = headers.getOrDefault(START, "unknown");
        final String tagLine = String.format("%d start=%s <%s>", status, start, response.getId());
        return JsonObject.newBuilder()
                .set(element.first().toString(), tagLine)
                .build();
    }

    private static Cleanup getCommand(final String id) {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(START, Instant.now().toString())
                .build();
        return Cleanup.of(id, headers);
    }

    private static <T> void enqueue(final Deque<Pair<Instant, T>> queue, final T element, final int maxQueueSize) {
        queue.addFirst(Pair.create(Instant.now(), element));
        if (queue.size() > maxQueueSize) {
            queue.removeLast();
        }
    }

    private interface Event {

        String name();
    }

    private static final class WokeUp implements Event {

        private static final WokeUp ENABLED = new WokeUp(true);

        private final boolean enabled;

        private WokeUp(final boolean enabled) {
            this.enabled = enabled;
        }

        private WokeUp enable(final boolean isEnabled) {
            return new WokeUp(isEnabled);
        }

        @Override
        public String name() {
            return enabled ? "WOKE_UP" : "Not waking up: background cleanup is disabled.";
        }
    }

    private final class StreamTerminated implements Event {

        private final String whatHappened;

        private StreamTerminated(final String whatHappened) {
            this.whatHappened = whatHappened;
        }

        @Override
        public String name() {
            return whatHappened;
        }
    }
}
