/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

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

    /**
     * Evaluate username password credentials.
     *
     * @param credentials the username password credentials.
     * @return evaluation result.
     * @since 2.0.0
     */
    T usernamePassword(UserPasswordCredentials credentials);

    /**
     * Evaluate SshPublicKeyAuthentication credentials.
     *
     * @param credentials the SshPublicKeyAuthentication credentials.
     * @return evaluation result.
     * @since 2.0.0
     */
    T sshPublicKeyAuthentication(SshPublicKeyCredentials credentials);

    /**
     * Evaluate HMAC credentials.
     *
     * @param credentials The HMAC credentials.
     * @return evaluation result.
     * @since 2.1.0
     */
    T hmac(HmacCredentials credentials);

    /**
     * Evaluate OAuth Client Credentials.
     *
     * @param credentials The OAuth Client Credentials.
     * @return evaluation result.
     * @since 2.2.0
     */
    T oauthClientCredentials(OAuthClientCredentials credentials);
}
