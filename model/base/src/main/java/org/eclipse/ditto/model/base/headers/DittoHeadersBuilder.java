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

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * This interface represents a mutable builder with a fluent API for a {@link DittoHeaders} object or an object of a
 * descendant type.
 *
 * @param <B> the type of the class which implements this interface; this type is used as return value for Method
 * Chaining.
 * @param <R> the type of the built DittoHeaders object.
 */
@NotThreadSafe
public interface DittoHeadersBuilder<B extends DittoHeadersBuilder, R extends DittoHeaders> {

    /**
     * Sets the specified correlation ID.
     *
     * @param correlationId the correlation ID to be set.
     * @return this builder for Method Chaining.
     * @throws IllegalArgumentException if {@code correlationId} is empty.
     */
    B correlationId(@Nullable CharSequence correlationId);

    /**
     * Sets the specified String as source of the command.
     *
     * @param source the source of the command to be set.
     * @return this builder for Method Chaining.
     * @throws IllegalArgumentException if {@code source} is empty.
     */
    B source(@Nullable CharSequence source);

    /**
     * Sets the json schema version value.
     *
     * @param schemaVersion the "schema version" value to be set.
     * @return this builder for Method Chaining.
     */
    B schemaVersion(@Nullable JsonSchemaVersion schemaVersion);

    /**
     * Sets the authorization context value.
     *
     * @param authorizationContext the "authorizationContext" value to be set.
     * @return this builder for Method Chaining.
     */
    B authorizationContext(@Nullable AuthorizationContext authorizationContext);

    /**
     * Sets the IDs of Authorization Subjects.
     *
     * @param authorizationSubjectIds the IDs to be set.
     * @return this builder for Method Chaining.
     * @throws NullPointerException if {@code authorizationSubjectIds} is {@code null}.
     */
    B authorizationSubjects(Collection<String> authorizationSubjectIds);

    /**
     * Sets the authorizationSubjects value.
     *
     * @param authorizationSubject the authorizationSubject value to be set.
     * @param furtherAuthorizationSubjects further of "authorized subjects" to be set.
     * @return this builder for Method Chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    B authorizationSubjects(CharSequence authorizationSubject, CharSequence... furtherAuthorizationSubjects);

    /**
     * Sets the readSubjects value.
     *
     * @param readSubjects the readSubjects value to be set.
     * @return this builder for Method Chaining.
     * @throws NullPointerException if {@code readSubjects} is {@code null}.
     */
    B readSubjects(Collection<String> readSubjects);

    /**
     * Sets the specified String as channel of the Signal/Exception.
     *
     * @param channel the channel of the Signal/Exception to be set.
     * @return this builder for Method Chaining.
     * @throws IllegalArgumentException if {@code channel} is empty.
     */
    B channel(@Nullable CharSequence channel);

    /**
     * Sets the responseRequired value.
     *
     * @param responseRequired the responseRequired value to be set.
     * @return this builder for Method Chaining.
     */
    B responseRequired(boolean responseRequired);

    /**
     * Sets the dryRun value.
     *
     * @param dryRun the dryRun value to be set.
     * @return this builder for Method Chaining.
     */
    B dryRun(boolean dryRun);

    /**
     * Sets the origin value.
     *
     * @param origin the origin value to be set.
     * @return this builder for Method Chaining.
     */
    B origin(CharSequence origin);

    /**
     * Sets the contentType value.
     *
     * @param contentType the contentType value to be set.
     * @return this builder for Method Chaining.
     */
    B contentType(CharSequence contentType);

    /**
     * Puts an arbitrary header with the specified {@code name} and String {@code value} to this builder.
     *
     * @param key the header name to use.
     * @param value the String value.
     * @return this builder for Method Chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if key is empty or if {@code value} represents an invalid Java type.
     */
    B putHeader(CharSequence key, CharSequence value);

    /**
     * Puts the specified headers to this builder. Existing headers with the same key will be replaced.
     *
     * @param headers the headers to be put.
     * @return this builder for Method Chaining.
     * @throws NullPointerException if {@code headers} is {@code null}.
     * @throws IllegalArgumentException if {@code headers} contains a value that did not represent its appropriate Java
     * type.
     */
    B putHeaders(Map<String, String> headers);

    /**
     * Removes from this builder the value which is associated with the specified key.
     *
     * @param key the key to remove the associated value for.
     * @return this builder for Method Chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    B removeHeader(CharSequence key);

    /**
     * Creates a DittoHeaders object containing the key-value-pairs which were put to this builder.
     *
     * @return the headers.
     */
    R build();

}
