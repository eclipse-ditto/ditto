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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.json.FieldType.REGULAR;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_1;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
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
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Command which starts a stream from a persistence query actor based on the the passed in time span without
 * authorization. This command is sent only internally by the Ditto services, e.g. eventing or search, in order to
 * synchronize their search index.
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class SudoStreamModifiedEntities extends AbstractCommand<SudoStreamModifiedEntities>
        implements StreamingMessage {

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + SudoStreamModifiedEntities.class.getName();

    static final JsonFieldDefinition<String> JSON_START =
            JsonFactory.newStringFieldDefinition("payload/start", REGULAR, V_1, V_2);

    static final JsonFieldDefinition<String> JSON_END =
            JsonFactory.newStringFieldDefinition("payload/end", REGULAR, V_1, V_2);

    static final JsonFieldDefinition<Integer> JSON_BURST =
            JsonFactory.newIntFieldDefinition("payload/burst", REGULAR, V_1, V_2);

    static final JsonFieldDefinition<Long> JSON_TIMEOUT_MILLIS =
            JsonFactory.newLongFieldDefinition("payload/timeoutMillis", REGULAR, V_1, V_2);

    private final Instant start;

    private final Instant end;

    @Nullable
    private final Integer burst;

    @Nullable
    private final Long timeoutMillis;

    private SudoStreamModifiedEntities(final Instant start, final Instant end, final Integer burst,
            final Long timeoutMillis, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);

        this.start = checkNotNull(start, "start");
        this.end = checkNotNull(end, "end");
        this.burst = burst;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Creates a new {@code SudoStreamModifiedEntities} command.
     *
     * @param start Earliest timestamp of modifications to consider (inclusive).
     * @param end Latest timestamp of modifications to consider (exclusive).
     * @param burst the amount of elements to be collected per message
     * @param timeoutMillis maximum time to wait for acknowledgement of each stream element.
     * @param dittoHeaders the command headers of the request.
     * @return a command for retrieving modified Things without authorization.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SudoStreamModifiedEntities of(final Instant start, final Instant end, final Integer burst,
            final Long timeoutMillis, final DittoHeaders dittoHeaders) {
        return new SudoStreamModifiedEntities(start, end, burst, timeoutMillis, dittoHeaders);
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
            final Integer burst = jsonObject.getValue(JSON_BURST).orElse(null);
            final Long timeoutMillis = jsonObject.getValue(JSON_TIMEOUT_MILLIS).orElse(null);
            return SudoStreamModifiedEntities.of(start, end, burst, timeoutMillis, dittoHeaders);
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
     * Returns the streaming burst.
     *
     * @return number of elements to send per message.
     */
    public Optional<Integer> getBurst() {
        return Optional.ofNullable(burst);
    }

    /**
     * Returns the timeout in milliseconds.
     *
     * @return the timeout.
     */
    public Optional<Long> getTimeoutMillis() {
        return Optional.ofNullable(timeoutMillis);
    }


    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_START, start.toString(), predicate);
        jsonObjectBuilder.set(JSON_END, end.toString(), predicate);
        jsonObjectBuilder.set(JSON_BURST, burst, predicate);
        jsonObjectBuilder.set(JSON_TIMEOUT_MILLIS, timeoutMillis, predicate);
    }

    @Override
    public String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public SudoStreamModifiedEntities setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(start, end, burst, timeoutMillis, dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), start, end, burst, timeoutMillis);
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
                && Objects.equals(burst, that.burst)
                && Objects.equals(timeoutMillis, that.timeoutMillis)
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
                + ", end=" + end
                + ", burst=" + burst
                + ", timeoutMillis=" + timeoutMillis
                + "]";
    }

    @Override
    public String getId() {
        return "";
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return TYPE;
    }
}
