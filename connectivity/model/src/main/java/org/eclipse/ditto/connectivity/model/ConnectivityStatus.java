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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * An enumeration of status of connectivity resource.
 */
public enum ConnectivityStatus implements CharSequence, Jsonifiable<JsonObject> {

    /**
     * Indicates an open {@code Connection}.
     */
    OPEN("open"),

    /**
     * Indicates a closed {@code Connection}.
     */
    CLOSED("closed"),

    /**
     * Indicates a failed {@code Connection}.
     */
    FAILED("failed"),

    /**
     * Indicates a failed {@code Connection} due to wrong configuration.
     *
     * @since 2.1.0
     */
    MISCONFIGURED("misconfigured"),

    /**
     * Indicates an unknown status.
     */
    UNKNOWN("unknown");

    /**
     * JSON field of the name.
     */
    public static final JsonFieldDefinition<String> JSON_KEY_NAME =
            JsonFactory.newStringFieldDefinition("name");

    private final String name;

    ConnectivityStatus(final String name) {
        this.name = checkNotNull(name);
    }

    /**
     * Returns the {@code ConnectivityStatus} for the given {@code name} if it exists.
     *
     * @param name the name.
     * @return the ConnectivityStatus or an empty optional.
     */
    public static Optional<ConnectivityStatus> forName(final CharSequence name) {
        checkNotNull(name, "Name");
        return Arrays.stream(values())
                .filter(c -> c.name.contentEquals(name))
                .findFirst();
    }

    /**
     * Creates a new {@link ConnectivityStatus} from a JSON object.
     *
     * @param jsonObject the JSON object.
     * @return the created instance.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected format.
     * @throws IllegalArgumentException if the passed jsonObject contains a name that is no constant of this enum.
     */
    public static ConnectivityStatus fromJson(final JsonObject jsonObject) {
        final String name = checkNotNull(jsonObject, "jsonObject").getValueOrThrow(JSON_KEY_NAME);
        return ConnectivityStatus.valueOf(name.toUpperCase());
    }

    /**
     * Returns the name of this {@code ConnectivityStatus}.
     *
     * @return the name.
     */
    public String getName() {
        return name;
    }

    @Override
    public JsonObject toJson() {
        return JsonObject.newBuilder()
                .set(JSON_KEY_NAME, name()) // uses the Enum's name() and not the name!
                .build();
    }

    @Override
    public int length() {
        return name.length();
    }

    @Override
    public char charAt(final int index) {
        return name.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return name.subSequence(start, end);
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * @return true if the connectivity status indicates a failure and false if not.
     * @since 2.1.0
     */
    public boolean isFailure() {
        return this == FAILED || this == MISCONFIGURED;
    }

}
