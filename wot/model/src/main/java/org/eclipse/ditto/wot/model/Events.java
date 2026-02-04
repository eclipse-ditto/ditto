/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonObject;

/**
 * Events is a container for named {@link Event}s.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#eventaffordance">WoT TD EventAffordance</a>
 * @since 2.4.0
 */
public interface Events extends Map<String, Event>, Jsonifiable<JsonObject> {

    /**
     * Creates a new Events container from the specified JSON object.
     *
     * @param jsonObject the JSON object containing event definitions.
     * @return the Events container.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    static Events fromJson(final JsonObject jsonObject) {
        return of(jsonObject.stream().collect(Collectors.toMap(
                field -> field.getKey().toString(),
                field -> Event.fromJson(field.getKey().toString(), field.getValue().asObject()),
                (u, v) -> {
                    throw WotThingModelInvalidException.newBuilder(String.format("Events: Duplicate key %s", u))
                            .build();
                },
                LinkedHashMap::new)
        ));
    }

    /**
     * Creates a new Events container from the specified collection of events.
     *
     * @param events the collection of events.
     * @return the Events container.
     * @throws NullPointerException if {@code events} is {@code null}.
     */
    static Events from(final Collection<Event> events) {
        return of(events.stream().collect(Collectors.toMap(
                Event::getEventName,
                e -> e,
                (u, v) -> {
                    throw WotThingModelInvalidException.newBuilder(String.format("Events: Duplicate key %s", u))
                            .build();
                },
                LinkedHashMap::new)
        ));
    }

    /**
     * Creates a new Events container from the specified map of event name to event.
     *
     * @param events the map of events.
     * @return the Events container.
     * @throws NullPointerException if {@code events} is {@code null}.
     */
    static Events of(final Map<String, Event> events) {
        return new ImmutableEvents(events);
    }

    /**
     * Returns the event with the specified name if it exists.
     *
     * @param eventName the name of the event.
     * @return the optional event.
     */
    Optional<Event> getEvent(CharSequence eventName);

}
