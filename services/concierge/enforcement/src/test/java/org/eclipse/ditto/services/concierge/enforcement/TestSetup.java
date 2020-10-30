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
package org.eclipse.ditto.services.concierge.enforcement;

import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;
import static org.eclipse.ditto.model.policies.SubjectIssuer.GOOGLE;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.concierge.common.CachesConfig;
import org.eclipse.ditto.services.concierge.common.DefaultCachesConfig;
import org.eclipse.ditto.services.models.concierge.pubsub.LiveSignalPub;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.eclipse.ditto.services.utils.cache.EntityIdWithResourceType;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.services.utils.cacheloaders.AclEnforcerCacheLoader;
import org.eclipse.ditto.services.utils.cacheloaders.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.services.utils.cacheloaders.ThingEnforcementIdCacheLoader;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.pubsub.DistributedPub;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.events.base.Event;

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
        final DefaultScopedConfig conciergeScopedConfig =
                DefaultScopedConfig.newInstance(dittoScopedConfig, "concierge");

        CACHES_CONFIG = DefaultCachesConfig.of(conciergeScopedConfig);
    }

    public static ActorRef newEnforcerActor(final ActorSystem system, final ActorRef testActorRef,
            final ActorRef mockEntitiesActor) {

        return new EnforcerActorBuilder(system, testActorRef, mockEntitiesActor).build();
    }

    public static ActorRef newEnforcerActor(final ActorSystem system,
            final ActorRef testActorRef,
            final ActorRef mockEntitiesActor,
            @Nullable final PreEnforcer preEnforcer) {
        return new EnforcerActorBuilder(system, testActorRef, mockEntitiesActor).setPreEnforcer(preEnforcer).build();
    }

    public static ActorRef newEnforcerActor(final ActorSystem system,
            final ActorRef testActorRef,
            final ActorRef thingsShardRegion,
            final ActorRef policiesShardRegion,
            @Nullable final PreEnforcer preEnforcer) {
        return new EnforcerActorBuilder(system, testActorRef, thingsShardRegion, policiesShardRegion).setPreEnforcer(
                preEnforcer).build();
    }

    static class EnforcerActorBuilder {

        private final ActorSystem system;
        private final ActorRef testActorRef;
        private final ActorRef thingsShardRegion;
        private final ActorRef policiesShardRegion;
        @Nullable private ActorRef conciergeForwarder;
        @Nullable private PreEnforcer preEnforcer;

        EnforcerActorBuilder(final ActorSystem system, final ActorRef testActorRef,
                final ActorRef mockEntityActors) {
            this.system = system;
            this.testActorRef = testActorRef;
            this.thingsShardRegion = mockEntityActors;
            this.policiesShardRegion = mockEntityActors;
        }

        EnforcerActorBuilder(final ActorSystem system, final ActorRef testActorRef,
                final ActorRef thingsShardRegion, final ActorRef policiesShardRegion) {
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

            final Duration askTimeout = CACHES_CONFIG.getAskTimeout();

            final PolicyEnforcerCacheLoader policyEnforcerCacheLoader =
                    new PolicyEnforcerCacheLoader(askTimeout, policiesShardRegion);
            final Cache<EntityIdWithResourceType, Entry<Enforcer>> policyEnforcerCache =
                    CaffeineCache.of(Caffeine.newBuilder(), policyEnforcerCacheLoader);
            final AclEnforcerCacheLoader aclEnforcerCacheLoader =
                    new AclEnforcerCacheLoader(askTimeout, thingsShardRegion);
            final Cache<EntityIdWithResourceType, Entry<Enforcer>> aclEnforcerCache =
                    CaffeineCache.of(Caffeine.newBuilder(), aclEnforcerCacheLoader);
            final ThingEnforcementIdCacheLoader thingEnforcementIdCacheLoader =
                    new ThingEnforcementIdCacheLoader(askTimeout, thingsShardRegion);
            final Cache<EntityIdWithResourceType, Entry<EntityIdWithResourceType>> thingIdCache =
                    CaffeineCache.of(Caffeine.newBuilder(), thingEnforcementIdCacheLoader);

            final Set<EnforcementProvider<?>> enforcementProviders = new HashSet<>();
            enforcementProviders.add(new ThingCommandEnforcement.Provider(thingsShardRegion,
                    policiesShardRegion, thingIdCache, policyEnforcerCache, aclEnforcerCache, preEnforcer));
            enforcementProviders.add(new PolicyCommandEnforcement.Provider(policiesShardRegion, policyEnforcerCache));
            enforcementProviders.add(
                    new LiveSignalEnforcement.Provider(thingIdCache, policyEnforcerCache, aclEnforcerCache,
                            new DummyLiveSignalPub(testActorRef)));

            final Props props =
                    EnforcerActor.props(testActorRef, enforcementProviders, conciergeForwarder, preEnforcer, null, null,
                            null);
            return system.actorOf(props, EnforcerActor.ACTOR_NAME);
        }

    }

    static String createUniqueName() {
        return "conciergeForwarder-" + UUID.randomUUID();
    }


    public static DittoHeaders headers(final JsonSchemaVersion schemaVersion) {
        return DittoHeaders.newBuilder()
                .authorizationContext(
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, SUBJECT,
                                AuthorizationSubject.newInstance(String.format("%s:%s", GOOGLE, SUBJECT_ID))))
                .schemaVersion(schemaVersion)
                .build();
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
        public DistributedPub<Command> command() {
            return new DistributedPub<>() {
                @Override
                public ActorRef getPublisher() {
                    return pubSubMediator;
                }

                @Override
                public Object wrapForPublication(final Command message) {
                    return DistPubSubAccess.publish(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic(), message);
                }

                @Override
                public Object wrapForPublicationWithAcks(final Command message,
                        final Set<AcknowledgementRequest> ackRequests,
                        final EntityIdWithType entityId, final DittoHeaders dittoHeaders, final ActorRef sender) {
                    return wrapForPublication(message);
                }
            };
        }

        @Override
        public DistributedPub<Event> event() {
            return new DistributedPub<>() {
                @Override
                public ActorRef getPublisher() {
                    return pubSubMediator;
                }

                @Override
                public Object wrapForPublication(final Event message) {
                    return DistPubSubAccess.publish(StreamingType.LIVE_EVENTS.getDistributedPubSubTopic(), message);
                }

                @Override
                public Object wrapForPublicationWithAcks(final Event message,
                        final Set<AcknowledgementRequest> ackRequests,
                        final EntityIdWithType entityId, final DittoHeaders dittoHeaders, final ActorRef sender) {
                    return wrapForPublication(message);
                }
            };
        }

        @Override
        public DistributedPub<Signal> message() {
            return new DistributedPub<>() {

                @Override
                public ActorRef getPublisher() {
                    return pubSubMediator;
                }

                @Override
                public Object wrapForPublication(final Signal message) {
                    return DistPubSubAccess.publish(StreamingType.MESSAGES.getDistributedPubSubTopic(), message);
                }

                @Override
                public Object wrapForPublicationWithAcks(final Signal message,
                        final Set<AcknowledgementRequest> ackRequests,
                        final EntityIdWithType entityId, final DittoHeaders dittoHeaders, final ActorRef sender) {
                    return wrapForPublication(message);
                }
            };
        }

    }

}
