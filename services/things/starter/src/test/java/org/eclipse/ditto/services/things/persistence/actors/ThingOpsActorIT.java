/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.things.persistence.actors;

import java.time.Duration;
import java.util.Arrays;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource.OpsActorTestCases;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Tests {@link ThingOpsActor} against a local MongoDB.
 */
@AllValuesAreNonnullByDefault
@RunWith(Parameterized.class)
public final class ThingOpsActorIT extends OpsActorTestCases {

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSetting> data() {
        return Arrays.asList(TestSetting.NAMESPACES_WITHOUT_SUFFIX, TestSetting.NAMESPACES_WITH_SUFFIX);
    }

    public ThingOpsActorIT(final OpsActorTestCases.TestSetting testSetting) {
        super(testSetting);
    }

    @Override
    protected boolean idsStartWithNamespace() {
        return true;
    }

    @Override
    protected String getServiceName() {
        return "things";
    }

    @Override
    protected String getResourceType() {
        return ThingCommand.RESOURCE_TYPE;
    }

    @Override
    protected Object getCreateEntityCommand(final String id) {
        final DittoHeaders headers = DittoHeaders.empty();
        return CreateThing.of(Thing.newBuilder().setId(id).setPolicyId(id).build(), null, headers);
    }

    @Override
    protected Class<?> getCreateEntityResponseClass() {
        return CreateThingResponse.class;
    }

    @Override
    protected Object getRetrieveEntityCommand(final String id) {
        return RetrieveThing.of(id, DittoHeaders.empty());
    }

    @Override
    protected Class<?> getRetrieveEntityResponseClass() {
        return RetrieveThingResponse.class;
    }

    @Override
    protected Class<?> getEntityNotAccessibleClass() {
        return ThingNotAccessibleException.class;
    }

    @Override
    protected ActorRef startActorUnderTest(final ActorSystem actorSystem, final ActorRef pubSubMediator,
            final Config config) {

        final Props opsActorProps = ThingOpsActor.props(pubSubMediator, config);
        return actorSystem.actorOf(opsActorProps, ThingOpsActor.ACTOR_NAME);
    }

    @Override
    protected ActorRef startEntityActor(final ActorSystem system, final ActorRef pubSubMediator, final String id) {
        // essentially never restart
        final Duration minBackOff = Duration.ofSeconds(36000);
        final Duration maxBackOff = Duration.ofSeconds(36000);
        final double randomFactor = 0.2;

        final Props props = ThingSupervisorActor.props(pubSubMediator, minBackOff, maxBackOff, randomFactor,
                theId -> ThingPersistenceActor.props(theId, pubSubMediator));

        return system.actorOf(props, id);
    }
}
