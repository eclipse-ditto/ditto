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
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AccessControlListModelFactory;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.junit.After;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Base test class for testing persistence actors of the things persistence.
 */
public abstract class PersistenceActorTestBase {

    protected static final String THING_ID = "org.eclipse.ditto:thingId";
    protected static final String POLICY_ID = "org.eclipse.ditto:policyId";
    protected static final String AUTH_SUBJECT = "allowedId";
    protected static final AuthorizationSubject AUTHORIZED_SUBJECT =
            AuthorizationModelFactory.newAuthSubject(AUTH_SUBJECT);

    protected static final Attributes THING_ATTRIBUTES = ThingsModelFactory.newAttributesBuilder()
            .set("attrKey", "attrVal")
            .build();

    protected static final Predicate<JsonField> IS_MODIFIED = field -> field.getDefinition()
            .map(Thing.JsonFields.MODIFIED::equals)
            .orElse(false);

    protected static final JsonFieldSelector ALL_FIELDS_SELECTOR = JsonFactory.newFieldSelector(
            Thing.JsonFields.ATTRIBUTES, Thing.JsonFields.ACL,
            Thing.JsonFields.FEATURES, Thing.JsonFields.ID, Thing.JsonFields.MODIFIED, Thing.JsonFields.REVISION,
            Thing.JsonFields.POLICY_ID, Thing.JsonFields.LIFECYCLE);

    private static final FeatureDefinition FEATURE_DEFINITION = FeatureDefinition.fromIdentifier("ns:name:version");
    private static final FeatureProperties FEATURE_PROPERTIES =
            FeatureProperties.newBuilder().set("featureKey", "featureValue").build();
    private static final Feature THING_FEATURE =
            ThingsModelFactory.newFeature("featureId", FEATURE_DEFINITION, FEATURE_PROPERTIES);
    private static final Features THING_FEATURES = ThingsModelFactory.newFeaturesBuilder()
            .set(THING_FEATURE)
            .build();
    private static final ThingLifecycle THING_LIFECYCLE = ThingLifecycle.ACTIVE;
    private static final long THING_REVISION = 1;

    protected ActorSystem actorSystem = null;
    protected ActorRef pubSubMediator = null;
    protected DittoHeaders dittoHeadersV1;
    protected DittoHeaders dittoHeadersV2;

    protected static DittoHeaders createDittoHeadersMock(final JsonSchemaVersion schemaVersion,
            final String... authSubjects) {

        final DittoHeadersBuilder builder = DittoHeaders.newBuilder();
        builder.authorizationSubjects(Arrays.asList(authSubjects));
        builder.schemaVersion(schemaVersion);
        return builder.build();
    }

    protected static Thing createThingV2WithRandomId() {
        return createThingV2WithId(THING_ID + UUID.randomUUID());
    }

    protected static Thing createThingV2WithId(final String thingId) {
        return ThingsModelFactory.newThingBuilder()
                .setLifecycle(THING_LIFECYCLE)
                .setAttributes(THING_ATTRIBUTES)
                .setFeatures(THING_FEATURES)
                .setRevision(THING_REVISION)
                .setId(thingId)
                .setPolicyId(POLICY_ID)
                .build();
    }

    protected static Thing createThingV1WithRandomId() {
        return createThingV1WithId(THING_ID + new Random().nextInt());
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

    protected static void waitSecs(final long secs) {
        waitMillis(secs * 1000);
    }

    protected static void waitMillis(final long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void setup(final Config customConfig) {
        requireNonNull(customConfig, "Consider to use ConfigFactory.empty()");
        final Config config = customConfig.withFallback(ConfigFactory.load("test"));

        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        pubSubMediator = new TestProbe(actorSystem, "mock-pubSub-mediator").ref();

        dittoHeadersV1 = createDittoHeadersMock(JsonSchemaVersion.V_1, AUTH_SUBJECT);
        dittoHeadersV2 = createDittoHeadersMock(JsonSchemaVersion.V_2, AUTH_SUBJECT);
    }

    @After
    public void tearDownBase() {
        TestKit.shutdownActorSystem(actorSystem);
        actorSystem = null;
    }

    protected ActorRef createPersistenceActorFor(final String thingId) {
        return createPersistenceActorWithPubSubFor(thingId, pubSubMediator);
    }

    protected ActorRef createPersistenceActorWithPubSubFor(final String thingId, final ActorRef pubSubMediator) {
        return actorSystem.actorOf(getPropsOfThingPersistenceActor(thingId, pubSubMediator));
    }

    private Props getPropsOfThingPersistenceActor(final String thingId) {
        return getPropsOfThingPersistenceActor(thingId, pubSubMediator);
    }

    private Props getPropsOfThingPersistenceActor(final String thingId, final ActorRef pubSubMediator) {
        return ThingPersistenceActor.props(thingId, pubSubMediator);
    }

    protected ActorRef createSupervisorActorFor(final String thingId) {
        final Duration minBackOff = Duration.ofSeconds(7);
        final Duration maxBackOff = Duration.ofSeconds(60);
        final double randomFactor = 0.2;

        final Props props = ThingSupervisorActor.props(minBackOff, maxBackOff, randomFactor,
                this::getPropsOfThingPersistenceActor);

        return actorSystem.actorOf(props, thingId);
    }

}
