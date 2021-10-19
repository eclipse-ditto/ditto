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
package org.eclipse.ditto.concierge.service.starter.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.ditto.concierge.api.actors.ConciergeForwarderActor;
import org.eclipse.ditto.concierge.service.actors.ShardRegions;
import org.eclipse.ditto.concierge.service.common.ConciergeConfig;
import org.eclipse.ditto.concierge.service.common.DittoConciergeConfig;
import org.eclipse.ditto.concierge.service.starter.actors.CachedNamespaceInvalidator;
import org.eclipse.ditto.concierge.service.starter.actors.DispatcherActor;
import org.eclipse.ditto.concierge.service.starter.actors.PolicyCacheUpdateActor;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorContext;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

public final class DefaultEnforcerActorFactoryTest {

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = ActorSystem.create("AkkaTestSystem", ConfigFactory.load("test"));
    }

    @AfterClass
    public static void shutdown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void createFactory() {
        new TestKit(actorSystem) {{
            final ConciergeConfig conciergeConfig = DittoConciergeConfig
                        .of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
            final ShardRegions shardRegions = ShardRegions.of(actorSystem, conciergeConfig.getClusterConfig());

            final TestProbe pubSubProbe = TestProbe.apply(actorSystem);
            final DefaultEnforcerActorFactory enforcerActoryFactory = new DefaultEnforcerActorFactory();

            final ActorContext mockActorContext = mock(ActorContext.class);
            when(mockActorContext.system()).thenReturn(actorSystem);

            when(mockActorContext.actorOf(any(Props.class), anyString())).thenAnswer(i -> {
                return actorSystem.actorOf(i.getArgument(0), i.getArgument(1));
            });

            enforcerActoryFactory.startEnforcerActor(
                        mockActorContext, conciergeConfig,
                        pubSubProbe.ref(), shardRegions);

            verify(mockActorContext).actorOf(any(Props.class), eq(DispatcherActor.ACTOR_NAME));
            verify(mockActorContext).actorOf(any(Props.class), eq(PolicyCacheUpdateActor.ACTOR_NAME));
            verify(mockActorContext).actorOf(any(Props.class), eq(ConciergeForwarderActor.ACTOR_NAME));
            verify(mockActorContext).actorOf(any(Props.class), eq(CachedNamespaceInvalidator.ACTOR_NAME));
        }};
    }

}
