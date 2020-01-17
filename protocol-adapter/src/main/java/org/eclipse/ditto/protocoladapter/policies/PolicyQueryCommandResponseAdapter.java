/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.adaptables.AdaptableConstructorFactory;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntriesResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResourceResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResourcesResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjectResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjectsResponse;

/**
 * Adapter for mapping a {@link PolicyQueryCommandResponse} to and from an {@link Adaptable}.
 */
public class PolicyQueryCommandResponseAdapter extends AbstractPolicyAdapter<PolicyQueryCommandResponse<?>> {

    private PolicyQueryCommandResponseAdapter(
            final Map<String, JsonifiableMapper<PolicyQueryCommandResponse<?>>> mappingStrategies,
            final HeaderTranslator headerTranslator) {
        super(mappingStrategies, headerTranslator,
                AdaptableConstructorFactory.newPolicyQueryResponseAdaptableConstructor());
    }

    /**
     * Returns a new PolicyQueryCommandResponseAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static PolicyQueryCommandResponseAdapter of(final HeaderTranslator headerTranslator) {
        return new PolicyQueryCommandResponseAdapter(mappingStrategies(), requireNonNull(headerTranslator));
    }

    private static Map<String, JsonifiableMapper<PolicyQueryCommandResponse<?>>> mappingStrategies() {
        final Map<String, JsonifiableMapper<PolicyQueryCommandResponse<?>>> mappingStrategies = new HashMap<>();

        addTopLevelResponses(mappingStrategies);

        addPolicyEntryResponses(mappingStrategies);

        addPolicyEntryResourceResponses(mappingStrategies);

        addPolicyEntrySubjectResponses(mappingStrategies);

        return mappingStrategies;
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final JsonPointer path = adaptable.getPayload().getPath();
        final String commandName = getAction(topicPath) + upperCaseFirst(pathMatcher.match(path));
        return topicPath.getGroup() + ".responses:" + commandName;
    }

    private static void addTopLevelResponses(
            final Map<String, JsonifiableMapper<PolicyQueryCommandResponse<?>>> mappingStrategies) {
        mappingStrategies.put(RetrievePolicyResponse.TYPE,
                adaptable -> RetrievePolicyResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        policyFrom(adaptable), dittoHeadersFrom(adaptable)));
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
