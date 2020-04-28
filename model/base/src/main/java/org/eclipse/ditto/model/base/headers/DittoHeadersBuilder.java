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

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatchers;
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
     * Sets a generated random correlation ID.
     *
     * @return this builder for Method Chaining.
     */
    default B randomCorrelationId() {
        return correlationId(String.valueOf(UUID.randomUUID()));
    }

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
     * @deprecated as of 1.1.0, please use {@link #authorizationContext(AuthorizationContext)} instead for adding the
     * {@code authorizationSubjects}
     */
    @Deprecated
    B authorizationSubjects(Collection<String> authorizationSubjectIds);

    /**
     * Sets the authorizationSubjects value.
     *
     * @param authorizationSubject the authorizationSubject value to be set.
     * @param furtherAuthorizationSubjects further of "authorized subjects" to be set.
     * @return this builder for Method Chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @deprecated as of 1.1.0, please use {@link #authorizationContext(AuthorizationContext)} instead for adding the
     * {@code authorizationSubjects}
     */
    @Deprecated
    B authorizationSubjects(CharSequence authorizationSubject, CharSequence... furtherAuthorizationSubjects);

    /**
     * Sets the readSubjects value.
     *
     * @param readSubjects the readSubjects value to be set.
     * @return this builder for Method Chaining.
     * @throws NullPointerException if {@code readSubjects} is {@code null}.
     * @deprecated as of 1.1.0, please use {@link #readGrantedSubjects(Collection)}.
     */
    @Deprecated
    B readSubjects(Collection<String> readSubjects);

    /**
     * Sets the subjects with granted READ access.
     *
     * @param readGrantedSubjects the value to be set.
     * @return this builder for Method Chaining.
     * @throws NullPointerException if {@code readGrantedSubjects} is {@code null}.
     * @since 1.1.0
     */
    B readGrantedSubjects(Collection<AuthorizationSubject> readGrantedSubjects);

    /**
     * Sets the subjects with explicitly revoked READ access.
     *
     * @param readRevokedSubjects the value to be set.
     * @return this builder for Method Chaining.
     * @throws NullPointerException if {@code readRevokedSubjects} is {@code null}.
     * @since 1.1.0
     */
    B readRevokedSubjects(Collection<AuthorizationSubject> readRevokedSubjects);

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
     * Call this method for explicitly waiving a response.
     * <em>
     * Please note: If ACK requests are set (see {@link #acknowledgementRequests(Collection)} calling this method has no
     * effect.
     * ACK requests always imply that a response is required.
     * </em>
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
     * Sets the ETag value.
     *
     * @param eTag The ETag value to be set.
     * @return this builder for Method Chaining
     */
    B eTag(EntityTag eTag);

    /**
     * Sets the If-Match value.
     *
     * @param entityTags The entity tags where one should match.
     * @return this builder for Method Chaining
     */
    B ifMatch(EntityTagMatchers entityTags);

    /**
     * Sets the If-None-Match value.
     *
     * @param entityTags The entity tags that must not match.
     * @return this builder for Method Chaining
     */
    B ifNoneMatch(EntityTagMatchers entityTags);

    /**
     * Sets the inbound {@code MessageMapper} ID value.
     *
     * @param inboundPayloadMapperId the inbound {@code MessageMapper} ID which mapped incoming arbitrary payload from external sources.
     * @return this builder for Method Chaining
     */
    B inboundPayloadMapper(String inboundPayloadMapperId);

    /**
     * Set the reply-target.
     *
     * @param replyTarget the reply-target identifier.
     * @return this builder.
     */
    B replyTarget(@Nullable Integer replyTarget);

    /**
     * Sets the acknowledgements ("ACK") which are requested together with an issued Ditto {@code Command}.
     * Such ACKs are sent back to the issuer of the command so that it can be verified which steps were successful.
     * <p>
     * In addition to built-in ACK labels like
     * {@link org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel#TWIN_PERSISTED} also custom labels may be used
     * which can be sent back even by external systems.
     * </p>
     * <p>
     * As long as ACKs are requested, calls of {@link #responseRequired(boolean)} are neglected as requested ACKs always
     * imply that a response is required.
     * </p>
     *
     * @param acknowledgementRequests the requests for acknowledgements.
     * @return this builder.
     * @throws NullPointerException if {@code acknowledgementRequests} is {@code null}.
     * @since 1.1.0
     */
    B acknowledgementRequests(Collection<AcknowledgementRequest> acknowledgementRequests);

    /**
     * Sets the acknowledgements ("ACK") which are requested together with an issued Ditto {@code Command}.
     * Such ACKs are sent back to the issuer of the command so that it can be verified which steps were successful.
     * <p>
     * In addition to built-in ACK labels like
     * {@link org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel#TWIN_PERSISTED} also custom labels may be used
     * which can be sent back even by external systems.
     * </p>
     * <p>
     * As long as ACKs are requested, calls of {@link #responseRequired(boolean)} are neglected as requested ACKs always
     * imply that a response is required.
     * </p>
     *
     * @param acknowledgementRequest the request for an acknowledgement.
     * @param furtherAcknowledgementRequests further requests for acknowledgements.
     * @return this builder.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.1.0
     */
    B acknowledgementRequest(AcknowledgementRequest acknowledgementRequest,
            AcknowledgementRequest... furtherAcknowledgementRequests);

    /**
     * Sets the <em>positive</em> timeout string of the DittoHeaders to build.
     *
     * @param timeoutStr the duration of the command containing the DittoHeaders to time out.
     * @return this builder.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException if the given timeout char sequence
     * does not contain a parsable duration or if the duration is negative.
     * @since 1.1.0
     */
    B timeout(@Nullable CharSequence timeoutStr);

    /**
     * Sets the <em>positive</em> timeout duration of the DittoHeaders to build.
     *
     * @param timeout the duration of the command containing the DittoHeaders to time out.
     * @return this builder.
     * @throws IllegalArgumentException if {@code timeout} is negative.
     * @since 1.1.0
     */
    B timeout(@Nullable Duration timeout);

    /**
     * Puts an arbitrary header with the specified {@code name} and String {@code value} to this builder.
     *
     * @param key the header name to use.
     * @param value the String value.
     * @return this builder for Method Chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if key is empty
     * @throws org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException if {@code value} represents an
     * invalid Java type.
     */
    B putHeader(CharSequence key, CharSequence value);

    /**
     * Puts the specified headers to this builder. Existing headers with the same key will be replaced.
     *
     * @param headers the headers to be put.
     * @return this builder for Method Chaining.
     * @throws NullPointerException if {@code headers} is {@code null}.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoHeaderInvalidException if {@code headers} contains a value
     * that did not represent its appropriate Java type.
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
     * Removes all precondition headers from this builder.
     *
     * @return The builder without preconditionHeaders
     */
    B removePreconditionHeaders();

    /**
     * Creates a DittoHeaders object containing the key-value-pairs which were put to this builder.
     *
     * @return the headers.
     */
    R build();

}
