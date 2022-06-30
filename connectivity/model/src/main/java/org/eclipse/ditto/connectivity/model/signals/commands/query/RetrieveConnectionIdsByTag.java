/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model.signals.commands.query;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Command which retrieves all {@link org.eclipse.ditto.connectivity.model.ConnectionId}s of connections that have a tag
 * which matches {@link #tag}.
 *
 * @since 3.0.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ConnectivityCommand.TYPE_PREFIX, name = RetrieveConnectionIdsByTag.NAME)
public final class RetrieveConnectionIdsByTag extends AbstractCommand<RetrieveConnectionIdsByTag>
        implements ConnectivityQueryCommand<RetrieveConnectionIdsByTag> {

    private static final JsonFieldDefinition<String> JSON_TAG =
            JsonFieldDefinition.ofString("tag", FieldType.REGULAR, JsonSchemaVersion.V_2);

    public static final String NAME = "retrieveConnectionIdsByTag";

    public static final String TYPE = ConnectivityCommand.TYPE_PREFIX + NAME;

    private final String tag;


    private RetrieveConnectionIdsByTag(final String tag, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.tag = tag;
    }

    /**
     * Returns a new instance of {@code RetrieveConnectionIdsByTag}.
     *
     * @param tag the tag for which the filtering should be applied.
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveConnectionIdsByTag command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionIdsByTag of(final String tag, final DittoHeaders dittoHeaders) {
        return new RetrieveConnectionIdsByTag(tag, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnectionIdsByTag} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be retrieved.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveConnectionIdsByTag fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnectionIdsByTag} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveConnectionIdsByTag fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrieveConnectionIdsByTag>(TYPE, jsonObject).deserialize(
                () -> {
                    final String tag = jsonObject.getValueOrThrow(JSON_TAG);
                    return of(tag, dittoHeaders);
                });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        jsonObjectBuilder.set(JSON_TAG, tag, thePredicate);
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public RetrieveConnectionIdsByTag setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(tag, dittoHeaders);
    }

    public String getTag() {
        return tag;
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveConnectionIdsByTag;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final RetrieveConnectionIdsByTag that = (RetrieveConnectionIdsByTag) o;
        return Objects.equals(tag, that.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), tag);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", tag=" + tag +
                "]";
    }

}
