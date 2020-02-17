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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Headers for commands and their responses which provide additional information needed for correlation and transfer.
 *
 * <em>Implementations of this interface are required to be immutable.</em>
 */
public interface DittoHeaders extends Jsonifiable<JsonObject>, Map<String, String> {

    /**
     * Returns an empty {@code DittoHeaders} object.
     *
     * @return empty DittoHeaders.
     */
    static DittoHeaders empty() {
        return DefaultDittoHeadersBuilder.getEmptyHeaders();
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
     * Creates a single-line String representation of the passed {@code readSubjects} which were retrieved by the
     * {@link #getReadSubjects()} of the DittoHeaders instance. Used when transmitting via a messaging header.
     *
     * @param readSubjects the authorization subjects having "READ" permission.
     * @return a String representation.
     * @deprecated Use {@link DittoHeaderDefinition#READ_SUBJECTS} to read subjects
     */
    @Deprecated
    static String readSubjectsToString(final Set<String> readSubjects) {
        return String.join(",", readSubjects);
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
     * Returns a mutable builder with a fluent API for immutable {@code DittoHeaders}. The builder is initialised with
     * the entries of this instance.
     *
     * @return the new builder.
     */
    default DittoHeadersBuilder toBuilder() {
        return DefaultDittoHeadersBuilder.of(this);
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
     * @deprecated as of 1.1.0, please use {@link #getReadGrantedSubjects()} instead.
     */
    @Deprecated
    Set<String> getReadSubjects();

    /**
     * Returns the authorization subjects with granted "READ" permissions for the key in the map defining a pointer in
     * the Thing.
     * Changes on the returned Set are not reflected back to this headers object.
     *
     * @return the read granted subjects for pointers in the Thing.
     * @since 1.1.0
     */
    Set<AuthorizationSubject> getReadGrantedSubjects();

    /**
     * Returns the authorization subjects with explicitly revoked "READ" permissions for the key in the map defining a
     * pointer in the Thing.
     * Changes on the returned Set are not reflected back to this headers object.
     *
     * @return the read revoked subjects for pointers in the Thing.
     * @since 1.1.0
     */
    Set<AuthorizationSubject> getReadRevokedSubjects();

    /**
     * Returns the channel (twin/live) on which a Signal/Exception was sent/occurred.
     *
     * @return the channel (twin/live).
     */
    Optional<String> getChannel();

    /**
     * Returns whether a response to a command is required or if it may be omitted (fire and forget semantics).
     * If acknowledgment labels are set the return value of this method is implicitly {@code true}.
     *
     * @return the "response required" value.
     * @see #getRequestedAckLabels()
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

    /**
     * Returns the eTag of the entity.
     *
     * @return the "ETag" value.
     */
    Optional<EntityTag> getETag();

    /**
     * Returns the entity-tags contained in the If-Match header.
     *
     * @return the entity-tags contained in the If-Match header.
     */
    Optional<EntityTagMatchers> getIfMatch();

    /**
     * Returns the entity-tags contained in the If-None-Match header.
     *
     * @return the entity-tags contained in the If-None-Match header.
     */
    Optional<EntityTagMatchers> getIfNoneMatch();

    /**
     * Returns the inbound {@code MessageMapper} ID which mapped incoming arbitrary payload from external sources.
     *
     * @return the {@code MessageMapper} which mapped incoming payload.
     */
    Optional<String> getInboundPayloadMapper();

    /**
     * @return the reply target of a command-response.
     */
    Optional<Integer> getReplyTarget();

    /**
     * Indicates whether the size of the headers entries is greater than the specified size.
     *
     * @param size the size to compare to.
     * @return {@code true} if the size of the headers entries exceeds {@code size}, {@code false} else.
     * @throws IllegalArgumentException if {@code maxSizeBytes} is negative.
     */
    boolean isEntriesSizeGreaterThan(long size);

    /**
     * Truncates this headers to the specified size limit, keeping as many header entries as possible.
     *
     * @param maxSizeBytes the maximum allowed size in bytes.
     * @return the headers within the size limit.
     * @throws IllegalArgumentException if {@code maxSizeBytes} is negative.
     */
    DittoHeaders truncate(long maxSizeBytes);

    /**
     * Returns the acknowledgement ("ACK") labels which were requested together with an issued Ditto {@code Command}.
     * Such ack labels are sent back to the issuer of the command so that it can be verified which steps were
     * successful.
     * <p>
     * In addition to built-in ACK labels like
     * {@link org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel#PERSISTED} also custom labels may be specified
     * which can be sent back even by external systems.
     * </p>
     *
     * @return an unsorted Set of the requested acknowledgement labels. Changes on the set are not reflected back to
     * this DittoHeaders instance.
     * @since 1.1.0
     */
    Set<AcknowledgementLabel> getRequestedAckLabels();

}
