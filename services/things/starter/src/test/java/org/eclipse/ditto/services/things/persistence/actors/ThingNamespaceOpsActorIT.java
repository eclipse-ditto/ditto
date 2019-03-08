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

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.config.DefaultThingConfig;
import org.eclipse.ditto.services.things.persistence.config.ThingConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.namespace.EventSourceNamespaceOpsActorTestCases;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.junit.BeforeClass;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * Tests {@link org.eclipse.ditto.services.things.persistence.actors.ThingNamespaceOpsActor} against a local MongoDB.
 */
@AllValuesAreNonnullByDefault
public final class ThingNamespaceOpsActorIT extends EventSourceNamespaceOpsActorTestCases {

    private static ThingConfig thingConfig;

    @BeforeClass
    public static void initTestFixture() {
        thingConfig = DefaultThingConfig.of(ConfigFactory.load("thing-test"));
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
    protected List<String> getSupportedPrefixes() {
        return Collections.singletonList(ThingCommand.RESOURCE_TYPE);
    }

    @Override
    protected Object getCreateEntityCommand(final String id) {
        return CreateThing.of(Thing.newBuilder().setId(id).setPolicyId(id).build(), null, DittoHeaders.empty());
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

        final Props namespaceOpsActorProps = ThingNamespaceOpsActor.props(pubSubMediator, config, mongoDbConfig);
        return actorSystem.actorOf(namespaceOpsActorProps, ThingNamespaceOpsActor.ACTOR_NAME);
    }

    @Override
    protected ActorRef startEntityActor(final ActorSystem system, final ActorRef pubSubMediator, final String id) {
        final Props props = ThingSupervisorActor.props(pubSubMediator, thingConfig,
                theId -> ThingPersistenceActor.props(theId, pubSubMediator, thingConfig, true));

        return system.actorOf(props, id);
    }

}
