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
package org.eclipse.ditto.services.utils.health;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.Jsonifiable;

public final class StatusDetailMessage implements Jsonifiable<JsonObject> {

    public enum Level {
        INFO, WARN, ERROR;

        public static final Level DEFAULT = INFO;
    }

    private final Level level;
    private final JsonValue message;

    private StatusDetailMessage(final Level level, final JsonValue message) {
        this.level = level;
        this.message = message;
    }

    public static StatusDetailMessage of(final Level level, final String message) {
        requireNonNull(level, "The Level must not be null!");
        requireNonNull(message, "The Message must not be null!");

        return new StatusDetailMessage(level, JsonValue.of(message));
    }

    public static StatusDetailMessage of(final Level level, final JsonValue message) {
        requireNonNull(level, "The Level must not be null!");
        requireNonNull(message, "The Message must not be null!");

        return new StatusDetailMessage(level, message);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(level.toString(), message);
        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@link StatusDetailMessage} from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @return the message.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static StatusDetailMessage fromJson(final JsonObject jsonObject) {
        if (jsonObject.getSize() != 1) {
            throw new JsonParseException("Message must contain exactly one field, but does not:\n" + jsonObject);
        }

        final JsonField messageField = jsonObject.iterator().next();
        final Level level = Level.valueOf(messageField.getKeyName());
        final JsonValue message = messageField.getValue();

        return of(level, message);
    }

    public Level getLevel() {
        return level;
    }

    public JsonValue getMessage() {
        return message;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final StatusDetailMessage that = (StatusDetailMessage) o;
        return level == that.level &&
                Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, message);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "level=" + level +
                ", message=" + message +
                ']';
    }

}
