/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.write;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.flatten.FlatRow;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.flatten.ThingFlattener;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteResultStatus;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterData;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterResult;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.reactivestreams.Publisher;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;

import reactor.core.publisher.Mono;

/**
 * Regression unit test (no DB, no Testcontainers) for the round-2 finding that {@code fullWrite}/{@code emptiedOut}
 * used to call {@link ThingFlattener#flatten(SearchIndexDocument)} EAGERLY while building the {@code Mono} --
 * BEFORE {@link PostgresSearchUpdaterFlow}'s {@code applyModel} attached its {@code onErrorResume} -- so a throwing
 * flatten escaped the per-element error boundary entirely and would have failed {@code flatMapConcat}'s mapping
 * function, aborting the WHOLE stream instead of just the one poisoned thing (violating the "one thing's failure
 * aborts nothing else" contract documented on the class).
 * <p>
 * Exercises the real {@link PostgresSearchUpdaterFlow#create()} Pekko flow end-to-end, using the package-private
 * {@link PostgresSearchUpdaterFlow#forConnectionFactory(ConnectionFactory, Function)} test-only seam to inject:
 * </p>
 * <ul>
 *     <li>a flattener that deliberately throws for one specific thing ID -- {@link SearchIndexDocument} is a
 *     {@code final} class with no throwing public construction path (and this module carries no Mockito dependency),
 *     so injecting the flattener function is the least invasive way to force the eager call site to throw;</li>
 *     <li>a {@link ConnectionFactory} whose {@code create()} always fails, so the test needs no real PostgreSQL:
 *     what matters here is that the flow SURVIVES and emits one {@link UpdaterResult} per input element, not
 *     whether any individual write actually reaches a database.</li>
 * </ul>
 */
public final class PostgresSearchUpdaterFlowTest {

    private static final ThingId POISON_THING_ID = ThingId.of("org.eclipse.ditto", "poison-thing");
    private static final ThingId HEALTHY_THING_ID = ThingId.of("org.eclipse.ditto", "healthy-thing");

    private static ActorSystem system;

    @BeforeClass
    public static void setUp() {
        system = ActorSystem.create(PostgresSearchUpdaterFlowTest.class.getSimpleName());
    }

    @AfterClass
    public static void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    @Test
    public void throwingFlattenIsContainedToItsOwnElementAndTheStreamSurvives() {
        final Function<SearchIndexDocument, List<FlatRow>> throwingFlattener = document -> {
            if (POISON_THING_ID.equals(document.thingId())) {
                throw new IllegalStateException("simulated flatten failure for " + POISON_THING_ID);
            }
            return ThingFlattener.flatten(document);
        };
        final PostgresSearchUpdaterFlow flow =
                PostgresSearchUpdaterFlow.forConnectionFactory(new AlwaysFailingConnectionFactory(),
                        throwingFlattener);

        final UpdaterData poisonData = updaterDataFor(POISON_THING_ID);
        final UpdaterData healthyData = updaterDataFor(HEALTHY_THING_ID);

        final List<UpdaterResult> results;
        try {
            results = Source.from(List.of(poisonData, healthyData))
                    .via(flow.create())
                    .runWith(Sink.seq(), system)
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError(
                    "the flow must never fail as a whole -- a per-element error must not abort the stream", e);
        }

        assertThat(results)
                .as("both elements must be processed -- the poisoned one must not abort the healthy one")
                .hasSize(2);

        final UpdaterResult poisonResult = results.get(0);
        assertThat(poisonResult.writeModel().getMetadata().getThingId().toString()).isEqualTo(POISON_THING_ID.toString());
        assertThat(poisonResult.result().classify())
                .as("a throwing flatten must be caught and classified as UNACKNOWLEDGED, not fail the stream")
                .isEqualTo(SearchWriteResultStatus.UNACKNOWLEDGED);

        // the healthy element's flatten succeeds; the injected connection factory then fails deterministically
        // since no real DB is available in this unit test -- also UNACKNOWLEDGED, but for a different reason
        // (connection acquisition, not flattening). What matters is that it was processed at all, i.e. the stream
        // reached and completed the second element instead of dying after the first one's eager throw.
        final UpdaterResult healthyResult = results.get(1);
        assertThat(healthyResult.writeModel().getMetadata().getThingId().toString()).isEqualTo(HEALTHY_THING_ID.toString());
        assertThat(healthyResult.result().classify()).isEqualTo(SearchWriteResultStatus.UNACKNOWLEDGED);
    }

    private static UpdaterData updaterDataFor(final ThingId thingId) {
        final Metadata metadata = metadata(thingId, 1L);
        final JsonObject thing = JsonObject.newBuilder()
                .set("thingId", thingId.toString())
                .set("_namespace", thingId.getNamespace())
                .set("_revision", 1)
                .set("attributes", JsonObject.newBuilder().set("a", 1).build())
                .build();
        final SearchIndexDocument document = SearchIndexDocument.newBuilder(thingId)
                .namespace(thingId.getNamespace())
                .revision(1L)
                .policyId(PolicyId.of("org.eclipse.ditto", "the-policy"))
                .policyRevision(1L)
                .referencedPolicies(Set.of(PolicyTag.of(PolicyId.of("org.eclipse.ditto", "the-policy"), 1L)))
                .thing(thing)
                .build();
        final ThingWriteModel model = ThingWriteModel.of(metadata, document);
        return new UpdaterData(model, model);
    }

    private static Metadata metadata(final ThingId thingId, final long revision) {
        final PolicyTag policyTag = PolicyTag.of(PolicyId.of("org.eclipse.ditto", "the-policy"), revision);
        return Metadata.of(thingId, revision, policyTag, null, Set.of(policyTag), null);
    }

    /** A {@link ConnectionFactory} whose {@code create()} always errors -- no real PostgreSQL needed. */
    private static final class AlwaysFailingConnectionFactory implements ConnectionFactory {

        @Override
        public Publisher<? extends Connection> create() {
            return Mono.error(new IllegalStateException("no real database in this unit test"));
        }

        @Override
        public ConnectionFactoryMetadata getMetadata() {
            return () -> "always-failing-test-fake";
        }
    }

}
