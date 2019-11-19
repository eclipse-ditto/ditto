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
package org.eclipse.ditto.protocoladapter;

import java.util.Collection;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.protocoladapter.TestConstants.Policies;
import org.eclipse.ditto.protocoladapter.TestConstants.Policies.TopicPaths;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntriesResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResourceResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResourcesResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjectResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjectsResponse;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link PolicyQueryCommandResponseAdapter}.
 */
@RunWith(Parameterized.class)
public final class ParametrizedPolicyQueryCommandResponseAdapterTest
        extends BaseParametrizedCommandAdapterTest<PolicyQueryCommandResponse> implements ProtocolAdapterTest {

    @Parameterized.Parameters(name = "{0}: adaptable={1}, command={2}")
    public static Collection<Object[]> data() {
        return toObjects(retrievePolicyResponse(),
                retrievePolicyEntryResponse(),
                retrievePolicyEntriesResponse(),
                retrieveResourceResponse(),
                retrieveResourcesResponse(),
                retrieveSubjectResponse(),
                retrieveSubjectsResponse());
    }

    private PolicyQueryCommandResponseAdapter underTest;

    @Before
    public void setUp() {
        underTest = PolicyQueryCommandResponseAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Override
    protected AbstractPolicyAdapter<PolicyQueryCommandResponse> underTest() {
        return underTest;
    }

    private static TestParameter<PolicyQueryCommandResponse> retrievePolicyResponse() {
        final RetrievePolicyResponse response = RetrievePolicyResponse.of(Policies.POLICY_ID,
                Policies.POLICY, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE, EMPTY_PATH,
                Policies.POLICY.toJson(FieldType.notHidden()), HttpStatusCode.OK);
        return TestParameter.of("retrievePolicyResponse", adaptable, response);
    }

    private static TestParameter<PolicyQueryCommandResponse> retrievePolicyEntryResponse() {
        final RetrievePolicyEntryResponse response = RetrievePolicyEntryResponse.of(Policies.POLICY_ID,
                Policies.POLICY_ENTRY, Policies.HEADERS);
        final Adaptable adaptable =
                TestConstants.adaptable(TopicPaths.RETRIEVE, entriesPath(Policies.POLICY_ENTRY_LABEL),
                        Policies.POLICY_ENTRY.toJson(FieldType.notHidden()), HttpStatusCode.OK);
        return TestParameter.of("retrievePolicyEntryResponse", adaptable, response);
    }

    private static TestParameter<PolicyQueryCommandResponse> retrievePolicyEntriesResponse() {
        final RetrievePolicyEntriesResponse response =
                RetrievePolicyEntriesResponse.of(Policies.POLICY_ID, Policies.POLICY_ENTRIES, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE, JsonPointer.of("entries"),
                fromIterable(Policies.POLICY_ENTRIES, e -> JsonKey.of(e.getLabel()),
                        e -> e.toJson(FieldType.notHidden())), HttpStatusCode.OK);
        return TestParameter.of("retrievePolicyEntriesResponse", adaptable, response);
    }

    private static TestParameter<PolicyQueryCommandResponse> retrieveResourceResponse() {
        final RetrieveResourceResponse response = RetrieveResourceResponse.of(
                Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.RESOURCE1, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE,
                resourcesPath(Policies.POLICY_ENTRY_LABEL, Policies.RESOURCE1.getResourceKey()),
                Policies.RESOURCE1.toJson(FieldType.notHidden()), HttpStatusCode.OK);
        return TestParameter.of("retrieveResourceResponse", adaptable, response);
    }

    private static TestParameter<PolicyQueryCommandResponse> retrieveResourcesResponse() {
        final RetrieveResourcesResponse response = RetrieveResourcesResponse.of(
                Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.RESOURCES, Policies.HEADERS);
        final Adaptable adaptable =
                TestConstants.adaptable(TopicPaths.RETRIEVE, resourcesPath(Policies.POLICY_ENTRY_LABEL),
                        Policies.RESOURCES.toJson(FieldType.notHidden()), HttpStatusCode.OK);
        return TestParameter.of("retrieveResourcesResponse", adaptable, response);
    }

    private static TestParameter<PolicyQueryCommandResponse> retrieveSubjectResponse() {
        final RetrieveSubjectResponse response = RetrieveSubjectResponse.of(
                Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.SUBJECT1,
                Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE,
                subjectsPath(Policies.POLICY_ENTRY_LABEL, Policies.SUBJECT1.getId()),
                Policies.SUBJECT1.toJson(FieldType.notHidden()), HttpStatusCode.OK);
        return TestParameter.of("retrieveSubjectResponse", adaptable, response);
    }

    private static TestParameter<PolicyQueryCommandResponse> retrieveSubjectsResponse() {
        final RetrieveSubjectsResponse response = RetrieveSubjectsResponse.of(
                Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.SUBJECTS, Policies.HEADERS);
        final Adaptable adaptable =
                TestConstants.adaptable(TopicPaths.RETRIEVE, subjectsPath(Policies.POLICY_ENTRY_LABEL),
                        Policies.SUBJECTS.toJson(FieldType.notHidden()), HttpStatusCode.OK);
        return TestParameter.of("retrieveSubjectsResponse", adaptable, response);
    }

}
