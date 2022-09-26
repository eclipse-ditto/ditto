/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement.pre;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProviderExtension;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyImportNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Pre-Enforcer for authorizing modifications to policy imports.
 */
public class PolicyImportsPreEnforcer implements PreEnforcer {

    private static final String POLICY_RESOURCE = "policy";
    public static final String ENTRIES_PREFIX = "/entries/";
    private final PolicyEnforcerProvider policyEnforcerProvider;

    // TOOO DG logging
    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyImportsPreEnforcer.class);

    /**
     * Constructs a new instance of PolicyImportsPreEnforcer extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    @SuppressWarnings("unused")
    public PolicyImportsPreEnforcer(final ActorSystem actorSystem, final Config config) {
        policyEnforcerProvider = PolicyEnforcerProviderExtension.get(actorSystem).getPolicyEnforcerProvider();
    }

    /**
     * package-private constructor to pass a PolicyEnforcerProvider in tests.
     * @param policyEnforcerProvider a PolicyEnforcerProvider
     */
    PolicyImportsPreEnforcer(final PolicyEnforcerProvider policyEnforcerProvider) {
        this.policyEnforcerProvider = policyEnforcerProvider;
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal) {
        if (signal instanceof ModifyPolicy modifyPolicy) {
            return doApply(modifyPolicy.getPolicy().getPolicyImports().stream(), modifyPolicy);
        } else if (signal instanceof CreatePolicy createPolicy) {
            return doApply(createPolicy.getPolicy().getPolicyImports().stream(), createPolicy);
        } else if (signal instanceof ModifyPolicyImports modifyPolicyImports) {
            return doApply(modifyPolicyImports.getPolicyImports().stream(), modifyPolicyImports);
        } else if (signal instanceof ModifyPolicyImport modifyPolicyImport) {
            return doApply(Stream.of(modifyPolicyImport.getPolicyImport()), modifyPolicyImport);
        } else {
            return CompletableFuture.completedStage(signal);
        }
    }

    private CompletionStage<Signal<?>> doApply(final Stream<PolicyImport> policyImportStream,
            final PolicyModifyCommand<?> command) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        return policyImportStream.map(
                        policyImport -> getPolicyEnforcer(policyImport.getImportedPolicyId(), dittoHeaders).thenApply(
                                importedPolicyEnforcer -> authorize(command, importedPolicyEnforcer.getEnforcer(),
                                        policyImport)))
                .reduce(CompletableFuture.completedStage(true), (s1, s2) -> s1.thenCombine(s2, (b1, b2) -> b1 && b2))
                .thenApply(ignored -> command);
    }

    private CompletionStage<PolicyEnforcer> getPolicyEnforcer(final PolicyId policyId,
            final DittoHeaders dittoHeaders) {
        return policyEnforcerProvider.getPolicyEnforcer(policyId)
                .thenApply(policyEnforcerOpt -> policyEnforcerOpt.orElseThrow(
                        () -> PolicyNotAccessibleException.newBuilder(policyId).dittoHeaders(dittoHeaders).build()));
    }

    private static Set<ResourceKey> getResourceKeys(final PolicyImport policyImport) {
        return policyImport.getEffectedImports().orElse(PoliciesModelFactory.emptyEffectedImportedEntries())
                .getImportedLabels()
                .stream()
                .map(l -> ENTRIES_PREFIX + l)
                .map(path -> ResourceKey.newInstance(POLICY_RESOURCE, path))
                .collect(Collectors.toSet());
    }

    private boolean authorize(final PolicyModifyCommand<?> command, final Enforcer enforcer,
            final PolicyImport policyImport) {
        final Set<ResourceKey> resourceKeys = getResourceKeys(policyImport);
        final AuthorizationContext authorizationContext = command.getDittoHeaders().getAuthorizationContext();
        final boolean hasAccess =
                enforcer.hasUnrestrictedPermissions(resourceKeys, authorizationContext, Permission.READ);
        if (!hasAccess) {
            throw errorForPolicyModifyCommand(command, policyImport);
        } else {
            return true;
        }
    }

    private static DittoRuntimeException errorForPolicyModifyCommand(final PolicyModifyCommand<?> policyModifyCommand,
            final PolicyImport policyImport) {
        return PolicyImportNotAccessibleException.newBuilder(policyModifyCommand.getEntityId(),
                policyImport.getImportedPolicyId()).build();
    }
}
