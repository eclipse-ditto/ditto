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
package org.eclipse.ditto.services.policies.persistence.actors.policy;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.eclipse.ditto.services.policies.persistence.TestConstants.Policy.SUBJECT_TYPE;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.model.policies.assertions.DittoPolicyAssertions;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.policies.persistence.TestConstants;
import org.eclipse.ditto.services.policies.persistence.actors.PersistenceActorTestBase;
import org.eclipse.ditto.services.policies.persistence.serializer.PolicyMongoSnapshotAdapter;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyEntryModificationInvalidException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyEntryNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.ResourceNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.SubjectNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntry;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteResource;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteResourceResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubject;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubjectResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntry;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResource;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResourceResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubject;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjectResponse;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntry;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResource;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResourceResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubject;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjectResponse;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

/**
 * Unit test for the {@link PolicyPersistenceActor}.
 */
public final class PolicyPersistenceActorTest extends PersistenceActorTestBase {

    @Before
    public void setup()
    {
        setUpBase();
    }
    /** */
    @Test
    public void tryToRetrievePolicyWhichWasNotYetCreated() {
        final String policyId = "test.ns:23420815";
        final PolicyCommand retrievePolicyCommand = RetrievePolicy.of(policyId, dittoHeadersMockV2);

        new JavaTestKit(actorSystem) {
            {
                final ActorRef policyPersistenceActor = createPersistenceActorFor(policyId);
                policyPersistenceActor.tell(retrievePolicyCommand, getRef());
                expectMsgClass(PolicyNotAccessibleException.class);
            }
        };
    }

    /**
     * The PolicyPersistenceActor is created with a Policy ID. Any command it receives which belongs to a Policy with a
     * different ID should lead to an exception as the command was obviously sent to the wrong PolicyPersistenceActor.
     */
    @Test
    public void tryToCreatePolicyWithDifferentPolicyId() {
        final String policyIdOfActor = "test.ns:23420815";
        final Policy policy = createPolicyWithRandomId();
        final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);

        final PolicyMongoSnapshotAdapter snapshotAdapter = new PolicyMongoSnapshotAdapter();
        final Props props = PolicyPersistenceActor.props(policyIdOfActor, snapshotAdapter, pubSubMediator,
                policyCacheFacade);
        final TestActorRef<PolicyPersistenceActor> underTest = TestActorRef.create(actorSystem, props);
        final PolicyPersistenceActor policyPersistenceActor = underTest.underlyingActor();
        final PartialFunction<Object, BoxedUnit> receiveCommand = policyPersistenceActor.receiveCommand();

        try {
            receiveCommand.apply(createPolicyCommand);
            fail("Expected IllegalArgumentException to be thrown.");
        } catch (final Exception e) {
            assertThat(e).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** */
    @Test
    public void createPolicy() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);
            }
        };
    }

    /** */
    @Test
    public void modifyPolicy() {
        final Policy policy = createPolicyWithRandomId();
        final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);

        final Policy modifiedPolicy = policy.setEntry(
                PoliciesModelFactory.newPolicyEntry(Label.of("anotherOne"), POLICY_SUBJECTS,
                        POLICY_RESOURCES_ALL));
        final ModifyPolicy modifyPolicyCommand =
                ModifyPolicy.of(policy.getId().get(), modifiedPolicy, dittoHeadersMockV2);

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(policy);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicyResponse = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicyResponse.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                underTest.tell(modifyPolicyCommand, getRef());
                expectMsgEquals(ModifyPolicyResponse.modified(policy.getId().get(), dittoHeadersMockV2));
            }
        };
    }

    /** */
    @Test
    public void retrievePolicy() {
        final Policy policy = createPolicyWithRandomId();
        final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
        final PolicyCommand retrievePolicyCommand =
                RetrievePolicy.of(policy.getId().orElse(null), dittoHeadersMockV2);
        final PolicyQueryCommandResponse expectedResponse =
                RetrievePolicyResponse.of(policy.getId().orElse(null), policy, retrievePolicyCommand.getDittoHeaders());

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(policy);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                underTest.tell(retrievePolicyCommand, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    /** */
    @Test
    public void sudoRetrievePolicy() {
        final Policy policy = createPolicyWithRandomId();
        final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);

        final SudoRetrievePolicy sudoRetrievePolicyCommand =
                SudoRetrievePolicy.of(policy.getId().orElse(null), dittoHeadersMockV2);

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(policy);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                underTest.tell(sudoRetrievePolicyCommand, getRef());
                expectMsgClass(SudoRetrievePolicyResponse.class);
            }
        };
    }

    /** */
    @Test
    public void deletePolicy() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final DeletePolicy deletePolicy = DeletePolicy.of(policy.getId().orElse(null), dittoHeadersMockV2);
                policyPersistenceActor.tell(deletePolicy, getRef());
                expectMsgEquals(DeletePolicyResponse.of(policy.getId().orElse(null), dittoHeadersMockV2));
            }
        };
    }

    /** */
    @Test
    public void createPolicyEntry() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final PolicyEntry policyEntryToAdd =
                        PoliciesModelFactory.newPolicyEntry(Label.of("anotherLabel"),
                                POLICY_SUBJECTS, POLICY_RESOURCES_ALL);

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final String policyId = policy.getId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ModifyPolicyEntry modifyPolicyEntry =
                        ModifyPolicyEntry.of(policyId, policyEntryToAdd, headersMockWithOtherAuth);
                underTest.tell(modifyPolicyEntry, getRef());
                expectMsgEquals(
                        ModifyPolicyEntryResponse.created(policyId, policyEntryToAdd, headersMockWithOtherAuth));

                final RetrievePolicyEntry retrievePolicyEntry =
                        RetrievePolicyEntry.of(policyId, policyEntryToAdd.getLabel(), headersMockWithOtherAuth);
                final RetrievePolicyEntryResponse expectedResponse =
                        RetrievePolicyEntryResponse.of(policyId, policyEntryToAdd, headersMockWithOtherAuth);
                underTest.tell(retrievePolicyEntry, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    /** */
    @Test
    public void modifyPolicyEntry() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Subject newSubject =
                        Subject.newInstance(SubjectIssuer.GOOGLE, "anotherOne");
                final PolicyEntry policyEntryToModify = PoliciesModelFactory.newPolicyEntry(POLICY_LABEL,
                        Subjects.newInstance(POLICY_SUBJECT, newSubject), POLICY_RESOURCES_ALL);

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final String policyId = policy.getId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ModifyPolicyEntry modifyPolicyEntry =
                        ModifyPolicyEntry.of(policyId, policyEntryToModify, headersMockWithOtherAuth);
                underTest.tell(modifyPolicyEntry, getRef());
                expectMsgEquals(ModifyPolicyEntryResponse.modified(policyId, headersMockWithOtherAuth));

                final RetrievePolicyEntry retrievePolicyEntry =
                        RetrievePolicyEntry.of(policyId, policyEntryToModify.getLabel(), headersMockWithOtherAuth);
                final RetrievePolicyEntryResponse expectedResponse =
                        RetrievePolicyEntryResponse.of(policyId, policyEntryToModify, headersMockWithOtherAuth);
                underTest.tell(retrievePolicyEntry, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    /** */
    @Test
    public void tryToModifyPolicyEntryWithInvalidPermissions() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Subject newSubject =
                        Subject.newInstance(SubjectIssuer.GOOGLE, "anotherOne");

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final String policyId = policy.getId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final PolicyEntry policyEntryToModify = PoliciesModelFactory.newPolicyEntry(POLICY_LABEL,
                        Subjects.newInstance(POLICY_SUBJECT, newSubject), POLICY_RESOURCES_READ);

                final ModifyPolicyEntry modifyPolicyEntry =
                        ModifyPolicyEntry.of(policyId, policyEntryToModify, headersMockWithOtherAuth);
                underTest.tell(modifyPolicyEntry, getRef());
                expectMsgClass(PolicyEntryModificationInvalidException.class);
            }
        };
    }

    /** */
    @Test
    public void removePolicyEntry() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final String policyId = policy.getId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final DeletePolicyEntry deletePolicyEntry =
                        DeletePolicyEntry.of(policyId, ANOTHER_POLICY_LABEL, headersMockWithOtherAuth);
                underTest.tell(deletePolicyEntry, getRef());
                expectMsgEquals(DeletePolicyEntryResponse.of(policyId, ANOTHER_POLICY_LABEL, headersMockWithOtherAuth));

                final RetrievePolicyEntry retrievePolicyEntry =
                        RetrievePolicyEntry.of(policyId, ANOTHER_POLICY_LABEL, headersMockWithOtherAuth);
                final PolicyEntryNotAccessibleException expectedResponse = PolicyEntryNotAccessibleException
                        .newBuilder(POLICY_ID, ANOTHER_POLICY_LABEL.toString())
                        .dittoHeaders(headersMockWithOtherAuth)
                        .build();
                underTest.tell(retrievePolicyEntry, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    /** */
    @Test
    public void tryToRemoveLastPolicyEntry() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final String policyId = policy.getId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final DeletePolicyEntry deletePolicyEntry =
                        DeletePolicyEntry.of(policyId, POLICY_LABEL, headersMockWithOtherAuth);
                underTest.tell(deletePolicyEntry, getRef());
                expectMsgClass(PolicyEntryModificationInvalidException.class);
            }
        };
    }

    /** */
    @Test
    public void createResource() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Resource resourceToAdd = Resource.newInstance(PoliciesResourceType.policyResource(
                        "/attributes"), EffectedPermissions.newInstance(PoliciesModelFactory.noPermissions(),
                        TestConstants.Policy.PERMISSIONS_ALL));

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final String policyId = policy.getId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ModifyResource modifyResource =
                        ModifyResource.of(policyId, POLICY_LABEL, resourceToAdd, headersMockWithOtherAuth);
                underTest.tell(modifyResource, getRef());
                expectMsgEquals(ModifyResourceResponse.created(policyId, POLICY_LABEL, resourceToAdd,
                        headersMockWithOtherAuth));

                final RetrieveResource retrieveResource =
                        RetrieveResource.of(policyId, POLICY_LABEL, resourceToAdd.getResourceKey(),
                                headersMockWithOtherAuth);
                final RetrieveResourceResponse expectedResponse =
                        RetrieveResourceResponse.of(policyId, POLICY_LABEL, resourceToAdd, headersMockWithOtherAuth);
                underTest.tell(retrieveResource, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    /** */
    @Test
    public void modifyResource() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Resource resourceToModify = Resource.newInstance(PoliciesResourceType.policyResource(
                        POLICY_RESOURCE_PATH), EffectedPermissions.newInstance(TestConstants.Policy.PERMISSIONS_ALL,
                        PoliciesModelFactory.noPermissions()));

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final String policyId = policy.getId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ModifyResource modifyResource =
                        ModifyResource.of(policyId, ANOTHER_POLICY_LABEL, resourceToModify, headersMockWithOtherAuth);
                underTest.tell(modifyResource, getRef());
                expectMsgEquals(
                        ModifyResourceResponse.modified(policyId, ANOTHER_POLICY_LABEL, headersMockWithOtherAuth));

                final RetrieveResource retrieveResource = RetrieveResource.of(policyId, ANOTHER_POLICY_LABEL,
                        resourceToModify.getResourceKey(), headersMockWithOtherAuth);
                final RetrieveResourceResponse expectedResponse =
                        RetrieveResourceResponse.of(policyId, ANOTHER_POLICY_LABEL, resourceToModify,
                                headersMockWithOtherAuth);
                underTest.tell(retrieveResource, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    /** */
    @Test
    public void tryToModifyResource() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Resource resourceToModify = Resource.newInstance(
                        PoliciesResourceType.policyResource(POLICY_RESOURCE_PATH),
                        EffectedPermissions.newInstance(PoliciesModelFactory.noPermissions(),
                                TestConstants.Policy.PERMISSIONS_ALL));

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final String policyId = policy.getId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ModifyResource modifyResource =
                        ModifyResource.of(policyId, ANOTHER_POLICY_LABEL, resourceToModify, headersMockWithOtherAuth);
                underTest.tell(modifyResource, getRef());
                expectMsgClass(PolicyEntryModificationInvalidException.class);
            }
        };
    }

    /** */
    @Test
    public void removeResource() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final String policyId = policy.getId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final DeleteResource deleteResource =
                        DeleteResource.of(policyId, ANOTHER_POLICY_LABEL,
                                PoliciesResourceType.policyResource(POLICY_RESOURCE_PATH),
                                headersMockWithOtherAuth);
                underTest.tell(deleteResource, getRef());
                expectMsgEquals(DeleteResourceResponse.of(policyId, ANOTHER_POLICY_LABEL,
                        PoliciesResourceType.policyResource(POLICY_RESOURCE_PATH), headersMockWithOtherAuth));

                final RetrieveResource retrieveResource =
                        RetrieveResource.of(policyId, ANOTHER_POLICY_LABEL,
                                PoliciesResourceType.policyResource(POLICY_RESOURCE_PATH),
                                headersMockWithOtherAuth);
                final ResourceNotAccessibleException expectedResponse = ResourceNotAccessibleException
                        .newBuilder(POLICY_ID, ANOTHER_POLICY_LABEL.toString(), POLICY_RESOURCE_PATH.toString())
                        .dittoHeaders(headersMockWithOtherAuth).build();
                underTest.tell(retrieveResource, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    /** */
    @Test
    public void createSubject() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Subject subjectToAdd =
                        Subject.newInstance(SubjectIssuer.GOOGLE, "anotherSubjectId");

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final String policyId = policy.getId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ModifySubject modifySubject =
                        ModifySubject.of(policyId, POLICY_LABEL, subjectToAdd, headersMockWithOtherAuth);
                underTest.tell(modifySubject, getRef());
                expectMsgEquals(
                        ModifySubjectResponse.created(policyId, POLICY_LABEL, subjectToAdd, headersMockWithOtherAuth));

                final RetrieveSubject retrieveSubject =
                        RetrieveSubject.of(policyId, POLICY_LABEL, subjectToAdd.getId(), headersMockWithOtherAuth);
                final RetrieveSubjectResponse expectedResponse =
                        RetrieveSubjectResponse.of(policyId, POLICY_LABEL, subjectToAdd, headersMockWithOtherAuth);
                underTest.tell(retrieveSubject, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    /** */
    @Test
    public void modifySubject() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final Subject subjectToModify =
                        Subject.newInstance(POLICY_SUBJECT_ID, SUBJECT_TYPE);

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final String policyId = policy.getId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ModifySubject modifySubject =
                        ModifySubject.of(policyId, POLICY_LABEL, subjectToModify, headersMockWithOtherAuth);
                underTest.tell(modifySubject, getRef());
                expectMsgEquals(ModifySubjectResponse.modified(policyId, POLICY_LABEL, headersMockWithOtherAuth));

                final RetrieveSubject retrieveSubject =
                        RetrieveSubject.of(policyId, POLICY_LABEL, subjectToModify.getId(), headersMockWithOtherAuth);
                final RetrieveSubjectResponse expectedResponse =
                        RetrieveSubjectResponse.of(policyId, POLICY_LABEL, subjectToModify, headersMockWithOtherAuth);
                underTest.tell(retrieveSubject, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    /** */
    @Test
    public void removeSubject() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();

                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.LATEST, AUTH_SUBJECT, UNAUTH_SUBJECT);

                final String policyId = policy.getId().orElse(null);
                final ActorRef underTest = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                underTest.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final DeleteSubject deleteSubject =
                        DeleteSubject.of(policyId, ANOTHER_POLICY_LABEL, POLICY_SUBJECT_ID, headersMockWithOtherAuth);
                underTest.tell(deleteSubject, getRef());
                expectMsgEquals(
                        DeleteSubjectResponse.of(policyId, ANOTHER_POLICY_LABEL, POLICY_SUBJECT_ID,
                                headersMockWithOtherAuth));

                final RetrieveSubject retrieveSubject =
                        RetrieveSubject.of(policyId, ANOTHER_POLICY_LABEL, POLICY_SUBJECT_ID, headersMockWithOtherAuth);
                final SubjectNotAccessibleException expectedResponse = SubjectNotAccessibleException
                        .newBuilder(POLICY_ID, ANOTHER_POLICY_LABEL.toString(), POLICY_RESOURCE_PATH.toString())
                        .dittoHeaders(headersMockWithOtherAuth).build();
                underTest.tell(retrieveSubject, getRef());
                expectMsgEquals(expectedResponse);
            }
        };
    }

    /** */
    @Test
    public void recoverPolicyCreated() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final ActorRef policyPersistenceActorRecovered = createPersistenceActorFor(policy);
                final RetrievePolicy retrievePolicy =
                        RetrievePolicy.of(policy.getId().orElse(null), dittoHeadersMockV2);
                final RetrievePolicyResponse response =
                        RetrievePolicyResponse.of(policy.getId().orElse(null), policy, dittoHeadersMockV2);

                policyPersistenceActorRecovered.tell(retrievePolicy, getRef());

                expectMsgEquals(response);

                assertThat(getLastSender()).isEqualTo(policyPersistenceActorRecovered);
            }
        };
    }

    /** */
    @Test
    public void recoverPolicyDeleted() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final DeletePolicy deletePolicy = DeletePolicy.of(policy.getId().orElse(null), dittoHeadersMockV2);
                policyPersistenceActor.tell(deletePolicy, getRef());
                expectMsgEquals(DeletePolicyResponse.of(policy.getId().orElse(null), dittoHeadersMockV2));

                // restart
                final ActorRef policyPersistenceActorRecovered = createPersistenceActorFor(policy);
                final RetrievePolicy retrievePolicy =
                        RetrievePolicy.of(policy.getId().orElse(null), dittoHeadersMockV2);
                policyPersistenceActorRecovered.tell(retrievePolicy, getRef());

                // A deleted Policy cannot be retrieved anymore.
                expectMsgClass(PolicyNotAccessibleException.class);

                assertThat(getLastSender()).isEqualTo(policyPersistenceActorRecovered);
            }
        };
    }

    /** */
    @Test
    public void recoverPolicyEntryModified() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final Subject newSubject =
                        Subject.newInstance(SubjectIssuer.GOOGLE, "anotherOne");
                final Resource newResource =
                        Resource.newInstance(PoliciesResourceType.policyResource("/attributes"), EffectedPermissions
                                .newInstance(PoliciesModelFactory.noPermissions(),
                                        TestConstants.Policy.PERMISSIONS_ALL));
                final PolicyEntry policyEntry = PoliciesModelFactory.newPolicyEntry(Label.of("anotherLabel"),
                        Subjects.newInstance(newSubject), Resources.newInstance(newResource));

                final ModifyPolicyEntry modifyPolicyEntry =
                        ModifyPolicyEntry.of(policy.getId().orElse(null), policyEntry, dittoHeadersMockV2);
                policyPersistenceActor.tell(modifyPolicyEntry, getRef());
                expectMsgEquals(ModifyPolicyEntryResponse.created(policy.getId().orElse(null), policyEntry,
                        dittoHeadersMockV2));

                // restart
                final ActorRef policyPersistenceActorRecovered = createPersistenceActorFor(policy);

                final Policy policyWithUpdatedPolicyEntry = policy.setEntry(policyEntry);
                final RetrievePolicyResponse response =
                        RetrievePolicyResponse.of(policyWithUpdatedPolicyEntry.getId().orElse(null),
                                policyWithUpdatedPolicyEntry, dittoHeadersMockV2);
                final RetrievePolicy retrievePolicy =
                        RetrievePolicy.of(policyWithUpdatedPolicyEntry.getId().orElse(null), dittoHeadersMockV2);
                policyPersistenceActorRecovered.tell(retrievePolicy, getRef());
                expectMsgEquals(response);

                assertThat(getLastSender()).isEqualTo(policyPersistenceActorRecovered);
            }
        };
    }

    /** */
    @Test
    public void recoverPolicyEntryDeleted() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(policy);

                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                final DeletePolicyEntry deletePolicyEntry =
                        DeletePolicyEntry.of(policy.getId().orElse(null), ANOTHER_POLICY_LABEL, dittoHeadersMockV2);
                policyPersistenceActor.tell(deletePolicyEntry, getRef());
                expectMsgEquals(DeletePolicyEntryResponse.of(policy.getId().orElse(null), ANOTHER_POLICY_LABEL,
                        dittoHeadersMockV2));

                // restart
                final ActorRef policyPersistenceActorRecovered = createPersistenceActorFor(policy);

                final RetrievePolicyResponse response =
                        RetrievePolicyResponse.of(policy.getId().orElse(null), policy.removeEntry(ANOTHER_POLICY_LABEL),
                                dittoHeadersMockV2);
                final RetrievePolicy retrievePolicy =
                        RetrievePolicy.of(policy.getId().orElse(null), dittoHeadersMockV2);
                policyPersistenceActorRecovered.tell(retrievePolicy, getRef());
                expectMsgEquals(response);

                assertThat(getLastSender()).isEqualTo(policyPersistenceActorRecovered);
            }
        };
    }

    /** */
    @Test
    public void ensureSequenceNumberCorrectness() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(policy);

                // create the policy - results in sequence number 1
                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                // modify the policy's entries - results in sequence number 2
                final DeletePolicyEntry deletePolicyEntry =
                        DeletePolicyEntry.of(policy.getId().orElse(null), ANOTHER_POLICY_LABEL, dittoHeadersMockV2);
                policyPersistenceActor.tell(deletePolicyEntry, getRef());
                expectMsgEquals(DeletePolicyEntryResponse.of(policy.getId().orElse(null), ANOTHER_POLICY_LABEL,
                        dittoHeadersMockV2));

                // retrieve the policy's sequence number
                final long versionExpected = 2;
                final Policy policyExpected = PoliciesModelFactory.newPolicyBuilder(policy) //
                        .remove(ANOTHER_POLICY_LABEL) //
                        .setRevision(versionExpected) //
                        .build();
                final RetrievePolicy retrievePolicy =
                        RetrievePolicy.of(policy.getId().orElse(null), dittoHeadersMockV2);
                policyPersistenceActor.tell(retrievePolicy, getRef());
                expectMsgEquals(RetrievePolicyResponse.of(policy.getId().orElse(null), policyExpected,
                        retrievePolicy.getDittoHeaders()));
            }
        };
    }

    /** */
    @Test
    public void ensureSequenceNumberCorrectnessAfterRecovery() {
        new JavaTestKit(actorSystem) {
            {
                final Policy policy = createPolicyWithRandomId();
                final ActorRef policyPersistenceActor = createPersistenceActorFor(policy);

                // create the policy - results in sequence number 1
                final CreatePolicy createPolicyCommand = CreatePolicy.of(policy, dittoHeadersMockV2);
                policyPersistenceActor.tell(createPolicyCommand, getRef());
                final CreatePolicyResponse createPolicy1Response = expectMsgClass(CreatePolicyResponse.class);
                DittoPolicyAssertions.assertThat(createPolicy1Response.getPolicyCreated().get())
                        .isEqualEqualToButModified(policy);

                // modify the policy's entries - results in sequence number 2
                final DeletePolicyEntry deletePolicyEntry =
                        DeletePolicyEntry.of(policy.getId().orElse(null), ANOTHER_POLICY_LABEL, dittoHeadersMockV2);
                policyPersistenceActor.tell(deletePolicyEntry, getRef());
                expectMsgEquals(DeletePolicyEntryResponse.of(policy.getId().orElse(null), ANOTHER_POLICY_LABEL,
                        dittoHeadersMockV2));

                // retrieve the policy's sequence number from recovered actor
                final long versionExpected = 2;
                final Policy policyExpected = PoliciesModelFactory.newPolicyBuilder(policy) //
                        .remove(ANOTHER_POLICY_LABEL) //
                        .setRevision(versionExpected) //
                        .build();
                final ActorRef policyPersistenceActorRecovered = createPersistenceActorFor(policy);
                final RetrievePolicy retrievePolicy =
                        RetrievePolicy.of(policy.getId().orElse(null), dittoHeadersMockV2);
                policyPersistenceActorRecovered.tell(retrievePolicy, getRef());

                expectMsgEquals(RetrievePolicyResponse.of(policy.getId().orElse(null), policyExpected,
                        retrievePolicy.getDittoHeaders()));

                assertThat(getLastSender()).isEqualTo(policyPersistenceActorRecovered);
            }
        };
    }

    private ActorRef createPersistenceActorFor(final Policy policy) {
        return createPersistenceActorFor(policy.getId().orElse(null));
    }

    private ActorRef createPersistenceActorFor(final String policyId) {
        final PolicyMongoSnapshotAdapter snapshotAdapter = new PolicyMongoSnapshotAdapter();
        final Props props = PolicyPersistenceActor.props(policyId, snapshotAdapter, pubSubMediator, policyCacheFacade);
        return actorSystem.actorOf(props);
    }
}
