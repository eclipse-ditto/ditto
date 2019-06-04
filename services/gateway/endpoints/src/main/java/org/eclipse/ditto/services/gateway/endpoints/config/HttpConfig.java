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
package org.eclipse.ditto.services.gateway.endpoints.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.gateway.endpoints.actors.DefaultHttpRequestActorPropsFactory;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the Gateway service's HTTP behaviour.
 * <p>
 * Java serialization is supported for {@code HttpConfig}.
 * </p>
 */
@Immutable
public interface HttpConfig extends org.eclipse.ditto.services.base.config.http.HttpConfig {

    /**
     * Returns the schema versions the API Gateway supports.
     *
     * @return an unmodifiable unsorted Set containing the schema versions.
     */
    Set<Integer> getSupportedSchemaVersions();

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
     * Returns the full qualified classname of the {@link org.eclipse.ditto.services.gateway.endpoints.actors.HttpRequestActorPropsFactory}
     * implementation to use for instantiating the Gateway {@link org.eclipse.ditto.services.gateway.endpoints.actors.AbstractHttpRequestActor}.
     *
     * @return the full qualified classname of the HttpRequestActorPropsFactory implementation to use.
     */
    String getActorPropsFactoryFullQualifiedClassname();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code HttpConfig}.
     */
    enum GatewayHttpConfigValue implements KnownConfigValue {

        /**
         * The schema versions the API Gateway should support.
         */
        SCHEMA_VERSIONS("http.schema-versions", Arrays.asList(1, 2)),

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
        REQUEST_TIMEOUT("request_timeout", Duration.ofMinutes(1L)),

        /**
         * The full qualified classname of the HttpRequestActorPropsFactory to instantiate.
         */
        ACTOR_PROPS_FACTORY("actor-props-factory", DefaultHttpRequestActorPropsFactory.class.getName())
        ;

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
