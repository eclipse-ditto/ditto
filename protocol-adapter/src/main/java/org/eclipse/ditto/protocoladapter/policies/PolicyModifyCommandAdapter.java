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

/**
 * Adapter for mapping a {@link PolicyModifyCommand} to and from an {@link Adaptable}.
 */
final class PolicyModifyCommandAdapter extends AbstractPolicyAdapter<PolicyModifyCommand<?>> {

    private PolicyModifyCommandAdapter(
            final Map<String, JsonifiableMapper<PolicyModifyCommand<?>>> mappingStrategies,
            final HeaderTranslator headerTranslator) {
        super(mappingStrategies, headerTranslator, AdaptableConstructorFactory.newPolicyModifyAdaptableConstructor());
    }

    /**
     * Returns a new PolicyModifyCommandAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static PolicyModifyCommandAdapter of(final HeaderTranslator headerTranslator) {
        return new PolicyModifyCommandAdapter(mappingStrategies(), requireNonNull(headerTranslator));
    }

    private static Map<String, JsonifiableMapper<PolicyModifyCommand<?>>> mappingStrategies() {
        final Map<String, JsonifiableMapper<PolicyModifyCommand<?>>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(CreatePolicy.TYPE, adaptable -> CreatePolicy.of(policyFrom(adaptable),
                dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyPolicy.TYPE,
                adaptable -> ModifyPolicy.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        policyFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(DeletePolicy.TYPE,
                adaptable -> DeletePolicy.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyPolicyEntry.TYPE,
                adaptable -> ModifyPolicyEntry.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        policyEntryFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(DeletePolicyEntry.TYPE,
                adaptable -> DeletePolicyEntry.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        labelFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyPolicyEntries.TYPE, adaptable -> ModifyPolicyEntries.of(
                policyIdFromTopicPath(adaptable.getTopicPath()),
                policyEntriesFrom(adaptable),
                dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyResource.TYPE, adaptable -> ModifyResource.of(
                policyIdFromTopicPath(adaptable.getTopicPath()),
                labelFrom(adaptable),
                resourceFrom(adaptable),
                dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyResources.TYPE, adaptable -> ModifyResources.of(
                policyIdFromTopicPath(adaptable.getTopicPath()),
                labelFrom(adaptable),
                resourcesFrom(adaptable),
                dittoHeadersFrom(adaptable)));

        mappingStrategies.put(DeleteResource.TYPE, adaptable -> DeleteResource.of(
                policyIdFromTopicPath(adaptable.getTopicPath()),
                labelFrom(adaptable),
                entryResourceKeyFromPath(adaptable.getPayload().getPath()),
                dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifySubject.TYPE, adaptable -> ModifySubject.of(
                policyIdFromTopicPath(adaptable.getTopicPath()),
                labelFrom(adaptable),
                subjectFrom(adaptable),
                dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifySubjects.TYPE, adaptable -> ModifySubjects.of(
                policyIdFromTopicPath(adaptable.getTopicPath()),
                labelFrom(adaptable),
                subjectsFrom(adaptable),
                dittoHeadersFrom(adaptable)));

        mappingStrategies.put(DeleteSubject.TYPE, adaptable -> DeleteSubject.of(
                policyIdFromTopicPath(adaptable.getTopicPath()),
                labelFrom(adaptable),
                entrySubjectIdFromPath(adaptable.getPayload().getPath()),
                dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final JsonPointer path = adaptable.getPayload().getPath();
        final String commandName = getAction(topicPath) + upperCaseFirst(pathMatcher.match(path));
        return topicPath.getGroup() + "." + topicPath.getCriterion() + ":" + commandName;
    }

}
