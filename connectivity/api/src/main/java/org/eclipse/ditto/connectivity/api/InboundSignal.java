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
package org.eclipse.ditto.connectivity.api;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectivityConstants;
import org.eclipse.ditto.internal.utils.cluster.MappingStrategies;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Envelope of an incoming signal for client actors to inform each other.
 *
 * @since 1.5.0
 */
@Immutable
public final class InboundSignal implements Jsonifiable.WithFieldSelectorAndPredicate<JsonField>,
        Signal<InboundSignal> {

    private static final String TYPE = InboundSignal.class.getSimpleName();
    private final Signal<?> signal;
    private final boolean dispatched;

    private InboundSignal(final Signal<?> signal, final boolean dispatched) {
        this.signal = checkNotNull(signal, "signal");
        this.dispatched = dispatched;
    }

    /**
     * Wrap an incoming signal.
     *
     * @param signal the signal.
     * @return the envelope.
     */
    public static InboundSignal of(final Signal<?> signal) {
        return new InboundSignal(signal, false);
    }

    /**
     * Return a copy of this object setting the {@code dispatched} flag to {@code true}.
     *
     * @return the copy.
     */
    public InboundSignal asDispatched() {
        return new InboundSignal(signal, true);
    }

    /**
     * Get the {@code dispatched} flag.
     *
     * @return The flag.
     */
    public boolean isDispatched() {
        return dispatched;
    }

    /**
     * Deserialize an incoming signal from JSON.
     *
     * @param jsonObject the JSON representation of an inbound signal.
     * @param mappingStrategies the mapping strategies with which to deserialize the signal.
     * @return the inbound signal.
     */
    public static InboundSignal fromJson(final JsonObject jsonObject, final MappingStrategies mappingStrategies) {

        final DittoHeaders dittoHeaders =
                DittoHeaders.newBuilder(jsonObject.getValueOrThrow(JsonFields.HEADERS)).build();
        final String signalType = jsonObject.getValueOrThrow(JsonFields.SIGNAL_TYPE);
        final JsonObject signalJson = jsonObject.getValueOrThrow(JsonFields.SIGNAL);
        final Jsonifiable<?> signal = mappingStrategies.getMappingStrategy(signalType)
                .orElseThrow(() -> new NoSuchElementException("InboundSignal: No strategy found for signal type " +
                        signalType))
                .parse(signalJson, dittoHeaders);
        final boolean dispatched = jsonObject.getValue(JsonFields.DISPATCHED).orElse(true);

        return new InboundSignal((Signal<?>) signal, dispatched);
    }

    /**
     * Get the wrapped signal.
     *
     * @return the signal.
     */
    public Signal<?> getSignal() {
        return signal;
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof InboundSignal that) {
            return Objects.equals(signal, that.signal) && dispatched == that.dispatched;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(signal, dispatched);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[signal=" + signal +
                ", dispatched=" + dispatched +
                "]";
    }

    @Override
    public JsonObject toJson() {
        return toJson(JsonSchemaVersion.LATEST, FieldType.all());
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        final JsonObject signalJson = signal.toJson(schemaVersion, predicate);
        final JsonObject headers = signal.getDittoHeaders().toJson();
        return JsonObject.newBuilder()
                .set(JsonFields.SIGNAL, signalJson)
                .set(JsonFields.HEADERS, headers)
                .set(JsonFields.SIGNAL_TYPE, signal.getType())
                .set(JsonFields.DISPATCHED, dispatched)
                .build();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, field -> fieldSelector.getPointers().contains(field.getKey().asPointer()));
    }

    @Override
    public InboundSignal setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new InboundSignal(signal.setDittoHeaders(dittoHeaders), dispatched);
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return signal.getDittoHeaders();
    }

    @Nonnull
    @Override
    public String getManifest() {
        return TYPE;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return ConnectivityConstants.ENTITY_TYPE.toString();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    private static final class JsonFields {

        private static final JsonFieldDefinition<JsonObject> SIGNAL =
                JsonFactory.newJsonObjectFieldDefinition("signal");

        private static final JsonFieldDefinition<JsonObject> HEADERS =
                JsonFactory.newJsonObjectFieldDefinition("headers");

        private static final JsonFieldDefinition<String> SIGNAL_TYPE =
                JsonFactory.newStringFieldDefinition("signalType");

        private static final JsonFieldDefinition<Boolean> DISPATCHED =
                JsonFactory.newBooleanFieldDefinition("dispatched");
    }
}
