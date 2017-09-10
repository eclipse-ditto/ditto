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
package org.eclipse.ditto.signals.events.base;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectReader;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonReader;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;

/**
 * This class helps to deserialize JSON to a sub-class of {@link Event}. Hereby this class extracts the values which
 * are
 * common for all events. All remaining required values have to be extracted in
 * {@link FactoryMethodFunction#create(long, Instant, JsonObjectReader)}. There the actual event object is created, too.
 *
 * @param <T> the type of the Event.
 */
@Immutable
public final class EventJsonDeserializer<T extends Event> {

    private final JsonObject jsonObject;
    private final JsonObjectReader jsonObjectReader;
    private final String expectedType;
    private final String eventTypePrefix;

    /**
     * Constructs a new {@code EventJsonDeserializer} object.
     *
     * @param type the type of the target event of deserialization.
     * @param jsonObject the JSON object to deserialized.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code type} is empty or does not contain a type prefix.
     */
    public EventJsonDeserializer(final String type, final JsonObject jsonObject) {
        validateType(argumentNotEmpty(type, "event type"));
        checkNotNull(jsonObject, "JSON object to be deserialized");

        this.jsonObject = jsonObject;
        jsonObjectReader = JsonReader.from(jsonObject);
        expectedType = type;
        eventTypePrefix = type.split(":")[0];
    }

    /**
     * Constructs a new {@code EventJsonDeserializer} object.
     *
     * @param type the type of the event to deserialize to.
     * @param jsonString the JSON string to be deserialized.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonString} does not contain a valid JSON object.
     */
    public EventJsonDeserializer(final String type, final String jsonString) {
        this(type, JsonFactory.newObject(jsonString));
    }

    private void validateType(final String type) {
        // for backward compatibility, extract the prefix for later use:
        if (!type.contains(":")) {
            throw new IllegalArgumentException(
                    MessageFormat.format("The type ''{0}'' does not contain a prefix separated by a colon (':').",
                            type));
        }
    }

    /**
     * Partly deserializes the JSON which was given to this object's constructor. The factory method function which is
     * given to this method is responsible for creating the actual {@code Event}. This method receives the partly
     * deserialized values as well as the {@link JsonReader} for the JSON to obtain further values if required.
     *
     * @param factoryMethodFunction creates the actual {@code Event} object.
     * @return the created event.
     * @throws NullPointerException if {@code factoryMethodFunction} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the JSON is invalid or if the event TYPE differs from the expected one.
     */
    public T deserialize(final FactoryMethodFunction<T> factoryMethodFunction) {
        checkNotNull(factoryMethodFunction, "method for creating an event object");
        validateEventType();

        // added in V2 for V1, fallback to revision "0":
        final Long revision = jsonObject.getValue(Event.JsonFields.REVISION).map(JsonValue::asLong).orElse(0L);

        final Instant timestamp = jsonObject.getValue(Event.JsonFields.TIMESTAMP)
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(this::tryToParseModified)
                .orElse(null);

        return factoryMethodFunction.create(revision, timestamp, jsonObjectReader);
    }

    private void validateEventType() {
        final Optional<String> typeOpt = jsonObject.getValue(Event.JsonFields.TYPE).map(JsonValue::asString);
        final Optional<String> eventOpt = jsonObject.getValue(Event.JsonFields.ID).map(JsonValue::asString);
        final String type = typeOpt.orElseGet(() -> // if type was not present (was included in V2)
                eventOpt // take "event" instead
                        .map(event -> eventTypePrefix + ':' + event) // and transform to V2 format
                        .orElseThrow(() -> JsonMissingFieldException.newBuilder() // fail if "event" also is not present
                                .fieldName(Event.JsonFields.TYPE.getPointer().toString()).build()));

        if (!expectedType.equals(type)) {
            final String msg =
                    MessageFormat.format("Event JSON was not a ''{0}'' event but a ''{1}''!", expectedType, type);
            final JsonParseException jsonParseException = new JsonParseException(msg);
            throw new DittoJsonException(jsonParseException);
        }
    }

    private Instant tryToParseModified(final String dateTime) {
        try {
            return Instant.parse(dateTime);
        } catch (final DateTimeParseException e) {
            throw new JsonParseException("The JSON object's field '_modified' is not in ISO-8601 format as expected");
        }
    }

    /**
     * Represents a function that accepts three arguments to produce a {@code Event}. The arguments were extracted from
     * a
     * given JSON beforehand.
     *
     * @param <T> the type of the result of the function.
     */
    @FunctionalInterface
    public interface FactoryMethodFunction<T extends Event> {

        /**
         * Creates a {@code Event} with the help of the given arguments.
         *
         * @param revision the revision of the Entity.
         * @param timestamp the event's timestamp.
         * @param jsonObjectReader the reader which was initialized with the JSON to be deserialized. It can be used to
         * obtain further values from JSON.
         * @return the created event.
         */
        T create(long revision, Instant timestamp, JsonObjectReader jsonObjectReader);
    }

}
