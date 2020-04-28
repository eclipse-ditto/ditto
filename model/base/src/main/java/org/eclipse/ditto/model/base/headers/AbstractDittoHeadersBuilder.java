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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.JsonValueContainer;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * An abstract base implementation for subclasses of {@link DittoHeadersBuilder}. This implementation does already
 * most of the work including header value validation.
 */
@NotThreadSafe
public abstract class AbstractDittoHeadersBuilder<S extends AbstractDittoHeadersBuilder, R extends DittoHeaders>
        implements DittoHeadersBuilder<S, R> {

    protected final S myself;
    private final Map<String, String> headers;
    private final Collection<HeaderDefinition> definitions;

    /**
     * Constructs a new {@code AbstractDittoHeadersBuilder} object.
     *
     * @param initialHeaders initial key-value-pairs or an empty map.
     * @param definitions a collection of all well known {@link HeaderDefinition}s of this builder. The definitions
     * are used for header value validation.
     * @param selfType this type is used to simulate the "self type" of the returned object for Method Chaining of
     * the builder methods.
     * @throws NullPointerException if any argument is {@code null}.
     */
    @SuppressWarnings("unchecked")
    protected AbstractDittoHeadersBuilder(final Map<String, String> initialHeaders,
            final Collection<? extends HeaderDefinition> definitions, final Class<?> selfType) {

        checkNotNull(initialHeaders, "initial headers");
        checkNotNull(definitions, "header definitions");
        validateValueTypes(initialHeaders, definitions);
        myself = (S) selfType.cast(this);
        headers = new HashMap<>(initialHeaders);
        this.definitions = new HashSet<>(definitions);
        Collections.addAll(this.definitions, DittoHeaderDefinition.values());
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
        final Map<String, String> result = new HashMap<>(jsonObject.getSize());
        jsonObject.forEach(jsonField -> {
            final JsonValue jsonValue = jsonField.getValue();
            final String stringValue = jsonValue.isString() ? jsonValue.asString() : jsonValue.toString();
            result.put(jsonField.getKeyName(), stringValue);
        });

        return result;
    }

    @Override
    public S correlationId(@Nullable final CharSequence correlationId) {
        putCharSequence(DittoHeaderDefinition.CORRELATION_ID, correlationId);
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
            headers.put(definition.getKey(), value.toString());
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
        putCharSequence(definition, jsonValue.isString() ? jsonValue.asString() : jsonValue.toString());
    }

    @Override
    @Deprecated
    public S authorizationSubjects(final Collection<String> authorizationSubjectIds) {
        authorizationContext(keepSubjectsWithIssuerPrefix(authorizationSubjectIds));
        return myself;
    }

    private static AuthorizationContext keepSubjectsWithIssuerPrefix(
            final Collection<String> subjectsWithAndWithoutPrefix) {

        final List<AuthorizationSubject> authSubjects = subjectsWithAndWithoutPrefix.stream()
                .map(AuthorizationSubject::newInstance)
                .collect(Collectors.toList());
        final AuthorizationContext authorizationContext = AuthorizationModelFactory.newAuthContext(authSubjects);

        return AbstractDittoHeaders.keepAuthContextSubjectsWithIssuer(authorizationContext);
    }

    @Override
    @Deprecated
    public S authorizationSubjects(final CharSequence authorizationSubject,
            final CharSequence... furtherAuthorizationSubjects) {

        checkNotNull(authorizationSubject, "Authorization Subject ID");
        checkNotNull(furtherAuthorizationSubjects, "further Authorization Subject IDs");

        final Collection<String> allAuthorizationSubjects = new ArrayList<>(1 + furtherAuthorizationSubjects.length);
        allAuthorizationSubjects.add(authorizationSubject.toString());
        for (final CharSequence furtherAuthorizationSubject : furtherAuthorizationSubjects) {
            checkNotNull(furtherAuthorizationSubject, "further Authorization Subject ID");
            allAuthorizationSubjects.add(furtherAuthorizationSubject.toString());
        }

        return authorizationSubjects(allAuthorizationSubjects);
    }

    @Override
    public S readSubjects(final Collection<String> readSubjects) {
        putStringCollection(DittoHeaderDefinition.READ_SUBJECTS, readSubjects);
        return myself;
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
        putCharSequence(DittoHeaderDefinition.CHANNEL, channel);
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
    public S contentType(final CharSequence contentType) {
        putCharSequence(DittoHeaderDefinition.CONTENT_TYPE, contentType);
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
    public S inboundPayloadMapper(final String inboundPayloadMapperId) {
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
    public S putHeader(final CharSequence key, final CharSequence value) {
        validateKey(key);
        checkNotNull(value, "value");
        validateValueType(key, value);
        headers.put(key.toString(), value.toString());
        return myself;
    }

    private static void validateKey(final CharSequence key) {
        argumentNotEmpty(key, "key");
    }

    protected void validateValueType(final CharSequence key, final CharSequence value) {
        definitions.stream()
                .filter(definition -> Objects.equals(definition.getKey(), key.toString()))
                .findAny()
                .ifPresent(definition -> definition.validateValue(value));
    }

    @Override
    public S putHeaders(final Map<String, String> headers) {
        checkNotNull(headers, "headers");
        validateValueTypes(headers, definitions);
        this.headers.putAll(headers);
        return myself;
    }

    @Override
    public S removeHeader(final CharSequence key) {
        validateKey(key);
        headers.remove(key.toString());
        return myself;
    }

    @Override
    public S removePreconditionHeaders() {
        headers.remove(DittoHeaderDefinition.IF_MATCH.getKey());
        headers.remove(DittoHeaderDefinition.IF_NONE_MATCH.getKey());
        return myself;
    }

    @Override
    public R build() {
        // do it here
        calculateIsResponseRequired();
        final ImmutableDittoHeaders dittoHeaders = ImmutableDittoHeaders.of(headers);
        return doBuild(dittoHeaders);
    }

    private void  calculateIsResponseRequired() {
        // The order is important. A timeout of zero eventually determines response-required to be false.
        calculateIsResponseRequiredViaRequestedAcks();
        calculateIsResponseRequiredViaTimeout();
    }

    private void calculateIsResponseRequiredViaRequestedAcks() {
        @Nullable final String ackRequests = headers.get(DittoHeaderDefinition.REQUESTED_ACKS.getKey());
        if (null != ackRequests && !headers.containsKey(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey())) {
            // only if "response-required" was not already set, assume the default
            final boolean containsAckRequests = !JsonArray.of(ackRequests).isEmpty();
            responseRequired(containsAckRequests);
        }
    }

    private void calculateIsResponseRequiredViaTimeout() {
        @Nullable final String timeoutValue = headers.get(DittoHeaderDefinition.TIMEOUT.getKey());
        if (null != timeoutValue) {
            final DittoDuration dittoDuration = DittoDuration.parseDuration(timeoutValue);

            // sets response-required explicitly to false, which is desired in that case
            if (dittoDuration.isZero()) {
                responseRequired(false);
            }
        }
    }

    @Override
    public String toString() {
        return headers.toString();
    }

    protected abstract R doBuild(DittoHeaders dittoHeaders);

}
