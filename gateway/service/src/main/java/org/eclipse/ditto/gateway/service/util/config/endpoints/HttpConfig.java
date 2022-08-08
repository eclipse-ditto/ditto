/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.util.config.endpoints;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import akka.http.javadsl.model.MediaTypes;

/**
 * Provides configuration settings of the Gateway service's HTTP behaviour.
 */
@Immutable
public interface HttpConfig extends org.eclipse.ditto.base.service.config.http.HttpConfig {

    /**
     * Returns the schema versions the API Gateway supports.
     *
     * @return an unmodifiable unsorted Set containing the schema versions.
     */
    Set<JsonSchemaVersion> getSupportedSchemaVersions();

    /**
     * Returns the list of HTTP headers to read proxy-forwarded protocol from.
     *
     * @return the header list.
     */
    List<String> getProtocolHeaders();

    /**
     * Indicates whether transport encryption via HTTPS should be enforced.
     *
     * @return {@code true} if HTTPS should be enforced, {@code false} else.
     */
    boolean isForceHttps();

    /**
     * Indicates whether the client/requester should be redirected to the HTTPS URL if she tries to access via plain
     * HTTP and {@link #isForceHttps()} returns {@code true}.
     *
     * @return {@code true} if requests should be redirected to HTTPS URL, {@code false} else.
     */
    boolean isRedirectToHttps();

    /**
     * Returns the pattern for blocking paths which should <em>not</em> be redirected to HTTPS, even if
     * {@link #isRedirectToHttps()} return {@code true}.
     *
     * @return the pattern.
     */
    Pattern getRedirectToHttpsBlocklistPattern();

    /**
     * Indicates whether Cross-Origin Resource Sharing (CORS) should be enabled.
     *
     * @return {@code true} if CORS should be enabled, {@code false} else.
     */
    boolean isEnableCors();

    /**
     * Returns the timeout for HTTP requests.
     *
     * @return the timeout.
     */
    Duration getRequestTimeout();

    /**
     * Returns definitions of headers which should be derived from query parameters.
     * I. e. if query parameters are supplied with the same name as the configured header keys then the query parameters
     * will be converted to header key-value pairs.
     *
     * @return the definitions of headers which should be derived from query parameters.
     * @since 1.1.0
     */
    Set<HeaderDefinition> getQueryParametersAsHeaders();

    /**
     * Allowed Media-Types, which should also be accepted by the endpoints besides the one they accept.
     *
     * @return media-types.
     * @since 1.1.0
     */
    Set<String> getAdditionalAcceptedMediaTypes();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code HttpConfig}.
     */
    enum GatewayHttpConfigValue implements KnownConfigValue {

        /**
         * The schema versions the API Gateway should support.
         */
        SCHEMA_VERSIONS("http.schema-versions", List.of(2)),

        /**
         * HTTP headers to read forwarded protocols from.
         */
        PROTOCOL_HEADERS("protocol-headers", List.of()),

        /**
         * Determines whether transport encryption via HTTPS should be enforced.
         */
        FORCE_HTTPS("forcehttps", false),

        /**
         * Determines whether the client/requester should be redirected to the HTTPS URL if she tries to access via
         * plain and {@link #FORCE_HTTPS} is set to {@code true}.
         */
        REDIRECT_TO_HTTPS("redirect-to-https", false),

        /**
         * The pattern for blocking paths which should <em>not</em> be redirected to HTTPS, even if
         * {@link #REDIRECT_TO_HTTPS} is set to {@code true}.
         */
        REDIRECT_TO_HTTPS_BLOCKLIST_PATTERN("redirect-to-https-blocklist-pattern",
                "/cr.*|/api.*|/ws.*|/status.*|/overall.*"),

        /**
         * Determines whether Cross-Origin Resource Sharing (CORS) should be enabled.
         */
        ENABLE_CORS("enablecors", false),

        /**
         * The timeout for HTTP requests.
         */
        REQUEST_TIMEOUT("request-timeout", Duration.ofMinutes(1L)),

        /**
         * Denotes the name of query parameters that equal the names of well-known headers; the here defined query
         * parameters will be converted to key-value pairs of request headers for further processing.
         *
         * @since 1.1.0
         */
        QUERY_PARAMS_AS_HEADERS("query-params-as-headers", Arrays.asList(
                DittoHeaderDefinition.CHANNEL.getKey(),
                DittoHeaderDefinition.CORRELATION_ID.getKey(),
                DittoHeaderDefinition.REQUESTED_ACKS.getKey(),
                DittoHeaderDefinition.DECLARED_ACKS.getKey(),
                DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(),
                DittoHeaderDefinition.TIMEOUT.getKey(),
                DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(),
                DittoHeaderDefinition.ALLOW_POLICY_LOCKOUT.getKey(),
                DittoHeaderDefinition.CONDITION.getKey(),
                DittoHeaderDefinition.LIVE_CHANNEL_CONDITION.getKey()
        )),

        /**
         * PUT and POST resources validate that the content-type of a request is supported. With this config value
         * additional media-types can be specified, which will also be accepted. Default value
         * 'application/octet-stream' is for unknown or not further specified payload and request without any
         * content-type declaration will also be mapped to this type by akka-http.
         *
         * @since 1.1.0
         */
        ADDITIONAL_ACCEPTED_MEDIA_TYPES("additional-accepted-media-types",
                MediaTypes.APPLICATION_OCTET_STREAM.toString());

        private final String path;
        private final Object defaultValue;

        GatewayHttpConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }

}
