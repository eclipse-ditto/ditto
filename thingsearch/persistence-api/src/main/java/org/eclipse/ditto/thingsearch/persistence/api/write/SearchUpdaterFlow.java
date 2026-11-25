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
package org.eclipse.ditto.thingsearch.persistence.api.write;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Flow;

/**
 * Backend-neutral seam producing the stream stage that applies search write models against the active
 * backend. A backend implements {@link #create()} with its own bulk-write logic (Mongo: an ordered/unordered
 * bulk write; PostgreSQL: batched JSONB upserts). Consumed by the search updater stream.
 *
 * @since 4.0.0
 */
public interface SearchUpdaterFlow {

    /**
     * @return the flow that maps each {@link UpdaterData} element to the {@link UpdaterResult} of applying it.
     */
    Flow<UpdaterData, UpdaterResult, NotUsed> create();
}
