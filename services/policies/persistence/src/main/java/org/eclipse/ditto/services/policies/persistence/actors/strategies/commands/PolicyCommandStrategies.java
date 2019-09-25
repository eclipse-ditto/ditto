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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.commands;

import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.utils.persistentactors.commands.AbstractCommandStrategies;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * Command strategies of {@code PolicyPersistenceActor}.
 */
public final class PolicyCommandStrategies
        extends AbstractCommandStrategies<Command, Policy, PolicyId, Result<PolicyEvent>> {

    private static final PolicyCommandStrategies INSTANCE = new PolicyCommandStrategies();
    private static final CreatePolicyStrategy CREATE_POLICY_STRATEGY = new CreatePolicyStrategy();

    private PolicyCommandStrategies() {
        super(Command.class);

        // Policy level
        addStrategy(new PolicyConflictStrategy());
        addStrategy(new ModifyPolicyStrategy());
        addStrategy(new RetrievePolicyStrategy());
        addStrategy(new DeletePolicyStrategy());

        // Policy Entries
        addStrategy(new ModifyPolicyEntriesStrategy());
        addStrategy(new RetrievePolicyEntriesStrategy());

        // Policy Entry
        addStrategy(new ModifyPolicyEntryStrategy());
        addStrategy(new RetrievePolicyEntryStrategy());
        addStrategy(new DeletePolicyEntryStrategy());

        // Subjects
        addStrategy(new ModifySubjectsStrategy());
        addStrategy(new ModifySubjectStrategy());
        addStrategy(new RetrieveSubjectsStrategy());
        addStrategy(new RetrieveSubjectStrategy());
        addStrategy(new DeleteSubjectStrategy());

        // Resources
        addStrategy(new ModifyResourcesStrategy());
        addStrategy(new ModifyResourceStrategy());
        addStrategy(new RetrieveResourcesStrategy());
        addStrategy(new RetrieveResourceStrategy());
        addStrategy(new DeleteResourceStrategy());

        // Sudo
        addStrategy(new SudoRetrievePolicyStrategy());
    }

    /**
     * @return command strategies for policy persistence actor.
     */
    public static PolicyCommandStrategies getInstance() {
        return INSTANCE;
    }

    /**
     * @return command strategy to create a policy.
     */
    public static CommandStrategy<CreatePolicy, Policy, PolicyId, Result<PolicyEvent>> getCreatePolicyStrategy() {
        return CREATE_POLICY_STRATEGY;
    }

    @Override
    protected Result<PolicyEvent> getEmptyResult() {
        return ResultFactory.emptyResult();
    }

}
