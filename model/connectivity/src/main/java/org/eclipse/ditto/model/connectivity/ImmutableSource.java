/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

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
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link Source}.
 */
@Immutable
final class ImmutableSource implements Source {

    static final int DEFAULT_CONSUMER_COUNT = 1;

    private final Set<String> addresses;
    private final int consumerCount;
    private final AuthorizationContext authorizationContext;

    ImmutableSource(final Set<String> addresses, final int consumerCount,
            final AuthorizationContext authorizationContext) {
        this.addresses = Collections.unmodifiableSet(new HashSet<>(addresses));
        this.consumerCount = consumerCount;
        this.authorizationContext = ConditionChecker.checkNotNull(authorizationContext, "authorizationContext");
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
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(Source.JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate);
        jsonObjectBuilder.set(Source.JsonFields.ADDRESSES, addresses.stream()
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));

        if (!authorizationContext.isEmpty()) {
            jsonObjectBuilder.set(Target.JsonFields.AUTHORIZATION_CONTEXT, authorizationContext.stream()
                    .map(AuthorizationSubject::getId)
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray()), predicate);
        }

        jsonObjectBuilder.set(JsonFields.CONSUMER_COUNT, consumerCount, predicate);
        return jsonObjectBuilder.build();
    }

    /**
     * Creates a new {@code Source} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Source to be created.
     * @return a new Source which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Source fromJson(final JsonObject jsonObject) {
        final Set<String> readSources = jsonObject.getValue(JsonFields.ADDRESSES)
                .map(array -> array.stream()
                        .map(JsonValue::asString)
                        .collect(Collectors.toSet())).orElse(Collections.emptySet());
        final int readConsumerCount =
                jsonObject.getValue(Source.JsonFields.CONSUMER_COUNT).orElse(DEFAULT_CONSUMER_COUNT);
        final JsonArray authContext = jsonObject.getValue(Connection.JsonFields.AUTHORIZATION_CONTEXT)
                .orElseGet(() -> JsonArray.newBuilder().build());
        final List<AuthorizationSubject> authorizationSubjects = authContext.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(AuthorizationSubject::newInstance)
                .collect(Collectors.toList());
        final AuthorizationContext readAuthorizationContext =
                AuthorizationModelFactory.newAuthContext(authorizationSubjects);

        return new ImmutableSource(readSources, readConsumerCount, readAuthorizationContext);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutableSource that = (ImmutableSource) o;
        return consumerCount == that.consumerCount &&
                Objects.equals(addresses, that.addresses) &&
                Objects.equals(authorizationContext, that.authorizationContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(addresses, consumerCount, authorizationContext);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "addresses=" + addresses +
                ", consumerCount=" + consumerCount +
                ", authorizationContext=" + authorizationContext +
                "]";
    }
}
