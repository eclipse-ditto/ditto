/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence;

import javax.annotation.concurrent.Immutable;

/**
 * Message indicating that the BulkWrite performed by {@code MongoSearchUpdaterFlow}.
 */
@Immutable
public final class BulkWriteComplete {

    private final String bulkWriteCorrelationId;

    private BulkWriteComplete(final String bulkWriteCorrelationId) {
        this.bulkWriteCorrelationId = bulkWriteCorrelationId;
    }

    /**
     * Creates a new BulkWriteComplete instance with the given {@code metadata}.
     *
     * @param bulkWriteCorrelationId the correlationId of the bulkWrite.
     * @return the instance.
     */
    public static BulkWriteComplete of(final String bulkWriteCorrelationId) {
        return new BulkWriteComplete(bulkWriteCorrelationId);
    }

    /**
     * @return the correlationId of the bulkWrite.
     */
    public String getBulkWriteCorrelationId() {
        return bulkWriteCorrelationId;
    }
}
