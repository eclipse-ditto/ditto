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
package org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt;

import java.security.PublicKey;
import java.util.Optional;

/**
 * A provider for {@link PublicKey}s.
 */
public interface PublicKeyProvider {

    /**
     * Returns the {@code PublicKey} for the given {@code issuer} and {@code keyId}.
     *
     * @param issuer the issuer of the key.
     * @param keyId the identifier of the key.
     * @return the PublicKey.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Optional<PublicKey> getPublicKey(String issuer, String keyId);
}
