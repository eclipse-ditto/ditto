/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model.signals.announcements;


import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.announcements.AbstractAnnouncement;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionIdInvalidException;
import org.eclipse.ditto.json.JsonExceptionBuilder;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Abstract superclass of connectivity announcements.
 *
 * @param <T> type of a concrete subclass.
 * @since 2.1.0
 */
public abstract class AbstractConnectivityAnnouncement<T extends AbstractConnectivityAnnouncement<T>>
        extends AbstractAnnouncement<T>
        implements ConnectivityAnnouncement<T> {

    private final ConnectionId connectionId;

    /**
     * Create a connectivity announcement object.
     *
     * @param connectionId the connection ID.
     * @param dittoHeaders the Ditto headers.
     */
    protected AbstractConnectivityAnnouncement(final ConnectionId connectionId, final DittoHeaders dittoHeaders) {
        super(dittoHeaders);
        this.connectionId = checkNotNull(connectionId, "connectionId");
    }

    protected static ConnectionId deserializeConnectionId(final JsonObject jsonObject) {
        final JsonFieldDefinition<String> fieldDefinition = ConnectivityAnnouncement.JsonFields.JSON_CONNECTION_ID;
        final String connectionIdJsonValue = jsonObject.getValueOrThrow(fieldDefinition);
        try {
            return ConnectionId.of(connectionIdJsonValue);
        } catch (final ConnectionIdInvalidException e) {
            throw getJsonParseException(fieldDefinition, ConnectionId.class, e);
        }
    }

    protected static JsonParseException getJsonParseException(final JsonFieldDefinition<?> fieldDefinition,
            final Class<?> targetClass, final Throwable cause) {
        return getJsonParseExceptionBuilder(fieldDefinition, targetClass, cause)
                .build();
    }

    protected static JsonExceptionBuilder<JsonParseException> getJsonParseExceptionBuilder(
            final JsonFieldDefinition<?> fieldDefinition, final Class<?> targetClass, final Throwable cause) {

        return JsonParseException.newBuilder()
                .message(MessageFormat.format("Failed to deserialize field <{0}> as {1}: {2}",
                        fieldDefinition.getPointer(),
                        targetClass.getName(),
                        cause.getMessage()))
                .cause(cause);
    }

    /**
     * Append {@code ConnectivityAnnouncement}-specific payload to the passed {@code jsonObjectBuilder}.
     *
     * @param jsonObjectBuilder the JsonObjectBuilder to add the payload to.
     * @param predicate the predicate to evaluate when adding the payload.
     */
    protected abstract void appendConnectivityAnnouncementPayload(JsonObjectBuilder jsonObjectBuilder,
            Predicate<JsonField> predicate);

    @Override
    public ConnectionId getEntityId() {
        return connectionId;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(ConnectivityAnnouncement.JsonFields.JSON_CONNECTION_ID, connectionId.toString(), predicate);
        appendConnectivityAnnouncementPayload(jsonObjectBuilder, predicate);
    }

    @Override
    public String toString() {
        return super.toString() + ", connectionId=" + connectionId;
    }

    @Override
    public boolean equals(final Object other) {
        if (super.equals(other)) {
            return Objects.equals(((AbstractConnectivityAnnouncement<?>) other).connectionId, connectionId);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, super.hashCode());
    }
}
