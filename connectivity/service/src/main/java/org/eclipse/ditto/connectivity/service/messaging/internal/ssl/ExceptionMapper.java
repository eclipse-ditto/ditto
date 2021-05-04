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
package org.eclipse.ditto.connectivity.service.messaging.internal.ssl;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionUnavailableException;

/**
 * Helper class to map SSL exceptions to Ditto exceptions with more meaningful descriptions.
 */
final class ExceptionMapper {

    private final DittoHeaders dittoHeaders;
    private final JsonPointer privateKeyPath;
    private final JsonPointer publicKeyPath;
    private final JsonPointer certificatePath;

    /**
     * @return an instance of the ExceptionMapper prepared to be used when working with
     * {@link org.eclipse.ditto.connectivity.model.ClientCertificateCredentials}.
     */
    static ExceptionMapper forClientCertificateCredentials() {
        return forClientCertificateCredentials(DittoHeaders.empty());
    }

    /**
     * @return an instance of the ExceptionMapper prepared to be used when working with
     * {@link org.eclipse.ditto.connectivity.model.ClientCertificateCredentials}.
     */
    static ExceptionMapper forClientCertificateCredentials(@Nullable final DittoHeaders dittoHeaders) {
        final JsonPointer certificatePath = Connection.JsonFields.CREDENTIALS.getPointer()
                .append(ClientCertificateCredentials.JsonFields.CLIENT_CERTIFICATE.getPointer());
        final JsonPointer privateKeyPath = Connection.JsonFields.CREDENTIALS.getPointer()
                .append(ClientCertificateCredentials.JsonFields.CLIENT_KEY.getPointer());
        return new ExceptionMapper(dittoHeaders == null ? DittoHeaders.empty() : dittoHeaders, privateKeyPath,
                JsonPointer.empty(), certificatePath);
    }

    /**
     * @return an instance of the ExceptionMapper prepared to be used when working with
     * {@link org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials}.
     */
    static ExceptionMapper forSshPublicKeyCredentials(final DittoHeaders dittoHeaders) {
        final JsonPointer publicKeyPath = Connection.JsonFields.CREDENTIALS.getPointer()
                .append(SshPublicKeyCredentials.JsonFields.PUBLIC_KEY.getPointer());
        final JsonPointer privateKeyPath = Connection.JsonFields.CREDENTIALS.getPointer()
                .append(SshPublicKeyCredentials.JsonFields.PRIVATE_KEY.getPointer());
        return new ExceptionMapper(dittoHeaders, privateKeyPath, publicKeyPath, JsonPointer.empty());
    }

    /**
     * @return an instance of the ExceptionMapper prepared to be used when working with
     * trusted certificates.
     */
    static ExceptionMapper forTrustedCertificates(final DittoHeaders dittoHeaders) {
        final JsonPointer trustedCertificates = Connection.JsonFields.TRUSTED_CERTIFICATES.getPointer();
        return new ExceptionMapper(dittoHeaders, JsonPointer.empty(), JsonPointer.empty(), trustedCertificates);
    }

    /**
     * Constructor.
     *
     * @param dittoHeaders the ditto headers
     * @param privateKeyPath the path to the private key in the connection configuration json
     * @param publicKeyPath the path to the public key in the connection configuration json
     * @param certificatePath the path to the certificate(s) in the connection configuration json
     */
    ExceptionMapper(final DittoHeaders dittoHeaders, final JsonPointer privateKeyPath,
            final JsonPointer publicKeyPath, final JsonPointer certificatePath) {
        this.dittoHeaders = dittoHeaders;
        this.privateKeyPath = privateKeyPath;
        this.publicKeyPath = publicKeyPath;
        this.certificatePath = certificatePath;
    }

    /**
     * @return preconfigured builder for fatal errors
     */
    DittoRuntimeExceptionBuilder<ConnectionUnavailableException> fatalError(final String whatHappened) {
        return ConnectionUnavailableException.newBuilder(ConnectionId.of("unimportant"))
                .message(String.format("Fatal error: %s.", whatHappened))
                .description("Please contact the service team.")
                .dittoHeaders(dittoHeaders);
    }

    /**
     * @return preconfigured builder for private key bad format errors
     */
    DittoRuntimeExceptionBuilder<ConnectionConfigurationInvalidException> badPrivateKeyFormat(final String label,
            final String binaryFormat) {
        return badFormat(privateKeyPath, label, binaryFormat);
    }

    /**
     * @return preconfigured builder for public key bad format errors
     */
    DittoRuntimeExceptionBuilder<ConnectionConfigurationInvalidException> badPublicKeyFormat(final String label,
            final String binaryFormat) {
        return badFormat(publicKeyPath, label, binaryFormat);
    }

    /**
     * @return preconfigured builder for certificate bad format errors
     */
    DittoRuntimeExceptionBuilder<ConnectionConfigurationInvalidException> badCertificateFormat(final String label,
            final String binaryFormat) {
        return badFormat(certificatePath, label, binaryFormat);
    }

    /**
     * @return preconfigured builder for bad format errors
     */
    DittoRuntimeExceptionBuilder<ConnectionConfigurationInvalidException> badFormat(final JsonPointer errorLocation,
            final String label, final String binaryFormat) {
        final String message = String.format("%s: bad format. " +
                        "Expect PEM-encoded %s data specified by RFC-7468 starting with '-----BEGIN %s-----'",
                errorLocation, binaryFormat, label);
        return ConnectionConfigurationInvalidException.newBuilder(message).dittoHeaders(dittoHeaders);
    }

}
