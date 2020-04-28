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
package org.eclipse.ditto.model.connectivity;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
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
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link Target}.
 */
@Immutable
final class ImmutableTarget implements Target {

    /**
     * Default header mapping for legacy targets with no header mapping configured.
     */
    static final HeaderMapping DEFAULT_HEADER_MAPPING = ImmutableSource.DEFAULT_SOURCE_HEADER_MAPPING;

    private final String address;
    private final Set<FilteredTopic> topics;
    @Nullable private final Integer qos;
    private final AuthorizationContext authorizationContext;
    private final String originalAddress;
    @Nullable private final HeaderMapping headerMapping;
    private final PayloadMapping payloadMapping;

    private ImmutableTarget(final ImmutableTarget.Builder builder) {
        address = checkNotNull(builder.address, "address");
        originalAddress = checkNotNull(builder.originalAddress, "originalAddress");
        topics = Collections.unmodifiableSet(
                new HashSet<>(builder.topics == null ? Collections.emptySet() : builder.topics));
        qos = builder.qos;
        authorizationContext = checkNotNull(builder.authorizationContext, "authorizationContext");
        headerMapping = builder.headerMapping;
        payloadMapping = builder.payloadMapping;
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
        return new ImmutableTarget.Builder(this).address(newAddress).build();
    }

    @Override
    public Set<FilteredTopic> getTopics() {
        return topics;
    }

    @Override
    public Optional<Integer> getQos() {
        return Optional.ofNullable(qos);
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
    public PayloadMapping getPayloadMapping() {
        return payloadMapping;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate);
        jsonObjectBuilder.set(JsonFields.ADDRESS, address, predicate);
        jsonObjectBuilder.set(JsonFields.TOPICS, topics.stream()
                .map(FilteredTopic::toString)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));
        if (qos != null) {
            jsonObjectBuilder.set(JsonFields.QOS, qos, predicate);
        }
        if (!authorizationContext.isEmpty()) {
            jsonObjectBuilder.set(JsonFields.AUTHORIZATION_CONTEXT, authorizationContext.stream()
                    .map(AuthorizationSubject::getId)
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray()), predicate);
        }

        if (headerMapping != null) {
            jsonObjectBuilder.set(JsonFields.HEADER_MAPPING, headerMapping.toJson(schemaVersion, thePredicate),
                    predicate);
        }

        if (!payloadMapping.isEmpty()) {
            jsonObjectBuilder.set(Target.JsonFields.PAYLOAD_MAPPING, payloadMapping.toJson(), predicate);
        }

        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@code Target} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Target to be created.
     * @return a new Target which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Target fromJson(final JsonObject jsonObject) {

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
                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
                        authorizationSubjects);

        final HeaderMapping readHeaderMapping =
                jsonObject.getValue(JsonFields.HEADER_MAPPING)
                        .map(ImmutableHeaderMapping::fromJson)
                        .orElse(null);

        final PayloadMapping readMapping =
                jsonObject.getValue(JsonFields.PAYLOAD_MAPPING)
                        .map(ImmutablePayloadMapping::fromJson)
                        .orElse(ConnectivityModelFactory.emptyPayloadMapping());

        return new Builder()
                .address(jsonObject.getValueOrThrow(JsonFields.ADDRESS))
                .topics(readTopics)
                .qos(jsonObject.getValue(JsonFields.QOS).orElse(null))
                .authorizationContext(readAuthorizationContext)
                .headerMapping(readHeaderMapping)
                .payloadMapping(readMapping)
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutableTarget that = (ImmutableTarget) o;
        return address.equals(that.address) &&
                topics.equals(that.topics) &&
                Objects.equals(qos, that.qos) &&
                authorizationContext.equals(that.authorizationContext) &&
                originalAddress.equals(that.originalAddress) &&
                Objects.equals(headerMapping, that.headerMapping) &&
                payloadMapping.equals(that.payloadMapping);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, topics, qos, authorizationContext, originalAddress, headerMapping, payloadMapping);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "address=" + address +
                ", topics=" + topics +
                ", qos=" + qos +
                ", authorizationContext=" + authorizationContext +
                ", originalAddress=" + originalAddress +
                ", headerMapping=" + headerMapping +
                ", payloadMapping=" + payloadMapping +
                "]";
    }

    /**
     * Builder for {@code ImmutableTarget}.
     */
    @NotThreadSafe
    static final class Builder implements TargetBuilder {

        private PayloadMapping payloadMapping = ConnectivityModelFactory.emptyPayloadMapping();
        @Nullable private String address;
        @Nullable private String originalAddress;
        @Nullable private Set<FilteredTopic> topics;
        @Nullable private Integer qos;
        @Nullable private AuthorizationContext authorizationContext;
        @Nullable private HeaderMapping headerMapping;

        Builder() {
        }

        Builder(final Target target) {
            address(target.getAddress())
                    .authorizationContext(target.getAuthorizationContext())
                    .topics(target.getTopics())
                    .headerMapping(target.getHeaderMapping().orElse(null))
                    .qos(target.getQos().orElse(null))
                    .payloadMapping(target.getPayloadMapping());
        }

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
        public TargetBuilder qos(@Nullable final Integer qos) {
            this.qos = qos;
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
            final Set<FilteredTopic> theTopics = new HashSet<>(1 + additionalTopics.length);
            theTopics.add(requiredTopic);
            Collections.addAll(theTopics, additionalTopics);

            return topics(theTopics);
        }

        @Override
        public TargetBuilder topics(final Topic requiredTopic, final Topic... additionalTopics) {
            final Collection<Topic> theTopics = EnumSet.of(requiredTopic, additionalTopics);
            final Set<FilteredTopic> filteredTopics = theTopics.stream()
                    .map(ConnectivityModelFactory::newFilteredTopicBuilder)
                    .map(FilteredTopicBuilder::build)
                    .collect(Collectors.toSet());

            return topics(filteredTopics);
        }

        @Override
        public TargetBuilder headerMapping(@Nullable final HeaderMapping headerMapping) {
            this.headerMapping = headerMapping;
            return this;
        }

        @Override
        public TargetBuilder payloadMapping(final PayloadMapping payloadMapping) {
            this.payloadMapping = payloadMapping;
            return this;
        }

        @Override
        public Target build() {
            checkNotNull(address, "address");
            checkNotNull(topics, "topics");
            checkNotNull(authorizationContext, "authorizationContext");
            checkNotNull(originalAddress, "originalAddress");
            return new ImmutableTarget(this);
        }
    }
}
