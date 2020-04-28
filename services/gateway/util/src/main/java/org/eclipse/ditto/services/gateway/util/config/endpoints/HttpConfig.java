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
package org.eclipse.ditto.services.gateway.util.config.endpoints;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.HeaderDefinition;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the Gateway service's HTTP behaviour.
 */
@Immutable
public interface HttpConfig extends org.eclipse.ditto.services.base.config.http.HttpConfig {

    /**
     * Returns the schema versions the API Gateway supports.
     *
     * @return an unmodifiable unsorted Set containing the schema versions.
     */
    Set<JsonSchemaVersion> getSupportedSchemaVersions();

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
     * Returns the pattern for blacklisting paths which should <em>not</em> be redirected to HTTPS, even if
     * {@link #isRedirectToHttps()} return {@code true}.
     *
     * @return the pattern.
     */
    Pattern getRedirectToHttpsBlacklistPattern();

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
     * Returns the full qualified classname of the {@code org.eclipse.ditto.services.gateway.endpoints.actors.HttpRequestActorPropsFactory}
     * implementation to use for instantiating the Gateway {@code org.eclipse.ditto.services.gateway.endpoints.actors.AbstractHttpRequestActor}.
     *
     * @return the full qualified classname of the HttpRequestActorPropsFactory implementation to use.
     */
    String getActorPropsFactoryFullQualifiedClassname();

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
     * An enumeration of the known config path expressions and their associated default values for
     * {@code HttpConfig}.
     */
    enum GatewayHttpConfigValue implements KnownConfigValue {

        /**
         * The schema versions the API Gateway should support.
         */
        SCHEMA_VERSIONS("http.schema-versions", List.of(1, 2)),

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
         * The pattern for blacklisting paths which should <em>not</em> be redirected to HTTPS, even if
         * {@link #REDIRECT_TO_HTTPS} is set to {@code true}.
         */
        REDIRECT_TO_HTTPS_BLACKLIST_PATTERN("redirect-to-https-blacklist-pattern",
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
         * The full qualified classname of the HttpRequestActorPropsFactory to instantiate.
         */
        ACTOR_PROPS_FACTORY("actor-props-factory",
                "org.eclipse.ditto.services.gateway.endpoints.actors.DefaultHttpRequestActorPropsFactory"),

        /**
         * Denotes the name of query parameters that equal the names of well-known headers; the here defined query
         * parameters will be converted to key-value pairs of request headers for further processing.
         *
         * @since 1.1.0
         */
        QUERY_PARAMS_AS_HEADERS("query-params-as-headers", Arrays.asList(DittoHeaderDefinition.CORRELATION_ID.getKey(),
                DittoHeaderDefinition.REQUESTED_ACKS.getKey(),
                DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(),
                DittoHeaderDefinition.TIMEOUT.getKey()));

        private final String path;
        private final Object defaultValue;

        private GatewayHttpConfigValue(final String thePath, final Object theDefaultValue) {
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
