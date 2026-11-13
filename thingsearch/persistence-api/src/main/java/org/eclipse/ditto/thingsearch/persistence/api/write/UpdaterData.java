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

import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;

/**
 * Backend-neutral input element of the search updater flow: the current backend-neutral write model to apply
 * plus the previously-applied backend-neutral write model for the thing (used to compute incremental updates).
 * <p>
 * {@code writeModel} is the CURRENT neutral write model to persist &mdash; already post-enforcement and post
 * neutral-mapper. {@code lastWriteModel} is the previously-applied neutral write model, kept so a per-backend
 * {@link SearchUpdaterFlow} implementation (e.g. {@code MongoSearchUpdaterFlow}) can compute an incremental
 * update. Enforcement stays OUTSIDE the per-backend write-execution seam.
 *
 * @param writeModel the current neutral write model to persist.
 * @param lastWriteModel the previously-applied neutral write model of the thing's search-index entry.
 * @since 4.0.0
 */
public record UpdaterData(AbstractWriteModel writeModel, AbstractWriteModel lastWriteModel) {}
