/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.connectivity.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;

/**
 * Command which retrieves the logs of an established {@link org.eclipse.ditto.model.connectivity.Connection}.
 */
@Immutable
public final class RetrieveConnectionLogs extends AbstractCommand<RetrieveConnectionLogs>
        implements ConnectivityQueryCommand<RetrieveConnectionLogs> {

    /**
     * Name of this command.
     */
    public static final String NAME = "retrieveConnectionLogs";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final String connectionId;

    private RetrieveConnectionLogs(final String connectionId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.connectionId = connectionId;
    }

    /**
     * Returns a new instance of {@code RetrieveConnectionLogs}.
     *
     * @param connectionId the identifier of the connection to be retrieved.
     * @param dittoHeaders the headers of the request.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveConnectionLogs of(final String connectionId, final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "Connection ID");
        return new RetrieveConnectionLogs(connectionId, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnectionLogs} from a JSON string.
     *
     * @param jsonString the JSON string of which the command is to be retrieved.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveConnectionLogs fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConnectionLogs} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveConnectionLogs fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrieveConnectionLogs>(TYPE, jsonObject).deserialize(() -> {
            final String readConnectionId =
                    jsonObject.getValueOrThrow(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID);

            return of(readConnectionId, dittoHeaders);
        });
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID, connectionId, predicate);
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public RetrieveConnectionLogs setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof RetrieveConnectionLogs);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        if (!super.equals(o)) {return false;}
        final RetrieveConnectionLogs that = (RetrieveConnectionLogs) o;
        return Objects.equals(connectionId, that.connectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", connectionId=" + connectionId +
                "]";
    }

}
