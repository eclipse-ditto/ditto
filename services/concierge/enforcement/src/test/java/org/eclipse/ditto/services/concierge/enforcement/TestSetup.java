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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.concierge.common.CachesConfig;
import org.eclipse.ditto.services.concierge.common.DefaultCachesConfig;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.eclipse.ditto.services.utils.cache.EntityIdWithResourceType;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.services.utils.cacheloaders.AclEnforcerCacheLoader;
import org.eclipse.ditto.services.utils.cacheloaders.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.services.utils.cacheloaders.ThingEnforcementIdCacheLoader;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

public final class TestSetup {

    public static final String THING = "thing";
    public static final String THING_SUDO = "thing-sudo";
    public static final String POLICY_SUDO = "policy-sudo";

    public static final ThingId THING_ID = ThingId.of("thing", "id");
    public static final AuthorizationSubject SUBJECT = AuthorizationSubject.newInstance("dummy:subject");

    public static final Config RAW_CONFIG = ConfigFactory.load("test");
    public static final CachesConfig CACHES_CONFIG;

    static {
        final DefaultScopedConfig dittoScopedConfig = DefaultScopedConfig.dittoScoped(RAW_CONFIG);
        final DefaultScopedConfig conciergeScopedConfig =
                DefaultScopedConfig.newInstance(dittoScopedConfig, "concierge");

        CACHES_CONFIG = DefaultCachesConfig.of(conciergeScopedConfig);
    }

    public static ActorRef newEnforcerActor(final ActorSystem system, final ActorRef testActorRef,
            final ActorRef mockEntitiesActor) {

        return newEnforcerActor(system, testActorRef, mockEntitiesActor, null);
    }

    public static ActorRef newEnforcerActor(final ActorSystem system, final ActorRef testActorRef,
            final ActorRef mockEntitiesActor,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {

        return newEnforcerActor(system, testActorRef, mockEntitiesActor, mockEntitiesActor, preEnforcer);
    }

    public static ActorRef newEnforcerActor(final ActorSystem system, final ActorRef testActorRef,
            final ActorRef thingsShardRegion, final ActorRef policiesShardRegion,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {

        final ActorRef conciergeForwarder =
                new TestProbe(system, createUniqueName("conciergeForwarder-")).ref();
        final Duration askTimeout = CACHES_CONFIG.getAskTimeout();

        final PolicyEnforcerCacheLoader policyEnforcerCacheLoader =
                new PolicyEnforcerCacheLoader(askTimeout, policiesShardRegion);
        final Cache<EntityIdWithResourceType, Entry<Enforcer>> policyEnforcerCache =
                CaffeineCache.of(Caffeine.newBuilder(),
                policyEnforcerCacheLoader);
        final AclEnforcerCacheLoader aclEnforcerCacheLoader =
                new AclEnforcerCacheLoader(askTimeout, thingsShardRegion);
        final Cache<EntityIdWithResourceType, Entry<Enforcer>> aclEnforcerCache =
                CaffeineCache.of(Caffeine.newBuilder(),
                aclEnforcerCacheLoader);
        final ThingEnforcementIdCacheLoader thingEnforcementIdCacheLoader =
                new ThingEnforcementIdCacheLoader(askTimeout, thingsShardRegion);
        final Cache<EntityIdWithResourceType, Entry<EntityIdWithResourceType>> thingIdCache =
                CaffeineCache.of(Caffeine.newBuilder(), thingEnforcementIdCacheLoader);

        final Set<EnforcementProvider<?>> enforcementProviders = new HashSet<>();
        enforcementProviders.add(new ThingCommandEnforcement.Provider(thingsShardRegion,
                policiesShardRegion, thingIdCache, policyEnforcerCache, aclEnforcerCache, preEnforcer));
        enforcementProviders.add(new PolicyCommandEnforcement.Provider(policiesShardRegion, policyEnforcerCache));
        enforcementProviders.add(
                new LiveSignalEnforcement.Provider(thingIdCache, policyEnforcerCache, aclEnforcerCache));

        final Props props = EnforcerActor.props(testActorRef, enforcementProviders, conciergeForwarder,
                preEnforcer, null, null, null);
        return system.actorOf(props, THING + ":" + THING_ID);
    }

    private static String createUniqueName(final String prefix) {
        return prefix + UUID.randomUUID().toString();
    }

    public static DittoHeaders headers(final JsonSchemaVersion schemaVersion) {
        return DittoHeaders.newBuilder()
                .authorizationSubjects(SUBJECT.getId(), String.format("%s:%s", GOOGLE, SUBJECT))
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
        return (T) testKit.fishForMessage(scala.concurrent.duration.Duration.create(3, TimeUnit.SECONDS),
                clazz.getName(), clazz::isInstance);
    }

}
