/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.streaming;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;

/**
 * Abstract base implementation of {@link EntityIdWithRevision}.
 */
@Immutable
public abstract class AbstractEntityIdWithRevision implements EntityIdWithRevision {

    private final String id;
    private final long revision;

    protected AbstractEntityIdWithRevision(final String id, final long revision) {
        this.id = requireNonNull(id);
        this.revision = revision;
    }

    protected AbstractEntityIdWithRevision(final JsonObject jsonObject) {
        requireNonNull(jsonObject);

        this.id = jsonObject.getValueOrThrow(JsonFields.ID);
        this.revision = jsonObject.getValueOrThrow(JsonFields.REVISION);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public long getRevision() {
        return revision;
    }

    @Override
    public JsonObject toJson() {
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.ID, id)
                .set(JsonFields.REVISION, revision)
                .build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, revision);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "pmd:SimplifyConditional"})
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final AbstractEntityIdWithRevision that = (AbstractEntityIdWithRevision) obj;
        return Objects.equals(id, that.id) && Objects.equals(revision, that.revision);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "id=" + id + ", revision=" + revision + "]";
    }

}
