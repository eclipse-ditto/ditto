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
package org.eclipse.ditto.thingsearch.persistence.api.model;

import javax.annotation.concurrent.Immutable;

/**
 * Backend-neutral write model that deletes the search-index entry of a thing.
 *
 * @since 4.0.0
 */
@Immutable
public final class ThingDeleteModel extends AbstractWriteModel {

    private ThingDeleteModel(final Metadata metadata) {
        super(metadata);
    }

    /**
     * Create a delete write model for the thing identified by the given metadata.
     *
     * @param metadata the metadata of the thing to delete.
     * @return the delete write model.
     */
    public static ThingDeleteModel of(final Metadata metadata) {
        return new ThingDeleteModel(metadata);
    }

    @Override
    public ThingDeleteModel setMetadata(final Metadata metadata) {
        return new ThingDeleteModel(metadata);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }
}
