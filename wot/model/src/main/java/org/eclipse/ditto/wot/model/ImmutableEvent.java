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

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of {@link Event}.
 */
@Immutable
final class ImmutableEvent extends AbstractInteraction<Event, EventFormElement, EventForms> implements Event {

    private final String eventName;

    ImmutableEvent(final String eventName, final JsonObject wrappedObject) {
        super(wrappedObject);
        this.eventName = eventName;
    }

    @Override
    public String getEventName() {
        return eventName;
    }

    @Override
    public Optional<SingleDataSchema> getSubscription() {
        return wrappedObject.getValue(JsonFields.SUBSCRIPTION)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(SingleDataSchema::fromJson);
    }

    @Override
    public Optional<SingleDataSchema> getData() {
        return wrappedObject.getValue(JsonFields.DATA)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(SingleDataSchema::fromJson);
    }

    @Override
    public Optional<SingleDataSchema> getDataResponse() {
        return wrappedObject.getValue(JsonFields.DATA_RESPONSE)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(SingleDataSchema::fromJson);
    }

    @Override
    public Optional<SingleDataSchema> getCancellation() {
        return wrappedObject.getValue(JsonFields.CANCELLATION)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(SingleDataSchema::fromJson);
    }

    @Override
    public Optional<EventForms> getForms() {
        return wrappedObject.getValue(InteractionJsonFields.FORMS)
                .map(EventForms::fromJson);
    }

    @Override
    protected Event createInstance(final JsonObject newWrapped) {
        return new ImmutableEvent(eventName, newWrapped);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableEvent that = (ImmutableEvent) o;
        return super.equals(o) && Objects.equals(eventName, that.eventName);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ImmutableEvent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventName, super.hashCode());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "eventName=" + eventName +
                ", " + super.toString() +
                "]";
    }

}
