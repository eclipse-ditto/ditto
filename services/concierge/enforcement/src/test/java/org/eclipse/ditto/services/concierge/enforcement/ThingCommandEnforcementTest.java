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
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_2;
import static org.eclipse.ditto.model.policies.SubjectIssuer.GOOGLE;
import static org.eclipse.ditto.model.things.Permission.ADMINISTRATE;
import static org.eclipse.ditto.model.things.Permission.READ;
import static org.eclipse.ditto.model.things.Permission.WRITE;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.POLICY_SUDO;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.SUBJECT;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.THING_ID;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.THING_SUDO;

import java.util.UUID;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;

@SuppressWarnings({"squid:S3599", "squid:S1171"})
public final class ThingCommandEnforcementTest {

    private ActorSystem system;
    private MockEntitiesActor mockEntitiesActorInstance;
    private ActorRef mockEntitiesActor;

    @Before
    public void init() {
        system = ActorSystem.create();
        final TestActorRef<MockEntitiesActor> testActorRef =
                new TestActorRef<>(system, MockEntitiesActor.props(), system.guardian(), UUID.randomUUID().toString());
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
    public void rejectByAcl() {
        final JsonObject thingWithEmptyAcl = newThing()
                .setPermissions(AccessControlList.newBuilder().build())
                .build()
                .toJson(V_1, FieldType.all());
        final SudoRetrieveThingResponse response =
                SudoRetrieveThingResponse.of(thingWithEmptyAcl, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, response);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(readCommand(), getRef());
            expectMsgClass(ThingNotAccessibleException.class);

            underTest.tell(writeCommand(), getRef());
            expectMsgClass(FeatureNotModifiableException.class);
        }};
    }

    @Test
    public void rejectByPolicy() {
        final String policyId = "empty:policy";
        final JsonObject thingWithEmptyPolicy = newThingWithPolicyId(policyId);
        final JsonObject emptyPolicy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .build()
                .toJson(FieldType.all());
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithEmptyPolicy, DittoHeaders.empty());
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, emptyPolicy, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(readCommand(), getRef());
            expectMsgClass(ThingNotAccessibleException.class);

            underTest.tell(writeCommand(), getRef());
            expectMsgClass(FeatureNotModifiableException.class);
        }};
    }

    @Test
    public void rejectQueryByThingNotAccessibleException() {
        final DittoRuntimeException error = ThingNotAccessibleException.newBuilder(THING_ID).build();

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, error);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(readCommand(), getRef());
            expectMsg(error);
        }};
    }

    @Test
    public void rejectUpdateByThingNotAccessibleException() {
        final DittoRuntimeException error = ThingNotAccessibleException.newBuilder(THING_ID).build();
        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, error);

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(writeCommand(), getRef());
            expectMsgClass(ThingNotAccessibleException.class);
        }};
    }

    @Test
    public void rejectQueryByPolicyNotAccessibleException() {
        final String policyId = "not:accessible";
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(newThingWithPolicyId(policyId), DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(POLICY_SUDO,
                    PolicyNotAccessibleException.newBuilder(policyId).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(readCommand(), getRef());
            final DittoRuntimeException error = expectMsgClass(ThingNotAccessibleException.class);
            assertThat(error.getMessage()).contains(policyId);
            assertThat(error.getDescription().orElse("")).contains(policyId);
        }};
    }

    @Test
    public void rejectUpdateByPolicyNotAccessibleException() {
        final String policyId = "not:accessible";
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(newThingWithPolicyId(policyId), DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(POLICY_SUDO,
                    PolicyNotAccessibleException.newBuilder(policyId).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            underTest.tell(writeCommand(), getRef());
            final DittoRuntimeException error = expectMsgClass(ThingNotModifiableException.class);
            assertThat(error.getMessage()).contains(policyId);
            assertThat(error.getDescription().orElse("")).contains(policyId);
        }};
    }

    @Test
    public void rejectCreateByOwnAcl() {
        final AclEntry aclEntry =
                AclEntry.newInstance(AuthorizationSubject.newInstance("not-subject"),
                        READ, WRITE, ADMINISTRATE);

        final Thing thingWithEmptyAcl = newThing()
                .setPermissions(aclEntry)
                .build();

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO,
                    ThingNotAccessibleException.newBuilder(THING_ID).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thingWithEmptyAcl, null, headers(V_1));
            underTest.tell(createThing, getRef());
            expectMsgClass(ThingNotModifiableException.class);
        }};
    }

    @Test
    public void rejectCreateByOwnPolicy() {
        final String policyId = "empty:policy";
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel("dummy")
                .setSubject(GOOGLE, "not-subject")
                .setGrantedPermissions(PoliciesResourceType.policyResource("/"),
                        READ.name(), WRITE.name())
                .build();
        final Thing thing = newThing().build();

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO,
                    ThingNotAccessibleException.newBuilder(THING_ID).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thing, policy.toJson(), headers(V_2));
            underTest.tell(createThing, getRef());
            expectMsgClass(ThingNotModifiableException.class);
        }};

    }

    @Test
    public void acceptByAcl() {
        final JsonObject thingWithAcl = newThing()
                .setPermissions(
                        AclEntry.newInstance(SUBJECT, READ, WRITE, ADMINISTRATE))
                .build()
                .toJson(V_1, FieldType.all());
        final SudoRetrieveThingResponse response =
                SudoRetrieveThingResponse.of(thingWithAcl, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, response);

            final ActorRef underTest = newEnforcerActor(getRef());
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
    public void acceptByPolicy() {
        final String policyId = "policy:id";
        final JsonObject thingWithPolicy = newThingWithPolicyId(policyId);
        final JsonObject policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT.getId())
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        READ.name(),
                        WRITE.name())
                .build()
                .toJson(FieldType.all());
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(thingWithPolicy, DittoHeaders.empty());
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO, sudoRetrieveThingResponse);
            mockEntitiesActorInstance.setReply(POLICY_SUDO, sudoRetrievePolicyResponse);

            final ActorRef underTest = newEnforcerActor(getRef());

            final ThingCommand write = writeCommand();
            mockEntitiesActorInstance.setReply(write);
            underTest.tell(write, getRef());
            assertThat(expectMsgClass(write.getClass()).getId()).isEqualTo(write.getId());

            final ThingCommand read = readCommand();
            final RetrieveThingResponse retrieveThingResponse =
                    RetrieveThingResponse.of(THING_ID, JsonFactory.newObject(), DittoHeaders.empty());
            mockEntitiesActorInstance.setReply(retrieveThingResponse);
            underTest.tell(read, getRef());
            assertThat(expectMsgClass(retrieveThingResponse.getClass()).getId()).isEqualTo(read.getId());
        }};
    }

    @Test
    public void acceptCreateByOwnAcl() {
        final Thing thing = newThing()
                .setPermissions(AclEntry.newInstance(SUBJECT, READ, WRITE, ADMINISTRATE))
                .build();
        final CreateThing createThing = CreateThing.of(thing, null, headers(V_1));

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO,
                    ThingNotAccessibleException.newBuilder(THING_ID).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            mockEntitiesActorInstance.setReply(createThing);
            underTest.tell(createThing, getRef());
            final CreateThing filteredCreateThing = expectMsgClass(CreateThing.class);
            assertThat(filteredCreateThing.getThing()).isEqualTo(thing);
        }};
    }

    @Test
    public void acceptCreateByOwnPolicy() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(THING_ID)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, SUBJECT.getId())
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()), WRITE.name())
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()), WRITE.name())
                .build();
        final Thing thing = newThing().build();

        new TestKit(system) {{
            mockEntitiesActorInstance.setReply(THING_SUDO,
                    ThingNotAccessibleException.newBuilder(THING_ID).build());

            final ActorRef underTest = newEnforcerActor(getRef());
            final CreateThing createThing = CreateThing.of(thing, policy.toJson(), headers(V_2));
            mockEntitiesActorInstance.setReply(CreateThingResponse.of(thing, headers(V_2)));
            mockEntitiesActorInstance.setReply(CreatePolicyResponse.of(THING_ID, policy, headers(V_2)));
            underTest.tell(createThing, getRef());
            final CreateThingResponse expectedCreateThingResponse = expectMsgClass(CreateThingResponse.class);
            assertThat(expectedCreateThingResponse.getThingCreated().orElse(null)).isEqualTo(thing);
        }};

    }

    private ActorRef newEnforcerActor(final ActorRef testActorRef) {
        return TestSetup.newEnforcerActor(system, testActorRef, mockEntitiesActor);
    }

    private static JsonObject newThingWithPolicyId(final String policyId) {
        return newThing()
                .setPolicyId(policyId)
                .build()
                .toJson(V_2, FieldType.all());
    }

    private static DittoHeaders headers(final JsonSchemaVersion schemaVersion) {
        return DittoHeaders.newBuilder()
                .authorizationSubjects(SUBJECT.getId(), String.format("%s:%s", GOOGLE, SUBJECT))
                .schemaVersion(schemaVersion)
                .build();
    }

    private static ThingBuilder.FromScratch newThing() {
        return ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(1L);
    }

    private static ThingCommand readCommand() {
        return RetrieveThing.of(THING_ID, headers(V_2));
    }

    private static ThingCommand writeCommand() {
        return ModifyFeature.of(THING_ID, Feature.newBuilder().withId("x").build(), headers(V_2));
    }

}
