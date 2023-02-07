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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.base.model.signals.commands.WithSelectedFields;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.WithConnectionId;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Command which retrieves a {@link org.eclipse.ditto.connectivity.model.Connection} of type 'hono'
 * after resolving its aliases and with its additional properties like header mappings and specific config.
 *
 * @since 3.2.0
 */
@Immutable
@JsonParsableCommand(typePrefix = ConnectivityCommand.TYPE_PREFIX, name = RetrieveResolvedHonoConnection.NAME)
public final class RetrieveResolvedHonoConnection extends AbstractCommand<RetrieveResolvedHonoConnection>
        implements ConnectivityQueryCommand<RetrieveResolvedHonoConnection>, WithConnectionId, WithSelectedFields,
        SignalWithEntityId<RetrieveResolvedHonoConnection> {

    /**
     * Name of this command.
     */
    public static final String NAME = "retrieveResolvedHonoConnection";

    /**
     * Type of this command.
     */
    public static final String TYPE = ConnectivityCommand.TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<String> JSON_SELECTED_FIELDS =
            JsonFactory.newStringFieldDefinition("selectedFields", FieldType.REGULAR,
                    JsonSchemaVersion.V_2);

    private final ConnectionId connectionId;
    @Nullable private final JsonFieldSelector selectedFields;

    private RetrieveResolvedHonoConnection(final ConnectionId connectionId,
            @Nullable final JsonFieldSelector selectedFields,
            final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.connectionId = connectionId;
        this.selectedFields = selectedFields;
    }

    /**
     * Returns a new instance of {@code RetrieveHonoConnection}.
     *
     * @param connectionId the identifier of the connection to be retrieved.
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveHonoConnection command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveResolvedHonoConnection of(final ConnectionId connectionId, final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "connectionId");
        return new RetrieveResolvedHonoConnection(connectionId, null, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code RetrieveResolvedHonoConnection}.
     *
     * @param connectionId the identifier of the connection to be retrieved.
     * @param selectedFields the fields of the JSON representation of the HonoConnection to retrieve.
     * @param dittoHeaders the headers of the request.
     * @return a new RetrieveResolvedHonoConnection command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveResolvedHonoConnection of(final ConnectionId connectionId,
            @Nullable final JsonFieldSelector selectedFields,
            final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "Connection ID");
        return new RetrieveResolvedHonoConnection(connectionId, selectedFields, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveHonoConnection} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be retrieved.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if connectionId is missing in the passed in {@code jsonString}
     */
    public static RetrieveResolvedHonoConnection fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveHonoConnection} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveResolvedHonoConnection fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrieveResolvedHonoConnection>(TYPE, jsonObject).deserialize(() -> {
            final String readConnectionId = jsonObject.getValueOrThrow(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID);
            final ConnectionId connectionId = ConnectionId.of(readConnectionId);
            final Optional<JsonFieldSelector> selectedFields = jsonObject.getValue(JSON_SELECTED_FIELDS)
                    .map(str -> JsonFactory.newFieldSelector(str, JsonFactory.newParseOptionsBuilder()
                            .withoutUrlDecoding()
                            .build()));

            return of(connectionId, selectedFields.orElse(null), dittoHeaders);
        });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID, String.valueOf(connectionId),
                predicate);
        if (null != selectedFields) {
            jsonObjectBuilder.set(JSON_SELECTED_FIELDS, selectedFields.toString(), predicate);
        }
    }

    @Override
    public ConnectionId getEntityId() {
        return connectionId;
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public Optional<JsonFieldSelector> getSelectedFields() {
        return Optional.ofNullable(selectedFields);
    }

    @Override
    public RetrieveResolvedHonoConnection setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, selectedFields, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveResolvedHonoConnection;
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
        final RetrieveResolvedHonoConnection that = (RetrieveResolvedHonoConnection) o;
        return Objects.equals(connectionId, that.connectionId) &&
                Objects.equals(selectedFields, that.selectedFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId, selectedFields);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connectionId=" + connectionId +
                ", selectedFields=" + selectedFields +
                "]";
    }

}
