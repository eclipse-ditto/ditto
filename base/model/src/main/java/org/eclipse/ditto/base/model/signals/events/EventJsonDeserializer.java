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
package org.eclipse.ditto.base.model.signals.events;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

/**
 * This class helps to deserialize JSON to a sub-class of {@link Event}. Hereby this class extracts the values which
 * are
 * common for all events. All remaining required values have to be extracted in
 * {@link EventJsonDeserializer.FactoryMethodFunction#create(long, java.time.Instant, org.eclipse.ditto.base.model.entity.metadata.Metadata)}.
 * There the actual event object is created, too.
 *
 * @param <T> the type of the Event.
 */
@Immutable
public final class EventJsonDeserializer<T extends Event<T>> {

    private final JsonObject jsonObject;
    private final String expectedType;

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
        final Long revision = jsonObject.getValue(EventsourcedEvent.JsonFields.REVISION).orElse(0L);

        final Instant timestamp = jsonObject.getValue(Event.JsonFields.TIMESTAMP.getPointer())
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(EventJsonDeserializer::tryToParseModified)
                .orElse(null);

        final Metadata metadata = jsonObject.getValue(Event.JsonFields.METADATA.getPointer())
                .filter(jsonValue -> !jsonValue.isNull() && jsonValue.isObject())
                .map(JsonValue::asObject)
                .map(Metadata::newMetadata)
                .orElse(null);

        return factoryMethodFunction.create(revision, timestamp, metadata);
    }

    private void validateEventType() {
        final String type = jsonObject.getValue(Event.JsonFields.TYPE)
                .orElseThrow(() -> new JsonMissingFieldException(Event.JsonFields.TYPE.getPointer()));

        if (!expectedType.equals(type)) {
            final String msgPattern = "Event JSON was not a <{0}> event but a <{1}>!";
            final String msg = MessageFormat.format(msgPattern, expectedType, type);
            throw new DittoJsonException(new JsonParseException(msg));
        }
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
    public interface FactoryMethodFunction<T extends Event<T>> {

        /**
         * Creates a {@code Event} with the help of the given arguments.
         *
         * @param revision the revision of the event or {@code 0L} if the event did not contain a revision.
         * @param timestamp the event's timestamp.
         * @param metadata the event's metadata.
         * @return the created event.
         */
        T create(long revision, @Nullable Instant timestamp, @Nullable Metadata metadata);

    }

}
