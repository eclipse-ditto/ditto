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

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.policies.api.Permission.MIN_REQUIRED_POLICY_PERMISSIONS;
import static org.eclipse.ditto.things.service.enforcement.LiveSignalEnforcement.addEffectedReadSubjectsToThingLiveSignal;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.exceptions.AskException;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.EntityNotCreatableException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.LiveChannelTimeoutStrategy;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandToExceptionRegistry;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayCommandTimeoutException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.internal.models.signal.CommandHeaderRestoration;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.internal.utils.pubsub.LiveSignalPub;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonPointerInvalidException;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.api.PoliciesValidator;
import org.eclipse.ditto.policies.enforcement.AbstractEnforcementReloaded;
import org.eclipse.ditto.policies.enforcement.CreationRestrictionEnforcer;
import org.eclipse.ditto.policies.enforcement.LiveResponseAndAcknowledgementForwarder;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PreEnforcer;
import org.eclipse.ditto.policies.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.policies.enforcement.placeholders.references.PolicyIdReferencePlaceholderResolver;
import org.eclipse.ditto.policies.enforcement.placeholders.references.ReferencePlaceholder;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyException;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.policies.model.signals.commands.PolicyErrorResponse;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyConflictException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.rql.model.ParserException;
import org.eclipse.ditto.rql.model.predicates.ast.RootNode;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.things.FieldNamesPredicateVisitor;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.LiveChannelConditionNotAllowedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyIdNotAllowedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.PolicyInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingCommandToAccessExceptionRegistry;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingCommandToModifyExceptionRegistry;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionInvalidException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;

/**
 * Authorizes {@link ThingCommand}s and filters {@link ThingCommandResponse}s.
 */
public final class ThingCommandEnforcement
        extends AbstractEnforcementReloaded<ThingCommand<?>, ThingCommandResponse<?>> {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(ThingCommandEnforcement.class);


    private static final Map<String, ThreadSafeDittoLogger> NAMESPACE_INSPECTION_LOGGERS = new HashMap<>();

    /**
     * Label of default policy entry in default policy.
     */
    private static final String DEFAULT_POLICY_ENTRY_LABEL = "DEFAULT";

    /**
     * Json fields that are always shown regardless of authorization.
     */
    private static final JsonFieldSelector THING_QUERY_COMMAND_RESPONSE_ALLOWLIST =
            JsonFactory.newFieldSelector(Thing.JsonFields.ID);

    private final ActorRef ackReceiverActor;
    private final ActorRef policiesShardRegion;
    private final PreEnforcer preEnforcer;
    private final PolicyIdReferencePlaceholderResolver policyIdReferencePlaceholderResolver;
    private final CreationRestrictionEnforcer creationRestrictionEnforcer;
    private final LiveSignalPub liveSignalPub;
    private final ActorSystem actorSystem;
    private final EnforcementConfig enforcementConfig;
    private final ResponseReceiverCache responseReceiverCache;

    /**
     * Creates a new instance of the thing command enforcer.
     *
     * @param actorSystem
     * @param ackReceiverActor
     * @param policiesShardRegion the policies shard region to load policies from and to use in order to create new
     * (inline) policies when creating new things.
     * @param creationRestrictionEnforcer the CreationRestrictionEnforcer to apply in order to enforce creation of new
     * things based on its config.
     * @param enforcementConfig the configuration to apply for this command enforcement implementation.
     * @param preEnforcer
     * @param liveSignalPub
     */
    public ThingCommandEnforcement(final ActorSystem actorSystem, // TODO TJ required to start actor in actorSystem (root) ?!
            final ActorRef ackReceiverActor,
            final ActorRef policiesShardRegion,
            final CreationRestrictionEnforcer creationRestrictionEnforcer,
            final EnforcementConfig enforcementConfig,
            final PreEnforcer preEnforcer,
            final LiveSignalPub liveSignalPub) {

        this.actorSystem = actorSystem;
        this.ackReceiverActor = requireNonNull(ackReceiverActor);
        this.policiesShardRegion = requireNonNull(policiesShardRegion);
        this.creationRestrictionEnforcer = creationRestrictionEnforcer;
        this.enforcementConfig = enforcementConfig;

        enforcementConfig.getSpecialLoggingInspectedNamespaces()
                .forEach(loggedNamespace -> NAMESPACE_INSPECTION_LOGGERS.put(
                        loggedNamespace,
                        DittoLoggerFactory.getThreadSafeLogger(ThingCommandEnforcement.class.getName() +
                                ".namespace." + loggedNamespace)));

        this.preEnforcer = preEnforcer;
        policyIdReferencePlaceholderResolver =
                PolicyIdReferencePlaceholderResolver.of(policiesShardRegion, enforcementConfig.getAskWithRetryConfig(),
                        actorSystem.getScheduler(), actorSystem.getDispatcher()); // TODO TJ need to configure different executor?
        this.liveSignalPub = liveSignalPub;
        responseReceiverCache = ResponseReceiverCache.lookup(actorSystem);
    }

    /**
     * TODO TJ doc
     * @return
     */
    public ActorRef getPoliciesShardRegion() {
        return policiesShardRegion;
    }

    @Override
    public CompletionStage<ThingCommand<?>> authorizeSignal(final ThingCommand<?> thingCommand,
            final PolicyEnforcer enforcer) {

        final ThingCommand<?> authorizedCommand;
        if (isWotTdRequestingThingQueryCommand(thingCommand)) {
            // authorization is not necessary for WoT TD requesting thing query command, this is treated as public
            // information:
            FeatureToggle.checkWotIntegrationFeatureEnabled(thingCommand.getType(), thingCommand.getDittoHeaders());
            // for retrieving the WoT TD, assume that full TD gets returned unfiltered:
            authorizedCommand = prepareThingCommandBeforeSendingToPersistence(thingCommand);
        } else {
            final ThingCommand<?> commandWithReadSubjects = authorizeByPolicyOrThrow(enforcer.getEnforcer(), thingCommand);
            if (commandWithReadSubjects instanceof ThingQueryCommand<?> thingQueryCommand) {
                authorizedCommand = ensureTwinChannel(thingQueryCommand);
            } else if (commandWithReadSubjects.getDittoHeaders().getLiveChannelCondition().isPresent()) {
                throw LiveChannelConditionNotAllowedException.newBuilder()
                        .dittoHeaders(commandWithReadSubjects.getDittoHeaders())
                        .build();
            } else {
                authorizedCommand = prepareThingCommandBeforeSendingToPersistence(commandWithReadSubjects);
            }
        }
        return CompletableFuture.completedStage(authorizedCommand);
    }

    @Override
    public CompletionStage<ThingCommand<?>> authorizeSignalWithMissingEnforcer(final ThingCommand<?> signal) {

        // Without prior enforcer in cache, enforce CreateThing by self.
        // DO NOT use Contextual.askFuture to handle the ask-steps of a CreateThing command! Otherwise
        // the query- and modify-commands sent immediately after may be processed before the thing is created.
        return enforceCreateThingBySelf(signal)
                .thenCompose(this::handleInitialCreateThing)
                .exceptionally(throwable -> {
                    final ThreadSafeDittoLogger l = LOGGER.withCorrelationId(signal);

                    final var dittoRuntimeException =
                            DittoRuntimeException.asDittoRuntimeException(throwable, cause -> {
                                l.warn("Error during thing by itself enforcement - {}: {}",
                                        cause.getClass().getSimpleName(), cause.getMessage());
                                throw GatewayInternalErrorException.newBuilder()
                                        .cause(cause)
                                        .build();
                            });

                    l.debug("DittoRuntimeException during enforceThingCommandByNonexistentEnforcer - {}: {}",
                            dittoRuntimeException.getClass().getSimpleName(), dittoRuntimeException.getMessage());
                    throw dittoRuntimeException;
                });
    }

    @Override
    public CompletionStage<ThingCommandResponse<?>> filterResponse(final ThingCommandResponse<?> commandResponse,
            final PolicyEnforcer policyEnforcer) {

        if (commandResponse instanceof ThingQueryCommandResponse<?> thingQueryCommandResponse) {
            try {
                return CompletableFuture.completedStage(
                        buildJsonViewForThingQueryCommandResponse(thingQueryCommandResponse,
                                policyEnforcer.getEnforcer())
                );
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
     * TODO TJ move where? and call from where and when?
     * @param command
     * @param response
     * @param startTime
     * @param policyEnforcer
     * @return
     */
    private CompletionStage<ThingQueryCommandResponse<?>> doSmartChannelSelection(final ThingQueryCommand<?> command,
            final ThingQueryCommandResponse<?> response, final Instant startTime, final PolicyEnforcer policyEnforcer) {

        final ThingQueryCommandResponse<?> twinResponseWithTwinChannel = setTwinChannel(response);
        final ThingQueryCommandResponse<?> twinResponse = CommandHeaderRestoration.restoreCommandConnectivityHeaders(
                twinResponseWithTwinChannel, command.getDittoHeaders());
        if (!shouldAttemptLiveChannel(command, twinResponse)) {
            return CompletableFuture.completedStage(twinResponse);
        }

        final ThingQueryCommand<?> liveCommand = toLiveCommand(command, policyEnforcer.getEnforcer());
        final var pub = liveSignalPub.command();
        final var liveResponseForwarder = startLiveResponseForwarder(liveCommand);
        if (enforcementConfig.shouldDispatchGlobally(liveCommand)) {
            return responseReceiverCache.insertResponseReceiverConflictFreeWithFuture(
                    liveCommand,
                    newCommand -> liveResponseForwarder,
                    (newCommand, forwarder) ->
                            LiveSignalEnforcement.adjustTimeoutAndFilterLiveQueryResponse(this,
                                    newCommand,
                                    startTime,
                                    pub,
                                    forwarder,
                                    policyEnforcer,
                                    getFallbackResponseCaster(liveCommand, twinResponse)
                            )
            );
        } else {
            return LiveSignalEnforcement.adjustTimeoutAndFilterLiveQueryResponse(this,
                    liveCommand,
                    startTime,
                    pub,
                    liveResponseForwarder,
                    policyEnforcer,
                    getFallbackResponseCaster(liveCommand, twinResponse)
            );
        }
    }

    private ActorRef startLiveResponseForwarder(final ThingQueryCommand<?> signal) {
        final var pub = liveSignalPub.command();
        final var props = LiveResponseAndAcknowledgementForwarder.props(signal, pub.getPublisher(),
                ackReceiverActor);
        return actorSystem.actorOf(props); // TODO TJ check if we should really create a root level actor here
    }

    private Function<Object, CompletionStage<ThingQueryCommandResponse<?>>> getFallbackResponseCaster(
            final ThingQueryCommand<?> liveCommand, final ThingQueryCommandResponse<?> twinResponse) {

        return response -> {
            if (response instanceof ThingQueryCommandResponse) {
                return CompletableFuture.completedStage(
                        setAdditionalHeaders((ThingQueryCommandResponse<?>) response, liveCommand.getDittoHeaders()));
            } else if (response instanceof ErrorResponse) {
                throw setAdditionalHeaders(((ErrorResponse<?>) response),
                        liveCommand.getDittoHeaders()).getDittoRuntimeException();
            } else if (response instanceof AskException || response instanceof AskTimeoutException) {
                return applyTimeoutStrategy(liveCommand, twinResponse);
            } else {
                final var errorToReport = reportErrorOrResponse(
                        "before building JsonView for live response via smart channel selection",
                        response, null, liveCommand.getDittoHeaders());
                throw errorToReport;
            }
        };
    }

    private static boolean shouldAttemptLiveChannel(final ThingQueryCommand<?> command,
            final ThingQueryCommandResponse<?> twinResponse) {
        return isLiveChannelConditionMatched(command, twinResponse) || isLiveQueryCommandWithTimeoutStrategy(command);
    }

    private static boolean isLiveChannelConditionMatched(final ThingQueryCommand<?> command,
            final ThingQueryCommandResponse<?> twinResponse) {
        return command.getDittoHeaders().getLiveChannelCondition().isPresent() &&
                twinResponse.getDittoHeaders().didLiveChannelConditionMatch();
    }

    static boolean isLiveQueryCommandWithTimeoutStrategy(final Signal<?> command) {
        return command instanceof ThingQueryCommand &&
                command.getDittoHeaders().getLiveChannelTimeoutStrategy().isPresent() &&
                Signal.isChannelLive(command);
    }

    private static <T extends DittoHeadersSettable<?>> T setAdditionalHeaders(final T settable,
            final DittoHeaders commandHeaders) {
        final DittoHeaders dittoHeaders = settable.getDittoHeaders();
        final DittoHeadersSettable<?> theSettable = settable.setDittoHeaders(dittoHeaders
                .toBuilder()
                .putHeaders(getAdditionalLiveResponseHeaders(dittoHeaders))
                .build());
        return (T) CommandHeaderRestoration.restoreCommandConnectivityHeaders(theSettable, commandHeaders);
    }

    private static CompletionStage<ThingQueryCommandResponse<?>> applyTimeoutStrategy(
            final ThingCommand<?> command,
            final ThingQueryCommandResponse<?> twinResponse) {

        if (isTwinFallbackEnabled(twinResponse)) {
            return CompletableFuture.completedStage(twinResponse);
        } else {
            final var timeout = LiveSignalEnforcement.getLiveSignalTimeout(command);
            final GatewayCommandTimeoutException timeoutException = GatewayCommandTimeoutException.newBuilder(timeout)
                    .dittoHeaders(twinResponse.getDittoHeaders()
                            .toBuilder()
                            .channel(Signal.CHANNEL_LIVE)
                            .putHeaders(getAdditionalLiveResponseHeaders(twinResponse.getDittoHeaders()))
                            .build())
                    .build();
            final GatewayCommandTimeoutException timeoutExceptionWithConnectivityHeaders =
                    CommandHeaderRestoration.restoreCommandConnectivityHeaders(timeoutException,
                            command.getDittoHeaders());
            return CompletableFuture.failedStage(timeoutExceptionWithConnectivityHeaders);
        }
    }

    private static boolean isTwinFallbackEnabled(final Signal<?> signal) {
        final var liveChannelFallbackStrategy =
                signal.getDittoHeaders().getLiveChannelTimeoutStrategy().orElse(LiveChannelTimeoutStrategy.FAIL);
        return LiveChannelTimeoutStrategy.USE_TWIN == liveChannelFallbackStrategy;
    }

    private static ThingQueryCommand<?> toLiveCommand(final ThingQueryCommand<?> command, final Enforcer enforcer) {
        final ThingQueryCommand<?> withReadSubjects = addEffectedReadSubjectsToThingLiveSignal(command, enforcer);
        return withReadSubjects.setDittoHeaders(withReadSubjects.getDittoHeaders().toBuilder()
                .liveChannelCondition(null)
                .channel(Signal.CHANNEL_LIVE)
                .build());
    }

    /**
     * Query caches again to authorize a {@code CreateThing} command with explicit policy ID and no inline policy.
     *
     * @param command the command.
     * @param policyId the policy ID.
     * @return the completionStage of contextual including message and receiver
     */
    private CompletionStage<ThingCommand<?>> enforceCreateThingForNonexistentThingWithPolicyId(
            final CreateThing command, final PolicyId policyId) {

        if (null != policyEnforcerLoader) {
            return policyEnforcerLoader.apply(policyId)
                    .thenCompose(policyEnforcer -> {
                        if (null != policyEnforcer) {
                            return authorizeSignal(command, policyEnforcer);
                        } else {
                            throw errorForExistingThingWithDeletedPolicy(command, command.getEntityId(), policyId);
                        }
                    });
        } else {
            LOGGER.withCorrelationId(command)
                    .error("PolicyEnforcerLoader was not present in order to load a PolicyEnforcer.");
            throw new IllegalStateException("PolicyEnforcerLoader was absent");
        }
    }

    /**
     * Limit view on entity of {@code ThingQueryCommandResponse} by enforcer.
     *
     * @param response the response.
     * @param enforcer the enforcer.
     * @return response with view on entity restricted by enforcer.
     */
    static <T extends ThingQueryCommandResponse<T>> T buildJsonViewForThingQueryCommandResponse(
            final ThingQueryCommandResponse<T> response, final Enforcer enforcer) {

        final JsonValue entity = response.getEntity();
        if (entity.isObject()) {
            final JsonObject filteredView =
                    getJsonViewForThingQueryCommandResponse(entity.asObject(), response, enforcer);
            return response.setEntity(filteredView);
        } else {
            return response.setEntity(entity);
        }
    }

    /**
     * Prepares a passed in {@code authorizedThingCommand} before sending it to the persistence.
     *
     * @param authorizedThingCommand command to prepare.
     * @return the passed in authorizedThingCommand.
     */
    private ThingCommand<?> prepareThingCommandBeforeSendingToPersistence(final ThingCommand<?> authorizedThingCommand) {

        if (NAMESPACE_INSPECTION_LOGGERS.containsKey(authorizedThingCommand.getEntityId().getNamespace())) {
            final ThreadSafeDittoLogger namespaceLogger = NAMESPACE_INSPECTION_LOGGERS
                    .get(authorizedThingCommand.getEntityId().getNamespace()).withCorrelationId(authorizedThingCommand);
            if (authorizedThingCommand instanceof ThingModifyCommand) {
                final JsonValue value = ((ThingModifyCommand<?>) authorizedThingCommand).getEntity().orElse(null);
                if (null != value) {
                    final Set<ResourceKey> resourceKeys = calculateLeaves(authorizedThingCommand.getResourcePath(), value);
                    namespaceLogger.info("Forwarding modify command type <{}> with resourceKeys <{}>",
                            authorizedThingCommand.getType(),
                            resourceKeys);
                }
            }
            namespaceLogger
                    .debug("Forwarding command type <{}>: <{}>", authorizedThingCommand.getType(), authorizedThingCommand);
        }
        return authorizedThingCommand;
    }

    /**
     * Restrict view on a JSON object by enforcer.
     *
     * @param responseEntity the JSON object to restrict view on.
     * @param response the response containing the object.
     * @param enforcer the enforcer.
     * @return JSON object with view restricted by enforcer.
     */
    private static JsonObject getJsonViewForThingQueryCommandResponse(final JsonObject responseEntity,
            final ThingQueryCommandResponse<?> response, final Enforcer enforcer) {


        final var resourceKey = ResourceKey.newInstance(ThingConstants.ENTITY_TYPE, response.getResourcePath());
        final var authorizationContext = response.getDittoHeaders().getAuthorizationContext();

        return enforcer.buildJsonView(resourceKey, responseEntity, authorizationContext,
                THING_QUERY_COMMAND_RESPONSE_ALLOWLIST, Permissions.newInstance(Permission.READ));
    }

    /**
     * Create error for commands to an existing thing whose policy is deleted.
     *
     * @param thingCommand the triggering command.
     * @param thingId ID of the thing.
     * @param policyId ID of the deleted policy.
     * @return an appropriate error.
     */
    private static DittoRuntimeException errorForExistingThingWithDeletedPolicy(final ThingCommand<?> thingCommand,
            final ThingId thingId, final CharSequence policyId) {

        final var message = String.format(
                "The Thing with ID '%s' could not be accessed as its Policy with ID '%s' is not or no longer existing.",
                thingId, policyId);
        final var description = String.format(
                "Recreate/create the Policy with ID '%s' in order to get access to the Thing again.",
                policyId);

        if (thingCommand instanceof ThingModifyCommand) {
            return ThingNotModifiableException.newBuilder(thingId)
                    .message(message)
                    .description(description)
                    .dittoHeaders(thingCommand.getDittoHeaders())
                    .build();
        } else {
            return ThingNotAccessibleException.newBuilder(thingId)
                    .message(message)
                    .description(description)
                    .dittoHeaders(thingCommand.getDittoHeaders())
                    .build();
        }
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
     * Authorize a thing-command by authorization information contained in itself. Only {@code CreateThing} commands are
     * authorized in this manner in the absence of an existing enforcer. {@code ModifyThing} commands are transformed to
     * {@code CreateThing} commands before being processed.
     *
     * @param command the incoming command which needs to be a {@link CreateThing} to be handled in this function.
     * @return a CompletionStage of the authorized {@link CreateThing} command extended by read subjects or a failed
     * CompletionStage with a DittoRuntimeException as cause.
     */
    private CompletionStage<CreateThing> enforceCreateThingBySelf(final ThingCommand<?> command) {
        final ThingCommand<?> thingCommand = transformModifyThingToCreateThing(command);
        if (thingCommand instanceof CreateThing createThingCommand) {
            final var enforcerContext = new CreationRestrictionEnforcer.Context(command.getResourceType(),
                    command.getEntityId().getNamespace(),
                    command.getDittoHeaders()
            );
            if (creationRestrictionEnforcer.canCreate(enforcerContext)) {
                return replaceInitialPolicyWithCopiedPolicyIfPresent(createThingCommand)
                        .thenApply(createThing -> {
                            final Optional<JsonObject> initialPolicyOptional = createThing.getInitialPolicy();
                            return initialPolicyOptional.map(initialPolicy ->
                                    enforceCreateThingByOwnInlinedPolicyOrThrow(createThing, initialPolicy)
                            ).orElseGet(() -> enforceCreateThingByAuthorizationContext(createThing));
                        });
            } else {
                throw EntityNotCreatableException.newBuilder(createThingCommand.getEntityId())
                        .dittoHeaders(createThingCommand.getDittoHeaders())
                        .build();
            }
        } else {
            // Other commands cannot be authorized by policy contained in self.
            final DittoRuntimeException error;
            if (thingCommand instanceof ThingModifyCommand) {
                error = ThingNotModifiableException.newBuilder(thingCommand.getEntityId())
                        .dittoHeaders(thingCommand.getDittoHeaders())
                        .build();
            } else {
                error = ThingNotAccessibleException.newBuilder(thingCommand.getEntityId())
                        .dittoHeaders(thingCommand.getDittoHeaders())
                        .build();
            }
            LOGGER.withCorrelationId(command)
                    .info("Enforcer was not existing for Thing <{}> and no auth info was inlined, responding with: {} - {}",
                            thingCommand.getEntityId(), error.getClass().getSimpleName(), error.getMessage());
            throw error;
        }
    }

    private CompletionStage<CreateThing> replaceInitialPolicyWithCopiedPolicyIfPresent(final CreateThing createThing) {
        return getInitialPolicyOrCopiedPolicy(createThing)
                .thenApply(initialPolicyOrCopiedPolicy ->
                        CreateThing.of(createThing.getThing(), initialPolicyOrCopiedPolicy,
                                createThing.getDittoHeaders()));
    }

    private CompletionStage<JsonObject> getInitialPolicyOrCopiedPolicy(final CreateThing createThing) {
        final ThreadSafeDittoLogger l = LOGGER.withCorrelationId(createThing);
        return createThing.getPolicyIdOrPlaceholder()
                .flatMap(ReferencePlaceholder::fromCharSequence)
                .map(referencePlaceholder -> {
                    l.debug("CreateThing command contains a reference placeholder for the policy it wants to copy: {}",
                            referencePlaceholder);
                    final var dittoHeadersWithoutPreconditionHeaders = createThing.getDittoHeaders().toBuilder()
                            .removePreconditionHeaders()
                            .responseRequired(true)
                            .build();
                    return policyIdReferencePlaceholderResolver.resolve(referencePlaceholder,
                            dittoHeadersWithoutPreconditionHeaders);
                })
                .orElseGet(() -> CompletableFuture.completedFuture(createThing.getPolicyIdOrPlaceholder().orElse(null)))
                .thenCompose(policyId -> {
                    if (policyId != null) {
                        l.debug("CreateThing command wants to use a copy of Policy <{}>", policyId);
                        return retrievePolicyWithEnforcement(createThing.getDittoHeaders(), PolicyId.of(policyId))
                                .thenApply(policy -> policy.toJson(JsonSchemaVersion.V_2).remove("policyId"));
                    } else {
                        l.debug("CreateThing command did not contain a policy that should be copied.");
                        return CompletableFuture.completedFuture(createThing.getInitialPolicy().orElse(null));
                    }
                });
    }

    private CompletionStage<Policy> retrievePolicyWithEnforcement(final DittoHeaders dittoHeaders,
            final PolicyId policyId) {
        final var adjustedHeaders = dittoHeaders.toBuilder()
                .removePreconditionHeaders()
                .responseRequired(true)
                .build();

        return AskWithRetry.askWithRetry(policiesShardRegion, RetrievePolicy.of(policyId, adjustedHeaders),
                enforcementConfig.getAskWithRetryConfig(), actorSystem.getScheduler(), actorSystem.getDispatcher(), // TODO TJ need to make scheduler and dispatcher configurable?
                response -> {
                    if (response instanceof RetrievePolicyResponse rpr) {
                        return rpr.getPolicy();
                    } else if (response instanceof PolicyErrorResponse per) {
                        throw per.getDittoRuntimeException();
                    } else if (response instanceof DittoRuntimeException dre) {
                        throw dre;
                    } else {
                        LOGGER.withCorrelationId(adjustedHeaders)
                                .error("Got an unexpected response while retrieving a Policy that should be copied" +
                                        " during Thing creation: {}", response);
                        // TODO TJ other internal error than that:
                        throw GatewayInternalErrorException.newBuilder().build();
                    }
                });
    }

    private static CreateThing enforceCreateThingByAuthorizationContext(final CreateThing createThing) {

        // Command without authorization information is authorized by default.
        final var dittoHeaders = createThing.getDittoHeaders();
        final var authorizationContext = dittoHeaders.getAuthorizationContext();
        final Set<AuthorizationSubject> authorizedSubjects = authorizationContext.getFirstAuthorizationSubject()
                .map(Collections::singleton)
                .orElse(Collections.emptySet());
        final Enforcer enforcer = new AuthorizedSubjectsEnforcer(
                AuthorizationContext.newInstance(authorizationContext.getType(), authorizedSubjects));
        return addEffectedReadSubjectsToThingSignal(createThing, enforcer);
    }

    private CreateThing enforceCreateThingByOwnInlinedPolicyOrThrow(final CreateThing createThing,
            final JsonObject inlinedPolicy) {

        final var initialPolicy = getInitialPolicy(createThing, inlinedPolicy);
        final var policiesValidator = PoliciesValidator.newInstance(initialPolicy);
        if (policiesValidator.isValid()) {
            final var initialEnforcer = PolicyEnforcers.defaultEvaluator(initialPolicy);
            return authorizeByPolicyOrThrow(initialEnforcer, createThing);
        } else {
            throw PolicyInvalidException.newBuilder(MIN_REQUIRED_POLICY_PERMISSIONS, createThing.getEntityId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
        }
    }

    @SuppressWarnings("java:S1193")
    private Policy getInitialPolicy(final CreateThing createThing, final JsonObject inlinedPolicy) {
        try {
            // Java doesn't permit conversion of this early return into assignment to final variable.
            return PoliciesModelFactory.newPolicy(inlinedPolicy);
        } catch (final JsonRuntimeException | DittoJsonException e) {
            final var thingId = createThing.getEntityId();
            throw PolicyInvalidException.newBuilderForCause(e, thingId)
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
        } catch (final DittoRuntimeException e) {
            final var dittoHeaders = createThing.getDittoHeaders();
            // PolicyException is an interface!
            if (e instanceof PolicyException) {
                // user error; no need to log stack trace.
                throw e.setDittoHeaders(dittoHeaders);
            } else {
                throw reportError("Error during creation of inline policy from JSON", e,
                        createThing.getDittoHeaders());
            }
        }
    }

    /**
     * Transform a {@code ModifyThing} command sent to nonexistent thing to {@code CreateThing} command if it is sent to
     * a nonexistent thing.
     *
     * @param receivedCommand the command to transform.
     * @return {@code CreateThing} command containing the same information if the argument is a {@code ModifyThing}
     * command. Otherwise return the command itself.
     */
    private static ThingCommand<?> transformModifyThingToCreateThing(final ThingCommand<?> receivedCommand) {
        if (receivedCommand instanceof ModifyThing modifyThing) {
            final JsonObject initialPolicy = modifyThing.getInitialPolicy().orElse(null);
            final String policyIdOrPlaceholder = modifyThing.getPolicyIdOrPlaceholder().orElse(null);
            final var newThing = modifyThing.getThing().toBuilder()
                    .setId(modifyThing.getEntityId())
                    .build();
            return CreateThing.of(newThing, initialPolicy, policyIdOrPlaceholder, modifyThing.getDittoHeaders());
        } else {
            return receivedCommand;
        }
    }

    /**
     * Authorize a thing-command by a policy enforcer.
     *
     * @param <T> type of the thing-command.
     * @param policyEnforcer the policy enforcer.
     * @param command the command to authorize.
     * @return optionally the authorized command extended by read subjects.
     */
    static <T extends ThingCommand<T>> T authorizeByPolicyOrThrow(final Enforcer policyEnforcer,
            final ThingCommand<T> command) {

        final var thingResourceKey = PoliciesResourceType.thingResource(command.getResourcePath());
        final var dittoHeaders = command.getDittoHeaders();
        final var authorizationContext = dittoHeaders.getAuthorizationContext();

        final boolean commandAuthorized;
        if (command instanceof MergeThing mergeThing) {
            commandAuthorized = enforceMergeThingCommand(policyEnforcer, mergeThing, thingResourceKey,
                    authorizationContext);
        } else if (command instanceof ThingModifyCommand) {
            commandAuthorized = policyEnforcer.hasUnrestrictedPermissions(thingResourceKey, authorizationContext,
                    Permission.WRITE);
        } else {
            commandAuthorized =
                    policyEnforcer.hasPartialPermissions(thingResourceKey, authorizationContext, Permission.READ);
        }

        final var condition = dittoHeaders.getCondition();
        if (!(command instanceof CreateThing) && condition.isPresent()) {
            enforceReadPermissionOnCondition(condition.get(), policyEnforcer, dittoHeaders, () ->
                    ThingConditionFailedException.newBuilderForInsufficientPermission(dittoHeaders).build());
        }
        final var liveChannelCondition = dittoHeaders.getLiveChannelCondition();
        if ((command instanceof ThingQueryCommand) && liveChannelCondition.isPresent()) {
            enforceReadPermissionOnCondition(liveChannelCondition.get(), policyEnforcer, dittoHeaders, () ->
                    ThingConditionFailedException.newBuilderForInsufficientLiveChannelPermission(dittoHeaders).build());
        }

        if (commandAuthorized) {
            return addEffectedReadSubjectsToThingSignal(command, policyEnforcer);
        } else {
            throw errorForThingCommand(command);
        }
    }

    /**
     * Extend a signal by subject headers given with granted and revoked READ access.
     * The subjects are provided by the given enforcer for the resource type {@link ThingConstants#ENTITY_TYPE}.
     * TODO TJ copied from AbstractEnforcement
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
            final Enforcer policyEnforcer,
            final DittoHeaders dittoHeaders,
            final Supplier<DittoRuntimeException> exceptionSupplier) {

        final var authorizationContext = dittoHeaders.getAuthorizationContext();
        final var rootNode = tryParseRqlCondition(condition, dittoHeaders);
        final var resourceKeys = determineResourceKeys(rootNode, dittoHeaders);

        if (!policyEnforcer.hasUnrestrictedPermissions(resourceKeys, authorizationContext, Permission.READ)) {
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

    private static boolean enforceMergeThingCommand(final Enforcer policyEnforcer,
            final MergeThing command, final ResourceKey thingResourceKey,
            final AuthorizationContext authorizationContext) {
        if (policyEnforcer.hasUnrestrictedPermissions(thingResourceKey, authorizationContext, Permission.WRITE)) {
            // unrestricted permissions at thingResourceKey level
            return true;
        } else if (policyEnforcer.hasPartialPermissions(thingResourceKey, authorizationContext, Permission.WRITE)) {
            // in case of partial permissions at thingResourceKey level check all leaves of merge patch for
            // unrestricted permissions
            final Set<ResourceKey> resourceKeys = calculateLeaves(command.getPath(), command.getValue());
            return policyEnforcer.hasUnrestrictedPermissions(resourceKeys, authorizationContext, Permission.WRITE);
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


    private CompletionStage<ThingCommand<?>> handleInitialCreateThing(final CreateThing createThing) {

        final CompletionStage<ThingCommand<?>> result;
        if (shouldCreatePolicyForCreateThing(createThing)) {
            checkForErrorsInCreateThingWithPolicy(createThing);
            result = createPolicyInScopeOfCreateThing(createThing)
                    .thenApply(this::prepareThingCommandBeforeSendingToPersistence);
        } else if (createThing.getThing().getPolicyId().isPresent()) {
            final var policyId = createThing.getThing()
                    .getPolicyId()
                    .orElseThrow(IllegalStateException::new);
            checkForErrorsInCreateThingWithPolicy(createThing);
            result = enforceCreateThingForNonexistentThingWithPolicyId(createThing, policyId);
        } else {
            // nothing to do with policy, simply forward the command
            result = CompletableFuture.completedFuture(prepareThingCommandBeforeSendingToPersistence(createThing));
        }
        return result;
    }

    private static boolean shouldCreatePolicyForCreateThing(final CreateThing createThing) {
        return createThing.getInitialPolicy().isPresent() || createThing.getThing().getPolicyId().isEmpty();
    }

    private static void checkForErrorsInCreateThingWithPolicy(final CreateThing command) {
        validatePolicyIdForCreateThing(command);
    }

    private static void validatePolicyIdForCreateThing(final CreateThing createThing) {
        final var thing = createThing.getThing();
        final Optional<String> policyIdOpt = thing.getPolicyId().map(String::valueOf);
        final Optional<String> policyIdInPolicyOpt = createThing.getInitialPolicy()
                .flatMap(jsonObject -> jsonObject.getValue(Thing.JsonFields.POLICY_ID));

        final boolean isValid;
        if (policyIdOpt.isPresent()) {
            isValid = policyIdInPolicyOpt.isEmpty() || policyIdInPolicyOpt.equals(policyIdOpt);
        } else {
            isValid = true;
        }

        if (!isValid) {
            throw PolicyIdNotAllowedException.newBuilder(createThing.getEntityId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
        }
    }

    private CompletionStage<CreateThing> createPolicyInScopeOfCreateThing(final CreateThing createThing) {

        try {
            final Optional<Policy> policy = getInlinedOrDefaultPolicyForCreateThing(createThing);
            if (policy.isPresent()) {
                final var dittoHeadersForCreatePolicy = DittoHeaders.newBuilder(createThing.getDittoHeaders())
                        .removePreconditionHeaders()
                        .responseRequired(true)
                        .build();

                final var createPolicy = CreatePolicy.of(policy.get(), dittoHeadersForCreatePolicy);
                return createPolicyAndReturnCreateThing(createPolicy, createThing);
            } else {
                // cannot create policy.
                final var thingId = createThing.getEntityId();
                final var message = String.format("The Thing with ID '%s' could not be created with implicit " +
                        "Policy because no authorization subject is present.", thingId);
                throw ThingNotCreatableException.newBuilderForPolicyMissing(thingId, PolicyId.of(thingId))
                        .message(message)
                        .description(() -> null)
                        .dittoHeaders(createThing.getDittoHeaders())
                        .build();
            }
        } catch (final RuntimeException error) {
            throw reportError("error before creating thing with initial policy", error,
                    createThing.getDittoHeaders());
        }
    }

    private CompletionStage<CreateThing> createPolicyAndReturnCreateThing(final CreatePolicy createPolicy,
            final CreateThing createThingWithoutPolicyId) {

        final var createThing = CreateThing.of(
                createThingWithoutPolicyId.getThing().setPolicyId(createPolicy.getEntityId()),
                null,
                createThingWithoutPolicyId.getDittoHeaders());

        return preEnforcer.apply(createPolicy)
                .thenCompose(msg -> Patterns.ask(policiesShardRegion, msg,
                                enforcementConfig.getAskWithRetryConfig().getAskTimeout()
                                        .multipliedBy(5L)) // don't retry creating policy (not idempotent!) - but increase default timeout for doing so
                        .thenApply(policyResponse -> {
                            handlePolicyResponseForCreateThing(createPolicy, createThing, policyResponse);
                            return createThing;
                        })
                )
                .exceptionally(throwable -> {
                    if (throwable instanceof AskTimeoutException) {
                        throw PolicyUnavailableException.newBuilder(createPolicy.getEntityId())
                                .dittoHeaders(createThing.getDittoHeaders())
                                .build();
                    }
                    throw reportError(String.format("creating initial policy during creation of Thing <%s>",
                            createThing.getEntityId()), throwable, createThing.getDittoHeaders());
                });
    }

    private void handlePolicyResponseForCreateThing(final CreatePolicy createPolicy, final CreateThing createThing,
            final Object policyResponse) {

        if (policyResponse instanceof CreatePolicyResponse createPolicyResponse) {
            createPolicyResponse.getPolicyCreated().ifPresent(this::injectCreatedPolicy);
        } else {
            if (shouldReportInitialPolicyCreationFailure(policyResponse)) {

                throw reportInitialPolicyCreationFailure(createPolicy.getEntityId(), createThing);
            } else if (isAskTimeoutException(policyResponse, null)) {

                throw PolicyUnavailableException.newBuilder(createPolicy.getEntityId())
                        .dittoHeaders(createThing.getDittoHeaders())
                        .build();
            } else {

                final var hint = String.format("creating initial policy during creation of Thing <%s>",
                        createThing.getEntityId());
                throw reportErrorOrResponse(hint, policyResponse, null, createThing.getDittoHeaders());
            }
        }
    }

    private void injectCreatedPolicy(final Policy createdPolicy) {
        if (null != policyInjectionConsumer) {
            policyInjectionConsumer.accept(createdPolicy);
        }
    }

    private static boolean shouldReportInitialPolicyCreationFailure(final Object policyResponse) {
        return policyResponse instanceof PolicyConflictException ||
                policyResponse instanceof PolicyNotAccessibleException ||
                policyResponse instanceof NamespaceBlockedException;
    }

    private static ThingNotCreatableException reportInitialPolicyCreationFailure(final PolicyId policyId,
            final CreateThing command) {

        LOGGER.withCorrelationId(command)
                .info("Failed to create Policy with ID <{}> because it already exists." +
                        " The CreateThing command which would have created a Policy for the Thing with ID <{}>" +
                        " is therefore not handled.", policyId, command.getEntityId());
        return ThingNotCreatableException.newBuilderForPolicyExisting(command.getEntityId(), policyId)
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private static Optional<Policy> getInlinedOrDefaultPolicyForCreateThing(final CreateThing createThing) {
        final Optional<JsonObject> initialPolicy = createThing.getInitialPolicy();
        if (initialPolicy.isPresent()) {
            final JsonObject policyJson = initialPolicy.get();
            final JsonObjectBuilder policyJsonBuilder = policyJson.toBuilder();
            final var thing = createThing.getThing();
            if (thing.getPolicyId().isPresent() || !policyJson.contains(Policy.JsonFields.ID.getPointer())) {
                final String policyId = thing.getPolicyId()
                        .map(String::valueOf)
                        .orElse(createThing.getEntityId().toString());
                policyJsonBuilder.set(Policy.JsonFields.ID, policyId);
            }
            return Optional.of(PoliciesModelFactory.newPolicy(policyJsonBuilder.build()));
        } else {
            return getDefaultPolicy(createThing.getDittoHeaders().getAuthorizationContext(),
                    createThing.getEntityId());
        }
    }

    private static Optional<Policy> getDefaultPolicy(final AuthorizationContext authorizationContext,
            final ThingId thingId) {

        final Optional<Subject> subjectOptional = authorizationContext.getFirstAuthorizationSubject()
                .map(AuthorizationSubject::getId)
                .map(SubjectId::newInstance)
                .map(Subject::newInstance);

        return subjectOptional.map(subject ->
                Policy.newBuilder(PolicyId.of(thingId))
                        .forLabel(DEFAULT_POLICY_ENTRY_LABEL)
                        .setSubject(subject)
                        .setGrantedPermissions(PoliciesResourceType.thingResource("/"),
                                org.eclipse.ditto.things.api.Permission.DEFAULT_THING_PERMISSIONS)
                        .setGrantedPermissions(PoliciesResourceType.policyResource("/"),
                                org.eclipse.ditto.policies.api.Permission.DEFAULT_POLICY_PERMISSIONS)
                        .setGrantedPermissions(PoliciesResourceType.messageResource("/"),
                                org.eclipse.ditto.policies.api.Permission.DEFAULT_POLICY_PERMISSIONS)
                        .build());
    }

    private static ThingQueryCommandResponse<?> setTwinChannel(final ThingQueryCommandResponse<?> response) {
        return response.setDittoHeaders(response.getDittoHeaders()
                .toBuilder()
                .channel(Signal.CHANNEL_TWIN)
                .putHeaders(getAdditionalLiveResponseHeaders(response.getDittoHeaders()))
                .build());
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

    private static DittoHeaders getAdditionalLiveResponseHeaders(final DittoHeaders responseHeaders) {
        final var liveChannelConditionMatched = responseHeaders.getOrDefault(
                DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(), Boolean.TRUE.toString());
        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(), liveChannelConditionMatched)
                .responseRequired(false);
        return dittoHeadersBuilder.build();
    }

}
