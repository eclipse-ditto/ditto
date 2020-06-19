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
package org.eclipse.ditto.model.connectivity;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.ResponseType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link ReplyTarget}.
 */
@Immutable
final class ImmutableReplyTarget implements ReplyTarget {

    private static final Set<ResponseType> DEFAULT_EXPECTED_RESPONSE_TYPES;

    static {
        DEFAULT_EXPECTED_RESPONSE_TYPES = new HashSet<>();
        DEFAULT_EXPECTED_RESPONSE_TYPES.add(ResponseType.RESPONSE);
        DEFAULT_EXPECTED_RESPONSE_TYPES.add(ResponseType.ERROR);
    }

    private final String address;
    @Nullable private final HeaderMapping headerMapping;
    private final Collection<ResponseType> expectedResponseTypes;

    private ImmutableReplyTarget(final Builder builder) {
        this.address = checkNotNull(builder.address);
        this.headerMapping = builder.headerMapping;
        this.expectedResponseTypes = Collections.unmodifiableSet(new HashSet<>(builder.expectedResponseTypes));
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
    public Collection<ResponseType> getExpectedResponseTypes() {
        return expectedResponseTypes;
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
        jsonObjectBuilder.set(JsonFields.EXPECTED_RESPONSE_TYPES, expectedResponseTypes.stream().map(Enum::name)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray()));
        return jsonObjectBuilder.build();
    }

    static ReplyTarget fromJson(final JsonObject jsonObject) {
        return fromJsonOptional(jsonObject).orElseThrow(() -> new JsonMissingFieldException(JsonFields.ADDRESS));
    }

    static Optional<ReplyTarget> fromJsonOptional(final JsonObject jsonObject) {
        return jsonObject.getValue(JsonFields.ADDRESS).map(address -> new Builder()
                .address(address)
                .headerMapping(jsonObject.getValue(JsonFields.HEADER_MAPPING)
                        .map(ConnectivityModelFactory::newHeaderMapping)
                        .orElse(null))
                .expectedResponseTypes(jsonObject.getValue(JsonFields.EXPECTED_RESPONSE_TYPES)
                        .map(jsonArray -> jsonArray.stream()
                                .map(JsonValue::asString)
                                .map(ResponseType::valueOf)
                                .collect(Collectors.toSet()))
                        .orElse(DEFAULT_EXPECTED_RESPONSE_TYPES))
                .build());

    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutableReplyTarget that = (ImmutableReplyTarget) o;
        return Objects.equals(address, that.address) &&
                Objects.equals(headerMapping, that.headerMapping) &&
                Objects.equals(expectedResponseTypes, that.expectedResponseTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, headerMapping, expectedResponseTypes);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "address=" + address +
                ", headerMapping=" + headerMapping +
                ", expectedResponseTypes=" + expectedResponseTypes +
                "]";
    }

    /**
     * Builder for {@code ImmutableTarget}.
     */
    @NotThreadSafe
    static final class Builder implements ReplyTarget.Builder {

        @Nullable private String address;
        @Nullable private HeaderMapping headerMapping;
        private final Collection<ResponseType> expectedResponseTypes = DEFAULT_EXPECTED_RESPONSE_TYPES;

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

        @Override
        public ReplyTarget.Builder expectedResponseTypes(final Collection<ResponseType> expectedResponseTypes) {
            this.expectedResponseTypes.clear();
            this.expectedResponseTypes.addAll(expectedResponseTypes);
            return this;
        }

        @Override
        public ReplyTarget.Builder expectedResponseTypes(final ResponseType... expectedResponseTypes) {
            return expectedResponseTypes(Arrays.asList(expectedResponseTypes));
        }
    }
}
