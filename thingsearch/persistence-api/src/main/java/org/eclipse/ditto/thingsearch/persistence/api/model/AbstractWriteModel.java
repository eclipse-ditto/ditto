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

import java.util.Objects;

/**
 * Backend-neutral base of the search write-model hierarchy: a single change to be applied to the search
 * index for one thing, carrying the {@link Metadata} that identifies the thing and drives acknowledgements.
 * <p>
 * Unlike the backend-specific write models, this hierarchy holds NO storage driver types (no BSON, no SQL).
 * A backend translates a concrete write model into its own update representation.
 *
 * @since 4.0.0
 */
public abstract class AbstractWriteModel {

    private final Metadata metadata;

    protected AbstractWriteModel(final Metadata metadata) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    /**
     * @return the metadata of the thing this write model applies to.
     */
    public Metadata getMetadata() {
        return metadata;
    }

    /**
     * Return a copy of this write model with the given metadata.
     *
     * @param metadata the new metadata.
     * @return the copy.
     */
    public abstract AbstractWriteModel setMetadata(Metadata metadata);

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractWriteModel that = (AbstractWriteModel) o;
        return Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metadata);
    }

    @Override
    public String toString() {
        return "metadata=" + metadata;
    }
}
