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
package org.eclipse.ditto.services.connectivity.messaging.internal.ssl;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.PKIXRevocationChecker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.InfoProviderFactory;

/**
 * Wraps a {@link java.security.cert.PKIXRevocationChecker} and catches failures.
 * Failure is logged using {@link #connectionLogger} but not thrown further.
 */
final class SilentlyFailingRevocationChecker extends PKIXRevocationChecker {

    private final List<CertPathValidatorException> ignoredExceptions;
    private final PKIXRevocationChecker revocationChecker;
    @Nullable private final ConnectionLogger connectionLogger;

    private SilentlyFailingRevocationChecker(final PKIXRevocationChecker revocationChecker,
            @Nullable final ConnectionLogger connectionLogger) {
        this.ignoredExceptions = new ArrayList<>();
        this.revocationChecker = checkNotNull(revocationChecker, "revocationChecker");
        this.connectionLogger = connectionLogger;
    }

    /**
     * Creates a new instance of this silently failing revocation checker.
     *
     * @param revocationChecker the revocation checker that should be wrapped.
     * @param connectionLogger used to log failures during certificate validation.
     * @return the new instance.
     */
    static PKIXRevocationChecker getInstance(final PKIXRevocationChecker revocationChecker,
            @Nullable final ConnectionLogger connectionLogger) {
        return new SilentlyFailingRevocationChecker(revocationChecker, connectionLogger);
    }

    @Override
    public void init(final boolean b) throws CertPathValidatorException {
        revocationChecker.init(b);
        ignoredExceptions.clear();
    }

    @Override
    public boolean isForwardCheckingSupported() {
        return revocationChecker.isForwardCheckingSupported();
    }

    @Override
    public Set<String> getSupportedExtensions() {
        return revocationChecker.getSupportedExtensions();
    }

    @Override
    public void check(final Certificate certificate, final Collection<String> collection) {
        try {
            revocationChecker.check(certificate, collection);
        } catch (final CertPathValidatorException e) {
            ignoredExceptions.add(e);
            if (connectionLogger != null) {
                connectionLogger.exception(InfoProviderFactory.empty(), e);
            }
        }
    }

    @Override
    public List<CertPathValidatorException> getSoftFailExceptions() {
        final List<CertPathValidatorException> softFailExceptions =
                new ArrayList<>(revocationChecker.getSoftFailExceptions());
        softFailExceptions.addAll(ignoredExceptions);
        return softFailExceptions;
    }

}
