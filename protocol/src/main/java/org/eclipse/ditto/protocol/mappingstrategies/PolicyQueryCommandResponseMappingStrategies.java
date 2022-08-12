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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntriesResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntryResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImportsResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResourceResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResourcesResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjectResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjectsResponse;
import org.eclipse.ditto.protocol.JsonifiableMapper;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for policy query command responses.
 */
final class PolicyQueryCommandResponseMappingStrategies
        extends AbstractPolicyMappingStrategies<PolicyQueryCommandResponse<?>> {

    private static final PolicyQueryCommandResponseMappingStrategies INSTANCE =
            new PolicyQueryCommandResponseMappingStrategies();

    static PolicyQueryCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

    private PolicyQueryCommandResponseMappingStrategies() {
        super(initMappingStrategies());
    }

    private static Map<String, JsonifiableMapper<PolicyQueryCommandResponse<?>>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<PolicyQueryCommandResponse<?>>> mappingStrategies = new HashMap<>();
        addTopLevelResponses(mappingStrategies);
        addPolicyEntryResponses(mappingStrategies);
        addPolicyEntryResourceResponses(mappingStrategies);
        addPolicyEntrySubjectResponses(mappingStrategies);
        addPolicyImportResponses(mappingStrategies);
        return mappingStrategies;
    }

    private static void addTopLevelResponses(
            final Map<String, JsonifiableMapper<PolicyQueryCommandResponse<?>>> mappingStrategies) {
        mappingStrategies.put(RetrievePolicyResponse.TYPE,
                adaptable -> RetrievePolicyResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        policyJsonFrom(adaptable), dittoHeadersFrom(adaptable)));
    }

    private static void addPolicyEntryResponses(
            final Map<String, JsonifiableMapper<PolicyQueryCommandResponse<?>>> mappingStrategies) {

        mappingStrategies.put(RetrievePolicyEntryResponse.TYPE,
                adaptable -> RetrievePolicyEntryResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        policyEntryFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrievePolicyEntriesResponse.TYPE,
                adaptable -> RetrievePolicyEntriesResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        policyEntriesFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

    }

    private static void addPolicyImportResponses(
            final Map<String, JsonifiableMapper<PolicyQueryCommandResponse<?>>> mappingStrategies) {

        mappingStrategies.put(RetrievePolicyImportResponse.TYPE,
                adaptable -> RetrievePolicyImportResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        policyImportFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrievePolicyImportsResponse.TYPE,
                adaptable -> RetrievePolicyImportsResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        policyImportsFrom(adaptable), dittoHeadersFrom(adaptable)));
    }

    private static void addPolicyEntryResourceResponses(
            final Map<String, JsonifiableMapper<PolicyQueryCommandResponse<?>>> mappingStrategies) {

        mappingStrategies.put(RetrieveResourceResponse.TYPE,
                adaptable -> RetrieveResourceResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        labelFrom(adaptable), resourceFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveResourcesResponse.TYPE,
                adaptable -> RetrieveResourcesResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        labelFrom(adaptable), resourcesFrom(adaptable), dittoHeadersFrom(adaptable)));

    }

    private static void addPolicyEntrySubjectResponses(
            final Map<String, JsonifiableMapper<PolicyQueryCommandResponse<?>>> mappingStrategies) {

        mappingStrategies.put(RetrieveSubjectResponse.TYPE,
                adaptable -> RetrieveSubjectResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        labelFrom(adaptable), subjectFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveSubjectsResponse.TYPE,
                adaptable -> RetrieveSubjectsResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        labelFrom(adaptable), subjectsFrom(adaptable), dittoHeadersFrom(adaptable)));
    }
}
