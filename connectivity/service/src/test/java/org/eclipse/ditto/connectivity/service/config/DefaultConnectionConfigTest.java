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
package org.eclipse.ditto.connectivity.service.config;

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.service.config.supervision.DefaultSupervisorConfig;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.CleanupConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultConnectionConfig}.
 */
public final class DefaultConnectionConfigTest {

    private static Config connectionTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        connectionTestConf = ConfigFactory.load("connection-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultConnectionConfig.class,
                areImmutable(),
                assumingFields("allowedHostnames", "blockedHostnames", "blockedSubnets")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements(),
                provided(DefaultSupervisorConfig.class,
                        SnapshotConfig.class,
                        CleanupConfig.class,
                        MqttConfig.class,
                        KafkaConfig.class,
                        Amqp10Config.class,
                        HttpPushConfig.class
                ).areAlsoImmutable()
        );
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultConnectionConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final ConnectionConfig underTest = DefaultConnectionConfig.of(connectionTestConf);

        softly.assertThat(underTest.getClientActorAskTimeout())
                .as(ConnectionConfig.ConnectionConfigValue.CLIENT_ACTOR_ASK_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(10L));

        softly.assertThat(underTest.getClientActorRestartsBeforeEscalation())
                .as(ConnectionConfig.ConnectionConfigValue.CLIENT_ACTOR_RESTARTS_BEFORE_ESCALATION.getConfigPath())
                .isEqualTo(7);

        softly.assertThat(underTest.getAllowedHostnames())
                .as(ConnectionConfig.ConnectionConfigValue.ALLOWED_HOSTNAMES.getConfigPath())
                .containsExactly("eclipse.org");

        softly.assertThat(underTest.getBlockedHostnames())
                .as(ConnectionConfig.ConnectionConfigValue.BLOCKED_HOSTNAMES.getConfigPath())
                .containsExactly("localhost");

        softly.assertThat(underTest.getBlockedSubnets())
                .as(ConnectionConfig.ConnectionConfigValue.BLOCKED_SUBNETS.getConfigPath())
                .containsExactly("11.1.0.0/16");

        softly.assertThat(underTest.getBlockedHostRegex())
                .as(ConnectionConfig.ConnectionConfigValue.BLOCKED_HOST_REGEX.getConfigPath())
                .isEqualTo("^.*\\.svc.cluster.local$");

        softly.assertThat(underTest.getSupervisorConfig())
                .as("supervisorConfig")
                .satisfies(supervisorConfig -> softly.assertThat(supervisorConfig.getExponentialBackOffConfig())
                        .as("exponentialBackOffConfig")
                        .satisfies(exponentialBackOffConfig -> {
                            softly.assertThat(exponentialBackOffConfig.getMin())
                                    .as(ExponentialBackOffConfig.ExponentialBackOffConfigValue.MIN.getConfigPath())
                                    .isEqualTo(Duration.ofSeconds(2L));
                            softly.assertThat(exponentialBackOffConfig.getMax())
                                    .as(ExponentialBackOffConfig.ExponentialBackOffConfigValue.MAX.getConfigPath())
                                    .isEqualTo(Duration.ofSeconds(50L));
                            softly.assertThat(exponentialBackOffConfig.getRandomFactor())
                                    .as(ExponentialBackOffConfig.ExponentialBackOffConfigValue.RANDOM_FACTOR.getConfigPath())
                                    .isEqualTo(0.1D);
                        }));

        softly.assertThat(underTest.getSnapshotConfig())
                .as("snapshotConfig")
                .satisfies(snapshotConfig -> softly.assertThat(snapshotConfig.getThreshold())
                        .as(SnapshotConfig.SnapshotConfigValue.THRESHOLD.getConfigPath())
                        .isEqualTo(20));

        softly.assertThat(underTest.getHttpPushConfig())
                .as("httpPushConfig")
                .satisfies(httpPushConfig -> softly.assertThat(httpPushConfig.getMaxQueueSize())
                        .as(HttpPushConfig.ConfigValue.MAX_QUEUE_SIZE.getConfigPath())
                        .isEqualTo(9));

        softly.assertThat(underTest.getAmqp091Config())
                .as("amqp091Config")
                .satisfies(amqp091Config -> softly.assertThat(amqp091Config.getPublisherPendingAckTTL())
                        .as(Amqp091Config.ConfigValue.PUBLISHER_PENDING_ACK_TTL.getConfigPath())
                        .isEqualTo(Duration.ofSeconds(31556736L)));

        softly.assertThat(underTest.getMaxNumberOfSources())
                .as("maxNumberOfSources")
                .satisfies(maxNumberOfSources -> softly.assertThat(maxNumberOfSources)
                        .as(ConnectionConfig.ConnectionConfigValue.MAX_SOURCE_NUMBER.getConfigPath())
                        .isEqualTo(3));

        softly.assertThat(underTest.getMaxNumberOfTargets())
                .as("maxNumberOfTargets")
                .satisfies(maxNumberOfTargets -> softly.assertThat(maxNumberOfTargets)
                        .as(ConnectionConfig.ConnectionConfigValue.MAX_TARGET_NUMBER.getConfigPath())
                        .isEqualTo(3));

        softly.assertThat(underTest.getAckLabelDeclareInterval())
                .as(ConnectionConfig.ConnectionConfigValue.ACK_LABEL_DECLARE_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofSeconds(99L));

        softly.assertThat(underTest.areAllClientActorsOnOneNode())
                .as(ConnectionConfig.ConnectionConfigValue.ALL_CLIENT_ACTORS_ON_ONE_NODE.getConfigPath())
                .isEqualTo(true);

        softly.assertThat(underTest.getShutdownTimeout())
                .as(ConnectionConfig.ConnectionConfigValue.SHUTDOWN_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofMinutes(7));
    }

}
