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
package org.eclipse.ditto.base.service.actors;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.internal.utils.config.raw.RawConfigSupplier;
import org.junit.After;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.Attributes;
import akka.testkit.javadsl.TestKit;

/**
 * Abstract class to test the service root actors.
 */
public abstract class AbstractDittoRootActorTest {

    private final List<ActorSystem> actorSystems = new ArrayList<>();

    /**
     * Get the name of the service with which to load config files.
     *
     * @return the service name.
     */
    protected abstract String serviceName();

    /**
     * Construct the Props object of the root actor under test.
     *
     * @param system the actor system.
     * @return The props object.
     */
    protected abstract Props getRootActorProps(final ActorSystem system);

    /**
     * Create the actor system. By default, create an actor system with the service default config and random ports
     * that forms its own cluster.
     *
     * @return the actor system.
     */
    protected ActorSystem createActorSystem() {
        final Map<String, Object> configMap = Map.of(
                // bind random ports
                "akka.management.http.port", 0,
                "akka.remote.artery.canonical.port", 0,
                "akka.cluster.seed-nodes", List.of(),
                "ditto.http.port", 0,
                "ditto.metrics.prometheus.port", 0,

                // turn off System.exit(0) in the post-shutdown hook to avoid JVM termination at the end of this test
                "akka.coordinated-shutdown.exit-jvm", "off"
        );
        final Config config = ConfigFactory.parseMap(overrideConfig())
                .withFallback(ConfigFactory.parseMap(configMap))
                .withFallback(RawConfigSupplier.of(serviceName()).get());
        final ActorSystem system = ActorSystem.create(getClass().getSimpleName(), config);
        final Cluster cluster = Cluster.get(system);
        cluster.join(cluster.selfAddress());
        return system;
    }

    /**
     * Check if the root actor restarted successfully. By default, check that the test kit receives no Terminated
     * message.
     *
     * @param underTest the root actor experiencing restarts.
     * @param failingChild the child actor throwing unknown exceptions.
     * @param testKit the test kit.
     */
    protected void assertRestartSuccess(final ActorRef underTest, final ActorRef failingChild, final TestKit testKit) {
        testKit.expectNoMessage();
    }

    /**
     * Whether to disable logging in tests.
     * Override in subclasses to debug.
     *
     * @return whether to disable logging.
     */
    protected boolean disableLogging() {
        return true;
    }

    /**
     * To be overridden in subclasses. Create root actor with a specific name if given.
     *
     * @return the root actor name if it is important, or an empty optional otherwise.
     */
    protected Optional<String> getRootActorName() {
        return Optional.empty();
    }

    /**
     * Service-specific config override.
     *
     * @return the config override.
     */
    protected Map<String, Object> overrideConfig() {
        return Map.of();
    }

    @After
    public void terminateCreatedActorSystems() {
        for (final ActorSystem system : actorSystems) {
            TestKit.shutdownActorSystem(system);
        }
        actorSystems.clear();
    }

    @Test
    public void testRootActorRestart() {
        final ActorSystem system = createAndRememberForCleanUp();

        if (disableLogging()) {
            system.eventStream().setLogLevel(Attributes.logLevelOff());
        }

        new TestKit(system) {{
            final ActorRef underTest = getRootActorName()
                    .map(name -> watch(system.actorOf(getRootActorProps(system), name)))
                    .orElseGet(() -> watch(system.actorOf(getRootActorProps(system))));

            final String failingChild = "failingChild";
            underTest.tell(new StartChildActor(
                    Props.create(ThrowingActor.class, () -> new ThrowingActor(getRef())),
                    failingChild
            ), getRef());

            final ActorRef throwingActor = expectMsgClass(Duration.ofSeconds(100L), ActorRef.class);
            throwingActor.tell(new UnknownException(), getRef());

            assertRestartSuccess(underTest, throwingActor, this);
        }};
    }

    private ActorSystem createAndRememberForCleanUp() {
        final ActorSystem system = createActorSystem();
        actorSystems.add(system);
        return system;
    }

    /**
     * An exception not handled in the supervising strategy of DittoRootActor.
     */
    private static final class UnknownException extends RuntimeException {}

    /**
     * An actor that throws each runtime exception it receives.
     */
    protected static final class ThrowingActor extends AbstractActor {

        public ThrowingActor() {}

        public ThrowingActor(final ActorRef toNotify) {
            toNotify.tell(getSelf(), getSelf());
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(RuntimeException.class, e -> {
                        throw e;
                    })
                    .build();
        }
    }
}
