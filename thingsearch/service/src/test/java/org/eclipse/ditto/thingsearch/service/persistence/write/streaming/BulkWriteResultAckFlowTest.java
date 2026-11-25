/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteError;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteResult;
import org.junit.After;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoSocketReadException;
import com.mongodb.ServerAddress;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Tests {@link BulkWriteResultAckFlow}.
 */
public final class BulkWriteResultAckFlowTest {

    private final ActorSystem actorSystem = ActorSystem.create();

    @After
    public void stopActorSystem() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void allSuccess() {
        final List<AbstractWriteModel> writeModels = generate5WriteModels();
        // matched=3, upserts=2 (>= nonDelete count of 2), no errors => OK
        final SearchWriteResult result = SearchWriteResult.acknowledged(5, 2, 3, 1, 2, List.of());

        // WHEN
        final var report = runBulkWriteResultAckFlow(writeModels, result);

        // THEN
        for (final var message : getMessages(report)) {
            actorSystem.log().info(message);
            assertThat(message).contains("Acknowledged: Success");
        }
    }

    @Test
    public void partialSuccess() {
        final List<AbstractWriteModel> writeModels = generate5WriteModels();
        final SearchWriteResult result = SearchWriteResult.acknowledged(5, 2, 2, 2, 0, List.of(
                new SearchWriteError(3, SearchWriteError.Category.DUPLICATE_KEY, "E11000 duplicate key error"),
                new SearchWriteError(4, SearchWriteError.Category.OTHER, "E50 operation timed out")
        ));

        // WHEN: BulkWriteResultAckFlow receives partial update success with errors, one of which is not duplicate key
        final var report = runBulkWriteResultAckFlow(writeModels, result);
        final var message = report.get(0).second().get(0);

        // THEN: the non-duplicate-key error triggers a failure acknowledgement
        actorSystem.log().info(message);
        assertThat(message).contains("Acknowledged: PartialSuccess");
        assertThat(report.get(0).first()).isEqualTo(BulkWriteResultAckFlow.Status.WRITE_ERROR);
    }

    @Test
    public void unexpectedMongoSocketReadException() {
        final List<AbstractWriteModel> writeModels = generate5WriteModels();

        // WHEN: BulkWriteResultAckFlow receives unexpected error
        final SearchWriteResult result = SearchWriteResult.unexpectedError(writeModels.size(),
                new MongoSocketReadException("Gee, database is down. Whatever shall I do?", new ServerAddress(),
                        new IllegalMonitorStateException("Unsupported resolution")));
        final var report = runBulkWriteResultAckFlow(writeModels, result);
        final var message = report.get(0).second().get(0);

        // THEN: all ThingUpdaters receive negative acknowledgement.
        actorSystem.log().info(message);
        assertThat(message).contains("NotAcknowledged: UnexpectedError", "MongoSocketReadException");
        assertThat(report.get(0).first()).isEqualTo(BulkWriteResultAckFlow.Status.UNACKNOWLEDGED);
    }

    // test that indices in bulk write errors are all within bounds.
    @Test
    public void errorIndexOutOfBoundError() {
        final List<AbstractWriteModel> writeModels = generate5WriteModels();
        final SearchWriteResult result = SearchWriteResult.acknowledged(5, 2, 2, 2, 0, List.of(
                new SearchWriteError(0, SearchWriteError.Category.DUPLICATE_KEY, "E11000 duplicate key error"),
                new SearchWriteError(5, SearchWriteError.Category.OTHER, "E50 operation timed out")
        ));

        // WHEN: BulkWriteResultAckFlow receives partial update success with at least 1 error with out-of-bound index
        final var report = runBulkWriteResultAckFlow(writeModels, result);
        final var message = report.get(0).second().get(0);

        // THEN: All updates are considered failures
        actorSystem.log().info(message);
        assertThat(message).contains("ConsistencyError[indexOutOfBound]");
        assertThat(report.get(0).first()).isEqualTo(BulkWriteResultAckFlow.Status.CONSISTENCY_ERROR);
    }

    @Test
    public void acknowledgements() {
        final List<TestProbe> probes =
                IntStream.range(0, 5).mapToObj(i -> TestProbe.apply(actorSystem)).toList();
        final List<AbstractWriteModel> writeModels = generateWriteModels(probes);
        final SearchWriteResult result = SearchWriteResult.acknowledged(5, 2, 2, 2, 0, List.of(
                new SearchWriteError(3, SearchWriteError.Category.DUPLICATE_KEY, "E11000 duplicate key error"),
                new SearchWriteError(4, SearchWriteError.Category.OTHER, "E50 operation timed out")
        ));

        // WHEN: BulkWriteResultAckFlow receives partial update success with errors, one of which is not duplicate key
        runBulkWriteResultAckFlow(writeModels, result);

        // THEN: only the non-duplicate-key sender receives negative acknowledgement
        assertThat(probes.get(0).expectMsgClass(Acknowledgement.class).getHttpStatus())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(probes.get(1).expectMsgClass(Acknowledgement.class).getHttpStatus())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(probes.get(2).expectMsgClass(Acknowledgement.class).getHttpStatus())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(probes.get(3).expectMsgClass(Acknowledgement.class).getHttpStatus())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(probes.get(4).expectMsgClass(Acknowledgement.class).getHttpStatus())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    public void successEntryNamesTheCountsAndTheCorrelationId() {
        final List<AbstractWriteModel> writeModels = generate5WriteModels();
        final SearchWriteResult result =
                SearchWriteResult.acknowledged(5, 2, 3, 1, 2, List.of()).withDiagnostics("abc", 0, 0);

        final var report = BulkWriteResultAckFlow.checkBulkWriteResult(writeModels, result);

        assertThat(report.second().get(0)).isEqualTo("Acknowledged: Success[ack=true,errors=0,matched=3,upserts=2," +
                "inserted=0,modified=1,deleted=0] - correlation: abc");
    }

    @Test
    public void partialSuccessAndUnexpectedErrorEntriesEndWithTheCorrelationId() {
        final SearchWriteResult partial = SearchWriteResult.acknowledged(5, 2, 2, 2, 0, List.of(
                new SearchWriteError(3, SearchWriteError.Category.DUPLICATE_KEY, "E11000 duplicate key error"),
                new SearchWriteError(4, SearchWriteError.Category.OTHER, "E50 operation timed out")
        )).withDiagnostics("abc", 4, 5);
        final SearchWriteResult unexpected = SearchWriteResult.unexpectedError(5, new IllegalStateException("boom"));

        final String partialEntry =
                BulkWriteResultAckFlow.checkBulkWriteResult(generate5WriteModels(), partial).second().get(0);
        final String unexpectedEntry = BulkWriteResultAckFlow.checkBulkWriteResult(generate5WriteModels(),
                unexpected.withDiagnostics("abc", 0, 0)).second().get(0);
        final String uncorrelatedEntry =
                BulkWriteResultAckFlow.checkBulkWriteResult(generate5WriteModels(), unexpected).second().get(0);

        assertThat(partialEntry)
                .startsWith("Acknowledged: PartialSuccess[ack=true,errorCount=2,matched=2,upserts=0,inserted=4," +
                        "modified=2,deleted=5,errors=[")
                .endsWith("] - correlation: abc");
        assertThat(unexpectedEntry)
                .startsWith("NotAcknowledged: UnexpectedError[stacktrace=")
                .endsWith("] - correlation: abc");
        assertThat(uncorrelatedEntry)
                .startsWith("NotAcknowledged: UnexpectedError[stacktrace=")
                .endsWith("] - correlation: ");
    }

    @Test
    public void incorrectPatchOfAPatchModelIsLoggedWithItsFilter() {
        final ThingId thingA = ThingId.of("thing", "a");
        final ThingId thingB = ThingId.of("thing", "b");
        final AbstractWriteModel patch = ThingWriteModel.of(Metadata.of(thingA, 8L, null, null, Set.of(), null),
                SearchIndexDocument.newBuilder(thingA).revision(8L).build()).asPatchUpdate(7L);
        final AbstractWriteModel full = ThingWriteModel.of(Metadata.of(thingB, 3L, null, null, Set.of(), null),
                SearchIndexDocument.newBuilder(thingB).revision(3L).build());
        final SearchWriteResult result =
                SearchWriteResult.acknowledged(2, 2, 1, 1, 0, List.of()).withDiagnostics("abc", 0, 0);

        final Logger logger = (Logger) LoggerFactory.getLogger(BulkWriteResultAckFlow.class.getName());
        final ListAppender<ILoggingEvent> appender = new MdcFreezingListAppender();
        appender.start();
        logger.addAppender(appender);
        final Pair<BulkWriteResultAckFlow.Status, List<String>> report;
        try {
            report = BulkWriteResultAckFlow.checkBulkWriteResult(List.of(patch, full), result);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(report.first()).isEqualTo(BulkWriteResultAckFlow.Status.INCORRECT_PATCH);
        assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage())
                            .startsWith("Encountered incorrect patch update for metadata: <")
                            .endsWith("> and filter: <_id=" + thingA + ", _revision=7>");
                    assertThat(event.getMDCPropertyMap()).containsEntry("correlation-id", "abc");
                });
        assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.INFO)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage()).isEqualTo(
                            "Skipping retry of full update in a batch with an incorrect patch: <" + thingB + ">");
                    assertThat(event.getMDCPropertyMap()).containsEntry("correlation-id", "abc");
                });
    }

    private List<String> getMessages(final List<Pair<BulkWriteResultAckFlow.Status, List<String>>> report) {
        final var messages = report.stream().flatMap(pair -> pair.second().stream()).toList();
        assertThat(messages).isNotEmpty();
        return messages;
    }

    private List<Pair<BulkWriteResultAckFlow.Status, List<String>>> runBulkWriteResultAckFlow(
            final List<AbstractWriteModel> writeModels, final SearchWriteResult result) {
        return Source.single(new BulkWriteResultAckFlow.NeutralBulkResult(writeModels, result))
                .via(BulkWriteResultAckFlow.start())
                .runWith(Sink.seq(), actorSystem)
                .toCompletableFuture()
                .join();
    }

    private List<AbstractWriteModel> generate5WriteModels() {
        return generateWriteModels(
                IntStream.range(0, 5).mapToObj(i -> TestProbe.apply(actorSystem)).collect(Collectors.toList()));
    }

    private List<AbstractWriteModel> generateWriteModels(final List<TestProbe> probes) {
        final int howMany = probes.size();
        final List<AbstractWriteModel> writeModels = new ArrayList<>(howMany);
        for (int i = 0; i < howMany; ++i) {
            final ThingId thingId = ThingId.of("thing", String.valueOf(i));
            final long thingRevision = i * 10L;
            final PolicyId policyId = i % 4 < 2 ? null : PolicyId.of("policy", String.valueOf(i));
            final long policyRevision = i * 100L;
            final PolicyTag policyTag = policyId == null ? null : PolicyTag.of(policyId, policyRevision);
            final Metadata metadata =
                    Metadata.of(thingId, thingRevision, policyTag, null, Set.of(), List.of(), null,
                            actorSystem.actorSelection(probes.get(i).ref().path()));
            final AbstractWriteModel abstractModel;
            if (i % 2 == 0) {
                abstractModel = ThingDeleteModel.of(metadata);
            } else {
                abstractModel = ThingWriteModel.of(metadata,
                        SearchIndexDocument.newBuilder(thingId).revision(thingRevision).build());
            }
            writeModels.add(abstractModel);
        }
        return writeModels;
    }

    /**
     * Logback reads the MDC of an event lazily; this appender captures it while the event is appended.
     */
    private static final class MdcFreezingListAppender extends ListAppender<ILoggingEvent> {

        @Override
        protected void append(final ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    }
}
