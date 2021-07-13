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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.net.util.SubnetUtils;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;

import akka.event.LoggingAdapter;

/**
 * Validates a given hostname against a set of fixed blocked addresses (e.g. loopback, multicast, ...) and a set of
 * blocked/allowed hostnames from configuration.
 * <p>
 * The allowed hostnames override the blocked hostnames e.g. if a host would be blocked because it resolves to a blocked
 * address (localhost, site-local, ...), the host can be allowed by adding it to the list allowed hostnames.
 */
final class DefaultHostValidator implements HostValidator {

    private final Collection<String> allowedHostnames;
    private final Collection<InetAddress> blockedAddresses;
    private final Collection<SubnetUtils.SubnetInfo> blockedSubnets;
    private final AddressResolver resolver;

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
        this.allowedHostnames = connectivityConfig.getConnectionConfig().getAllowedHostnames();
        final Collection<String> blockedHostnames = connectivityConfig.getConnectionConfig().getBlockedHostnames();
        this.blockedAddresses = calculateBlockedAddresses(blockedHostnames, loggingAdapter);
        final Collection<String> blockedSubnetsList = connectivityConfig.getConnectionConfig().getBlockedSubnets();
        this.blockedSubnets = calculateBlockedSubnets(blockedSubnetsList, loggingAdapter);
    }

    /**
     * Validate if connections to a host are allowed by checking (in this order):
     * <ul>
     *     <li>if the block-list is empty, this completely disables validation, every host is allowed</li>
     *     <li>if the host is contained in the allow-list, the host is allowed</li>
     *     <li>if the host is resolved to a blocked ip (loopback, site-local, multicast, wildcard ip), the host is blocked</li>
     *     <li>if the host is contained in the block-list, the host is blocked</li>
     *  </ul>
     * Loopback, private, multicast and wildcard addresses are allowed only if the block-list is empty or explicitly
     * contained in allow-list.
     *
     * @param host the host to check.
     * @return whether connections to the host are permitted.
     */
    @Override
    public HostValidationResult validateHost(final String host) {
        if (blockedAddresses.isEmpty()) {
            // If not even localhost is blocked, then permit even private, loopback, multicast and wildcard IPs.
            return HostValidationResult.valid();
        } else if (allowedHostnames.contains(host)) {
            // the host is contained in the allow-list, do not block
            return HostValidationResult.valid();
        } else {
            // Forbid blocked, private, loopback, multicast and wildcard IPs.
            try {
                final InetAddress[] inetAddresses = resolver.resolve(host);
                for (final InetAddress requestAddress : inetAddresses) {
                    if (requestAddress.isLoopbackAddress()) {
                        return HostValidationResult.blocked(host, "the hostname resolved to a loopback address.");
                    } else if (requestAddress.isSiteLocalAddress()) {
                        return HostValidationResult.blocked(host, "the hostname resolved to a site local address.");
                    } else if (requestAddress.isMulticastAddress()) {
                        return HostValidationResult.blocked(host, "the hostname resolved to a multicast address.");
                    } else if (requestAddress.isAnyLocalAddress()) {
                        return HostValidationResult.blocked(host, "the hostname resolved to a wildcard address.");
                    } else if (blockedAddresses.contains(requestAddress)) {
                        // host is contained in the block-list --> block
                        return HostValidationResult.blocked(host);
                    }
                    for (final SubnetUtils.SubnetInfo subnet : blockedSubnets) {
                        if (subnet.isInRange(requestAddress.getHostAddress())) {
                            return HostValidationResult.blocked(host, "the hostname resides in a blocked subnet.");
                        }
                    }
                }
                return HostValidationResult.valid();
            } catch (UnknownHostException e) {
                final var reason = String.format("The configured host '%s' is invalid: %s", host, e.getMessage());
                return HostValidationResult.invalid(host, reason);
            }
        }
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
                        log.warning("Could not resolve hostname during building blocked hostnames set: <{}>",
                                host);
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toSet());
    }

    /**
     * Calculate blocked subnets from cidr range strings that should not be accessed.
     *
     * @param blockedSubnets blocked subnets.
     * @param log the logger.
     * @return info of blocked subnets.
     */
    private Collection<SubnetUtils.SubnetInfo> calculateBlockedSubnets(final Collection<String> blockedSubnets,
            final LoggingAdapter log) {

        return blockedSubnets.stream()
                .filter(blockedSubnet -> !blockedSubnet.isEmpty())
                .flatMap(blockedSubnet -> {
                    try {
                        return Stream.of(new SubnetUtils(blockedSubnet).getInfo());
                    } catch (final IllegalArgumentException e) {
                        log.warning("Could not create subnet info during building blocked subnets set: <{}>",
                                blockedSubnet);
                        return Stream.empty();
                    }
                })
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
