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
package org.eclipse.ditto.things.service.enforcement;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandToExceptionRegistry;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonPointerInvalidException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.AbstractEnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.EnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicyResponse;
import org.eclipse.ditto.rql.model.ParserException;
import org.eclipse.ditto.rql.model.predicates.ast.RootNode;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.things.FieldNamesPredicateVisitor;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.LiveChannelConditionNotAllowedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingCommandToAccessExceptionRegistry;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingCommandToModifyExceptionRegistry;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Authorizes {@link ThingCommand}s and filters {@link ThingCommandResponse}s.
 */
final class ThingCommandEnforcement
        extends AbstractEnforcementReloaded<ThingCommand<?>, ThingCommandResponse<?>>
        implements ThingEnforcementStrategy {

    private static final Map<String, ThreadSafeDittoLogger> NAMESPACE_INSPECTION_LOGGERS = new HashMap<>();

    /**
     * Json fields that are always shown regardless of authorization.
     */
    private static final JsonFieldSelector THING_QUERY_COMMAND_RESPONSE_ALLOWLIST =
            JsonFactory.newFieldSelector(Thing.JsonFields.ID);
    private final ActorSystem actorSystem;
    private final ActorRef policiesShardRegion;
    private final AskWithRetryConfig askWithRetryConfig;

    /**
     * Creates a new instance of the thing command enforcer.
     *
     * @param actorSystem the actor system to load config, dispatchers from.
     * @param policiesShardRegion the policies shard region to load policies from and to use in order to create new
     * (inline) policies when creating new things.
     * @param enforcementConfig the configuration to apply for this command enforcement implementation.
     */
    public ThingCommandEnforcement(
            final ActorSystem actorSystem,
            final ActorRef policiesShardRegion,
            final EnforcementConfig enforcementConfig) {

        this.actorSystem = actorSystem;
        this.policiesShardRegion = policiesShardRegion;
        this.askWithRetryConfig = enforcementConfig.getAskWithRetryConfig();
        enforcementConfig.getSpecialLoggingInspectedNamespaces()
                .forEach(loggedNamespace -> NAMESPACE_INSPECTION_LOGGERS.put(
                        loggedNamespace,
                        DittoLoggerFactory.getThreadSafeLogger(ThingCommandEnforcement.class.getName() +
                                ".namespace." + loggedNamespace)));
    }

    @Override
    public boolean isApplicable(final Signal<?> signal) {
        return signal instanceof ThingCommand<?> && !Command.isLiveCommand(signal);
    }

    @Override
    public boolean responseIsApplicable(final CommandResponse<?> commandResponse) {
        return commandResponse instanceof ThingCommandResponse<?> && !CommandResponse.isLiveCommandResponse(
                commandResponse);
    }

    @Override
    @SuppressWarnings("unchecked")
    public EnforcementReloaded<ThingCommand<?>, ThingCommandResponse<?>> getEnforcement() {
        return this;
    }

    @Override
    public CompletionStage<ThingCommand<?>> authorizeSignal(final ThingCommand<?> command,
            final PolicyEnforcer policyEnforcer) {

        if (command.getCategory() == Command.Category.QUERY && !command.getDittoHeaders().isResponseRequired()) {
            // ignore query command with response-required=false
            return CompletableFuture.completedStage(null);
        }

        final ThingCommand<?> authorizedCommand;
        if (isWotTdRequestingThingQueryCommand(command)) {
            // authorization is not necessary for WoT TD requesting thing query command, this is treated as public
            // information:
            FeatureToggle.checkWotIntegrationFeatureEnabled(command.getType(), command.getDittoHeaders());
            // for retrieving the WoT TD, assume that full TD gets returned unfiltered:
            authorizedCommand = prepareThingCommandBeforeSendingToPersistence(command);
        } else {
            try {
                final var commandWithReadSubjects = authorizeByPolicyOrThrow(policyEnforcer.getEnforcer(), command);
                if (commandWithReadSubjects instanceof ThingQueryCommand<?> thingQueryCommand) {
                    authorizedCommand = prepareThingCommandBeforeSendingToPersistence(
                            ensureTwinChannel(thingQueryCommand)
                    );
                } else if (commandWithReadSubjects.getDittoHeaders()
                        .getLiveChannelCondition()
                        .isPresent()) {
                    throw LiveChannelConditionNotAllowedException.newBuilder()
                            .dittoHeaders(commandWithReadSubjects.getDittoHeaders())
                            .build();
                } else {
                    authorizedCommand = prepareThingCommandBeforeSendingToPersistence(commandWithReadSubjects);
                }
            } catch (final Throwable error) {
                if (command instanceof CreateThing createThing && !Signal.isChannelLive(createThing)) {
                    return handleFailedCreateThing(createThing, policyEnforcer)
                            .thenCompose(done -> CompletableFuture.failedStage(error));
                }
                return CompletableFuture.failedStage(error);
            }
        }
        return CompletableFuture.completedStage(authorizedCommand);
    }

    private static ThingQueryCommand<?> ensureTwinChannel(final ThingQueryCommand<?> command) {

        if (Signal.isChannelLive(command)) {
            return command.setDittoHeaders(command.getDittoHeaders()
                    .toBuilder()
                    .channel(Signal.CHANNEL_TWIN)
                    .build());
        } else {
            return command;
        }
    }

    @Override
    public CompletionStage<ThingCommand<?>> authorizeSignalWithMissingEnforcer(final ThingCommand<?> signal) {
        throw ThingNotAccessibleException.newBuilder(signal.getEntityId())
                .dittoHeaders(signal.getDittoHeaders())
                .build();
    }

    @Override
    public boolean shouldFilterCommandResponse(final ThingCommandResponse<?> commandResponse) {
        return commandResponse instanceof ThingQueryCommandResponse<?>;
    }

    @Override
    public CompletionStage<ThingCommandResponse<?>> filterResponse(final ThingCommandResponse<?> commandResponse,
            final PolicyEnforcer policyEnforcer) {

        if (commandResponse instanceof ThingQueryCommandResponse<?> thingQueryCommandResponse) {
            try {
                final ThingQueryCommandResponse<?> filteredResponse =
                        buildJsonViewForThingQueryCommandResponse(thingQueryCommandResponse,
                                policyEnforcer.getEnforcer());
                return CompletableFuture.completedStage(filteredResponse);
            } catch (final RuntimeException e) {
                throw reportError("Error after building JsonView", e, commandResponse.getDittoHeaders());
            }
        } else {
            // no filtering required for non ThingQueryCommandResponse:
            return CompletableFuture.completedStage(commandResponse);
        }
    }

    private boolean isWotTdRequestingThingQueryCommand(final ThingCommand<?> thingCommand) {

        // all listed thingCommand types here must handle "Accept: application/td+json", otherwise access control
        // will be bypassed when using the header "Accept: application/td+json"!
        return (thingCommand instanceof RetrieveThing || thingCommand instanceof RetrieveFeature) &&
                thingCommand.getDittoHeaders().getAccept()
                        .filter(ContentType.APPLICATION_TD_JSON.getValue()::equals)
                        .isPresent();
    }

    /**
     * Limit view on entity of {@code ThingQueryCommandResponse} by enforcer.
     *
     * @param response the response.
     * @param enforcer the enforcer.
     * @return response with view on entity restricted by enforcer.
     */
    @SuppressWarnings("unchecked")
    <T extends ThingQueryCommandResponse<T>> T buildJsonViewForThingQueryCommandResponse(
            final ThingQueryCommandResponse<T> response, final Enforcer enforcer) {

        final JsonValue entity = response.getEntity();
        if (entity.isObject()) {
            final var filteredView = getJsonViewForCommandResponse(entity.asObject(), response, enforcer);
            return response.setEntity(filteredView);
        } else {
            return (T) response;
        }
    }

    /**
     * Prepares a passed in {@code authorizedThingCommand} before sending it to the persistence.
     *
     * @param authorizedThingCommand command to prepare.
     * @return the passed in authorizedThingCommand.
     */
    private ThingCommand<?> prepareThingCommandBeforeSendingToPersistence(
            final ThingCommand<?> authorizedThingCommand) {

        if (NAMESPACE_INSPECTION_LOGGERS.containsKey(authorizedThingCommand.getEntityId().getNamespace())) {
            final ThreadSafeDittoLogger namespaceLogger = NAMESPACE_INSPECTION_LOGGERS
                    .get(authorizedThingCommand.getEntityId().getNamespace()).withCorrelationId(authorizedThingCommand);
            if (authorizedThingCommand instanceof ThingModifyCommand) {
                final JsonValue value = ((ThingModifyCommand<?>) authorizedThingCommand).getEntity().orElse(null);
                if (null != value) {
                    final Set<ResourceKey> resourceKeys =
                            calculateLeaves(authorizedThingCommand.getResourcePath(), value);
                    namespaceLogger.info("Forwarding modify command type <{}> with resourceKeys <{}>",
                            authorizedThingCommand.getType(),
                            resourceKeys);
                }
            }
            namespaceLogger
                    .debug("Forwarding command type <{}>: <{}>", authorizedThingCommand.getType(),
                            authorizedThingCommand);
        }
        return authorizedThingCommand;
    }

    /**
     * Restrict view on a JSON object by enforcer.
     *
     * @param responseEntity the JSON object to restrict view on.
     * @param response the command response containing the object.
     * @param enforcer the enforcer.
     * @return JSON object with view restricted by enforcer.
     */
    static JsonObject getJsonViewForCommandResponse(final JsonObject responseEntity,
            final CommandResponse<?> response,
            final Enforcer enforcer) {

        final var resourceKey = ResourceKey.newInstance(ThingConstants.ENTITY_TYPE, response.getResourcePath());
        final var authorizationContext = response.getDittoHeaders().getAuthorizationContext();

        return enforcer.buildJsonView(resourceKey, responseEntity, authorizationContext,
                THING_QUERY_COMMAND_RESPONSE_ALLOWLIST, Permissions.newInstance(Permission.READ));
    }

    /**
     * Create error due to failing to execute a thing-command in the expected way.
     *
     * @param thingCommand the command.
     * @return the error.
     */
    static DittoRuntimeException errorForThingCommand(final ThingCommand<?> thingCommand) {

        final CommandToExceptionRegistry<ThingCommand<?>, DittoRuntimeException> registry =
                thingCommand instanceof ThingModifyCommand
                        ? ThingCommandToModifyExceptionRegistry.getInstance()
                        : ThingCommandToAccessExceptionRegistry.getInstance();
        return registry.exceptionFrom(thingCommand);
    }

    /**
     * Authorize a thing-command by a policy enforcer.
     *
     * @param <T> type of the thing-command.
     * @param enforcer the enforcer.
     * @param command the command to authorize.
     * @return optionally the authorized command extended by read subjects.
     */
    static <T extends ThingCommand<T>> T authorizeByPolicyOrThrow(final Enforcer enforcer,
            final ThingCommand<T> command) {

        final var thingResourceKey = PoliciesResourceType.thingResource(command.getResourcePath());
        final var dittoHeaders = command.getDittoHeaders();
        final var authorizationContext = dittoHeaders.getAuthorizationContext();

        final boolean commandAuthorized;
        if (command instanceof MergeThing mergeThing) {
            commandAuthorized = enforceMergeThingCommand(enforcer, mergeThing, thingResourceKey, authorizationContext);
        } else if (command instanceof ThingModifyCommand) {
            commandAuthorized =
                    enforcer.hasUnrestrictedPermissions(thingResourceKey, authorizationContext, Permission.WRITE);
        } else {
            commandAuthorized = enforcer.hasPartialPermissions(thingResourceKey, authorizationContext, Permission.READ);
        }

        final var condition = dittoHeaders.getCondition();
        if (!(command instanceof CreateThing) && condition.isPresent()) {
            enforceReadPermissionOnCondition(condition.get(), enforcer, dittoHeaders,
                    () -> ThingConditionFailedException.newBuilderForInsufficientPermission(dittoHeaders).build());
        } else if ((command instanceof ThingQueryCommand)) {
            dittoHeaders.getLiveChannelCondition()
                    .ifPresent(liveChannelCondition -> enforceReadPermissionOnCondition(liveChannelCondition, enforcer,
                            dittoHeaders,
                            () -> ThingConditionFailedException.newBuilderForInsufficientLiveChannelPermission(
                                    dittoHeaders).build()));
        }

        if (commandAuthorized) {
            return addEffectedReadSubjectsToThingSignal(command, enforcer);
        } else {
            throw errorForThingCommand(command);
        }
    }

    private CompletionStage<Done> handleFailedCreateThing(
            final CreateThing createThing,
            final PolicyEnforcer policyEnforcer) {

        if (shouldDeletePolicy(createThing)) {
            return deletePolicy(policyEnforcer.getPolicy().flatMap(Policy::getEntityId).orElseThrow(), createThing);
        }
        return CompletableFuture.completedStage(Done.getInstance());
    }

    private static boolean shouldDeletePolicy(final CreateThing createThing) {
        return wasPolicyCopied(createThing)
                || wasInlinePolicyCreated(createThing)
                || wasDefaultPolicyCreated(createThing);
    }

    private static boolean wasPolicyCopied(final CreateThing createThing) {
        return createThing.getPolicyIdOrPlaceholder().isPresent();
    }

    private static boolean wasInlinePolicyCreated(final CreateThing createThing) {
        return createThing.getInitialPolicy().isPresent();
    }

    private static boolean wasDefaultPolicyCreated(final CreateThing createThing) {
        return createThing.getThing().getPolicyId().isEmpty() && createThing.getPolicyIdOrPlaceholder().isEmpty();
    }

    private CompletionStage<Done> deletePolicy(final PolicyId policyId, final CreateThing createThing) {
        final DittoHeaders dittoHeaders = createThing.getDittoHeaders();
        final var dittoHeadersForCreatePolicy = DittoHeaders.newBuilder(dittoHeaders)
                .removePreconditionHeaders()
                .responseRequired(true)
                .putHeader(DittoHeaderDefinition.DITTO_SUDO.getKey(), "true")
                .build();
        return doDeletePolicy(DeletePolicy.of(policyId, dittoHeadersForCreatePolicy));
    }

    private CompletionStage<Done> doDeletePolicy(final DeletePolicy deletePolicy) {
        return AskWithRetry.askWithRetry(policiesShardRegion, deletePolicy, askWithRetryConfig, actorSystem,
                        this::handleDeletePolicyResponse)
                .thenCompose(success -> {
                    if (Boolean.FALSE.equals(success)) {
                        return doDeletePolicy(deletePolicy);
                    }
                    return CompletableFuture.completedStage(Done.getInstance());
                });
    }

    private boolean handleDeletePolicyResponse(final Object policyResponse) {
        //not accessible means already deleted and can be considered as success
        return policyResponse instanceof DeletePolicyResponse || policyResponse instanceof PolicyNotAccessibleException;
    }


    /**
     * Extend a signal by subject headers given with granted and revoked READ access.
     * The subjects are provided by the given enforcer for the resource type {@link ThingConstants#ENTITY_TYPE}.
     *
     * @param signal the signal to extend.
     * @param enforcer the enforcer.
     * @return the extended signal.
     */
    static <T extends Signal<T>> T addEffectedReadSubjectsToThingSignal(final Signal<T> signal,
            final Enforcer enforcer) {

        final var resourceKey = ResourceKey.newInstance(ThingConstants.ENTITY_TYPE, signal.getResourcePath());
        final var authorizationSubjects = enforcer.getSubjectsWithUnrestrictedPermission(resourceKey, Permission.READ);
        final var newHeaders = DittoHeaders.newBuilder(signal.getDittoHeaders())
                .readGrantedSubjects(authorizationSubjects)
                .build();

        return signal.setDittoHeaders(newHeaders);
    }

    private static void enforceReadPermissionOnCondition(final String condition,
            final Enforcer enforcer,
            final DittoHeaders dittoHeaders,
            final Supplier<DittoRuntimeException> exceptionSupplier) {

        final var authorizationContext = dittoHeaders.getAuthorizationContext();
        final var rootNode = tryParseRqlCondition(condition, dittoHeaders);
        final var resourceKeys = determineResourceKeys(rootNode, dittoHeaders);

        if (!enforcer.hasUnrestrictedPermissions(resourceKeys, authorizationContext, Permission.READ)) {
            throw exceptionSupplier.get();
        }
    }

    private static RootNode tryParseRqlCondition(final String condition, final DittoHeaders dittoHeaders) {
        try {
            return RqlPredicateParser.getInstance().parse(condition);
        } catch (final ParserException e) {
            throw ThingConditionInvalidException.newBuilder(condition, e.getMessage())
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private static Set<ResourceKey> determineResourceKeys(final RootNode rootNode, final DittoHeaders dittoHeaders) {
        final var visitor = FieldNamesPredicateVisitor.getNewInstance();
        visitor.visit(rootNode);
        final var extractedFieldNames = visitor.getFieldNames();

        return extractedFieldNames.stream()
                .map(fieldName -> tryGetResourceKey(fieldName, dittoHeaders))
                .collect(Collectors.toSet());
    }

    private static ResourceKey tryGetResourceKey(final String fieldName, final DittoHeaders dittoHeaders) {
        try {
            return PoliciesResourceType.thingResource(fieldName);
        } catch (final JsonPointerInvalidException e) {
            throw ThingConditionInvalidException.newBuilder(fieldName, e.getDescription().orElse(""))
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private static boolean enforceMergeThingCommand(final Enforcer enforcer,
            final MergeThing command,
            final ResourceKey thingResourceKey,
            final AuthorizationContext authorizationContext) {

        if (enforcer.hasUnrestrictedPermissions(thingResourceKey, authorizationContext, Permission.WRITE)) {
            // unrestricted permissions at thingResourceKey level
            return true;
        } else if (enforcer.hasPartialPermissions(thingResourceKey, authorizationContext, Permission.WRITE)) {
            // in case of partial permissions at thingResourceKey level check all leaves of merge patch for
            // unrestricted permissions
            final Set<ResourceKey> resourceKeys = calculateLeaves(command.getPath(), command.getValue());
            return enforcer.hasUnrestrictedPermissions(resourceKeys, authorizationContext, Permission.WRITE);
        } else {
            // not even partial permission
            return false;
        }
    }

    private static Set<ResourceKey> calculateLeaves(final JsonPointer path, final JsonValue value) {
        if (value.isObject()) {
            return value.asObject().stream()
                    .map(f -> calculateLeaves(path.append(f.getKey().asPointer()), f.getValue()))
                    .reduce(new HashSet<>(), ThingCommandEnforcement::addAll, ThingCommandEnforcement::addAll);
        } else {
            return Set.of(PoliciesResourceType.thingResource(path));
        }
    }

    private static Set<ResourceKey> addAll(final Set<ResourceKey> result, final Set<ResourceKey> toBeAdded) {
        result.addAll(toBeAdded);
        return result;
    }

}
