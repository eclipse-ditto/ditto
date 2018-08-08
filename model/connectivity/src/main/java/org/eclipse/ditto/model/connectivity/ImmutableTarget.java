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
import java.util.Optional;
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
 * Immutable implementation of {@link Target}.
 */
@Immutable
final class ImmutableTarget implements Target {

    private final String address;
    private final Set<Topic> topics;
    private final AuthorizationContext authorizationContext;

    ImmutableTarget(final String address, final Set<Topic> topics, final AuthorizationContext authorizationContext) {
        this.address = address;
        this.topics = Collections.unmodifiableSet(new HashSet<>(topics));
        this.authorizationContext = ConditionChecker.checkNotNull(authorizationContext, "authorizationContext");
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public Set<Topic> getTopics() {
        return topics;
    }

    @Override
    public AuthorizationContext getAuthorizationContext() {
        return authorizationContext;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(Target.JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate);
        jsonObjectBuilder.set(Target.JsonFields.ADDRESS, address, predicate);
        jsonObjectBuilder.set(Target.JsonFields.TOPICS, topics.stream()
                .map(Topic::getName)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));
        if (!authorizationContext.isEmpty()) {
            jsonObjectBuilder.set(JsonFields.AUTHORIZATION_CONTEXT, authorizationContext.stream()
                    .map(AuthorizationSubject::getId)
                    .map(JsonFactory::newValue)
                    .collect(JsonCollectors.valuesToArray()), predicate);
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
        final String readAddress = jsonObject.getValueOrThrow(Target.JsonFields.ADDRESS);
        final Set<Topic> readTopics = jsonObject.getValue(JsonFields.TOPICS)
                .map(array -> array.stream()
                        .map(JsonValue::asString)
                        .map(Topic::forName)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());

        final JsonArray authContext = jsonObject.getValue(Target.JsonFields.AUTHORIZATION_CONTEXT)
                .orElseGet(() -> JsonArray.newBuilder().build());
        final List<AuthorizationSubject> authorizationSubjects = authContext.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .map(AuthorizationSubject::newInstance)
                .collect(Collectors.toList());
        final AuthorizationContext readAuthorizationContext =
                AuthorizationModelFactory.newAuthContext(authorizationSubjects);

        return new ImmutableTarget(readAddress, readTopics, readAuthorizationContext);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutableTarget that = (ImmutableTarget) o;
        return Objects.equals(address, that.address) &&
                Objects.equals(topics, that.topics) &&
                Objects.equals(authorizationContext, that.authorizationContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, topics, authorizationContext);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "address=" + address +
                ", topics=" + topics +
                ", authorizationContext=" + authorizationContext +
                "]";
    }
}
