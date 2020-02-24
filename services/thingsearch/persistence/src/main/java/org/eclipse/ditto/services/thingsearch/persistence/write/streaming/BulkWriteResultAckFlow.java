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
package org.eclipse.ditto.services.thingsearch.persistence.write.streaming;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.UpdateThingResponse;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.Metadata;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.WriteResultAndErrors;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;

import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.stream.javadsl.Flow;

/**
 * Flow that sends positive or acknowledgements to ThingUpdater.
 */
final class BulkWriteResultAckFlow {

    private static final String ERRORS_COUNTER_NAME = "search-index-update-errors";

    private final ActorRef updaterShard;
    private final Counter errorsCounter;

    private BulkWriteResultAckFlow(final ActorRef updaterShard) {
        this.updaterShard = updaterShard;
        this.errorsCounter = DittoMetrics.counter(ERRORS_COUNTER_NAME);
    }

    static BulkWriteResultAckFlow of(final ActorRef updaterShard) {
        return new BulkWriteResultAckFlow(updaterShard);
    }

    Flow<WriteResultAndErrors, String, NotUsed> start() {
        return Flow.<WriteResultAndErrors>create()
                .mapConcat(this::checkBulkWriteResult);
    }

    private Iterable<String> checkBulkWriteResult(final WriteResultAndErrors writeResultAndErrors) {
        if (wasNotAcknowledged(writeResultAndErrors)) {
            // All failed.
            acknowledgeFailures(getAllThings(writeResultAndErrors));
            return Collections.singleton(logResult("NotAcknowledged", writeResultAndErrors, false));
        } else {
            final Optional<String> consistencyError = checkForConsistencyError(writeResultAndErrors);
            if (consistencyError.isPresent()) {
                // write result is not consistent; there is a bug with Ditto or with its environment
                acknowledgeFailures(getAllThings(writeResultAndErrors));
                return Collections.singleton(consistencyError.get());
            } else {
                final List<BulkWriteError> errors = writeResultAndErrors.getBulkWriteErrors();
                final List<String> logEntries = new ArrayList<>(errors.size() + 1);
                final List<Metadata> failedThings = new ArrayList<>(errors.size());
                logEntries.add(logResult("Acknowledged", writeResultAndErrors, errors.isEmpty()));
                for (final BulkWriteError error : errors) {
                    final Metadata metadata = writeResultAndErrors.getWriteModels().get(error.getIndex()).getMetadata();
                    logEntries.add(String.format("UpdateFailed for %s due to %s", metadata, error));
                    failedThings.add(metadata);
                }
                acknowledgeFailures(failedThings);
                return logEntries;
            }
        }
    }

    private void acknowledgeFailures(final List<Metadata> things) {
        errorsCounter.increment(things.size());
        acknowledge(things, BulkWriteResultAckFlow::createFailureResponse);
    }

    private void acknowledge(final List<Metadata> things,
            final Function<Metadata, UpdateThingResponse> responseCreator) {

        for (final Metadata metadata : things) {
            final UpdateThingResponse response = responseCreator.apply(metadata);
            final ShardedMessageEnvelope envelope =
                    ShardedMessageEnvelope.of(response.getEntityId(), response.getType(), response.toJson(),
                            response.getDittoHeaders());
            updaterShard.tell(envelope, ActorRef.noSender());
        }
    }

    private static UpdateThingResponse createFailureResponse(final Metadata metadata) {
        return createResponse(metadata, false);
    }

    private static UpdateThingResponse createResponse(final Metadata metadata, final boolean isSuccess) {
        return UpdateThingResponse.of(
                metadata.getThingId(),
                metadata.getThingRevision(),
                metadata.getPolicyId().orElse(null),
                metadata.getPolicyRevision(),
                isSuccess,
                DittoHeaders.empty()
        );
    }

    private static boolean wasNotAcknowledged(final WriteResultAndErrors writeResultAndErrors) {
        return !writeResultAndErrors.getBulkWriteResult().wasAcknowledged();
    }

    /**
     * Check if the bulk write result is consistent with the requested write models.
     *
     * @param resultAndErrors data structure containing input and output of the bulk write operation.
     * @return whether the data is consistent.
     */
    private static Optional<String> checkForConsistencyError(final WriteResultAndErrors resultAndErrors) {
        final int requested = resultAndErrors.getWriteModels().size();
        if (!areAllIndexesWithinBounds(resultAndErrors.getBulkWriteErrors(), requested)) {
            // some indexes not within bounds
            return Optional.of(String.format("ConsistencyError[indexOutOfBound]: %s", resultAndErrors.toString()));
        } else {
            return Optional.empty();
        }
    }

    private static boolean areAllIndexesWithinBounds(final List<BulkWriteError> bulkWriteErrors, final int requested) {
        return bulkWriteErrors.stream().mapToInt(BulkWriteError::getIndex).allMatch(i -> 0 <= i && i < requested);
    }

    private static List<Metadata> getAllThings(final WriteResultAndErrors writeResultAndErrors) {
        return writeResultAndErrors.getWriteModels()
                .stream()
                .map(AbstractWriteModel::getMetadata)
                .collect(Collectors.toList());
    }

    private static String logResult(final String status, final WriteResultAndErrors writeResultAndErrors,
            final boolean isCompleteSuccess) {
        final Optional<Throwable> unexpectedError = writeResultAndErrors.getUnexpectedError();
        if (unexpectedError.isPresent()) {
            final Throwable error = unexpectedError.get();
            final StringWriter stackTraceWriter = new StringWriter();
            stackTraceWriter.append(String.format("%s: UnexpectedError[stacktrace=", status));
            error.printStackTrace(new PrintWriter(stackTraceWriter));
            return stackTraceWriter.append("]").toString();
        } else if (isCompleteSuccess) {
            final BulkWriteResult bulkWriteResult = writeResultAndErrors.getBulkWriteResult();
            return String.format(
                    "%s: Success[ack=%b,errors=%d,matched=%d,upserts=%d,inserted=%d,modified=%d,deleted=%d]",
                    status,
                    bulkWriteResult.wasAcknowledged(),
                    writeResultAndErrors.getBulkWriteErrors().size(),
                    bulkWriteResult.getMatchedCount(),
                    bulkWriteResult.getUpserts().size(),
                    bulkWriteResult.getInsertedCount(),
                    bulkWriteResult.getModifiedCount(),
                    bulkWriteResult.getDeletedCount());
        } else {
            // partial success
            final BulkWriteResult bulkWriteResult = writeResultAndErrors.getBulkWriteResult();
            return String.format(
                    "%s: PartialSuccess[ack=%b,errorCount=%d,matched=%d,upserts=%d,inserted=%d,modified=%d," +
                            "deleted=%d,errors=%s]",
                    status,
                    bulkWriteResult.wasAcknowledged(),
                    writeResultAndErrors.getBulkWriteErrors().size(),
                    bulkWriteResult.getMatchedCount(),
                    bulkWriteResult.getUpserts().size(),
                    bulkWriteResult.getInsertedCount(),
                    bulkWriteResult.getModifiedCount(),
                    bulkWriteResult.getDeletedCount(),
                    writeResultAndErrors.getBulkWriteErrors()
            );
        }
    }
}
