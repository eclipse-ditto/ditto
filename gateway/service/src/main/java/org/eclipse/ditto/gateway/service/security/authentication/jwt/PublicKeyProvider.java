/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A provider for {@link java.security.PublicKey}s.
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
    CompletableFuture<Optional<PublicKeyWithParser>> getPublicKeyWithParser(String issuer, String keyId);

}
