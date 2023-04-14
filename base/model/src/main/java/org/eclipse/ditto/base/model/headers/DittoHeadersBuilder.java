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
package org.eclipse.ditto.base.model.headers;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonValue;

/**
 * This interface represents a mutable builder with a fluent API for a {@link org.eclipse.ditto.base.model.headers.DittoHeaders} object or an object of a
 * descendant type.
 *
 * @param <B> the type of the class which implements this interface; this type is used as return value for Method
 * Chaining.
 * @param <R> the type of the built DittoHeaders object.
 */
@NotThreadSafe
public interface DittoHeadersBuilder<B extends DittoHeadersBuilder<B, R>, R extends DittoHeaders> {

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
     * Allowed values are {@code "twin"} and {@code "live"}.
     * If omitted, {@code "twin"} is used as default.
     *
     * @param channel the channel of the Signal/Exception to be set.
     * {@code null} removes an already set channel header.
     * @return this builder for Method Chaining.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException if {@code channel} after trimming and
     * converting to lower case does not match {@code "twin"} or {@code "live"}.
     */
    B channel(@Nullable CharSequence channel);

    /**
     * Sets the responseRequired value.
     * Call this method for explicitly waiving a response.
     * <em>
     * Please note: If ACK requests are set (see {@link #acknowledgementRequests(java.util.Collection)} calling this method has no
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
    B contentType(@Nullable CharSequence contentType);

    /**
     * Sets the Ditto typed contentType value.
     *
     * @param contentType the Ditto typed contentType value to be set.
     * @return this builder for Method Chaining.
     * @since 1.3.0
     */
    B contentType(@Nullable ContentType contentType);

    /**
     * Sets the accept value.
     *
     * @param accept the accept value to be set.
     * @return this builder for Method Chaining.
     * @since 2.4.0
     */
    B accept(@Nullable CharSequence accept);

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
     * Sets the If-Equal option.
     *
     * @param ifEqualOption The if-equal option to set defining what to do with a value to update which is equal to
     * its previous value.
     * @return this builder for Method Chaining
     * @since 3.3.0
     */
    B ifEqual(IfEqualOption ifEqualOption);

    /**
     * Sets the inbound {@code MessageMapper} ID value.
     *
     * @param inboundPayloadMapperId the inbound {@code MessageMapper} ID which mapped incoming arbitrary payload from external sources. If null, the header will be removed.
     * @return this builder for Method Chaining
     */
    B inboundPayloadMapper(@Nullable String inboundPayloadMapperId);

    /**
     * Set the reply-target.
     *
     * @param replyTarget the reply-target identifier.
     * @return this builder.
     */
    B replyTarget(@Nullable Integer replyTarget);

    /**
     * Set the expected response types. In combination with {@link #replyTarget(Integer)} this decides which type
     * of responses are delivered to a reply target of a connection source.
     *
     * @param responseTypes the response types that should be delivered to a reply target.
     * @return this builder.
     * @since 1.2.0
     */
    B expectedResponseTypes(ResponseType... responseTypes);

    /**
     * Set the expected response types. In combination with {@link #replyTarget(Integer)} this decides which type
     * of responses are delivered to a reply target of a connection source.
     *
     * @param responseTypes the response types that should be delivered to a reply target.
     * @return this builder.
     * @since 1.2.0
     */
    B expectedResponseTypes(Collection<ResponseType> responseTypes);

    /**
     * Sets the acknowledgements ("ACK") which are requested together with an issued Ditto {@code Command}.
     * Such ACKs are sent back to the issuer of the command so that it can be verified which steps were successful.
     * <p>
     * In addition to built-in ACK labels like
     * {@link org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel#TWIN_PERSISTED} also custom labels may be used
     * which can be sent back even by external systems.
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
     * {@link org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel#TWIN_PERSISTED} also custom labels may be used
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
     * @throws org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException if the given timeout char sequence
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
     * Puts the given metadata association to this builder.
     * An existing entry with the same key will be replaced.
     *
     * @param key the metadata key.
     * @param value the metadata value.
     * @return this builder.
     * @since 1.2.0
     */
    B putMetadata(MetadataHeaderKey key, JsonValue value);

    /**
     * Sets the allowPolicyLockout value.
     *
     * @param allowPolicyLockout the allowPolicyLockout value to be set.
     * @return this builder for method chaining.
     * @since 1.3.0
     */
    B allowPolicyLockout(boolean allowPolicyLockout);

    /**
     * Sets the tags which should be applied by persistence actors when appending an event into the event journal.
     *
     * @param journalTags the tags to apply for event journal persistence.
     * @return this builder for method chaining.
     * @since 2.0.0
     */
    B journalTags(Collection<String> journalTags);

    /**
     * Sets the condition which should be evaluated when applying the request.
     *
     * @param condition the condition to check before applying the request.
     * @return this builder for method chaining.
     * @since 2.1.0
     */
    B condition(String condition);

    /**
     * Sets the channel condition which decides, if the live channel shall be used for the request.
     *
     * @param liveChannelCondition the channel condition to check before applying the request.
     * @return this builder for method chaining.
     * @since 2.2.0
     */
    B liveChannelCondition(@Nullable String liveChannelCondition);

    /**
     * Puts an arbitrary header with the specified {@code name} and String {@code value} to this builder.
     *
     * @param key the header name to use.
     * @param value the String value.
     * @return this builder for Method Chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if key is empty
     * @throws org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException if {@code value} represents an
     * invalid Java type.
     */
    B putHeader(CharSequence key, CharSequence value);

    /**
     * Puts the specified headers to this builder. Existing headers with the same key will be replaced.
     *
     * @param headers the headers to be put.
     * @return this builder for Method Chaining.
     * @throws NullPointerException if {@code headers} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException if {@code headers} contains a value
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
     * Sets the traceparent value.
     *
     * @param traceparent the w3c traceparent header
     * @return The builder with w3c traceparent set
     * @since 2.1.0
     */
    B traceparent(@Nullable CharSequence traceparent);

    /**
     * Sets the tracestate value.
     *
     * @param tracestate the w3c tracestate header
     * @return The builder with w3c tracestate set
     * @since 2.1.0
     */
    B tracestate(@Nullable CharSequence tracestate);

    /**
     * Creates a DittoHeaders object containing the key-value-pairs which were put to this builder.
     *
     * @return the headers.
     */
    R build();

}
