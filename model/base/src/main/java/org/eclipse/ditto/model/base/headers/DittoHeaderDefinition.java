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
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatchers;

/**
 * Enumeration of definitions of well known Ditto Headers including their key and Java type.
 * Note: All header keys must be lower-case;
 */
public enum DittoHeaderDefinition implements HeaderDefinition {

    /**
     * Header definition for the authorization context value.
     * <p>
     * Key: {@code "ditto-auth-context"}, Java type: {@link JsonObject}.
     * </p>
     */
    AUTHORIZATION_CONTEXT("ditto-auth-context", JsonObject.class, false, false,
            HeaderValueValidators.getJsonObjectValidator()),

    /**
     * Header definition for correlation Id value.
     * <p>
     * Key: {@code "correlation-id"}, Java type: {@link String}.
     * </p>
     */
    CORRELATION_ID("correlation-id", String.class, true, true, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for schema version value.
     * <p>
     * Key: {@code "version"}, Java type: {@code int}.
     * </p>
     */
    SCHEMA_VERSION("version", int.class, true, true, HeaderValueValidators.getIntValidator()),

    /**
     * Header definition for response required value.
     * <p>
     * Key: {@code "response-required"}, Java type: {@code boolean}.
     * </p>
     */
    RESPONSE_REQUIRED("response-required", boolean.class, true, true, HeaderValueValidators.getBooleanValidator()),

    /**
     * Header definition for dry run value.
     * <p>
     * Key: {@code "ditto-dry-run"}, Java type: {@code boolean}.
     * </p>
     */
    DRY_RUN("ditto-dry-run", boolean.class, false, false, HeaderValueValidators.getBooleanValidator()),

    /**
     * Header definition for read subjects value.
     * <p>
     * Key: {@code "read-subjects"}, Java type: {@link JsonArray}.
     * </p>
     */
    READ_SUBJECTS("ditto-read-subjects", JsonArray.class, false, false, HeaderValueValidators.getJsonArrayValidator()),

    /**
     * Header definition for subjects with revoked READ subjects.
     *
     * <p>
     * Key: {@code "read-revoked-subjects"}, Java type: {@link JsonArray}.
     * </p>
     *
     * @since 1.1.0
     */
    READ_REVOKED_SUBJECTS("ditto-read-revoked-subjects", JsonArray.class, false, false,
            HeaderValueValidators.getJsonArrayValidator()),

    /**
     * Header definition for a signal's content-type.
     * <p>
     * Key: {@code "content-type"}, Java type: {@link String}.
     * </p>
     */
    CONTENT_TYPE("content-type", String.class, true, true, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for the reply to address. MUST be lower-case.
     * "reply-to" is a standard internet message header (RFC-5322).
     * <p>
     * Key: {@code "reply-to"}, Java type: String.
     * </p>
     *
     * @since 1.1.0
     */
    REPLY_TO("reply-to", String.class, true, true, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for channel value meaning distinguishing between live/twin.
     * <p>
     * Key: {@code "ditto-channel"}, Java type: {@link String}.
     * </p>
     */
    CHANNEL("ditto-channel", String.class, false, false, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for origin value that is set to the id of the originating session.
     * <p>
     * Key: {@code "ditto-origin"}, Java type: {@link String}.
     * </p>
     */
    ORIGIN("ditto-origin", String.class, false, false, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for "ETag".
     * <p>
     * Key: {@code "ETag"}, Java type: {@link String}.
     * </p>
     */
    ETAG("etag", EntityTag.class, String.class, false, true, HeaderValueValidators.getEntityTagValidator()),

    /**
     * Header definition for "If-Match".
     * <p>
     * Key: {@code "If-Match"}, Java type: {@link String}.
     * </p>
     */
    IF_MATCH("if-match", EntityTagMatchers.class, String.class, true, false,
            HeaderValueValidators.getEntityTagMatchersValidator()),

    /**
     * Header definition for "If-None-Match".
     * <p>
     * Key: {@code "If-None-Match"}, Java type: {@link String}.
     * </p>
     */
    IF_NONE_MATCH("if-none-match", EntityTagMatchers.class, String.class, true, false,
            HeaderValueValidators.getEntityTagMatchersValidator()),

    /**
     * Header definition for the internal header "ditto-reply-target". This header is evaluated for responses to be
     * published.
     * <p>
     * Key: {@code "ditto-reply-target"}, Java type: {@link java.lang.Integer}.
     * </p>
     */
    REPLY_TARGET("ditto-reply-target", Integer.class, false, false, HeaderValueValidators.getIntValidator()),

    /**
     * Header definition for "ditto-inbound-payload-mapper".
     * <p>
     * Key: {@code "ditto-inbound-payload-mapper"}, Java type: {@link String}.
     * </p>
     */
    INBOUND_PAYLOAD_MAPPER("ditto-inbound-payload-mapper", String.class, false, false,
            HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for the authorization subject that caused an event.
     * External header of the same name is always discarded.
     * <p>
     * Key: {@code "ditto-originator"}, Java type: {@link String}.
     * </p>
     */
    ORIGINATOR("ditto-originator", String.class, false, true, HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for defining which acknowledgements ("ack") are requested for a command processed by Ditto.
     * <p>
     * Key: {@code "requested-acks"}, Java type: {@link JsonArray}.
     * </p>
     *
     * @since 1.1.0
     */
    REQUESTED_ACKS("requested-acks", JsonArray.class, true, true, HeaderValueValidators.getJsonArrayValidator()),

    /**
     * Header definition for the timeout of a command or message.
     * <p>
     * Key: {@code "timeout"}, Java type: {@code String}.
     * </p>
     *
     * @since 1.1.0
     */
    TIMEOUT("timeout", DittoDuration.class, String.class, true, true,
            HeaderValueValidators.getDittoDurationValidator()),

    /**
     * Header definition for the entity id related to the command/event/response/error.
     * <p>
     * Key: {@code "ditto-entity-id"}, Java type: {@link String}.
     * </p>
     *
     * @since 1.1.0
     */
    ENTITY_ID("ditto-entity-id", String.class, false, false,
            HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for the response header defining which authentication method that should be used
     * to gain access to a resource
     * <p>
     * Key: {@code "www-authenticate"}, Java type: {@link String}.
     * </p>
     *
     * @since 1.1.0
     */
    WWW_AUTHENTICATE("www-authenticate", String.class, false, true,
            HeaderValueValidators.getNoOpValidator()),

    /**
     * Header definition for the response header defining the HTTP "Location" where a new resource was created.
     * <p>
     * Key: {@code "location"}, Java type: {@link String}.
     * </p>
     *
     * @since 1.1.0
     */
    LOCATION("location", String.class, false, true,
            HeaderValueValidators.getNoOpValidator()),
    ;

    /**
     * Map to speed up lookup of header definition by key.
     */
    private static final Map<CharSequence, DittoHeaderDefinition> VALUES_BY_KEY = Arrays.stream(values())
            .collect(Collectors.toMap(DittoHeaderDefinition::getKey, Function.identity()));

    private final String key;
    private final Class<?> type;
    private final Class<?> serializationType;
    private final boolean readFromExternalHeaders;
    private final boolean writeToExternalHeaders;
    private final ValueValidator valueValidator;

    /**
     * @param theKey the key used as key for header map.
     * @param theType the Java type of the header value which is associated with this definition's key.
     * @param readFromExternalHeaders whether Ditto reads this header from headers sent by externals.
     * @param writeToExternalHeaders whether Ditto publishes this header to externals.
     */
    private DittoHeaderDefinition(final String theKey,
            final Class<?> theType,
            final boolean readFromExternalHeaders,
            final boolean writeToExternalHeaders,
            final ValueValidator valueValidator) {

        this(theKey, theType, theType, readFromExternalHeaders, writeToExternalHeaders, valueValidator);
    }

    /**
     * @param theKey the key used as key for header map.
     * @param theType the Java type of the header value which is associated with this definition's key.
     * @param serializationType the type to which this header value should be serialized.
     * @param readFromExternalHeaders whether Ditto reads this header from headers sent by externals.
     * @param writeToExternalHeaders whether Ditto publishes this header to externals.
     */
    private DittoHeaderDefinition(final String theKey,
            final Class<?> theType,
            final Class<?> serializationType,
            final boolean readFromExternalHeaders,
            final boolean writeToExternalHeaders,
            final ValueValidator valueValidator) {

        key = theKey.toLowerCase();
        type = theType;
        this.serializationType = serializationType;
        this.readFromExternalHeaders = readFromExternalHeaders;
        this.writeToExternalHeaders = writeToExternalHeaders;
        this.valueValidator = valueValidator;
    }

    /**
     * Finds an appropriate {@code DittoHeaderKey} for the specified key.
     *
     * @param key the key to look up.
     * @return the DittoHeaderKey or an empty Optional.
     */
    public static Optional<HeaderDefinition> forKey(@Nullable final CharSequence key) {
        return Optional.ofNullable(VALUES_BY_KEY.get(key));
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Class getJavaType() {
        return type;
    }

    @Override
    public Class getSerializationType() {
        return serializationType;
    }

    @Override
    public boolean shouldReadFromExternalHeaders() {
        return readFromExternalHeaders;
    }

    @Override
    public boolean shouldWriteToExternalHeaders() {
        return writeToExternalHeaders;
    }

    @Override
    public void validateValue(@Nullable final CharSequence value) {
        valueValidator.accept(this, value);
    }

    @Override
    public String toString() {
        return getKey();
    }

}
