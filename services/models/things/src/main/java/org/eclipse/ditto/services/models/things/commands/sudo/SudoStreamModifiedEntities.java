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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.json.FieldType.REGULAR;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_1;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;

import java.time.Instant;
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

    static final JsonFieldDefinition<String> JSON_START =
            JsonFactory.newStringFieldDefinition("payload/start", REGULAR, V_1, V_2);

    static final JsonFieldDefinition<String> JSON_END =
            JsonFactory.newStringFieldDefinition("payload/end", REGULAR, V_1, V_2);

    static final JsonFieldDefinition<Integer> JSON_RATE =
            JsonFactory.newIntFieldDefinition("payload/rate", REGULAR, V_1, V_2);

    private final Instant start;

    private final Instant end;

    private final int rate;

    private SudoStreamModifiedEntities(final Instant start, final Instant end, final int rate,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);

        this.start = checkNotNull(start, "start");
        this.end = checkNotNull(end, "end");
        this.rate = rate;
    }

    /**
     * Creates a new {@code SudoStreamModifiedEntities} command.
     *
     * @param start Earliest timestamp of modifications to consider.
     * @param end Latest timestamp of modifications to consider.
     * @param dittoHeaders the command headers of the request.
     * @return a command for retrieving modified Things without authorization.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoStreamModifiedEntities of(final Instant start, final Instant end, final int rate,
            final DittoHeaders dittoHeaders) {
        return new SudoStreamModifiedEntities(start, end, rate, dittoHeaders);
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
            final Instant start = Instant.parse(jsonObject.getValueOrThrow(JSON_START));
            final Instant end = Instant.parse(jsonObject.getValueOrThrow(JSON_END));
            final int rate = jsonObject.getValueOrThrow(JSON_RATE);
            return SudoStreamModifiedEntities.of(start, end, rate, dittoHeaders);
        } catch (final DateTimeParseException e) {
            throw JsonParseException.newBuilder()
                    .message("A given instant is not a valid timestamp.")
                    .description(jsonObject.toString())
                    .cause(e)
                    .build();
        }
    }

    /**
     * Returns the earliest timestamp of modifications to consider.
     *
     * @return the starting timestamp.
     */
    public Instant getStart() {
        return start;
    }

    /**
     * Returns the latest timestamp of modifications to consider.
     *
     * @return the end instance.
     */
    public Instant getEnd() {
        return end;
    }

    /**
     * Returns the streaming rate.
     *
     * @return Number of elements to stream per second.
     */
    public int getRate() {
        return rate;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_START, start.toString(), predicate);
        jsonObjectBuilder.set(JSON_END, end.toString(), predicate);
        jsonObjectBuilder.set(JSON_RATE, rate, predicate);
    }

    @Override
    public SudoStreamModifiedEntities setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(start, end, rate, dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), start, end, rate);
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
        return that.canEqual(this)
                && Objects.equals(start, that.start)
                && Objects.equals(end, that.end)
                && Objects.equals(rate, that.rate)
                && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof SudoStreamModifiedEntities;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString()
                + ", start=" + start
                + ", end= " + end
                + ", rate= " + rate
                + "]";
    }

}
