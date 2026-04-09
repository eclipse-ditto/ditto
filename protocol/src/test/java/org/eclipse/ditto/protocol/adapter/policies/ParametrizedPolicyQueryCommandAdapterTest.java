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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.ParametrizedCommandAdapterTest;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TestConstants.Policies;
import org.eclipse.ditto.protocol.TestConstants.Policies.TopicPaths;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntries;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntryAllowedImportAdditions;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntryImportable;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntryNamespaces;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportEntries;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportEntriesAdditions;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportEntryAddition;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResource;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResources;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubject;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjectAlias;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjectAliases;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjects;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link PolicyQueryCommandAdapter}.
 */
@RunWith(Parameterized.class)
public final class ParametrizedPolicyQueryCommandAdapterTest
        extends ParametrizedCommandAdapterTest<PolicyQueryCommand<?>> implements ProtocolAdapterTest {

    @Parameterized.Parameters(name = "{0}: adaptable={1}, command={2}")
    public static Collection<Object[]> data() {
        return toObjects(retrievePolicy(),
                retrievePolicyEntry(),
                retrievePolicyEntries(),
                retrieveResource(),
                retrieveResources(),
                retrieveSubject(),
                retrieveSubjects(),
                retrievePolicyEntryAllowedImportAdditions(),
                retrievePolicyEntryNamespaces(),
                retrievePolicyEntryImportable(),
                retrievePolicyImportEntries(),
                retrievePolicyImportEntriesAdditions(),
                retrievePolicyImportEntryAddition(),
                retrieveSubjectAliases(),
                retrieveSubjectAlias());
    }

    private PolicyQueryCommandAdapter underTest;

    @Before
    public void setUp() {
        underTest = PolicyQueryCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Override
    protected AbstractPolicyAdapter<PolicyQueryCommand<?>> underTest() {
        return underTest;
    }

    @Override
    protected TopicPath.Channel defaultChannel() {
        return TopicPath.Channel.NONE;
    }

    private static TestParameter<PolicyQueryCommand<?>> retrievePolicy() {
        final RetrievePolicy command = RetrievePolicy.of(Policies.POLICY_ID, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE, EMPTY_PATH);
        return TestParameter.of("retrievePolicy", adaptable, command);
    }

    private static TestParameter<PolicyQueryCommand<?>> retrievePolicyEntry() {
        final RetrievePolicyEntry retrievePolicyEntry =
                RetrievePolicyEntry.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.HEADERS);
        final Adaptable adaptable =
                TestConstants.adaptable(TopicPaths.RETRIEVE, entriesPath(Policies.POLICY_ENTRY_LABEL));
        return TestParameter.of("retrievePolicyEntry", adaptable, retrievePolicyEntry);
    }

    private static TestParameter<PolicyQueryCommand<?>> retrievePolicyEntries() {
        final RetrievePolicyEntries command = RetrievePolicyEntries.of(Policies.POLICY_ID, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE, JsonPointer.of("entries"));
        return TestParameter.of("retrievePolicyEntries", adaptable, command);
    }

    private static TestParameter<PolicyQueryCommand<?>> retrieveResource() {
        final RetrieveResource command =
                RetrieveResource.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL,
                        Policies.RESOURCE1.getResourceKey(),
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE,
                resourcesPath(Policies.POLICY_ENTRY_LABEL, Policies.RESOURCE1.getResourceKey()));
        return TestParameter.of("retrieveResource", adaptable, command);
    }

    private static TestParameter<PolicyQueryCommand<?>> retrieveResources() {
        final RetrieveResources command =
                RetrieveResources.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.HEADERS);
        final Adaptable adaptable =
                TestConstants.adaptable(TopicPaths.RETRIEVE, resourcesPath(Policies.POLICY_ENTRY_LABEL));
        return TestParameter.of("retrieveResources", adaptable, command);
    }

    private static TestParameter<PolicyQueryCommand<?>> retrieveSubject() {
        final RetrieveSubject command =
                RetrieveSubject.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.SUBJECT1.getId(),
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE,
                subjectsPath(Policies.POLICY_ENTRY_LABEL, Policies.SUBJECT1.getId()));
        return TestParameter.of("retrieveSubject", adaptable, command);
    }

    private static TestParameter<PolicyQueryCommand<?>> retrieveSubjects() {
        final RetrieveSubjects command =
                RetrieveSubjects.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.HEADERS);
        final Adaptable adaptable =
                TestConstants.adaptable(TopicPaths.RETRIEVE, subjectsPath(Policies.POLICY_ENTRY_LABEL));
        return TestParameter.of("retrieveSubjects", adaptable, command);
    }

    private static TestParameter<PolicyQueryCommand<?>> retrievePolicyEntryAllowedImportAdditions() {
        final RetrievePolicyEntryAllowedImportAdditions command =
                RetrievePolicyEntryAllowedImportAdditions.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL,
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE,
                JsonPointer.of("/entries/" + Policies.POLICY_ENTRY_LABEL + "/allowedImportAdditions"));
        return TestParameter.of("retrievePolicyEntryAllowedImportAdditions", adaptable, command);
    }

    private static TestParameter<PolicyQueryCommand<?>> retrievePolicyEntryNamespaces() {
        final RetrievePolicyEntryNamespaces command =
                RetrievePolicyEntryNamespaces.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL,
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE,
                JsonPointer.of("/entries/" + Policies.POLICY_ENTRY_LABEL + "/namespaces"));
        return TestParameter.of("retrievePolicyEntryNamespaces", adaptable, command);
    }

    private static TestParameter<PolicyQueryCommand<?>> retrievePolicyEntryImportable() {
        final RetrievePolicyEntryImportable command =
                RetrievePolicyEntryImportable.of(Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL,
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE,
                JsonPointer.of("/entries/" + Policies.POLICY_ENTRY_LABEL + "/importable"));
        return TestParameter.of("retrievePolicyEntryImportable", adaptable, command);
    }

    private static TestParameter<PolicyQueryCommand<?>> retrievePolicyImportEntries() {
        final RetrievePolicyImportEntries command =
                RetrievePolicyImportEntries.of(Policies.POLICY_ID, Policies.IMPORTED_POLICY_ID,
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE,
                JsonPointer.of("/imports/" + Policies.IMPORTED_POLICY_ID + "/entries"));
        return TestParameter.of("retrievePolicyImportEntries", adaptable, command);
    }

    private static TestParameter<PolicyQueryCommand<?>> retrievePolicyImportEntriesAdditions() {
        final RetrievePolicyImportEntriesAdditions command =
                RetrievePolicyImportEntriesAdditions.of(Policies.POLICY_ID, Policies.IMPORTED_POLICY_ID,
                        Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE,
                JsonPointer.of("/imports/" + Policies.IMPORTED_POLICY_ID + "/entriesAdditions"));
        return TestParameter.of("retrievePolicyImportEntriesAdditions", adaptable, command);
    }

    private static TestParameter<PolicyQueryCommand<?>> retrievePolicyImportEntryAddition() {
        final RetrievePolicyImportEntryAddition command =
                RetrievePolicyImportEntryAddition.of(Policies.POLICY_ID, Policies.IMPORTED_POLICY_ID,
                        Policies.POLICY_ENTRY_LABEL, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE,
                JsonPointer.of("/imports/" + Policies.IMPORTED_POLICY_ID + "/entriesAdditions/" +
                        Policies.POLICY_ENTRY_LABEL));
        return TestParameter.of("retrievePolicyImportEntryAddition", adaptable, command);
    }

    private static TestParameter<PolicyQueryCommand<?>> retrieveSubjectAliases() {
        final RetrieveSubjectAliases command =
                RetrieveSubjectAliases.of(Policies.POLICY_ID, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE,
                JsonPointer.of("/subjectAliases"));
        return TestParameter.of("retrieveSubjectAliases", adaptable, command);
    }

    private static TestParameter<PolicyQueryCommand<?>> retrieveSubjectAlias() {
        final RetrieveSubjectAlias command =
                RetrieveSubjectAlias.of(Policies.POLICY_ID, Policies.SUBJECT_ALIAS_LABEL, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE,
                JsonPointer.of("/subjectAliases/" + Policies.SUBJECT_ALIAS_LABEL));
        return TestParameter.of("retrieveSubjectAlias", adaptable, command);
    }

}
