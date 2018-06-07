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
package org.eclipse.ditto.services.concierge.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_1;
import static org.eclipse.ditto.model.things.Permission.ADMINISTRATE;
import static org.eclipse.ditto.model.things.Permission.READ;
import static org.eclipse.ditto.model.things.Permission.WRITE;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.SUBJECT;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.THING_SUDO;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.newThing;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.readCommand;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.writeCommand;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;

@SuppressWarnings({"squid:S3599", "squid:S1171"})
public final class PreEnforcementTest {

    private ActorSystem system;
    private ActorRef mockEntitiesActor;
    private MockEntitiesActor mockEntitiesActorInstance;

    @Before
    public void init() {
        system = ActorSystem.create();
        final TestActorRef<MockEntitiesActor> testActorRef =
                new TestActorRef<>(system, MockEntitiesActor.props(), system.guardian(), UUID
                        .randomUUID().toString());
        mockEntitiesActorInstance = testActorRef.underlyingActor();
        mockEntitiesActor = testActorRef;
    }

    @After
    public void shutdown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Test
    public void acceptWhenPreEnforcementIsSuccessful() {
        final JsonObject thingWithAcl = newThing()
                .setPermissions(
                        AclEntry.newInstance(SUBJECT, READ, WRITE, ADMINISTRATE))
                .build()
                .toJson(V_1, FieldType.all());
        final SudoRetrieveThingResponse response =
                SudoRetrieveThingResponse.of(thingWithAcl, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, response);

            final ActorRef underTest = newEnforcerActor(getRef(), CompletableFuture::completedFuture);
            final ThingCommand read = readCommand();
            mockEntitiesActorInstance.setReply(read);
            underTest.tell(read, getRef());
            assertThat(expectMsgClass(read.getClass()).getId()).isEqualTo(read.getId());

            final ThingCommand write = writeCommand();
            mockEntitiesActorInstance.setReply(write);
            underTest.tell(write, getRef());
            assertThat(expectMsgClass(write.getClass()).getId()).isEqualTo(write.getId());
        }};
    }

    @Test
    public void rejectWhenPreEnforcementThrowsDittoRuntimeException() {
        final JsonObject thingWithAcl = newThing()
                .setPermissions(
                        AclEntry.newInstance(SUBJECT, READ, WRITE, ADMINISTRATE))
                .build()
                .toJson(V_1, FieldType.all());
        final SudoRetrieveThingResponse response =
                SudoRetrieveThingResponse.of(thingWithAcl, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, response);

            final GatewayAuthenticationFailedException mockedEx =
                    GatewayAuthenticationFailedException.newBuilder("wanted exception").build();

            final ActorRef underTest = newEnforcerActor(getRef(), msg -> CompletableFuture.supplyAsync(() -> {
                throw mockedEx;
            }));
            final ThingCommand read = readCommand();
            underTest.tell(read, getRef());
            assertThat(expectMsgClass(mockedEx.getClass())).isEqualTo(mockedEx);

            final ThingCommand write = writeCommand();
            underTest.tell(write, getRef());
            assertThat(expectMsgClass(mockedEx.getClass())).isEqualTo(mockedEx);
        }};
    }


    @Test
    public void rejectWhenPreEnforcementReturnsUnexpectedException() {
        final JsonObject thingWithAcl = newThing()
                .setPermissions(
                        AclEntry.newInstance(SUBJECT, READ, WRITE, ADMINISTRATE))
                .build()
                .toJson(V_1, FieldType.all());
        final SudoRetrieveThingResponse response =
                SudoRetrieveThingResponse.of(thingWithAcl, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, response);

            final IllegalStateException mockedEx = new IllegalStateException("wanted exception");

            final Class<? extends DittoRuntimeException> unexpectedExceptionResultClass =
                    GatewayInternalErrorException.class;
            final ActorRef underTest = newEnforcerActor(getRef(), msg -> CompletableFuture.supplyAsync(() -> {
                throw mockedEx;
            }));
            final ThingCommand read = readCommand();
            underTest.tell(read, getRef());
            expectMsgClass(unexpectedExceptionResultClass);

            final ThingCommand write = writeCommand();
            underTest.tell(write, getRef());
            expectMsgClass(unexpectedExceptionResultClass);
        }};
    }

    private ActorRef newEnforcerActor(final ActorRef testActorRef,
            final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {
        return TestSetup.newEnforcerActor(system, testActorRef, mockEntitiesActor, preEnforcer);
    }
}
