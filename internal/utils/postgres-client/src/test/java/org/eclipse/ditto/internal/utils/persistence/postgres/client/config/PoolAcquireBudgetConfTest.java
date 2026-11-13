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

import java.time.Duration;
import java.util.Map;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;

/**
 * Pins the pool-overload settings of the <em>production</em> {@code ditto-postgres-client.conf}: the pool's acquire
 * budget must stay below the caller's deadline, and each of the four settings must be overridable via its
 * environment variable.
 * <p>
 * Why: a write that waits for a connection longer than its caller is willing to wait still commits after the caller
 * was told it failed (for {@code CreateThing}: an orphan Policy). See the budget-vs-deadline comment in
 * {@code ditto-postgres-client.conf} and {@code ConnectionPoolFactory}.
 * </p>
 * <p>
 * The conf is parsed with the system environment switched OFF, so a developer's or CI's {@code POSTGRES_*} variables
 * cannot change what is asserted; the env-override test supplies its variables explicitly.
 * </p>
 */
public final class PoolAcquireBudgetConfTest {

    /**
     * The shortest caller deadline that waits on a things/policies journal write: the entity supervisor's
     * {@code local-ask.timeout} of 5 s.
     * <ul>
     *     <li>policies: {@code policies.conf} declares no {@code local-ask} block, so the code default applies —
     *     {@code LocalAskTimeoutConfig.LocalAskTimeoutConfigValue.ASK_TIMEOUT}
     *     ({@code ASK_TIMEOUT("timeout", Duration.ofSeconds(5L))}), at {@code base/service/src/main/java/org/eclipse/
     *     ditto/base/service/config/supervision/LocalAskTimeoutConfig.java:62};</li>
     *     <li>things: {@code ditto.things.thing.supervisor.local-ask.timeout = 5s} (env
     *     {@code THINGS_SUPERVISOR_LOCAL_ASK_TIMEOUT}), at
     *     {@code things/service/src/main/resources/things.conf:256}.</li>
     * </ul>
     * Applied by {@code AbstractPersistenceSupervisor.determineAskTimeoutForPersistenceActorForwarding()} once the
     * persistence actor has recovered; its expiry surfaces as {@code EnforcementTimeoutException}
     * ({@code AbstractPersistenceSupervisor.getEnforcementExceptionAsRuntimeException()}).
     * <p>
     * Hard-coded because postgres-client must not depend on base-service. If either source changes, update this
     * constant and re-check the budget below.
     * </p>
     */
    private static final Duration CALLER_DEADLINE = Duration.ofSeconds(5);

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    /**
     * Resolves the production conf with {@code env} standing in for the environment: HOCON looks an optional
     * substitution such as {@code ${?POSTGRES_POOL_MAX_SIZE}} up at the ROOT of the config first, so the variables are
     * layered in as root-level keys and the real system environment is switched off.
     */
    private static PostgresConfig productionConfigWithEnv(final Map<String, String> env) {
        final Config resolved = ConfigFactory.parseMap(env)
                .withFallback(ConfigFactory.parseResources("ditto-postgres-client.conf"))
                .resolve(ConfigResolveOptions.defaults().setUseSystemEnvironment(false));
        return DefaultPostgresConfig.of(resolved.getConfig("ditto"));
    }

    private static PostgresConfig productionConfig() {
        return productionConfigWithEnv(Map.of());
    }

    @Test
    public void productionConfCarriesTheDecidedOverloadValues() {
        final PostgresConfig config = productionConfig();
        final PostgresConfig.PoolConfig pool = config.getPoolConfig();

        softly.assertThat(pool.getMaxAcquireTime()).as("pool.max-acquire-time").isEqualTo(Duration.ofSeconds(4));
        softly.assertThat(config.getConnectTimeout()).as("connect-timeout").isEqualTo(Duration.ofSeconds(2));
        softly.assertThat(pool.getAcquireRetry()).as("pool.acquire-retry").isZero();
        softly.assertThat(pool.getPendingAcquireMaxSize())
                .as("pool.pending-acquire-max-size is unset in the conf and derived as 2 x max-size")
                .isEqualTo(2 * pool.getMaxSize());
    }

    @Test
    public void effectiveAcquireBudgetStaysBelowTheCallerDeadline() {
        final PostgresConfig.PoolConfig pool = productionConfig().getPoolConfig();

        // r2dbc-pool re-subscribes the whole acquire, with a fresh max-acquire-time, once per acquire-retry.
        final Duration effectiveAcquireBudget = pool.getMaxAcquireTime().multipliedBy(1L + pool.getAcquireRetry());

        assertThat(effectiveAcquireBudget)
                .as("effective acquire budget (4s) must stay below the caller deadline (5s): a borrow that waits "
                        + "longer only lets the write commit after the caller already gave up")
                .isEqualTo(Duration.ofSeconds(4))
                .isLessThan(CALLER_DEADLINE);
    }

    @Test
    public void connectTimeoutStaysBelowMaxAcquireTime() {
        final PostgresConfig config = productionConfig();

        assertThat(config.getConnectTimeout())
                .as("connect-timeout < max-acquire-time, so a failed connect surfaces as itself and not as a "
                        + "generic acquire timeout")
                .isLessThan(config.getPoolConfig().getMaxAcquireTime());
    }

    @Test
    public void everyOverloadSettingIsOverridableViaItsEnvironmentVariable() {
        final PostgresConfig config = productionConfigWithEnv(Map.of(
                "POSTGRES_CONNECT_TIMEOUT", "1500ms",
                "POSTGRES_POOL_MAX_ACQUIRE_TIME", "3s",
                "POSTGRES_POOL_ACQUIRE_RETRY", "1",
                "POSTGRES_POOL_PENDING_ACQUIRE_MAX_SIZE", "-1"));
        final PostgresConfig.PoolConfig pool = config.getPoolConfig();

        softly.assertThat(config.getConnectTimeout()).as("POSTGRES_CONNECT_TIMEOUT")
                .isEqualTo(Duration.ofMillis(1500));
        softly.assertThat(pool.getMaxAcquireTime()).as("POSTGRES_POOL_MAX_ACQUIRE_TIME")
                .isEqualTo(Duration.ofSeconds(3));
        softly.assertThat(pool.getAcquireRetry()).as("POSTGRES_POOL_ACQUIRE_RETRY").isEqualTo(1);
        softly.assertThat(pool.getPendingAcquireMaxSize()).as("POSTGRES_POOL_PENDING_ACQUIRE_MAX_SIZE=-1 (unbounded)")
                .isEqualTo(PostgresConfig.PoolConfig.UNBOUNDED_PENDING_ACQUIRE);
    }

    @Test
    public void derivedPendingBoundFollowsAMaxSizeOverride() {
        final PostgresConfig.PoolConfig pool =
                productionConfigWithEnv(Map.of("POSTGRES_POOL_MAX_SIZE", "10")).getPoolConfig();

        assertThat(pool.getPendingAcquireMaxSize())
                .as("unset pending-acquire-max-size tracks the overridden max-size")
                .isEqualTo(20);
    }

}
