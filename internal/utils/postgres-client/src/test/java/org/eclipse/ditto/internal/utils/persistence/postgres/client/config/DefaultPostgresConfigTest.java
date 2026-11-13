/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultPostgresConfig} and its nested config sections.
 */
public final class DefaultPostgresConfigTest {

    private static Config dittoConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        dittoConfig = ConfigFactory.load("postgres-test").getConfig("ditto");
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultPostgresConfig.class).usingGetClass().verify();
        EqualsVerifier.forClass(DefaultPoolConfig.class).usingGetClass().verify();
        EqualsVerifier.forClass(DefaultSslConfig.class).usingGetClass().verify();
        EqualsVerifier.forClass(DefaultDdlCredentialsConfig.class).usingGetClass().verify();
        EqualsVerifier.forClass(DefaultBootstrapConfig.class).usingGetClass().verify();
        EqualsVerifier.forClass(DefaultBootstrapRetryConfig.class).usingGetClass().verify();
    }

    @Test
    public void returnsDefaultValuesWhenBaseConfigIsEmpty() {
        final DefaultPostgresConfig underTest = DefaultPostgresConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getUri())
                .as("uri")
                .isEqualTo(PostgresConfig.PostgresConfigValue.URI.getDefaultValue());
        softly.assertThat(underTest.getUsername()).as("username").isEmpty();
        softly.assertThat(underTest.getPassword()).as("password").isEmpty();
        softly.assertThat(underTest.getPoolerMode()).as("pooler-mode").isEqualTo(PoolerMode.DIRECT);
        softly.assertThat(underTest.getConnectTimeout()).as("connect-timeout").isEqualTo(Duration.ofSeconds(2));
        softly.assertThat(underTest.getStatementTimeout()).as("statement-timeout").isEqualTo(Duration.ofSeconds(60));

        final PostgresConfig.PoolConfig pool = underTest.getPoolConfig();
        softly.assertThat(pool.getInitialSize()).as("pool.initial-size").isEqualTo(0);
        softly.assertThat(pool.getMaxSize()).as("pool.max-size").isEqualTo(25);
        softly.assertThat(pool.getMaxAcquireTime()).as("pool.max-acquire-time").isEqualTo(Duration.ofSeconds(4));
        softly.assertThat(pool.getAcquireRetry()).as("pool.acquire-retry").isEqualTo(0);
        softly.assertThat(pool.getPendingAcquireMaxSize())
                .as("pool.pending-acquire-max-size derived as 2 x max-size when unset")
                .isEqualTo(50);
        softly.assertThat(pool.getFetchSize()).as("pool.fetch-size").isEqualTo(0);
        softly.assertThat(pool.getPreparedStatementCacheQueries())
                .as("pool.prepared-statement-cache-queries").isEqualTo(0);
        softly.assertThat(pool.getDdlCredentials().isConfigured()).as("ddl configured").isFalse();

        final PostgresConfig.SslConfig ssl = underTest.getSslConfig();
        softly.assertThat(ssl.getMode()).as("ssl.mode").isEqualTo("verify-full");
        softly.assertThat(ssl.getRootCert()).as("ssl.root-cert").isEmpty();
        softly.assertThat(ssl.isAllowSystemTruststore()).as("ssl.allow-system-truststore").isFalse();

        final PostgresConfig.BootstrapConfig bootstrap = underTest.getBootstrapConfig();
        softly.assertThat(bootstrap.getAttemptTimeout()).as("bootstrap.attempt-timeout")
                .isEqualTo(Duration.ofSeconds(60));
        softly.assertThat(bootstrap.isExitOnFailure()).as("bootstrap.exit-on-failure").isTrue();
        softly.assertThat(bootstrap.getRetryConfig().isEnabled()).as("bootstrap.retry.enabled").isTrue();
        softly.assertThat(bootstrap.getRetryConfig().getMaxDuration()).as("bootstrap.retry.max-duration")
                .isEqualTo(Duration.ofSeconds(60));
        softly.assertThat(bootstrap.getRetryConfig().getMinBackoff()).as("bootstrap.retry.min-backoff")
                .isEqualTo(Duration.ofSeconds(1));
        softly.assertThat(bootstrap.getRetryConfig().getMaxBackoff()).as("bootstrap.retry.max-backoff")
                .isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    public void returnsConfiguredValues() {
        final DefaultPostgresConfig underTest = DefaultPostgresConfig.of(dittoConfig);

        softly.assertThat(underTest.getUri()).as("uri").isEqualTo("r2dbc:postgresql://db.example:6432/ditto_test");
        softly.assertThat(underTest.getUsername()).as("username").contains("ditto_runtime");
        softly.assertThat(underTest.getPassword()).as("password").contains("runtime_secret");
        softly.assertThat(underTest.getPoolerMode()).as("pooler-mode").isEqualTo(PoolerMode.TRANSACTION);
        softly.assertThat(underTest.getConnectTimeout()).as("connect-timeout").isEqualTo(Duration.ofSeconds(7));
        softly.assertThat(underTest.getStatementTimeout()).as("statement-timeout").isEqualTo(Duration.ofSeconds(90));

        final PostgresConfig.PoolConfig pool = underTest.getPoolConfig();
        softly.assertThat(pool.getInitialSize()).as("pool.initial-size").isEqualTo(5);
        softly.assertThat(pool.getMaxSize()).as("pool.max-size").isEqualTo(200);
        softly.assertThat(pool.getMaxAcquireTime()).as("pool.max-acquire-time").isEqualTo(Duration.ofSeconds(42));
        softly.assertThat(pool.getAcquireRetry()).as("pool.acquire-retry").isEqualTo(2);
        softly.assertThat(pool.getPendingAcquireMaxSize()).as("pool.pending-acquire-max-size").isEqualTo(17);
        softly.assertThat(pool.getMaxIdleTime()).as("pool.max-idle-time").isEqualTo(Duration.ofMinutes(5));
        softly.assertThat(pool.getMaxLifeTime()).as("pool.max-life-time").isEqualTo(Duration.ofMinutes(30));
        softly.assertThat(pool.getFetchSize()).as("pool.fetch-size").isEqualTo(50);
        softly.assertThat(pool.getPreparedStatementCacheQueries())
                .as("pool.prepared-statement-cache-queries").isEqualTo(256);

        final PostgresConfig.DdlCredentialsConfig ddl = pool.getDdlCredentials();
        softly.assertThat(ddl.isConfigured()).as("ddl configured").isTrue();
        softly.assertThat(ddl.getUsername()).as("ddl.username").contains("ditto_ddl");
        softly.assertThat(ddl.getPassword()).as("ddl.password").contains("ddl_secret");

        final PostgresConfig.SslConfig ssl = underTest.getSslConfig();
        softly.assertThat(ssl.getMode()).as("ssl.mode").isEqualTo("verify-ca");
        softly.assertThat(ssl.getRootCert()).as("ssl.root-cert").contains("/etc/ditto/pg-tls/ca.crt");
        softly.assertThat(ssl.getCert()).as("ssl.cert").contains("/etc/ditto/pg-tls/client.crt");
        softly.assertThat(ssl.getKey()).as("ssl.key").contains("/etc/ditto/pg-tls/client.key");
        softly.assertThat(ssl.getKeyPassword()).as("ssl.key-password").contains("key_secret");
        softly.assertThat(ssl.isAllowSystemTruststore()).as("ssl.allow-system-truststore").isFalse();

        final PostgresConfig.BootstrapConfig bootstrap = underTest.getBootstrapConfig();
        softly.assertThat(bootstrap.getAttemptTimeout()).as("bootstrap.attempt-timeout")
                .isEqualTo(Duration.ofSeconds(13));
        softly.assertThat(bootstrap.isExitOnFailure()).as("bootstrap.exit-on-failure").isFalse();
        softly.assertThat(bootstrap.getRetryConfig().isEnabled()).as("bootstrap.retry.enabled").isFalse();
        softly.assertThat(bootstrap.getRetryConfig().getMaxDuration()).as("bootstrap.retry.max-duration")
                .isEqualTo(Duration.ZERO);
        softly.assertThat(bootstrap.getRetryConfig().getMinBackoff()).as("bootstrap.retry.min-backoff")
                .isEqualTo(Duration.ofMillis(250));
        softly.assertThat(bootstrap.getRetryConfig().getMaxBackoff()).as("bootstrap.retry.max-backoff")
                .isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    public void bootstrapRetrySettingsAreReadFromTheNestedBlocks() {
        final PostgresConfig.BootstrapConfig bootstrap = bootstrapConfigOf("retry.max-duration = 5m");
        assertThat(bootstrap.getRetryConfig().getMaxDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(bootstrap.getRetryConfig().getMinBackoff()).as("unset keys keep their defaults")
                .isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    public void invalidBootstrapDurationsAreConfigErrors() {
        assertThatThrownBy(() -> bootstrapConfigOf("attempt-timeout = 0s"))
                .isInstanceOf(DittoConfigError.class).hasMessageContaining("attempt-timeout");
        assertThatThrownBy(() -> bootstrapConfigOf("retry.min-backoff = 0s"))
                .isInstanceOf(DittoConfigError.class).hasMessageContaining("min-backoff");
        assertThatThrownBy(() -> bootstrapConfigOf("retry.max-duration = -1s"))
                .isInstanceOf(DittoConfigError.class).hasMessageContaining("max-duration");
        assertThatThrownBy(() -> bootstrapConfigOf("retry { min-backoff = 5s, max-backoff = 2s }"))
                .isInstanceOf(DittoConfigError.class).hasMessageContaining("max-backoff");
    }

    private static PostgresConfig.BootstrapConfig bootstrapConfigOf(final String bootstrapBlock) {
        return DefaultPostgresConfig.of(ConfigFactory.parseString("postgresql.bootstrap {\n" + bootstrapBlock + "\n}"))
                .getBootstrapConfig();
    }

    @Test
    public void pendingAcquireMaxSizeIsDerivedAsTwiceMaxSizeWhenUnset() {
        softly.assertThat(poolConfigOf("max-size = 7").getPendingAcquireMaxSize())
                .as("unset -> 2 x max-size")
                .isEqualTo(14);
        softly.assertThat(poolConfigOf("max-size = 7\npending-acquire-max-size = null").getPendingAcquireMaxSize())
                .as("explicit null behaves like unset")
                .isEqualTo(14);
        softly.assertThat(poolConfigOf("max-size = " + Integer.MAX_VALUE).getPendingAcquireMaxSize())
                .as("the derived bound saturates instead of overflowing")
                .isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    public void pendingAcquireMaxSizeAcceptsAnExplicitBoundZeroAndMinusOneForUnbounded() {
        softly.assertThat(poolConfigOf("max-size = 7\npending-acquire-max-size = 3").getPendingAcquireMaxSize())
                .as("explicit bound wins over the derived default")
                .isEqualTo(3);
        softly.assertThat(poolConfigOf("pending-acquire-max-size = 0").getPendingAcquireMaxSize())
                .as("0 = no queue at all: fail every borrow once max-size connections are in use")
                .isZero();
        softly.assertThat(poolConfigOf("pending-acquire-max-size = -1").getPendingAcquireMaxSize())
                .as("-1 = unbounded (reactor-pool semantics)")
                .isEqualTo(PostgresConfig.PoolConfig.UNBOUNDED_PENDING_ACQUIRE);
    }

    @Test
    public void pendingAcquireMaxSizeBelowMinusOneIsRejected() {
        assertThatThrownBy(() -> poolConfigOf("pending-acquire-max-size = -2"))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("pending-acquire-max-size");
    }

    @Test
    public void nonNumericPendingAcquireMaxSizeIsAConfigError() {
        // e.g. POSTGRES_POOL_PENDING_ACQUIRE_MAX_SIZE=lots: must fail like every other invalid pool value, as a
        // DittoConfigError naming the key, not as a raw typesafe ConfigException.
        assertThatThrownBy(() -> poolConfigOf("pending-acquire-max-size = \"lots\""))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("pending-acquire-max-size");
    }

    @Test
    public void negativeAcquireRetryIsRejected() {
        assertThatThrownBy(() -> poolConfigOf("acquire-retry = -1"))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("acquire-retry");
    }

    private static PostgresConfig.PoolConfig poolConfigOf(final String poolBlock) {
        return DefaultPostgresConfig.of(ConfigFactory.parseString("postgresql.pool {\n" + poolBlock + "\n}"))
                .getPoolConfig();
    }

    @Test
    public void poolerModeFromConfigValueIsCaseInsensitive() {
        assertThat(PoolerMode.fromConfigValue("TRANSACTION")).contains(PoolerMode.TRANSACTION);
        assertThat(PoolerMode.fromConfigValue(" Session ")).contains(PoolerMode.SESSION);
        assertThat(PoolerMode.fromConfigValue("nonsense")).isEmpty();
        assertThat(PoolerMode.fromConfigValue(null)).isEmpty();
    }

    @Test
    public void toStringDoesNotLeakSecrets() {
        final DefaultPostgresConfig underTest = DefaultPostgresConfig.of(dittoConfig);
        final String rendered = underTest.toString();
        softly.assertThat(rendered).as("no runtime password").doesNotContain("runtime_secret");
        softly.assertThat(rendered).as("no ddl password").doesNotContain("ddl_secret");
        softly.assertThat(rendered).as("no key password").doesNotContain("key_secret");
    }

}
