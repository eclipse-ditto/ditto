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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of {@link ReplyTarget}.
 */
@Immutable
final class ImmutableReplyTarget implements ReplyTarget {

    private static final LinkedHashSet<ResponseType> DEFAULT_EXPECTED_RESPONSE_TYPES;

    static {
        final HashSet<ResponseType> defaultExpectedResponseTypes = new LinkedHashSet<>();
        defaultExpectedResponseTypes.add(ResponseType.RESPONSE);
        defaultExpectedResponseTypes.add(ResponseType.ERROR);
        DEFAULT_EXPECTED_RESPONSE_TYPES = new LinkedHashSet<>(defaultExpectedResponseTypes);
    }

    private final String address;
    private final HeaderMapping headerMapping;
    private final Set<ResponseType> expectedResponseTypes;

    private ImmutableReplyTarget(final Builder builder) {
        this.address = checkNotNull(builder.address);
        this.headerMapping = builder.headerMapping;
        this.expectedResponseTypes = Collections.unmodifiableSet(new LinkedHashSet<>(builder.expectedResponseTypes));
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public HeaderMapping getHeaderMapping() {
        return headerMapping;
    }

    @Override
    public Optional<Integer> getQos() {
        // reply target does not support QoS yet
        return Optional.empty();
    }

    @Override
    public GenericTarget withAddress(final String newAddress) {
        return new Builder().address(newAddress)
                .headerMapping(headerMapping)
                .expectedResponseTypes(expectedResponseTypes)
                .build();
    }

    @Override
    public Set<ResponseType> getExpectedResponseTypes() {
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
        jsonObjectBuilder.set(JsonFields.HEADER_MAPPING, headerMapping.toJson(schemaVersion, predicate),
                predicate);
        jsonObjectBuilder.set(JsonFields.EXPECTED_RESPONSE_TYPES, expectedResponseTypes.stream()
                .map(ResponseType::getName)
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
                                .map(ResponseType::fromName)
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .collect(Collectors.toCollection(LinkedHashSet::new)))
                        .orElse(DEFAULT_EXPECTED_RESPONSE_TYPES))
                .build());

    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
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
        private HeaderMapping headerMapping = ConnectivityModelFactory.emptyHeaderMapping();
        private final Collection<ResponseType> expectedResponseTypes = new LinkedHashSet<>(DEFAULT_EXPECTED_RESPONSE_TYPES);

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
            this.headerMapping = headerMapping == null ? ConnectivityModelFactory.emptyHeaderMapping() : headerMapping;
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
