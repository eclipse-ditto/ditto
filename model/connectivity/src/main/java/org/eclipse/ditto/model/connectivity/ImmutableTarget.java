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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link Target}.
 */
@Immutable
final class ImmutableTarget implements Target {

    private final String address;
    private final Set<FilteredTopic> topics;
    private final AuthorizationContext authorizationContext;
    private final String originalAddress;
    @Nullable private final HeaderMapping headerMapping;

    private ImmutableTarget(final String address, final Set<FilteredTopic> topics,
            final AuthorizationContext authorizationContext, @Nullable final HeaderMapping headerMapping) {
        this.address = checkNotNull(address, "address");
        this.originalAddress = this.address;
        this.topics = Collections.unmodifiableSet(new HashSet<>(checkNotNull(topics, "topics")));
        this.authorizationContext = checkNotNull(authorizationContext, "authorizationContext");
        this.headerMapping = headerMapping;
    }

    private ImmutableTarget(final String address, final Set<FilteredTopic> topics,
            final AuthorizationContext authorizationContext, @Nullable final HeaderMapping headerMapping,
            final String originalAddress) {
        this.address = checkNotNull(address, "address");
        this.originalAddress = checkNotNull(originalAddress, "originalAddress");
        this.topics = Collections.unmodifiableSet(new HashSet<>(checkNotNull(topics, "topics")));
        this.authorizationContext = checkNotNull(authorizationContext, "authorizationContext");
        this.headerMapping = headerMapping;
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public String getOriginalAddress() {
        return originalAddress;
    }

    @Override
    public Target withAddress(final String newAddress) {
        return new ImmutableTarget(newAddress, topics, authorizationContext, headerMapping, address);
    }

    @Override
    public Set<FilteredTopic> getTopics() {
        return topics;
    }

    @Override
    public AuthorizationContext getAuthorizationContext() {
        return authorizationContext;
    }

    @Override
    public Optional<HeaderMapping> getHeaderMapping() {
        return Optional.ofNullable(headerMapping);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(Target.JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate);
        jsonObjectBuilder.set(Target.JsonFields.ADDRESS, address, predicate);
        jsonObjectBuilder.set(Target.JsonFields.TOPICS, topics.stream()
                .map(FilteredTopic::toString)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));
        if (!authorizationContext.isEmpty()) {
            jsonObjectBuilder.set(JsonFields.AUTHORIZATION_CONTEXT, authorizationContext.stream()
                    .map(AuthorizationSubject::getId)
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray()), predicate);
        }

        if (headerMapping != null) {
            jsonObjectBuilder.set(Target.JsonFields.HEADER_MAPPING, headerMapping.toJson(schemaVersion, thePredicate),
                    predicate);
        }

        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@code Target} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Target to be created.
     * @return a new Source which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Target fromJson(final JsonObject jsonObject) {
        final String readAddress = jsonObject.getValueOrThrow(JsonFields.ADDRESS);
        final Set<FilteredTopic> readTopics = jsonObject.getValue(JsonFields.TOPICS)
                .map(array -> array.stream()
                        .map(JsonValue::asString)
                        .map(ConnectivityModelFactory::newFilteredTopic)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());

        final JsonArray authContext = jsonObject.getValue(JsonFields.AUTHORIZATION_CONTEXT)
                .orElseGet(() -> JsonArray.newBuilder().build());
        final List<AuthorizationSubject> authorizationSubjects = authContext.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(AuthorizationSubject::newInstance)
                .collect(Collectors.toList());
        final AuthorizationContext readAuthorizationContext =
                AuthorizationModelFactory.newAuthContext(authorizationSubjects);

        final HeaderMapping readHeaderMapping =
                jsonObject.getValue(Source.JsonFields.HEADER_MAPPING)
                        .map(ImmutableHeaderMapping::fromJson)
                        .orElse(null);

        return new ImmutableTarget(readAddress, readTopics, readAuthorizationContext, readHeaderMapping);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutableTarget that = (ImmutableTarget) o;
        return Objects.equals(address, that.address) &&
                Objects.equals(topics, that.topics) &&
                Objects.equals(headerMapping, that.headerMapping) &&
                Objects.equals(originalAddress, that.originalAddress) &&
                Objects.equals(authorizationContext, that.authorizationContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, topics, authorizationContext, headerMapping, originalAddress);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "address=" + address +
                ", originalAddress=" + originalAddress +
                ", topics=" + topics +
                ", authorizationContext=" + authorizationContext +
                ", headerMapping=" + headerMapping +
                "]";
    }

    /**
     * Builder for {@code ImmutableSource}.
     */
    @NotThreadSafe
    static final class Builder implements TargetBuilder {

        @Nullable private String address;
        @Nullable private String originalAddress;
        @Nullable private Set<FilteredTopic> topics;
        @Nullable private AuthorizationContext authorizationContext;
        @Nullable private HeaderMapping headerMapping;

        @Override
        public TargetBuilder address(final String address) {
            this.address = address;
            if (originalAddress == null) {
                originalAddress = address;
            }
            return this;
        }

        @Override
        public TargetBuilder originalAddress(final String originalAddress) {
            this.originalAddress = originalAddress;
            return this;
        }

        @Override
        public TargetBuilder authorizationContext(final AuthorizationContext authorizationContext) {
            this.authorizationContext = authorizationContext;
            return this;
        }

        @Override
        public TargetBuilder topics(final Set<FilteredTopic> topics) {
            this.topics = topics;
            return this;
        }

        @Override
        public TargetBuilder topics(final FilteredTopic requiredTopic, final FilteredTopic... additionalTopics) {
            final Set<FilteredTopic> topics = new HashSet<>(Collections.singleton(requiredTopic));
            topics.addAll(Arrays.asList(additionalTopics));
            return topics(topics);
        }

        @Override
        public TargetBuilder topics(final Topic requiredTopic, final Topic... additionalTopics) {
            final Set<Topic> topics = new HashSet<>(Collections.singleton(requiredTopic));
            topics.addAll(Arrays.asList(additionalTopics));
            return topics(topics.stream().map(ConnectivityModelFactory::newFilteredTopic).collect(Collectors.toSet()));
        }

        @Override
        public TargetBuilder headerMapping(@Nullable final HeaderMapping headerMapping) {
            this.headerMapping = headerMapping;
            return this;
        }

        @Override
        public Target build() {
            checkNotNull(address, "address");
            checkNotNull(topics, "topics");
            checkNotNull(authorizationContext, "authorizationContext");
            checkNotNull(originalAddress, "originalAddress");
            return new ImmutableTarget(address, topics, authorizationContext, headerMapping, originalAddress);
        }
    }
}
