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
package org.eclipse.ditto.concierge.service.enforcement;

import static org.eclipse.ditto.base.model.json.JsonSchemaVersion.V_2;
import static org.eclipse.ditto.policies.model.SubjectIssuer.GOOGLE;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.concierge.service.common.CachesConfig;
import org.eclipse.ditto.concierge.service.common.DefaultCachesConfig;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CaffeineCache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyCacheLoader;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcer;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.internal.utils.cacheloaders.ThingEnforcementIdCacheLoader;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.config.DefaultAskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.LiveSignalPub;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import com.github.benmanes.caffeine.cache.Caffeine;
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

    public static final ThingId THING_ID = ThingId.of("thing", "id");
    public static final String SUBJECT_ID = "subject";
    public static final AuthorizationSubject SUBJECT = AuthorizationSubject.newInstance("dummy:" + SUBJECT_ID);

    private static final Config RAW_CONFIG = ConfigFactory.load("test");
    private static final CachesConfig CACHES_CONFIG;

    static {
        final DefaultScopedConfig dittoScopedConfig = DefaultScopedConfig.dittoScoped(RAW_CONFIG);
        final DefaultScopedConfig conciergeScopedConfig = DefaultScopedConfig.newInstance(dittoScopedConfig,
                "concierge");

        CACHES_CONFIG = DefaultCachesConfig.of(conciergeScopedConfig);
    }

    public static ActorRef newEnforcerActor(final ActorSystem system, final ActorRef testActorRef,
            final ActorRef mockEntitiesActor) {

        return new EnforcerActorBuilder(system, testActorRef, mockEntitiesActor).build();
    }

    public static ActorRef newEnforcerActor(final ActorSystem system, final ActorRef testActorRef,
            final ActorRef mockEntitiesActor, @Nullable final PreEnforcer preEnforcer) {
        return new EnforcerActorBuilder(system, testActorRef, mockEntitiesActor).setPreEnforcer(preEnforcer).build();
    }

    public static ActorRef newEnforcerActor(final ActorSystem system, final ActorRef testActorRef,
            final ActorRef thingsShardRegion, final ActorRef policiesShardRegion,
            @Nullable final PreEnforcer preEnforcer) {
        return new EnforcerActorBuilder(system, testActorRef, thingsShardRegion, policiesShardRegion)
                .setPreEnforcer(preEnforcer).build();
    }

    static class EnforcerActorBuilder {

        private final ActorSystem system;
        private final ActorRef testActorRef;
        private final ActorRef thingsShardRegion;
        private final ActorRef policiesShardRegion;
        @Nullable
        private ActorRef conciergeForwarder;
        @Nullable
        private PreEnforcer preEnforcer;

        EnforcerActorBuilder(final ActorSystem system, final ActorRef testActorRef, final ActorRef mockEntityActors) {
            this.system = system;
            this.testActorRef = testActorRef;
            this.thingsShardRegion = mockEntityActors;
            this.policiesShardRegion = mockEntityActors;
        }

        EnforcerActorBuilder(final ActorSystem system, final ActorRef testActorRef, final ActorRef thingsShardRegion,
                final ActorRef policiesShardRegion) {
            this.system = system;
            this.testActorRef = testActorRef;
            this.thingsShardRegion = thingsShardRegion;
            this.policiesShardRegion = policiesShardRegion;
        }

        public EnforcerActorBuilder setPreEnforcer(@Nullable final PreEnforcer preEnforcer) {
            this.preEnforcer = preEnforcer;
            return this;
        }

        public EnforcerActorBuilder setConciergeForwarder(final ActorRef conciergeForwarder) {
            this.conciergeForwarder = conciergeForwarder;
            return this;
        }

        public ActorRef build() {

            if (conciergeForwarder == null) {
                conciergeForwarder = new TestProbe(system, createUniqueName()).ref();
            }

            final AskWithRetryConfig askWithRetryConfig = CACHES_CONFIG.getAskWithRetryConfig();

            final PolicyCacheLoader policyCacheLoader = new PolicyCacheLoader(
                    DefaultAskWithRetryConfig.of(ConfigFactory.empty(), "test"), system.getScheduler(), policiesShardRegion);
            final Cache<EnforcementCacheKey, Entry<Policy>> policyCache = CaffeineCache.of(Caffeine.newBuilder(),
                    policyCacheLoader);
            final PolicyEnforcerCacheLoader policyEnforcerCacheLoader = new PolicyEnforcerCacheLoader(policyCache);
            final Cache<EnforcementCacheKey, Entry<PolicyEnforcer>> policyEnforcerCache =
                    CaffeineCache.of(Caffeine.newBuilder(), policyEnforcerCacheLoader);
            final Cache<EnforcementCacheKey, Entry<Enforcer>> projectedEnforcerCache =
                    policyEnforcerCache.projectValues(PolicyEnforcer::project, PolicyEnforcer::embed);
            final ThingEnforcementIdCacheLoader thingEnforcementIdCacheLoader =
                    new ThingEnforcementIdCacheLoader(askWithRetryConfig, system.getScheduler(), thingsShardRegion);
            final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingIdCache =
                    CaffeineCache.of(Caffeine.newBuilder(), thingEnforcementIdCacheLoader);

            final Set<EnforcementProvider<?>> enforcementProviders = new HashSet<>();
            enforcementProviders.add(new ThingCommandEnforcement.Provider(thingsShardRegion,
                    policiesShardRegion, thingIdCache, policyCache, projectedEnforcerCache, preEnforcer));
            enforcementProviders.add(new PolicyCommandEnforcement.Provider(policiesShardRegion, policyCache, policyEnforcerCache));
            enforcementProviders.add(
                    new LiveSignalEnforcement.Provider(thingIdCache, projectedEnforcerCache,
                            new DummyLiveSignalPub(testActorRef)));

            final Props props =
                    EnforcerActor.props(testActorRef, enforcementProviders, conciergeForwarder, preEnforcer, null,
                            null, null);
            return system.actorOf(props, EnforcerActor.ACTOR_NAME);
        }

    }

    static String createUniqueName() {
        return "conciergeForwarder-" + UUID.randomUUID();
    }

    public static DittoHeaders headers(final JsonSchemaVersion schemaVersion) {
        return DittoHeaders.newBuilder()
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        SUBJECT, AuthorizationSubject.newInstance(String.format("%s:%s", GOOGLE, SUBJECT_ID))))
                .schemaVersion(schemaVersion).build();
    }

    public static ThingBuilder.FromScratch newThing() {
        return ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(1L);
    }

    public static ThingCommand readCommand() {
        return RetrieveThing.of(THING_ID, headers(V_2));
    }

    public static ThingCommand writeCommand() {
        return ModifyFeature.of(THING_ID, Feature.newBuilder().withId("x").build(), headers(V_2));
    }

    /**
     * Similar to {@link TestKit#expectMsgClass(Class)} but ignores other messages occurring while waiting for a
     * message of the the passed {@code clazz}.
     *
     * @param testKit the TestKit to fish for messages in
     * @param clazz the type of the message to wait for
     * @param <T> the type of the waited for message
     * @return the message
     */
    public static <T> T fishForMsgClass(final TestKit testKit, final Class<T> clazz) {
        return clazz.cast(
                testKit.fishForMessage(FiniteDuration.apply(3, TimeUnit.SECONDS), clazz.getName(), clazz::isInstance));
    }

    private static final class DummyLiveSignalPub implements LiveSignalPub {

        private final ActorRef pubSubMediator;

        private DummyLiveSignalPub(final ActorRef pubSubMediator) {
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
