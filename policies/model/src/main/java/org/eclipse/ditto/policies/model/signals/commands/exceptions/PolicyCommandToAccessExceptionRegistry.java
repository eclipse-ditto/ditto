/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.commands.exceptions;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandToExceptionRegistry;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntries;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResource;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveResources;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubject;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrieveSubjects;

/**
 * Registry to map policy commands to their according access exception.
 */
public final class PolicyCommandToAccessExceptionRegistry
        extends AbstractCommandToExceptionRegistry<PolicyCommand<?>, DittoRuntimeException> {

    private static final PolicyCommandToAccessExceptionRegistry INSTANCE = createInstance();

    private PolicyCommandToAccessExceptionRegistry(
            final Map<String, Function<PolicyCommand<?>, DittoRuntimeException>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Returns an instance of {@code PolicyCommandToAccessExceptionRegistry}.
     *
     * @return the instance.
     */
    public static PolicyCommandToAccessExceptionRegistry getInstance() {
        return INSTANCE;
    }

    private static PolicyCommandToAccessExceptionRegistry createInstance() {
        final Map<String, Function<PolicyCommand<?>, DittoRuntimeException>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(RetrievePolicy.TYPE,
                command -> PolicyNotAccessibleException.newBuilder(command.getEntityId())
                .dittoHeaders(command.getDittoHeaders())
                .build());
        mappingStrategies.put(RetrievePolicyEntries.TYPE,
                command -> PolicyNotAccessibleException.newBuilder(command.getEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(RetrievePolicyEntry.TYPE,
                command -> PolicyEntryNotAccessibleException.newBuilder(command.getEntityId(),
                        ((RetrievePolicyEntry) command).getLabel())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(RetrieveResource.TYPE,
                command -> ResourceNotAccessibleException.newBuilder(command.getEntityId(),
                        ((RetrievePolicyEntry) command).getLabel(),
                        command.getResourcePath().toString())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(RetrieveResources.TYPE,
                command -> ResourcesNotAccessibleException.newBuilder(command.getEntityId(),
                        ((RetrievePolicyEntry) command).getLabel())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(RetrieveSubject.TYPE,
                command -> SubjectNotAccessibleException.newBuilder(command.getEntityId(),
                ((RetrieveSubject) command).getLabel(),
                ((RetrieveSubject) command).getSubjectId())
                .dittoHeaders(command.getDittoHeaders())
                .build());
        mappingStrategies.put(RetrieveSubjects.TYPE,
                command -> SubjectsNotAccessibleException.newBuilder(command.getEntityId(),
                        ((RetrieveSubjects) command).getLabel())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(RetrievePolicyImports.TYPE,
                command -> PolicyImportsNotAccessibleException.newBuilder(command.getEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(RetrievePolicyImport.TYPE,
                command -> PolicyImportNotAccessibleException.newBuilder(command.getEntityId(),
                        ((RetrievePolicyImport) command).getImportedPolicyId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());

        return new PolicyCommandToAccessExceptionRegistry(mappingStrategies);
    }

}
