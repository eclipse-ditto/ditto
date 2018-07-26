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
package org.eclipse.ditto.services.gateway.starter.service.util;

/**
 * This class encloses everything regarding configuration keys.
 */
public final class ConfigKeys {

    private static final String DITTO_PREFIX = "ditto.";
    private static final String GATEWAY_PREFIX = DITTO_PREFIX + "gateway.";
    /**
     * Key of the "forcehttps" config entry.
     */
    public static final String FORCE_HTTPS = GATEWAY_PREFIX + "forcehttps";
    /**
     * Key of the "enablecors" config entry.
     */
    public static final String ENABLE_CORS = GATEWAY_PREFIX + "enablecors";
    /**
     * Configures whether the client/requester should be redirected to the HTTPS URL if he tries to access via plain
     * HTTP and {@link #FORCE_HTTPS} is set to true.
     */
    public static final String REDIRECT_TO_HTTPS = GATEWAY_PREFIX + "redirect-to-https";
    /**
     * Configures a blacklist of paths for which NOT to redirect to HTTPS when {@link #REDIRECT_TO_HTTPS} was set.
     */
    public static final String REDIRECT_TO_HTTPS_BLACKLIST_PATTERN =
            GATEWAY_PREFIX + "redirect-to-https-blacklist-pattern";

    private static final String ENABLED_SUFFIX = "enabled";
    private static final String HTTP_MIDSECTION = "http.";


    private static final String HTTP_PREFIX = GATEWAY_PREFIX + HTTP_MIDSECTION;

    /**
     * The supported SchemaVersions the API Gateway should support (Array of Integers).
     */
    public static final String SCHEMA_VERSIONS = HTTP_PREFIX + "schema-versions";

    private static final String WEBSOCKET_PREFIX = GATEWAY_PREFIX + "websocket.";
    /**
     * Key of the Websocket subscriber backpressure config.
     */
    public static final String WEBSOCKET_SUBSCRIBER_BACKPRESSURE =
            WEBSOCKET_PREFIX + "subscriber.backpressure-queue-size";
    /**
     * Key of the Websocket publisher backpressure config.
     */
    public static final String WEBSOCKET_PUBLISHER_BACKPRESSURE =
            WEBSOCKET_PREFIX + "publisher.backpressure-buffer-size";

    private static final String MESSAGE_PREFIX = GATEWAY_PREFIX + "message.";
    /**
     * The default timeout of claim messages initiated via /messages resource.
     */
    public static final String MESSAGE_DEFAULT_TIMEOUT = MESSAGE_PREFIX + "default-timeout";
    /**
     * The maximum possible timeout of claim messages initiated via /messages resource.
     */
    public static final String MESSAGE_MAX_TIMEOUT = MESSAGE_PREFIX + "max-timeout";

    private static final String CLAIMMESSAGE_PREFIX = GATEWAY_PREFIX + "claim-message.";
    /**
     * The default timeout of claim messages initiated via /claim resource.
     */
    public static final String CLAIMMESSAGE_DEFAULT_TIMEOUT = CLAIMMESSAGE_PREFIX + "default-timeout";
    /**
     * The maximum possible timeout of claim messages initiated via /claim resource.
     */
    public static final String CLAIMMESSAGE_MAX_TIMEOUT = CLAIMMESSAGE_PREFIX + "max-timeout";

    private static final String AUTHENTICATION_PREFIX = GATEWAY_PREFIX + "authentication.";
    private static final String AUTHENTICATION_DUMMY_PREFIX = AUTHENTICATION_PREFIX + "dummy.";
    /**
     * Whether dummy authentication should be enabled or not.
     *
     * <p><strong>Note: </strong>Don't use in production!</p>
     */
    public static final String AUTHENTICATION_DUMMY_ENABLED = AUTHENTICATION_DUMMY_PREFIX + ENABLED_SUFFIX;
    private static final String AUTHENTICATION_HTTP_PREFIX = AUTHENTICATION_PREFIX + HTTP_MIDSECTION;
    private static final String AUTHENTICATION_HTTP_PROXY_PREFIX = AUTHENTICATION_HTTP_PREFIX + "proxy.";
    /**
     * Key of the Authentication "HTTP proxy enabled" config entry.
     */
    public static final String AUTHENTICATION_HTTP_PROXY_ENABLED = AUTHENTICATION_HTTP_PROXY_PREFIX + ENABLED_SUFFIX;
    /**
     * Key of the Authentication "HTTP proxy host" config entry.
     */
    public static final String AUTHENTICATION_HTTP_PROXY_HOST = AUTHENTICATION_HTTP_PROXY_PREFIX + "host";
    /**
     * Key of the Authentication "HTTP proxy port" config entry.
     */
    public static final String AUTHENTICATION_HTTP_PROXY_PORT = AUTHENTICATION_HTTP_PROXY_PREFIX + "port";
    /**
     * Key of the Authentication "HTTP proxy username" config entry.
     */
    public static final String AUTHENTICATION_HTTP_PROXY_USERNAME = AUTHENTICATION_HTTP_PROXY_PREFIX + "username";
    /**
     * Key of the Authentication "HTTP proxy password" config entry.
     */
    public static final String AUTHENTICATION_HTTP_PROXY_PASSWORD = AUTHENTICATION_HTTP_PROXY_PREFIX + "password";

    private static final String HEALTH_CHECK_PREFIX = GATEWAY_PREFIX + "health-check.";

    /**
     * The timeout used by the health check for determining the health of a single service.
     */
    public static final String HEALTH_CHECK_SERVICE_TIMEOUT = HEALTH_CHECK_PREFIX + "service.timeout";

    private static final String HEALTH_CHECK_CLUSTER_ROLES_PREFIX = HEALTH_CHECK_PREFIX + "cluster-roles.";

    /**
     * Whether the health check for presence of all cluster roles should be enabled or not.
     */
    public static final String HEALTH_CHECK_CLUSTER_ROLES_ENABLED = HEALTH_CHECK_CLUSTER_ROLES_PREFIX + ENABLED_SUFFIX;

    /**
     * Whether the health check for presence of all cluster roles should be enabled or not.
     */
    public static final String HEALTH_CHECK_CLUSTER_ROLES_EXPECTED = HEALTH_CHECK_CLUSTER_ROLES_PREFIX + "expected";

    private static final String DEVOPS_PREFIX = GATEWAY_PREFIX + "devops.";
    /**
     * Whether devops resources (e.g. /status) should be secured with BasicAuth or not.
     */
    public static final String DEVOPS_SECURE_STATUS = DEVOPS_PREFIX + "securestatus";


    private static final String PUBLIC_HEALTH_PREFIX = GATEWAY_PREFIX + "public-health.";

    /**
     * The timeout for the cache of the external health check information.
     */
    public static final String STATUS_HEALTH_EXTERNAL_CACHE_TIMEOUT = PUBLIC_HEALTH_PREFIX + "cache-timeout";


    private static final String CACHE_PREFIX = GATEWAY_PREFIX + "cache.";

    /**
     * The maximum entries of PublicKeys to be cached.
     */
    public static final String CACHE_PUBLIC_KEYS_MAX = CACHE_PREFIX + "publickeys.maxentries";

    /**
     * The expiry of cached entries of public keys.
     */
    public static final String CACHE_PUBLIC_KEYS_EXPIRY = CACHE_PREFIX + "publickeys.expiry";

    private static final String SECRETS_PREFIX = "secrets.";
    /**
     * Key of the gateway devops password.
     */
    public static final String SECRETS_DEVOPS_PASSWORD = SECRETS_PREFIX + "devops_password";

    private static final String AKKA_PREFIX = "akka.";
    private static final String AKKA_HTTP_PREFIX = AKKA_PREFIX + HTTP_MIDSECTION;
    /**
     * Key for the Akka HTTP Server's request timeout.
     */
    public static final String AKKA_HTTP_SERVER_REQUEST_TIMEOUT = AKKA_HTTP_PREFIX + "server.request-timeout";

    /*
     * This class is not designed for instantiation.
     */
    private ConfigKeys() {
        // no-op
    }
}
