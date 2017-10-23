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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;


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
     * @param authorizationSubject the mandatory authorization subject of the new authorization context.
     * @param furtherAuthorizationSubjects additional authorization subjects of the new authorization context.
     * @return the new {@code AuthorizationContext}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static AuthorizationContext newInstance(final AuthorizationSubject authorizationSubject,
            final AuthorizationSubject... furtherAuthorizationSubjects) {

        return AuthorizationModelFactory.newAuthContext(authorizationSubject, furtherAuthorizationSubjects);
    }

    /**
     * Returns all authorization subjects of this context.
     *
     * @return an unmodifiable sorted list of all authorization subjects of this context.
     */
    List<AuthorizationSubject> getAuthorizationSubjects();

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
     * Returns all non hidden marked fields of this authorization context.
     *
     * @return a JSON object representation of this authorization context including only {@link FieldType#REGULAR}
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
         * JSON field containing the {@link JsonSchemaVersion}.
         */
        public static final JsonFieldDefinition<Integer> JSON_SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, FieldType.HIDDEN,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the authorized subjects as JSON array.
         */
        public static final JsonFieldDefinition<JsonArray> AUTH_SUBJECTS =
                JsonFactory.newJsonArrayFieldDefinition("authorizedSubjects", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
