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
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Contains the {@code status} and a {@code detail} in case the Status was not {@link HealthStatus.Status#UP} of a
 * single health "entry".
 */
@Immutable
public final class HealthStatus implements Jsonifiable<JsonObject> {

    /**
     * JSON field of the status.
     */
    public static final JsonFieldDefinition<String> JSON_KEY_STATUS = JsonFactory.newStringFieldDefinition("status");

    /**
     * JSON field of the detail.
     */
    public static final JsonFieldDefinition<String> JSON_KEY_DETAIL = JsonFactory.newStringFieldDefinition("detail");

    private final Status status;
    private final String detail;

    private HealthStatus(final Status status, final String detail) {
        this.status = status;
        this.detail = detail;
    }

    /**
     * Returns a new {@code HealthStatus} instance with the specified {@code status} and {@code detail}.
     *
     * @param status the status.
     * @return the HealthStatus instance.
     */
    public static HealthStatus of(final Status status) {
        return of(status, null);
    }

    /**
     * Returns a new {@code HealthStatus} instance with the specified {@code status} and {@code detail}.
     *
     * @param status the status.
     * @param detail the detail.
     * @return the HealthStatus instance.
     */
    public static HealthStatus of(final Status status, final String detail) {
        requireNonNull(status, "The Status must not be null!");
        return new HealthStatus(status, detail);
    }

    /**
     * Creates a new {@link HealthStatus} from a JSON string.
     *
     * @param jsonString the JSON string of which a new HealthStatus is to be created.
     * @return the HealthStatus which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} does not contain a JSON object
     * or if it is not valid JSON.
     * @throws JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected 'HealthStatus'
     * format.
     */
    public static HealthStatus fromJson(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    /**
     * Creates a new {@link HealthStatus} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new HealthStatus is to be created.
     * @return the HealthStatus which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} is not valid JSON.
     * @throws JsonMissingFieldException if the passed in {@code jsonObject} was not in the expected 'HealthStatus'
     * format.
     */
    public static HealthStatus fromJson(final JsonObject jsonObject) {
        final Status status = Status.valueOf(jsonObject.getValueOrThrow(JSON_KEY_STATUS));
        final String detail = jsonObject.getValue(JSON_KEY_DETAIL).orElse(null);

        return of(status, detail);
    }

    /**
     * Returns the {@code Status}.
     *
     * @return the status.
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Returns the optional detail.
     *
     * @return the detail or an empty optional.
     */
    public Optional<String> getDetail() {
        return Optional.ofNullable(detail);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JSON_KEY_STATUS, status.toString());
        if (null != detail) {
            jsonObjectBuilder.set(JSON_KEY_DETAIL, detail);
        }

        return jsonObjectBuilder.build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, detail);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final HealthStatus that = (HealthStatus) obj;
        return Objects.equals(status, that.status) && Objects.equals(detail, that.detail);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "status=" + status + ", detail=" + detail + "]";
    }

    /**
     * An enumeration of status codes for the health status in the style of spring boot actuator.
     */
    public enum Status {
        /**
         * Signals the application is in an unknown state.
         */
        UNKNOWN,

        /**
         * Signals the application is up and running.
         */
        UP,

        /**
         * Signals the application is down.
         */
        DOWN,

        /**
         * Signals the application is up, but cannot process requests.
         */
        OUT_OF_SERVICE
    }

}
