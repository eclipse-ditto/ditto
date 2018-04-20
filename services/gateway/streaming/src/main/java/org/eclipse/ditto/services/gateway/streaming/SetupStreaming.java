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
package org.eclipse.ditto.services.gateway.streaming;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * TODO doc
 */
public final class SetupStreaming implements Jsonifiable<JsonObject> {

    /**
     * JSON field containing the streaming type.
     */
    private static final JsonFieldDefinition<String> JSON_TYPE =
            JsonFactory.newStringFieldDefinition("type", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    /**
     * JSON field containing the streaming action (start|stop).
     */
    private static final JsonFieldDefinition<String> JSON_ACTION =
            JsonFactory.newStringFieldDefinition("action", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    /**
     * JSON field containing the optional filter for events.
     */
    private static final JsonFieldDefinition<String> JSON_EVENT_FILTER =
            JsonFactory.newStringFieldDefinition("eventFilter", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final String type;
    private final String action;
    @Nullable private final String eventFilter;

    private SetupStreaming(final String type, final String action, @Nullable final String eventFilter) {
        this.type = type;
        this.action = action;
        this.eventFilter = eventFilter;
    }

    /**
     *
     * @param jsonObject
     * @return
     * @throws org.eclipse.ditto.json.JsonException
     */
    public static SetupStreaming fromJson(final JsonObject jsonObject) {
        final String readType = jsonObject.getValueOrThrow(JSON_TYPE);
        final String readAction = jsonObject.getValueOrThrow(JSON_ACTION);
        final String readEventFilter = jsonObject.getValue(JSON_EVENT_FILTER).orElse(null);
        return new SetupStreaming(readType, readAction, readEventFilter);
    }

    public String getType() {
        return type;
    }

    public String getAction() {
        return action;
    }

    public Optional<String> getEventFilter() {
        return Optional.ofNullable(eventFilter);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder builder = JsonObject.newBuilder();
        builder.set(JSON_TYPE, type);
        builder.set(JSON_ACTION, action);
        if (eventFilter != null) {
            builder.set(JSON_EVENT_FILTER, eventFilter);
        }
        return builder.build();
    }
}
