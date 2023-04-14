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
package org.eclipse.ditto.base.model.headers;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Abstract immutable implementation of {@link org.eclipse.ditto.base.model.headers.DittoHeaders} which is heavily based on {@link java.util.AbstractMap}.
 */
@Immutable
@SuppressWarnings("squid:S2160")
public abstract class AbstractDittoHeaders implements DittoHeaders {

    final Map<String, Header> headers;

    /**
     * Constructs a new {@code AbstractDittoHeaders} object.
     *
     * @param headers the key-value-pairs of the result.
     * @throws NullPointerException if {@code headers} is {@code null}.
     */
    protected AbstractDittoHeaders(final Map<String, String> headers) {
        checkNotNull(headers, "headers");
        if (headers instanceof AbstractDittoHeaders) {
            // Share the map from the other AbstractDittoHeaders -- it is not modifiable. Otherwise case is not preserved.
            this.headers = ((AbstractDittoHeaders) headers).headers;
        } else {
            this.headers = indexByLowerCase(headers);
        }
    }

    /**
     * Construct a new {@code AbstractDittoHeaders} from a known case insensitive map.
     *
     * @param headers headers indexed by lower-case keys.
     * @param flag unused disambiguation parameter.
     */
    @SuppressWarnings("unused")
    protected AbstractDittoHeaders(final Map<String, Header> headers, final boolean flag) {
        checkNotNull(headers, "headers");
        this.headers = new LinkedHashMap<>(headers);
    }

    @Override
    public Map<String, String> asCaseSensitiveMap() {
        final LinkedHashMap<String, String> caseSensitiveMap = new LinkedHashMap<>();
        for (final Header header : headers.values()) {
            caseSensitiveMap.put(header.getKey(), header.getValue());
        }
        return caseSensitiveMap;
    }

    @Override
    public Set<String> keySet() {
        return headers.keySet();
    }

    @Override
    public Collection<String> values() {
        return headers.values().stream().map(Header::getValue).collect(Collectors.toList());
    }

    @Override
    public int size() {
        return headers.size();
    }

    @Override
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    @Override
    public boolean containsValue(final Object value) {
        if (!(value instanceof CharSequence)) {
            return false;
        } else {
            final String valueString = value.toString();
            return headers.values().stream().map(Header::getValue).anyMatch(valueString::equals);
        }
    }

    @Override
    public boolean containsKey(final Object key) {
        return key instanceof CharSequence && headers.containsKey(key.toString().toLowerCase());
    }

    @Override
    @Nullable
    public String get(final Object key) {
        if (key instanceof String) {
            return Optional.ofNullable(headers.get(key.toString().toLowerCase())).map(Header::getValue).orElse(null);
        } else {
            return null;
        }
    }

    private static JsonObject getAuthorizationContextAsJson(final Map<String, ? extends CharSequence> headers) {
        final CharSequence jsonObjectString = headers.get(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey());
        final JsonObject result;
        if (null != jsonObjectString) {
            result = JsonObject.of(jsonObjectString.toString());
        } else {
            result = JsonObject.empty();
        }
        return result;
    }

    @Override
    public Optional<String> getCorrelationId() {
        return getStringForDefinition(DittoHeaderDefinition.CORRELATION_ID);
    }

    protected Optional<String> getStringForDefinition(final HeaderDefinition definition) {
        return Optional.ofNullable(get(definition.getKey()));
    }

    @Override
    public Optional<String> getContentType() {
        return getStringForDefinition(DittoHeaderDefinition.CONTENT_TYPE);
    }

    @Override
    public Optional<String> getAccept() {
        return getStringForDefinition(DittoHeaderDefinition.ACCEPT);
    }

    @Override
    public Optional<ContentType> getDittoContentType() {
        return getContentType().map(ContentType::of);
    }

    @Override
    public Optional<JsonSchemaVersion> getSchemaVersion() {
        return getStringForDefinition(DittoHeaderDefinition.SCHEMA_VERSION)
                .map(Integer::valueOf)
                .flatMap(JsonSchemaVersion::forInt);
    }

    @Override
    public AuthorizationContext getAuthorizationContext() {
        return AuthorizationModelFactory.newAuthContext(getAuthorizationContextAsJson(headers));
    }

    protected JsonArray getJsonArrayForDefinition(final HeaderDefinition definition) {
        @Nullable final Header jsonArrayHeader = headers.get(definition.getKey());
        final JsonArray result;
        if (null != jsonArrayHeader) {
            result = JsonArray.of(jsonArrayHeader.getValue());
        } else {
            result = JsonArray.empty();
        }
        return result;
    }

    @Override
    public Set<AuthorizationSubject> getReadGrantedSubjects() {
        return getAuthorizationSubjectSet(DittoHeaderDefinition.READ_SUBJECTS);
    }

    private Set<AuthorizationSubject> getAuthorizationSubjectSet(final HeaderDefinition definition) {
        final JsonArray jsonValueArray = getJsonArrayForDefinition(definition);
        return jsonValueArray.stream()
                .map(JsonValue::asString)
                .map(AuthorizationSubject::newInstance)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<AuthorizationSubject> getReadRevokedSubjects() {
        return getAuthorizationSubjectSet(DittoHeaderDefinition.READ_REVOKED_SUBJECTS);
    }

    @Override
    public Optional<String> getChannel() {
        return getStringForDefinition(DittoHeaderDefinition.CHANNEL);
    }

    @Override
    public boolean isResponseRequired() {
        return !isExpectedBoolean(DittoHeaderDefinition.RESPONSE_REQUIRED, Boolean.FALSE);
    }

    /**
     * Indicates whether the value for the given HeaderDefinition evaluates to the given expected boolean.
     * If no value exists for the given HeaderDefinition or if the value is not a valid String representation of the
     * expected boolean, {@code false} will be returned.
     *
     * @param headerDefinition the definition of a supposed boolean value.
     * @param expected the boolean value which is expected to be set for {@code headerDefinition}.
     * @return {@code true} if and only if the header value for {@code headerDefinition} evaluates to {@code expected}.
     * @since 1.1.0
     */
    protected boolean isExpectedBoolean(final HeaderDefinition headerDefinition, final Boolean expected) {
        final String expectedString = expected.toString();

        // There is no need to do JSON parsing of the header value as String representations of boolean values look the
        // same for plain Java and JSON.
        return Optional.ofNullable(headers.get(headerDefinition.getKey()))
                .map(Header::getValue)
                .filter(expectedString::equalsIgnoreCase)
                .isPresent();
    }

    /**
     * Resolve type of a header not defined in {@link DittoHeaderDefinition}.
     * Implementations should be fast because this method is called multiple times during serialization of each object.
     *
     * @param key Name of the specific header.
     * @return Header definition of the specific header.
     */
    protected abstract Optional<HeaderDefinition> getSpecificDefinitionByKey(CharSequence key);

    @Override
    public boolean isDryRun() {
        return isExpectedBoolean(DittoHeaderDefinition.DRY_RUN, Boolean.TRUE);
    }

    @Override
    public boolean isSudo() {
        return isExpectedBoolean(DittoHeaderDefinition.DITTO_SUDO, Boolean.TRUE);
    }

    @Override
    public boolean shouldRetrieveDeleted() {
        return isExpectedBoolean(DittoHeaderDefinition.DITTO_RETRIEVE_DELETED, Boolean.TRUE);
    }

    @Override
    public Optional<String> getCondition() {
        return getStringForDefinition(DittoHeaderDefinition.CONDITION);
    }

    @Override
    public Optional<String> getLiveChannelCondition() {
        return getStringForDefinition(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION);
    }

    @Override
    public boolean didLiveChannelConditionMatch() {
        return isExpectedBoolean(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED, Boolean.TRUE);
    }

    @Override
    public Optional<String> getOrigin() {
        return getStringForDefinition(DittoHeaderDefinition.ORIGIN);
    }

    @Override
    public Optional<EntityTag> getETag() {
        return getStringForDefinition(DittoHeaderDefinition.ETAG)
                .map(EntityTag::fromString);
    }

    @Override
    public Optional<EntityTagMatchers> getIfMatch() {
        return getStringForDefinition(DittoHeaderDefinition.IF_MATCH)
                .map(EntityTagMatchers::fromCommaSeparatedString);
    }

    @Override
    public Optional<EntityTagMatchers> getIfNoneMatch() {
        return getStringForDefinition(DittoHeaderDefinition.IF_NONE_MATCH)
                .map(EntityTagMatchers::fromCommaSeparatedString);
    }

    @Override
    public Optional<IfEqualOption> getIfEqual() {
        return getStringForDefinition(DittoHeaderDefinition.IF_EQUAL)
                .flatMap(IfEqualOption::forOption);
    }

    @Override
    public Optional<String> getInboundPayloadMapper() {
        return getStringForDefinition(DittoHeaderDefinition.INBOUND_PAYLOAD_MAPPER);
    }

    @Override
    public Optional<Integer> getReplyTarget() {
        // This is an internal header. If NumberFormatException occurs then there is a bug.
        return getStringForDefinition(DittoHeaderDefinition.REPLY_TARGET).map(Integer::valueOf);
    }

    @Override
    public Collection<ResponseType> getExpectedResponseTypes() {
        final JsonArray jsonValueArray = getJsonArrayForDefinition(DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES);
        return jsonValueArray.stream()
                .map(JsonValue::asString)
                .map(ResponseType::fromName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList()); // toList() to keep original order
    }

    @Override
    public Set<AcknowledgementRequest> getAcknowledgementRequests() {
        final JsonArray jsonValueArray = getJsonArrayForDefinition(DittoHeaderDefinition.REQUESTED_ACKS);
        return jsonValueArray.stream()
                .map(JsonValue::asString)
                .map(AcknowledgementRequest::parseAcknowledgementRequest)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Optional<Duration> getTimeout() {
        return getStringForDefinition(DittoHeaderDefinition.TIMEOUT)
                .map(DittoDuration::parseDuration)
                .map(DittoDuration::getDuration);
    }

    @Override
    public Optional<LiveChannelTimeoutStrategy> getLiveChannelTimeoutStrategy() {
        return getStringForDefinition(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY)
                .flatMap(LiveChannelTimeoutStrategy::forHeaderValue);
    }

    @Override
    public MetadataHeaders getMetadataHeadersToPut() {
        final String metadataHeaderValue = getOrDefault(DittoHeaderDefinition.PUT_METADATA.getKey(), "");
        return MetadataHeaders.parseMetadataHeaders(metadataHeaderValue);
    }

    @Override
    public Set<JsonPointer> getMetadataFieldsToGet() {
        final String metadataFieldSelector = getOrDefault(DittoHeaderDefinition.GET_METADATA.getKey(), "");
        return JsonFactory.newFieldSelector(metadataFieldSelector,
                JsonParseOptions.newBuilder().withoutUrlDecoding().build()).getPointers();
    }

    @Override
    public Set<JsonPointer> getMetadataFieldsToDelete() {
        final String metadataFieldSelector = getOrDefault(DittoHeaderDefinition.DELETE_METADATA.getKey(), "");
        return JsonFactory.newFieldSelector(metadataFieldSelector,
                JsonParseOptions.newBuilder().withoutUrlDecoding().build()).getPointers();
    }

    @Override
    public boolean isAllowPolicyLockout() {
        return isExpectedBoolean(DittoHeaderDefinition.ALLOW_POLICY_LOCKOUT, Boolean.TRUE);
    }

    @Override
    public Set<String> getJournalTags() {
        final JsonArray jsonValueArray = getJsonArrayForDefinition(DittoHeaderDefinition.EVENT_JOURNAL_TAGS);
        return jsonValueArray.stream()
                .map(JsonValue::asString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Optional<String> getTraceParent() {
        return getStringForDefinition(DittoHeaderDefinition.W3C_TRACEPARENT);
    }

    @Override
    public Optional<String> getTraceState() {
        return getStringForDefinition(DittoHeaderDefinition.W3C_TRACESTATE);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonObject.newBuilder();
        headers.forEach((key, header) -> {
            final Class<?> type = getSerializationTypeForKey(key);
            final JsonValue jsonValue = CharSequence.class.isAssignableFrom(type)
                    ? JsonValue.of(header.getValue())
                    : JsonFactory.readFrom(header.getValue());
            jsonObjectBuilder.set(header.getKey(), jsonValue);
        });
        return jsonObjectBuilder.build();
    }

    private Class<?> getSerializationTypeForKey(final CharSequence key) {
        return getSpecificDefinitionByKey(key)
                .map(HeaderDefinition::getSerializationType)
                .orElseGet(() -> DittoHeaderDefinition.forKey(key)
                        .map(HeaderDefinition::getSerializationType)
                        .orElse(String.class));
    }

    @Override
    public String put(final String key, final String value) {
        throw newUnsupportedOperationException();
    }

    private static UnsupportedOperationException newUnsupportedOperationException() {
        return new UnsupportedOperationException("Ditto Headers are immutable!");
    }

    @Override
    public String remove(final Object key) {
        throw newUnsupportedOperationException();
    }

    @Override
    public void putAll(@Nonnull final Map<? extends String, ? extends String> m) {
        throw newUnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw newUnsupportedOperationException();
    }

    @Nonnull
    @Override
    public Set<Entry<String, String>> entrySet() {
        final Set<Entry<String, String>> linkedHashSet = headers.entrySet()
                .stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().getValue()))
                .collect(Collectors.toCollection(LinkedHashSet<Entry<String, String>>::new));
        return Collections.unmodifiableSet(linkedHashSet);
    }

    @Override
    public boolean isEntriesSizeGreaterThan(final long size) {
        checkArgument(size, s -> 0 <= size,
                () -> MessageFormat.format("The size to compare to must not be negative but it was <{0}>!", size));

        long quota = size;

        for (final Header header : headers.values()) {
            quota -= getHeaderLength(header);
            if (0 > quota) {
                return true;
            }
        }
        return false;
    }

    @Override
    public DittoHeaders truncate(final long maxSizeBytes) {
        checkArgument(maxSizeBytes, s -> 0 <= maxSizeBytes,
                () -> MessageFormat.format("The max size bytes must not be negative but it was <{0}>!", maxSizeBytes));

        final DittoHeadersBuilder<?, ?> builder = DittoHeaders.newBuilder();
        long quota = maxSizeBytes;

        for (final Header entry : getSortedHeadersByLength()) {
            quota -= getHeaderLength(entry);
            if (quota < 0) {
                break;
            }
            builder.putHeader(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    /*
     * Returns the header entries sorted by their length. The sort order is ascending,
     * i.e. the smallest entry is the first.
     */
    @Nonnull
    private List<Header> getSortedHeadersByLength() {
        return headers.values()
                .stream()
                .sorted(Comparator.comparingInt(AbstractDittoHeaders::getHeaderLength))
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return headers.toString();
    }

    @Override
    public int hashCode() {
        return headers.hashCode();
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof AbstractDittoHeaders) {
            final AbstractDittoHeaders that = (AbstractDittoHeaders) other;
            return Objects.equals(headers, that.headers);
        } else if (other instanceof Map<?, ?>) {
            return headers.equals(other);
        } else {
            return false;
        }
    }

    private static int getHeaderLength(final Header header) {
        return header.getKey().length() + header.getValue().length();
    }

    private static Map<String, Header> indexByLowerCase(final Map<String, String> map) {
        final Map<String, Header> headers = new LinkedHashMap<>();
        map.forEach((key, value) -> headers.put(key.toLowerCase(), Header.of(key, value)));
        return Collections.unmodifiableMap(headers);
    }
}
