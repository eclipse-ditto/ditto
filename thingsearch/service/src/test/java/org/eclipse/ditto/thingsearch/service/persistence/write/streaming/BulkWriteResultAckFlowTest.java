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

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.WriteResultAndErrors;
import org.eclipse.ditto.thingsearch.service.updater.actors.MongoWriteModel;
import org.junit.After;
import org.junit.Test;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoSocketReadException;
import com.mongodb.ServerAddress;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.bulk.BulkWriteUpsert;

import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

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
        final List<MongoWriteModel> writeModels = generate5WriteModels();
        final BulkWriteResult result = BulkWriteResult.acknowledged(0, 3, 1, 1,
                List.of(new BulkWriteUpsert(0, new BsonString("upsert 0")),
                        new BulkWriteUpsert(4, new BsonString("upsert 4"))),
                List.of()
        );

        // WHEN
        final WriteResultAndErrors resultAndErrors = WriteResultAndErrors.success(writeModels, result, "correlation");
        final var report = runBulkWriteResultAckFlow(resultAndErrors);

        // THEN
        for (final var message : getMessages(report)) {
            actorSystem.log().info(message);
            assertThat(message).contains("Acknowledged: Success");
        }
    }

    @Test
    public void partialSuccess() {
        final List<MongoWriteModel> writeModels = generate5WriteModels();
        final BulkWriteResult result = BulkWriteResult.acknowledged(1, 2, 1, 2, List.of(), List.of());
        final List<BulkWriteError> updateFailure = List.of(
                new BulkWriteError(11000, "E11000 duplicate key error", new BsonDocument(), 3),
                new BulkWriteError(50, "E50 operation timed out", new BsonDocument(), 4)
        );

        // WHEN: BulkWriteResultAckFlow receives partial update success with errors, one of which is not duplicate key
        final WriteResultAndErrors resultAndErrors = WriteResultAndErrors.failure(writeModels,
                new MongoBulkWriteException(result, updateFailure, null, new ServerAddress(), Set.of()), "correlation");
        final var report = runBulkWriteResultAckFlow(resultAndErrors);
        final var message = report.get(0).second().get(0);

        // THEN: the non-duplicate-key error triggers a failure acknowledgement
        actorSystem.log().info(message);
        assertThat(message).contains("Acknowledged: PartialSuccess");
        assertThat(report.get(0).first()).isEqualTo(BulkWriteResultAckFlow.Status.WRITE_ERROR);
    }

    @Test
    public void unexpectedMongoSocketReadException() {
        final List<MongoWriteModel> writeModels = generate5WriteModels();

        // WHEN: BulkWriteResultAckFlow receives unexpected error
        final WriteResultAndErrors resultAndErrors = WriteResultAndErrors.unexpectedError(writeModels,
                new MongoSocketReadException("Gee, database is down. Whatever shall I do?", new ServerAddress(),
                        new IllegalMonitorStateException("Unsupported resolution")), "correlation");
        final var report = runBulkWriteResultAckFlow(resultAndErrors);
        final var message = report.get(0).second().get(0);

        // THEN: all ThingUpdaters receive negative acknowledgement.
        actorSystem.log().info(message);
        assertThat(message).contains("NotAcknowledged: UnexpectedError", "MongoSocketReadException");
        assertThat(report.get(0).first()).isEqualTo(BulkWriteResultAckFlow.Status.UNACKNOWLEDGED);
    }

    // test that indices in bulk write errors are all within bounds.
    // upsert indexes are not checked since they do not participate in acknowledgement handling.
    @Test
    public void errorIndexOutOfBoundError() {
        final List<MongoWriteModel> writeModels = generate5WriteModels();
        final BulkWriteResult result = BulkWriteResult.acknowledged(1, 2, 1, 2, List.of(), List.of());
        final List<BulkWriteError> updateFailure = List.of(
                new BulkWriteError(11000, "E11000 duplicate key error", new BsonDocument(), 0),
                new BulkWriteError(50, "E50 operation timed out", new BsonDocument(), 5)
        );

        // WHEN: BulkWriteResultAckFlow receives partial update success with at least 1 error with out-of-bound index
        final WriteResultAndErrors resultAndErrors = WriteResultAndErrors.failure(writeModels,
                new MongoBulkWriteException(result, updateFailure, null, new ServerAddress(), Set.of()), "correlation");
        final var report = runBulkWriteResultAckFlow(resultAndErrors);
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
        final List<MongoWriteModel> writeModels = generateWriteModels(probes);
        final BulkWriteResult result = BulkWriteResult.acknowledged(1, 2, 1, 2, List.of(), List.of());
        final List<BulkWriteError> updateFailure = List.of(
                new BulkWriteError(11000, "E11000 duplicate key error", new BsonDocument(), 3),
                new BulkWriteError(50, "E50 operation timed out", new BsonDocument(), 4)
        );

        // WHEN: BulkWriteResultAckFlow receives partial update success with errors, one of which is not duplicate key
        final WriteResultAndErrors resultAndErrors = WriteResultAndErrors.failure(writeModels,
                new MongoBulkWriteException(result, updateFailure, null, new ServerAddress(), Set.of()), "correlation");
        runBulkWriteResultAckFlow(resultAndErrors);

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

    private List<String> getMessages(final List<Pair<BulkWriteResultAckFlow.Status, List<String>>> report) {
        final var messages = report.stream().flatMap(pair -> pair.second().stream()).toList();
        assertThat(messages).isNotEmpty();
        return messages;
    }

    private List<Pair<BulkWriteResultAckFlow.Status, List<String>>> runBulkWriteResultAckFlow(
            final WriteResultAndErrors writeResultAndErrors) {
        return Source.single(writeResultAndErrors)
                .via(BulkWriteResultAckFlow.start())
                .runWith(Sink.seq(), actorSystem)
                .toCompletableFuture()
                .join();
    }

    private List<MongoWriteModel> generate5WriteModels() {
        return generateWriteModels(
                IntStream.range(0, 5).mapToObj(i -> TestProbe.apply(actorSystem)).collect(Collectors.toList()));
    }

    private List<MongoWriteModel> generateWriteModels(final List<TestProbe> probes) {
        final int howMany = probes.size();
        final List<MongoWriteModel> writeModels = new ArrayList<>(howMany);
        for (int i = 0; i < howMany; ++i) {
            final ThingId thingId = ThingId.of("thing", String.valueOf(i));
            final long thingRevision = i * 10L;
            final PolicyId policyId = i % 4 < 2 ? null : PolicyId.of("policy", String.valueOf(i));
            final long policyRevision = i * 100L;
            final PolicyTag policyTag = policyId == null ? null : PolicyTag.of(policyId, policyRevision);
            final Metadata metadata =
                    Metadata.of(thingId, thingRevision, policyTag, Set.of(), List.of(), null,
                            actorSystem.actorSelection(probes.get(i).ref().path()));
            final AbstractWriteModel abstractModel;
            if (i % 2 == 0) {
                abstractModel = ThingDeleteModel.of(metadata);
            } else {
                abstractModel = ThingWriteModel.of(metadata, new BsonDocument());
            }
            writeModels.add(MongoWriteModel.of(abstractModel, abstractModel.toMongo(), false));
        }
        return writeModels;
    }
}
