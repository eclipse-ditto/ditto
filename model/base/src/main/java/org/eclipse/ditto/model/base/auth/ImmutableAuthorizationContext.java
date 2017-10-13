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
package org.eclipse.ditto.model.base.auth;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * An immutable implementation of {@link AuthorizationContext}.
 */
@Immutable
final class ImmutableAuthorizationContext implements AuthorizationContext {

    private final List<AuthorizationSubject> authorizationSubjects;

    private ImmutableAuthorizationContext(final List<AuthorizationSubject> theAuthorizationSubjects) {
        authorizationSubjects = theAuthorizationSubjects;
    }

    /**
     * Returns a new instance of {@code ImmutableAuthorizationContext} with the given authorization subjects.
     *
     * @param authorizationSubjects the authorization subjects of the new context.
     * @return the new {@code AuthorizationContext}.
     * @throws NullPointerException if {@code authorizationSubjects} is {@code null}.
     */
    public static AuthorizationContext of(final List<AuthorizationSubject> authorizationSubjects) {
        checkNotNull(authorizationSubjects, "authorization subjects");

        return new ImmutableAuthorizationContext(Collections.unmodifiableList(authorizationSubjects));
    }

    /**
     * Returns a new instance of {@code ImmutableAuthorizationContext} with the given authorization subjects.
     *
     * @param authorizationSubject the mandatory authorization subject of the new context.
     * @param furtherAuthorizationSubjects additional authorization subjects of the new context.
     * @return the new {@code AuthorizationContext}.
     * @throws NullPointerException if any argument is {@code null}.
     */

    public static AuthorizationContext of(final AuthorizationSubject authorizationSubject,
            final AuthorizationSubject... furtherAuthorizationSubjects) {
        checkNotNull(authorizationSubject, "mandatory authorization subject");
        checkNotNull(furtherAuthorizationSubjects, "additional authorization subjects");

        final List<AuthorizationSubject> allAuthSubjects = new ArrayList<>(1 + furtherAuthorizationSubjects.length);
        allAuthSubjects.add(authorizationSubject);
        Collections.addAll(allAuthSubjects, furtherAuthorizationSubjects);

        return new ImmutableAuthorizationContext(allAuthSubjects);
    }

    /**
     * Creates a new {@code AuthorizationContext} from the specified JSON object.
     *
     * @param jsonObject the JSON object of which a new AuthorizationContext instance is to be created.
     * @return the {@code AuthorizationContext} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'AuthorizationContext' format.
     */
    public static AuthorizationContext fromJson(final JsonObject jsonObject) {
        final List<AuthorizationSubject> authSubjects = jsonObject.getValueOrThrow(JsonFields.AUTH_SUBJECTS)
                .stream()
                .map(JsonValue::asString)
                .map(AuthorizationModelFactory::newAuthSubject)
                .collect(Collectors.toList());

        return of(authSubjects);
    }

    @Override
    public List<AuthorizationSubject> getAuthorizationSubjects() {
        return authorizationSubjects;
    }

    @Override
    public Optional<AuthorizationSubject> getFirstAuthorizationSubject() {
        return authorizationSubjects.stream().findFirst();
    }

    @Override
    public int getSize() {
        return authorizationSubjects.size();
    }

    @Override
    public boolean isEmpty() {
        return authorizationSubjects.isEmpty();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.JSON_SCHEMA_VERSION, schemaVersion.toInt(), predicate)
                .set(JsonFields.AUTH_SUBJECTS, authorizedSubjectsToJson(), predicate)
                .build();
    }

    private JsonArray authorizedSubjectsToJson() {
        return authorizationSubjects.stream()
                .map(AuthorizationSubject::getId)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray());
    }

    @Override
    public Iterator<AuthorizationSubject> iterator() {
        return new ArrayList<>(authorizationSubjects).iterator();
    }

    @Override
    public Stream<AuthorizationSubject> stream() {
        return authorizationSubjects.stream();
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableAuthorizationContext)) {
            return false;
        }

        final ImmutableAuthorizationContext that = (ImmutableAuthorizationContext) o;
        return Objects.equals(authorizationSubjects, that.authorizationSubjects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authorizationSubjects);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [authorizationSubjects=" + authorizationSubjects + "]";
    }

}
