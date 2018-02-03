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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * Extensible representation of the health of all systems.
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class Health implements Jsonifiable<JsonObject> {

    /**
     * JSON field of the overall status.
     */
    static final String JSON_FIELD_STATUS = "status";

    private final StatusInfo.Status overallStatus;
    private final Map<String, StatusInfo> componentStatuses;

    private Health(final Builder builder) {
        overallStatus = builder.overallStatus;
        componentStatuses = Collections.unmodifiableMap(new LinkedHashMap<>(builder.componentStatuses));
    }

    /**
     * Returns a concise summary of the health of an underlying system.
     *
     * @return the health summary.
     */
    public StatusInfo getOverallStatus() {
        return StatusInfo.fromStatus(overallStatus);
    }

    /**
     * Returns the health status of a component if it exists.
     *
     * @param componentName name of the component.
     * @return status of the component if it exists, or an empty optional otherwise.
     */
    public Optional<StatusInfo> getComponentStatus(final String componentName) {
        return Optional.ofNullable(componentStatuses.get(componentName));
    }

    /**
     * Returns an empty builder.
     *
     * @return A builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Returns a mutable builder of this object.
     *
     * @return A mutable builder containing exactly the statuses in this object.
     */
    public Builder toBuilder() {
        return new Builder(overallStatus, componentStatuses);
    }

    /**
     * Creates a new {@code Health} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new Health is to be created.
     * @return the Health which was created from the given JSON object.
     */
    public static Health fromJson(final JsonObject jsonObject) {
        final Builder builder = newBuilder();
        jsonObject.stream()
                .filter(jsonField -> jsonField.getValue().isObject())
                .forEach(jsonField -> {
                    final String fieldName = jsonField.getKeyName();
                    if (Objects.equals(fieldName, JSON_FIELD_STATUS)) {
                        final String statusName = jsonField.getValue().asString();
                        final StatusInfo.Status overallStatus = StatusInfo.Status.valueOf(statusName);
                        builder.setOverallStatus(overallStatus);
                    } else {
                        final StatusInfo healthStatus = StatusInfo.fromJson(jsonField.getValue().asObject());
                        builder.setComponentStatus(fieldName, healthStatus);
                    }
                });
        return builder.build();
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        componentStatuses.forEach((name, status) -> jsonObjectBuilder.set(name, status.toJson()));
        jsonObjectBuilder.set(StatusInfo.JSON_KEY_STATUS, overallStatus.name());
        return jsonObjectBuilder.build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(overallStatus, componentStatuses);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        } else {
            final Health that = (Health) obj;
            return Objects.equals(overallStatus, that.overallStatus) &&
                    Objects.equals(componentStatuses, that.componentStatuses);
        }
    }

    /**
     * Builder of {@code Health} objects.
     */
    public static final class Builder {

        private StatusInfo.Status overallStatus;
        private Map<String, StatusInfo> componentStatuses;

        private Builder() {
            overallStatus = StatusInfo.Status.UNKNOWN;
            componentStatuses = new LinkedHashMap<>();
        }

        private Builder(final StatusInfo.Status overallStatus, final Map<String, StatusInfo> componentStatuses) {
            this.overallStatus = overallStatus;
            this.componentStatuses = new LinkedHashMap<>(componentStatuses);
        }

        /**
         * Set the overall status.
         *
         * @param overallStatus The overall status.
         * @return This object.
         */
        public Builder setOverallStatus(final StatusInfo overallStatus) {
            return setOverallStatus(overallStatus.getStatus());
        }

        /**
         * Set the overall status.
         *
         * @param overallStatus The overall status.
         * @return This object.
         */
        public Builder setOverallStatus(final StatusInfo.Status overallStatus) {
            this.overallStatus = overallStatus;
            return this;
        }

        /**
         * Set the status of a component.
         *
         * @param componentName Name of the component, "persistence" or "messaging" for example.
         * @param componentStatus Status of the component.
         * @return This object.
         */
        public Builder setComponentStatus(final String componentName, final StatusInfo componentStatus) {
            componentStatuses.put(componentName, componentStatus);
            return this;
        }

        /**
         * Creates an immutable {@code Health} object from the builder.
         *
         * @return {@code Health} object containing all statuses of this builder.
         */
        public Health build() {
            return new Health(this);
        }
    }

}
