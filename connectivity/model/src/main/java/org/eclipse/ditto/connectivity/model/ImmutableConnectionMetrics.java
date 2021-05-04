/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link ConnectionMetrics}.
 */
@Immutable
final class ImmutableConnectionMetrics implements ConnectionMetrics {

    private final AddressMetric inboundMetrics;
    private final AddressMetric outboundMetrics;

    private ImmutableConnectionMetrics(final AddressMetric inboundMetrics,
            final AddressMetric outboundMetrics) {
        this.inboundMetrics = inboundMetrics;
        this.outboundMetrics = outboundMetrics;
    }

    /**
     * Creates a new {@code ImmutableConnectionMetrics} instance.
     *
     * @param inboundMetrics the ConnectionStatus of the inboundMetrics to create
     * @param outboundMetrics the ConnectionStatus of the outboundMetrics to create
     * @return a new instance of ConnectionMetrics.
     */
    public static ImmutableConnectionMetrics of(final AddressMetric inboundMetrics,
            final AddressMetric outboundMetrics) {
        checkNotNull(inboundMetrics, "inboundMetrics");
        checkNotNull(outboundMetrics, "outboundMetrics");
        return new ImmutableConnectionMetrics(inboundMetrics, outboundMetrics);
    }

    /**
     * Creates a new {@code ConnectionMetrics} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the ConnectionMetrics to be created.
     * @return a new ConnectionMetrics which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static ConnectionMetrics fromJson(final JsonObject jsonObject) {
        return of(
                ConnectivityModelFactory.addressMetricFromJson(jsonObject.getValueOrThrow(JsonFields.INBOUND_METRICS)),
                ConnectivityModelFactory.addressMetricFromJson(jsonObject.getValueOrThrow(JsonFields.OUTBOUND_METRICS))
        );
    }

    @Override
    public AddressMetric getInboundMetrics() {
        return inboundMetrics;
    }

    @Override
    public AddressMetric getOutboundMetrics() {
        return outboundMetrics;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(JsonFields.INBOUND_METRICS, inboundMetrics.toJson(schemaVersion, thePredicate));
        jsonObjectBuilder.set(JsonFields.OUTBOUND_METRICS, outboundMetrics.toJson(schemaVersion, thePredicate));
        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableConnectionMetrics)) {
            return false;
        }
        final ImmutableConnectionMetrics that = (ImmutableConnectionMetrics) o;
        return Objects.equals(inboundMetrics, that.inboundMetrics) &&
                Objects.equals(outboundMetrics, that.outboundMetrics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inboundMetrics, outboundMetrics);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "inboundMetrics=" + inboundMetrics +
                ", outboundMetrics=" + outboundMetrics +
                "]";
    }
}
