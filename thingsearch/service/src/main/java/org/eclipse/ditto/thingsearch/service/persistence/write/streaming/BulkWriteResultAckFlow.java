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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.ShardedMessageEnvelope;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.thingsearch.api.commands.sudo.UpdateThingResponse;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.WriteResultAndErrors;

import com.mongodb.ErrorCategory;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.stream.DelayOverflowStrategy;
import akka.stream.javadsl.Flow;

/**
 * Flow that sends acknowledgements to ThingUpdater according to bulk write results.
 */
final class BulkWriteResultAckFlow {

    private static final String ERRORS_COUNTER_NAME = "search-index-update-errors";

    private final ActorRef updaterShard;
    private final Counter errorsCounter;

    private BulkWriteResultAckFlow(final ActorRef updaterShard) {
        this.updaterShard = updaterShard;
        errorsCounter = DittoMetrics.counter(ERRORS_COUNTER_NAME);
    }

    static BulkWriteResultAckFlow of(final ActorRef updaterShard) {
        return new BulkWriteResultAckFlow(updaterShard);
    }

    Flow<WriteResultAndErrors, String, NotUsed> start(final Duration delay) {
        return getDelayFlow(delay).mapConcat(this::checkBulkWriteResult);
    }

    private Iterable<String> checkBulkWriteResult(final WriteResultAndErrors writeResultAndErrors) {
        if (wasNotAcknowledged(writeResultAndErrors)) {
            // All failed.
            acknowledgeFailures(getAllMetadata(writeResultAndErrors));
            return Collections.singleton(logResult("NotAcknowledged", writeResultAndErrors, false));
        } else {
            final Optional<String> consistencyError = checkForConsistencyError(writeResultAndErrors);
            if (consistencyError.isPresent()) {
                // write result is not consistent; there is a bug with Ditto or with its environment
                acknowledgeFailures(getAllMetadata(writeResultAndErrors));
                return Collections.singleton(consistencyError.get());
            } else {
                final List<BulkWriteError> errors = writeResultAndErrors.getBulkWriteErrors();
                final Collection<String> logEntries = new ArrayList<>(errors.size() + 1);
                final Collection<Metadata> failedMetadata = new ArrayList<>(errors.size());
                logEntries.add(logResult("Acknowledged", writeResultAndErrors, errors.isEmpty()));
                final BitSet failedIndices = new BitSet(writeResultAndErrors.getWriteModels().size());
                for (final BulkWriteError error : errors) {
                    final Metadata metadata = writeResultAndErrors.getWriteModels().get(error.getIndex()).getMetadata();
                    logEntries.add(String.format("UpdateFailed for %s due to %s", metadata, error));
                    if (error.getCategory() != ErrorCategory.DUPLICATE_KEY) {
                        failedIndices.set(error.getIndex());
                        failedMetadata.add(metadata);
                        // duplicate key error is considered success
                    }
                }
                acknowledgeFailures(failedMetadata);
                acknowledgeSuccesses(failedIndices, writeResultAndErrors.getWriteModels());
                return logEntries;
            }
        }
    }

    private static void acknowledgeSuccesses(final BitSet failedIndices, final List<AbstractWriteModel> writeModels) {
        for (int i = 0; i < writeModels.size(); ++i) {
            if (!failedIndices.get(i)) {
                writeModels.get(i).getMetadata().sendAck();
            }
        }
    }

    private void acknowledgeFailures(final Collection<Metadata> metadataList) {
        errorsCounter.increment(metadataList.size());
        for (final Metadata metadata : metadataList) {
            metadata.sendNAck(); // also stops timer even if no acknowledgement is requested
            final UpdateThingResponse response = createFailureResponse(metadata);
            metadata.getOrigin().ifPresentOrElse(
                    origin -> origin.tell(response, ActorRef.noSender()),
                    () -> {
                        final ShardedMessageEnvelope envelope =
                                ShardedMessageEnvelope.of(response.getEntityId(), response.getType(), response.toJson(),
                                        response.getDittoHeaders());
                        updaterShard.tell(envelope, ActorRef.noSender());
                    }
            );
        }
    }

    private static Flow<WriteResultAndErrors, WriteResultAndErrors, NotUsed> getDelayFlow(final Duration delay) {
        if (isPositive(delay)) {
            // delay required to delay the first result. delay applied for each buffered batch.
            return Flow.<WriteResultAndErrors>create()
                    .delay(delay, DelayOverflowStrategy.backpressure());
        } else {
            return Flow.create();
        }
    }

    private static boolean isPositive(final Duration duration) {
        return Duration.ZERO.minus(duration).isNegative();
    }

    private static UpdateThingResponse createFailureResponse(final Metadata metadata) {
        return UpdateThingResponse.of(
                metadata.getThingId(),
                metadata.getThingRevision(),
                metadata.getPolicyId().orElse(null),
                metadata.getPolicyId().flatMap(policyId -> metadata.getPolicyRevision()).orElse(null),
                false,
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
            return Optional.of(String.format("ConsistencyError[indexOutOfBound]: %s", resultAndErrors));
        } else {
            return Optional.empty();
        }
    }

    private static boolean areAllIndexesWithinBounds(final Collection<BulkWriteError> bulkWriteErrors,
            final int requested) {

        return bulkWriteErrors.stream().mapToInt(BulkWriteError::getIndex).allMatch(i -> 0 <= i && i < requested);
    }

    private static List<Metadata> getAllMetadata(final WriteResultAndErrors writeResultAndErrors) {
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
