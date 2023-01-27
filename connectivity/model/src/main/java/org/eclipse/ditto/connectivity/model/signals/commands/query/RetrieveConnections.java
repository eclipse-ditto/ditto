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
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.WithSelectedFields;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Command that retrieves several {@link org.eclipse.ditto.connectivity.model.Connection}s based on the passed in list
 * of Connection IDs.
 *
 * @since 3.0.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ConnectivityCommand.TYPE_PREFIX, name = RetrieveConnections.NAME)
public final class RetrieveConnections extends AbstractCommand<RetrieveConnections>
        implements ConnectivityQueryCommand<RetrieveConnections>, WithSelectedFields {

    /**
     * Name of the "Retrieve Connections" command.
     */
    public static final String NAME = "retrieveConnections";

    /**
     * Type of this command.
     */
    public static final String TYPE = ConnectivityCommand.TYPE_PREFIX + NAME;
    static final JsonFieldDefinition<Boolean> JSON_IDS_ONLY =
            JsonFactory.newBooleanFieldDefinition("idsOnly", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<String> JSON_SELECTED_FIELDS =
            JsonFactory.newStringFieldDefinition("selectedFields", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final boolean idsOnly;
    @Nullable private final JsonFieldSelector selectedFields;

    private RetrieveConnections(final boolean idsOnly,
            @Nullable final JsonFieldSelector selectedFields,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.idsOnly = idsOnly;
        this.selectedFields = selectedFields;
    }

    /**
     * Returns a new instance of the retrieve connections command.
     *
     * @param dittoHeaders provide additional information regarding connections retrieval like a correlation ID.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnections newInstance(final boolean idsOnly, final DittoHeaders dittoHeaders) {

        return new RetrieveConnections(idsOnly, null, dittoHeaders);
    }

    /**
     * Returns a new instance of the retrieve connections command.
     *
     * @param dittoHeaders provide additional information regarding connections retrieval like a correlation ID.
     * @param selectedFields the fields of the JSON representation of the Connection to retrieve.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnections newInstance(final boolean idsOnly,
            @Nullable final JsonFieldSelector selectedFields,
            final DittoHeaders dittoHeaders) {

        return new RetrieveConnections(idsOnly, selectedFields, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnections} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveConnections fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnections} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveConnections fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final boolean idsOnly = jsonObject.getValueOrThrow(JSON_IDS_ONLY);
        final JsonFieldSelector extractedFieldSelector = jsonObject.getValue(JSON_SELECTED_FIELDS)
                .map(str -> JsonFactory.newFieldSelector(str, JsonFactory.newParseOptionsBuilder()
                        .withoutUrlDecoding()
                        .build()))
                .orElse(null);

        return new RetrieveConnections(idsOnly, extractedFieldSelector, dittoHeaders);
    }

    /**
     * Specifies if only connections ids are to be retrieved.
     *
     * @return if only connections ids are to be retrieved.
     */
    public boolean getIdsOnly() {
        return idsOnly;
    }

    @Override
    public Optional<JsonFieldSelector> getSelectedFields() {
        return Optional.ofNullable(selectedFields);
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public RetrieveConnections setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new RetrieveConnections(idsOnly, selectedFields, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/connections");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion jsonSchemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = jsonSchemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_IDS_ONLY, idsOnly, predicate);
        if (null != selectedFields) {
            jsonObjectBuilder.set(JSON_SELECTED_FIELDS, selectedFields.toString(), predicate);
        }
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final RetrieveConnections that = (RetrieveConnections) o;
        return Objects.equals(idsOnly, that.idsOnly) &&
                Objects.equals(selectedFields, that.selectedFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), idsOnly, selectedFields);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", idsOnly=" + idsOnly +
                ", selectedFields=" + selectedFields +
                "]";
    }

}
