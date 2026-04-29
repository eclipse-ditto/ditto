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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProviderExtension;
import org.eclipse.ditto.policies.model.EntryReference;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImportInvalidException;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntries;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntry;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyEntryReferences;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;

import com.typesafe.config.Config;

/**
 * Pre-Enforcer for authorizing modifications to policy imports and entry references.
 * <p>
 * Performs three checks for each import reference: the referenced entry exists, its
 * {@code importable} type is not {@code NEVER}, and the caller has READ access on it. These checks
 * load the referenced policy via {@link PolicyEnforcerProvider} and are not cache-dependent in
 * behavior — a cache miss only adds latency, not a different outcome.
 * <p>
 * {@code allowedAdditions} is intentionally NOT enforced here. It is a runtime filter
 * applied by {@code PolicyImporter} during reference resolution; own additions on a referencing
 * entry that violate the strictest {@code allowedAdditions} across all import refs are
 * silently stripped at enforcement time. Persisted state may therefore contain own additions that
 * are not effective at runtime — by design.
 * <p>
 * The check that import references target a policy declared in the importing policy's imports
 * list is performed: (1) here for {@code CreatePolicy}/{@code ModifyPolicy} where the imports list
 * is in the command itself, and (2) by the persistence-actor strategy for all other commands
 * against authoritative state. The pre-enforcer never relies on a cache lookup of the importing
 * policy for this check, to keep behavior cache-independent.
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
            return doApply(modifyPolicy.getPolicy().getPolicyImports().stream(), modifyPolicy)
                    .thenCompose(s -> validateImportRefsInEntries(modifyPolicy.getPolicy(), modifyPolicy));
        } else if (signal instanceof CreatePolicy createPolicy) {
            return doApply(createPolicy.getPolicy().getPolicyImports().stream(), createPolicy)
                    .thenCompose(s -> validateImportRefsInEntries(createPolicy.getPolicy(), createPolicy));
        } else if (signal instanceof ModifyPolicyImports modifyPolicyImports) {
            return doApply(modifyPolicyImports.getPolicyImports().stream(), modifyPolicyImports);
        } else if (signal instanceof ModifyPolicyImport modifyPolicyImport) {
            return doApply(Stream.of(modifyPolicyImport.getPolicyImport()), modifyPolicyImport);
        } else if (signal instanceof ModifyPolicyEntryReferences modifyReferences) {
            return validateReferencesModification(modifyReferences);
        } else if (signal instanceof ModifyPolicyEntry modifyPolicyEntry) {
            return validateImportRefsInEntries(List.of(modifyPolicyEntry.getPolicyEntry()),
                    modifyPolicyEntry);
        } else if (signal instanceof ModifyPolicyEntries modifyPolicyEntries) {
            final List<PolicyEntry> entries = java.util.stream.StreamSupport
                    .stream(modifyPolicyEntries.getPolicyEntries().spliterator(), false)
                    .collect(Collectors.toList());
            return validateImportRefsInEntries(entries, modifyPolicyEntries);
        } else {
            return CompletableFuture.completedFuture(signal);
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
                .reduce(CompletableFuture.completedFuture(true), (s1, s2) -> s1.thenCombine(s2, (b1, b2) -> b1 && b2))
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
        }

        return true;
    }

    /**
     * Validates import references found in the entries of a whole policy (used for CreatePolicy/ModifyPolicy).
     * Since the full target policy is in the command, the imports list is authoritative here and the
     * check that import refs target declared imports is performed synchronously.
     */
    private CompletionStage<Signal<?>> validateImportRefsInEntries(final Policy policy,
            final PolicyModifyCommand<?> command) {
        final Set<PolicyId> declaredImports = policy.getPolicyImports().stream()
                .map(PolicyImport::getImportedPolicyId)
                .collect(Collectors.toSet());
        return validateImportRefsInEntries(
                java.util.stream.StreamSupport.stream(policy.spliterator(), false).collect(Collectors.toList()),
                declaredImports, command);
    }

    /**
     * Validates import references found in the given entries. For each import reference, loads the referenced
     * policy and verifies entry existence, importable type, and READ access. When {@code declaredImports} is
     * non-null, also verifies that each import reference targets a policy declared in the importing policy's
     * imports. Pass {@code null} for callers that do not have authoritative access to the imports list (e.g.
     * ModifyPolicyEntry/ModifyPolicyEntries) — the persistence-actor strategy validates this case.
     */
    private CompletionStage<Signal<?>> validateImportRefsInEntries(final List<PolicyEntry> entries,
            @javax.annotation.Nullable final Set<PolicyId> declaredImports,
            final PolicyModifyCommand<?> command) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        for (final PolicyEntry entry : entries) {
            for (final EntryReference ref : entry.getReferences()) {
                if (ref.isImportReference() && declaredImports != null) {
                    final PolicyId referencedPolicyId = ref.getImportedPolicyId().orElseThrow(() ->
                            new IllegalStateException("Import reference without imported policy ID"));
                    if (!declaredImports.contains(referencedPolicyId)) {
                        final CompletableFuture<Signal<?>> failed = new CompletableFuture<>();
                        failed.completeExceptionally(PolicyImportInvalidException.newBuilder()
                                .message("A reference points to policy '" + referencedPolicyId +
                                        "' which is not in this policy's imports.")
                                .description("Add an import for policy '" + referencedPolicyId +
                                        "' before referencing its entries.")
                                .dittoHeaders(dittoHeaders)
                                .build());
                        return failed;
                    }
                }
            }
        }

        final List<CompletableFuture<Boolean>> validations = new java.util.ArrayList<>();
        for (final PolicyEntry entry : entries) {
            for (final EntryReference ref : entry.getReferences()) {
                if (ref.isImportReference()) {
                    validations.add(
                            validateSingleImportReference(ref, dittoHeaders).toCompletableFuture());
                }
            }
        }

        if (validations.isEmpty()) {
            return CompletableFuture.completedFuture(command);
        }

        return CompletableFuture.allOf(validations.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> command);
    }

    /**
     * Overload for callers that do not have authoritative access to the importing policy's imports list
     * (e.g. ModifyPolicyEntry/ModifyPolicyEntries). The import-declaration check is skipped here — the
     * persistence-actor strategy enforces it against the authoritative state.
     */
    private CompletionStage<Signal<?>> validateImportRefsInEntries(final List<PolicyEntry> entries,
            final PolicyModifyCommand<?> command) {
        return validateImportRefsInEntries(entries, null, command);
    }

    /**
     * Validates a single import reference: loads the referenced policy, verifies entry existence,
     * importable type, and READ access on the referenced entry. {@code allowedAdditions} is
     * intentionally NOT checked here — it is enforced at resolution time as a runtime filter on
     * own additions, not as a write-time policy contract. See {@link PolicyImporter#resolveReferences}.
     */
    private CompletionStage<Boolean> validateSingleImportReference(final EntryReference ref,
            final DittoHeaders dittoHeaders) {

        final PolicyId referencedPolicyId = ref.getImportedPolicyId()
                .orElseThrow(() -> new IllegalStateException("Import reference without imported policy ID"));
        final Label referencedEntryLabel = ref.getEntryLabel();

        return getPolicyEnforcer(referencedPolicyId, dittoHeaders)
                .thenApply(importedEnforcer -> {
                    final Policy importedPolicy = importedEnforcer.getPolicy()
                            .orElseThrow(policyNotAccessible(referencedPolicyId, dittoHeaders));
                    final Optional<PolicyEntry> entryOpt =
                            importedPolicy.getEntryFor(referencedEntryLabel);
                    if (!entryOpt.isPresent()) {
                        throw PolicyImportInvalidException.newBuilder()
                                .message("A reference targets entry '" +
                                        referencedEntryLabel + "' of policy '" +
                                        referencedPolicyId + "' which does not exist.")
                                .dittoHeaders(dittoHeaders)
                                .build();
                    }
                    final PolicyEntry referencedEntry = entryOpt.get();
                    if (referencedEntry.getImportableType() == ImportableType.NEVER) {
                        throw PolicyImportInvalidException.newBuilder()
                                .message("A reference targets entry '" +
                                        referencedEntryLabel + "' of policy '" +
                                        referencedPolicyId +
                                        "' which is marked as importable='never'.")
                                .dittoHeaders(dittoHeaders)
                                .build();
                    }
                    final ResourceKey resourceKey = ResourceKey.newInstance(
                            POLICY_RESOURCE, ENTRIES_PREFIX + referencedEntryLabel);
                    final AuthorizationContext authCtx = dittoHeaders.getAuthorizationContext();
                    if (!importedEnforcer.getEnforcer().hasUnrestrictedPermissions(
                            Set.of(resourceKey), authCtx, Permission.READ)) {
                        throw PolicyNotAccessibleException.newBuilder(referencedPolicyId)
                                .description("Insufficient permissions to reference entry '" +
                                        referencedEntryLabel + "' of policy '" +
                                        referencedPolicyId + "'.")
                                .dittoHeaders(dittoHeaders)
                                .build();
                    }
                    return true;
                });
    }

    /**
     * Validates a {@link ModifyPolicyEntryReferences} command. For each import reference, verifies
     * that the referenced entry exists, is importable (importable != NEVER), and that the caller
     * has READ access on it. The check that import refs target a policy declared in the importing
     * policy's imports is performed by the persistence-actor strategy against authoritative state
     * (avoids cache-dependent behavior in the pre-enforcer). Local references are not validated
     * here — the strategy handles entry existence.
     */
    private CompletionStage<Signal<?>> validateReferencesModification(
            final ModifyPolicyEntryReferences command) {

        final List<EntryReference> importReferences = command.getReferences().stream()
                .filter(EntryReference::isImportReference)
                .collect(Collectors.toList());

        if (importReferences.isEmpty()) {
            return CompletableFuture.completedFuture(command);
        }

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        return importReferences.stream()
                .map(ref -> validateSingleImportReference(ref, dittoHeaders).toCompletableFuture())
                .reduce(CompletableFuture.completedFuture(true),
                        (s1, s2) -> s1.thenCombine(s2, (b1, b2) -> b1 && b2))
                .thenApply(ignored -> command);
    }

    private static DittoRuntimeException errorForPolicyModifyCommand(final PolicyImport policyImport) {
        return PolicyNotAccessibleException.newBuilder(policyImport.getImportedPolicyId())
                .description("Check if the ID of the imported Policy was correct and you " +
                        "have sufficient permissions on all imported policy entries.")
                .build();
    }
}
