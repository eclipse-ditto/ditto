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

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link ResourceStatus}.
 */
@Immutable
final class ImmutableResourceStatus implements ResourceStatus {

    private final ResourceType type;
    private final String status;
    private final String address;
    @Nullable private final String statusDetails;
    @Nullable private final Instant inStateSince;

    private ImmutableResourceStatus(final ResourceType type, final String address, final String status,
            @Nullable final String statusDetails,
            @Nullable final Instant inStateSince) {
        this.type = type;
        this.address = address;
        this.status = status;
        this.statusDetails = statusDetails;
        this.inStateSince = inStateSince;
    }

    /**
     * Creates a new {@code ImmutableResourceStatus} instance.
     *
     * @param type a resource type
     * @param address an address describing the resource
     * @param status the current status of the resource
     * @param statusDetails the optional status details
     * @param inStateSince the instant since the resource is in the given state
     * @return a new instance of ImmutableResourceStatus
     */
    public static ImmutableResourceStatus of(final ResourceType type, final String address, final String status,
            @Nullable final String statusDetails,
            @Nullable final Instant inStateSince) {
        return new ImmutableResourceStatus(type, address, status, statusDetails, inStateSince);
    }

    /**
     * Creates a new {@code ImmutableResourceStatus} instance.
     *
     * @param type a resource type
     * @param address an address describing the resource
     * @param status the current status of the connection
     * @param statusDetails the optional status details
     * @return a new instance of ImmutableResourceStatus
     */
    public static ImmutableResourceStatus of(final ResourceType type, final String address, final String status,
            @Nullable final String statusDetails) {
        return new ImmutableResourceStatus(type, address, status, statusDetails, null);
    }


    @Override
    public ResourceType getResourceType() {
        return type;
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public Optional<String> getStatusDetails() {
        return Optional.ofNullable(statusDetails);
    }

    @Override
    public Optional<Instant> getInStateSince() {
        return Optional.ofNullable(inStateSince);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(JsonFields.ADDRESS, address, predicate);
        jsonObjectBuilder.set(JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate);
        jsonObjectBuilder.set(JsonFields.STATUS, status, predicate);
        if (statusDetails != null) {
            jsonObjectBuilder.set(JsonFields.STATUS_DETAILS, statusDetails, predicate);
        }
        if (inStateSince != null) {
            jsonObjectBuilder.set(JsonFields.IN_STATE_SINCE, inStateSince.toString(), predicate);
        }
        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@code ResourceStatus} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the ResourceStatus to be created.
     * @param type a resource type
     * @return a new ResourceStatus which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static ResourceStatus fromJson(final JsonObject jsonObject, final ResourceType type) {
        final String readConnectionStatus = jsonObject.getValueOrThrow(JsonFields.STATUS);
        final String readAddress = jsonObject.getValueOrThrow(JsonFields.ADDRESS);
        final String readConnectionStatusDetails = jsonObject.getValue(JsonFields.STATUS_DETAILS).orElse(null);
        final Instant readInStateSince =
                jsonObject.getValue(JsonFields.IN_STATE_SINCE).map(Instant::parse).orElse(null);
        return ImmutableResourceStatus.of(type, readAddress, readConnectionStatus, readConnectionStatusDetails,
                readInStateSince);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableResourceStatus)) {
            return false;
        }
        final ImmutableResourceStatus that = (ImmutableResourceStatus) o;
        return type == that.type &&
                Objects.equals(status, that.status) &&
                Objects.equals(address, that.address) &&
                Objects.equals(inStateSince, that.inStateSince) &&
                Objects.equals(statusDetails, that.statusDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, statusDetails, type, address, inStateSince);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "type=" + type +
                ", status=" + status +
                ", address=" + address +
                ", statusDetails=" + statusDetails +
                ", inStateSince=" + inStateSince +
                "]";
    }

}
