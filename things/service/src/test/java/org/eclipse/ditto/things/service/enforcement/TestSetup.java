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
package org.eclipse.ditto.things.service.enforcement;

import static org.eclipse.ditto.base.model.json.JsonSchemaVersion.V_2;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.LiveSignalPub;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.enforcement.CreationRestrictionEnforcer;
import org.eclipse.ditto.policies.enforcement.EnforcementProvider;
import org.eclipse.ditto.policies.enforcement.EnforcerActor;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.policies.enforcement.PreEnforcer;
import org.eclipse.ditto.policies.enforcement.config.CachesConfig;
import org.eclipse.ditto.policies.enforcement.config.DefaultCachesConfig;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

public final class TestSetup {

    public static final String THING_SUDO = "thing-sudo";
    public static final String POLICY_SUDO = "policy-sudo";
    public static final String FEATURE_ID = "x";
    public static final String FEATURE_PROPERTY_1 = "key1";
    public static final String FEATURE_PROPERTY_2 = "key2";

    public static final ThingId THING_ID = ThingId.of("thing", "id");
    public static final PolicyId POLICY_ID = PolicyId.of("policy", "id");
    public static final Feature FEATURE = Feature.newBuilder()
            .properties(FeatureProperties.newBuilder()
                    .set(FEATURE_PROPERTY_1, 1)
                    .set(FEATURE_PROPERTY_2, "some string")
                    .build())
            .withId(FEATURE_ID)
            .build();
    public static final Thing THING = Thing.newBuilder()
            .setId(THING_ID)
            .setPolicyId(POLICY_ID)
            .setFeature(FEATURE)
            .build();

    public static final String SUBJECT_ID = "subject";
    public static final AuthorizationSubject SUBJECT = AuthorizationSubject.newInstance("dummy:" + SUBJECT_ID);

    private static final Config RAW_CONFIG = ConfigFactory.load("test");
    private static final CachesConfig CACHES_CONFIG;
    private static final EnforcementConfig ENFORCEMENT_CONFIG;

    static {
        final var dittoScopedConfig = DefaultScopedConfig.dittoScoped(RAW_CONFIG);

        CACHES_CONFIG = DefaultCachesConfig.of(dittoScopedConfig);
        ENFORCEMENT_CONFIG = DefaultEnforcementConfig.of(dittoScopedConfig);
    }

    public static ActorRef newEnforcerActor(final ActorSystem system, final ActorRef testActorRef,
            final ActorRef mockEntitiesActor) {

        return new EnforcerActorBuilder(system, testActorRef, mockEntitiesActor, mockEntitiesActor).build();
    }

    public static ActorRef newEnforcerActor(final ActorSystem system,
            final ActorRef testActorRef,
            final ActorRef thingsShardRegion,
            final ActorRef policiesShardRegion,
            final ActorRef pubSubMediatorRef,
            @Nullable final PreEnforcer preEnforcer,
            @Nullable final CreationRestrictionEnforcer creationRestrictionEnforcer) {
        return new EnforcerActorBuilder(system, testActorRef, thingsShardRegion, policiesShardRegion, pubSubMediatorRef)
                .setPreEnforcer(preEnforcer).setCreationRestrictionEnforcer(creationRestrictionEnforcer).build();
    }

    static class EnforcerActorBuilder {

        private final ActorSystem system;
        private final ActorRef testActorRef;
        private final ActorRef thingsShardRegion;
        private final ActorRef policiesShardRegion;
        private final ActorRef puSubMediatorRef;
        @Nullable private ActorRef commandForwarder;
        @Nullable private PreEnforcer preEnforcer;
        @Nullable private CreationRestrictionEnforcer creationRestrictionEnforcer;

        EnforcerActorBuilder(final ActorSystem system, final ActorRef testActorRef, final ActorRef thingsShardRegion,
                final ActorRef policiesShardRegion) {
            this.system = system;
            this.testActorRef = testActorRef;
            this.thingsShardRegion = thingsShardRegion;
            this.policiesShardRegion = policiesShardRegion;
            this.puSubMediatorRef = testActorRef;
        }

        EnforcerActorBuilder(final ActorSystem system, final ActorRef testActorRef, final ActorRef thingsShardRegion,
                final ActorRef policiesShardRegion, final ActorRef puSubMediatorRef) {
            this.system = system;
            this.testActorRef = testActorRef;
            this.thingsShardRegion = thingsShardRegion;
            this.policiesShardRegion = policiesShardRegion;
            this.puSubMediatorRef = puSubMediatorRef;
        }

        public EnforcerActorBuilder setPreEnforcer(@Nullable final PreEnforcer preEnforcer) {
            this.preEnforcer = preEnforcer;
            return this;
        }

        public EnforcerActorBuilder setCommandForwarder(final ActorRef commandForwarder) {
            this.commandForwarder = commandForwarder;
            return this;
        }

        public EnforcerActorBuilder setCreationRestrictionEnforcer(@Nullable final CreationRestrictionEnforcer creationRestrictionEnforcer) {
            this.creationRestrictionEnforcer = creationRestrictionEnforcer;
            return this;
        }

        public ActorRef build() {

            var commandForwarder = Optional.ofNullable(this.commandForwarder)
                    .orElseGet(() -> new TestProbe(system, createUniqueName()).ref());

            final AskWithRetryConfig askWithRetryConfig = CACHES_CONFIG.getAskWithRetryConfig();

            final PolicyEnforcerCacheLoader policyEnforcerCacheLoader =
                    new PolicyEnforcerCacheLoader(askWithRetryConfig, system.getScheduler(), policiesShardRegion);

            final Set<EnforcementProvider<?>> enforcementProviders = new HashSet<>();
            final LiveSignalPub liveSignalPub = new DummyLiveSignalPub(puSubMediatorRef);
//            enforcementProviders.add(new ThingCommandEnforcement.Provider(system, thingsShardRegion,
//                    policiesShardRegion, thingIdCache, projectedEnforcerCache, preEnforcer,
//                    creationRestrictionEnforcer, liveSignalPub, ENFORCEMENT_CONFIG));
//            enforcementProviders.add(new PolicyCommandEnforcement.Provider(policiesShardRegion, policyEnforcerCache,
//                    creationRestrictionEnforcer));
//            enforcementProviders.add(new LiveSignalEnforcement.Provider(thingIdCache,
//                    projectedEnforcerCache,
//                    system,
//                    liveSignalPub,
//                    ENFORCEMENT_CONFIG));
            final Props props = EnforcerActor.props(testActorRef, enforcementProviders, commandForwarder, ENFORCEMENT_CONFIG, preEnforcer,
                    null, null);

            return system.actorOf(props, EnforcerActor.ACTOR_NAME);
        }

    }

    static String createUniqueName() {
        return "commandForwarder-" + UUID.randomUUID();
    }

    public static ThingBuilder.FromScratch newThing() {
        return ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(1L);
    }

    public static JsonObject newThingWithPolicyId(final PolicyId policyId) {
        return newThing()
                .setPolicyId(policyId)
                .build()
                .toJson(V_2, FieldType.all());
    }

    /**
     * Similar to {@link TestKit#expectMsgClass(Class)} but ignores other messages occurring while waiting for a
     * message of the passed {@code clazz}.
     *
     * @param testKit the TestKit to fish for messages in
     * @param clazz the type of the message to wait for
     * @param <T> the type of the waited for message
     * @return the message
     */
    public static <T> T fishForMsgClass(final TestKit testKit, final Class<T> clazz) {
        return clazz.cast(
                testKit.fishForMessage(FiniteDuration.apply(5, TimeUnit.SECONDS), clazz.getName(), clazz::isInstance));
    }

    static final class DummyLiveSignalPub implements LiveSignalPub {

        private final ActorRef pubSubMediator;

        DummyLiveSignalPub(final ActorRef pubSubMediator) {
            this.pubSubMediator = pubSubMediator;
        }

        @Override
        public DistributedPub<ThingCommand<?>> command() {
            return new DistributedPub<>() {
                @Override
                public ActorRef getPublisher() {
                    return pubSubMediator;
                }

                @Override
                public Object wrapForPublication(final ThingCommand<?> message) {
                    return DistPubSubAccess.publish(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic(), message);
                }

                @Override
                public <S extends ThingCommand<?>> Object wrapForPublicationWithAcks(final S message,
                        final AckExtractor<S> ackExtractor) {
                    return wrapForPublication(message);
                }
            };
        }

        @Override
        public DistributedPub<ThingEvent<?>> event() {
            return new DistributedPub<>() {
                @Override
                public ActorRef getPublisher() {
                    return pubSubMediator;
                }

                @Override
                public Object wrapForPublication(final ThingEvent<?> message) {
                    return DistPubSubAccess.publish(StreamingType.LIVE_EVENTS.getDistributedPubSubTopic(), message);
                }

                @Override
                public <S extends ThingEvent<?>> Object wrapForPublicationWithAcks(final S message,
                        final AckExtractor<S> ackExtractor) {
                    return wrapForPublication(message);
                }
            };
        }

        @Override
        public DistributedPub<SignalWithEntityId<?>> message() {
            return new DistributedPub<>() {

                @Override
                public ActorRef getPublisher() {
                    return pubSubMediator;
                }

                @Override
                public Object wrapForPublication(final SignalWithEntityId<?> message) {
                    return DistPubSubAccess.publish(StreamingType.MESSAGES.getDistributedPubSubTopic(), message);
                }

                @Override
                public <S extends SignalWithEntityId<?>> Object wrapForPublicationWithAcks(final S message,
                        final AckExtractor<S> ackExtractor) {
                    return wrapForPublication(message);
                }
            };
        }

    }

}
