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
package org.eclipse.ditto.internal.utils.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Map;

import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link ShardRegionProxyActorFactoryTest}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ShardRegionProxyActorFactoryTest {

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE = ActorSystemResource.newInstance(
            ConfigFactory.parseMap(Map.ofEntries(
                    Map.entry(MappingStrategies.CONFIG_KEY_DITTO_MAPPING_STRATEGY_IMPLEMENTATION,
                            TestMappingStrategies.class.getName()),
                    Map.entry("akka.actor.provider", "cluster")
            ))
    );

    private static final int NUMBER_OF_SHARDS = 3;

    @Mock
    private ClusterConfig clusterConfig;

    @Before
    public void before() {
        Mockito.when(clusterConfig.getNumberOfShards()).thenReturn(NUMBER_OF_SHARDS);
    }

    @Test
    public void newInstanceWithNullActorSystemThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ShardRegionProxyActorFactory.newInstance(null, clusterConfig))
                .withMessage("The actorSystem must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullClusterConfigThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ShardRegionProxyActorFactory.newInstance(ACTOR_SYSTEM_RESOURCE.getActorSystem(),
                        null))
                .withMessage("The clusterConfig must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceReturnsNotNull() {
        final var underTest =
                ShardRegionProxyActorFactory.newInstance(ACTOR_SYSTEM_RESOURCE.getActorSystem(), clusterConfig);

        assertThat(underTest).isNotNull();
    }

    @Test
    public void getShardRegionProxyActorWithNullClusterRoleThrowsException() {
        final var underTest =
                ShardRegionProxyActorFactory.newInstance(ACTOR_SYSTEM_RESOURCE.getActorSystem(), clusterConfig);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.getShardRegionProxyActor(null, "myShardRegion"))
                .withMessage("The clusterRole must not be null!")
                .withNoCause();
    }

    @Test
    public void getShardRegionProxyActorWithEmptyClusterRoleThrowsException() {
        final var underTest =
                ShardRegionProxyActorFactory.newInstance(ACTOR_SYSTEM_RESOURCE.getActorSystem(), clusterConfig);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.getShardRegionProxyActor("", "myShardRegion"))
                .withMessage("The argument 'clusterRole' must not be empty!")
                .withNoCause();
    }

    @Test
    public void getShardRegionProxyActorWithNullShardRegionNameThrowsException() {
        final var underTest =
                ShardRegionProxyActorFactory.newInstance(ACTOR_SYSTEM_RESOURCE.getActorSystem(), clusterConfig);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.getShardRegionProxyActor("myClusterRole", null))
                .withMessage("The shardRegionName must not be null!")
                .withNoCause();
    }

    @Test
    public void getShardRegionProxyActorWithEmptyShardRegionNameThrowsException() {
        final var underTest =
                ShardRegionProxyActorFactory.newInstance(ACTOR_SYSTEM_RESOURCE.getActorSystem(), clusterConfig);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> underTest.getShardRegionProxyActor("myClusterRole", ""))
                .withMessage("The argument 'shardRegionName' must not be empty!")
                .withNoCause();
    }

    @Test
    public void getShardRegionProxyActorReturnsNotNull() {
        final var underTest =
                ShardRegionProxyActorFactory.newInstance(ACTOR_SYSTEM_RESOURCE.getActorSystem(), clusterConfig);

        final var shardRegionProxyActor = underTest.getShardRegionProxyActor("myClusterRole", "myShardRegionName");

        assertThat(shardRegionProxyActor).isNotNull();
    }

    public static final class TestMappingStrategies extends MappingStrategies {

        public TestMappingStrategies() {
            super(Map.of());
        }

    }

}