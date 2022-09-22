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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;


/**
 * Holder for all authorization subjects to check authorization to perform commands requiring authorization.
 * Implementations of this interface are required to be immutable.
 */
@Immutable
public interface AuthorizationContext
        extends Iterable<AuthorizationSubject>, Jsonifiable.WithPredicate<JsonObject, JsonField> {

    /**
     * Returns a new immutable {@code AuthorizationContext} with the given authorization subjects.
     *
     * @param type the type of the authorization context to create, predefined in {@link org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType}.
     * @param authorizationSubject the mandatory authorization subject of the new authorization context.
     * @param furtherAuthorizationSubjects additional authorization subjects of the new authorization context.
     * @return the new {@code AuthorizationContext}.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.1.0
     */
    static AuthorizationContext newInstance(final AuthorizationContextType type,
            final AuthorizationSubject authorizationSubject,
            final AuthorizationSubject... furtherAuthorizationSubjects) {

        return AuthorizationModelFactory.newAuthContext(type, authorizationSubject, furtherAuthorizationSubjects);
    }

    /**
     * Returns a new immutable {@code AuthorizationContext} with the given authorization subjects.
     *
     * @param type the type of the authorization context to create, predefined in {@link org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType}.
     * @param authorizationSubjects the authorization subjects of the new authorization context.
     * @return the new {@code AuthorizationContext}.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.1.0
     */
    static AuthorizationContext newInstance(final AuthorizationContextType type,
            final Iterable<AuthorizationSubject> authorizationSubjects) {

        return AuthorizationModelFactory.newAuthContext(type, authorizationSubjects);
    }

    /**
     * Returns a new immutable empty {@link org.eclipse.ditto.base.model.auth.AuthorizationContext}.
     *
     * @return the new {@code AuthorizationContext}.
     * @since 3.0.0
     */
    static AuthorizationContext empty() {
        return AuthorizationModelFactory.emptyAuthContext();
    }

    /**
     * Returns the type the authorization context was created with, specifying its "kind".
     *
     * @return the type of this authorization context.
     * @since 1.1.0
     */
    AuthorizationContextType getType();

    /**
     * Returns all authorization subjects of this context.
     *
     * @return an unmodifiable sorted list of all authorization subjects of this context.
     */
    List<AuthorizationSubject> getAuthorizationSubjects();

    /**
     * Adds the given authorization subjects at the beginning of the list.
     *
     * @param authorizationSubjects the authorization subjects to be added
     * @return a new authorization context with the given {@code authorizationSubjects} added at the beginning.
     * @throws NullPointerException if {@code authorizationSubjects} is {@code null}.
     */
    AuthorizationContext addHead(List<AuthorizationSubject> authorizationSubjects);

    /**
     * Adds the given authorization subjects at the end of the list.
     *
     * @param authorizationSubjects the authorization subjects to be added
     * @return a new authorization context with the given {@code authorizationSubjects} added at the end.
     * @throws NullPointerException if {@code authorizationSubjects} is {@code null}.
     */
    AuthorizationContext addTail(List<AuthorizationSubject> authorizationSubjects);

    /**
     * This convenience method returns a list containing the IDs of all AuthorizationSubjects of this context. Changes
     * on the returned list have no influence on this context object.
     *
     * @return the IDs.
     * @see #getAuthorizationSubjects()
     */
    default List<String> getAuthorizationSubjectIds() {
        return getAuthorizationSubjects()
                .stream()
                .map(AuthorizationSubject::getId)
                .collect(Collectors.toList());
    }

    /**
     * Returns the first authorization subject of this context.
     *
     * @return the first authorization subject of this context.
     */
    Optional<AuthorizationSubject> getFirstAuthorizationSubject();

    /**
     * Returns the size of this authorization context, i. e. the number of contained authorization subjects.
     *
     * @return the size of this context.
     */
    int getSize();

    /**
     * Indicates whether this authorization context is empty.
     *
     * @return {@code true} if this context does not contain any authorization subjects, {@code false} else.
     */
    boolean isEmpty();

    /**
     * Returns a sequential {@code Stream} with the authorization subjects of this context as its source.
     *
     * @return a sequential stream of the authorization subjects of this context.
     */
    Stream<AuthorizationSubject> stream();

    /**
     * Checks if this authorization context is authorized for a certain operation by evaluating the given granted and
     * revoked authorization subjects.
     * In evaluation revoked subjects weigh more than granted subjects, i. e. if the revoked and the granted set contain
     * a common subject the subject is regarded to be revoked.
     *
     * @param granted the authorization subjects which are granted to perform the operation.
     * @param revoked the authorization subjects which are revoked to perform the operation.
     * @return {@code true} if the authorization subjects of this authorization context are regarded as authorized to
     * perform a certain operation when the given granted and revoked subjects are taken into account.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.1.0
     */
    boolean isAuthorized(Collection<AuthorizationSubject> granted, Collection<AuthorizationSubject> revoked);

    /**
     * Returns all non-hidden marked fields of this authorization context.
     *
     * @return a JSON object representation of this authorization context including only {@link org.eclipse.ditto.base.model.json.FieldType#REGULAR}
     * marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    /**
     * The known {@link JsonField}s of an {@code AuthorizationContext}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the authorization context's type.
         *
         * @since 1.1.0
         */
        public static final JsonFieldDefinition<String> TYPE =
                JsonFactory.newStringFieldDefinition("type", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the authorized subjects as JSON array.
         */
        public static final JsonFieldDefinition<JsonArray> AUTH_SUBJECTS =
                JsonFactory.newJsonArrayFieldDefinition("subjects", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
