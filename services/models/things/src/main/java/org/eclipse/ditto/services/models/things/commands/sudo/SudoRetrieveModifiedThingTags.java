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
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;

/**
 * Command which retrieves several {@link ThingTag}s based on the the passed in time span without authorization. This
 * command is sent only internally by the Ditto services, e.g. eventing or search, in order to synchronize their Things
 * cache.
 */
@Immutable
public final class SudoRetrieveModifiedThingTags extends AbstractCommand<SudoRetrieveModifiedThingTags> implements
        SudoCommand<SudoRetrieveModifiedThingTags> {

    /**
     * Name of this command.
     */
    public static final String NAME = "sudoRetrieveModifiedThingTags";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_TIMESPAN =
            JsonFactory.newStringFieldDefinition("payload/timespan", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_OFFSET =
            JsonFactory.newStringFieldDefinition("payload/offset", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private static final String NULL_OFFSET = "PT0M";

    private final Duration timespan;

    private final Duration offset;

    private SudoRetrieveModifiedThingTags(final Duration timespan, final Duration offset,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);

        this.timespan = requireNonNull(timespan, "The timespan must not be null!");
        this.offset = offset;
    }

    /**
     * Creates a new {@code SudoRetrieveModifiedThingTags} command.
     *
     * @param timespan the duration for which all modified things should be retrieved.
     * @param dittoHeaders the command headers of the request.
     * @return a command for retrieving modified Things without authorization.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoRetrieveModifiedThingTags of(final Duration timespan, final DittoHeaders dittoHeaders) {
        return new SudoRetrieveModifiedThingTags(timespan, Duration.parse(NULL_OFFSET), dittoHeaders);
    }


    /**
     * Creates a new {@code SudoRetrieveModifiedThingTags} command.
     *
     * @param timespan the duration for which all modified things should be retrieved.
     * @param offset the duration for which the modified things should be skipped.
     * @param dittoHeaders the command headers of the request.
     * @return a command for retrieving modified Things without authorization.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoRetrieveModifiedThingTags of(final Duration timespan, final Duration offset,
            final DittoHeaders dittoHeaders) {
        return new SudoRetrieveModifiedThingTags(timespan, offset, dittoHeaders);
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
    public static SudoRetrieveModifiedThingTags fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
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
    public static SudoRetrieveModifiedThingTags fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        try {
            final Duration extractedTimespan = Duration.parse(jsonObject.getValueOrThrow(JSON_TIMESPAN));
            final Duration extractedOffset = Duration.parse(jsonObject.getValue(JSON_OFFSET).orElse(NULL_OFFSET));
            return SudoRetrieveModifiedThingTags.of(extractedTimespan, extractedOffset, dittoHeaders);
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

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_TIMESPAN, timespan.toString(), predicate);
        jsonObjectBuilder.set(JSON_OFFSET, offset.toString(), predicate);
    }

    @Override
    public SudoRetrieveModifiedThingTags setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(timespan, offset, dittoHeaders);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(timespan);
        result = prime * result + Objects.hashCode(offset);
        return result;
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
        final SudoRetrieveModifiedThingTags that = (SudoRetrieveModifiedThingTags) obj;
        return that.canEqual(this) && Objects.equals(timespan, that.timespan) && Objects.equals(offset, that.offset)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoRetrieveModifiedThingTags;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", timespan=" + timespan + ", offset= " + offset
                + "]";
    }

}
