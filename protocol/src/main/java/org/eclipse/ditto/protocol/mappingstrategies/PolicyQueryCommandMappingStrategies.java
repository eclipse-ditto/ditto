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

import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntries;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResource;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResources;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubject;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjects;
import org.eclipse.ditto.protocol.JsonifiableMapper;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for policy query commands.
 */
final class PolicyQueryCommandMappingStrategies extends AbstractPolicyMappingStrategies<PolicyQueryCommand<?>> {

    private static final PolicyQueryCommandMappingStrategies INSTANCE = new PolicyQueryCommandMappingStrategies();

    static PolicyQueryCommandMappingStrategies getInstance() {
        return INSTANCE;
    }

    private PolicyQueryCommandMappingStrategies() {
        super(initMappingStrategies());
    }

    private static Map<String, JsonifiableMapper<PolicyQueryCommand<?>>> initMappingStrategies() {

        final Map<String, JsonifiableMapper<PolicyQueryCommand<?>>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(RetrievePolicy.TYPE,
                adaptable -> RetrievePolicy.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        dittoHeadersFrom(adaptable),
                        selectedFieldsFrom(adaptable)));

        mappingStrategies.put(RetrievePolicyEntry.TYPE,
                adaptable -> RetrievePolicyEntry.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        labelFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrievePolicyEntries.TYPE,
                adaptable -> RetrievePolicyEntries.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrievePolicyImport.TYPE,
                adaptable -> RetrievePolicyImport.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        importedPolicyIdFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrievePolicyImports.TYPE,
                adaptable -> RetrievePolicyImports.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveResource.TYPE,
                adaptable -> RetrieveResource.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        labelFrom(adaptable), entryResourceKeyFromPath(adaptable.getPayload().getPath()),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveResources.TYPE,
                adaptable -> RetrieveResources.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        labelFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveSubject.TYPE,
                adaptable -> RetrieveSubject.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        labelFrom(adaptable),
                        entrySubjectIdFromPath(adaptable.getPayload().getPath()), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveSubjects.TYPE,
                adaptable -> RetrieveSubjects.of(policyIdFromTopicPath(adaptable.getTopicPath()),
                        labelFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;

    }

}
