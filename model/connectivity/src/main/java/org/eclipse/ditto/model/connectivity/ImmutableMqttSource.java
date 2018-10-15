/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Extends the default {@link Source} by fields required for consuming for MQTT sources.
 */
@Immutable
public final class ImmutableMqttSource extends DelegateSource implements MqttSource {

    // user should set qos for sources. the default is qos=2 for convenience
    private static final Integer DEFAULT_QOS = 2;
    private final int qos;

    private ImmutableMqttSource(final Source delegate, final int qos) {
        super(delegate);
        this.qos = qos;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        final JsonObjectBuilder jsonObjectBuilder = delegate.toJson(schemaVersion, predicate).toBuilder();
        jsonObjectBuilder.set(MqttSource.JsonFields.QOS, qos);
        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@code MqttSource} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Source to be created.
     * @param index the index to distinguish between sources that would otherwise be different
     * @return a new MqttSource which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static MqttSource fromJson(final JsonObject jsonObject, final int index) {
        final Source source = ImmutableSource.fromJson(jsonObject, index);
        final Builder builder = new Builder(source);
        final int readQos = jsonObject.getValue(MqttSource.JsonFields.QOS).orElse(DEFAULT_QOS);
        return builder.qos(readQos).build();
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
        final ImmutableMqttSource that = (ImmutableMqttSource) o;
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

    /**
     * Builder for {@code ImmutableMqttSource}.
     */
    @NotThreadSafe
    static final class Builder implements MqttSourceBuilder {

        private final SourceBuilder delegateBuilder = ConnectivityModelFactory.newSourceBuilder();

        // required
        @Nullable private Source delegate;
        // optional with default:
        private int qos = DEFAULT_QOS;

        Builder() {
        }

        Builder(final Source delegate) {
            this.delegate = delegate;
        }

        @Override
        public MqttSourceBuilder qos(final int qos) {
            this.qos = qos;
            return this;
        }

        @Override
        public MqttSourceBuilder addresses(final Set<String> addresses) {
            delegateBuilder.addresses(addresses);
            return this;
        }

        @Override
        public MqttSourceBuilder address(final String address) {
            delegateBuilder.address(address);
            return this;
        }

        @Override
        public MqttSourceBuilder consumerCount(final int consumerCount) {
            delegateBuilder.consumerCount(consumerCount);
            return this;
        }

        @Override
        public MqttSourceBuilder index(final int index) {
            delegateBuilder.index(index);
            return this;
        }

        @Override
        public MqttSourceBuilder authorizationContext(final AuthorizationContext authorizationContext) {
            delegateBuilder.authorizationContext(authorizationContext);
            return this;
        }

        @Override
        public MqttSourceBuilder enforcement(@Nullable final Enforcement enforcement) {
            delegateBuilder.enforcement(enforcement);
            return this;
        }

        @Override
        public MqttSource build() {
            if (delegate == null) {
                delegate = delegateBuilder.build();
            }
            return new ImmutableMqttSource(delegate, qos);
        }
    }
}
