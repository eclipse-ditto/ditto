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
package org.eclipse.ditto.wot.integration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.pekko.event.LoggingAdapter;
import org.eclipse.ditto.wot.api.config.WotHostValidationConfig;

/**
 * Validates hosts of WoT (Web of Things) ThingModel URLs before they are fetched via HTTP, in order to prevent
 * Server-Side Request Forgery (SSRF) against internal infrastructure.
 * <p>
 * Unless validation is disabled via configuration, hosts resolving to loopback, link-local, site-local, multicast or
 * wildcard addresses are blocked - as well as any configured blocked hostnames, blocked subnets and hosts matching the
 * configured blocked-host regex. An operator allow-list of hostnames overrides all block checks, for deployments that
 * intentionally host ThingModels on internal hosts.
 * <p>
 * Note: unlike Connectivity's {@code DefaultHostValidator}, this validator additionally blocks <em>link-local</em>
 * addresses (e.g. {@code 169.254.0.0/16} / {@code fe80::/10}), which cover the cloud instance-metadata endpoint
 * ({@code 169.254.169.254}).
 */
final class WotHostValidator {

    private final boolean enabled;
    private final Collection<String> allowedHostnames;
    private final Collection<InetAddress> blockedAddresses;
    private final Collection<String> blockedSubnets;
    @Nullable private final Pattern blockedHostRegexPattern;
    private final AddressResolver resolver;

    WotHostValidator(final WotHostValidationConfig config, final LoggingAdapter loggingAdapter) {
        this(config, loggingAdapter, InetAddress::getAllByName);
    }

    WotHostValidator(final WotHostValidationConfig config, final LoggingAdapter loggingAdapter,
            final AddressResolver resolver) {
        this.resolver = resolver;
        this.enabled = config.isEnabled();
        // hostnames are case-insensitive (RFC 4343), so normalize the allow-list to lower-case for comparison:
        this.allowedHostnames = config.getAllowedHostnames().stream()
                .map(hostname -> hostname.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.blockedAddresses = calculateBlockedAddresses(config.getBlockedHostnames(), loggingAdapter);
        this.blockedSubnets = config.getBlockedSubnets();
        // fail fast on a malformed blocked-subnet rather than throwing on every fetch later on:
        this.blockedSubnets.forEach(WotHostValidator::validateBlockedSubnet);
        final String regex = config.getBlockedHostRegex();
        // compile the blocked-host regex case-insensitively so an attacker cannot bypass it by changing host casing:
        this.blockedHostRegexPattern = regex.isEmpty() ? null : Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    private static void validateBlockedSubnet(final String subnet) {
        try {
            SubnetValidator.validateCidr(subnet);
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("Invalid 'blocked-subnets' entry configured for WoT ThingModel " +
                    "host validation: <" + subnet + ">: " + e.getMessage(), e);
        }
    }

    /**
     * Validates whether fetching a WoT ThingModel from the given host is permitted. Validation is applied (in this
     * order):
     * <ul>
     *     <li>if validation is disabled, every host is allowed</li>
     *     <li>if the host is contained in the allow-list, the host is allowed</li>
     *     <li>if the host matches the configured blocked-host regex, the host is blocked</li>
     *     <li>if the host resolves to a loopback, link-local, site-local, multicast or wildcard address, it is blocked</li>
     *     <li>if the host resolves to a configured blocked address, it is blocked</li>
     *     <li>if the host resolves to an address within a configured blocked subnet, it is blocked</li>
     * </ul>
     *
     * @param host the host to check.
     * @return the validation result.
     */
    HostValidationResult validateHost(final String host) {
        // hostnames are case-insensitive; normalize before comparing against the allow-list / regex:
        final String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!enabled) {
            return HostValidationResult.valid();
        } else if (allowedHostnames.contains(normalizedHost)) {
            // the host is explicitly allowed, do not block
            return HostValidationResult.valid();
        } else if (blockedHostRegexPattern != null && blockedHostRegexPattern.matcher(normalizedHost).matches()) {
            return HostValidationResult.blocked(host, "the host matched the configured blocked-host regex.");
        } else {
            return validateInetAddressesAndSubnets(host);
        }
    }

    private HostValidationResult validateInetAddressesAndSubnets(final String host) {
        try {
            final InetAddress[] inetAddresses = resolver.resolve(host);
            for (final InetAddress requestAddress : inetAddresses) {
                final String resolvedAddress = requestAddress.getHostAddress();
                if (requestAddress.isLoopbackAddress()) {
                    return HostValidationResult.blocked(host,
                            String.format("the hostname resolved to a loopback address (%s).", resolvedAddress));
                } else if (requestAddress.isLinkLocalAddress()) {
                    return HostValidationResult.blocked(host,
                            String.format("the hostname resolved to a link local address (%s).", resolvedAddress));
                } else if (requestAddress.isSiteLocalAddress()) {
                    return HostValidationResult.blocked(host,
                            String.format("the hostname resolved to a site local address (%s).", resolvedAddress));
                } else if (requestAddress.isMulticastAddress()) {
                    return HostValidationResult.blocked(host,
                            String.format("the hostname resolved to a multicast address (%s).", resolvedAddress));
                } else if (requestAddress.isAnyLocalAddress()) {
                    return HostValidationResult.blocked(host,
                            String.format("the hostname resolved to a wildcard address (%s).", resolvedAddress));
                } else if (blockedAddresses.contains(requestAddress)) {
                    return HostValidationResult.blocked(host,
                            String.format("the hostname resolved to a blocked address (%s).", resolvedAddress));
                }
                for (final String subnet : blockedSubnets) {
                    if (SubnetValidator.matches(subnet, requestAddress.getHostAddress())) {
                        return HostValidationResult.blocked(host,
                                String.format("the hostname resolved to address %s which resides in blocked subnet %s.",
                                        resolvedAddress, subnet));
                    }
                }
            }
        } catch (final UnknownHostException e) {
            return HostValidationResult.invalid(host,
                    String.format("the host could not be resolved: %s", e.getMessage()));
        }
        // if nothing matches the host is valid
        return HostValidationResult.valid();
    }

    private Collection<InetAddress> calculateBlockedAddresses(final Collection<String> blockedHostnames,
            final LoggingAdapter log) {
        return blockedHostnames.stream()
                .filter(host -> !host.isEmpty())
                .flatMap(host -> {
                    try {
                        return Stream.of(resolver.resolve(host));
                    } catch (final UnknownHostException e) {
                        log.warning("Could not resolve hostname during building blocked hostnames set: <{}> - " +
                                "Exception: <{}: {}>", host, e.getClass().getSimpleName(), e.getMessage());
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toSet());
    }

    /**
     * Resolves a host to its IP addresses.
     */
    @FunctionalInterface
    interface AddressResolver {

        /**
         * Resolves the given host to its addresses.
         *
         * @param host the host to resolve.
         * @return the resolved {@link java.net.InetAddress}es.
         * @throws UnknownHostException if the given host cannot be resolved successfully.
         */
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

}
