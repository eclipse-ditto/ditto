/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.model.connectivity.replies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.HeaderMapping;

/**
 * Immutable implementation of {@link ReplyTarget}.
 */
@Immutable
final class ImmutableReplyTarget implements ReplyTarget {

    private final String address;
    @Nullable private final HeaderMapping headerMapping;

    private ImmutableReplyTarget(final Builder builder) {
        this.address = checkNotNull(builder.address);
        this.headerMapping = builder.headerMapping;
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public Optional<HeaderMapping> getHeaderMapping() {
        return Optional.ofNullable(headerMapping);
    }

    @Override
    public ReplyTarget.Builder toBuilder() {
        return new Builder()
                .address(address)
                .headerMapping(headerMapping);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(JsonFields.ADDRESS, address);
        if (headerMapping != null) {
            jsonObjectBuilder.set(JsonFields.HEADER_MAPPING, headerMapping.toJson(schemaVersion, predicate),
                    predicate);
        }
        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new reply-target object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Target to be created.
     * @return a new Target which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    static ReplyTarget fromJson(final JsonObject jsonObject) {
        final HeaderMapping readHeaderMapping = jsonObject.getValue(JsonFields.HEADER_MAPPING)
                .map(ConnectivityModelFactory::newHeaderMapping)
                .orElse(null);

        return new Builder()
                .address(jsonObject.getValueOrThrow(JsonFields.ADDRESS))
                .headerMapping(readHeaderMapping)
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutableReplyTarget that = (ImmutableReplyTarget) o;
        return Objects.equals(address, that.address) &&
                Objects.equals(headerMapping, that.headerMapping);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, headerMapping);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "address=" + address +
                ", headerMapping=" + headerMapping +
                "]";
    }

    /**
     * Builder for {@code ImmutableTarget}.
     */
    @NotThreadSafe
    static final class Builder implements ReplyTarget.Builder {

        @Nullable private String address;
        @Nullable private HeaderMapping headerMapping;

        @Override
        public ReplyTarget build() {
            return new ImmutableReplyTarget(this);
        }

        @Override
        public ReplyTarget.Builder address(final String address) {
            this.address = address;
            return this;
        }

        @Override
        public Builder headerMapping(@Nullable final HeaderMapping headerMapping) {
            this.headerMapping = headerMapping;
            return this;
        }
    }
}
