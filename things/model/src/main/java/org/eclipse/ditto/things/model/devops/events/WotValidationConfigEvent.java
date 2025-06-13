/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model.devops.events;

import java.util.function.Predicate;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Interface for all WoT validation config events.
 *
 * @param <T> the type of the implementing class.
 * @since 3.8.0
 */
public interface WotValidationConfigEvent<T extends WotValidationConfigEvent<T>>
        extends Event<T>, EventsourcedEvent<T>, Jsonifiable.WithPredicate<JsonObject, JsonField> {

    /**
     * Type prefix of WoT validation config events.
     */
    String TYPE_PREFIX = "wot.validation.config." + TYPE_QUALIFIER + ":";

    /**
     * Resource type of WoT validation config events.
     */
    String RESOURCE_TYPE = "wot-validation-config";

    /**
     * Returns the entity ID of this event.
     *
     * @return the entity ID.
     */
    @Override
    WotValidationConfigId getEntityId();

    /**
     * Returns the type of this event.
     *
     * @return the type.
     */
    @Override
    String getType();

    /**
     * Sets the Ditto headers of this event.
     *
     * @param dittoHeaders the Ditto headers to set.
     * @return a copy of this event with the given Ditto headers.
     */
    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * Returns a JSON representation of this event.
     *
     * @return the JSON representation.
     */
    @Override
    default JsonObject toJson() {
        return toJson(JsonSchemaVersion.LATEST, FieldType.notHidden());
    }

    /**
     * Returns a JSON representation of this event.
     *
     * @param schemaVersion the JSON schema version to use.
     * @return the JSON representation.
     */
    default JsonObject toJson(final JsonSchemaVersion schemaVersion) {
        return toJson(schemaVersion, FieldType.notHidden());
    }

    /**
     * Returns all fields of this event matching the given predicate.
     *
     * @param predicate the predicate to apply to each field when building the JSON object.
     * @return a JSON object representation of this event including all fields matching the predicate.
     */
    @Override
    default JsonObject toJson(final Predicate<JsonField> predicate) {
        return toJson(JsonSchemaVersion.LATEST, predicate);
    }

    /**
     * Returns all fields of this event matching the given predicate.
     *
     * @param schemaVersion the JSON schema version to use.
     * @param predicate the predicate to apply to each field when building the JSON object.
     * @return a JSON object representation of this event including all fields matching the predicate.
     */
    @Override
    JsonObject toJson(JsonSchemaVersion schemaVersion, Predicate<JsonField> predicate);
} 