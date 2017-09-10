/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */

package org.eclipse.ditto.signals.commands.policies.exceptions;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.signals.commands.base.AbstractCommandToExceptionRegistry;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntries;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntry;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResource;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResources;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubject;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjects;

/**
 * Registry to map policy commands to their according access exception.
 */
public final class PolicyCommandToAccessExceptionRegistry
        extends AbstractCommandToExceptionRegistry<PolicyCommand, DittoRuntimeException> {

    private static final PolicyCommandToAccessExceptionRegistry INSTANCE = createInstance();

    private PolicyCommandToAccessExceptionRegistry(
            final Map<String, Function<PolicyCommand, DittoRuntimeException>> mappingStrategies) {
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
        final Map<String, Function<PolicyCommand, DittoRuntimeException>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(RetrievePolicy.TYPE, command -> PolicyNotAccessibleException.newBuilder(command.getId())
                .dittoHeaders(command.getDittoHeaders())
                .build());
        mappingStrategies.put(RetrievePolicyEntries.TYPE,
                command -> PolicyNotAccessibleException.newBuilder(command.getId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(RetrievePolicyEntry.TYPE,
                command -> PolicyEntryNotAccessibleException.newBuilder(command.getId(),
                        ((RetrievePolicyEntry) command).getLabel())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(RetrieveResource.TYPE,
                command -> ResourceNotAccessibleException.newBuilder(command.getId(),
                        ((RetrievePolicyEntry) command).getLabel(),
                        command.getResourcePath().toString())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(RetrieveResources.TYPE,
                command -> ResourcesNotAccessibleException.newBuilder(command.getId(),
                        ((RetrievePolicyEntry) command).getLabel())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(RetrieveSubject.TYPE, command -> SubjectNotAccessibleException.newBuilder(command.getId(),
                ((RetrieveSubject) command).getLabel(),
                ((RetrieveSubject) command).getSubjectId())
                .dittoHeaders(command.getDittoHeaders())
                .build());
        mappingStrategies.put(RetrieveSubjects.TYPE,
                command -> SubjectsNotAccessibleException.newBuilder(command.getId(),
                        ((RetrieveSubjects) command).getLabel())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());

        return new PolicyCommandToAccessExceptionRegistry(mappingStrategies);
    }

}
