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
package org.eclipse.ditto.model.connectivity.credentials;

/**
 * Process credentials of a connection.
 *
 * @param <T> what is to be computed from the credential.
 */
public interface CredentialsVisitor<T> {

    /**
     * Evaluate X.509 credentials.
     *
     * @param credentials the X.509 credentials.
     * @return evaluation result.
     */
    T clientCertificate(ClientCertificateCredentials credentials);
}
