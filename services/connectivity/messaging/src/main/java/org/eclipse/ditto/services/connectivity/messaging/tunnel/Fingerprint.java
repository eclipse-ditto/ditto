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
package org.eclipse.ditto.services.connectivity.messaging.tunnel;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Wraps a colon-separated fingerprint string ({@code ae:2e:29:7b:11:....}) and the algorithm used to calculate
 * the fingerprint. It provides factory methods to create an instance from either a prefixed
 * fingerprint (e.g. {@code sha256:ae:2e:29:7b:11:...}) or a {@link java.security.PublicKey}.
 */
final class Fingerprint {

    private static final Map<String, String> SUPPORTED_ALGORITHMS = initSupportedAlgorithms();
    private static final String DEFAULT_ALGORITHM = "SHA-256";

    private final String algorithm;
    private final String fingerprint;

    private Fingerprint(final String algorithm, final String fingerprint) {
        this.algorithm = algorithm;
        this.fingerprint = fingerprint.toUpperCase();
    }

    static Optional<Fingerprint> from(final String prefixedFingerprint) {
        final int firstColon = prefixedFingerprint.indexOf(':');
        if (firstColon >= 0) {
            final Optional<String> algorithmOpt = Optional.of(prefixedFingerprint)
                    .map(fp -> fp.substring(0, firstColon))
                    .map(String::toLowerCase)
                    .map(SUPPORTED_ALGORITHMS::get);

            final String algorithm = algorithmOpt.orElse(DEFAULT_ALGORITHM);
            final String fingerprint =
                    algorithmOpt.map(a -> prefixedFingerprint.substring(firstColon + 1))
                            .orElse(prefixedFingerprint);

            return Optional.of(new Fingerprint(algorithm, fingerprint));
        } else {
            return Optional.empty();
        }
    }

    static Optional<Fingerprint> from(final PublicKey publicKey, final String algorithm) {
        return getFingerPrintFromPublicKey(publicKey, algorithm).map(fp -> new Fingerprint(algorithm, fp));
    }

    boolean matches(final PublicKey publicKey) {
        return from(publicKey, algorithm)
                .map(fingerprintFromPublicKey -> Objects.equals(this, fingerprintFromPublicKey))
                .orElse(false);
    }

    private static Optional<String> getFingerPrintFromPublicKey(final PublicKey publicKey, final String algorithm) {
        return getRawFingerPrint(publicKey, algorithm)
                .map(arr -> IntStream.range(0, arr.length)
                        .mapToObj(i -> arr[i])
                        .map(Byte::toUnsignedInt)
                        .map(Integer::toHexString)
                        .map(hex -> hex.length() == 1 ? "0" + hex : hex)
                        .collect(Collectors.joining(":")));
    }

    private static Optional<byte[]> getRawFingerPrint(final PublicKey publicKey, final String algorithm) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
            digest.update(publicKey.getEncoded());
            return Optional.of(digest.digest());
        } catch (final NoSuchAlgorithmException e) {
            return Optional.empty();
        }
    }

    private static Map<String, String> initSupportedAlgorithms() {
        final Map<String, String> supportedDigests = new HashMap<>();
        Security.getAlgorithms("MessageDigest").forEach(algorithm -> {
            // store lowercase in map for case-insensitive lookup
            supportedDigests.put(algorithm.toLowerCase(), algorithm);
            // also allow variant without "-" as it is more widely used
            supportedDigests.put(algorithm.replaceAll("-", "").toLowerCase(), algorithm);
        });
        return supportedDigests;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Fingerprint that = (Fingerprint) o;
        return Objects.equals(algorithm, that.algorithm) &&
                Objects.equals(fingerprint, that.fingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(algorithm, fingerprint);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "algorithm=" + algorithm +
                ", fingerprint=" + fingerprint +
                "]";
    }
}
