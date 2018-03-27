/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.base.headers;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
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
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
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
    public Optional<String> getSource() {
        return getStringForDefinition(DittoHeaderDefinition.SOURCE);
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
    public Optional<String> getChannel() {
        return getStringForDefinition(DittoHeaderDefinition.CHANNEL);
    }

    @Override
    public boolean isResponseRequired() {
        return getBooleanForDefinition(DittoHeaderDefinition.RESPONSE_REQUIRED).orElse(true);
    }

    /**
     * Resolve type of a header not defined in {@link DittoHeaderDefinition}. Implementations should be fast because
     * this method is called multiple times during serialization of each object.
     *
     * @param key Name of the specific header.
     * @return Header definition of the specific header.
     */
    protected abstract Optional<HeaderDefinition> getSpecificDefinitionByKey(final CharSequence key);

    protected Optional<Boolean> getBooleanForDefinition(final HeaderDefinition definition) {
        return getStringForDefinition(definition)
                .map(JsonFactory::readFrom)
                .filter(JsonValue::isBoolean)
                .map(JsonValue::asBoolean);
    }

    @Override
    public boolean isDryRun() {
        return getBooleanForDefinition(DittoHeaderDefinition.DRY_RUN).orElse(false);
    }

    @Override
    public Optional<String> getOrigin() {
        return getStringForDefinition(DittoHeaderDefinition.ORIGIN);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        forEach((key, value) -> {
            final Class<?> type = getTypeForKey(key);
            final JsonValue jsonValue = type.isAssignableFrom(String.class)
                    ? JsonFactory.newValue(value)
                    : JsonFactory.readFrom(value);
            jsonObjectBuilder.set(key, jsonValue);
        });
        return jsonObjectBuilder.build();
    }

    private Class<?> getTypeForKey(final CharSequence key) {
        return getSpecificDefinitionByKey(key)
                .map(HeaderDefinition::getJavaType)
                .orElseGet(() -> DittoHeaderDefinition.forKey(key)
                        .map(HeaderDefinition::getJavaType)
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
    public String toString() {
        return headers.toString();
    }

}
