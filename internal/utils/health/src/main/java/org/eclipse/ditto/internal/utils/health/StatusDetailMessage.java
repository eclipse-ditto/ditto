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
package org.eclipse.ditto.internal.utils.health;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * A StatusDetailMessage contains a {@link Level} (INFO, WARN or ERROR) associated with a {@code message}.
 */
public final class StatusDetailMessage implements Jsonifiable<JsonObject> {

    /**
     * The available levels of a StatusDetailMessage.
     * <p>
     * Order of Level entries is important - as {@code compareTo} of Enum uses defined order - first entry is less than
     * second entry.
     */
    public enum Level {
        INFO,
        WARN,
        ERROR;

        public static final Level DEFAULT = INFO;
    }

    private final Level level;
    private final JsonValue message;

    private StatusDetailMessage(final Level level, final JsonValue message) {
        this.level = checkNotNull(level, "level");
        this.message = checkNotNull(message, "message");
    }

    public static StatusDetailMessage of(final Level level, final String message) {
        return new StatusDetailMessage(level, JsonValue.of(message));
    }

    public static StatusDetailMessage of(final Level level, final JsonValue message) {
        return new StatusDetailMessage(level, checkNotNull(message, "message"));
    }

    public static StatusDetailMessage warn(@Nullable final Throwable throwable) {
        return of(Level.WARN, String.valueOf(throwable));
    }

    @Override
    public JsonObject toJson() {
        return JsonObject.newBuilder()
                .set(level.toString(), message)
                .build();
    }

    /**
     * Creates a new {@code StatusDetailMessage} from a JSON object.
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
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final StatusDetailMessage that = (StatusDetailMessage) o;
        return level == that.level && Objects.equals(message, that.message);
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
