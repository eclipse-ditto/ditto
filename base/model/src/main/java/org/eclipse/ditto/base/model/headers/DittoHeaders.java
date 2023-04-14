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
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Headers for commands and their responses which provide additional information needed for correlation and transfer.
 *
 * <em>Implementations of this interface are required to be immutable.</em>
 */
public interface DittoHeaders extends Jsonifiable<JsonObject>, Map<String, String>, WithManifest {

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
        if (headers instanceof DittoHeaders) {
            return (DittoHeaders) headers;
        }
        return newBuilder(headers).build();
    }

    /**
     * Returns a new empty builder for a {@code DittoHeaders} object.
     *
     * @return the builder.
     */
    @SuppressWarnings({"rawtypes", "java:S3740"})
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
    @SuppressWarnings({"rawtypes", "java:S3740"})
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
    @SuppressWarnings({"rawtypes", "java:S3740"})
    static DittoHeadersBuilder newBuilder(final JsonObject jsonObject) {
        return DefaultDittoHeadersBuilder.of(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for immutable {@code DittoHeaders}. The builder is initialised with
     * the entries of this instance.
     *
     * @return the new builder.
     */
    default DittoHeadersBuilder<?, ?> toBuilder() {
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
     * Returns the accept header of the command.
     *
     * @return the accept header.
     * @since 2.4.0
     */
    Optional<String> getAccept();

    /**
     * Returns the parsed content-type of the entity.
     *
     * @return the parsed content-type.
     * @since 1.3.0
     */
    Optional<ContentType> getDittoContentType();

    /**
     * Returns the json schema version.
     *
     * @return the schema version.
     */
    Optional<JsonSchemaVersion> getSchemaVersion();

    /**
     * Returns the AuthorizationContext for the command containing this header.
     *
     * @return the AuthorizationContext.
     */
    AuthorizationContext getAuthorizationContext();

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
     * By default, this method returns {@code true}.
     *
     * @return {@code true} if a response is required, {@code false} else.
     */
    boolean isResponseRequired();

    /**
     * Returns whether a command is to be executed as a dry run.
     *
     * @return the "dry run" value.
     */
    boolean isDryRun();

    /**
     * Indicates whether this command is flagged as sudo command which should ignore some preventions.
     *
     * @return True if the command is flagged as sudo command, otherwise false.
     * @since 2.1.0
     */
    boolean isSudo();

    /**
     * Indicates whether a query command is meant to also retrieve deleted entities
     *
     * @return True if the command is meant to also retrieve deleted entities, otherwise false.
     * @since 2.1.0
     */
    boolean shouldRetrieveDeleted();

    /**
     * Returns the condition to use for applying the request.
     *
     * @return the condition contained in the Condition header.
     * @since 2.1.0
     */
    Optional<String> getCondition();

    /**
     * Returns the channel condition, if the live-channel shall be used for the request.
     *
     * @return the condition contained in the Condition header.
     * @since 2.3.0
     */
    Optional<String> getLiveChannelCondition();

    /**
     * Returns whether the live channel condition passed to the things persistence via the
     * {@link #getLiveChannelCondition()} header did match the persisted state of a thing or not.
     *
     * @return whether the live channel condition passed to the things persistence matched or not.
     * @since 2.3.0
     */
    boolean didLiveChannelConditionMatch();

    /**
     * Returns the id of the originating session (e.g. WebSocket, AMQP, ...)
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
     * Returns the "If-Equal" option header defining whether to update a value if it was equal to the previous value
     * or not.
     *
     * @return the if-equal option header.
     * @since 3.3.0
     */
    Optional<IfEqualOption> getIfEqual();

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
     * @return the list of response types that should be published to the reply target.
     * @since 1.2.0
     */
    Collection<ResponseType> getExpectedResponseTypes();

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
     * Returns the acknowledgements ("ACK") which were requested together with an issued Ditto {@code Command}.
     * Such ACKs are sent back to the issuer of the command so that it can be verified which steps were successful.
     * <p>
     * In addition to built-in ACK labels like
     * {@link org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel#TWIN_PERSISTED} also custom labels may be used
     * which can be sent back even by external systems.
     * </p>
     *
     * @return an unsorted Set of the requested acknowledgements.
     * Changes on the set are not reflected back to this DittoHeaders instance.
     * @since 1.1.0
     */
    Set<AcknowledgementRequest> getAcknowledgementRequests();

    /**
     * Returns the timeout of a command or message.
     * <p>
     * E.g. used for when {@code AcknowledgementLabel}s were requested as timeout defining how long to wait for those
     * Acknowledgements.
     * </p>
     *
     * @return the command timeout.
     * @since 1.1.0
     */
    Optional<Duration> getTimeout();

    /**
     * Returns what to do when a thing query command with smart channel selection does not receive a valid live
     * response within the given timeout period.
     *
     * @return the live channel timeout strategy if the headers define any.
     * @since 2.3.0
     */
    Optional<LiveChannelTimeoutStrategy> getLiveChannelTimeoutStrategy();

    /**
     * Returns the metadata headers to put/set for the (modifying) command they were added to.
     *
     * @return the MetadataHeaders to put being a sorted set of {@code MetadataHeader}s.
     * Changes on the returned set are not reflected back to this DittoHeaders instance.
     * @since 1.2.0
     */
    MetadataHeaders getMetadataHeadersToPut();

    /**
     * Returns the metadata fields to get for the (retrieving) response were they should be added to.
     *
     * @return set of {@code JsonPointer}s to get {@code Metadata} for.
     * Changes on the returned set are not reflected back to this DittoHeaders instance.
     * @since 3.0.0
     */
    Set<JsonPointer> getMetadataFieldsToGet();

    /**
     * Returns the metadata fields to delete metadata for the modifying request.
     *
     * @return set of {@code JsonPointer}s to delete {@code Metadata} for.
     * Changes on the returned set are not reflected back to this DittoHeaders instance.
     * @since 3.0.0
     */
    Set<JsonPointer> getMetadataFieldsToDelete();

    /**
     * Returns whether the policy lockout is allowed.
     *
     * @return {@code true} if the policy lockout is allowed
     * @since 1.3.0
     */
    boolean isAllowPolicyLockout();

    /**
     * Returns the tags which should be applied by persistence actors when appending an event into the event journal.
     *
     * @return the tags to apply for event journal persistence.
     * @since 2.0.0
     */
    Set<String> getJournalTags();


    /**
     * @return the w3c traceparent header or an empty optional if not available
     * @since 2.1.0
     */
    Optional<String> getTraceParent();

    /**
     * @return the w3c tracestate header or an empty optional if not available
     * @since 2.1.0
     */
    Optional<String> getTraceState();

    /**
     * Return a copy of the headers with the original capitalization of header keys.
     *
     * @return headers map with the original capitalization.
     * @since 2.0.0
     */
    Map<String, String> asCaseSensitiveMap();

    @Override
    @Nonnull
    default String getManifest() {
        // subclasses are serialized as DittoHeaders
        return DittoHeaders.class.getSimpleName();
    }
}
