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
package org.eclipse.ditto.connectivity.service.messaging.validation;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Tunnel.VALID_SSH_TUNNEL;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials;
import org.eclipse.ditto.connectivity.model.SshTunnel;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Tests {@link SshTunnelValidator}.
 */
public class SshTunnelValidatorTest {

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder().randomCorrelationId().build();
    private SshTunnelValidator VALIDATOR;

    @Before
    public void setUp() throws Exception {
        final HostValidator hostValidator = Mockito.mock(HostValidator.class);
        Mockito.when(hostValidator.validateHost(ArgumentMatchers.anyString())).thenReturn(HostValidationResult.valid());
        VALIDATOR = SshTunnelValidator.getInstance(DITTO_HEADERS, hostValidator);
    }

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
                SshPublicKeyCredentials.of("test", TestConstants.Certificates.SERVER_PUB, "dummy");
        final SshTunnel publicKeyAuth =
                ConnectivityModelFactory.newSshTunnelBuilder(VALID_SSH_TUNNEL)
                        .credentials(publicKeyAuthentication)
                        .build();
        validateAndExpectException(publicKeyAuth);
    }

    @Test
    public void testInvalidSshHost() {
        final HostValidator hostValidator = Mockito.mock(HostValidator.class);
        Mockito.doCallRealMethod().when(hostValidator).validateHostname(ArgumentMatchers.anyString(),
                ArgumentMatchers.any(DittoHeaders.class));
        Mockito.when(hostValidator.validateHost(ArgumentMatchers.anyString()))
                .thenReturn(HostValidationResult.blocked("blockedHost"));

        final SshTunnelValidator validator = SshTunnelValidator.getInstance(DITTO_HEADERS, hostValidator);
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> validator.validate(VALID_SSH_TUNNEL))
                .withMessageStartingWith("The SSH server URI '%s' is not valid", VALID_SSH_TUNNEL.getUri());
    }

    private void validateAndExpectException(final SshTunnel tunnel) {
        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> VALIDATOR.validate(tunnel));
    }
}
