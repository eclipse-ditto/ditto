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
