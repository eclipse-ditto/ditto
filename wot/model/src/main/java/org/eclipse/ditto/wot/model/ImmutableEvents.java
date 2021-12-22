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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of {@link Events}.
 */
@Immutable
final class ImmutableEvents extends AbstractMap<String, Event> implements Events {

    private final Map<String, Event> events;

    ImmutableEvents(final Map<String, Event> events) {
        this.events = checkNotNull(events, "events");
    }

    @Override
    public Optional<Event> getEvent(final CharSequence eventName) {
        return Optional.ofNullable(events.get(eventName.toString()));
    }

    @Override
    public Set<Entry<String, Event>> entrySet() {
        return events.entrySet();
    }

    @Override
    public JsonObject toJson() {
        return events.entrySet().stream()
                .map(e -> JsonField.newInstance(e.getKey(), e.getValue().toJson()))
                .collect(JsonCollectors.fieldsToObject());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableEvents that = (ImmutableEvents) o;
        return Objects.equals(events, that.events);
    }

    @Override
    public int hashCode() {
        return Objects.hash(events);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "events=" + events +
                "]";
    }
}
