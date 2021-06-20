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

package org.eclipse.ditto.connectivity.service.messaging.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.httppush.AzMonitorRequestSigningFactory;
import org.eclipse.ditto.connectivity.service.messaging.signing.AzSaslSigning;
import org.eclipse.ditto.connectivity.service.messaging.signing.AzSaslSigningFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorSystem;

/**
 * Unit test for {@link SaslPlainCredentialsSupplier}.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class SaslPlainCredentialsSupplierTest {

    private static final String SHARED_KEY_NAME = "name";
    private static final String SHARED_KEY = "key";
    private static final String ENDPOINT = "hmac.ditto.eclipse.org";
    private static final Duration TTL = Duration.ofMinutes(3L);

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = ActorSystem.create(SaslPlainCredentialsSupplier.class.getSimpleName(), TestConstants.CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        if (null != actorSystem) {
            actorSystem.terminate();
        }
    }

    @Test
    public void createsCredentialsForAzSaslRequestSigning() {
        final HmacCredentials credentials = createAzSaslHmacCredentials();
        final AzSaslSigning signing = getAzSaslRequestSigning();
        final Connection connection = TestConstants.createConnection()
                .toBuilder()
                .credentials(credentials)
                .build();
        final Optional<UserPasswordCredentials> result =
                SaslPlainCredentialsSupplier.of(actorSystem).get(connection);

        assertCredentialsContainCorrectSignature(result, signing);
    }

    private void assertCredentialsContainCorrectSignature(final Optional<UserPasswordCredentials> credentials,
            final AzSaslSigning expectedSigning) {
        assertThat(credentials).isNotEmpty();

        final Instant expiry = extractExpiryFromSharedAccessSignature(credentials.get().getPassword());
        final Instant calculationTimestamp = expiry.minus(TTL);
        final Optional<UserPasswordCredentials> expectedCredentials =
                expectedSigning.createSignedCredentials(calculationTimestamp);

        assertThat(credentials).isEqualTo(expectedCredentials);
    }

    @Test
    public void doesNotCreateCredentialsForConnectionWithoutCredentials() {
        final Connection connection = TestConstants.createConnection()
                .toBuilder()
                .uri("http://localhost:1234")
                .credentials(null)
                .build();
        final Optional<UserPasswordCredentials> result =
                SaslPlainCredentialsSupplier.of(actorSystem).get(connection);

        assertThat(result)
                .withFailMessage(
                        "SaslPlainCredentialsSupplier should not provide credentials for connection without credentials")
                .isEmpty();
    }

    @Test
    public void createFromUriIfCredentialTypeDoesNotMatch() {
        final Connection connection = TestConstants.createConnection()
                .toBuilder()
                .uri("http://user:pass@localhost:1234")
                .credentials(SshPublicKeyCredentials.of("user", "publicKey", "privateKey"))
                .build();

        final Optional<UserPasswordCredentials> result =
                SaslPlainCredentialsSupplier.of(actorSystem).get(connection);

        assertThat(result).contains(UserPasswordCredentials.newInstance("user", "pass"));
    }

    @Test
    public void failForUnknownHmacAlgorithm() {
        final Connection connection = TestConstants.createConnection()
                .toBuilder()
                .uri("http://localhost:1234")
                .credentials(createAzMonitorHmacCredentials())
                .build();

        assertThatExceptionOfType(MessageSendingFailedException.class)
                .isThrownBy(() -> SaslPlainCredentialsSupplier.of(actorSystem).get(connection));
    }

    private Instant extractExpiryFromSharedAccessSignature(final String password) {
        final Matcher matcher = Pattern.compile(".*&se=(?<se>\\d+).*").matcher(password);
        if (matcher.find()) {
            return Instant.ofEpochSecond(Long.parseLong(matcher.group("se")));
        } else {
            throw new AssertionError("Could not find sequence '&se=<timestamp>' in password: " + password);
        }
    }

    private HmacCredentials createAzSaslHmacCredentials() {
        final JsonObject parameters =
                JsonObject.newBuilder().set(AzSaslSigningFactory.JsonFields.SHARED_KEY_NAME, SHARED_KEY_NAME)
                        .set(AzSaslSigningFactory.JsonFields.SHARED_KEY, SHARED_KEY)
                        .set(AzSaslSigningFactory.JsonFields.TTL, TTL.getSeconds() + "s")
                        .set(AzSaslSigningFactory.JsonFields.ENDPOINT, ENDPOINT)
                        .build();
        return HmacCredentials.of("az-sasl", parameters);
    }

    private HmacCredentials createAzMonitorHmacCredentials() {
        final JsonObject parameters = JsonObject.newBuilder()
                .set(AzMonitorRequestSigningFactory.JsonFields.WORKSPACE_ID, "workspace")
                .set(AzMonitorRequestSigningFactory.JsonFields.SHARED_KEY, SHARED_KEY)
                .build();
        return HmacCredentials.of("az-monitor-2016-04-01", parameters);
    }

    private AzSaslSigning getAzSaslRequestSigning() {
        return AzSaslSigning.of(SHARED_KEY_NAME, SHARED_KEY, TTL, ENDPOINT);
    }

}
