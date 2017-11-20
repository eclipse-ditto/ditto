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
package org.eclipse.ditto.services.thingsearch.persistence;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * Wrapper for an incoming {@link ThingEvent} which allows to combine the event with a {@link JsonSchemaVersion} for
 * further processing.
 *
 * @param <T> The type of the {@link ThingEvent}.
 */
@Immutable
public final class ProcessableThingEvent<T extends ThingEvent> {

    /**
     * The thing event.
     */
    private final T thingEvent;
    /**
     * The schema version with which the event was received.
     */
    private final JsonSchemaVersion jsonSchemaVersion;

    /**
     * Default constructor.
     *
     * @param thingEvent Thing event.
     * @param jsonSchemaVersion the schema version with which the event was received.
     */
    private ProcessableThingEvent(final T thingEvent, final JsonSchemaVersion jsonSchemaVersion) {
        this.thingEvent = thingEvent;
        this.jsonSchemaVersion = jsonSchemaVersion;
    }

    /**
     * Create a new ProcessableThingEvent.
     *
     * @param thingEvent the thing event.
     * @param jsonSchemaVersion the schema version with which the event was received.
     * @return the created ProcessableThingEvent.
     */
    public static <T extends ThingEvent> ProcessableThingEvent<T> newInstance(
            final T thingEvent,
            final JsonSchemaVersion jsonSchemaVersion) {
        checkNotNull(thingEvent, "thingEvent");
        checkNotNull(jsonSchemaVersion, "jsonSchemaVersion");
        return new ProcessableThingEvent<>(thingEvent, jsonSchemaVersion);
    }

    /**
     * Get the Thing Event.
     *
     * @return The thing event.
     */
    public final T getThingEvent() {
        return thingEvent;
    }

    /**
     * Get the schema version.
     *
     * @return the schema version.
     */
    public final JsonSchemaVersion getJsonSchemaVersion() {
        return jsonSchemaVersion;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ProcessableThingEvent<?> that = (ProcessableThingEvent<?>) o;
        return Objects.equals(thingEvent, that.thingEvent) &&
                jsonSchemaVersion == that.jsonSchemaVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(thingEvent, jsonSchemaVersion);
    }
}
