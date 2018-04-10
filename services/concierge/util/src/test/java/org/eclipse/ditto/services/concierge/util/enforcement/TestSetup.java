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
package org.eclipse.ditto.services.concierge.util.enforcement;

import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;
import static org.eclipse.ditto.model.policies.SubjectIssuer.GOOGLE;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
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
import org.eclipse.ditto.services.concierge.util.cache.AclEnforcerCacheLoader;
import org.eclipse.ditto.services.concierge.util.cache.IdentityCache;
import org.eclipse.ditto.services.concierge.util.cache.PolicyEnforcerCacheLoader;
import org.eclipse.ditto.services.concierge.util.cache.ThingEnforcementIdCacheLoader;
import org.eclipse.ditto.services.concierge.util.cache.entry.Entry;
import org.eclipse.ditto.services.concierge.util.config.ConciergeConfigReader;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;

import com.github.benmanes.caffeine.cache.Caffeine;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

public class TestSetup {

    public static final String THING = "thing";
    public static final String THING_SUDO = "thing-sudo";
    public static final String POLICY_SUDO = "policy-sudo";

    public static final String THING_ID = "thing:id";
    public static final AuthorizationSubject SUBJECT = AuthorizationSubject.newInstance("dummy-subject");

    public static final ConciergeConfigReader CONFIG =
            ConciergeConfigReader.from("concierge")
                    .apply(ConfigUtil.determineConfig("test"));

    public static ActorRef newEnforcerActor(final ActorSystem system, final ActorRef testActorRef,
            final ActorRef mockEntitiesActor) {

        return newEnforcerActor(system, testActorRef, mockEntitiesActor, null);
    }

    public static ActorRef newEnforcerActor(final ActorSystem system, final ActorRef testActorRef,
            final ActorRef mockEntitiesActor,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {

        final Duration askTimeout = CONFIG.caches().askTimeout();
        final ActorRef thingsShardRegion = mockEntitiesActor;
        final ActorRef policiesShardRegion = mockEntitiesActor;

        final PolicyEnforcerCacheLoader policyEnforcerCacheLoader =
                new PolicyEnforcerCacheLoader(askTimeout, policiesShardRegion);
        final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache = CaffeineCache.of(Caffeine.newBuilder(),
                policyEnforcerCacheLoader);
        final AclEnforcerCacheLoader aclEnforcerCacheLoader =
                new AclEnforcerCacheLoader(askTimeout, thingsShardRegion);
        final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache = CaffeineCache.of(Caffeine.newBuilder(),
                aclEnforcerCacheLoader);
        final ThingEnforcementIdCacheLoader thingEnforcementIdCacheLoader =
                new ThingEnforcementIdCacheLoader(askTimeout, thingsShardRegion);
        final Cache<EntityId, Entry<EntityId>> thingIdCache =
                CaffeineCache.of(Caffeine.newBuilder(), thingEnforcementIdCacheLoader);

        final IdentityCache policyIdCache = new IdentityCache();

        final Set<EnforcementProvider<?>> enforcementProviders = new HashSet<>();
        enforcementProviders.add(new ThingCommandEnforcement.Provider(thingsShardRegion,
                policiesShardRegion, thingIdCache, policyIdCache, policyEnforcerCache, aclEnforcerCache));
        enforcementProviders.add(new PolicyCommandEnforcement.Provider(policiesShardRegion,
                policyIdCache, policyEnforcerCache));

        final Props props = EnforcerActor.props(testActorRef, enforcementProviders, preEnforcer);
        return system.actorOf(props, THING + ":" + THING_ID);
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
}
