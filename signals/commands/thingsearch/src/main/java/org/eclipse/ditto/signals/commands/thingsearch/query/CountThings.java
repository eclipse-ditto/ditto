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
package org.eclipse.ditto.signals.commands.thingsearch.query;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command for counting things.
 */
@Immutable
public final class CountThings extends AbstractCommand<CountThings> implements ThingSearchQueryCommand<CountThings> {

    /**
     * Name of the command.
     */
    public static final String NAME = "countThings";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_FILTER =
            JsonFactory.newStringFieldDefinition("filter", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    @Nullable
    private final String filter;

    private CountThings(final DittoHeaders dittoHeaders, @Nullable final String filter) {
        super(TYPE, dittoHeaders);
        this.filter = filter;
    }

    /**
     * Returns a new instance of {@code CountThings}.
     *
     * @param filter the optional filter string
     * @param dittoHeaders the headers of the command.
     * @return a new command for counting Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CountThings of(@Nullable final String filter, final DittoHeaders dittoHeaders) {

        return new CountThings(dittoHeaders, filter);
    }

    /**
     * Returns a new instance of {@code CountThings}.
     *
     * @param dittoHeaders the headers of the command.
     * @return a new command for counting Things.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static CountThings of(final DittoHeaders dittoHeaders) {
        return new CountThings(dittoHeaders, null);
    }

    /**
     * Creates a new {@code CountThings} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected format.
     */
    public static CountThings fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code CountThings} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static CountThings fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<CountThings>(TYPE, jsonObject).deserialize(() -> {
            final String extractedFilter = jsonObject.getValue(JSON_FILTER).orElse(null);

            return new CountThings(dittoHeaders, extractedFilter);
        });
    }

    /**
     * Get the optional filter string.
     *
     * @return the optional filter string.
     */
    public Optional<String> getFilter() {
        return Optional.ofNullable(filter);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        if (filter != null) {
            jsonObjectBuilder.set(JSON_FILTER, filter, predicate);
        }
    }

    @Override
    public CountThings setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(filter, dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CountThings))
            return false;
        if (!super.equals(o))
            return false;
        final CountThings that = (CountThings) o;
        return Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), filter);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + "filter='" + filter + "']";
    }
}
