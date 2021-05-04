/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.internal.ssl;

import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.PKIXRevocationChecker;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Pretends to be a {@link java.security.cert.PKIXRevocationChecker} to trick the JVM into not performing
 * revocation check.
 */
final class NoRevocationChecker extends PKIXRevocationChecker {

    private NoRevocationChecker() {}

    /**
     * Creates a new instance of this silently failing revocation checker.
     *
     * @return the new instance.
     */
    static PKIXRevocationChecker getInstance() {
        return new NoRevocationChecker();
    }

    @Override
    public void init(final boolean b) {
        // do nothing
    }

    @Override
    public boolean isForwardCheckingSupported() {
        return false;
    }

    @Override
    @Nullable
    public Set<String> getSupportedExtensions() {
        return null;
    }

    @Override
    public void check(final Certificate certificate, final Collection<String> collection) {
        // do nothing
    }

    @Override
    public List<CertPathValidatorException> getSoftFailExceptions() {
        return List.of();
    }

}
