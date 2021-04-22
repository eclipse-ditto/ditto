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

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
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
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntriesResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntryResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResourceResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResourcesResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjectResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjectsResponse;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit test for {@link PolicyQueryCommandResponseAdapter}.
 */
@RunWith(Parameterized.class)
public final class ParametrizedPolicyQueryCommandResponseAdapterTest
        extends ParametrizedCommandAdapterTest<PolicyQueryCommandResponse<?>> implements ProtocolAdapterTest {

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
    protected AbstractPolicyAdapter<PolicyQueryCommandResponse<?>> underTest() {
        return underTest;
    }

    @Override
    protected TopicPath.Channel defaultChannel() {
        return TopicPath.Channel.NONE;
    }

    private static TestParameter<PolicyQueryCommandResponse<?>> retrievePolicyResponse() {
        final RetrievePolicyResponse response = RetrievePolicyResponse.of(Policies.POLICY_ID,
                Policies.POLICY, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE, EMPTY_PATH,
                Policies.POLICY.toJson(FieldType.notHidden()), HttpStatus.OK);
        return TestParameter.of("retrievePolicyResponse", adaptable, response);
    }

    private static TestParameter<PolicyQueryCommandResponse<?>> retrievePolicyEntryResponse() {
        final RetrievePolicyEntryResponse response = RetrievePolicyEntryResponse.of(Policies.POLICY_ID,
                Policies.POLICY_ENTRY, Policies.HEADERS);
        final Adaptable adaptable =
                TestConstants.adaptable(TopicPaths.RETRIEVE, entriesPath(Policies.POLICY_ENTRY_LABEL),
                        Policies.POLICY_ENTRY.toJson(FieldType.notHidden()), HttpStatus.OK);
        return TestParameter.of("retrievePolicyEntryResponse", adaptable, response);
    }

    private static TestParameter<PolicyQueryCommandResponse<?>> retrievePolicyEntriesResponse() {
        final RetrievePolicyEntriesResponse response =
                RetrievePolicyEntriesResponse.of(Policies.POLICY_ID, Policies.POLICY_ENTRIES, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE, JsonPointer.of("entries"),
                fromIterable(Policies.POLICY_ENTRIES, e -> JsonKey.of(e.getLabel()),
                        e -> e.toJson(FieldType.notHidden())), HttpStatus.OK);
        return TestParameter.of("retrievePolicyEntriesResponse", adaptable, response);
    }

    private static TestParameter<PolicyQueryCommandResponse<?>> retrieveResourceResponse() {
        final RetrieveResourceResponse response = RetrieveResourceResponse.of(
                Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.RESOURCE1, Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE,
                resourcesPath(Policies.POLICY_ENTRY_LABEL, Policies.RESOURCE1.getResourceKey()),
                Policies.RESOURCE1.toJson(FieldType.notHidden()), HttpStatus.OK);
        return TestParameter.of("retrieveResourceResponse", adaptable, response);
    }

    private static TestParameter<PolicyQueryCommandResponse<?>> retrieveResourcesResponse() {
        final RetrieveResourcesResponse response = RetrieveResourcesResponse.of(
                Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.RESOURCES, Policies.HEADERS);
        final Adaptable adaptable =
                TestConstants.adaptable(TopicPaths.RETRIEVE, resourcesPath(Policies.POLICY_ENTRY_LABEL),
                        Policies.RESOURCES.toJson(FieldType.notHidden()), HttpStatus.OK);
        return TestParameter.of("retrieveResourcesResponse", adaptable, response);
    }

    private static TestParameter<PolicyQueryCommandResponse<?>> retrieveSubjectResponse() {
        final RetrieveSubjectResponse response = RetrieveSubjectResponse.of(
                Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.SUBJECT1,
                Policies.HEADERS);
        final Adaptable adaptable = TestConstants.adaptable(TopicPaths.RETRIEVE,
                subjectsPath(Policies.POLICY_ENTRY_LABEL, Policies.SUBJECT1.getId()),
                Policies.SUBJECT1.toJson(FieldType.notHidden()), HttpStatus.OK);
        return TestParameter.of("retrieveSubjectResponse", adaptable, response);
    }

    private static TestParameter<PolicyQueryCommandResponse<?>> retrieveSubjectsResponse() {
        final RetrieveSubjectsResponse response = RetrieveSubjectsResponse.of(
                Policies.POLICY_ID, Policies.POLICY_ENTRY_LABEL, Policies.SUBJECTS, Policies.HEADERS);
        final Adaptable adaptable =
                TestConstants.adaptable(TopicPaths.RETRIEVE, subjectsPath(Policies.POLICY_ENTRY_LABEL),
                        Policies.SUBJECTS.toJson(FieldType.notHidden()), HttpStatus.OK);
        return TestParameter.of("retrieveSubjectsResponse", adaptable, response);
    }

}
