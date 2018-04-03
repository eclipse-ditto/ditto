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
package org.eclipse.ditto.services.authorization.util.enforcement;

import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;
import static org.eclipse.ditto.model.policies.SubjectIssuer.GOOGLE;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;
import org.eclipse.ditto.services.authorization.util.cache.EnforcerCacheLoader;
import org.eclipse.ditto.services.authorization.util.cache.ThingEnforcementIdCacheLoader;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.authorization.util.config.AuthorizationConfigReader;
import org.eclipse.ditto.services.authorization.util.mock.MockEntityRegionMap;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;

import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

public class TestSetup {

    public static final String THING = "thing";
    public static final String THING_SUDO = "thing-sudo";
    public static final String POLICY_SUDO = "policy-sudo";

    public static final String THING_ID = "thing:id";
    public static final AuthorizationSubject SUBJECT = AuthorizationSubject.newInstance("dummy-subject");

    public static final AuthorizationConfigReader CONFIG =
            AuthorizationConfigReader.from("authorization")
                    .apply(ConfigUtil.determineConfig("test"));

    public static ActorRef newEnforcerActor(final ActorSystem system, final ActorRef testActorRef,
            final ActorRef mockEntitiesActor) {

        return newEnforcerActor(system, testActorRef, mockEntitiesActor, null);
    }

    public static ActorRef newEnforcerActor(final ActorSystem system, final ActorRef testActorRef,
            final ActorRef mockEntitiesActor,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {

        final EntityRegionMap testActorMap = MockEntityRegionMap.uniform(testActorRef);

        final Consumer<Map.Entry<String, MetricRegistry>> dummyReportingConsumer = unused -> {};
        final Duration askTimeout = CONFIG.caches().askTimeout();
        final EnforcerCacheLoader enforcerCacheLoader =
                new EnforcerCacheLoader(askTimeout, mockEntitiesActor, mockEntitiesActor);

        final Map<String, AsyncCacheLoader<EntityId, Entry<EntityId>>> enforcementIdCacheLoaders = new HashMap<>();
        final ThingEnforcementIdCacheLoader thingEnforcementIdCacheLoader =
                new ThingEnforcementIdCacheLoader(askTimeout, mockEntitiesActor);
        enforcementIdCacheLoaders.put(ThingCommand.RESOURCE_TYPE, thingEnforcementIdCacheLoader);

        final AuthorizationCaches authorizationCaches = new AuthorizationCaches(CONFIG.caches(), enforcerCacheLoader,
                enforcementIdCacheLoaders, dummyReportingConsumer);
        final Props props =
                EnforcerActorFactory.props(testActorRef, testActorMap, authorizationCaches, preEnforcer);
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
