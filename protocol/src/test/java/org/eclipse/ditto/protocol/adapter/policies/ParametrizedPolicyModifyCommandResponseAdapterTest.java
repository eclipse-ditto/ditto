/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter.policies;

import java.util.Collection;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ParametrizedCommandAdapterTest;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TestConstants.Policies;
import org.eclipse.ditto.protocol.TestConstants.Policies.TopicPaths;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyEntryResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteResourceResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubjectResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntriesResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntryResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResourceResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResourcesResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjectsResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommandResponse;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link PolicyModifyCommandResponseAdapter}.
 */
@RunWith(Parameterized.class)
public final class ParametrizedPolicyModifyCommandResponseAdapterTest
        extends ParametrizedCommandAdapterTest<PolicyModifyCommandResponse<?>> implements ProtocolAdapterTest {

    @Parameterized.Parameters(name = "{0}: adaptable={1}, response={2}")
    public static Collection<Object[]> data() {
        return toObjects(createPolicyResponse(),
                modifyPolicyCreatedResponse(),
                modifyPolicyModifiedResponse(),
                deletePolicyResponse(),
                modifyPolicyEntryCreatedResponse(),
                modifyPolicyEntryModifiedResponse(),
                deletePolicyEntryResponse(),
                modifyPolicyEntriesResponse(),
                modifyResourceCreatedResponse(),
                modifyResourceModifiedResponse(),
                deleteResourceResponse(),
                modifyResourcesResponse(),
                modifySubjectCreatedResponse(),
                modifySubjectModifiedResponse(),
                deleteSubjectResponse(),
                modifySubjectsResponse());
    }

    private PolicyModifyCommandResponseAdapter underTest;

    @Before
    public void setUp() {
        underTest = PolicyModifyCommandResponseAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Override
    protected PolicyModifyCommandResponseAdapter underTest() {
        return underTest;
    }

    @Override
    protected TopicPath.Channel defaultChannel() {
        return TopicPath.Channel.NONE;
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> createPolicyResponse() {
        final CreatePolicyResponse response = CreatePolicyResponse.of(Policies.POLICY_ID, Policies.POLICY,
                Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.CREATE, EMPTY_PATH,
                Policies.POLICY.toJson(FieldType.notHidden()), HttpStatus.CREATED);
        return TestParameter.of("createPolicyResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> modifyPolicyCreatedResponse() {
        final ModifyPolicyResponse response =
                ModifyPolicyResponse.created(Policies.POLICY_ID, Policies.POLICY, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY, EMPTY_PATH,
                Policies.POLICY.toJson(FieldType.notHidden()), HttpStatus.CREATED);
        return TestParameter.of("modifyPolicyCreatedResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> modifyPolicyModifiedResponse() {
        final ModifyPolicyResponse response =
                ModifyPolicyResponse.modified(Policies.POLICY_ID, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY, EMPTY_PATH, HttpStatus.NO_CONTENT);
        return TestParameter.of("modifyPolicyModifiedResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> deletePolicyResponse() {
        final DeletePolicyResponse response = DeletePolicyResponse.of(Policies.POLICY_ID, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.DELETE, EMPTY_PATH, HttpStatus.NO_CONTENT);
        return TestParameter.of("deletePolicyResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> modifyPolicyEntryCreatedResponse() {
        final ModifyPolicyEntryResponse
                response =
                ModifyPolicyEntryResponse.created(Policies.POLICY_ID, Policies.POLICY_ENTRY, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY,
                entriesPath(Policies.POLICY_ENTRY_LABEL),
                Policies.POLICY_ENTRY.toJson(FieldType.notHidden()), HttpStatus.CREATED);
        return TestParameter.of("modifyPolicyEntryCreatedResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> modifyPolicyEntryModifiedResponse() {
        final ModifyPolicyEntryResponse
                response = ModifyPolicyEntryResponse.modified(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL,
                Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY,
                entriesPath(Policies.POLICY_ENTRY_LABEL), HttpStatus.NO_CONTENT);
        return TestParameter.of("modifyPolicyEntryModifiedResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> deletePolicyEntryResponse() {
        final DeletePolicyEntryResponse response =
                DeletePolicyEntryResponse.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL2, Policies.HEADERS);
        final Adaptable adaptable =
                TestConstants.adaptable(TopicPaths.DELETE, entriesPath(Policies.POLICY_ENTRY_LABEL2),
                        HttpStatus.NO_CONTENT);
        return TestParameter.of("deletePolicyEntryResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> modifyPolicyEntriesResponse() {
        final ModifyPolicyEntriesResponse response =
                ModifyPolicyEntriesResponse.of(Policies.POLICY_ID, Policies.HEADERS);
        final Adaptable adaptable =
                TestConstants.adaptable(TopicPaths.MODIFY, entriesPath(), HttpStatus.NO_CONTENT);
        return TestParameter.of("modifyPolicyEntriesResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> modifyResourceCreatedResponse() {
        final ModifyResourceResponse response =
                ModifyResourceResponse.created(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.RESOURCE1,
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY,
                resourcesPath(Policies.POLICY_ENTRY_LABEL, Policies.RESOURCE1.getResourceKey()),
                Policies.RESOURCE1.toJson(FieldType.notHidden()), HttpStatus.CREATED);
        return TestParameter.of("modifyResourceCreatedResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> modifyResourceModifiedResponse() {
        final ModifyResourceResponse response =
                ModifyResourceResponse.modified(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL,
                        Policies.RESOURCE1.getResourceKey(), Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY,
                resourcesPath(Policies.POLICY_ENTRY_LABEL, Policies.RESOURCE1.getResourceKey()),
                HttpStatus.NO_CONTENT);
        return TestParameter.of("modifyResourceModifiedResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> deleteResourceResponse() {
        final DeleteResourceResponse response =
                DeleteResourceResponse.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL,
                        Policies.RESOURCE1.getResourceKey(),
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.DELETE,
                resourcesPath(Policies.POLICY_ENTRY_LABEL, Policies.RESOURCE1.getResourceKey()),
                HttpStatus.NO_CONTENT);
        return TestParameter.of("deleteResourceResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> modifyResourcesResponse() {
        final ModifyResourcesResponse response =
                ModifyResourcesResponse.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY,
                resourcesPath(Policies.POLICY_ENTRY_LABEL), HttpStatus.NO_CONTENT);
        return TestParameter.of("modifyResourcesResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> modifySubjectCreatedResponse() {
        final ModifySubjectResponse response = ModifySubjectResponse.created(Policies.POLICY_ID,
                Policies.POLICY_ENTRY_LABEL,
                Policies.SUBJECT1,
                Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY,
                subjectsPath(Policies.POLICY_ENTRY_LABEL, Policies.SUBJECT1.getId()),
                Policies.SUBJECT1.toJson(FieldType.notHidden()), HttpStatus.CREATED);
        return TestParameter.of("modifySubjectCreatedResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> modifySubjectModifiedResponse() {
        final ModifySubjectResponse response = ModifySubjectResponse.modified(Policies.POLICY_ID,
                Policies.POLICY_ENTRY_LABEL, Policies.SUBJECT_ID1, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY,
                subjectsPath(Policies.POLICY_ENTRY_LABEL, Policies.SUBJECT1.getId()), HttpStatus.NO_CONTENT);
        return TestParameter.of("modifySubjectModifiedResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> modifySubjectsResponse() {
        final ModifySubjectsResponse response =
                ModifySubjectsResponse.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL,
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY,
                subjectsPath(Policies.POLICY_ENTRY_LABEL), HttpStatus.NO_CONTENT);
        return TestParameter.of("modifySubjectsResponse", adaptable, response);
    }

    private static TestParameter<PolicyModifyCommandResponse<?>> deleteSubjectResponse() {
        final DeleteSubjectResponse response = DeleteSubjectResponse.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL,
                Policies.SUBJECT1.getId(),
                Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.DELETE,
                subjectsPath(Policies.POLICY_ENTRY_LABEL, Policies.SUBJECT1.getId()), HttpStatus.NO_CONTENT);
        return TestParameter.of("deleteSubjectResponse", adaptable, response);
    }

}
