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
package org.eclipse.ditto.base.model.auth;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * An immutable implementation of {@link org.eclipse.ditto.base.model.auth.AuthorizationContext}.
 */
@Immutable
final class ImmutableAuthorizationContext implements AuthorizationContext {

    private final AuthorizationContextType type;
    private final List<AuthorizationSubject> authorizationSubjects;
    @Nullable private List<String> authorizationSubjectIds;

    private ImmutableAuthorizationContext(final AuthorizationContextType type,
            final List<AuthorizationSubject> authorizationSubjects) {

        this.type = type;
        this.authorizationSubjects = authorizationSubjects;
        authorizationSubjectIds = null;
    }

    /**
     * Returns a new instance of {@code ImmutableAuthorizationContext} with the given authorization subjects.
     *
     * @param type the mandatory type defining which "kind" of authorization context should be created.
     * @param authorizationSubjects the authorization subjects of the new context.
     * @return the new {@code AuthorizationContext}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutableAuthorizationContext of(final AuthorizationContextType type,
            final List<AuthorizationSubject> authorizationSubjects) {
        checkNotNull(type, "type");
        checkNotNull(authorizationSubjects, "authorization subjects");

        return new ImmutableAuthorizationContext(type, Collections.unmodifiableList(authorizationSubjects));
    }

    /**
     * Returns a new instance of {@code ImmutableAuthorizationContext} with the given authorization subjects.
     *
     * @param type the mandatory type defining which "kind" of authorization context should be created.
     * @param authorizationSubject the mandatory authorization subject of the new context.
     * @param furtherAuthorizationSubjects additional authorization subjects of the new context.
     * @return the new {@code AuthorizationContext}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutableAuthorizationContext of(final AuthorizationContextType type,
            final AuthorizationSubject authorizationSubject,
            final AuthorizationSubject... furtherAuthorizationSubjects) {

        checkNotNull(type, "type");
        checkNotNull(authorizationSubject, "mandatory authorization subject");
        checkNotNull(furtherAuthorizationSubjects, "additional authorization subjects");

        final List<AuthorizationSubject> allAuthSubjects = new ArrayList<>(1 + furtherAuthorizationSubjects.length);
        allAuthSubjects.add(authorizationSubject);
        Collections.addAll(allAuthSubjects, furtherAuthorizationSubjects);

        return new ImmutableAuthorizationContext(type, allAuthSubjects);
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
    public static ImmutableAuthorizationContext fromJson(final JsonObject jsonObject) {
        if (jsonObject.isEmpty()) {
            return of(DittoAuthorizationContextType.UNSPECIFIED, Collections.emptyList());
        } else {
            final AuthorizationContextType type =
                    ImmutableAuthorizationContextType.of(jsonObject.getValueOrThrow(JsonFields.TYPE));
            final List<AuthorizationSubject> authSubjects = jsonObject.getValueOrThrow(JsonFields.AUTH_SUBJECTS)
                    .stream()
                    .map(JsonValue::asString)
                    .map(AuthorizationModelFactory::newAuthSubject)
                    .collect(Collectors.toList());

            return of(type, authSubjects);
        }
    }

    @Override
    public AuthorizationContextType getType() {
        return type;
    }

    @Override
    public List<AuthorizationSubject> getAuthorizationSubjects() {
        return authorizationSubjects;
    }

    @Override
    public List<String> getAuthorizationSubjectIds() {
        List<String> result = authorizationSubjectIds;

        // The AuthorizationSubject IDs are cached for performance reasons.
        // Caching has not to be thread-safe as the result of this method is guaranteed to be always the same and thus
        // it does not break immutability from a user's viewpoint (which is the crucial one).
        // Making this method thread-safe would eliminate the performance gains.
        if (null == result) {
            result = Collections.unmodifiableList(authorizationSubjects.stream()
                    .map(AuthorizationSubject::getId)
                    .collect(Collectors.toList()));
            authorizationSubjectIds = result;
        }
        return result;
    }

    @Override
    public Optional<AuthorizationSubject> getFirstAuthorizationSubject() {
        try {
            return Optional.of(authorizationSubjects.get(0));
        } catch (final IndexOutOfBoundsException e) {
            return Optional.empty();
        }
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
    public AuthorizationContext addHead(final List<AuthorizationSubject> authorizationSubjects) {
        checkNotNull(authorizationSubjects, "authorizationSubjects");

        final List<AuthorizationSubject> newAuthorizationSubjects =
                new ArrayList<>(this.authorizationSubjects.size() + authorizationSubjects.size());
        newAuthorizationSubjects.addAll(authorizationSubjects);
        newAuthorizationSubjects.addAll(this.authorizationSubjects);

        return new ImmutableAuthorizationContext(type, newAuthorizationSubjects);
    }

    @Override
    public AuthorizationContext addTail(final List<AuthorizationSubject> authorizationSubjects) {
        checkNotNull(authorizationSubjects, "authorizationSubjects");

        final List<AuthorizationSubject> newAuthorizationSubjects =
                new ArrayList<>(this.authorizationSubjects.size() + authorizationSubjects.size());
        newAuthorizationSubjects.addAll(this.authorizationSubjects);
        newAuthorizationSubjects.addAll(authorizationSubjects);

        return new ImmutableAuthorizationContext(type, newAuthorizationSubjects);
    }

    @Override
    public boolean isAuthorized(final Collection<AuthorizationSubject> granted,
            final Collection<AuthorizationSubject> revoked) {

        checkNotNull(granted, "granted");
        checkNotNull(revoked, "revoked");

        final boolean isGranted = !Collections.disjoint(granted, authorizationSubjects);
        final boolean isRevoked = !Collections.disjoint(revoked, authorizationSubjects);

        return isGranted && !isRevoked;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.TYPE, type.toString(), predicate)
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableAuthorizationContext that = (ImmutableAuthorizationContext) o;
        return type.equals(that.type) && authorizationSubjects.equals(that.authorizationSubjects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, authorizationSubjects);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "type=" + type +
                ", authorizationSubjects=" + authorizationSubjects +
                "]";
    }

}
