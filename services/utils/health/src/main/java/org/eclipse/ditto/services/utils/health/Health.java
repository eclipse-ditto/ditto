/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.health;

import static org.eclipse.ditto.json.JsonFactory.newFieldDefinition;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Representation of the health of underlying systems, such as messaging and persistence.
 */
@Immutable
public final class Health implements HealthRepresentation {

    /**
     * JSON field of the persistence status.
     */
    static final JsonFieldDefinition JSON_KEY_PERSISTENCE = newFieldDefinition("persistence", JsonObject.class);

    /**
     * JSON field of the cluster status.
     */
    static final JsonFieldDefinition JSON_KEY_CLUSTER = newFieldDefinition("cluster", JsonObject.class);


    private final HealthStatus healthStatusPersistence;
    private final HealthStatus healthStatusCluster;

    private Health(final HealthStatus healthStatusPersistence, final HealthStatus healthStatusCluster) {
        this.healthStatusPersistence = healthStatusPersistence;
        this.healthStatusCluster = healthStatusCluster;
    }

    /**
     * Returns a new {@code Health} instance.
     *
     * @return the Health instance.
     */
    public static Health newInstance() {
        return new Health(null, null);
    }

    /**
     * Returns a new {@code Health} instance with the specified {@code statusPersistence} and
     * {@code statusCluster}.
     *
     * @param statusPersistence the persistence's status.
     * @param statusCluster the cluster's status.
     * @return the Health instance.
     */
    public static Health of(final HealthStatus statusPersistence, final HealthStatus statusCluster) {
        return new Health(statusPersistence, statusCluster);
    }

    /**
     * Creates a new {@link Health} from a JSON string.
     *
     * @param jsonString the JSON string of which a new Health is to be created.
     * @return the Health which was created from the given JSON string.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} does not contain a JSON
     * object or if it is not valid JSON.
     */
    public static Health fromJson(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    /**
     * Creates a new {@link Health} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new Health is to be created.
     * @return the Health which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} is not valid JSON.
     */
    public static Health fromJson(final JsonObject jsonObject) {

        final HealthStatus statusPersistence = jsonObject.getValue(JSON_KEY_PERSISTENCE)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(HealthStatus::fromJson)
                .orElse(null);

        final HealthStatus statusCluster = jsonObject.getValue(JSON_KEY_CLUSTER)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(HealthStatus::fromJson)
                .orElse(null);

        return of(statusPersistence, statusCluster);
    }

    /**
     * Returns the optional {@code HealthStatus} of the persistence.
     *
     * @return the status.
     */
    public Optional<HealthStatus> getHealthStatusPersistence() {
        return Optional.ofNullable(healthStatusPersistence);
    }

    /**
     * Sets the specified {@code healthStatus}.
     *
     * @param healthStatus the healthStatus.
     * @return a copy of this object with the new healthStatus set.
     */
    public Health setHealthStatusPersistence(final HealthStatus healthStatus) {
        return Health.of(healthStatus, this.healthStatusCluster);
    }

    /**
     * Returns the optional {@code HealthStatus} of the cluster.
     *
     * @return the status.
     */
    public Optional<HealthStatus> getHealthStatusCluster() {
        return Optional.ofNullable(healthStatusCluster);
    }

    /**
     * Sets the specified {@code healthStatus}.
     *
     * @param healthStatus the healthStatus.
     * @return a copy of this object with the new healthStatus set.
     */
    public Health setHealthStatusCluster(final HealthStatus healthStatus) {
        return Health.of(this.healthStatusPersistence, healthStatus);
    }

    @Override
    public HealthStatus getHealthStatus() {
        boolean allUp = true;
        if (null != healthStatusPersistence) {
            allUp = allUp && (healthStatusPersistence.getStatus() == HealthStatus.Status.UP
                    || healthStatusPersistence.getStatus() == HealthStatus.Status.UNKNOWN);
        }
        if (null != healthStatusCluster) {
            allUp = allUp && (healthStatusCluster.getStatus() == HealthStatus.Status.UP
                    || healthStatusCluster.getStatus() == HealthStatus.Status.UNKNOWN);
        }

        final HealthStatus.Status status = allUp ? HealthStatus.Status.UP : HealthStatus.Status.DOWN;
        return HealthStatus.of(status);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(HealthStatus.JSON_KEY_STATUS, getHealthStatus().getStatus().toString());

        if (null != healthStatusPersistence) {
            jsonObjectBuilder.set(JSON_KEY_PERSISTENCE, healthStatusPersistence.toJson());
        }
        if (null != healthStatusCluster) {
            jsonObjectBuilder.set(JSON_KEY_CLUSTER, healthStatusCluster.toJson());
        }

        return jsonObjectBuilder.build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(healthStatusPersistence, healthStatusCluster);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Health that = (Health) obj;
        return Objects.equals(healthStatusPersistence, that.healthStatusPersistence)
                && Objects.equals(healthStatusCluster, that.healthStatusCluster);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "healthStatusPersistence=" + healthStatusPersistence +
                ", healthStatusCluster=" + healthStatusCluster + "]";
    }
}
