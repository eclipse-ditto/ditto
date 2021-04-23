/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.model.signals.events;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;

/**
 * This event is emitted after a stream of search results failed.
 * Corresponds to the reactive-streams signal {@code Subscriber#onError()}.
 *
 * @since 1.1.0
 */
@Immutable
@JsonParsableEvent(name = SubscriptionFailed.NAME, typePrefix = SubscriptionEvent.TYPE_PREFIX)
public final class SubscriptionFailed extends AbstractSubscriptionEvent<SubscriptionFailed> {

    /**
     * Name of the event.
     */
    public static final String NAME = "failed";

    /**
     * Type of this event.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private final DittoRuntimeException error;

    private SubscriptionFailed(final String subscriptionId, final DittoRuntimeException error,
            final DittoHeaders dittoHeaders) {
        super(TYPE, subscriptionId, dittoHeaders);
        this.error = error;
    }

    /**
     * Constructs a new {@code SubscriptionFailed} object.
     *
     * @param subscriptionId the subscription ID.
     * @param error the cause of the failure.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the SubscriptionFailed created.
     * @throws NullPointerException if either argument is null.
     */
    public static SubscriptionFailed of(final String subscriptionId,
            final DittoRuntimeException error,
            final DittoHeaders dittoHeaders) {
        return new SubscriptionFailed(subscriptionId, error, dittoHeaders);
    }

    /**
     * Creates a new {@code SubscriptionFailed} from a JSON object.
     *
     * @param jsonObject the JSON object from which a new SubscriptionFailed instance is to be created.
     * @param dittoHeaders the headers of the command which was the cause of this event.
     * @return the {@code SubscriptionFailed} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static SubscriptionFailed fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new EventJsonDeserializer<SubscriptionFailed>(TYPE, jsonObject)
                .deserialize((revision, timestamp, metadata) -> {
                    final String subscriptionId =
                            jsonObject.getValueOrThrow(SubscriptionEvent.JsonFields.SUBSCRIPTION_ID);
                    final JsonObject errorJson = jsonObject.getValueOrThrow(JsonFields.ERROR);
                    final DittoRuntimeException error =
                            GlobalErrorRegistry.getInstance().parse(errorJson, dittoHeaders);
                    return new SubscriptionFailed(subscriptionId, error, dittoHeaders);
                });
    }

    /**
     * Get the cause of the failure.
     *
     * @return the error in JSON format.
     */
    public DittoRuntimeException getError() {
        return error;
    }

    /**
     * Create a copy of this event with a new error.
     *
     * @param error the new error.
     * @return the copied event with new error.
     */
    public SubscriptionFailed setError(final DittoRuntimeException error) {
        return new SubscriptionFailed(getSubscriptionId(), error, getDittoHeaders());
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public SubscriptionFailed setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new SubscriptionFailed(getSubscriptionId(), error, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder) {
        jsonObjectBuilder.set(JsonFields.ERROR, error.toJson());
    }

    @Override
    public boolean equals(final Object o) {
        return super.equals(o) && Objects.equals(error, ((SubscriptionFailed) o).error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), error);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", error=" + error + "]";
    }

    /**
     * Json fields of this event.
     */
    public static final class JsonFields {

        /**
         * Json fields for a JSON representation of the error.
         */
        public static final JsonFieldDefinition<JsonObject> ERROR =
                JsonFactory.newJsonObjectFieldDefinition("error");

        JsonFields() {
            throw new AssertionError();
        }
    }
}
