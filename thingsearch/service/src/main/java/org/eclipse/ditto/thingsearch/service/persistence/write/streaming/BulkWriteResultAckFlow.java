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
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.WriteResultAndErrors;
import org.eclipse.ditto.thingsearch.service.updater.actors.MongoWriteModel;

import com.mongodb.ErrorCategory;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;

import akka.NotUsed;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;

/**
 * Flow that sends acknowledgements to ThingUpdater according to bulk write results.
 */
public final class BulkWriteResultAckFlow {

    private static final Counter ERRORS_COUNTER = DittoMetrics.counter("wildcard-search-index-update-errors");

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(BulkWriteResultAckFlow.class);

    private BulkWriteResultAckFlow() {}

    static Flow<WriteResultAndErrors, Pair<Status, List<String>>, NotUsed> start() {
        return Flow.<WriteResultAndErrors>create().map(BulkWriteResultAckFlow::checkBulkWriteResult);
    }

    /**
     * Check the result of an update operation, acknowledge successes and failures, and generate a report.
     *
     * @param writeResultAndErrors The result of an update operation.
     * @return The report.
     */
    public static Pair<Status, List<String>> checkBulkWriteResult(final WriteResultAndErrors writeResultAndErrors) {

        if (wasNotAcknowledged(writeResultAndErrors)) {
            // All failed.
            acknowledgeFailures(getAllMetadata(writeResultAndErrors));
            return Pair.create(Status.UNACKNOWLEDGED,
                    List.of(logResult("NotAcknowledged", writeResultAndErrors, false, false)));
        } else {
            final var consistencyError = checkForConsistencyError(writeResultAndErrors);
            switch (consistencyError.status()) {
                case CONSISTENCY_ERROR:
                    // write result is not consistent; there is a bug with Ditto or with its environment
                    acknowledgeFailures(getAllMetadata(writeResultAndErrors));

                    return Pair.create(consistencyError.status(), List.of(consistencyError.message));
                case INCORRECT_PATCH:
                    reportIncorrectPatch(writeResultAndErrors);

                    return Pair.create(consistencyError.status(),
                            getConsistencyOKResult(writeResultAndErrors, true));
                case OK:
                default:
                    return Pair.create(consistencyError.status(),
                            getConsistencyOKResult(writeResultAndErrors, false));
            }
        }
    }

    private static List<String> getConsistencyOKResult(final WriteResultAndErrors writeResultAndErrors,
            final boolean containsIncorrectPatch) {
        return acknowledgeSuccessesAndFailures(writeResultAndErrors, containsIncorrectPatch);
    }

    private static void reportIncorrectPatch(final WriteResultAndErrors writeResultAndErrors) {
        // Some patches are not applied due to inconsistent sequence number in the search index.
        // It is not possible to identify which patches are not applied; therefore request all patch updates to retry.
        writeResultAndErrors.getWriteModels().forEach(model -> {
            if (model.isPatchUpdate()) {
                final var abstractModel = model.getDitto();
                LOGGER.withCorrelationId(writeResultAndErrors.getBulkWriteCorrelationId())
                        .warn("Encountered incorrect patch update for metadata: <{}> and filter: <{}>",
                                abstractModel.getMetadata(), abstractModel.getFilter());
            } else {
                LOGGER.withCorrelationId(writeResultAndErrors.getBulkWriteCorrelationId())
                        .info("Skipping retry of full update in a batch with an incorrect patch: <{}>",
                                model.getDitto().getMetadata().getThingId());
            }
        });
    }

    private static List<String> acknowledgeSuccessesAndFailures(final WriteResultAndErrors writeResultAndErrors,
            final boolean containsIncorrectPatch) {
        final List<BulkWriteError> errors = writeResultAndErrors.getBulkWriteErrors();
        final List<String> logEntries = new ArrayList<>(errors.size() + 1);
        final Collection<Metadata> failedMetadata = new ArrayList<>(errors.size());
        logEntries.add(logResult("Acknowledged", writeResultAndErrors, errors.isEmpty(), containsIncorrectPatch));
        final BitSet failedIndices = new BitSet(writeResultAndErrors.getWriteModels().size());
        for (final BulkWriteError error : errors) {
            final Metadata metadata =
                    writeResultAndErrors.getWriteModels().get(error.getIndex()).getDitto().getMetadata();
            logEntries.add(String.format("UpdateFailed for %s due to %s", metadata, error));
            if (error.getCategory() != ErrorCategory.DUPLICATE_KEY) {
                failedIndices.set(error.getIndex());
                failedMetadata.add(metadata);
                // duplicate key error is considered success
            }
        }
        acknowledgeFailures(failedMetadata);
        acknowledgeSuccesses(failedIndices,
                writeResultAndErrors.getWriteModels());

        return logEntries;
    }

    private static void acknowledgeSuccesses(final BitSet failedIndices, final List<MongoWriteModel> writeModels) {
        for (int i = 0; i < writeModels.size(); ++i) {
            if (!failedIndices.get(i)) {
                writeModels.get(i).getDitto().getMetadata().sendAck();
            }
        }
    }

    private static void acknowledgeFailures(final Collection<Metadata> metadataList) {
        ERRORS_COUNTER.increment(metadataList.size());
        for (final Metadata metadata : metadataList) {
            metadata.sendNAck(); // also stops timer even if no acknowledgement is requested
        }
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
    private static ConsistencyCheckResult checkForConsistencyError(final WriteResultAndErrors resultAndErrors) {
        final int requested = resultAndErrors.getWriteModels().size();
        if (!areAllIndexesWithinBounds(resultAndErrors.getBulkWriteErrors(), requested)) {
            // some indexes not within bounds
            final var message = String.format("ConsistencyError[indexOutOfBound]: %s", resultAndErrors);

            return new ConsistencyCheckResult(Status.CONSISTENCY_ERROR, message);
        } else if (areUpdatesMissing(resultAndErrors)) {
            return new ConsistencyCheckResult(Status.INCORRECT_PATCH, "");
        } else if (!resultAndErrors.getBulkWriteErrors().isEmpty()) {
            return new ConsistencyCheckResult(Status.WRITE_ERROR, "");
        } else {
            return new ConsistencyCheckResult(Status.OK, "");
        }
    }

    private static boolean areUpdatesMissing(final WriteResultAndErrors resultAndErrors) {
        final var result = resultAndErrors.getBulkWriteResult();
        final long writeModelCount = resultAndErrors.getWriteModels().stream()
                .filter(writeModel -> !(writeModel.getDitto() instanceof ThingDeleteModel))
                .count();
        final long matchedCount = result.getMatchedCount();
        final long upsertCount = result.getUpserts().size();

        return matchedCount + upsertCount < writeModelCount;
    }

    private static boolean areAllIndexesWithinBounds(final Collection<BulkWriteError> bulkWriteErrors,
            final int requested) {
        return bulkWriteErrors.stream().mapToInt(BulkWriteError::getIndex).allMatch(i -> 0 <= i && i < requested);
    }

    private static List<Metadata> getAllMetadata(final WriteResultAndErrors writeResultAndErrors) {
        return writeResultAndErrors.getWriteModels()
                .stream()
                .map(MongoWriteModel::getDitto)
                .map(AbstractWriteModel::getMetadata)
                .toList();
    }

    private static String logResult(final String status, final WriteResultAndErrors writeResultAndErrors,
            final boolean containsNoErrors, final boolean containsIncorrectPatch) {
        final Optional<Throwable> unexpectedError = writeResultAndErrors.getUnexpectedError();
        if (unexpectedError.isPresent()) {
            final Throwable error = unexpectedError.get();
            if (error instanceof DittoRuntimeException dittoRuntimeException) {
                return dittoRuntimeException.toJsonString();
            } else {
                final StringWriter stackTraceWriter = new StringWriter();
                stackTraceWriter.append(String.format("%s: UnexpectedError[stacktrace=", status));
                error.printStackTrace(new PrintWriter(stackTraceWriter));
                return stackTraceWriter.append("] - correlation: ")
                        .append(writeResultAndErrors.getBulkWriteCorrelationId())
                        .toString();
            }
        } else if (containsNoErrors) {
            final BulkWriteResult bulkWriteResult = writeResultAndErrors.getBulkWriteResult();

            return String.format(
                    "%s: %s[ack=%b,errors=%d,matched=%d,upserts=%d,inserted=%d,modified=%d,deleted=%d] - correlation: %s",
                    status,
                    containsIncorrectPatch ? "IncorrectPatch" : "Success",
                    bulkWriteResult.wasAcknowledged(),
                    writeResultAndErrors.getBulkWriteErrors().size(),
                    bulkWriteResult.getMatchedCount(),
                    bulkWriteResult.getUpserts().size(),
                    bulkWriteResult.getInsertedCount(),
                    bulkWriteResult.getModifiedCount(),
                    bulkWriteResult.getDeletedCount(),
                    writeResultAndErrors.getBulkWriteCorrelationId());
        } else {
            // partial success or failure
            final BulkWriteResult bulkWriteResult = writeResultAndErrors.getBulkWriteResult();
            return String.format(
                    "%s: PartialSuccess[ack=%b,errorCount=%d,matched=%d,upserts=%d,inserted=%d,modified=%d," +
                            "deleted=%d,errors=%s] - correlation: %s",
                    status,
                    bulkWriteResult.wasAcknowledged(),
                    writeResultAndErrors.getBulkWriteErrors().size(),
                    bulkWriteResult.getMatchedCount(),
                    bulkWriteResult.getUpserts().size(),
                    bulkWriteResult.getInsertedCount(),
                    bulkWriteResult.getModifiedCount(),
                    bulkWriteResult.getDeletedCount(),
                    writeResultAndErrors.getBulkWriteErrors(),
                    writeResultAndErrors.getBulkWriteCorrelationId()
            );
        }
    }

    private record ConsistencyCheckResult(Status status, String message) {}

    /**
     * Summary of the write result status.
     */
    public enum Status {
        UNACKNOWLEDGED,
        CONSISTENCY_ERROR,
        INCORRECT_PATCH,
        WRITE_ERROR,
        OK
    }

}
