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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteResource;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntries;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResource;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResources;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjects;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;
import org.eclipse.ditto.protocol.JsonifiableMapper;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for policy modify commands.
 */
final class PolicyModifyCommandMappingStrategies extends AbstractPolicyMappingStrategies<PolicyModifyCommand<?>> {

    private static final PolicyModifyCommandMappingStrategies INSTANCE = new PolicyModifyCommandMappingStrategies();

    private PolicyModifyCommandMappingStrategies() {
        super(initMappingStrategies());
    }

    static PolicyModifyCommandMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<PolicyModifyCommand<?>>> initMappingStrategies() {
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

        mappingStrategies.put(ModifyPolicyImports.TYPE,
                adaptable -> ModifyPolicyImports.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        policyImportsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyPolicyImport.TYPE,
                adaptable -> ModifyPolicyImport.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        policyImportFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(DeletePolicyImport.TYPE,
                adaptable -> DeletePolicyImport.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        importedPolicyIdFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
        return mappingStrategies;
    }

}
