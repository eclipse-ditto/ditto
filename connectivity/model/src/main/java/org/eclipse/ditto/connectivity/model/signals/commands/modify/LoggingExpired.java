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

package org.eclipse.ditto.connectivity.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;

/**
 * Command that will enable logging in a {@link org.eclipse.ditto.connectivity.model.Connection}.
 */
@Immutable
@JsonParsableCommand(typePrefix = ConnectivityCommand.TYPE_PREFIX, name = LoggingExpired.NAME)
public final class LoggingExpired extends AbstractCommand<LoggingExpired>
        implements ConnectivityModifyCommand<LoggingExpired> {

    /**
     * Name of this command.
     */
    public static final String NAME = "loggingExpired";

    /**
     * Type of this command.
     */
    public static final String TYPE = ConnectivityCommand.TYPE_PREFIX + NAME;

    private final ConnectionId connectionId;

    private LoggingExpired(final ConnectionId connectionId, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.connectionId = connectionId;
    }

    /**
     * Creates a new instance of {@code LoggingExpired}.
     *
     * @param connectionId the connection for which logging should be enabled.
     * @return a new instance of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static LoggingExpired of(final ConnectionId connectionId) {
        return of(connectionId, DittoHeaders.empty());
    }

    /**
     * Creates a new instance of {@code LoggingExpired}.
     *
     * @param connectionId the connection for which logging should be enabled.
     * @return a new instance of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static LoggingExpired of(final ConnectionId connectionId, final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "Connection ID");
        checkNotNull(dittoHeaders, "dittoHeaders");
        return new LoggingExpired(connectionId, dittoHeaders);
    }

    /**
     * Creates a new {@code LoggingExpired} from a JSON string.
     *
     * @param jsonString the JSON containing the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static LoggingExpired fromJson(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    /**
     * Creates a new {@code LoggingExpired} from a JSON object.
     *
     * @param jsonObject the JSON containing the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static LoggingExpired fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<LoggingExpired>(TYPE, jsonObject).deserialize(() -> {
            final String readConnectionId =
                    jsonObject.getValueOrThrow(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID);
            final ConnectionId connectionId = ConnectionId.of(readConnectionId);

            return of(connectionId, dittoHeaders);
        });
    }

    public static LoggingExpired fromJson(final JsonObject jsonObject) {
        return fromJson(jsonObject, DittoHeaders.empty());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID, String.valueOf(connectionId),
                predicate);

    }

    @Override
    public ConnectionId getEntityId() {
        return connectionId;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public LoggingExpired setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof LoggingExpired);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final LoggingExpired that = (LoggingExpired) o;
        return Objects.equals(connectionId, that.connectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId);
    }

}
