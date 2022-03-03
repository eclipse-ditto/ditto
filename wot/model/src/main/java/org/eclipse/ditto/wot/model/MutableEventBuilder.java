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

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Mutable builder for {@link Event}s.
 */
final class MutableEventBuilder
        extends AbstractInteractionBuilder<Event.Builder, Event, EventFormElement, EventForms>
        implements Event.Builder {

    private final String eventName;

    MutableEventBuilder(final String eventName, final JsonObjectBuilder wrappedObjectBuilder) {
        super(wrappedObjectBuilder, MutableEventBuilder.class);
        this.eventName = eventName;
    }

    @Override
    public Event.Builder setSubscription(@Nullable final SingleDataSchema subscription) {
        if (subscription != null) {
            putValue(Event.JsonFields.SUBSCRIPTION, subscription.toJson());
        } else {
            remove(Event.JsonFields.SUBSCRIPTION);
        }
        return myself;
    }

    @Override
    public Event.Builder setData(@Nullable final SingleDataSchema data) {
        if (data != null) {
            putValue(Event.JsonFields.DATA, data.toJson());
        } else {
            remove(Event.JsonFields.DATA);
        }
        return myself;
    }

    @Override
    public Event.Builder setDataResponse(@Nullable final SingleDataSchema dataResponse) {
        if (dataResponse != null) {
            putValue(Event.JsonFields.DATA_RESPONSE, dataResponse.toJson());
        } else {
            remove(Event.JsonFields.DATA_RESPONSE);
        }
        return myself;
    }

    @Override
    public Event.Builder setCancellation(@Nullable final SingleDataSchema cancellation) {
        if (cancellation != null) {
            putValue(Event.JsonFields.CANCELLATION, cancellation.toJson());
        } else {
            remove(Event.JsonFields.CANCELLATION);
        }
        return myself;
    }

    @Override
    public Event build() {
        return new ImmutableEvent(eventName, wrappedObjectBuilder.build());
    }

}
