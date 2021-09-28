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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.signals.ShardedMessageEnvelope;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.commands.sudo.UpdateThingResponse;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.WriteResultAndErrors;
import org.junit.After;
import org.junit.Test;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoSocketReadException;
import com.mongodb.ServerAddress;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.bulk.BulkWriteUpsert;

import akka.actor.ActorSystem;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link BulkWriteResultAckFlow}.
 */
public final class BulkWriteResultAckFlowTest {

    private final ActorSystem actorSystem = ActorSystem.create();
    private final TestProbe updaterShardProbe = TestProbe.apply("updater", actorSystem);
    private final BulkWriteResultAckFlow underTest = BulkWriteResultAckFlow.of(updaterShardProbe.ref());

    @After
    public void stopActorSystem() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void allSuccess() {
        final List<AbstractWriteModel> writeModels = generate5WriteModels();
        final BulkWriteResult result = BulkWriteResult.acknowledged(0, 3, 1, 1,
                List.of(new BulkWriteUpsert(0, new BsonString("upsert 0")),
                        new BulkWriteUpsert(4, new BsonString("upsert 4")))
        );

        // WHEN
        final WriteResultAndErrors resultAndErrors = WriteResultAndErrors.success(writeModels, result);
        final String message = runBulkWriteResultAckFlowAndGetFirstLogEntry(resultAndErrors);

        // THEN
        actorSystem.log().info(message);
        assertThat(message).contains("Acknowledged: Success");
    }

    @Test
    public void partialSuccess() {
        final List<AbstractWriteModel> writeModels = generate5WriteModels();
        final BulkWriteResult result = BulkWriteResult.acknowledged(1, 2, 1, 2, List.of(), List.of());
        final List<BulkWriteError> updateFailure = List.of(
                new BulkWriteError(11000, "E11000 duplicate key error", new BsonDocument(), 3),
                new BulkWriteError(50, "E50 operation timed out", new BsonDocument(), 4)
        );

        // WHEN: BulkWriteResultAckFlow receives partial update success with errors, one of which is not duplicate key
        final WriteResultAndErrors resultAndErrors = WriteResultAndErrors.failure(writeModels,
                new MongoBulkWriteException(result, updateFailure, null, new ServerAddress(), Set.of()));
        final String message = runBulkWriteResultAckFlowAndGetFirstLogEntry(resultAndErrors);

        // THEN: the non-duplicate-key error triggers a failure acknowledgement
        actorSystem.log().info(message);
        assertThat(expectUpdateThingResponse(writeModels.get(4).getMetadata().getThingId()))
                .describedAs("response is failure")
                .returns(false, UpdateThingResponse::isSuccess);
        assertThat(message).contains("Acknowledged: PartialSuccess");
    }

    @Test
    public void unexpectedMongoSocketReadException() {
        final List<AbstractWriteModel> writeModels = generate5WriteModels();

        // WHEN: BulkWriteResultAckFlow receives unexpected error
        final WriteResultAndErrors resultAndErrors = WriteResultAndErrors.unexpectedError(writeModels,
                new MongoSocketReadException("Gee, database is down. Whatever shall I do?", new ServerAddress(),
                        new IllegalMonitorStateException("Unsupported resolution")));
        final String message = runBulkWriteResultAckFlowAndGetFirstLogEntry(resultAndErrors);

        // THEN: all ThingUpdaters receive negative acknowledgement.
        actorSystem.log().info(message);
        for (final AbstractWriteModel writeModel : writeModels) {
            assertThat(expectUpdateThingResponse(writeModel.getMetadata().getThingId()))
                    .describedAs("response is failure")
                    .returns(false, UpdateThingResponse::isSuccess);
        }
        assertThat(message).contains("NotAcknowledged: UnexpectedError", "MongoSocketReadException");
    }

    // test that indices in bulk write errors are all within bounds.
    // upsert indexes are not checked since they do not participate in acknowledgement handling.
    @Test
    public void errorIndexOutOfBoundError() {
        final List<AbstractWriteModel> writeModels = generate5WriteModels();
        final BulkWriteResult result = BulkWriteResult.acknowledged(1, 2, 1, 2, List.of(), List.of());
        final List<BulkWriteError> updateFailure = List.of(
                new BulkWriteError(11000, "E11000 duplicate key error", new BsonDocument(), 0),
                new BulkWriteError(50, "E50 operation timed out", new BsonDocument(), 5)
        );

        // WHEN: BulkWriteResultAckFlow receives partial update success with at least 1 error with out-of-bound index
        final WriteResultAndErrors resultAndErrors = WriteResultAndErrors.failure(writeModels,
                new MongoBulkWriteException(result, updateFailure, null, new ServerAddress(), Set.of()));
        final String message = runBulkWriteResultAckFlowAndGetFirstLogEntry(resultAndErrors);

        // THEN: All updates are considered failures
        actorSystem.log().info(message);
        for (final AbstractWriteModel writeModel : writeModels) {
            final UpdateThingResponse response = expectUpdateThingResponse(writeModel.getMetadata().getThingId());
            assertThat(response).describedAs("response is failure").returns(false, UpdateThingResponse::isSuccess);
        }
        assertThat(message).contains("ConsistencyError[indexOutOfBound]");
    }

    @Test
    public void acknowledgements() {
        final List<TestProbe> probes =
                IntStream.range(0, 5).mapToObj(i -> TestProbe.apply(actorSystem)).collect(Collectors.toList());
        final List<AbstractWriteModel> writeModels = generateWriteModels(probes);
        final BulkWriteResult result = BulkWriteResult.acknowledged(1, 2, 1, 2, List.of(), List.of());
        final List<BulkWriteError> updateFailure = List.of(
                new BulkWriteError(11000, "E11000 duplicate key error", new BsonDocument(), 3),
                new BulkWriteError(50, "E50 operation timed out", new BsonDocument(), 4)
        );

        // WHEN: BulkWriteResultAckFlow receives partial update success with errors, one of which is not duplicate key
        final WriteResultAndErrors resultAndErrors = WriteResultAndErrors.failure(writeModels,
                new MongoBulkWriteException(result, updateFailure, null, new ServerAddress(), Set.of()));
        runBulkWriteResultAckFlowAndGetFirstLogEntry(resultAndErrors);

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

    private String runBulkWriteResultAckFlowAndGetFirstLogEntry(final WriteResultAndErrors writeResultAndErrors) {
        return Source.single(writeResultAndErrors)
                .via(underTest.start(Duration.ZERO))
                .runWith(Sink.head(), actorSystem)
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
            final long thingRevision = i * 10;
            final PolicyId policyId = i % 4 < 2 ? null : PolicyId.of("policy", String.valueOf(i));
            final long policyRevision = i * 100;
            final Metadata metadata =
                    Metadata.of(thingId, thingRevision, policyId, policyRevision, List.of(), null, probes.get(i).ref());
            if (i % 2 == 0) {
                writeModels.add(ThingDeleteModel.of(metadata));
            } else {
                writeModels.add(ThingWriteModel.of(metadata, new BsonDocument()));
            }
        }
        return writeModels;
    }

    private UpdateThingResponse expectUpdateThingResponse(final ThingId forWhom) {
        final ShardedMessageEnvelope envelope = updaterShardProbe.expectMsgClass(ShardedMessageEnvelope.class);
        assertThat(envelope.getType()).describedAs("ShardedMessageEnvelope#getType")
                .isEqualTo(UpdateThingResponse.TYPE);
        assertThat((CharSequence) envelope.getEntityId()).describedAs("ShardedMessageEnvelope#getEntityId")
                .isEqualTo(forWhom);
        return UpdateThingResponse.fromJson(envelope.getMessage(), envelope.getDittoHeaders());
    }

}
