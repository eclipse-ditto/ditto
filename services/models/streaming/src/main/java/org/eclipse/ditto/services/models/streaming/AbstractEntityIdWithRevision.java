/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.models.streaming;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

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
    public JsonValue toJson() {
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
