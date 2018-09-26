/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.security.cache;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

/**
 * Represents a Public Key ID with an issuer.
 */
public final class PublicKeyIdWithIssuer {
    private final String keyId;
    private final String issuer;

    private PublicKeyIdWithIssuer(final String keyId, final String issuer) {
        this.keyId = keyId;
        this.issuer = issuer;
    }

    /**
     * Creates a new instance.
     *
     * @param keyId the ID of the Public Key.
     * @param issuer the issuer of the Public Key.
     * @return the created instance.
     */
    public static PublicKeyIdWithIssuer of(final String keyId, final String issuer) {
        requireNonNull(keyId);
        requireNonNull(issuer);

        return new PublicKeyIdWithIssuer(keyId, issuer);
    }

    /**
     * Returns the ID of the Public Key.
     * @return the ID of the Public Key.
     */
    public String getKeyId() {
        return keyId;
    }

    /**
     * Returns the issuer of the Public Key.
     * @return the issuer of the Public Key.
     */
    public String getIssuer() {
        return issuer;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PublicKeyIdWithIssuer that = (PublicKeyIdWithIssuer) o;
        return Objects.equals(keyId, that.keyId) &&
                Objects.equals(issuer, that.issuer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyId, issuer);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "keyId='" + keyId + '\'' +
                ", issuer='" + issuer + '\'' +
                ']';
    }
}
