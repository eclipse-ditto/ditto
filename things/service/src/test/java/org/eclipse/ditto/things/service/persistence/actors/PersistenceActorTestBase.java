/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.service.common.config.DefaultThingConfig;
import org.eclipse.ditto.things.service.common.config.ThingConfig;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.rules.TestWatcher;
import org.slf4j.Logger;

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

    protected static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "thingId");
    protected static final PolicyId POLICY_ID = PolicyId.of("org.eclipse.ditto:policyId");
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
            Thing.JsonFields.ATTRIBUTES, Thing.JsonFields.FEATURES, Thing.JsonFields.ID, Thing.JsonFields.MODIFIED,
            Thing.JsonFields.CREATED, Thing.JsonFields.REVISION, Thing.JsonFields.POLICY_ID,
            Thing.JsonFields.LIFECYCLE);

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

    protected static Config testConfig;
    protected static ThingConfig thingConfig;

    protected ActorSystem actorSystem = null;
    protected TestProbe pubSubTestProbe = null;
    protected ActorRef pubSubMediator = null;
    protected DittoHeaders dittoHeadersV2;

    @BeforeClass
    public static void initTestFixture() {
        testConfig = ConfigFactory.load("test");
        thingConfig = getThingConfig(testConfig);
    }

    protected static ThingConfig getThingConfig(final Config testConfig) {
        return DefaultThingConfig.of(testConfig.getConfig("ditto.things"));
    }

    protected static DittoHeaders createDittoHeadersMock(final JsonSchemaVersion schemaVersion,
            final String... authSubjectIds) {

        final List<AuthorizationSubject> authSubjects = Arrays.stream(authSubjectIds)
                .map(AuthorizationSubject::newInstance)
                .collect(Collectors.toList());

        return DittoHeaders.newBuilder()
                .authorizationContext(
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, authSubjects))
                .schemaVersion(schemaVersion)
                .build();
    }

    protected static Thing createThingV2WithRandomId() {
        return createThingV2WithId(ThingId.of(THING_ID.getNamespace(), THING_ID.getName() + UUID.randomUUID()));
    }

    protected static Thing createThingV2WithId(final ThingId thingId) {
        return ThingsModelFactory.newThingBuilder()
                .setLifecycle(THING_LIFECYCLE)
                .setAttributes(THING_ATTRIBUTES)
                .setFeatures(THING_FEATURES)
                .setRevision(THING_REVISION)
                .setId(thingId)
                .setPolicyId(POLICY_ID)
                .build();
    }

    protected void setup(final Config customConfig) {
        requireNonNull(customConfig, "Consider to use ConfigFactory.empty()");
        final Config config = customConfig.withFallback(ConfigFactory.load("test"));

        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        pubSubTestProbe = TestProbe.apply("mock-pubSub-mediator", actorSystem);
        pubSubMediator = pubSubTestProbe.ref();

        dittoHeadersV2 = createDittoHeadersMock(JsonSchemaVersion.V_2, "test:" + AUTH_SUBJECT);
    }

    @After
    public void tearDownBase() {
        TestKit.shutdownActorSystem(actorSystem);
        actorSystem = null;
    }

    protected ActorRef createPersistenceActorFor(final ThingId thingId) {
        return createPersistenceActorWithPubSubFor(thingId);
    }

    protected ActorRef createPersistenceActorWithPubSubFor(final ThingId thingId) {

        return actorSystem.actorOf(getPropsOfThingPersistenceActor(thingId, getDistributedPub()));
    }

    private Props getPropsOfThingPersistenceActor(final ThingId thingId, final DistributedPub<ThingEvent<?>> pub) {

        return ThingPersistenceActor.props(thingId, pub, pubSubMediator);
    }

    protected ActorRef createSupervisorActorFor(final ThingId thingId) {
        final Props props =
                ThingSupervisorActor.props(pubSubMediator, getDistributedPub(), this::getPropsOfThingPersistenceActor);

        return actorSystem.actorOf(props, thingId.toString());
    }

    /**
     * This TestWatcher logs the name of each performed test method.
     */
    protected static final class TestedMethodLoggingWatcher extends TestWatcher {

        private final Logger logger;

        public TestedMethodLoggingWatcher(final Logger logger) {
            this.logger = logger;
        }

        @Override
        protected void starting(final org.junit.runner.Description description) {
            logger.info("Testing: {}#{}()", description.getTestClass().getSimpleName(), description.getMethodName());
        }

    }

    /**
     * Disable logging for 1 test to hide stacktrace or other logs on level ERROR. Comment out to debug the test.
     */
    protected void disableLogging() {
        actorSystem.eventStream().setLogLevel(akka.stream.Attributes.logLevelOff());
    }

    protected DistributedPub<ThingEvent<?>> getDistributedPub() {
        return new TestPub();
    }

    @AllParametersAndReturnValuesAreNonnullByDefault
    private final class TestPub implements DistributedPub<ThingEvent<?>> {

        private TestPub() {}

        @Override
        public ActorRef getPublisher() {
            return pubSubMediator;
        }

        @Override
        public Object wrapForPublication(final ThingEvent<?> message) {
            return message;
        }

        @Override
        public <S extends ThingEvent<?>> Object wrapForPublicationWithAcks(final S message,
                final AckExtractor<S> ackExtractor) {
            return wrapForPublication(message);
        }
    }
}
