/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Holds the data required to apply enforcement of Thing IDs. The target can be an arbitrary String containing the ID,
 * which must match against the passed filters (which may contain placeholders like {@code {{ thing:id }}} etc.
 */
@Immutable
final class ImmutableEnforcement implements Enforcement {

    private final String input;
    private final Set<String> matchers;

    // TODO move away from here
    @Nullable
    private final String errorMessage;

    private ImmutableEnforcement(final String input, final Set<String> matchers, @Nullable final String errorMessage) {
        this.input = input;
        this.matchers = Collections.unmodifiableSet(new HashSet<>(matchers));
        this.errorMessage = errorMessage;
    }

    /**
     * Create a ThingIdEnforcement with default error message.
     *
     * @param input input of the signal to apply Thing ID enforcement.
     * @param matchers matchers to match the input against.
     * @return ThingIdEnforcement with default error message.
     */
    static ImmutableEnforcement of(final String input, final Set<String> matchers) {
        return new ImmutableEnforcement(input, matchers, null);
    }

    /**
     * Create a ThingIdEnforcement with default error message.
     *
     * @param input input of the signal to apply Thing ID enforcement.
     * @param matchers matchers to match the input against.
     * @param errorMessage The error message if enforcement fails.
     * @return ThingIdEnforcement with default error message.
     */
    static ImmutableEnforcement of(final String input, final Set<String> matchers, final String errorMessage) {
        return new ImmutableEnforcement(input, matchers, errorMessage);
    }

    /**
     * Retrieve the string to match against matchers.
     *
     * @return the string that is supposed to match one of the matchers.
     */
    public String getInput() {
        return input;
    }

    /**
     * Retrieve set of matchers that are comared against the input string.
     * Filters contain placeholders ({@code {{ ... }}}).
     *
     * @return the matchers.
     */
    public Set<String> getMatchers() {
        return matchers;
    }

    /**
     * Return the error when ID enforcement fails.
     *
     * @param dittoHeaders Ditto headers of the signal subjected to ID enforcement.
     * @return the error.
     */
    public IdEnforcementFailedException getError(final DittoHeaders dittoHeaders) {
        final DittoRuntimeExceptionBuilder<IdEnforcementFailedException> builder;
        if (errorMessage == null) {
            builder = IdEnforcementFailedException.newBuilder(getInput());
        } else {
            builder = IdEnforcementFailedException.newBuilder().message(errorMessage);
        }
        return builder.dittoHeaders(dittoHeaders).build();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JsonFields.INPUT, input, predicate);
        jsonObjectBuilder.set(JsonFields.MATCHERS, matchers.stream()
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));

        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@code Source} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Enforcement to be created.
     * @return a new Source which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Enforcement fromJson(final JsonObject jsonObject) {
        final Set<String> readMatchers = jsonObject.getValue(JsonFields.MATCHERS)
                .map(array -> array.stream()
                        .map(JsonValue::asString)
                        .collect(Collectors.toSet())).orElse(Collections.emptySet());
        final String readInput =
                jsonObject.getValueOrThrow(JsonFields.INPUT);
        return new ImmutableEnforcement(readInput, readMatchers, null);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutableEnforcement that = (ImmutableEnforcement) o;
        return Objects.equals(input, that.input) &&
                Objects.equals(matchers, that.matchers) &&
                Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(input, matchers, errorMessage);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "input=" + input +
                ", matchers=" + matchers +
                ", errorMessage=" + errorMessage +
                "]";
    }
}