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
package org.eclipse.ditto.services.gateway.proxy.actors;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.models.things.ThingCacheEntry;
import org.eclipse.ditto.services.utils.akka.JavaTestProbe;
import org.eclipse.ditto.services.utils.distributedcache.actors.ReadConsistency;
import org.eclipse.ditto.services.utils.distributedcache.actors.RetrieveCacheEntry;
import org.eclipse.ditto.services.utils.distributedcache.actors.RetrieveCacheEntryResponse;
import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.JavaTestKit;

/**
 * Unit test for {@link EnforcerLookupActor}.
 */
public final class EnforcerLookupActorTest {

    private static final Config CONFIG = ConfigFactory.load("test");

    private static final String POLICY_ID = "org.eclipse.ditto:myPolicy";
    private static final String THING_ID_V1 = "org.eclipse.ditto:myThingV1";
    private static final String THING_ID_V2 = "org.eclipse.ditto:myThingV2";
    private static final String THING_ID_NOT_EXISTING = "org.eclipse.ditto:notExisting";
    private static final Long THING_SEQUENCE_NUMBER = 1337L;

    private static final CacheEntry THING_CACHE_V1 =
            ThingCacheEntry.of(JsonSchemaVersion.V_1, null, THING_SEQUENCE_NUMBER);

    private static final CacheEntry THING_CACHE_V2 =
            ThingCacheEntry.of(JsonSchemaVersion.V_2, POLICY_ID, THING_SEQUENCE_NUMBER);

    private static final CacheEntry THING_CACHE_DELETED =
            ThingCacheEntry.of(JsonSchemaVersion.LATEST, POLICY_ID, THING_SEQUENCE_NUMBER)
                    .asDeleted(THING_SEQUENCE_NUMBER);

    private ActorSystem actorSystem;
    private ActorRef underTest;
    private ActorRef aclEnforcer;
    private ActorRef policyEnforcer;

    @Before
    public void setUp() throws Exception {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
        aclEnforcer = actorSystem.actorOf(Props.create(ShardRegionMock.class), "aclEnforcer");
        policyEnforcer = actorSystem.actorOf(Props.create(ShardRegionMock.class), "policyEnforcer");

        final ActorRef thingCacheFacade = actorSystem.actorOf(Props.create(CacheFacadeMock.class), "thingCacheFacade");
        final ThingEnforcerLookupFunctionMock lookupFunction =
                new ThingEnforcerLookupFunctionMock(aclEnforcer, policyEnforcer);

        underTest = actorSystem.actorOf(EnforcerLookupActor.props(aclEnforcer, policyEnforcer, thingCacheFacade,
                lookupFunction), "enforcerLookup");
    }

    @After
    public void tearDown() throws Exception {
        JavaTestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void lookupThingV1() {
        new JavaTestProbe(actorSystem) {
            {
                final RetrieveThing retrieveThing = RetrieveThing.of(THING_ID_V1, DittoHeaders.empty());
                final LookupContext lookupContext = LookupContext.getInstance(retrieveThing, ref(), ref());
                final LookupEnforcer lookupEnforcer =
                        new LookupEnforcer(THING_ID_V1, lookupContext, ReadConsistency.LOCAL);
                final LookupEnforcerResponse expectedResponse =
                        new LookupEnforcerResponse(aclEnforcer, THING_ID_V1, lookupContext, THING_CACHE_V1);

                underTest.tell(lookupEnforcer, ref());

                expectMsgEquals(expectedResponse);
            }
        };
    }

    @Test
    public void lookupThingV2() {
        new JavaTestProbe(actorSystem) {
            {
                final RetrieveThing retrieveThing = RetrieveThing.of(THING_ID_V2, DittoHeaders.empty());
                final LookupContext lookupContext = LookupContext.getInstance(retrieveThing, ref(), ref());
                final LookupEnforcer lookupEnforcer =
                        new LookupEnforcer(THING_ID_V2, lookupContext, ReadConsistency.LOCAL);
                final LookupEnforcerResponse expectedResponse =
                        new LookupEnforcerResponse(policyEnforcer, POLICY_ID, lookupContext, THING_CACHE_V2);

                underTest.tell(lookupEnforcer, ref());

                expectMsgEquals(expectedResponse);
            }
        };
    }

    @Test
    public void lookupThingNotExisting() {
        new JavaTestProbe(actorSystem) {
            {
                final RetrieveThing retrieveThing =
                        RetrieveThing.of(THING_ID_NOT_EXISTING, DittoHeaders.empty());
                final LookupContext lookupContext = LookupContext.getInstance(retrieveThing, ref(), ref());
                final LookupEnforcer lookupEnforcer =
                        new LookupEnforcer(THING_ID_NOT_EXISTING, lookupContext, ReadConsistency.LOCAL);
                final LookupEnforcerResponse expectedResponse =
                        new LookupEnforcerResponse(null, null, lookupContext, THING_CACHE_DELETED);

                underTest.tell(lookupEnforcer, ref());

                expectMsgEquals(expectedResponse);
            }
        };
    }

    private static final class ThingEnforcerLookupFunctionMock implements EnforcerLookupFunction {

        private final ActorRef aclEnforcer;
        private final ActorRef policyEnforcer;

        private ThingEnforcerLookupFunctionMock(final ActorRef aclEnforcer, final ActorRef policyEnforcer) {
            this.aclEnforcer = aclEnforcer;
            this.policyEnforcer = policyEnforcer;
        }

        @Override
        public CompletionStage<LookupResult> lookup(final CharSequence id, final CharSequence correlationId) {
            final LookupResult lookupResult;

            if (THING_ID_V1.equals(id)) {
                lookupResult = LookupResult.of(THING_ID_V1,
                        THING_CACHE_V1, aclEnforcer);
            } else if (THING_ID_V2.equals(id)) {
                lookupResult = LookupResult.of(POLICY_ID,
                        THING_CACHE_V2, policyEnforcer);
            } else {
                lookupResult = LookupResult.notFound();
            }

            return CompletableFuture.completedFuture(lookupResult);
        }
    }

    private static final class ShardRegionMock extends AbstractActor {

        private final DiagnosticLoggingAdapter log;

        private ShardRegionMock() {
            log = Logging.apply(this);
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .matchAny(o -> log.debug("Received: {}", o))
                    .build();
        }
    }

    private static final class CacheFacadeMock extends AbstractActor {

        private final DiagnosticLoggingAdapter log;

        private CacheFacadeMock() {
            log = Logging.apply(this);
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(RetrieveCacheEntry.class, this::retrieveCacheEntry)
                    .matchAny(o -> log.debug("Received: {}", o))
                    .build();
        }

        private void retrieveCacheEntry(final RetrieveCacheEntry command) {
            final CacheEntry cacheEntry;
            if (THING_ID_V1.equals(command.getId())) {
                cacheEntry = THING_CACHE_V1;
            } else if (THING_ID_V2.equals(command.getId())) {
                cacheEntry = THING_CACHE_V2;
            } else {
                cacheEntry = THING_CACHE_DELETED;
            }

            getSender().tell(
                    new RetrieveCacheEntryResponse(command.getId(), cacheEntry, command.getContext().orElse(null)),
                    getSelf());
        }
    }

}
