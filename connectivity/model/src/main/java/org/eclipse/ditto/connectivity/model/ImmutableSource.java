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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.FilteredAcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of {@link Source}.
 */
@Immutable
final class ImmutableSource implements Source {

    /**
     * Default address to publish responses of incoming commands.
     */
    static final String DEFAULT_REPLY_TARGET_ADDRESS = "{{header:reply-to}}";

    /**
     * Placeholder for getting the header 'correlation-id'
     */
    static final String PLACEHOLDER_HEADER_CORRELATION_ID = "{{header:correlation-id}}";
    private static final String PLACEHOLDER_HEADER_CONTENT_TYPE = "{{header:content-type}}";

    /**
     * Default header mapping for legacy sources (i. e., no reply-target defined)
     */
    static final HeaderMapping DEFAULT_SOURCE_HEADER_MAPPING =
            ConnectivityModelFactory.newHeaderMapping(JsonObject.newBuilder()
                    .set("correlation-id", PLACEHOLDER_HEADER_CORRELATION_ID)
                    .set("content-type", PLACEHOLDER_HEADER_CONTENT_TYPE)
                    .set("reply-to", DEFAULT_REPLY_TARGET_ADDRESS)
                    .build());

    /**
     * Default header mapping for the reply-target added to legacy sources.
     */
    static final HeaderMapping DEFAULT_REPLY_TARGET_HEADER_MAPPING =
            ConnectivityModelFactory.newHeaderMapping(JsonObject.newBuilder()
                    .set("correlation-id", PLACEHOLDER_HEADER_CORRELATION_ID)
                    .set("content-type", PLACEHOLDER_HEADER_CONTENT_TYPE)
                    .build());

    private static final int DEFAULT_CONSUMER_COUNT = 1;
    private static final int DEFAULT_INDEX = 0;
    private static final boolean DEFAULT_REPLY_TARGET_ENABLED = true;

    private final Set<String> addresses;
    private final int consumerCount;
    @Nullable private final Integer qos;
    private final int index;
    private final AuthorizationContext authorizationContext;
    @Nullable private final Enforcement enforcement;
    @Nullable private final FilteredAcknowledgementRequest acknowledgementRequests;
    private final HeaderMapping headerMapping;
    private final PayloadMapping payloadMapping;
    private final boolean replyTargetEnabled;
    @Nullable private final ReplyTarget replyTarget;
    private final Set<AcknowledgementLabel> declaredAcknowledgementLabels;

    private ImmutableSource(final Builder builder) {
        addresses = Collections.unmodifiableSet(
                new LinkedHashSet<>(ConditionChecker.checkNotNull(builder.addresses, "addresses")));
        consumerCount = builder.consumerCount;
        qos = builder.qos;
        authorizationContext = ConditionChecker.checkNotNull(builder.authorizationContext, "authorizationContext");
        index = builder.index;
        enforcement = builder.enforcement;
        acknowledgementRequests = builder.acknowledgementRequests;
        headerMapping = builder.headerMapping;
        payloadMapping = builder.payloadMapping;
        replyTargetEnabled = builder.replyTargetEnabled;
        replyTarget = builder.replyTarget;
        declaredAcknowledgementLabels = builder.declaredAcknowledgementLabels;
    }

    @Override
    public Set<String> getAddresses() {
        return addresses;
    }

    @Override
    public int getConsumerCount() {
        return consumerCount;
    }

    @Override
    public AuthorizationContext getAuthorizationContext() {
        return authorizationContext;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public Optional<Integer> getQos() {
        return Optional.ofNullable(qos);
    }

    @Override
    public Optional<Enforcement> getEnforcement() {
        return Optional.ofNullable(enforcement);
    }

    @Override
    public Optional<FilteredAcknowledgementRequest> getAcknowledgementRequests() {
        return Optional.ofNullable(acknowledgementRequests);
    }

    @Override
    public HeaderMapping getHeaderMapping() {
        return headerMapping;
    }

    @Override
    public PayloadMapping getPayloadMapping() {
        return payloadMapping;
    }

    @Override
    public Optional<ReplyTarget> getReplyTarget() {
        return Optional.ofNullable(replyTarget);
    }

    @Override
    public boolean isReplyTargetEnabled() {
        return replyTargetEnabled;
    }

    @Override
    public Set<AcknowledgementLabel> getDeclaredAcknowledgementLabels() {
        return declaredAcknowledgementLabels;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JsonFields.ADDRESSES, addresses.stream()
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));
        jsonObjectBuilder.set(JsonFields.CONSUMER_COUNT, consumerCount, predicate);
        if (qos != null) {
            jsonObjectBuilder.set(JsonFields.QOS, qos);
        }

        if (!authorizationContext.isEmpty()) {
            jsonObjectBuilder.set(JsonFields.AUTHORIZATION_CONTEXT, authorizationContext.stream()
                    .map(AuthorizationSubject::getId)
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray()), predicate);
        }

        if (enforcement != null) {
            jsonObjectBuilder.set(JsonFields.ENFORCEMENT, enforcement.toJson(schemaVersion, thePredicate), predicate);
        }

        if (acknowledgementRequests != null) {
            jsonObjectBuilder.set(JsonFields.ACKNOWLEDGEMENT_REQUESTS,
                    acknowledgementRequests.toJson(schemaVersion, thePredicate), predicate);
        }

        jsonObjectBuilder.set(JsonFields.HEADER_MAPPING, headerMapping.toJson(schemaVersion, thePredicate), predicate);

        if (!payloadMapping.isEmpty()) {
            jsonObjectBuilder.set(JsonFields.PAYLOAD_MAPPING, payloadMapping.toJson(), predicate);
        }

        if (replyTarget != null) {
            jsonObjectBuilder.set(JsonFields.REPLY_TARGET, replyTarget.toJson(schemaVersion, thePredicate), predicate);
        }

        if (!(replyTargetEnabled && replyTarget == null)) {
            // only set replyTargetEnabled if it is not set by reply-target migration
            jsonObjectBuilder.set(JsonFields.REPLY_TARGET_ENABLED, replyTargetEnabled, predicate);
        }

        if (!declaredAcknowledgementLabels.isEmpty()) {
            jsonObjectBuilder.set(JsonFields.DECLARED_ACKS, declaredAcksToJson(declaredAcknowledgementLabels));
        }

        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@code Source} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Source to be created.
     * @param index the index to distinguish between sources that would otherwise be different
     * @return a new Source which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Source fromJson(final JsonObject jsonObject, final int index) {
        final Set<String> readSources = jsonObject.getValue(JsonFields.ADDRESSES)
                .map(array -> array.stream()
                        .map(JsonValue::asString)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElseGet(LinkedHashSet::new);
        final int readConsumerCount =
                jsonObject.getValue(JsonFields.CONSUMER_COUNT).orElse(DEFAULT_CONSUMER_COUNT);
        final Integer readQos = jsonObject.getValue(JsonFields.QOS).orElse(null);
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

        final Enforcement readEnforcement =
                jsonObject.getValue(JsonFields.ENFORCEMENT).map(ImmutableEnforcement::fromJson).orElse(null);

        final FilteredAcknowledgementRequest readAcknowledgementRequests =
                jsonObject.getValue(JsonFields.ACKNOWLEDGEMENT_REQUESTS)
                        .map(FilteredAcknowledgementRequest::fromJson)
                        .orElse(null);

        final HeaderMapping readHeaderMapping =
                jsonObject.getValue(JsonFields.HEADER_MAPPING).map(ImmutableHeaderMapping::fromJson).orElse(null);

        final PayloadMapping readPayloadMapping =
                jsonObject.getValue(JsonFields.PAYLOAD_MAPPING)
                        .map(ImmutablePayloadMapping::fromJson)
                        .orElse(ConnectivityModelFactory.emptyPayloadMapping());

        final boolean replyTargetEnabled =
                jsonObject.getValue(JsonFields.REPLY_TARGET_ENABLED).orElse(DEFAULT_REPLY_TARGET_ENABLED);

        final ReplyTarget readReplyTarget =
                jsonObject.getValue(JsonFields.REPLY_TARGET)
                        .flatMap(ImmutableReplyTarget::fromJsonOptional)
                        .orElse(null);

        final Set<AcknowledgementLabel> declaredAcknowledgementLabels =
                jsonObject.getValue(JsonFields.DECLARED_ACKS)
                        .map(ImmutableSource::declaredAcksFromJson)
                        .orElse(Collections.emptySet());

        return new Builder()
                .addresses(readSources)
                .qos(readQos)
                .authorizationContext(readAuthorizationContext)
                .consumerCount(readConsumerCount)
                .index(index)
                .enforcement(readEnforcement)
                .acknowledgementRequests(readAcknowledgementRequests)
                .headerMapping(readHeaderMapping)
                .payloadMapping(readPayloadMapping)
                .replyTargetEnabled(replyTargetEnabled)
                .replyTarget(readReplyTarget)
                .declaredAcknowledgementLabels(declaredAcknowledgementLabels)
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutableSource that = (ImmutableSource) o;
        return consumerCount == that.consumerCount &&
                Objects.equals(addresses, that.addresses) &&
                Objects.equals(index, that.index) &&
                Objects.equals(qos, that.qos) &&
                Objects.equals(enforcement, that.enforcement) &&
                Objects.equals(acknowledgementRequests, that.acknowledgementRequests) &&
                Objects.equals(headerMapping, that.headerMapping) &&
                Objects.equals(payloadMapping, that.payloadMapping) &&
                Objects.equals(authorizationContext, that.authorizationContext) &&
                replyTargetEnabled == that.replyTargetEnabled &&
                Objects.equals(replyTarget, that.replyTarget) &&
                Objects.equals(declaredAcknowledgementLabels, that.declaredAcknowledgementLabels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, addresses, qos, consumerCount, authorizationContext, enforcement,
                acknowledgementRequests,
                headerMapping, payloadMapping, replyTargetEnabled, replyTarget, declaredAcknowledgementLabels);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "index=" + index +
                ", addresses=" + addresses +
                ", consumerCount=" + consumerCount +
                ", qos=" + qos +
                ", authorizationContext=" + authorizationContext +
                ", enforcement=" + enforcement +
                ", acknowledgementRequests=" + acknowledgementRequests +
                ", headerMapping=" + headerMapping +
                ", payloadMapping=" + payloadMapping +
                ", replyTargetEnabled=" + replyTargetEnabled +
                ", replyTarget=" + replyTarget +
                ", declaredAcknowledgementLabels=" + declaredAcknowledgementLabels +
                "]";
    }

    static Source migrateReplyTarget(final Source source, final ConnectionType connectionType) {
        final boolean isLegacySource = source.isReplyTargetEnabled() && !source.getReplyTarget().isPresent();
        final boolean supportsHeaders = ConnectionType.supportsHeaders(connectionType);
        if (isLegacySource && supportsHeaders) {
            return migrateReplyTargetWithHeaders(source);
        } else if (isLegacySource) {
            return migrateReplyTargetWithoutHeaders(source);
        } else {
            return source;
        }
    }

    private static Source migrateReplyTargetWithoutHeaders(final Source source) {
        return ConnectivityModelFactory.newSourceBuilder(source)
                .replyTarget(ReplyTarget.newBuilder().address(DEFAULT_REPLY_TARGET_ADDRESS).build())
                .build();
    }

    private static Source migrateReplyTargetWithHeaders(final Source source) {
        final Map<String, String> mergedMapping = new HashMap<>(DEFAULT_SOURCE_HEADER_MAPPING.getMapping());
        mergedMapping.putAll(source.getHeaderMapping().getMapping());
        final HeaderMapping mapping = ConnectivityModelFactory.newHeaderMapping(mergedMapping);

        return ConnectivityModelFactory.newSourceBuilder(source)
                .headerMapping(mapping)
                .replyTarget(ReplyTarget.newBuilder()
                        .address(DEFAULT_REPLY_TARGET_ADDRESS)
                        .headerMapping(DEFAULT_REPLY_TARGET_HEADER_MAPPING)
                        .build())
                .build();
    }

    private static Set<AcknowledgementLabel> declaredAcksFromJson(final JsonArray jsonArray) {
        return jsonArray.stream()
                .map(JsonValue::formatAsString)
                .map(AcknowledgementLabel::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static JsonArray declaredAcksToJson(final Set<AcknowledgementLabel> declaredAcks) {
        return declaredAcks.stream()
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
    }

    /**
     * Builder for {@code ImmutableSource}.
     */
    @NotThreadSafe
    static final class Builder implements SourceBuilder<Builder> {

        // required but changeable:
        @Nullable private Set<String> addresses = new LinkedHashSet<>();
        @Nullable private AuthorizationContext authorizationContext;

        // optional:
        @Nullable private Enforcement enforcement;
        private HeaderMapping headerMapping = ConnectivityModelFactory.emptyHeaderMapping();
        @Nullable private Integer qos = null;
        @Nullable private FilteredAcknowledgementRequest acknowledgementRequests;
        private boolean replyTargetEnabled = DEFAULT_REPLY_TARGET_ENABLED;
        @Nullable private ReplyTarget replyTarget;
        private Set<AcknowledgementLabel> declaredAcknowledgementLabels = Collections.emptySet();

        // optional with default:
        private PayloadMapping payloadMapping = ConnectivityModelFactory.emptyPayloadMapping();
        private int index = DEFAULT_INDEX;
        private int consumerCount = DEFAULT_CONSUMER_COUNT;

        Builder() {}

        public Builder(final Source source) {
            addresses(source.getAddresses())
                    .authorizationContext(source.getAuthorizationContext())
                    .enforcement(source.getEnforcement().orElse(null))
                    .headerMapping(source.getHeaderMapping())
                    .qos(source.getQos().orElse(null))
                    .acknowledgementRequests(source.getAcknowledgementRequests().orElse(null))
                    .replyTarget(source.getReplyTarget().orElse(null))
                    .replyTargetEnabled(source.isReplyTargetEnabled())
                    .declaredAcknowledgementLabels(source.getDeclaredAcknowledgementLabels())
                    .payloadMapping(source.getPayloadMapping())
                    .index(source.getIndex())
                    .consumerCount(source.getConsumerCount());
        }

        @Override
        public Builder addresses(final Set<String> addresses) {
            this.addresses = addresses;
            return this;
        }

        @Override
        public Builder address(final String address) {
            if (addresses == null) {
                addresses = new LinkedHashSet<>();
            }
            addresses.add(address);
            return this;
        }

        @Override
        public Builder consumerCount(final int consumerCount) {
            this.consumerCount = consumerCount;
            return this;
        }

        @Override
        public Builder index(final int index) {
            this.index = index;
            return this;
        }

        @Override
        public Builder qos(@Nullable final Integer qos) {
            this.qos = qos;
            return this;
        }

        @Override
        public Builder authorizationContext(final AuthorizationContext authorizationContext) {
            this.authorizationContext = ConditionChecker.checkNotNull(authorizationContext, "authorizationContext");
            return this;
        }

        @Override
        public Builder enforcement(@Nullable final Enforcement enforcement) {
            this.enforcement = enforcement;
            return this;
        }

        @Override
        public Builder acknowledgementRequests(final @Nullable FilteredAcknowledgementRequest acknowledgementRequests) {
            this.acknowledgementRequests = acknowledgementRequests;
            return this;
        }

        @Override
        public Builder headerMapping(@Nullable final HeaderMapping headerMapping) {
            this.headerMapping = headerMapping == null ? ConnectivityModelFactory.emptyHeaderMapping() : headerMapping;
            return this;
        }

        @Override
        public Builder payloadMapping(final PayloadMapping payloadMapping) {
            this.payloadMapping = payloadMapping;
            return this;
        }

        @Override
        public Builder replyTarget(@Nullable final ReplyTarget replyTarget) {
            this.replyTarget = replyTarget;
            return this;
        }

        @Override
        public Builder replyTargetEnabled(final boolean replyTargetEnabled) {
            this.replyTargetEnabled = replyTargetEnabled;
            return this;
        }

        @Override
        public Builder declaredAcknowledgementLabels(final Set<AcknowledgementLabel> declaredAcknowledgmentLabels) {
            declaredAcknowledgementLabels = declaredAcknowledgmentLabels;
            return this;
        }

        @Override
        public Source build() {
            return new ImmutableSource(this);
        }

    }

}
