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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;

/**
 * This class helps to deserialize JSON to a sub-class of {@link Event}. Hereby this class extracts the values which
 * are
 * common for all events. All remaining required values have to be extracted in
 * {@link FactoryMethodFunction#create(long, Instant)}. There the actual event object is created, too.
 *
 * @param <T> the type of the Event.
 */
@Immutable
public final class EventJsonDeserializer<T extends Event> {

    private final JsonObject jsonObject;
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
        expectedType = type;
        eventTypePrefix = type.split(":")[0];
    }

    private static void validateType(final String type) {
        // for backward compatibility, extract the prefix for later use:
        if (!type.contains(":")) {
            final String msgPattern = "The type <{0}> does not contain a prefix separated by a colon (':')!";
            throw new IllegalArgumentException(MessageFormat.format(msgPattern, type));
        }
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

    /**
     * Partly deserializes the JSON which was given to this object's constructor. The factory method function which is
     * given to this method is responsible for creating the actual {@code Event}. This method receives the partly
     * deserialized values which can be completed by the implementor by further values if required.
     *
     * @param factoryMethodFunction creates the actual {@code Event} object.
     * @return the created event.
     * @throws NullPointerException if {@code factoryMethodFunction} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the JSON is invalid or if the event TYPE differs from the
     * expected one.
     */
    public T deserialize(final FactoryMethodFunction<T> factoryMethodFunction) {
        checkNotNull(factoryMethodFunction, "method for creating an event object");
        validateEventType();

        // added in V2 for V1, fallback to revision "0":
        final Long revision = jsonObject.getValue(Event.JsonFields.REVISION).orElse(0L);

        final Instant timestamp = jsonObject.getValue(Event.JsonFields.TIMESTAMP.getPointer())
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(EventJsonDeserializer::tryToParseModified)
                .orElse(null);

        return factoryMethodFunction.create(revision, timestamp);
    }

    private void validateEventType() {
        final String type = jsonObject.getValue(Event.JsonFields.TYPE)
                .orElseGet(() -> // if type was not present (was included in V2)
                        // take event instead and transform to V2 format, fail if "event" is not present, too
                        extractEventTypeV1()
                                .orElseThrow(() -> new JsonMissingFieldException(Event.JsonFields.TYPE.getPointer()))
                );

        if (!expectedType.equals(type)) {
            final String msgPattern = "Event JSON was not a <{0}> event but a <{1}>!";
            final String msg = MessageFormat.format(msgPattern, expectedType, type);
            throw new DittoJsonException(new JsonParseException(msg));
        }
    }

    @SuppressWarnings("squid:CallToDeprecatedMethod")
    private Optional<String> extractEventTypeV1() {
        return jsonObject.getValue(Event.JsonFields.ID)
                .map(event -> eventTypePrefix + ':' + event);
    }

    private static Instant tryToParseModified(final CharSequence dateTime) {
        try {
            return Instant.parse(dateTime);
        } catch (final DateTimeParseException e) {
            throw new JsonParseException("The JSON object's field '_modified' is not in ISO-8601 format as expected");
        }
    }

    /**
     * Represents a function that accepts three arguments to produce a {@code Event}. The arguments were extracted from
     * a given JSON beforehand.
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
         * @return the created event.
         */
        T create(long revision, @Nullable Instant timestamp);

    }

}
