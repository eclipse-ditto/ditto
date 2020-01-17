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

import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.adaptables.AdaptableConstructorFactory;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteResourceResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubjectResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntriesResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResourceResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResourcesResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjectResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjectsResponse;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandResponse;

/**
 * Adapter for mapping a {@link PolicyModifyCommandResponse} to and from an {@link Adaptable}.
 */
public class PolicyModifyCommandResponseAdapter extends AbstractPolicyAdapter<PolicyModifyCommandResponse<?>> {

    private PolicyModifyCommandResponseAdapter(
            final Map<String, JsonifiableMapper<PolicyModifyCommandResponse<?>>> mappingStrategies,
            final HeaderTranslator headerTranslator) {
        super(mappingStrategies, headerTranslator,
                AdaptableConstructorFactory.newPolicyModifyResponseAdaptableConstructor());
    }


    /**
     * Returns a new PolicyModifyCommandResponseAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static PolicyModifyCommandResponseAdapter of(final HeaderTranslator headerTranslator) {
        return new PolicyModifyCommandResponseAdapter(mappingStrategies(), requireNonNull(headerTranslator));
    }

    private static Map<String, JsonifiableMapper<PolicyModifyCommandResponse<?>>> mappingStrategies() {
        final Map<String, JsonifiableMapper<PolicyModifyCommandResponse<?>>> mappingStrategies = new HashMap<>();

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
            final Map<String, JsonifiableMapper<PolicyModifyCommandResponse<?>>> mappingStrategies) {
        mappingStrategies.put(CreatePolicyResponse.TYPE,
                adaptable -> CreatePolicyResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        policyFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyPolicyResponse.TYPE,
                adaptable -> isCreated(adaptable) ? policyCreated(adaptable) : policyModified(adaptable));

        mappingStrategies.put(DeletePolicyResponse.TYPE,
                adaptable -> DeletePolicyResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        dittoHeadersFrom(adaptable)));
    }

    private static void addPolicyEntryResponses(
            final Map<String, JsonifiableMapper<PolicyModifyCommandResponse<?>>> mappingStrategies) {

        mappingStrategies.put(ModifyPolicyEntryResponse.TYPE,
                adaptable -> isCreated(adaptable) ? entryCreated(adaptable) : entryModified(adaptable));

        mappingStrategies.put(DeletePolicyEntryResponse.TYPE,
                adaptable -> DeletePolicyEntryResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        labelFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyPolicyEntriesResponse.TYPE,
                adaptable -> ModifyPolicyEntriesResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        dittoHeadersFrom(adaptable)));
    }

    private static void addPolicyEntryResourceResponses(
            final Map<String, JsonifiableMapper<PolicyModifyCommandResponse<?>>> mappingStrategies) {


        mappingStrategies.put(ModifyResourceResponse.TYPE,
                adaptable -> isCreated(adaptable) ? resourceCreated(adaptable) : resourceModified(adaptable));

        mappingStrategies.put(ModifyResourcesResponse.TYPE, adaptable -> ModifyResourcesResponse.of(
                policyIdFromTopicPath(adaptable.getTopicPath()), labelFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(DeleteResourceResponse.TYPE, adaptable -> DeleteResourceResponse.of(
                policyIdFromTopicPath(adaptable.getTopicPath()),
                labelFrom(adaptable), entryResourceKeyFromPath(adaptable.getPayload().getPath()),
                dittoHeadersFrom(adaptable)));
    }

    private static void addPolicyEntrySubjectResponses(
            final Map<String, JsonifiableMapper<PolicyModifyCommandResponse<?>>> mappingStrategies) {

        mappingStrategies.put(ModifySubjectResponse.TYPE,
                adaptable -> isCreated(adaptable) ? subjectCreated(adaptable) : subjectModified(adaptable));

        mappingStrategies.put(ModifySubjectsResponse.TYPE,
                adaptable -> ModifySubjectsResponse.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        labelFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(DeleteSubjectResponse.TYPE,
                adaptable -> DeleteSubjectResponse.of(
                        policyIdFromTopicPath(adaptable.getTopicPath()),
                        labelFrom(adaptable),
                        entrySubjectIdFromPath(adaptable.getPayload().getPath()),
                        dittoHeadersFrom(adaptable)));

    }


    private static ModifyPolicyResponse policyCreated(final Adaptable adaptable) {
        return ModifyPolicyResponse.created(policyIdFromTopicPath(adaptable.getTopicPath()),
                policyFrom(adaptable), dittoHeadersFrom(adaptable));
    }

    private static ModifyPolicyResponse policyModified(final Adaptable adaptable) {
        return ModifyPolicyResponse.modified(policyIdFromTopicPath(adaptable.getTopicPath()),
                dittoHeadersFrom(adaptable));
    }

    private static ModifyPolicyEntryResponse entryCreated(final Adaptable adaptable) {
        final PolicyId policyId = policyIdFromTopicPath(adaptable.getTopicPath());
        return ModifyPolicyEntryResponse.created(policyId, policyEntryFrom(adaptable), dittoHeadersFrom(adaptable));
    }

    private static ModifyPolicyEntryResponse entryModified(final Adaptable adaptable) {
        final PolicyId policyId = policyIdFromTopicPath(adaptable.getTopicPath());
        return ModifyPolicyEntryResponse.modified(policyId, labelFrom(adaptable), dittoHeadersFrom(adaptable));
    }

    private static ModifyResourceResponse resourceCreated(final Adaptable adaptable) {
        final PolicyId policyId = policyIdFromTopicPath(adaptable.getTopicPath());
        final ResourceKey resourceKey = entryResourceKeyFromPath(adaptable.getPayload().getPath());
        final JsonValue effectedPermissions =
                adaptable.getPayload().getValue().orElseThrow(() -> JsonParseException.newBuilder().build());
        final Resource resourceCreated = Resource.newInstance(resourceKey, effectedPermissions);
        return ModifyResourceResponse.created(policyId, labelFrom(adaptable), resourceCreated,
                dittoHeadersFrom(adaptable));
    }

    private static ModifyResourceResponse resourceModified(final Adaptable adaptable) {
        final PolicyId policyId = policyIdFromTopicPath(adaptable.getTopicPath());
        return ModifyResourceResponse.modified(policyId, labelFrom(adaptable),
                entryResourceKeyFromPath(adaptable.getPayload().getPath()), dittoHeadersFrom(adaptable));
    }

    private static ModifySubjectResponse subjectCreated(final Adaptable adaptable) {
        return ModifySubjectResponse.created(
                policyIdFromTopicPath(adaptable.getTopicPath()),
                labelFrom(adaptable),
                subjectFrom(adaptable),
                dittoHeadersFrom(adaptable));
    }

    private static ModifySubjectResponse subjectModified(final Adaptable adaptable) {
        return ModifySubjectResponse.modified(
                policyIdFromTopicPath(adaptable.getTopicPath()),
                labelFrom(adaptable),
                entrySubjectIdFromPath(adaptable.getPayload().getPath()),
                dittoHeadersFrom(adaptable));
    }
}
