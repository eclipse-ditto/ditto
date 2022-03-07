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

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;

/**
 * Immutable implementation of {@link ResourceStatus}.
 */
@Immutable
final class ImmutableResourceStatus implements ResourceStatus {

    private final ResourceType type;
    private final String client;
    private final ConnectivityStatus status;
    @Nullable private final RecoveryStatus recoveryStatus;
    @Nullable private final String address;
    @Nullable private final String statusDetails;
    @Nullable private final Instant inStateSince;

    private ImmutableResourceStatus(final ResourceType type, final String client, final ConnectivityStatus status,
            @Nullable final RecoveryStatus recoveryStatus,
            @Nullable final String address,
            @Nullable final String statusDetails,
            @Nullable final Instant inStateSince) {

        this.type = checkNotNull(type, "type");
        this.client = checkNotNull(client, "client");
        this.status = checkNotNull(status, "status");
        this.recoveryStatus = recoveryStatus;
        this.address = address;
        this.statusDetails = statusDetails;
        this.inStateSince = inStateSince;
    }

    /**
     * Creates a new {@code ImmutableResourceStatus} instance.
     *
     * @param type a resource type
     * @param client the client identifier of the resource
     * @param status the current status of the resource
     * @param address an address describing the resource
     * @param statusDetails the optional status details
     * @param inStateSince the instant since the resource is in the given state
     * @return a new instance of ImmutableResourceStatus
     * @throws NullPointerException if any non-nullable argument is {@code null}.
     */
    public static ImmutableResourceStatus of(final ResourceType type,
            final String client,
            final ConnectivityStatus status,
            @Nullable final String address,
            @Nullable final String statusDetails,
            @Nullable final Instant inStateSince) {

        return new ImmutableResourceStatus(type, client, status, null, address, statusDetails, inStateSince);
    }

    /**
     * Creates a new {@code ImmutableResourceStatus} instance.
     *
     * @param type a resource type
     * @param client the client identifier of the resource
     * @param status the current status of the resource
     * @param recoveryStatus the current recovery status of the resource
     * @param address an address describing the resource
     * @param statusDetails the optional status details
     * @param inStateSince the instant since the resource is in the given state
     * @return a new instance of ImmutableResourceStatus
     * @throws NullPointerException if any non-nullable argument is {@code null}.
     */
    public static ImmutableResourceStatus of(final ResourceType type,
            final String client,
            final ConnectivityStatus status,
            @Nullable final RecoveryStatus recoveryStatus,
            @Nullable final String address,
            @Nullable final String statusDetails,
            @Nullable final Instant inStateSince) {

        return new ImmutableResourceStatus(type, client, status, recoveryStatus, address, statusDetails, inStateSince);
    }

    /**
     * Creates a new {@code ImmutableResourceStatus} instance.
     *
     * @param type a resource type
     * @param client the client identifier of the resource
     * @param status the current status of the connection
     * @param address an address describing the resource
     * @param statusDetails the optional status details
     * @return a new instance of ImmutableResourceStatus
     * @throws NullPointerException if any non-nullable argument is {@code null}.
     */
    public static ImmutableResourceStatus of(final ResourceType type,
            final String client,
            final ConnectivityStatus status,
            @Nullable final String address,
            @Nullable final String statusDetails) {

        return new ImmutableResourceStatus(type, client, status, null, address, statusDetails, null);
    }

    @Override
    public ResourceType getResourceType() {
        return type;
    }

    @Override
    public String getClient() {
        return client;
    }

    @Override
    public Optional<String> getAddress() {
        return Optional.ofNullable(address);
    }

    @Override
    public ConnectivityStatus getStatus() {
        return status;
    }

    @Override
    public Optional<RecoveryStatus> getRecoveryStatus() {
        return Optional.ofNullable(recoveryStatus);
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
        jsonObjectBuilder.set(JsonFields.TYPE, type.getName(), predicate);
        jsonObjectBuilder.set(JsonFields.CLIENT, client, predicate);
        if (address != null) {
            jsonObjectBuilder.set(JsonFields.ADDRESS, address, predicate);
        }
        jsonObjectBuilder.set(JsonFields.STATUS, status.getName(), predicate);
        if (recoveryStatus != null) {
            jsonObjectBuilder.set(JsonFields.RECOVERY_STATUS, recoveryStatus.getName(), predicate);
        }
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
     * @return a new ResourceStatus which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static ResourceStatus fromJson(final JsonObject jsonObject) {
        return new ImmutableResourceStatus(
                ResourceType.forName(jsonObject.getValueOrThrow(JsonFields.TYPE)).orElse(ResourceType.UNKNOWN),
                jsonObject.getValueOrThrow(JsonFields.CLIENT),
                ConnectivityStatus.forName(jsonObject.getValueOrThrow(JsonFields.STATUS)).orElse(ConnectivityStatus.UNKNOWN),
                jsonObject.getValue(JsonFields.RECOVERY_STATUS).flatMap(RecoveryStatus::forName).orElse(null),
                jsonObject.getValue(JsonFields.ADDRESS).orElse(null),
                jsonObject.getValue(JsonFields.STATUS_DETAILS).orElse(null),
                jsonObject.getValue(JsonFields.IN_STATE_SINCE).map(Instant::parse).orElse(null)
        );
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
                Objects.equals(client, that.client) &&
                Objects.equals(status, that.status) &&
                Objects.equals(recoveryStatus, that.recoveryStatus) &&
                Objects.equals(address, that.address) &&
                Objects.equals(inStateSince, that.inStateSince) &&
                Objects.equals(statusDetails, that.statusDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, client, status, recoveryStatus, address, statusDetails, inStateSince);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "type=" + type +
                ", client=" + client +
                ", status=" + status +
                ", recoveryStatus=" + recoveryStatus +
                ", address=" + address +
                ", statusDetails=" + statusDetails +
                ", inStateSince=" + inStateSince +
                "]";
    }

}
