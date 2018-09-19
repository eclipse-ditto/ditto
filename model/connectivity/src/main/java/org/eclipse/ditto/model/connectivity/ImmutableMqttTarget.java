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

import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Extends the default {@link Target} by fields required for publishing for MQTT targets.
 */
public final class ImmutableMqttTarget extends DelegateTarget implements MqttTarget {

    // for target the default is qos=0 because we have qos=0 all over the akka cluster
    private static final Integer DEFAULT_QOS = 0;
    private final int qos;

    ImmutableMqttTarget(final Target target, final int qos) {
        super(target);
        this.qos = qos;
    }

    @Override
    public Target withAddress(final String newAddress) {
        return new ImmutableMqttTarget(delegate.withAddress(newAddress), qos);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {

        final JsonObjectBuilder jsonObjectBuilder = delegate.toJson(schemaVersion, predicate).toBuilder();

        jsonObjectBuilder.set(MqttSource.JsonFields.QOS, qos);

        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@code Source} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Source to be created.
     * @return a new Source which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Target fromJson(final JsonObject jsonObject) {
        final Target target = ImmutableTarget.fromJson(jsonObject);
        final int readQos = jsonObject.getValue(MqttTarget.JsonFields.QOS).orElse(DEFAULT_QOS);
        return new ImmutableMqttTarget(target, readQos);
    }

    @Override
    public int getQos() {
        return qos;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ImmutableMqttTarget that = (ImmutableMqttTarget) o;
        return qos == that.qos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), qos);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "qos=" + qos +
                ", delegate=" + delegate +
                "]";
    }
}
