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

/**
 * Registry to map policy commands to their according modify exception.
 */
public final class PolicyCommandToModifyExceptionRegistry
        extends AbstractCommandToExceptionRegistry<PolicyCommand, DittoRuntimeException> {

    private static final PolicyCommandToModifyExceptionRegistry INSTANCE = createInstance();

    private PolicyCommandToModifyExceptionRegistry(
            final Map<String, Function<PolicyCommand, DittoRuntimeException>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Returns an instance of {@code PolicyCommandToModifyExceptionRegistry}.
     *
     * @return the instance.
     */
    public static PolicyCommandToModifyExceptionRegistry getInstance() {
        return INSTANCE;
    }

    private static PolicyCommandToModifyExceptionRegistry createInstance() {
        final Map<String, Function<PolicyCommand, DittoRuntimeException>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(DeletePolicy.TYPE, command -> PolicyNotModifiableException.newBuilder(command.getId())
                .dittoHeaders(command.getDittoHeaders())
                .build());
        mappingStrategies.put(DeletePolicyEntry.TYPE, command -> PolicyEntryNotModifiableException.newBuilder(command.getId(),
                ((DeletePolicyEntry) command).getLabel())
                .dittoHeaders(command.getDittoHeaders())
                .build());
        mappingStrategies.put(DeleteResource.TYPE, command ->
                ResourceNotModifiableException.newBuilder(command.getId(), ((DeleteResource) command).getLabel(),
                        command.getResourcePath().toString())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(DeleteSubject.TYPE, command ->
                SubjectNotModifiableException.newBuilder(command.getId(), ((DeleteSubject) command).getLabel(),
                        ((DeleteSubject) command).getSubjectId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(CreatePolicy.TYPE, command -> PolicyNotAccessibleException.newBuilder(command.getId())
                .dittoHeaders(command.getDittoHeaders())
                .build());
        mappingStrategies.put(ModifyPolicy.TYPE, command -> PolicyNotModifiableException.newBuilder(command.getId())
                .dittoHeaders(command.getDittoHeaders())
                .build());
        mappingStrategies.put(ModifyPolicyEntries.TYPE,
                command -> PolicyNotModifiableException.newBuilder(command.getId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(ModifyPolicyEntry.TYPE,
                command -> PolicyEntryNotModifiableException.newBuilder(command.getId(),
                        ((ModifyPolicyEntry) command).getPolicyEntry().getLabel())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(ModifyResource.TYPE, command -> ResourceNotModifiableException.newBuilder(command.getId(),
                ((ModifyResource) command).getLabel(), command.getResourcePath().toString())
                .dittoHeaders(command.getDittoHeaders())
                .build());
        mappingStrategies.put(ModifyResources.TYPE,
                command -> ResourcesNotModifiableException.newBuilder(command.getId(),
                        ((ModifyResources) command).getLabel())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(ModifySubject.TYPE, command -> SubjectNotModifiableException.newBuilder(command.getId(),
                ((ModifySubject) command).getLabel(),
                ((ModifySubject) command).getSubject().getId())
                .dittoHeaders(command.getDittoHeaders())
                .build());
        mappingStrategies.put(ModifySubjects.TYPE, command -> SubjectsNotModifiableException.newBuilder(command.getId(),
                ((ModifySubjects) command).getLabel())
                .dittoHeaders(command.getDittoHeaders())
                .build());

        return new PolicyCommandToModifyExceptionRegistry(mappingStrategies);
    }

}
