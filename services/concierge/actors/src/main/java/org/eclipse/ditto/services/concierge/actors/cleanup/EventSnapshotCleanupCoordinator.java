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

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
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
import org.eclipse.ditto.services.utils.akka.controlflow.Transistor;
import org.eclipse.ditto.services.utils.health.AbstractBackgroundStreamingActorWithConfigWithStatusReport;
import org.eclipse.ditto.signals.commands.cleanup.CleanupPersistence;
import org.eclipse.ditto.signals.commands.cleanup.CleanupPersistenceResponse;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.FanInShape2;
import akka.stream.Graph;
import akka.stream.SourceShape;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
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
public final class EventSnapshotCleanupCoordinator
        extends AbstractBackgroundStreamingActorWithConfigWithStatusReport<PersistenceCleanupConfig> {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "eventSnapshotCleanupCoordinator";

    private static final String ERROR_MESSAGE_HEADER = "error";

    private static final JsonFieldDefinition<JsonArray> JSON_CREDIT_DECISIONS =
            JsonFactory.newJsonArrayFieldDefinition("credit-decisions");

    private static final JsonFieldDefinition<JsonArray> JSON_ACTIONS =
            JsonFactory.newJsonArrayFieldDefinition("actions");

    private static final String START = "start";

    private final ActorRef pubSubMediator;
    private final ShardRegions shardRegions;

    // logs for status reporting
    private final Deque<Pair<Instant, CreditDecision>> creditDecisions;
    private final Deque<Pair<Instant, CleanupPersistenceResponse>> actions;

    @SuppressWarnings("unused")
    private EventSnapshotCleanupCoordinator(final PersistenceCleanupConfig config, final ActorRef pubSubMediator,
            final ShardRegions shardRegions) {

        super(config);
        this.pubSubMediator = pubSubMediator;
        this.shardRegions = shardRegions;
        creditDecisions = new ArrayDeque<>(config.getKeptCreditDecisions() + 1);
        actions = new ArrayDeque<>(config.getKeptActions() + 1);
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
    protected void preEnhanceStreamingBehavior(final ReceiveBuilder streamingReceiveBuilder) {
        streamingReceiveBuilder.match(CreditDecision.class,
                creditDecision ->
                        enqueue(creditDecisions, creditDecision, config.getKeptCreditDecisions()))
                .match(CleanupPersistenceResponse.class, cleanupResponse ->
                        enqueue(actions, cleanupResponse, config.getKeptActions()));
    }

    private <T> Flow<T, T, NotUsed> reportToSelf() {
        return Flow.fromFunction(x -> {
            getSelf().tell(x, getSelf());
            return x;
        });
    }

    @Override
    protected PersistenceCleanupConfig parseConfig(final Config config) {
        return PersistenceCleanupConfig.fromConfig(config);
    }

    private Source<EntityIdWithRevision, NotUsed> getEntityIdWithRevisionSource() {
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

    @Override
    protected Source<CleanupPersistenceResponse, NotUsed> getSource() {

        final PartialFunction<EntityIdWithRevision, CompletionStage<CleanupPersistenceResponse>>
                askShardRegionForCleanupByTagType =
                new PFBuilder<EntityIdWithRevision, CompletionStage<CleanupPersistenceResponse>>()
                        .match(ThingTag.class, thingTag ->
                                askShardRegionForCleanup(shardRegions.things(), ThingCommand.RESOURCE_TYPE, thingTag))
                        .match(PolicyTag.class, policyTag ->
                                askShardRegionForCleanup(shardRegions.policies(), PolicyCommand.RESOURCE_TYPE,
                                        policyTag))
                        .match(ConnectionTag.class, connTag ->
                                askShardRegionForCleanup(shardRegions.connections(), ConnectivityCommand.RESOURCE_TYPE,
                                        connTag))
                        .matchAny(e -> {
                            final String errorMessage = String.format("Unexpected entity ID type: " + e);
                            log.error(errorMessage);
                            final CleanupPersistenceResponse failureResponse =
                                    CleanupPersistenceResponse.failure(DefaultEntityId.dummy(),
                                            DittoHeaders.newBuilder().putHeader(ERROR_MESSAGE_HEADER, errorMessage)
                                                    .build());
                            return CompletableFuture.completedFuture(failureResponse);
                        })
                        .build();

        return getEntityIdWithRevisionSource()
                .mapAsync(config.getParallelism(), askShardRegionForCleanupByTagType::apply)
                .via(reportToSelf()) // include self-reporting for acknowledged
                .log(EventSnapshotCleanupCoordinator.class.getSimpleName(), log);
    }

    private CompletionStage<CleanupPersistenceResponse> askShardRegionForCleanup(final ActorRef shardRegion,
            final String resourceType, final EntityIdWithRevision tag) {

        final EntityId id = tag.getEntityId();
        final CleanupPersistence cleanupPersistence = getCleanupCommand(id);
        return Patterns.ask(shardRegion, cleanupPersistence, config.getCleanupTimeout())
                .handle((result, error) -> {
                    if (result instanceof CleanupPersistenceResponse) {
                        final CleanupPersistenceResponse response = ((CleanupPersistenceResponse) result);
                        final DittoHeaders headers =
                                cleanupPersistence.getDittoHeaders()
                                        .toBuilder()
                                        .putHeaders(response.getDittoHeaders())
                                        .build();
                        return response.setDittoHeaders(headers);
                    } else {
                        final String errorMessage =
                                String.format("Unexpected response from shard <%s>: result=<%s> error=<%s>",
                                        resourceType, result, error);
                        return CleanupPersistenceResponse.failure(id,
                                cleanupPersistence.getDittoHeaders().toBuilder()
                                        .putHeader(ERROR_MESSAGE_HEADER, errorMessage)
                                        .build());
                    }
                });
    }

    @Override
    protected void postEnhanceStatusReport(final JsonObjectBuilder statusReportBuilder) {
        statusReportBuilder.set(JSON_CREDIT_DECISIONS, creditDecisions.stream()
                .map(EventSnapshotCleanupCoordinator::renderCreditDecision)
                .collect(JsonCollectors.valuesToArray()))
                .set(JSON_ACTIONS, actions.stream()
                        .map(EventSnapshotCleanupCoordinator::renderAction)
                        .collect(JsonCollectors.valuesToArray()))
                .build();
    }

    private static JsonObject renderCreditDecision(final Pair<Instant, CreditDecision> element) {
        return JsonObject.newBuilder()
                .set(element.first().toString(), element.second().toString())
                .build();
    }

    private static JsonObject renderAction(final Pair<Instant, CleanupPersistenceResponse> element) {
        final CleanupPersistenceResponse response = element.second();
        final DittoHeaders headers = response.getDittoHeaders();
        final int status = response.getStatusCodeValue();
        final String start = headers.getOrDefault(START, "unknown");
        final String message = getResponseMessage(response);
        final String tagLine = String.format("%d start=%s <%s>", status, start, message);
        return JsonObject.newBuilder()
                .set(element.first().toString(), tagLine)
                .build();
    }

    private static String getResponseMessage(final CleanupPersistenceResponse response) {
        final StringBuilder messageBuilder = new StringBuilder();
        if (!response.getEntityId().isDummy()) {
            messageBuilder.append(response.getEntityId().toString());
            if (response.getDittoHeaders().containsKey(ERROR_MESSAGE_HEADER)) {
                messageBuilder.append(": ").append(response.getDittoHeaders().get(ERROR_MESSAGE_HEADER));
            }
        } else {
            messageBuilder.append(response.getDittoHeaders().get(ERROR_MESSAGE_HEADER));
        }
        return messageBuilder.toString();
    }

    private static CleanupPersistence getCleanupCommand(final EntityId id) {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .putHeader(START, Instant.now().toString())
                .build();
        return CleanupPersistence.of(id, headers);
    }
}
