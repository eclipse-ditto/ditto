/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Extends the default {@link Source} by fields required for consuming for MQTT sources.
 */
public final class ImmutableMqttSource extends DelegateSource implements MqttSource {

    // user should set qos for sources. the default is qos=2 for convenience
    private static final Integer DEFAULT_QOS = 2;
    private final int qos;
    private final Set<String> filters;

    ImmutableMqttSource(final Source source, final int qos, final Set<String> filters) {
        super(source);
        this.qos = qos;
        this.filters = Collections.unmodifiableSet(new HashSet<>(filters));
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        final JsonObjectBuilder jsonObjectBuilder = delegate.toJson(schemaVersion, predicate).toBuilder();
        jsonObjectBuilder.set(MqttSource.JsonFields.QOS, qos);
        jsonObjectBuilder.set(MqttSource.JsonFields.FILTERS, filters.stream()
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));
        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@code MqttSource} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Source to be created.
     * @param index the index to distinguish between sources that would otherwise be different
     * @return a new Source which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Source fromJson(final JsonObject jsonObject, final int index) {
        final Source source = ImmutableSource.fromJson(jsonObject, index);
        final int readQos = jsonObject.getValue(MqttSource.JsonFields.QOS).orElse(DEFAULT_QOS);
        final Set<String> readFilters = jsonObject.getValue(MqttSource.JsonFields.FILTERS)
                .map(array -> array.stream()
                        .map(JsonValue::asString)
                        .collect(Collectors.toSet())).orElse(Collections.emptySet());
        return new ImmutableMqttSource(source, readQos, readFilters);
    }

    @Override
    public int getQos() {
        return qos;
    }

    @Override
    public Set<String> getFilters() {
        return filters;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final ImmutableMqttSource that = (ImmutableMqttSource) o;
        return qos == that.qos &&
                Objects.equals(filters, that.filters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), qos, filters);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "qos=" + qos +
                ", filters=" + filters +
                ", delegate=" + delegate +
                "]";
    }
}
