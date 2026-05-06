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
package org.eclipse.ditto.policies.service.enforcement;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandToExceptionRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.AbstractEnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyView;
import org.eclipse.ditto.policies.model.PolicyViewInvalidException;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.actions.PolicyActionCommand;
import org.eclipse.ditto.policies.model.signals.commands.actions.TopLevelPolicyActionCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyCommandToAccessExceptionRegistry;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyCommandToActionsExceptionRegistry;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyCommandToModifyExceptionRegistry;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;

/**
 * Authorizes {@link PolicyCommand}s and filters {@link PolicyCommandResponse}s.
 */
public final class PolicyCommandEnforcement
        extends AbstractEnforcementReloaded<Signal<?>, PolicyCommandResponse<?>> {

    /**
     * Json fields that are always shown regardless of authorization.
     */
    private static final JsonFieldSelector POLICY_QUERY_COMMAND_RESPONSE_ALLOWLIST =
            JsonFactory.newFieldSelector(Policy.JsonFields.ID);

    private static final JsonParseOptions JSON_PARSE_OPTIONS_FOR_FIELDS_SELECTOR =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();

    private final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver;
    private final NamespacePoliciesConfig namespacePoliciesConfig;

    public PolicyCommandEnforcement(
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver,
            final NamespacePoliciesConfig namespacePoliciesConfig) {
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
        this.namespacePoliciesConfig = Objects.requireNonNull(namespacePoliciesConfig, "namespacePoliciesConfig");
    }

    @Override
    public CompletionStage<Signal<?>> authorizeSignal(final Signal<?> signal,
            final PolicyEnforcer policyEnforcer) {

        if (signal instanceof Command<?> command &&
                command.getCategory() == Command.Category.QUERY && !command.getDittoHeaders().isResponseRequired()) {
            // ignore query command with response-required=false
            return CompletableFuture.completedFuture(null);
        }

        final Enforcer enforcer = policyEnforcer.getEnforcer();
        final var policyResourceKey = PoliciesResourceType.policyResource(signal.getResourcePath());
        final var authorizationContext = signal.getDittoHeaders().getAuthorizationContext();
        final Signal<?> authorizedCommand;
        if (signal instanceof CreatePolicy createPolicy) {
            authorizedCommand = authorizeCreatePolicy(enforcer, createPolicy, policyResourceKey, authorizationContext);
        } else if (signal instanceof PolicyActionCommand) {
            authorizedCommand = authorizeActionCommand(policyEnforcer, signal, policyResourceKey,
                    authorizationContext).orElseThrow(() -> errorForPolicyCommand(signal));
        } else if (signal instanceof PolicyModifyCommand) {
            if (hasUnrestrictedWritePermission(enforcer, policyResourceKey, authorizationContext)) {
                authorizedCommand = signal;
            } else {
                throw errorForPolicyCommand(signal);
            }
        } else {
            final String permission = Permission.READ;
            if (enforcer.hasPartialPermissions(policyResourceKey,
                    authorizationContext,
                    permission)) {
                authorizedCommand = stripConditionalHeadersForResolvedView(signal);
            } else {
                throw errorForPolicyCommand(signal);
            }
        }

        return CompletableFuture.completedFuture(authorizedCommand);
    }

    // For policy-view=resolved, drop If-Match / If-None-Match so the conditional-header strategy can't 304 on the
    // importing policy's revision alone (the merged content can change without that revision moving).
    private static Signal<?> stripConditionalHeadersForResolvedView(final Signal<?> signal) {
        if (!(signal instanceof RetrievePolicy retrievePolicy) || !isResolvedView(retrievePolicy.getDittoHeaders())) {
            return signal;
        }
        final DittoHeaders headers = retrievePolicy.getDittoHeaders();
        if (!headers.containsKey(DittoHeaderDefinition.IF_MATCH.getKey())
                && !headers.containsKey(DittoHeaderDefinition.IF_NONE_MATCH.getKey())) {
            return signal;
        }
        final DittoHeaders stripped = headers.toBuilder()
                .removeHeader(DittoHeaderDefinition.IF_MATCH.getKey())
                .removeHeader(DittoHeaderDefinition.IF_NONE_MATCH.getKey())
                .build();
        return retrievePolicy.setDittoHeaders(stripped);
    }

    private static boolean isResolvedView(final DittoHeaders headers) {
        try {
            return PolicyView.from(headers).map(PolicyView::isResolved).orElse(false);
        } catch (final PolicyViewInvalidException e) {
            throw e.setDittoHeaders(headers);
        }
    }

    private PolicyCommand<?> authorizeCreatePolicy(final Enforcer enforcer,
            final CreatePolicy createPolicy,
            final ResourceKey policyResourceKey,
            final AuthorizationContext authorizationContext) {

        if (hasUnrestrictedWritePermission(enforcer, policyResourceKey, authorizationContext) ||
                createPolicy.getDittoHeaders().isAllowPolicyLockout()) {
            return createPolicy;
        } else {
            throw errorForPolicyCommand(createPolicy);
        }
    }

    @Override
    public CompletionStage<Signal<?>> authorizeSignalWithMissingEnforcer(final Signal<?> command) {
        final PolicyId policyId;
        if (command instanceof WithEntityId withEntityId) {
            policyId = PolicyId.of(withEntityId.getEntityId());
        } else {
            LOGGER.warn("Processed signal which does not have an entityId: {}", command);
            throw DittoInternalErrorException.newBuilder()
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        }
        throw PolicyNotAccessibleException.newBuilder(policyId)
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    @Override
    public boolean shouldFilterCommandResponse(final PolicyCommandResponse<?> commandResponse) {
        return commandResponse instanceof PolicyQueryCommandResponse<?>;
    }

    @Override
    public CompletionStage<PolicyCommandResponse<?>> filterResponse(final PolicyCommandResponse<?> commandResponse,
            final PolicyEnforcer policyEnforcer) {

        if (commandResponse instanceof RetrievePolicyResponse retrieveResp
                && isResolvedView(retrieveResp.getDittoHeaders())) {
            return resolveAndRebuildResponse(retrieveResp)
                    .thenApply(rebuilt -> applyEnforcerJsonView(rebuilt, policyEnforcer));
        }

        final PolicyCommandResponse<?> result;
        if (commandResponse instanceof PolicyQueryCommandResponse<?> policyQueryCommandResponse) {
            result = applyEnforcerJsonView(policyQueryCommandResponse, policyEnforcer);
        } else {
            // no filtering required for non PolicyQueryCommandResponses:
            result = commandResponse;
        }
        return CompletableFuture.completedFuture(result);
    }

    private <T extends PolicyQueryCommandResponse<T>> T applyEnforcerJsonView(
            final PolicyQueryCommandResponse<T> response, final PolicyEnforcer policyEnforcer) {
        try {
            return buildJsonViewForPolicyQueryCommandResponse(response, policyEnforcer.getEnforcer());
        } catch (final DittoRuntimeException dre) {
            throw dre;
        } catch (final RuntimeException e) {
            throw reportError("Error after building JsonView", e, response.getDittoHeaders());
        }
    }

    private CompletionStage<RetrievePolicyResponse> resolveAndRebuildResponse(final RetrievePolicyResponse response) {
        final PolicyId policyId = response.getEntityId();
        return policyResolver.apply(policyId)
                .thenCompose(rawPolicyOpt -> {
                    if (rawPolicyOpt.isEmpty()) {
                        return CompletableFuture.completedFuture(response);
                    }
                    final Policy rawPolicy = rawPolicyOpt.get();
                    final AuthorizationContext authCtx = response.getDittoHeaders().getAuthorizationContext();
                    return mergeAndDropUnreadable(rawPolicy, authCtx)
                            .thenApply(filtered -> rebuildResponseWithMergedPolicy(response, filtered));
                })
                .exceptionally(throwable -> {
                    final DittoRuntimeException dre = DittoRuntimeException.asDittoRuntimeException(throwable, t -> {
                        LOGGER.withCorrelationId(response.getDittoHeaders())
                                .warn("Could not resolve effective policy view for <{}>; returning unresolved view." +
                                        " Cause: {}", policyId, t.toString());
                        return null;
                    });
                    if (dre != null) {
                        throw dre;
                    }
                    return response;
                });
    }

    /**
     * Builds the merged policy and drops every entry the caller has no READ on within its source. Without this
     * source-side check, broad READ on the importing policy would expose the contents of every imported and
     * namespace-root policy, regardless of the caller's permissions on the source.
     */
    private CompletionStage<Policy> mergeAndDropUnreadable(final Policy rawPolicy, final AuthorizationContext authCtx) {
        final Set<Label> rawLabels = rawPolicy.getEntriesSet().stream()
                .map(PolicyEntry::getLabel)
                .collect(Collectors.toUnmodifiableSet());
        final List<PolicyId> nsRootIds = namespacePoliciesConfig.getRootPoliciesForNamespace(
                rawPolicy.getNamespace().orElse(""));

        final Map<PolicyId, CompletableFuture<Optional<PolicyEnforcer>>> sources = new LinkedHashMap<>();
        for (final PolicyImport imp : rawPolicy.getPolicyImports()) {
            sources.computeIfAbsent(imp.getImportedPolicyId(), this::resolveSourceEnforcer);
        }
        for (final PolicyId nsRootId : nsRootIds) {
            sources.computeIfAbsent(nsRootId, this::resolveSourceEnforcer);
        }
        final CompletionStage<Policy> mergedStage = PolicyEnforcer
                .withResolvedImportsAndNamespacePolicies(rawPolicy, policyResolver, namespacePoliciesConfig)
                .thenApply(enforcer -> enforcer.getPolicy().orElse(rawPolicy));
        if (sources.isEmpty()) {
            return mergedStage;
        }
        final CompletableFuture<Void> allSources = CompletableFuture.allOf(
                sources.values().toArray(new CompletableFuture[0]));

        return mergedStage.thenCombine(allSources, (merged, ignored) -> {
            Policy result = merged;
            // Declared imports — labels are unique per source via the imported-<sourceId>- prefix, no precedence concern.
            for (final PolicyImport imp : rawPolicy.getPolicyImports()) {
                final PolicyEnforcer src = sources.get(imp.getImportedPolicyId()).join().orElse(null);
                if (src == null) continue;
                for (final PolicyEntry e : src.getPolicy().orElseThrow()) {
                    if (isContributedByImport(e, imp) && !hasSourceSideRead(src, e.getLabel(), authCtx)) {
                        result = result.removeEntry(PoliciesModelFactory.newImportedLabel(
                                imp.getImportedPolicyId(), e.getLabel()));
                    }
                }
            }
            // Namespace-roots — first-wins precedence mirrors PolicyEnforcer.mergeImplicitEntries; later ns-roots'
            // same-label entries are silently dropped from the merge, so checking them would inspect the wrong source.
            final Set<Label> claimed = new HashSet<>();
            for (final PolicyId nsRootId : nsRootIds) {
                final PolicyEnforcer src = sources.get(nsRootId).join().orElse(null);
                if (src == null) continue;
                for (final PolicyEntry e : src.getPolicy().orElseThrow()) {
                    if (!ImportableType.IMPLICIT.equals(e.getImportableType())) continue;
                    final Label label = e.getLabel();
                    if (rawLabels.contains(label) || !claimed.add(label)) continue;
                    if (!hasSourceSideRead(src, label, authCtx)) {
                        result = result.removeEntry(label);
                    }
                }
            }
            return result;
        });
    }

    private CompletableFuture<Optional<PolicyEnforcer>> resolveSourceEnforcer(final PolicyId sourceId) {
        return policyResolver.apply(sourceId)
                .thenCompose(opt -> opt.isPresent()
                        ? PolicyEnforcer.withResolvedImports(opt.get(), policyResolver).thenApply(Optional::of)
                        : CompletableFuture.completedFuture(Optional.<PolicyEnforcer>empty()))
                .toCompletableFuture();
    }

    private static boolean hasSourceSideRead(final PolicyEnforcer src, final Label label,
            final AuthorizationContext authCtx) {
        return src.getEnforcer().hasPartialPermissions(
                PoliciesResourceType.policyResource("/entries/" + label), authCtx, Permission.READ);
    }

    private static boolean isContributedByImport(final PolicyEntry sourceEntry, final PolicyImport imp) {
        return switch (sourceEntry.getImportableType()) {
            case IMPLICIT -> true;
            case EXPLICIT -> imp.getEffectedImports()
                    .map(EffectedImports::getImportedLabels)
                    .map(labels -> labels.contains(sourceEntry.getLabel()))
                    .orElse(false);
            case NEVER -> false;
        };
    }

    private static RetrievePolicyResponse rebuildResponseWithMergedPolicy(final RetrievePolicyResponse original,
            final Policy mergedPolicy) {
        final JsonSchemaVersion version = original.getImplementedSchemaVersion();
        final JsonObject mergedJson = mergedPolicy.toJson(version, FieldType.regularOrSpecial());
        final JsonValue originalEntity = original.getEntity(version);
        final JsonObject projected = projectMerged(mergedJson, originalEntity, original.getDittoHeaders());

        final DittoHeaders headersWithoutEtag = original.getDittoHeaders().toBuilder()
                .removeHeader(DittoHeaderDefinition.ETAG.getKey())
                .build();
        return RetrievePolicyResponse.of(original.getEntityId(), projected, headersWithoutEtag);
    }

    private static JsonObject projectMerged(final JsonObject mergedJson,
            final JsonValue originalEntity,
            final DittoHeaders headers) {
        final String selectorString = headers.get(DittoHeaderDefinition.POLICY_VIEW_FIELDS_SELECTOR.getKey());
        if (selectorString != null && !selectorString.isEmpty()) {
            // The selector was just constructed by PoliciesRoute from a successful parse; if it fails to
            // round-trip here, that's an internal bug worth surfacing rather than silently returning a wider view.
            final JsonFieldSelector selector = JsonFactory.newFieldSelector(selectorString,
                    JSON_PARSE_OPTIONS_FOR_FIELDS_SELECTOR);
            return mergedJson.get(selector);
        }
        if (!originalEntity.isObject()) {
            return mergedJson;
        }
        final List<JsonKey> originalKeys = originalEntity.asObject().getKeys();
        if (originalKeys.isEmpty()) {
            return mergedJson;
        }
        final JsonObjectBuilder builder = JsonObject.newBuilder();
        for (final JsonKey key : originalKeys) {
            mergedJson.getValue(key).ifPresentOrElse(
                    value -> builder.set(key.toString(), value),
                    () -> originalEntity.asObject().getValue(key)
                            .ifPresent(value -> builder.set(key.toString(), value))
            );
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private <T extends Signal<?>> Optional<T> authorizeActionCommand(
            final PolicyEnforcer enforcer,
            final T command, final ResourceKey resourceKey, final AuthorizationContext authorizationContext) {

        if (command instanceof TopLevelPolicyActionCommand topLevelPolicyActionCommand) {
            return (Optional<T>) authorizeTopLevelAction(enforcer, topLevelPolicyActionCommand, authorizationContext);
        } else {
            return authorizeEntryLevelAction(enforcer.getEnforcer(), command, resourceKey, authorizationContext);
        }
    }

    private <T extends Signal<?>> Optional<T> authorizeEntryLevelAction(final Enforcer enforcer,
            final T command, final ResourceKey resourceKey, final AuthorizationContext authorizationContext) {
        return enforcer.hasUnrestrictedPermissions(resourceKey, authorizationContext, Permission.EXECUTE) ?
                Optional.of(command) : Optional.empty();
    }

    private Optional<TopLevelPolicyActionCommand> authorizeTopLevelAction(
            final PolicyEnforcer policyEnforcer,
            final TopLevelPolicyActionCommand command,
            final AuthorizationContext authorizationContext) {

        final var enforcer = policyEnforcer.getEnforcer();

        final List<Label> labels = getLabelsFromPolicyEnforcer(policyEnforcer);
        final var authorizedLabels = labels.stream()
                .filter(label -> enforcer.hasUnrestrictedPermissions(asResourceKey(label, command),
                        authorizationContext, Permission.EXECUTE))
                .toList();

        if (authorizedLabels.isEmpty()) {
            return Optional.empty();
        } else {
            final var adjustedCommand =
                    TopLevelPolicyActionCommand.of(command.getPolicyActionCommand(), authorizedLabels);
            return Optional.of(adjustedCommand);
        }
    }

    private static List<Label> getLabelsFromPolicyEnforcer(final PolicyEnforcer policyEnforcer) {
        return policyEnforcer.getPolicy()
                .map(policy -> policy.getEntriesSet().stream()
                        .map(PolicyEntry::getLabel)
                        .toList()).orElse(List.of());
    }

    private boolean hasUnrestrictedWritePermission(final Enforcer enforcer,
            final ResourceKey policyResourceKey,
            final AuthorizationContext authorizationContext) {
        return enforcer.hasUnrestrictedPermissions(policyResourceKey, authorizationContext, Permission.WRITE);
    }

    /**
     * Limit view on entity of {@code PolicyQueryCommandResponse} by enforcer.
     *
     * @param response the response.
     * @param enforcer the enforcer.
     * @return a {@code CompletionStage} containing the response with view on entity restricted by enforcer.
     */
    public <T extends PolicyQueryCommandResponse<T>> T buildJsonViewForPolicyQueryCommandResponse(
            final PolicyQueryCommandResponse<T> response,
            final Enforcer enforcer) {

        final JsonValue entity = response.getEntity();
        final T result;
        if (entity.isObject()) {
            final JsonObject filteredView =
                    getJsonViewForPolicyQueryCommandResponse(entity.asObject(), response, enforcer);
            result = response.setEntity(filteredView);
        } else {
            result = response.setEntity(entity);
        }
        return result;
    }

    private JsonObject getJsonViewForPolicyQueryCommandResponse(final JsonObject responseEntity,
            final PolicyQueryCommandResponse<?> response,
            final Enforcer enforcer) {

        final var resourceKey = ResourceKey.newInstance(PolicyCommand.RESOURCE_TYPE, response.getResourcePath());
        final var authorizationContext = response.getDittoHeaders().getAuthorizationContext();

        return enforcer.buildJsonView(resourceKey, responseEntity, authorizationContext,
                POLICY_QUERY_COMMAND_RESPONSE_ALLOWLIST, Permissions.newInstance(Permission.READ));
    }

    /**
     * Create error due to failing to execute a policy-command in the expected way.
     *
     * @param policySignal the signal.
     * @return the error.
     */
    private static DittoRuntimeException errorForPolicyCommand(final Signal<?> policySignal) {

        if (policySignal instanceof PolicyCommand<?> policyCommand) {
            final CommandToExceptionRegistry<PolicyCommand<?>, DittoRuntimeException> registry;
            if (policyCommand instanceof PolicyActionCommand) {
                registry = PolicyCommandToActionsExceptionRegistry.getInstance();
            } else if (policyCommand instanceof PolicyModifyCommand) {
                registry = PolicyCommandToModifyExceptionRegistry.getInstance();
            } else {
                registry = PolicyCommandToAccessExceptionRegistry.getInstance();
            }
            return registry.exceptionFrom(policyCommand);
        } else if (policySignal instanceof WithEntityId withEntityId) {
            return PolicyNotAccessibleException.newBuilder(PolicyId.of(withEntityId.getEntityId()))
                    .dittoHeaders(policySignal.getDittoHeaders())
                    .build();
        } else {
            LOGGER.error("Received signal for which no DittoRuntimeException due to lack of access " +
                    "could be determined: {}", policySignal);
            return DittoInternalErrorException.newBuilder()
                    .dittoHeaders(policySignal.getDittoHeaders())
                    .build();
        }
    }

    /**
     * Convert a policy entry label for a top-level policy action into a resource path for authorization check.
     *
     * @param label the policy entry label.
     * @param command the top-level policy action command.
     * @return the resource key.
     */
    private static ResourceKey asResourceKey(final Label label, final PolicyCommand<?> command) {
        return ResourceKey.newInstance(PoliciesResourceType.POLICY,
                Policy.JsonFields.ENTRIES.getPointer().addLeaf(JsonKey.of(label)).append(command.getResourcePath()));
    }

}
