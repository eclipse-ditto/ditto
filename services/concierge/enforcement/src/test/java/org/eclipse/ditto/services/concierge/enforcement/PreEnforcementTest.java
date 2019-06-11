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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_1;
import static org.eclipse.ditto.model.things.Permission.ADMINISTRATE;
import static org.eclipse.ditto.model.things.Permission.READ;
import static org.eclipse.ditto.model.things.Permission.WRITE;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.SUBJECT;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.THING_SUDO;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.fishForMsgClass;
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
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;

@SuppressWarnings({"squid:S3599", "squid:S1171"})
public final class PreEnforcementTest {

    private ActorSystem system;
    private ActorRef mockEntitiesActor;
    private MockEntitiesActor mockEntitiesActorInstance;

    @Before
    public void init() {
        system = ActorSystem.create("test", ConfigFactory.load("test"));
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
                .setPermissions(AclEntry.newInstance(SUBJECT, READ, WRITE, ADMINISTRATE))
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
            assertThat(fishForMsgClass(this, read.getClass()).getId()).isEqualTo(read.getId());

            final ThingCommand write = writeCommand();
            mockEntitiesActorInstance.setReply(write);
            underTest.tell(write, getRef());
            assertThat(fishForMsgClass(this, write.getClass()).getId()).isEqualTo(write.getId());
        }};
    }

    @Test
    public void rejectWhenPreEnforcementThrowsDittoRuntimeException() {
        disableLogging();

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
            assertThat(fishForMsgClass(this, mockedEx.getClass())).isEqualTo(mockedEx);

            final ThingCommand write = writeCommand();
            underTest.tell(write, getRef());
            assertThat(fishForMsgClass(this, mockedEx.getClass())).isEqualTo(mockedEx);
        }};
    }

    @Test
    public void rejectWhenPreEnforcementThrowsThingIdInvalidException() {
        disableLogging();

        final String invalidThingId = "invalidThingId";
        final Thing thing = newThing()
                .setId(invalidThingId)
                .setPermissions(
                        AclEntry.newInstance(SUBJECT, READ, WRITE, ADMINISTRATE))
                .build();
        final SudoRetrieveThingResponse response =
                SudoRetrieveThingResponse.of(thing.toJson(), DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, response);

            final ThingIdInvalidException mockedEx =
                    ThingIdInvalidException.newBuilder(invalidThingId).build();

            final ActorRef underTest = newEnforcerActor(getRef(), msg -> CompletableFuture.supplyAsync(() -> {
                throw mockedEx;
            }));

            final ThingCommand create = CreateThing.of(thing, null, DittoHeaders.empty());
            underTest.tell(create, getRef());
            assertThat(fishForMsgClass(this, mockedEx.getClass())).isEqualTo(mockedEx);
        }};
    }

    @Test
    public void rejectWhenPreEnforcementReturnsUnexpectedException() {
        disableLogging();

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
            fishForMsgClass(this, unexpectedExceptionResultClass);

            final ThingCommand write = writeCommand();
            underTest.tell(write, getRef());
            fishForMsgClass(this, unexpectedExceptionResultClass);
        }};
    }

    private ActorRef newEnforcerActor(final ActorRef testActorRef,
            final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {
        return TestSetup.newEnforcerActor(system, testActorRef, mockEntitiesActor, preEnforcer);
    }

    /**
     * Disable logging for 1 test to hide stacktrace or other logs on level ERROR. Comment out to debug the test.
     */
    private void disableLogging() {
        system.eventStream().setLogLevel(Logging.levelFor("off").get().asInt());
    }
}
