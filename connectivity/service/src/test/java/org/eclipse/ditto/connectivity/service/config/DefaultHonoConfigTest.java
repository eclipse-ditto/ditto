/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.WithConfigPath;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import nl.jqno.equalsverifier.EqualsVerifier;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link DefaultHonoConfig}.
 */
public final class DefaultHonoConfigTest {

    @Rule
    public final TestName testName = new TestName();

    private ActorSystem actorSystem;

    @After
    public void after() {
        if (null != actorSystem) {
            TestKit.shutdownActorSystem(actorSystem, FiniteDuration.apply(5, TimeUnit.SECONDS), false);
        }
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultHonoConfig.class,
                areImmutable(),
                assumingFields("bootstrapServerUris").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultHonoConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void newInstanceWithNullActorSystemThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultHonoConfig(null))
                .withMessage("The actorSystem must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceThrowsDittoConfigErrorIfBaseUriSyntaxIsInvalid() {
        final var configKey = getFullQualifiedConfigKey(HonoConfig.HonoConfigValue.BASE_URI);
        final var invalidUri = "192.168.1.256:80";

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> new DefaultHonoConfig(getActorSystem(
                        ConfigFactory.parseMap(Map.of(configKey, invalidUri))
                )))
                .withMessageStartingWith("The string value at <%s> is not a URI:",
                        HonoConfig.HonoConfigValue.BASE_URI.getConfigPath())
                .withMessageEndingWith(invalidUri)
                .withCauseInstanceOf(URISyntaxException.class);
    }

    @Test
    public void getBaseUriReturnsDefaultValueIfNotContainedInConfig() {
        final var defaultHonoConfig = new DefaultHonoConfig(getActorSystem(ConfigFactory.empty()));

        assertThat(defaultHonoConfig.getBaseUri())
                .isEqualTo(URI.create(HonoConfig.HonoConfigValue.BASE_URI.getDefaultValue().toString()));
    }

    @Test
    public void getBaseUriReturnsExplicitlyConfiguredValue() {
        final var baseUri = URI.create("example.org:8080");
        final var defaultHonoConfig = new DefaultHonoConfig(getActorSystem(ConfigFactory.parseMap(
                Map.of(getFullQualifiedConfigKey(HonoConfig.HonoConfigValue.BASE_URI), baseUri.toString())
        )));

        assertThat(defaultHonoConfig.getBaseUri()).isEqualTo(baseUri);
    }

    @Test
    public void isValidateCertificatesReturnsDefaultValueIfNotContainedInConfig() {
        final var defaultHonoConfig = new DefaultHonoConfig(getActorSystem(ConfigFactory.empty()));

        assertThat(defaultHonoConfig.isValidateCertificates())
                .isEqualTo(HonoConfig.HonoConfigValue.VALIDATE_CERTIFICATES.getDefaultValue());
    }

    @Test
    public void isValidateCertificatesReturnsExplicitlyConfiguredValue() {
        final var validateCertificates = true;
        final var defaultHonoConfig = new DefaultHonoConfig(getActorSystem(ConfigFactory.parseMap(
                Map.of(getFullQualifiedConfigKey(HonoConfig.HonoConfigValue.VALIDATE_CERTIFICATES),
                        validateCertificates)
        )));

        assertThat(defaultHonoConfig.isValidateCertificates()).isEqualTo(validateCertificates);
    }

    @Test
    public void newInstanceThrowsDittoConfigErrorIfSaslMechanismIsUnknown() {
        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> new DefaultHonoConfig(getActorSystem(ConfigFactory.parseMap(
                        Map.of(getFullQualifiedConfigKey(HonoConfig.HonoConfigValue.SASL_MECHANISM), 42)
                ))))
                .withCauseInstanceOf(ConfigException.BadValue.class);
    }

    @Test
    public void getSaslMechanismReturnsDefaultValueIfNotContainedInConfig() {
        final var defaultHonoConfig = new DefaultHonoConfig(getActorSystem(ConfigFactory.empty()));

        assertThat(defaultHonoConfig.getSaslMechanism())
                .isEqualTo(HonoConfig.SaslMechanism.valueOf(
                        HonoConfig.HonoConfigValue.SASL_MECHANISM.getDefaultValue().toString()
                ));
    }

    @Test
    public void newInstanceThrowsDittoConfigErrorIfOneBootstrapServerUriSyntaxIsInvalid() {
        final var configKey = getFullQualifiedConfigKey(HonoConfig.HonoConfigValue.BOOTSTRAP_SERVERS);
        final var invalidUri = "192.168.1.256:80";
        final var bootstrapServerUrisString = "example.com," + invalidUri + ",10.1.0.35:8080";

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> new DefaultHonoConfig(getActorSystem(
                        ConfigFactory.parseMap(Map.of(configKey, bootstrapServerUrisString))
                )))
                .withMessageStartingWith("The string at index <1> for key <%s> is not a valid URI:",
                        HonoConfig.HonoConfigValue.BOOTSTRAP_SERVERS.getConfigPath())
                .withMessageEndingWith(invalidUri)
                .withCauseInstanceOf(URISyntaxException.class);
    }

    @Test
    public void getBootstrapServerUrisReturnsDefaultValueIfNotContainedInConfig() {
        final var defaultHonoConfig = new DefaultHonoConfig(getActorSystem(ConfigFactory.empty()));

        assertThat(defaultHonoConfig.getBootstrapServerUris())
                .containsOnly(URI.create(HonoConfig.HonoConfigValue.BOOTSTRAP_SERVERS.getDefaultValue().toString()));
    }

    @Test
    public void getBootstrapServerUrisReturnsExplicitlyConfiguredValues() {
        final var bootstrapServerUris = List.of(URI.create("www.example.org"),
                URI.create("tcp://192.168.10.1:8080"),
                URI.create("file://bin/server"));
        final var defaultHonoConfig = new DefaultHonoConfig(getActorSystem(ConfigFactory.parseMap(Map.of(
                getFullQualifiedConfigKey(HonoConfig.HonoConfigValue.BOOTSTRAP_SERVERS),
                bootstrapServerUris.stream()
                        .map(URI::toString)
                        .map(s -> s.concat("   ")) // blanks should get trimmed
                        .collect(Collectors.joining(","))
        ))));

        assertThat(defaultHonoConfig.getBootstrapServerUris()).containsExactlyElementsOf(bootstrapServerUris);
    }

    @Test
    public void getCredentialsReturnsDefaultValueIfNotContainedInConfig() {
        final var defaultHonoConfig = new DefaultHonoConfig(getActorSystem(ConfigFactory.empty()));

        assertThat(defaultHonoConfig.getUserPasswordCredentials())
                .isEqualTo(UserPasswordCredentials.newInstance("", ""));
    }

    @Test
    public void getCredentialsReturnsExplicitlyConfiguredValues() {
        final var username = "Herbert W. Franke";
        final var password = "PeterParsival";
        final var defaultHonoConfig = new DefaultHonoConfig(getActorSystem(ConfigFactory.parseMap(Map.of(
                getFullQualifiedConfigKey(HonoConfig.HonoConfigValue.USERNAME), username,
                getFullQualifiedConfigKey(HonoConfig.HonoConfigValue.PASSWORD), password
        ))));

        assertThat(defaultHonoConfig.getUserPasswordCredentials())
                .isEqualTo(UserPasswordCredentials.newInstance(username, password));
    }

    private static String getFullQualifiedConfigKey(final WithConfigPath withConfigPath) {
        return MessageFormat.format("{0}.{1}", HonoConfig.PREFIX, withConfigPath.getConfigPath());
    }

    private ActorSystem getActorSystem(final Config config) {
        actorSystem = ActorSystem.create(testName.getMethodName(), config);
        return actorSystem;
    }

}