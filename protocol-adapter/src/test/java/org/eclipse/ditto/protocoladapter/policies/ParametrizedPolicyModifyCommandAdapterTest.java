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
package org.eclipse.ditto.protocoladapter.policies;

import java.util.Collection;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.BaseParametrizedCommandAdapterTest;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocoladapter.TestConstants;
import org.eclipse.ditto.protocoladapter.TestConstants.Policies;
import org.eclipse.ditto.protocoladapter.TestConstants.Policies.TopicPaths;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntry;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteResource;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubject;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntries;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntry;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResource;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResources;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubject;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjects;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link PolicyModifyCommandAdapter}.
 */
@RunWith(Parameterized.class)
public final class ParametrizedPolicyModifyCommandAdapterTest
        extends BaseParametrizedCommandAdapterTest<PolicyModifyCommand<?>> implements ProtocolAdapterTest {

    @Parameterized.Parameters(name = "{0}: adaptable={1}, command={2}")
    public static Collection<Object[]> data() {
        return toObjects(createPolicy(),
                modifyPolicy(),
                deletePolicy(),
                modifyPolicyEntry(),
                deletePolicyEntry(),
                modifyPolicyEntries(),
                modifyResource(),
                modifyResources(),
                deleteResource(),
                modifySubject(),
                modifySubjects(),
                deleteSubject());
    }

    private PolicyModifyCommandAdapter underTest;

    @Before
    public void setUp() {
        underTest = PolicyModifyCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Override
    protected AbstractPolicyAdapter<PolicyModifyCommand<?>> underTest() {
        return underTest;
    }

    private static TestParameter<PolicyModifyCommand<?>> createPolicy() {
        final CreatePolicy command = CreatePolicy.of(Policies.POLICY, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.CREATE, EMPTY_PATH,
                Policies.POLICY.toJson(FieldType.regularOrSpecial()));
        return TestParameter.of("createPolicy", adaptable, command);
    }

    private static TestParameter<PolicyModifyCommand<?>> modifyPolicy() {
        final ModifyPolicy command = ModifyPolicy.of(Policies.POLICY_ID, Policies.POLICY, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY, EMPTY_PATH,
                Policies.POLICY.toJson(FieldType.regularOrSpecial()));
        return TestParameter.of("modifyPolicy", adaptable, command);
    }

    private static TestParameter<PolicyModifyCommand<?>> deletePolicy() {
        final DeletePolicy command = DeletePolicy.of(Policies.POLICY_ID, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.DELETE, EMPTY_PATH);
        return TestParameter.of("deletePolicy", adaptable, command);
    }

    private static TestParameter<PolicyModifyCommand<?>> modifyPolicyEntry() {
        final ModifyPolicyEntry modifyPolicyEntry =
                ModifyPolicyEntry.of(Policies.POLICY_ID, Policies.POLICY_ENTRY, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY,
                entriesPath(Policies.POLICY_ENTRY_LABEL),
                Policies.POLICY_ENTRY.toJson(FieldType.regularOrSpecial()));
        return TestParameter.of("modifyPolicyEntry", adaptable, modifyPolicyEntry);
    }

    private static TestParameter<PolicyModifyCommand<?>> deletePolicyEntry() {
        final DeletePolicyEntry deletePolicyEntry =
                DeletePolicyEntry.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL2, Policies.HEADERS);
        final Adaptable adaptable =
                TestConstants.adaptable(TopicPaths.DELETE, entriesPath(Policies.POLICY_ENTRY_LABEL2));
        return TestParameter.of("deletePolicyEntry", adaptable, deletePolicyEntry);
    }

    private static TestParameter<PolicyModifyCommand<?>> modifyPolicyEntries() {
        final ModifyPolicyEntries command =
                ModifyPolicyEntries.of(Policies.POLICY_ID, Policies.POLICY_ENTRIES, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY, entriesPath(),
                fromIterable(Policies.POLICY_ENTRIES,
                        entry -> JsonKey.of(entry.getLabel()),
                        entry -> entry.toJson(FieldType.regularOrSpecial())));
        return TestParameter.of("modifyPolicyEntries", adaptable, command);
    }

    private static TestParameter<PolicyModifyCommand<?>> modifyResource() {
        final ModifyResource command =
                ModifyResource.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.RESOURCE1,
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY,
                resourcesPath(Policies.POLICY_ENTRY_LABEL, Policies.RESOURCE1.getResourceKey()),
                Policies.RESOURCE1.toJson(FieldType.regularOrSpecial()));
        return TestParameter.of("modifyResource", adaptable, command);
    }

    private static TestParameter<PolicyModifyCommand<?>> modifyResources() {
        final ModifyResources command =
                ModifyResources.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL,
                        Resources.newInstance(Policies.RESOURCE1, Policies.RESOURCE2),
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY,
                resourcesPath(Policies.POLICY_ENTRY_LABEL),
                Policies.RESOURCES.toJson(FieldType.regularOrSpecial()));
        return TestParameter.of("modifyResources", adaptable, command);
    }

    private static TestParameter<PolicyModifyCommand<?>> deleteResource() {
        final DeleteResource command =
                DeleteResource.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL,
                        Policies.RESOURCE1.getResourceKey(),
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.DELETE,
                resourcesPath(Policies.POLICY_ENTRY_LABEL, Policies.RESOURCE1.getResourceKey()));
        return TestParameter.of("deleteResource", adaptable, command);
    }

    private static TestParameter<PolicyModifyCommand<?>> modifySubject() {
        final ModifySubject command =
                ModifySubject.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL,
                        Policies.SUBJECT1,
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY,
                subjectsPath(Policies.POLICY_ENTRY_LABEL, Policies.SUBJECT1.getId()),
                Policies.SUBJECT1.toJson(FieldType.regularOrSpecial()));
        return TestParameter.of("modifySubject", adaptable, command);
    }

    private static TestParameter<PolicyModifyCommand<?>> modifySubjects() {
        final ModifySubjects command =
                ModifySubjects.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL,
                        Policies.SUBJECTS,
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.MODIFY,
                subjectsPath(Policies.POLICY_ENTRY_LABEL),
                Policies.SUBJECTS.toJson(FieldType.regularOrSpecial()));
        return TestParameter.of("modifySubjects", adaptable, command);
    }

    private static TestParameter<PolicyModifyCommand<?>> deleteSubject() {
        final DeleteSubject command =
                DeleteSubject.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL,
                        Policies.SUBJECT1.getId(),
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.DELETE,
                subjectsPath(Policies.POLICY_ENTRY_LABEL, Policies.SUBJECT1.getId()));
        return TestParameter.of("deleteSubject", adaptable, command);
    }
}
