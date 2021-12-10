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
import org.eclipse.ditto.protocol.JsonifiableMapper;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for policy modify command responses.
 */
final class PolicyModifyCommandResponseMappingStrategies
        extends AbstractPolicyMappingStrategies<PolicyModifyCommandResponse<?>> {

    private static final PolicyModifyCommandResponseMappingStrategies INSTANCE =
            new PolicyModifyCommandResponseMappingStrategies();

    private PolicyModifyCommandResponseMappingStrategies() {
        super(initMappingStrategies());
    }

    private static Map<String, JsonifiableMapper<PolicyModifyCommandResponse<?>>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<PolicyModifyCommandResponse<?>>> mappingStrategies = new HashMap<>();

        addTopLevelResponses(mappingStrategies);

        addPolicyEntryResponses(mappingStrategies);

        addPolicyEntryResourceResponses(mappingStrategies);

        addPolicyEntrySubjectResponses(mappingStrategies);

        return mappingStrategies;
    }

    static PolicyModifyCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static void addTopLevelResponses(
            final Map<String, JsonifiableMapper<PolicyModifyCommandResponse<?>>> mappingStrategies) {

        mappingStrategies.put(CreatePolicyResponse.TYPE, (AdaptableToSignalMapper) AdaptableToSignalMapper.of(
                        CreatePolicyResponse.class, mappingContext ->
                        CreatePolicyResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                                        mappingContext.getPolicy().orElse(null),
                                        mappingContext.getHttpStatusOrThrow(),
                                        mappingContext.getDittoHeaders())));

        mappingStrategies.put(ModifyPolicyResponse.TYPE, (AdaptableToSignalMapper) AdaptableToSignalMapper.of(
                ModifyPolicyResponse.class, mappingContext ->
                        ModifyPolicyResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getPolicy().orElse(null),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));

        mappingStrategies.put(DeletePolicyResponse.TYPE, (AdaptableToSignalMapper) AdaptableToSignalMapper.of(
                DeletePolicyResponse.class, mappingContext ->
                        DeletePolicyResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                                mappingContext.getHttpStatusOrThrow(),
                                mappingContext.getDittoHeaders())));
    }

    private static void addPolicyEntryResponses(
            final Map<String, JsonifiableMapper<PolicyModifyCommandResponse<?>>> mappingStrategies) {

        mappingStrategies.put(ModifyPolicyEntryResponse.TYPE, (AdaptableToSignalMapper) AdaptableToSignalMapper.of(
                ModifyPolicyEntryResponse.class, mappingContext ->
                        ModifyPolicyEntryResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                                mappingContext.getPolicyEntry().orElse(null),
                                mappingContext.getLabelOrThrow(),
                                mappingContext.getHttpStatusOrThrow(),
                                mappingContext.getDittoHeaders())));

        mappingStrategies.put(DeletePolicyEntryResponse.TYPE, (AdaptableToSignalMapper) AdaptableToSignalMapper.of(
                DeletePolicyEntryResponse.class, mappingContext ->
                        DeletePolicyEntryResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                                mappingContext.getLabelOrThrow(),
                                mappingContext.getHttpStatusOrThrow(),
                                mappingContext.getDittoHeaders())));

        mappingStrategies.put(ModifyPolicyEntriesResponse.TYPE, (AdaptableToSignalMapper) AdaptableToSignalMapper.of(
                ModifyPolicyEntriesResponse.class, mappingContext ->
                        ModifyPolicyEntriesResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                                mappingContext.getHttpStatusOrThrow(),
                                mappingContext.getDittoHeaders())));
    }

    private static void addPolicyEntryResourceResponses(
            final Map<String, JsonifiableMapper<PolicyModifyCommandResponse<?>>> mappingStrategies) {

        mappingStrategies.put(ModifyResourceResponse.TYPE, (AdaptableToSignalMapper) AdaptableToSignalMapper.of(
                ModifyResourceResponse.class, mappingContext ->
                        ModifyResourceResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                                mappingContext.getLabelOrThrow(),
                                mappingContext.getResourceKeyOrThrow(),
                                mappingContext.getResource().orElse(null),
                                mappingContext.getHttpStatusOrThrow(),
                                mappingContext.getDittoHeaders())));

        mappingStrategies.put(ModifyResourcesResponse.TYPE, (AdaptableToSignalMapper) AdaptableToSignalMapper.of(
                ModifyResourcesResponse.class, mappingContext ->
                        ModifyResourcesResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                                mappingContext.getLabelOrThrow(),
                                mappingContext.getHttpStatusOrThrow(),
                                mappingContext.getDittoHeaders())));

        mappingStrategies.put(DeleteResourceResponse.TYPE, (AdaptableToSignalMapper) AdaptableToSignalMapper.of(
                DeleteResourceResponse.class, mappingContext ->
                        DeleteResourceResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                                mappingContext.getLabelOrThrow(),
                                mappingContext.getResourceKeyOrThrow(),
                                mappingContext.getHttpStatusOrThrow(),
                                mappingContext.getDittoHeaders())));
    }

    private static void addPolicyEntrySubjectResponses(
            final Map<String, JsonifiableMapper<PolicyModifyCommandResponse<?>>> mappingStrategies) {

        mappingStrategies.put(ModifySubjectResponse.TYPE, (AdaptableToSignalMapper) AdaptableToSignalMapper.of(
                ModifySubjectResponse.class, mappingContext ->
                        ModifySubjectResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                                mappingContext.getLabelOrThrow(),
                                mappingContext.getSubjectIdOrThrow(),
                                mappingContext.getSubject().orElse(null),
                                mappingContext.getHttpStatusOrThrow(),
                                mappingContext.getDittoHeaders())));

        mappingStrategies.put(ModifySubjectsResponse.TYPE, (AdaptableToSignalMapper) AdaptableToSignalMapper.of(
                ModifySubjectsResponse.class, mappingContext ->
                        ModifySubjectsResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                                mappingContext.getLabelOrThrow(),
                                mappingContext.getHttpStatusOrThrow(),
                                mappingContext.getDittoHeaders())));

        mappingStrategies.put(DeleteSubjectResponse.TYPE, (AdaptableToSignalMapper) AdaptableToSignalMapper.of(
                DeleteSubjectResponse.class, mappingContext ->
                        DeleteSubjectResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                                mappingContext.getLabelOrThrow(),
                                mappingContext.getSubjectIdOrThrow(),
                                mappingContext.getHttpStatusOrThrow(),
                                mappingContext.getDittoHeaders())));
    }

}
