/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.api.config;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for restricting to which hosts WoT (Web of Things) ThingModels may be fetched via
 * HTTP, in order to prevent Server-Side Request Forgery (SSRF) against internal infrastructure.
 *
 * @since 3.8.13
 */
@Immutable
public interface WotHostValidationConfig {

    /**
     * Returns whether host validation of WoT ThingModel URLs is enabled.
     * When enabled (the default), fetching ThingModels from loopback, link-local, site-local, multicast and wildcard
     * addresses is blocked unless the host is explicitly added to the {@link #getAllowedHostnames() allowed hostnames}.
     *
     * @return whether host validation is enabled.
     */
    boolean isEnabled();

    /**
     * Returns the hostnames which are always allowed to be used for fetching WoT ThingModels, overriding the blocked
     * address checks. Useful for deployments intentionally hosting ThingModels on internal hosts.
     *
     * @return the collection of allowed hostnames.
     */
    Collection<String> getAllowedHostnames();

    /**
     * Returns additional hostnames which are blocked from being used for fetching WoT ThingModels (on top of the
     * always-blocked loopback / link-local / site-local / multicast / wildcard addresses).
     *
     * @return the collection of blocked hostnames.
     */
    Collection<String> getBlockedHostnames();

    /**
     * Returns the subnets (CIDR notation) which are blocked from being used for fetching WoT ThingModels.
     *
     * @return the collection of blocked subnets.
     */
    Collection<String> getBlockedSubnets();

    /**
     * Returns a regular expression which, if a host matches it, causes the host to be blocked from being used for
     * fetching WoT ThingModels. An empty string disables the regex check.
     *
     * @return the blocked host regex.
     */
    String getBlockedHostRegex();

    /**
     * Returns the maximum number of HTTP redirects to follow when fetching a WoT ThingModel. Each redirect target is
     * re-validated against this configuration.
     *
     * @return the maximum number of redirects to follow.
     */
    int getMaxRedirects();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code WotHostValidationConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Whether host validation of WoT ThingModel URLs is enabled.
         */
        ENABLED("enabled", true),

        /**
         * The hostnames which are always allowed (comma separated), overriding the blocked address checks.
         */
        ALLOWED_HOSTNAMES("allowed-hostnames", ""),

        /**
         * Additional blocked hostnames (comma separated).
         */
        BLOCKED_HOSTNAMES("blocked-hostnames", ""),

        /**
         * Blocked subnets in CIDR notation (comma separated). Defaults to the carrier-grade NAT range
         * {@code 100.64.0.0/10} (RFC 6598), which is not covered by any of the built-in address-class checks.
         */
        BLOCKED_SUBNETS("blocked-subnets", "100.64.0.0/10"),

        /**
         * A regular expression blocking matching hosts. Empty disables the check.
         */
        BLOCKED_HOST_REGEX("blocked-host-regex", ""),

        /**
         * The maximum number of HTTP redirects to follow when fetching a ThingModel.
         */
        MAX_REDIRECTS("max-redirects", 5);

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
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
