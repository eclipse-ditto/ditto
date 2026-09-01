/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.validation;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;

import org.apache.pekko.event.LoggingAdapter;

/**
 * Validates a given hostname against a set of fixed blocked addresses (e.g. loopback, link-local, multicast, ...), a
 * set of blocked/allowed hostnames and a set of blocked subnets from configuration.
 * <p>
 * The allowed hostnames override the blocked hostnames e.g. if a host would be blocked because it resolves to a blocked
 * address (localhost, site-local, ...), the host can be allowed by adding it to the list allowed hostnames.
 */
final class DefaultHostValidator implements HostValidator {

    private final Collection<String> allowedHostnames;
    private final Collection<InetAddress> blockedAddresses;
    private final Collection<String> blockedSubnets;
    private final AddressResolver resolver;
    private final Pattern hostRegexPattern;

    /**
     * Creates a new instance of {@link DefaultHostValidator}.
     *
     * @param connectivityConfig the connectivity config used to load the allow-/block-list
     * @param loggingAdapter logging adapter
     */
    DefaultHostValidator(final ConnectivityConfig connectivityConfig, final LoggingAdapter loggingAdapter) {
        this(connectivityConfig, loggingAdapter, InetAddress::getAllByName);
    }

    /**
     * Creates a new instance of {@link DefaultHostValidator}.
     *
     * @param connectivityConfig the connectivity config used to load the allow-/block-list
     * @param loggingAdapter logging adapter
     * @param resolver custom resolver (used for tests only)
     */
    DefaultHostValidator(final ConnectivityConfig connectivityConfig, final LoggingAdapter loggingAdapter,
            final AddressResolver resolver) {
        this.resolver = resolver;
        // hostnames are case-insensitive (RFC 4343), so normalize the allow-list to lower-case for comparison:
        this.allowedHostnames = connectivityConfig.getConnectionConfig().getAllowedHostnames().stream()
                .map(hostname -> hostname.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        final Collection<String> blockedHostnames = connectivityConfig.getConnectionConfig().getBlockedHostnames();
        this.blockedAddresses = calculateBlockedAddresses(blockedHostnames, loggingAdapter);
        final Collection<String> blockedSubnetsList = connectivityConfig.getConnectionConfig().getBlockedSubnets();
        this.blockedSubnets = filterEmptyBlockedSubnets(blockedSubnetsList);
        final var regex = connectivityConfig.getConnectionConfig().getBlockedHostRegex();
        // compile the blocked-host regex case-insensitively so an attacker cannot bypass it by changing host casing:
        this.hostRegexPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /**
     * Validate if connections to a host are allowed by checking (in this order):
     * <ul>
     *     <li>if the block-list is empty, this completely disables validation, every host is allowed</li>
     *     <li>if the host is contained in the allow-list, the host is allowed</li>
     *     <li>if the host matches the kubernetes cluster dns name suffix, the host is blocked</li>
     *     <li>if the host is resolved to a blocked ip (loopback, link-local, site-local, IPv6 unique-local,
     *     multicast, wildcard ip), the host is blocked</li>
     *     <li>if the host is contained in the block-list, the host is blocked</li>
     *     <li>if the host is contained in the blocked-subnet list, the host is blocked</li>
     *  </ul>
     * Loopback, private, multicast and wildcard addresses are allowed only if the block-list is empty or explicitly
     * contained in allow-list.
     *
     * @param host the host to check.
     * @return whether connections to the host are permitted.
     */
    @Override
    public HostValidationResult validateHost(final String host) {
        // hostnames are case-insensitive; normalize before comparing against the allow-list / regex:
        final String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (blockedAddresses.isEmpty()) {
            // If not even localhost is blocked, then permit even private, loopback, multicast and wildcard IPs.
            return HostValidationResult.valid();
        } else if (allowedHostnames.contains(normalizedHost)) {
            // the host is contained in the allow-list, do not block
            return HostValidationResult.valid();
        } else if (hostRegexPattern.matcher(normalizedHost).matches()) {
            // the host matches the regex pattern --> block
            return HostValidationResult.blocked(host);
        } else {
            return validateInetAddressesAndSubnets(host);
        }
    }

    private HostValidationResult validateInetAddressesAndSubnets(final String host) {
        // Forbid blocked, private, loopback, multicast and wildcard IPs and forbid ips in blocked subnets.
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
                } else if (isUniqueLocalAddress(requestAddress)) {
                    return HostValidationResult.blocked(host,
                            String.format("the hostname resolved to a unique local address (%s).", resolvedAddress));
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
            final var reason = String.format("The configured host '%s' is invalid: %s", host, e.getMessage());
            return HostValidationResult.invalid(host, reason);
        }
        // if nothing matches the host is valid
        return HostValidationResult.valid();
    }

    /**
     * Checks whether the given address is an IPv6 unique-local address ({@code fc00::/7}, RFC 4193) - the IPv6
     * counterpart of the IPv4 private ranges. This has to be checked explicitly, because
     * {@link InetAddress#isSiteLocalAddress()} only covers the deprecated {@code fec0::/10} site-local prefix (RFC
     * 3879) and therefore does <em>not</em> match unique-local addresses such as the IPv6 cloud instance-metadata
     * endpoint {@code fd00:ec2::254}.
     *
     * @param address the address to check.
     * @return whether the address is an IPv6 unique-local address.
     */
    private static boolean isUniqueLocalAddress(final InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }

    /**
     * Resolve blocked hostnames into IP addresses that should not be accessed.
     *
     * @param blockedHostnames blocked hostnames.
     * @param log the logger.
     * @return blocked IP addresses.
     */
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
     * Filters out empty blocked subnets.
     *
     * @param blockedSubnets blocked subnets.
     * @return the blocked subnets.
     */
    private Collection<String> filterEmptyBlockedSubnets(final Collection<String> blockedSubnets) {

        return blockedSubnets.stream()
                .filter(blockedSubnet -> !blockedSubnet.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Resolves host to ip addresses.
     */
    @FunctionalInterface
    interface AddressResolver {

        /**
         * Resolves the given host to its addresses.
         *
         * @param host the host to resolve
         * @return the resolved {@link java.net.InetAddress}es
         * @throws UnknownHostException if the given host cannot be resolved successfully
         * (see {@link java.net.InetAddress#getAllByName(String)})
         */
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

}
