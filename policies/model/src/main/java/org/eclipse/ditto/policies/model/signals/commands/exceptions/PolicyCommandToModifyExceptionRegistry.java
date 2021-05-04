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
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteResource;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeleteSubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntries;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResource;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResources;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubjects;

/**
 * Registry to map policy commands to their according modify exception.
 */
public final class PolicyCommandToModifyExceptionRegistry
        extends AbstractCommandToExceptionRegistry<PolicyCommand<?>, DittoRuntimeException> {

    private static final PolicyCommandToModifyExceptionRegistry INSTANCE = createInstance();

    private PolicyCommandToModifyExceptionRegistry(
            final Map<String, Function<PolicyCommand<?>, DittoRuntimeException>> mappingStrategies) {
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
        final Map<String, Function<PolicyCommand<?>, DittoRuntimeException>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(DeletePolicy.TYPE,
                command -> PolicyNotModifiableException.newBuilder(command.getEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(DeletePolicyEntry.TYPE,
                command -> PolicyEntryNotModifiableException.newBuilder(command.getEntityId(),
                        ((DeletePolicyEntry) command).getLabel())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(DeleteResource.TYPE, command ->
                ResourceNotModifiableException.newBuilder(command.getEntityId(), ((DeleteResource) command).getLabel(),
                        command.getResourcePath().toString())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(DeleteSubject.TYPE, command ->
                SubjectNotModifiableException.newBuilder(command.getEntityId(), ((DeleteSubject) command).getLabel(),
                        ((DeleteSubject) command).getSubjectId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(CreatePolicy.TYPE,
                command -> PolicyNotAccessibleException.newBuilder(command.getEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(ModifyPolicy.TYPE,
                command -> PolicyNotModifiableException.newBuilder(command.getEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(ModifyPolicyEntries.TYPE,
                command -> PolicyNotModifiableException.newBuilder(command.getEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(ModifyPolicyEntry.TYPE,
                command -> PolicyEntryNotModifiableException.newBuilder(command.getEntityId(),
                        ((ModifyPolicyEntry) command).getPolicyEntry().getLabel())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(ModifyResource.TYPE,
                command -> ResourceNotModifiableException.newBuilder(command.getEntityId(),
                        ((ModifyResource) command).getLabel(), command.getResourcePath().toString())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(ModifyResources.TYPE,
                command -> ResourcesNotModifiableException.newBuilder(command.getEntityId(),
                        ((ModifyResources) command).getLabel())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(ModifySubject.TYPE,
                command -> SubjectNotModifiableException.newBuilder(command.getEntityId(),
                        ((ModifySubject) command).getLabel(),
                        ((ModifySubject) command).getSubject().getId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
        mappingStrategies.put(ModifySubjects.TYPE,
                command -> SubjectsNotModifiableException.newBuilder(command.getEntityId(),
                        ((ModifySubjects) command).getLabel())
                        .dittoHeaders(command.getDittoHeaders())
                        .build());

        return new PolicyCommandToModifyExceptionRegistry(mappingStrategies);
    }

}
