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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProviderExtension;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Pre-Enforcer for authorizing modifications to policy imports.
 */
public class PolicyImportsPreEnforcer implements PreEnforcer {

    private static final ThreadSafeDittoLogger LOG =
            DittoLoggerFactory.getThreadSafeLogger(PolicyImportsPreEnforcer.class);
    private static final String POLICY_RESOURCE = "policy";
    private static final String ENTRIES_PREFIX = "/entries/";
    private final PolicyEnforcerProvider policyEnforcerProvider;

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
     * Package-private constructor to pass a PolicyEnforcerProvider in tests.
     *
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

        if (LOG.isDebugEnabled()) {
            LOG.withCorrelationId(command)
                    .debug("Applying policy import pre-enforcement on policy <{}>.", command.getEntityId());
        }

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        return policyImportStream.map(
                        policyImport -> getPolicyEnforcer(policyImport.getImportedPolicyId(), dittoHeaders).thenApply(
                                importedPolicyEnforcer -> authorize(command, importedPolicyEnforcer,
                                        policyImport)))
                .reduce(CompletableFuture.completedStage(true), (s1, s2) -> s1.thenCombine(s2, (b1, b2) -> b1 && b2))
                .thenApply(ignored -> command);
    }

    private CompletionStage<PolicyEnforcer> getPolicyEnforcer(final PolicyId policyId,
            final DittoHeaders dittoHeaders) {
        return policyEnforcerProvider.getPolicyEnforcer(policyId)
                .thenApply(policyEnforcerOpt -> policyEnforcerOpt.orElseThrow(
                        policyNotAccessible(policyId, dittoHeaders)));
    }

    private static Supplier<PolicyNotAccessibleException> policyNotAccessible(
            final PolicyId policyId, final DittoHeaders dittoHeaders) {
        return () -> PolicyNotAccessibleException.newBuilder(policyId).dittoHeaders(dittoHeaders).build();
    }

    private static Set<ResourceKey> getImportedResourceKeys(final Policy importedPolicy, final PolicyImport policyImport) {
        final Stream<Label> implicitImports = importedPolicy.stream()
                .filter(entry -> ImportableType.IMPLICIT.equals(entry.getImportableType()))
                .map(PolicyEntry::getLabel);

        final Stream<Label> explicitImports =
                policyImport.getEffectedImports().orElse(PoliciesModelFactory.emptyEffectedImportedEntries())
                        .getImportedLabels()
                        .stream();

        return Stream.concat(implicitImports, explicitImports)
                .map(l -> ENTRIES_PREFIX + l)
                .map(path -> ResourceKey.newInstance(POLICY_RESOURCE, path))
                .collect(Collectors.toSet());
    }

    private boolean authorize(final PolicyModifyCommand<?> command, final PolicyEnforcer policyEnforcer,
            final PolicyImport policyImport) {
        final Enforcer enforcer = policyEnforcer.getEnforcer();
        final Policy importedPolicy = policyEnforcer.getPolicy().orElseThrow(policyNotAccessible(command.getEntityId(), command.getDittoHeaders()));
        final Set<ResourceKey> resourceKeys = getImportedResourceKeys(importedPolicy, policyImport);
        final AuthorizationContext authorizationContext = command.getDittoHeaders().getAuthorizationContext();
        // the authorized subject must have READ access on the given entries of the imported policy
        final boolean hasAccess =
                enforcer.hasUnrestrictedPermissions(resourceKeys, authorizationContext, Permission.READ);
        if (LOG.isDebugEnabled()) {
            LOG.withCorrelationId(command)
                    .debug("Enforcement result for command <{}> and policy import {}: {}.", command, policyImport,
                            hasAccess ? "authorized" : "not authorized");
        }
        if (!hasAccess) {
            throw errorForPolicyModifyCommand(policyImport);
        } else {
            return true;
        }
    }

    private static DittoRuntimeException errorForPolicyModifyCommand(final PolicyImport policyImport) {
        return PolicyNotAccessibleException.newBuilder(policyImport.getImportedPolicyId())
                .description("Check if the ID of the imported Policy was correct and you " +
                        "have sufficient permissions on all imported policy entries.")
                .build();
    }
}
