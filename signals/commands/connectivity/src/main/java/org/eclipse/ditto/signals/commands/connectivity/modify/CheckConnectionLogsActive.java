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

package org.eclipse.ditto.signals.commands.connectivity.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommand;

/**
 * Command that will enable logging in a {@link org.eclipse.ditto.model.connectivity.Connection}.
 */
@Immutable
@JsonParsableCommand(typePrefix = CheckConnectionLogsActive.TYPE_PREFIX, name = CheckConnectionLogsActive.NAME)
public final class CheckConnectionLogsActive extends AbstractCommand<CheckConnectionLogsActive>
        implements ConnectivityModifyCommand<CheckConnectionLogsActive> {

    /**
     * Name of this command.
     */
    public static final String NAME = "checkConnectionLogsActive";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final String connectionId;
    private final Instant timestamp;

    private CheckConnectionLogsActive(final String connectionId,
            final Instant timestamp) {
        super(TYPE, DittoHeaders.empty());
        this.connectionId = connectionId;
        this.timestamp = timestamp;
    }

    private CheckConnectionLogsActive(final String connectionId,
            final Instant timestamp, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.connectionId = connectionId;
        this.timestamp = timestamp;
    }

    /**
     * Creates a new instance of {@code CheckConnectionLogsActive}.
     *
     * @param connectionId the connection for which logging should be enabled.
     * @return a new instance of the command.
     * @throws java.lang.NullPointerException if any argument is {@code null}.
     */
    public static CheckConnectionLogsActive of(final String connectionId,
            final Instant timestamp) {
        checkNotNull(connectionId, "Connection ID");
        checkNotNull(timestamp, "timestamp");
        return new CheckConnectionLogsActive(connectionId, timestamp);
    }

    /**
     * Creates a new instance of {@code CheckConnectionLogsActive}.
     *
     * @param connectionId the connection for which logging should be enabled.
     * @return a new instance of the command.
     * @throws java.lang.NullPointerException if any argument is {@code null}.
     */
    public static CheckConnectionLogsActive of(final String connectionId,
            final Instant timestamp, final DittoHeaders dittoHeaders) {
        checkNotNull(connectionId, "Connection ID");
        checkNotNull(dittoHeaders, "dittoHeaders");
        checkNotNull(timestamp, "timestamp");
        return new CheckConnectionLogsActive(connectionId, timestamp, dittoHeaders);
    }

    /**
     * Creates a new {@code CheckConnectionLogsActive} from a JSON string.
     *
     * @param jsonString the JSON containing the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static CheckConnectionLogsActive fromJson(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    /**
     * Creates a new {@code CheckConnectionLogsActive} from a JSON object.
     *
     * @param jsonObject the JSON containing the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static CheckConnectionLogsActive fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<CheckConnectionLogsActive>(TYPE, jsonObject).deserialize(() -> {
            final String readConnectionId =
                    jsonObject.getValueOrThrow(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID);
            final Instant readTimeStamp =
                    getTimestampAsInstant(
                            jsonObject.getValueOrThrow(ConnectivityModifyCommand.JsonFields.JSON_TIMESTAMP));
            return of(readConnectionId, readTimeStamp, dittoHeaders);
        });
    }

    public static CheckConnectionLogsActive fromJson(final JsonObject jsonObject) {
        return new CommandJsonDeserializer<CheckConnectionLogsActive>(TYPE, jsonObject).deserialize(() -> {
            final String readConnectionId =
                    jsonObject.getValueOrThrow(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID);
            final Instant readTimeStamp =
                    getTimestampAsInstant(
                            jsonObject.getValueOrThrow(ConnectivityModifyCommand.JsonFields.JSON_TIMESTAMP));
            return of(readConnectionId, readTimeStamp);
        });
    }

    private static Instant getTimestampAsInstant(final String value) {
        try {
            return Instant.parse(value);
        } catch (final JsonParseException e) {
            throw new DittoJsonException(e);
        }
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ConnectivityCommand.JsonFields.JSON_CONNECTION_ID, connectionId, predicate);
        jsonObjectBuilder.set(ConnectivityCommand.JsonFields.JSON_TIMESTAMP, timestamp.toString(), predicate);

    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    public Instant getTimestamp() {
        return this.timestamp;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public CheckConnectionLogsActive setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(connectionId, timestamp, dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof CheckConnectionLogsActive);
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
        final CheckConnectionLogsActive that = (CheckConnectionLogsActive) o;
        return Objects.equals(connectionId, that.connectionId) &&
                Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId, timestamp);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connectionId=" + connectionId +
                ", timestamp=" + timestamp +
                "]";
    }
}
