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

package org.eclipse.ditto.connectivity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableSource}.
 */
public final class ImmutableSshTunnelTest {

    private static final String URI = "foo:bar@example.com:443";
    private static final String USER = "User";
    private static final String PASSWORD = "Password";

    private static final SshTunnel SSH_TUNNEL_WITH_USERNAME_PW_CREDENTIALS_AND_EMPTY_KNOWN_HOSTS =
            ConnectivityModelFactory.newSshTunnel(true, UserPasswordCredentials.newInstance(USER, PASSWORD), false,
                    Collections.emptyList(), URI);

    private static final JsonObject SSH_TUNNEL_JSON_WITH_USERNAME_PW_CREDENTIALS_AND_EMPTY_KNOWN_HOSTS = JsonObject
            .newBuilder()
            .set(SshTunnel.JsonFields.ENABLED, true)
            .set(SshTunnel.JsonFields.CREDENTIALS, JsonFactory.newObjectBuilder()
                    .set(UserPasswordCredentials.JsonFields.TYPE, UserPasswordCredentials.TYPE)
                    .set(UserPasswordCredentials.JsonFields.USERNAME, USER)
                    .set(UserPasswordCredentials.JsonFields.PASSWORD, PASSWORD)
                    .build())
            .set(SshTunnel.JsonFields.VALIDATE_HOST, false)
            .set(SshTunnel.JsonFields.KNOWN_HOSTS, JsonArray.empty())
            .set(SshTunnel.JsonFields.URI, URI)
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSshTunnel.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSshTunnel.class, areImmutable(),
                provided(Credentials.class).isAlsoImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = SSH_TUNNEL_WITH_USERNAME_PW_CREDENTIALS_AND_EMPTY_KNOWN_HOSTS.toJson();
        assertThat(actual).isEqualTo(SSH_TUNNEL_JSON_WITH_USERNAME_PW_CREDENTIALS_AND_EMPTY_KNOWN_HOSTS);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final SshTunnel actual =
                ImmutableSshTunnel.fromJson(SSH_TUNNEL_JSON_WITH_USERNAME_PW_CREDENTIALS_AND_EMPTY_KNOWN_HOSTS);
        assertThat(actual).isEqualTo(SSH_TUNNEL_WITH_USERNAME_PW_CREDENTIALS_AND_EMPTY_KNOWN_HOSTS);
    }

    @Test
    public void addKnownHostsToExistingSshTunnel() {
        final List<String> knownHosts = new ArrayList<>();
        final String aKnownHost = "aKnownHost";
        knownHosts.add(aKnownHost);
        final SshTunnel sshTunnelWithKnownHosts = new ImmutableSshTunnel
                .Builder(SSH_TUNNEL_WITH_USERNAME_PW_CREDENTIALS_AND_EMPTY_KNOWN_HOSTS)
                .knownHosts(knownHosts)
                .build();

        assertThat(sshTunnelWithKnownHosts.getKnownHosts()).containsExactly(aKnownHost);
    }

}
