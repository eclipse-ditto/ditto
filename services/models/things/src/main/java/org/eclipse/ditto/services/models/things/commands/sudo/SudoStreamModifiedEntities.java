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
package org.eclipse.ditto.services.models.things.commands.sudo;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.model.base.json.FieldType.REGULAR;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_1;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;

/**
 * Command which retrieves several {@link ThingTag}s based on the the passed in time span without authorization. This
 * command is sent only internally by the Ditto services, e.g. eventing or search, in order to synchronize their Things
 * cache.
 */
@Immutable
public final class SudoStreamModifiedEntities extends AbstractCommand<SudoStreamModifiedEntities> implements
        SudoCommand<SudoStreamModifiedEntities> {

    /**
     * Name of this command.
     */
    public static final String NAME = "sudoStreamModifiedEntities";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_TIMESPAN =
            JsonFactory.newStringFieldDefinition("payload/timespan", REGULAR, V_1, V_2);

    static final JsonFieldDefinition<String> JSON_OFFSET =
            JsonFactory.newStringFieldDefinition("payload/offset", REGULAR, V_1, V_2);

    static final JsonFieldDefinition<Integer> JSON_ELEMENTS_PER_SECOND =
            JsonFactory.newIntFieldDefinition("payload/elementsPerSecond", REGULAR, V_1, V_2);

    static final JsonFieldDefinition<String> JSON_ELEMENT_RECIPIENT =
            JsonFactory.newStringFieldDefinition("payload/elementRecipient", REGULAR, V_1, V_2);

    static final JsonFieldDefinition<String> JSON_STATUS_RECIPIENT =
            JsonFactory.newStringFieldDefinition("payload/statusRecipient", REGULAR, V_1, V_2);


    private static final String NULL_OFFSET = "PT0M";

    private final Duration timespan;

    private final Duration offset;

    private final int elementsPerSecond;

    private final String elementRecipient;

    private final String statusRecipient;

    private SudoStreamModifiedEntities(final Duration timespan, final Duration offset, final int elementsPerSecond,
            final String elementRecipient, final String statusRecipient, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);

        this.timespan = requireNonNull(timespan, "The timespan must not be null!");
        this.offset = offset;
        this.elementRecipient = elementRecipient;
        this.statusRecipient = statusRecipient;
        this.elementsPerSecond = elementsPerSecond;
    }

    /**
     * Creates a new {@code SudoRetrieveModifiedThingTags} command.
     *
     * @param timespan the duration for which all modified things should be retrieved.
     * @param offset the duration for which the modified things should be skipped.
     * @param elementRecipient TODO
     * @param statusRecipient TODO
     * @param dittoHeaders the command headers of the request.
     * @return a command for retrieving modified Things without authorization.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoStreamModifiedEntities of(final Duration timespan, final Duration offset,
            final int elementsPerSecond, final String elementRecipient, final String statusRecipient,
            final DittoHeaders dittoHeaders) {
        return new SudoStreamModifiedEntities(timespan, offset, elementsPerSecond, elementRecipient,
                statusRecipient, dittoHeaders);
    }


    /**
     * Creates a new {@code SudoRetrieveModifiedThingTags} from a JSON string.
     *
     * @param jsonString the JSON string of which a new SudoRetrieveModifiedThingTags is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the SudoRetrieveModifiedThingTags which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws JsonParseException if the passed in {@code jsonString} does not contain a JSON object or if it is not
     * valid JSON.
     * @throws JsonMissingFieldException if the passed in {@code jsonString} was not in the expected format.
     */
    public static SudoStreamModifiedEntities fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SudoRetrieveModifiedThingTags} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new SudoRetrieveModifiedThingTags is to be created.
     * @param dittoHeaders the optional command headers of the request.
     * @return the SudoRetrieveModifiedThingTags which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static SudoStreamModifiedEntities fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        try {
            final Duration extractedTimespan = Duration.parse(jsonObject.getValueOrThrow(JSON_TIMESPAN));
            final Duration extractedOffset = Duration.parse(jsonObject.getValue(JSON_OFFSET).orElse(NULL_OFFSET));
            final int elementsPerSecond = jsonObject.getValueOrThrow(JSON_ELEMENTS_PER_SECOND);
            final String elementRecipient = jsonObject.getValueOrThrow(JSON_ELEMENT_RECIPIENT);
            final String statusRecipient = jsonObject.getValueOrThrow(JSON_STATUS_RECIPIENT);
            return SudoStreamModifiedEntities.of(extractedTimespan, extractedOffset, elementsPerSecond,
                    elementRecipient, statusRecipient, dittoHeaders);
        } catch (final DateTimeParseException e) {
            throw JsonParseException.newBuilder()
                    .message("The given timespan is no valid Duration.")
                    .description("The timespan must be given in the format 'PnDTnHnMn.nS'.")
                    .cause(e)
                    .build();
        }
    }

    /**
     * Returns the timespan for which all modified Things should be retrieved.
     *
     * @return the timespan.
     */
    public Duration getTimespan() {
        return timespan;
    }

    /**
     * Returns the offset before which all modified things should be retrieved.
     *
     * @return the offset.
     */
    public Duration getOffset() {
        return offset;
    }

    /**
     * Returns the streaming rate.
     *
     * @return Number of elements to stream per second.
     */
    public int getElementsPerSecond() {
        return elementsPerSecond;
    }

    /**
     * Returns the serialized actor reference to stream elements to.
     *
     * @return The actor reference.
     */
    public String getElementRecipient() {
        return elementRecipient;
    }

    /**
     * Returns the serialized actor reference to stream status messages to.
     */
    public String getStatusRecipient() {
        return statusRecipient;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_TIMESPAN, timespan.toString(), predicate);
        jsonObjectBuilder.set(JSON_OFFSET, offset.toString(), predicate);
        jsonObjectBuilder.set(JSON_ELEMENTS_PER_SECOND, elementsPerSecond, predicate);
        jsonObjectBuilder.set(JSON_ELEMENT_RECIPIENT, elementRecipient, predicate);
        jsonObjectBuilder.set(JSON_STATUS_RECIPIENT, statusRecipient, predicate);
    }

    @Override
    public SudoStreamModifiedEntities setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(timespan, offset, elementsPerSecond, elementRecipient, statusRecipient, dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), timespan, offset, elementsPerSecond, elementRecipient,
                statusRecipient);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "pmd:SimplifyConditional"})
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final SudoStreamModifiedEntities that = (SudoStreamModifiedEntities) obj;
        return that.canEqual(this) && Objects.equals(timespan, that.timespan) && Objects.equals(offset, that.offset)
                && Objects.equals(elementsPerSecond, that.elementsPerSecond)
                && Objects.equals(elementRecipient, that.elementRecipient)
                && Objects.equals(statusRecipient, that.statusRecipient)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoStreamModifiedEntities;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString()
                + ", timespan=" + timespan
                + ", offset= " + offset
                + ", elementsPerSecond= " + elementsPerSecond
                + ", elementRecipient=" + elementRecipient
                + ", statusRecipient=" + statusRecipient
                + "]";
    }

}
