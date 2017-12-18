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
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Represents the ID of an entity with a revision of the entity.
 */
@Immutable
public final class EntityIdWithRevision implements Jsonifiable, StreamingMessage {

    /**
     * Type of this message.
     */
    public static final String TYPE = TYPE_PREFIX + EntityIdWithRevision.class.getName();

    private final String id;
    private final long revision;

    private EntityIdWithRevision(final String id, final long revision) {
        this.id = id;
        this.revision = revision;
    }

    /**
     * Returns a new {@code EntityIdWithRevision}.
     *
     * @param id the ID of the entity.
     * @param revision the revision of the entity.
     * @return a new EntityIdWithRevision.
     */
    public static EntityIdWithRevision of(final String id, final long revision) {
        requireNonNull(id, "The entity ID must not be null!");

        return new EntityIdWithRevision(id, revision);
    }

    /**
     * Creates a new {@code EntityIdWithRevision} from a JSON string.
     *
     * @param jsonString the JSON string of which a new EntityIdWithRevision is to be created.
     * @return the EntityIdWithRevision which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} does not contain a JSON
     * object or if it is not valid JSON.
     * @throws JsonMissingFieldException if the passed in {@code jsonString} was not in the expected
     * 'EntityIdWithRevision' format.
     */
    public static EntityIdWithRevision fromJson(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    /**
     * Creates a new {@code EntityIdWithRevision} from a JSON string.
     *
     * @param jsonObject the JSON object of which a new EntityIdWithRevision is to be created.
     * @return the EntityIdWithRevision which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected
     * 'EntityIdWithRevision' format.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} is not valid JSON.
     */
    public static EntityIdWithRevision fromJson(final JsonObject jsonObject) {
        final String extractedEntityId = jsonObject.getValueOrThrow(JsonFields.ID);
        final Long extractedRevision = jsonObject.getValueOrThrow(JsonFields.REVISION);

        return of(extractedEntityId, extractedRevision);
    }

    /**
     * Returns the ID of the modified entity.
     *
     * @return the ID of the modified entity.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the revision of the modified entity.
     *
     * @return the revision of the modified entity.
     */
    public long getRevision() {
        return revision;
    }

    @Override
    public JsonValue toJson() {
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.TYPE, TYPE)
                .set(JsonFields.ID, id)
                .set(JsonFields.REVISION, revision)
                .build();
    }

    /**
     * Returns this tag as an identifier in the format {@code <id>:<revision>}.
     *
     * @return the tag as an identifier
     */
    public String asIdentifierString() {
        return id + ":" + revision;
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
        final EntityIdWithRevision that = (EntityIdWithRevision) obj;
        return Objects.equals(id, that.id) && Objects.equals(revision, that.revision);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "id=" + id + ", revision=" + revision + "]";
    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a EntityIdWithRevision.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * JSON field containing the message's ID.
         */
        public static final JsonFieldDefinition<String> ID = JsonFactory.newStringFieldDefinition("id");

        /**
         * JSON field containing the message's revision.
         */
        public static final JsonFieldDefinition<Long> REVISION = JsonFactory.newLongFieldDefinition("revision");

        /**
         * JSON field containing the message's type.
         */
        public static final JsonFieldDefinition<String> TYPE = Command.JsonFields.TYPE;

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
