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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeader;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.JsonValueContainer;

/**
 * An abstract base implementation for subclasses of {@link org.eclipse.ditto.base.model.headers.DittoHeadersBuilder}.
 * This implementation does already most of the work including header value validation. Insertion order and
 * re-insertion order is maintained via a linked hash map. Since Java linked hash map does not maintain
 * re-insertion order, each entry is removed from the map before they are added.
 */
@NotThreadSafe
public abstract class AbstractDittoHeadersBuilder<S extends AbstractDittoHeadersBuilder<S, R>, R extends DittoHeaders>
        implements DittoHeadersBuilder<S, R> {

    private static final JsonParseOptions JSON_FIELD_SELECTOR_PARSE_OPTIONS = JsonFactory.newParseOptionsBuilder()
            .withoutUrlDecoding()
            .build();

    private static final Map<String, HeaderDefinition> BUILT_IN_DEFINITIONS;

    static {
        final DittoHeaderDefinition[] dittoHeaderDefinitions = DittoHeaderDefinition.values();
        final Map<String, HeaderDefinition> definitions = new LinkedHashMap<>(dittoHeaderDefinitions.length);
        for (final DittoHeaderDefinition dittoHeaderDefinition : dittoHeaderDefinitions) {
            definitions.put(dittoHeaderDefinition.getKey(), dittoHeaderDefinition);
        }
        BUILT_IN_DEFINITIONS = Collections.unmodifiableMap(definitions);
    }

    protected final S myself;
    private final Map<String, Header> headers;
    private final Map<String, HeaderDefinition> definitions;
    private MetadataHeaders metadataHeaders;
    private JsonFieldSelector getMetadataFieldSelector;
    private JsonFieldSelector deleteMetadataFieldSelector;

    /**
     * Constructs a new {@code AbstractDittoHeadersBuilder} object.
     *
     * @param initialHeaders initial key-value-pairs or an empty map.
     * @param definitions a collection of all well known {@link org.eclipse.ditto.base.model.headers.HeaderDefinition}s
     * of this builder. The definitions are used for header value validation.
     * @param selfType this type is used to simulate the "self type" of the returned object for Method Chaining of
     * the builder methods.
     * @throws NullPointerException if any argument is {@code null}.
     */
    @SuppressWarnings("unchecked")
    protected AbstractDittoHeadersBuilder(final Map<String, String> initialHeaders,
            final Collection<? extends HeaderDefinition> definitions, final Class<?> selfType) {

        checkNotNull(initialHeaders, "initial headers");
        checkNotNull(definitions, "header definitions");
        validateValueTypes(initialHeaders, definitions); // this constructor does validate the known value types
        myself = (S) selfType.cast(this);
        headers = preserveCaseSensitivity(initialHeaders);
        metadataHeaders = MetadataHeaders.newInstance();
        metadataHeaders.addAll(extractMetadataHeaders(headers));
        this.definitions = getHeaderDefinitionsAsMap(definitions);
        getMetadataFieldSelector = extractMetadataFieldSelector(headers, DittoHeaderDefinition.GET_METADATA);
        deleteMetadataFieldSelector = extractMetadataFieldSelector(headers, DittoHeaderDefinition.DELETE_METADATA);
    }

    private static MetadataHeaders extractMetadataHeaders(final Map<String, Header> headers) {
        final MetadataHeaders result;
        final CharSequence putMetadataHeaderCharSequence = headers.remove(DittoHeaderDefinition.PUT_METADATA.getKey());
        if (null != putMetadataHeaderCharSequence) {
            result = MetadataHeaders.parseMetadataHeaders(putMetadataHeaderCharSequence);
        } else {
            result = MetadataHeaders.newInstance();
        }
        return result;
    }

    private static JsonFieldSelector extractMetadataFieldSelector(final Map<String, Header> headers,
            final DittoHeaderDefinition headerDefinition) {
        final JsonFieldSelector result;
        final CharSequence metadataFieldSelector = headers.remove(headerDefinition.getKey());

        if (null != metadataFieldSelector) {
            result = JsonFactory.newFieldSelector(metadataFieldSelector.toString(),
                    JSON_FIELD_SELECTOR_PARSE_OPTIONS);
        } else {
            result = JsonFactory.emptyFieldSelector();
        }
        return result;
    }

    private static Map<String, HeaderDefinition> getHeaderDefinitionsAsMap(
            final Collection<? extends HeaderDefinition> headerDefinitions) {

        final DittoHeaderDefinition[] dittoHeaderDefinitions = DittoHeaderDefinition.values();
        final Map<String, HeaderDefinition> result =
                new LinkedHashMap<>(headerDefinitions.size() + dittoHeaderDefinitions.length);
        for (final HeaderDefinition definition : headerDefinitions) {
            result.put(definition.getKey(), definition);
        }
        result.putAll(BUILT_IN_DEFINITIONS);
        return result;
    }

    /**
     * Constructs a new {@code AbstractDittoHeadersBuilder} object based on an existing {@code DittoHeaders} instance
     * applying a performance optimization: skipping the validation of values types as we can be sure that they already
     * are valid when being passed in as DittoHeaders.
     *
     * @param initialHeaders initial DittoHeaders.
     * @param definitions a collection of all well known {@link org.eclipse.ditto.base.model.headers.HeaderDefinition}s
     * of this builder. The definitions are used for header value validation.
     * @param selfType this type is used to simulate the "self type" of the returned object for Method Chaining of
     * the builder methods.
     * @throws NullPointerException if any argument is {@code null}.
     */
    @SuppressWarnings("unchecked")
    protected AbstractDittoHeadersBuilder(final R initialHeaders,
            final Collection<? extends HeaderDefinition> definitions, final Class<?> selfType) {

        checkNotNull(initialHeaders, "initialHeaders");
        checkNotNull(definitions, "definitions");
        myself = (S) selfType.cast(this);
        headers = preserveCaseSensitivity(initialHeaders);
        metadataHeaders = MetadataHeaders.newInstance();
        metadataHeaders.addAll(extractMetadataHeaders(headers));
        this.definitions = getHeaderDefinitionsAsMap(definitions);
        getMetadataFieldSelector = extractMetadataFieldSelector(headers, DittoHeaderDefinition.GET_METADATA);
        deleteMetadataFieldSelector = extractMetadataFieldSelector(headers, DittoHeaderDefinition.DELETE_METADATA);
    }

    /**
     * Validates the values of the specified headers with the help of the specified definitions.
     *
     * @param headers the key-value-pairs to be validated.
     * @param definitions perform the actual validation.
     */
    protected void validateValueTypes(final Map<String, String> headers,
            final Collection<? extends HeaderDefinition> definitions) {

        for (final HeaderDefinition definition : definitions) {
            final String value = headers.get(definition.getKey());
            if (null != value) {
                definition.validateValue(value);
            }
        }
    }

    protected static Map<String, String> toMap(final JsonValueContainer<JsonField> jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        final Map<String, String> result = new LinkedHashMap<>(jsonObject.getSize());
        jsonObject.forEach(jsonField -> {
            final JsonValue jsonValue = jsonField.getValue();
            if (!jsonValue.isNull()) {
                final String stringValue = jsonValue.isString() ? jsonValue.asString() : jsonValue.toString();
                result.put(jsonField.getKeyName(), stringValue);
            }
        });

        return result;
    }

    @Override
    public S correlationId(@Nullable final CharSequence correlationId) {
        // special handling: as pass-through header, preserve capitalization of original header.
        final String key = DittoHeaderDefinition.CORRELATION_ID.getKey();
        if (correlationId != null) {
            checkNotEmpty(correlationId, "correlationId");
            final Header previousCorrelationId = headers.remove(key);
            if (previousCorrelationId != null) {
                headers.put(key, Header.of(previousCorrelationId.getKey(), correlationId.toString()));
            } else {
                headers.put(key, Header.of(key, correlationId.toString()));
            }
        } else {
            headers.remove(key);
        }
        return myself;
    }

    /**
     * Puts the specified CharSequence value to this builder using the key of the specified definition. If the value
     * is {@code null} a possibly existing value for the same key is removed; thus putting a {@code null} value is same
     * as removing the key-value-pair.
     *
     * @param definition provides the key to be associated with {@code value}.
     * @param value the value to be associated with the key of {@code definition}.
     */
    protected void putCharSequence(final HeaderDefinition definition, @Nullable final CharSequence value) {
        if (null != value) {
            checkNotEmpty(value, definition.getKey());
            headers.remove(definition.getKey());
            headers.put(definition.getKey(), Header.of(definition.getKey(), value.toString()));
        } else {
            removeHeader(definition.getKey());
        }
    }

    @Override
    public S schemaVersion(@Nullable final JsonSchemaVersion schemaVersion) {
        if (null != schemaVersion) {
            putCharSequence(DittoHeaderDefinition.SCHEMA_VERSION, schemaVersion.toString());
        } else {
            removeHeader(DittoHeaderDefinition.SCHEMA_VERSION.getKey());
        }
        return myself;
    }

    @Override
    public S authorizationContext(@Nullable final AuthorizationContext authorizationContext) {
        if (null != authorizationContext) {
            putJsonValue(DittoHeaderDefinition.AUTHORIZATION_CONTEXT, authorizationContext.toJson());
        } else {
            removeHeader(DittoHeaderDefinition.AUTHORIZATION_CONTEXT.getKey());
        }
        return myself;
    }

    @Override
    public S replyTarget(@Nullable final Integer replyTarget) {
        if (replyTarget != null) {
            putCharSequence(DittoHeaderDefinition.REPLY_TARGET, String.valueOf(replyTarget));
        } else {
            removeHeader(DittoHeaderDefinition.REPLY_TARGET.getKey());
        }
        return myself;
    }

    @Override
    public S expectedResponseTypes(final ResponseType... responseTypes) {
        checkNotNull(responseTypes, "responseTypes");
        final List<String> expectedResponseTypes = Arrays.stream(responseTypes)
                .map(ResponseType::getName)
                .collect(Collectors.toList());
        putStringCollection(DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES, expectedResponseTypes);
        return myself;
    }

    @Override
    public S expectedResponseTypes(final Collection<ResponseType> responseTypes) {
        checkNotNull(responseTypes, "responseTypes");
        if (!responseTypes.isEmpty()) {
            final List<String> expectedResponseTypes = responseTypes.stream()
                    .map(ResponseType::getName)
                    .collect(Collectors.toList());
            putStringCollection(DittoHeaderDefinition.EXPECTED_RESPONSE_TYPES, expectedResponseTypes);
        }
        return myself;
    }

    protected void putStringCollection(final HeaderDefinition definition, final Collection<String> collection) {
        checkNotNull(collection, definition.getKey());
        putJsonValue(definition, toJsonValueArray(collection));
    }

    private static JsonValue toJsonValueArray(final Collection<String> stringCollection) {
        return stringCollection.stream()
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray());
    }

    private void putJsonValue(final HeaderDefinition definition, final JsonValue jsonValue) {
        if (!jsonValue.isNull()) {
            putCharSequence(definition, jsonValue.isString() ? jsonValue.asString() : jsonValue.toString());
        }
    }

    @Override
    public S readGrantedSubjects(final Collection<AuthorizationSubject> readGrantedSubjects) {
        putAuthorizationSubjectCollection(readGrantedSubjects, DittoHeaderDefinition.READ_SUBJECTS);
        return myself;
    }

    private void putAuthorizationSubjectCollection(final Collection<AuthorizationSubject> authorizationSubjects,
            final HeaderDefinition definition) {

        checkNotNull(authorizationSubjects, definition.getKey());
        final JsonArray authorizationSubjectIdsJsonArray = authorizationSubjects.stream()
                .map(AuthorizationSubject::getId)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray());
        putJsonValue(definition, authorizationSubjectIdsJsonArray);
    }

    @Override
    public S readRevokedSubjects(final Collection<AuthorizationSubject> readRevokedSubjects) {
        putAuthorizationSubjectCollection(readRevokedSubjects, DittoHeaderDefinition.READ_REVOKED_SUBJECTS);
        return myself;
    }

    @Override
    public S channel(@Nullable final CharSequence channel) {
        final DittoHeaderDefinition headerDefinition = DittoHeaderDefinition.CHANNEL;
        if (null != channel) {
            final ValueValidator dittoChannelValidator = HeaderValueValidators.getDittoChannelValidator();
            dittoChannelValidator.accept(headerDefinition, channel);
            putCharSequence(headerDefinition, channel.toString().trim().toLowerCase(Locale.ENGLISH));
        } else {
            removeHeader(headerDefinition.getKey());
        }
        return myself;
    }

    @Override
    public S responseRequired(final boolean responseRequired) {
        putBoolean(DittoHeaderDefinition.RESPONSE_REQUIRED, responseRequired);
        return myself;
    }

    protected void putBoolean(final HeaderDefinition definition, final boolean value) {
        putJsonValue(definition, JsonFactory.newValue(value));
    }

    @Override
    public S dryRun(final boolean dryRun) {
        putBoolean(DittoHeaderDefinition.DRY_RUN, dryRun);
        return myself;
    }

    @Override
    public S origin(final CharSequence origin) {
        putCharSequence(DittoHeaderDefinition.ORIGIN, origin);
        return myself;
    }

    @Override
    public S contentType(@Nullable final CharSequence contentType) {
        putCharSequence(DittoHeaderDefinition.CONTENT_TYPE, contentType);
        return myself;
    }

    @Override
    public S contentType(@Nullable final ContentType contentType) {
        if (null != contentType) {
            putCharSequence(DittoHeaderDefinition.CONTENT_TYPE, contentType.getValue());
        } else {
            removeHeader(DittoHeaderDefinition.CONTENT_TYPE.getKey());
        }
        return myself;
    }

    @Override
    public S accept(@Nullable final CharSequence accept) {
        putCharSequence(DittoHeaderDefinition.ACCEPT, accept);
        return myself;
    }

    @Override
    public S eTag(final EntityTag eTag) {
        putCharSequence(DittoHeaderDefinition.ETAG, eTag.toString());
        return myself;
    }

    @Override
    public S ifMatch(final EntityTagMatchers entityTags) {
        putCharSequence(DittoHeaderDefinition.IF_MATCH, entityTags.toString());
        return myself;
    }

    @Override
    public S ifNoneMatch(final EntityTagMatchers entityTags) {
        putCharSequence(DittoHeaderDefinition.IF_NONE_MATCH, entityTags.toString());
        return myself;
    }

    @Override
    public S ifEqual(final IfEqualOption ifEqualOption) {
        putCharSequence(DittoHeaderDefinition.IF_EQUAL, ifEqualOption.toString());
        return myself;
    }

    @Override
    public S inboundPayloadMapper(@Nullable final String inboundPayloadMapperId) {
        putCharSequence(DittoHeaderDefinition.INBOUND_PAYLOAD_MAPPER, inboundPayloadMapperId);
        return myself;
    }

    @Override
    public S acknowledgementRequests(final Collection<AcknowledgementRequest> acknowledgementRequests) {
        checkNotNull(acknowledgementRequests, "acknowledgementRequests");
        putJsonValue(DittoHeaderDefinition.REQUESTED_ACKS, acknowledgementRequests.stream()
                .map(AcknowledgementRequest::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray()));
        return myself;
    }

    @Override
    public S acknowledgementRequest(final AcknowledgementRequest acknowledgementRequest,
            final AcknowledgementRequest... furtherAcknowledgementRequests) {

        checkNotNull(acknowledgementRequest, "acknowledgementRequest");
        checkNotNull(furtherAcknowledgementRequests, "furtherAcknowledgementRequests");
        final Collection<AcknowledgementRequest> ackRequests =
                new ArrayList<>(1 + furtherAcknowledgementRequests.length);

        ackRequests.add(acknowledgementRequest);
        Collections.addAll(ackRequests, furtherAcknowledgementRequests);
        return acknowledgementRequests(ackRequests);
    }

    @Override
    public S timeout(@Nullable final CharSequence timeoutStr) {
        if (null != timeoutStr) {
            return timeout(tryToParseDuration(timeoutStr));
        }
        return timeout((DittoDuration) null);
    }

    private static DittoDuration tryToParseDuration(final CharSequence duration) {
        try {
            return DittoDuration.parseDuration(duration);
        } catch (final IllegalArgumentException e) {
            throw DittoHeaderInvalidException.newInvalidTypeBuilder(DittoHeaderDefinition.TIMEOUT.getKey(), duration,
                    "duration").build();
        }
    }

    @Override
    public S timeout(@Nullable final Duration timeout) {
        if (null != timeout) {
            return timeout(DittoDuration.of(timeout));
        }
        return timeout((DittoDuration) null);
    }

    private S timeout(@Nullable final DittoDuration timeout) {
        final DittoHeaderDefinition definition = DittoHeaderDefinition.TIMEOUT;
        if (null != timeout) {
            putCharSequence(definition, timeout.toString());
        } else {
            removeHeader(definition.getKey());
        }
        return myself;
    }

    @Override
    public S putMetadata(final MetadataHeaderKey key, final JsonValue value) {
        metadataHeaders.add(MetadataHeader.of(key, value));
        return myself;
    }

    @Override
    public S allowPolicyLockout(final boolean allowPolicyLockout) {
        putBoolean(DittoHeaderDefinition.ALLOW_POLICY_LOCKOUT, allowPolicyLockout);
        return myself;
    }

    @Override
    public S journalTags(final Collection<String> journalTags) {
        putJsonValue(DittoHeaderDefinition.EVENT_JOURNAL_TAGS, journalTags.stream()
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray()));
        return myself;
    }

    @Override
    public S condition(final String condition) {
        putCharSequence(DittoHeaderDefinition.CONDITION, condition);
        return myself;
    }

    @Override
    public S liveChannelCondition(@Nullable final String liveChannelCondition) {
        putCharSequence(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION, liveChannelCondition);
        return myself;
    }

    @Override
    public S putHeader(final CharSequence key, final CharSequence value) {
        validateKey(key);
        checkNotNull(value, "value");
        final String keyString = key.toString().toLowerCase();
        validateValueType(keyString, value);
        if (isPutMetadataKey(keyString)) {
            metadataHeaders = MetadataHeaders.parseMetadataHeaders(value);
        } else if (isGetMetadataKey(keyString)) {
            getMetadataFieldSelector =
                    JsonFactory.newFieldSelector(value.toString(), JSON_FIELD_SELECTOR_PARSE_OPTIONS);
        } else if (isDeleteMetadataKey(keyString)) {
            deleteMetadataFieldSelector =
                    JsonFactory.newFieldSelector(value.toString(), JSON_FIELD_SELECTOR_PARSE_OPTIONS);
        } else if (DittoHeaderDefinition.CORRELATION_ID.getKey().equals(keyString)) {
            correlationId(value);
        } else {
            headers.remove(keyString);
            headers.put(keyString, Header.of(key.toString(), value.toString()));
        }
        return myself;
    }

    private static void validateKey(final CharSequence key) {
        argumentNotEmpty(key, "key");
    }

    protected void validateValueType(final CharSequence key, final CharSequence value) {
        @Nullable final HeaderDefinition headerDefinition = definitions.get(key.toString());
        if (null != headerDefinition) {
            headerDefinition.validateValue(value);
        }
    }

    private static boolean isPutMetadataKey(final CharSequence key) {
        return Objects.equals(DittoHeaderDefinition.PUT_METADATA.getKey(), key.toString());
    }

    private static boolean isGetMetadataKey(final CharSequence key) {
        return Objects.equals(DittoHeaderDefinition.GET_METADATA.getKey(), key.toString());
    }

    private static boolean isDeleteMetadataKey(final CharSequence key) {
        return Objects.equals(DittoHeaderDefinition.DELETE_METADATA.getKey(), key.toString());
    }

    @Override
    public S putHeaders(final Map<String, String> headers) {
        checkNotNull(headers, "headers");
        headers.forEach(this::putHeader);
        return myself;
    }

    @Override
    public S removeHeader(final CharSequence key) {
        validateKey(key);
        final String keyString = key.toString().toLowerCase();
        headers.remove(keyString);
        if (isPutMetadataKey(keyString)) {
            metadataHeaders.clear();
        }
        if (isGetMetadataKey(keyString)) {
            getMetadataFieldSelector = JsonFactory.emptyFieldSelector();
        }
        if (isDeleteMetadataKey(keyString)) {
            deleteMetadataFieldSelector = JsonFactory.emptyFieldSelector();
        }

        return myself;
    }

    @Override
    public S removePreconditionHeaders() {
        headers.remove(DittoHeaderDefinition.IF_MATCH.getKey());
        headers.remove(DittoHeaderDefinition.IF_NONE_MATCH.getKey());
        return myself;
    }

    @Override
    public S traceparent(@Nullable final CharSequence traceparent) {
        if (traceparent != null) {
            putCharSequence(DittoHeaderDefinition.W3C_TRACEPARENT, traceparent);
        }
        return myself;
    }

    @Override
    public S tracestate(@Nullable final CharSequence tracestate) {
        if (tracestate != null && tracestate.length() > 0) {
            putCharSequence(DittoHeaderDefinition.W3C_TRACESTATE, tracestate);
        }
        return myself;
    }

    @Override
    public R build() {
        putMetadataHeadersToRegularHeaders();
        putGetMetadataFieldSelectorToRegularHeaders();
        putDeleteMetadataFieldSelectorToRegularHeaders();

        final ImmutableDittoHeaders dittoHeaders = ImmutableDittoHeaders.fromBuilder(headers);
        return doBuild(dittoHeaders);
    }

    private void putMetadataHeadersToRegularHeaders() {
        if (!metadataHeaders.isEmpty()) {
            headers.put(DittoHeaderDefinition.PUT_METADATA.getKey(),
                    Header.of(DittoHeaderDefinition.PUT_METADATA.getKey(), metadataHeaders.toJsonString()));
        }
    }

    private void putGetMetadataFieldSelectorToRegularHeaders() {
        if (!getMetadataFieldSelector.isEmpty()) {
            headers.put(DittoHeaderDefinition.GET_METADATA.getKey(),
                    Header.of(DittoHeaderDefinition.GET_METADATA.getKey(), getMetadataFieldSelector.toString()));
        }
    }

    private void putDeleteMetadataFieldSelectorToRegularHeaders() {
        if (!deleteMetadataFieldSelector.isEmpty()) {
            headers.put(DittoHeaderDefinition.DELETE_METADATA.getKey(),
                    Header.of(DittoHeaderDefinition.DELETE_METADATA.getKey(), deleteMetadataFieldSelector.toString()));
        }
    }

    @Override
    public String toString() {
        putMetadataHeadersToRegularHeaders();
        putGetMetadataFieldSelectorToRegularHeaders();
        putDeleteMetadataFieldSelectorToRegularHeaders();
        return headers.toString();
    }

    protected abstract R doBuild(DittoHeaders dittoHeaders);

    private static Map<String, Header> preserveCaseSensitivity(final Map<String, String> headers) {
        if (headers instanceof AbstractDittoHeaders) {
            return new LinkedHashMap<>(((AbstractDittoHeaders) headers).headers);
        } else {
            final LinkedHashMap<String, Header> result = new LinkedHashMap<>();
            headers.forEach((k, v) -> result.put(k.toLowerCase(), Header.of(k, v)));
            return result;
        }
    }

}
