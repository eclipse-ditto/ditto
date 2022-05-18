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
package org.eclipse.ditto.thingsearch.service.persistence.write.model;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.thingsearch.service.updater.actors.MongoWriteModel;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;

/**
 * Data structure containing requested write models, write results and errors for reliable search index update.
 */
@Immutable
public final class WriteResultAndErrors {

    private final List<MongoWriteModel> writeModels;
    private final BulkWriteResult bulkWriteResult;
    private final List<BulkWriteError> bulkWriteErrors;
    @Nullable private final Throwable unexpectedError;
    private final String bulkWriteCorrelationId;

    private WriteResultAndErrors(
            final Collection<MongoWriteModel> writeModels,
            final BulkWriteResult bulkWriteResult,
            final List<BulkWriteError> bulkWriteErrors,
            @Nullable final Throwable unexpectedError,
            final String bulkWriteCorrelationId) {
        this.writeModels = writeModels.stream().toList();
        this.bulkWriteResult = bulkWriteResult;
        this.bulkWriteErrors = bulkWriteErrors;
        this.unexpectedError = unexpectedError;
        this.bulkWriteCorrelationId = bulkWriteCorrelationId;
    }

    /**
     * Create a WriteResultAndErrors from a successful bulk write result.
     *
     * @param writeModels the write models requested.
     * @param bulkWriteResult the successful bulk write result.
     * @param bulkWriteCorrelationId a correlationId to use for correlating bulk write log statements.
     * @return the write result without errors.
     */
    public static WriteResultAndErrors success(final Collection<MongoWriteModel> writeModels,
            final BulkWriteResult bulkWriteResult,
            final String bulkWriteCorrelationId) {
        return new WriteResultAndErrors(writeModels, bulkWriteResult, Collections.emptyList(), null,
                bulkWriteCorrelationId);
    }

    /**
     * Create a WriteResultAndErrors from a MongoBulkWriteException.
     *
     * @param writeModels the requested write models.
     * @param mongoBulkWriteException the exception.
     * @param bulkWriteCorrelationId a correlationId to use for correlating bulk write log statements.
     * @return the write result with errors.
     */
    public static WriteResultAndErrors failure(final Collection<MongoWriteModel> writeModels,
            final MongoBulkWriteException mongoBulkWriteException,
            final String bulkWriteCorrelationId) {
        return new WriteResultAndErrors(writeModels, mongoBulkWriteException.getWriteResult(),
                mongoBulkWriteException.getWriteErrors(), null, bulkWriteCorrelationId);
    }

    public static WriteResultAndErrors failure(final Throwable error) {
        return new WriteResultAndErrors(List.of(), BulkWriteResult.unacknowledged(), List.of(), error, "");
    }

    /**
     * Create a WriteResultAndErrors from an unexpected error. Getting called suggests a bug in Ditto or in its
     * environment.
     *
     * @param writeModels the requested write models.
     * @param unexpectedError the unexpected error.
     * @param bulkWriteCorrelationId a correlationId to use for correlating bulk write log statements.
     * @return the write result with an unexpected error.
     */
    public static WriteResultAndErrors unexpectedError(final Collection<MongoWriteModel> writeModels,
            final Throwable unexpectedError,
            final String bulkWriteCorrelationId) {
        return new WriteResultAndErrors(writeModels, BulkWriteResult.unacknowledged(), Collections.emptyList(),
                unexpectedError, bulkWriteCorrelationId);
    }

    /**
     * Retrieve the requested write models.
     *
     * @return the write models.
     */
    public List<MongoWriteModel> getWriteModels() {
        return writeModels;
    }

    /**
     * Retrieve the bulk write result.
     *
     * @return the bulk write result.
     */
    public BulkWriteResult getBulkWriteResult() {
        return bulkWriteResult;
    }

    /**
     * Retrieve the bulk write errors.
     *
     * @return the bulk write errors.
     */
    public List<BulkWriteError> getBulkWriteErrors() {
        return bulkWriteErrors;
    }

    /**
     * Retrieve the unexpected error if any.
     *
     * @return the unexpected error.
     */
    public Optional<Throwable> getUnexpectedError() {
        return Optional.ofNullable(unexpectedError);
    }

    /**
     * Retrieve the correlationId of the bulk write operation to use for correlating log statements.
     *
     * @return the correlationId of the bulk write operation.
     */
    public String getBulkWriteCorrelationId() {
        return bulkWriteCorrelationId;
    }

    /**
     * Check if the error is a namespace-blocked exception.
     *
     * @return whether the error is a namespace-blocked exception.
     */
    public boolean isNamespaceBlockedException() {
        return unexpectedError instanceof NamespaceBlockedException;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof final WriteResultAndErrors that) {
            return Objects.equals(writeModels, that.writeModels) &&
                    Objects.equals(bulkWriteResult, that.bulkWriteResult) &&
                    Objects.equals(bulkWriteErrors, that.bulkWriteErrors) &&
                    Objects.equals(unexpectedError, that.unexpectedError) &&
                    Objects.equals(bulkWriteCorrelationId, that.bulkWriteCorrelationId);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(writeModels, bulkWriteResult, bulkWriteErrors, unexpectedError, bulkWriteCorrelationId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[writeModels=" + writeModels +
                ",bulkWriteResult=" + bulkWriteResult +
                ",bulkWriteErrors=" + bulkWriteErrors +
                ",unexpectedError=" + unexpectedError +
                ",bulkWriteCorrelationId=" + bulkWriteCorrelationId +
                "]";
    }
}
