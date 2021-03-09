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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;

/**
 * Implementation of {@link org.apache.sshd.client.keyverifier.ServerKeyVerifier} that verifies the server key
 * against a given list of fingerprints in the format {@code <algorithm>:<fingerprint>}, where {@code algorithm} is any
 * supported digest algorithm and the {@code fingerprint} is a string of colon separated bytes in hex format. E.g.
 * {@code SHA256:ae:2e:29:7b:11:93:ef:b3:4b:55:c6:83:28:8b:91:a4:12:c6:42:ca:21:f8:30:4d:3d:36:b5:4c:c3:b3:0f:44} is
 * a valid fingerprint. The algorithm prefix is optional and defaults to {@code SHA-256} if not specified.
 */
final class FingerprintVerifier implements ServerKeyVerifier {

    private final Set<Fingerprint> knownHosts;

    /**
     * Instantiates a new {@code FingerprintVerifier}.
     *
     * @param knownHosts the list of known host fingerprints
     */
    FingerprintVerifier(final List<String> knownHosts) {
        checkNotNull(knownHosts, "knownHosts");
        this.knownHosts = Collections.unmodifiableSet(new HashSet<>(
                knownHosts.stream()
                        .flatMap(prefixedFingerprint -> Fingerprint.from(prefixedFingerprint).stream())
                        .collect(Collectors.toSet())));
    }

    @Override
    public boolean verifyServerKey(final ClientSession clientSession,
            final SocketAddress remoteAddress,
            final PublicKey serverKey) {
        return knownHosts.stream().anyMatch(knownHost -> knownHost.matches(serverKey));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final FingerprintVerifier that = (FingerprintVerifier) o;
        return Objects.equals(knownHosts, that.knownHosts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(knownHosts);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "knownHosts=" + knownHosts +
                "]";
    }
}
