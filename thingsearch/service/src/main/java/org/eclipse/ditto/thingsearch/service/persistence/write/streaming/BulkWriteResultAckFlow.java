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

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteError;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteResult;

import org.apache.pekko.NotUsed;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Flow;

/**
 * Flow that sends acknowledgements to ThingUpdater according to backend-neutral write results.
 */
public final class BulkWriteResultAckFlow {

    private static final Counter ERRORS_COUNTER = DittoMetrics.counter("wildcard-search-index-update-errors");

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(BulkWriteResultAckFlow.class);

    private BulkWriteResultAckFlow() {}

    /**
     * A backend-neutral bulk result: the write models submitted together with the {@link SearchWriteResult} of
     * applying them.
     *
     * @param writeModels the submitted neutral write models.
     * @param result the neutral write result.
     */
    public record NeutralBulkResult(List<AbstractWriteModel> writeModels, SearchWriteResult result) {}

    static Flow<NeutralBulkResult, Pair<Status, List<String>>, NotUsed> start() {
        return Flow.<NeutralBulkResult>create()
                .map(neutralBulkResult ->
                        checkBulkWriteResult(neutralBulkResult.writeModels(), neutralBulkResult.result()));
    }

    /**
     * Check the result of an update operation, acknowledge successes and failures, and generate a report.
     *
     * @param writeModels the submitted neutral write models.
     * @param result the neutral write result.
     * @return The report.
     */
    public static Pair<Status, List<String>> checkBulkWriteResult(final List<AbstractWriteModel> writeModels,
            final SearchWriteResult result) {

        switch (result.classify()) {
            case UNACKNOWLEDGED:
                // All failed.
                acknowledgeFailures(getAllMetadata(writeModels));
                return Pair.create(Status.UNACKNOWLEDGED,
                        List.of(logResult("NotAcknowledged", result, false, false)));
            case CONSISTENCY_ERROR:
                // write result is not consistent; there is a bug with Ditto or with its environment
                acknowledgeFailures(getAllMetadata(writeModels));
                return Pair.create(Status.CONSISTENCY_ERROR,
                        List.of(String.format("ConsistencyError[indexOutOfBound]: %s", result)));
            case INCORRECT_PATCH:
                reportIncorrectPatch(writeModels);
                return Pair.create(Status.INCORRECT_PATCH,
                        acknowledgeSuccessesAndFailures(writeModels, result, true));
            case WRITE_ERROR:
                return Pair.create(Status.WRITE_ERROR,
                        acknowledgeSuccessesAndFailures(writeModels, result, false));
            case OK:
            default:
                return Pair.create(Status.OK,
                        acknowledgeSuccessesAndFailures(writeModels, result, false));
        }
    }

    private static void reportIncorrectPatch(final List<AbstractWriteModel> writeModels) {
        // Some patches are not applied due to inconsistent sequence number in the search index.
        // It is not possible to identify which patches are not applied; therefore request all patch updates to retry.
        writeModels.forEach(model -> {
            if (model instanceof ThingWriteModel thingWriteModel && thingWriteModel.isPatchUpdate()) {
                LOGGER.warn("Encountered incorrect patch update for metadata: <{}>", model.getMetadata());
            } else {
                LOGGER.info("Skipping retry of full update in a batch with an incorrect patch: <{}>",
                        model.getMetadata().getThingId());
            }
        });
    }

    private static List<String> acknowledgeSuccessesAndFailures(final List<AbstractWriteModel> writeModels,
            final SearchWriteResult result, final boolean containsIncorrectPatch) {
        final List<SearchWriteError> errors = result.getErrors();
        final List<String> logEntries = new ArrayList<>(errors.size() + 1);
        final Collection<Metadata> failedMetadata = new ArrayList<>(errors.size());
        logEntries.add(logResult("Acknowledged", result, errors.isEmpty(), containsIncorrectPatch));
        final BitSet failedIndices = new BitSet(writeModels.size());
        for (final SearchWriteError error : errors) {
            final Metadata metadata = writeModels.get(error.index()).getMetadata();
            logEntries.add(String.format("UpdateFailed for %s due to %s", metadata, error));
            if (error.category() != SearchWriteError.Category.DUPLICATE_KEY) {
                failedIndices.set(error.index());
                failedMetadata.add(metadata);
                // duplicate key error is considered success
            }
        }
        acknowledgeFailures(failedMetadata);
        acknowledgeSuccesses(failedIndices, writeModels);

        return logEntries;
    }

    private static void acknowledgeSuccesses(final BitSet failedIndices, final List<AbstractWriteModel> writeModels) {
        for (int i = 0; i < writeModels.size(); ++i) {
            if (!failedIndices.get(i)) {
                writeModels.get(i).getMetadata().sendAck();
            }
        }
    }

    private static void acknowledgeFailures(final Collection<Metadata> metadataList) {
        ERRORS_COUNTER.increment(metadataList.size());
        for (final Metadata metadata : metadataList) {
            metadata.sendNAck(); // also stops timer even if no acknowledgement is requested
        }
    }

    private static List<Metadata> getAllMetadata(final List<AbstractWriteModel> writeModels) {
        return writeModels.stream()
                .map(AbstractWriteModel::getMetadata)
                .toList();
    }

    private static String logResult(final String status, final SearchWriteResult result,
            final boolean containsNoErrors, final boolean containsIncorrectPatch) {
        final var unexpectedError = result.getUnexpectedError();
        if (unexpectedError != null) {
            if (unexpectedError instanceof DittoRuntimeException dittoRuntimeException) {
                return dittoRuntimeException.toJsonString();
            } else {
                final StringWriter stackTraceWriter = new StringWriter();
                stackTraceWriter.append(String.format("%s: UnexpectedError[stacktrace=", status));
                unexpectedError.printStackTrace(new PrintWriter(stackTraceWriter));
                return stackTraceWriter.append("]").toString();
            }
        } else if (containsNoErrors) {
            return String.format(
                    "%s: %s[ack=%b,errors=%d,matched=%d,upserts=%d,modified=%d]",
                    status,
                    containsIncorrectPatch ? "IncorrectPatch" : "Success",
                    result.isAcknowledged(),
                    result.getErrors().size(),
                    result.getMatchedCount(),
                    result.getUpsertedCount(),
                    result.getModifiedCount());
        } else {
            // partial success or failure
            return String.format(
                    "%s: PartialSuccess[ack=%b,errorCount=%d,matched=%d,upserts=%d,modified=%d,errors=%s]",
                    status,
                    result.isAcknowledged(),
                    result.getErrors().size(),
                    result.getMatchedCount(),
                    result.getUpsertedCount(),
                    result.getModifiedCount(),
                    result.getErrors());
        }
    }

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
