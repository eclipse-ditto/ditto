/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.things.persistence.actors;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AccessControlListModelFactory;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.utils.distributedcache.actors.CacheFacadeActor;
import org.eclipse.ditto.services.utils.distributedcache.actors.CacheRole;
import org.junit.After;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.testkit.javadsl.TestKit;

/**
 * Base test class for testing persistence actors of the things persistence.
 */
public abstract class PersistenceActorTestBase {

    protected static final String THING_ID = "org.eclipse.ditto:thingId";
    protected static final String AUTH_SUBJECT = "allowedId";
    protected static final AuthorizationSubject AUTHORIZED_SUBJECT =
            AuthorizationModelFactory.newAuthSubject(AUTH_SUBJECT);

    protected static final Attributes THING_ATTRIBUTES = ThingsModelFactory.emptyAttributes();

    private static final Features THING_FEATURES = ThingsModelFactory.emptyFeatures();
    private static final ThingLifecycle THING_LIFECYCLE = ThingLifecycle.ACTIVE;
    private static final long THING_REVISION = 1;

    protected static final Predicate<JsonField> IS_MODIFIED =
            field -> field.getDefinition().map(definition -> definition == Thing.JsonFields.MODIFIED).orElse(false);

    protected ActorSystem actorSystem = null;
    protected ActorRef pubSubMediator = null;
    protected DittoHeaders dittoHeadersV1;
    protected DittoHeaders dittoHeadersV2;
    protected ActorRef thingCacheFacade;

    protected static DittoHeaders createDittoHeadersMock(final JsonSchemaVersion schemaVersion,
            final String... authSubjects) {
        final DittoHeadersBuilder builder = DittoHeaders.newBuilder();
        builder.authorizationSubjects(Arrays.asList(authSubjects));
        builder.schemaVersion(schemaVersion);
        return builder.build();
    }

    protected static Thing createThingV2WithRandomId() {
        final String thingId = THING_ID + UUID.randomUUID();
        return createThingV2WithId(thingId);
    }

    protected static Thing createThingV2WithId(final String thingId) {
        return ThingsModelFactory.newThingBuilder()
                .setLifecycle(THING_LIFECYCLE)
                .setAttributes(THING_ATTRIBUTES)
                .setFeatures(THING_FEATURES)
                .setRevision(THING_REVISION)
                .setId(thingId)
                .setPolicyId(thingId)
                .build();
    }

    protected static Thing createThingV1WithRandomId() {
        final Random rnd = new Random();
        final String thingId = THING_ID + rnd.nextInt();
        return createThingV1WithId(thingId);
    }

    protected static Thing createThingV1WithId(final String thingId) {
        return ThingsModelFactory.newThingBuilder()
                .setLifecycle(THING_LIFECYCLE)
                .setAttributes(THING_ATTRIBUTES)
                .setFeatures(THING_FEATURES)
                .setRevision(THING_REVISION)
                .setId("test.ns:" + thingId)
                .setPermissions(AUTHORIZED_SUBJECT, AccessControlListModelFactory.allPermissions()).build();
    }

    protected void setup(final Config customConfig) {
        requireNonNull(customConfig, "Consider to use ConfigFactory.empty()");
        final Config config = customConfig.withFallback(ConfigFactory.load("test"));

        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        pubSubMediator = DistributedPubSub.get(actorSystem).mediator();

        thingCacheFacade = actorSystem.actorOf(CacheFacadeActor.props(CacheRole.THING,
                actorSystem.settings().config()), CacheFacadeActor.actorNameFor(CacheRole.THING));

        dittoHeadersV1 = createDittoHeadersMock(JsonSchemaVersion.V_1, AUTH_SUBJECT);
        dittoHeadersV2 = createDittoHeadersMock(JsonSchemaVersion.V_2, AUTH_SUBJECT);
    }

    /** */
    @After
    public void tearDownBase() {
        TestKit.shutdownActorSystem(actorSystem);
        actorSystem = null;
    }

    protected ActorRef createPersistenceActorFor(final String thingId) {
        final Props props = ThingPersistenceActor.props(thingId, pubSubMediator, thingCacheFacade);
        return actorSystem.actorOf(props);
    }

    protected ActorRef createSupervisorActorFor(final String thingId) {
        final java.time.Duration minBackoff = java.time.Duration.ofSeconds(7);
        final java.time.Duration maxBackoff = java.time.Duration.ofSeconds(60);
        final double randomFactor = 0.2;

        final ThingsActorsCreator actorsCreator = new TestThingsActorsCreator(minBackoff, maxBackoff, randomFactor);
        final Props props = actorsCreator.createSupervisorActor(pubSubMediator, thingCacheFacade);

        return actorSystem.actorOf(props, thingId);
    }

    protected static void waitSecs(final long secs) {
        waitMillis(secs * 1000);
    }

    protected static void waitMillis(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static final class TestThingsActorsCreator implements ThingsActorsCreator {

        private final java.time.Duration minBackoff;
        private final java.time.Duration maxBackoff;
        private final double randomFactor;

        public TestThingsActorsCreator(final Duration minBackoff, final Duration maxBackoff,
                final double randomFactor) {
            this.minBackoff = minBackoff;
            this.maxBackoff = maxBackoff;
            this.randomFactor = randomFactor;
        }

        @Override
        public Props createRootActor() {
            return null;
        }

        @Override
        public Props createSupervisorActor(final ActorRef pubSubMediator, final ActorRef thingCacheFacade) {
            return ThingSupervisorActor.props(pubSubMediator, minBackoff, maxBackoff, randomFactor, thingCacheFacade,
                    this);
        }

        @Override
        public Props createPersistentActor(final String thingId, final ActorRef pubSubMediator,
                final ActorRef thingCacheFacade) {
            return ThingPersistenceActor.props(thingId, pubSubMediator, thingCacheFacade);
        }
    }
}
