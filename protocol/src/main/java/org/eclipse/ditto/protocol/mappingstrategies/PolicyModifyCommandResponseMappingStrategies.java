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

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyEntryResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImportResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteResourceResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubjectResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntriesResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntryResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImportsResponse;
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
final class PolicyModifyCommandResponseMappingStrategies implements MappingStrategies<PolicyModifyCommandResponse<?>> {

    private static final PolicyModifyCommandResponseMappingStrategies INSTANCE =
            new PolicyModifyCommandResponseMappingStrategies();

    private final Map<String, JsonifiableMapper<? extends PolicyModifyCommandResponse<?>>> mappingStrategies;

    private PolicyModifyCommandResponseMappingStrategies() {
        mappingStrategies = Collections.unmodifiableMap(initMappingStrategies());
    }

    private static Map<String, AdaptableToSignalMapper<? extends PolicyModifyCommandResponse<?>>> initMappingStrategies() {
        final Stream.Builder<AdaptableToSignalMapper<? extends PolicyModifyCommandResponse<?>>> streamBuilder =
                Stream.builder();

        addTopLevelResponses(streamBuilder);

        addPolicyEntryResponses(streamBuilder);

        addPolicyEntryResourceResponses(streamBuilder);

        addPolicyEntrySubjectResponses(streamBuilder);

        addPolicyImportResponses(streamBuilder);

        final Stream<AdaptableToSignalMapper<? extends PolicyModifyCommandResponse<?>>> mappers = streamBuilder.build();
        return mappers.collect(Collectors.toMap(AdaptableToSignalMapper::getSignalType, Function.identity()));
    }

    static PolicyModifyCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static void addTopLevelResponses(
            final Consumer<AdaptableToSignalMapper<? extends PolicyModifyCommandResponse<?>>> streamBuilder
    ) {
        streamBuilder.accept(AdaptableToSignalMapper.of(CreatePolicyResponse.TYPE,
                mappingContext -> CreatePolicyResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getPolicy().orElse(null),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));

        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyPolicyResponse.TYPE,
                mappingContext -> ModifyPolicyResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getPolicy().orElse(null),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));

        streamBuilder.accept(AdaptableToSignalMapper.of(DeletePolicyResponse.TYPE,
                mappingContext -> DeletePolicyResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));
    }

    private static void addPolicyEntryResponses(
            final Consumer<AdaptableToSignalMapper<? extends PolicyModifyCommandResponse<?>>> streamBuilder
    ) {
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyPolicyEntryResponse.TYPE,
                mappingContext -> ModifyPolicyEntryResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getPolicyEntry().orElse(null),
                        mappingContext.getLabelOrThrow(),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));

        streamBuilder.accept(AdaptableToSignalMapper.of(DeletePolicyEntryResponse.TYPE,
                mappingContext -> DeletePolicyEntryResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getLabelOrThrow(),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));

        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyPolicyEntriesResponse.TYPE,
                mappingContext -> ModifyPolicyEntriesResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));
    }

    private static void addPolicyImportResponses(
            final Consumer<AdaptableToSignalMapper<? extends PolicyModifyCommandResponse<?>>> streamBuilder) {

        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyPolicyImportsResponse.TYPE,
                mappingContext -> ModifyPolicyImportsResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getPolicyImports().orElse(null),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));

        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyPolicyImportResponse.TYPE,
                mappingContext -> ModifyPolicyImportResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getImportedPolicyId(),
                        mappingContext.getPolicyImport().orElse(null),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));

        streamBuilder.accept(AdaptableToSignalMapper.of(DeletePolicyImportResponse.TYPE,
                mappingContext -> DeletePolicyImportResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getImportedPolicyId(),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));
    }

    private static void addPolicyEntryResourceResponses(
            final Consumer<AdaptableToSignalMapper<? extends PolicyModifyCommandResponse<?>>> streamBuilder
    ) {
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyResourceResponse.TYPE,
                mappingContext -> ModifyResourceResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getLabelOrThrow(),
                        mappingContext.getResourceKeyOrThrow(),
                        mappingContext.getResource().orElse(null),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));

        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyResourcesResponse.TYPE,
                mappingContext -> ModifyResourcesResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getLabelOrThrow(),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));

        streamBuilder.accept(AdaptableToSignalMapper.of(DeleteResourceResponse.TYPE,
                mappingContext -> DeleteResourceResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getLabelOrThrow(),
                        mappingContext.getResourceKeyOrThrow(),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));
    }

    private static void addPolicyEntrySubjectResponses(
            final Consumer<AdaptableToSignalMapper<? extends PolicyModifyCommandResponse<?>>> streamBuilder
    ) {
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifySubjectResponse.TYPE,
                mappingContext -> ModifySubjectResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getLabelOrThrow(),
                        mappingContext.getSubjectIdOrThrow(),
                        mappingContext.getSubject().orElse(null),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));

        streamBuilder.accept(AdaptableToSignalMapper.of(ModifySubjectsResponse.TYPE,
                mappingContext -> ModifySubjectsResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getLabelOrThrow(),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));

        streamBuilder.accept(AdaptableToSignalMapper.of(DeleteSubjectResponse.TYPE,
                mappingContext -> DeleteSubjectResponse.newInstance(mappingContext.getPolicyIdFromTopicPath(),
                        mappingContext.getLabelOrThrow(),
                        mappingContext.getSubjectIdOrThrow(),
                        mappingContext.getHttpStatusOrThrow(),
                        mappingContext.getDittoHeaders())));
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public JsonifiableMapper<PolicyModifyCommandResponse<?>> find(final String type) {
        return (JsonifiableMapper<PolicyModifyCommandResponse<?>>) mappingStrategies.get(type);
    }

}
