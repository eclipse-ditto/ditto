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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link ConnectionMetrics}.
 */
@Immutable
final class ImmutableConnectionMetrics implements ConnectionMetrics {

    private final AddressMetric metrics;

    private ImmutableConnectionMetrics(final AddressMetric metrics) {
        this.metrics = metrics;
    }

    /**
     * Creates a new {@code ImmutableConnectionMetrics} instance.
     *
     * @param metrics the ConnectionStatus of the metrics to create
     * @return a new instance of ConnectionMetrics.
     */
    public static ImmutableConnectionMetrics of(final AddressMetric metrics) {
        checkNotNull(metrics, "metrics");
        return new ImmutableConnectionMetrics(metrics);
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
        return of(ConnectivityModelFactory.addressMetricFromJson(jsonObject.getValueOrThrow(JsonFields.OVERALL_METRICS)));
    }

    @Override
    public AddressMetric getMetrics() {
        return metrics;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(JsonFields.OVERALL_METRICS, metrics.toJson(schemaVersion, thePredicate));
        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {return true;}
        if (!(o instanceof ImmutableConnectionMetrics)) {return false;}
        final ImmutableConnectionMetrics that = (ImmutableConnectionMetrics) o;
        return Objects.equals(metrics, that.metrics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metrics);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                ", metrics=" + metrics +
                "]";
    }
}
