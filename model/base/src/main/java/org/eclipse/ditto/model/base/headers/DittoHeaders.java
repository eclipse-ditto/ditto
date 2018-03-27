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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Headers for commands and their responses which provide additional information needed for correlation and transfer.
 * <p>
 * <em>Implementations of this interface are required to be immutable.</em>
 */
public interface DittoHeaders extends Jsonifiable<JsonObject>, Map<String, String> {

    /**
     * Returns an empty {@code DittoHeaders} object.
     *
     * @return empty ditto headers.
     */
    static DittoHeaders empty() {
        return DefaultDittoHeadersBuilder.newInstance().build();
    }

    /**
     * Returns a new instance of {@code DittoHeaders} containing the specified key-value-pairs.
     *
     * @param headers the key-value-pairs of the result.
     * @return the DittoHeaders.
     * @throws NullPointerException if {@code headers} is {@code null}.
     * @throws IllegalArgumentException if {@code headers} contains an invalid key-value-pair.
     */
    static DittoHeaders of(final Map<String, String> headers) {
        return newBuilder(headers).build();
    }

    /**
     * Returns a new empty builder for a {@code DittoHeaders} object.
     *
     * @return the builder.
     */
    static DittoHeadersBuilder newBuilder() {
        return DefaultDittoHeadersBuilder.newInstance();
    }

    /**
     * Returns a new builder for a {@code DittoHeaders} object which is initialised with the specified headers.
     *
     * @param headers the initial headers.
     * @return the builder.
     * @throws NullPointerException if {@code headers} is {@code null}.
     * @throws IllegalArgumentException if {@code headers} contains an invalid key-value-pair.
     */
    static DittoHeadersBuilder newBuilder(final Map<String, String> headers) {
        return DefaultDittoHeadersBuilder.of(headers);
    }

    /**
     * Returns a new builder for a {@code DittoHeaders} object which is initialised with the headers the specified
     * JSON object provides.
     *
     * @param jsonObject the JSON object which provides the initial headers.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} contains an invalid header.
     */
    static DittoHeadersBuilder newBuilder(final JsonObject jsonObject) {
        return DefaultDittoHeadersBuilder.of(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for immutable {@code DittoHeaders}. The builder is initialised with
     * the entries of this instance.
     *
     * @return the new builder.
     */
    default DittoHeadersBuilder toBuilder() {
        return DefaultDittoHeadersBuilder.of(this);
    }

    /**
     * Creates a single-line String representation of the passed {@code readSubjects} which were retrieved by the
     * {@link #getReadSubjects()} of the DittoHeaders instance. Used when transmitting via a messaging header.
     *
     * @param readSubjects the authorization subjects having "READ" permission.
     * @return a String representation.
     * @deprecated Use {@link DittoHeaderDefinition#READ_SUBJECTS} to read subjects
     */
    @Deprecated
    static String readSubjectsToString(final Set<String> readSubjects) {
        return readSubjects.stream().collect(Collectors.joining(","));
    }

    /**
     * Transforms the single-line String representation of {@link #readSubjectsToString(Set)} back to a Set of
     * {@code readSubjects} as it is returned by {@link #getReadSubjects()}.
     *
     * @param readSubjectsString the String representation.
     * @return the authorization subjects having "READ" permission for the key in the map defining a pointer.
     * @deprecated Use {@link DittoHeaderDefinition#READ_SUBJECTS} to read subjects
     */
    @Deprecated
    static Set<String> readSubjectsFromString(final String readSubjectsString) {
        return new HashSet<>(Arrays.asList(readSubjectsString.split(",")));
    }

    /**
     * Returns the ID that is used to mark messages which belong together between clients.
     *
     * @return the correlation identifier.
     */
    Optional<String> getCorrelationId();

    /**
     * Returns the content-type of the entity.
     *
     * @return the content-type.
     */
    Optional<String> getContentType();

    /**
     * Returns the source which caused the command, e.g. a "clientId".
     *
     * @return the source which caused the command.
     */
    Optional<String> getSource();

    /**
     * Returns the json schema version.
     *
     * @return the schema version.
     */
    Optional<JsonSchemaVersion> getSchemaVersion();

    /**
     * Returns the authorization subjects for the command containing this header in a List of their String
     * representation. Changes on the returned List are not reflected back to this headers object.
     *
     * @return the authorization subjects for the command.
     */
    List<String> getAuthorizationSubjects();

    /**
     * Returns the AuthorizationContext for the command containing this header.
     *
     * @return the AuthorizationContext.
     */
    AuthorizationContext getAuthorizationContext();

    /**
     * Returns the authorization subjects having "READ" permission for the key in the map defining a pointer in the
     * Thing. Changes on the returned Set are not reflected back to this headers object.
     *
     * @return the read subjects for pointers in the Thing.
     */
    Set<String> getReadSubjects();

    /**
     * Returns the channel (twin/live) on which a Signal/Exception was sent/occurred.
     *
     * @return the channel (twin/live).
     */
    Optional<String> getChannel();

    /**
     * Returns whether a response to a command is required or if it may be omitted (fire and forget semantics)
     *
     * @return the "response required" value.
     */
    boolean isResponseRequired();

    /**
     * Returns whether a command is to be executed as a dry run.
     *
     * @return the "dry run" value.
     */
    boolean isDryRun();

    /**
     * Returns the id of the orignating session (e.g. WebSocket, AMQP, ...)
     *
     * @return the "origin" value.
     */
    Optional<String> getOrigin();

}
