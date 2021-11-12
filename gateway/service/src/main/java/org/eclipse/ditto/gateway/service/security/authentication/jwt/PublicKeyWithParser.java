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

package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import java.security.PublicKey;
import java.util.Objects;

import io.jsonwebtoken.JwtParser;

/**
 * Bucket holding the publicKey and jwtParser used for parsing incoming JWTs.
 */
final class PublicKeyWithParser {

    private final PublicKey publicKey;
    private final JwtParser jwtParser;

    /**
     * Creates a new {@code PublicKeyWithParser} instance.
     *
     * @param publicKey publicKey to use for jwt parsing.
     * @param jwtParser the actual jwtParser using the given publicKey.
     */
    PublicKeyWithParser(final PublicKey publicKey, final JwtParser jwtParser) {
        this.publicKey = publicKey;
        this.jwtParser = jwtParser;
    }

    JwtParser getJwtParser() {
        return jwtParser;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PublicKeyWithParser that = (PublicKeyWithParser) o;
        return Objects.equals(publicKey, that.publicKey) &&
                Objects.equals(jwtParser, that.jwtParser);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicKey, jwtParser);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "publicKey=" + publicKey +
                ", jwtParser=" + jwtParser +
                "]";
    }
}
