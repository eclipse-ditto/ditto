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
package org.eclipse.ditto.services.models.things;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Represents the ID of a Thing with a revision of the Thing combined as its own type.
 */
@Immutable
public final class ThingTag implements Jsonifiable {

    private final String thingId;
    private final long revision;

    private ThingTag(final String thingId, final long revision) {
        this.thingId = thingId;
        this.revision = revision;
    }

    /**
     * Returns a new {@code ThingTag}.
     *
     * @param thingId the ID of the modified Thing.
     * @param revision the revision of the modified Thing.
     * @return a new ThingTag.
     */
    public static ThingTag of(final String thingId, final long revision) {
        requireNonNull(thingId, "The Thing ID must not be null!");

        return new ThingTag(thingId, revision);
    }

    /**
     * Creates a new {@code ThingTag} from a JSON string.
     *
     * @param jsonString the JSON string of which a new ThingTag is to be created.
     * @return the ThingTag which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} does not contain a JSON
     * object or if it is not valid JSON.
     * @throws JsonMissingFieldException if the passed in {@code jsonString} was not in the expected 'ThingTag' format.
     */
    public static ThingTag fromJson(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    /**
     * Creates a new {@code ThingTag} from a JSON string.
     *
     * @param jsonObject the JSON object of which a new ThingTag is to be created.
     * @return the ThingTag which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected 'ThingTag' format.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} is not valid JSON.
     */
    public static ThingTag fromJson(final JsonObject jsonObject) {
        final String extractedThingId = jsonObject.getValueOrThrow(JsonFields.ID);
        final Long extractedRevision = jsonObject.getValueOrThrow(JsonFields.REVISION);

        return of(extractedThingId, extractedRevision);
    }

    /**
     * Returns the ID of the modified Thing.
     *
     * @return the ID of the modified Thing.
     */
    public String getThingId() {
        return thingId;
    }

    /**
     * Returns the revision of the modified Thing.
     *
     * @return the revision of the modified Thing.
     */
    public long getRevision() {
        return revision;
    }

    @Override
    public JsonValue toJson() {
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.ID, thingId)
                .set(JsonFields.REVISION, revision)
                .build();
    }

    /**
     * Returns this tag as an identifier in the format {@code <thingId>:<revision>}.
     * @return the tag as an identifier
     */
    public String asIdentifierString() {
        return thingId + ":" + revision;
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingId, revision);
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
        final ThingTag that = (ThingTag) obj;
        return Objects.equals(thingId, that.thingId) && Objects.equals(revision, that.revision);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "thingId=" + thingId + ", revision=" + revision + "]";
    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a ThingTag.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * JSON field containing the ThingTag's ID.
         */
        public static final JsonFieldDefinition<String> ID = JsonFactory.newStringFieldDefinition("thingId");

        /**
         * JSON field containing the ThingTag's revision.
         */
        public static final JsonFieldDefinition<Long> REVISION = JsonFactory.newLongFieldDefinition("revision");

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
