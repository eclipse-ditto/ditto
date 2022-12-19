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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.persistentactors.commands.AbstractCommandStrategies;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.PolicyConfig;

import akka.actor.ActorSystem;

/**
 * Command strategies of {@code PolicyPersistenceActor}.
 */
public final class PolicyCommandStrategies
        extends AbstractCommandStrategies<Command<?>, Policy, PolicyId, PolicyEvent<?>> {

    @SuppressWarnings("java:S3077") // volatile because of double checked locking pattern
    @Nullable private static volatile PolicyCommandStrategies instance;
    @SuppressWarnings("java:S3077") // volatile because of double checked locking pattern
    @Nullable private static volatile CreatePolicyStrategy createPolicyStrategy;

    private PolicyCommandStrategies(final PolicyConfig policyConfig, final ActorSystem system) {
        super(Command.class);

        // Policy level
        addStrategy(new PolicyConflictStrategy(policyConfig));
        addStrategy(new ModifyPolicyStrategy(policyConfig));
        addStrategy(new RetrievePolicyStrategy(policyConfig));
        addStrategy(new DeletePolicyStrategy(policyConfig));
        addStrategy(new TopLevelPolicyActionCommandStrategy(policyConfig, system));

        // Policy Entries
        addStrategy(new ModifyPolicyEntriesStrategy(policyConfig));
        addStrategy(new RetrievePolicyEntriesStrategy(policyConfig));

        // Policy Entry
        addStrategy(new ModifyPolicyEntryStrategy(policyConfig));
        addStrategy(new RetrievePolicyEntryStrategy(policyConfig));
        addStrategy(new DeletePolicyEntryStrategy(policyConfig));
        addStrategy(new ActivateTokenIntegrationStrategy(policyConfig, system));
        addStrategy(new DeactivateTokenIntegrationStrategy(policyConfig, system));

        // Policy Import
        addStrategy(new ModifyPolicyImportsStrategy(policyConfig));
        addStrategy(new ModifyPolicyImportStrategy(policyConfig));
        addStrategy(new RetrievePolicyImportsStrategy(policyConfig));
        addStrategy(new RetrievePolicyImportStrategy(policyConfig));
        addStrategy(new DeletePolicyImportStrategy(policyConfig));

        // Subjects
        addStrategy(new ModifySubjectsStrategy(policyConfig));
        addStrategy(new ModifySubjectStrategy(policyConfig));
        addStrategy(new RetrieveSubjectsStrategy(policyConfig));
        addStrategy(new RetrieveSubjectStrategy(policyConfig));
        addStrategy(new DeleteSubjectStrategy(policyConfig));

        // Resources
        addStrategy(new ModifyResourcesStrategy(policyConfig));
        addStrategy(new ModifyResourceStrategy(policyConfig));
        addStrategy(new RetrieveResourcesStrategy(policyConfig));
        addStrategy(new RetrieveResourceStrategy(policyConfig));
        addStrategy(new DeleteResourceStrategy(policyConfig));

        // Sudo
        addStrategy(new SudoRetrievePolicyStrategy(policyConfig));
        addStrategy(new SudoRetrievePolicyRevisionStrategy(policyConfig));
        addStrategy(new SudoDeleteExpiredSubjectStrategy(policyConfig));
    }

    /**
     * @param policyConfig the PolicyConfig of the Policy service to apply.
     * @param system the Akka ActorSystem to use in order to e.g. dynamically load classes.
     * @return command strategies for policy persistence actor.
     */
    public static PolicyCommandStrategies getInstance(final PolicyConfig policyConfig,
            final ActorSystem system) {
        PolicyCommandStrategies localInstance = instance;
        if (null == localInstance) {
            synchronized (PolicyCommandStrategies.class) {
                localInstance = instance;
                if (null == localInstance) {
                    instance = localInstance = new PolicyCommandStrategies(policyConfig, system);
                }
            }
        }
        return localInstance;
    }

    /**
     * @param policyConfig the PolicyConfig of the Policy service to apply.
     * @return command strategy to create a policy.
     */
    public static CommandStrategy<CreatePolicy, Policy, PolicyId, PolicyEvent<?>> getCreatePolicyStrategy(
            final PolicyConfig policyConfig) {
        CreatePolicyStrategy localCreatePolicyStrategy = createPolicyStrategy;
        if (null == localCreatePolicyStrategy) {
            synchronized (PolicyCommandStrategies.class) {
                localCreatePolicyStrategy = createPolicyStrategy;
                if (null == localCreatePolicyStrategy) {
                    createPolicyStrategy = localCreatePolicyStrategy = new CreatePolicyStrategy(policyConfig);
                }
            }
        }
        return localCreatePolicyStrategy;
    }

    @Override
    protected Result<PolicyEvent<?>> getEmptyResult() {
        return ResultFactory.emptyResult();
    }

}
