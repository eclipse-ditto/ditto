/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

final class SubnetValidator {

    private SubnetValidator() {
        throw new AssertionError();
    }

    /**
     * Validates that the given input address matches the matcher address. This method respects either IPv4 or IPv6
     * addresses including subnet notation.
     *
     * @param matcher the address to be matched.
     * @param input the input which is validates.
     * @return if the input address matches the matcher address.
     */
    static boolean matches(final String matcher, final String input) {
        final InetAddress inputAddress = parseAddress(input);
        final int subnetMask = getNetworkMask(matcher);
        final InetAddress matcherAddress = getMatcherAddress(matcher, subnetMask);
        if (!matchesIpFamily(matcherAddress, inputAddress)) {
            return false;
        } else if (subnetMask < 0) {
            return inputAddress.equals(matcherAddress);
        } else {
            return matchesWithSubnetMask(matcherAddress.getAddress(), inputAddress.getAddress(), subnetMask);
        }
    }

    private static boolean matchesIpFamily(final InetAddress matcher, final InetAddress input) {
        if (matcher instanceof Inet4Address) {
            return input instanceof Inet4Address;
        } else if (matcher instanceof Inet6Address) {
            return input instanceof Inet6Address;
        } else {
            throw new IllegalArgumentException(String.format("IP address %s is in no known IP family.", matcher));
        }
    }

    private static InetAddress parseAddress(final String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(String.format("Address %s has unknown host.", address), e);
        }
    }

    private static int getNetworkMask(final String address) {
        final int result;
        if (hasSubnetMask(address)) {
            final String[] addressAndMask = address.split("/");
            result = Integer.parseInt(addressAndMask[1]);
        } else {
            result = -1;
        }
        return result;
    }

    @SuppressWarnings("java:S2692")
    private static boolean hasSubnetMask(final String address) {
        return address.indexOf("/") > 0;
    }

    private static InetAddress getMatcherAddress(final String address, final int subnetMask) {
        final String intermediateIpAddress;
        if (hasSubnetMask(address)) {
            final String[] addressAndMask = address.split("/");
            intermediateIpAddress = addressAndMask[0];
        } else {
            intermediateIpAddress = address;
        }
        final InetAddress parsedAddress = parseAddress(intermediateIpAddress);
        if (!(parsedAddress.getAddress().length * 8 >= subnetMask)) {
            throw new IllegalArgumentException(String.format("IP address %s " +
                    "does not match subnet mask with length: %d", address, subnetMask));
        } else {
            return parsedAddress;
        }
    }

    private static boolean matchesWithSubnetMask(final byte[] matcher, final byte[] input, final int subnetMask) {
        final int nMaskFullBytes = subnetMask / 8;
        final byte finalByte = (byte) ('\uff00' >> (subnetMask & 7));

        for (int i = 0; i < nMaskFullBytes; ++i) {
            if (input[i] != matcher[i]) {
                return false;
            }
        }
        if (finalByte != 0) {
            return (input[nMaskFullBytes] & finalByte) == (matcher[nMaskFullBytes] & finalByte);
        } else {
            return true;
        }
    }

}
