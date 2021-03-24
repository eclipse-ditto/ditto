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
package org.eclipse.ditto.services.connectivity.messaging.validation;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.SshPublicKeyCredentials;
import org.eclipse.ditto.model.connectivity.SshTunnel;
import org.eclipse.ditto.model.connectivity.UserPasswordCredentials;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.messaging.validation.SshTunnelValidator}.
 */
public class SshTunnelValidatorTest {

    private static final SshTunnel VALID_SSH_TUNNEL =
            ConnectivityModelFactory.newSshTunnel(true, UserPasswordCredentials.newInstance("username", "password"),
                    true, List.of("MD5:11:22:33:44:55"), "ssh://host:2222");
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder().randomCorrelationId().build();
    private static final SshTunnelValidator VALIDATOR = SshTunnelValidator.getInstance(DITTO_HEADERS);

    @Test
    public void validConfiguration() {
        VALIDATOR.validate(VALID_SSH_TUNNEL);
    }

    @Test
    public void testWrongProtocol() {
        validateAndExpectException(
                ConnectivityModelFactory.newSshTunnelBuilder(VALID_SSH_TUNNEL)
                        .uri("tcp://host:1234")
                        .build());
    }

    @Test
    public void testNoProtocol() {
        validateAndExpectException(
                ConnectivityModelFactory.newSshTunnelBuilder(VALID_SSH_TUNNEL)
                        .uri("host:1234")
                        .build());
    }

    @Test
    public void testEmptyUsername() {
        validateAndExpectException(ConnectivityModelFactory.newSshTunnelBuilder(VALID_SSH_TUNNEL)
                .credentials(UserPasswordCredentials.newInstance("", "password"))
                .build());
    }

    @Test
    public void testEmptyPassword() {
        validateAndExpectException(ConnectivityModelFactory.newSshTunnelBuilder(VALID_SSH_TUNNEL)
                .credentials(UserPasswordCredentials.newInstance("username", ""))
                .build());
    }

    @Test
    public void testValidPublicKeyAuthentication() {
        final SshPublicKeyCredentials publicKeyAuthentication =
                SshPublicKeyCredentials.of("test", TestConstants.Certificates.SERVER_PUB,
                        TestConstants.Certificates.SERVER_KEY);
        final SshTunnel publicKeyAuth =
                ConnectivityModelFactory.newSshTunnelBuilder(VALID_SSH_TUNNEL)
                        .credentials(publicKeyAuthentication)
                        .build();
        VALIDATOR.validate(publicKeyAuth);
    }

    @Test
    public void testInvalidPublicKey() {
        final SshPublicKeyCredentials publicKeyAuthentication =
                SshPublicKeyCredentials.of("test", "dummy",
                        TestConstants.Certificates.SERVER_KEY);
        final SshTunnel publicKeyAuth =
                ConnectivityModelFactory.newSshTunnelBuilder(VALID_SSH_TUNNEL)
                        .credentials(publicKeyAuthentication)
                        .build();
        validateAndExpectException(publicKeyAuth);
    }

    @Test
    public void testInvalidPrivateKey() {
        final SshPublicKeyCredentials publicKeyAuthentication =
                SshPublicKeyCredentials.of("test", TestConstants.Certificates.SERVER_PUB,
                        "dummy");
        final SshTunnel publicKeyAuth =
                ConnectivityModelFactory.newSshTunnelBuilder(VALID_SSH_TUNNEL)
                        .credentials(publicKeyAuthentication)
                        .build();
        validateAndExpectException(publicKeyAuth);
    }

    private void validateAndExpectException(final SshTunnel tunnel) {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> VALIDATOR.validate(tunnel));
    }
}
