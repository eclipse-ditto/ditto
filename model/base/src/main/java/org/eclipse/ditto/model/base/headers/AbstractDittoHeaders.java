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
package org.eclipse.ditto.model.base.headers;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Abstract immutable implementation of {@link DittoHeaders} which is heavily based on {@link AbstractMap}.
 */
@Immutable
@SuppressWarnings("squid:S2160")
public abstract class AbstractDittoHeaders extends AbstractMap<String, String> implements DittoHeaders {

    private final Map<String, String> headers;

    /**
     * Constructs a new {@code AbstractDittoHeaders} object.
     *
     * @param headers the key-value-pairs of the result.
     * @throws NullPointerException if {@code headers} is {@code null}.
     */
    protected AbstractDittoHeaders(final Map<String, String> headers) {
        checkNotNull(headers, "headers map");
        this.headers = Collections.unmodifiableMap(new HashMap<>(headers));
    }

    @Override
    public Optional<String> getCorrelationId() {
        return getStringForDefinition(DittoHeaderDefinition.CORRELATION_ID);
    }

    protected Optional<String> getStringForDefinition(final HeaderDefinition definition) {
        return Optional.ofNullable(headers.get(definition.getKey()));
    }

    @Override
    public Optional<String> getContentType() {
        return getStringForDefinition(DittoHeaderDefinition.CONTENT_TYPE);
    }

    @Override
    public Optional<JsonSchemaVersion> getSchemaVersion() {
        return getStringForDefinition(DittoHeaderDefinition.SCHEMA_VERSION)
                .map(Integer::valueOf)
                .flatMap(JsonSchemaVersion::forInt);
    }

    @Override
    public List<String> getAuthorizationSubjects() {
        final JsonArray jsonValueArray = getJsonArrayForDefinition(DittoHeaderDefinition.AUTHORIZATION_SUBJECTS);
        return jsonValueArray.stream()
                .map(JsonValue::asString)
                .collect(Collectors.toList());
    }

    protected JsonArray getJsonArrayForDefinition(final HeaderDefinition definition) {
        return getStringForDefinition(definition)
                .map(JsonFactory::newArray)
                .orElseGet(JsonFactory::newArray);
    }

    @Override
    public AuthorizationContext getAuthorizationContext() {
        final List<AuthorizationSubject> authSubjects = getAuthorizationSubjects()
                .stream()
                .map(AuthorizationModelFactory::newAuthSubject)
                .collect(Collectors.toList());
        return AuthorizationModelFactory.newAuthContext(authSubjects);
    }

    @Override
    public Set<String> getReadSubjects() {
        final JsonArray jsonValueArray = getJsonArrayForDefinition(DittoHeaderDefinition.READ_SUBJECTS);
        return jsonValueArray.stream()
                .map(JsonValue::asString)
                .collect(Collectors.toSet());
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
        boolean result = true;
        if (isExpectedBoolean(DittoHeaderDefinition.RESPONSE_REQUIRED, Boolean.FALSE)) {
            final String reqAckLabels = headers.getOrDefault(DittoHeaderDefinition.REQUESTED_ACK_LABELS.getKey(), "");
            result = !reqAckLabels.isEmpty();
        }
        return result;
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
        return expectedString.equalsIgnoreCase(headers.get(headerDefinition.getKey()));
    }

    /**
     * Resolve type of a header not defined in {@link DittoHeaderDefinition}.
     * Implementations should be fast because this method is called multiple times during serialization of each object.
     *
     * @param key Name of the specific header.
     * @return Header definition of the specific header.
     */
    protected abstract Optional<HeaderDefinition> getSpecificDefinitionByKey(CharSequence key);

    /**
     * @deprecated as of 1.1.0 please use {@link #isExpectedBoolean(HeaderDefinition, Boolean)} instead.
     */
    @Deprecated
    protected Optional<Boolean> getBooleanForDefinition(final HeaderDefinition definition) {
        return getStringForDefinition(definition)
                .map(JsonFactory::readFrom)
                .filter(JsonValue::isBoolean)
                .map(JsonValue::asBoolean);
    }

    @Override
    public boolean isDryRun() {
        return isExpectedBoolean(DittoHeaderDefinition.DRY_RUN, Boolean.TRUE);
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
    public Optional<String> getInboundPayloadMapper() {
        return getStringForDefinition(DittoHeaderDefinition.INBOUND_PAYLOAD_MAPPER);
    }

    @Override
    public Optional<Integer> getReplyTarget() {
        // This is an internal header. If NumberFormatException occurs then there is a bug.
        return getStringForDefinition(DittoHeaderDefinition.REPLY_TARGET).map(Integer::valueOf);
    }

    @Override
    public Set<AcknowledgementLabel> getRequestedAckLabels() {
        final JsonArray jsonValueArray = getJsonArrayForDefinition(DittoHeaderDefinition.REQUESTED_ACK_LABELS);
        return jsonValueArray.stream()
                .map(JsonValue::asString)
                .map(AcknowledgementLabel::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Optional<Duration> getTimeout() {
        return getStringForDefinition(DittoHeaderDefinition.TIMEOUT)
                .map(DittoDuration::fromTimeoutString)
                .map(DittoDuration::getDuration);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        forEach((key, value) -> {
            final Class<?> type = getSerializationTypeForKey(key);
            final JsonValue jsonValue = CharSequence.class.isAssignableFrom(type)
                    ? JsonFactory.newValue(value)
                    : JsonFactory.readFrom(value);
            jsonObjectBuilder.set(key, jsonValue);
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
        return headers.entrySet();
    }

    @Override
    public boolean isEntriesSizeGreaterThan(final long size) {
        checkArgument(size, s -> 0 <= size,
                () -> MessageFormat.format("The size to compare to must not be negative but it was <{0}>!", size));

        long quota = size;

        for (final Entry<String, String> entry : headers.entrySet()) {
            quota -= getEntryLength(entry);
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

        for (final Map.Entry<String, String> entry : getSortedEntriesByLength()) {
            quota -= getEntryLength(entry);
            if (quota < 0) {
                break;
            }
            builder.putHeader(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    /*
     * Returns the header entries sorted by their length. The sort order is ascending,
     * i. e. the smallest entry is the first.
     */
    @Nonnull
    private List<Entry<String, String>> getSortedEntriesByLength() {
        return headers.entrySet()
                .stream()
                .sorted(Comparator.comparingInt(AbstractDittoHeaders::getEntryLength))
                .collect(Collectors.toList());
    }

    private static int getEntryLength(final Map.Entry<String, String> entry) {
        final String key = entry.getKey();
        final String value = entry.getValue();

        return key.length() + value.length();
    }

    @Override
    public String toString() {
        return headers.toString();
    }

}
