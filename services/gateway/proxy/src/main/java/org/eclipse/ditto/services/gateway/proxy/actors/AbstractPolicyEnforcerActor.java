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
package org.eclipse.ditto.services.gateway.proxy.actors;

import static org.eclipse.ditto.services.models.policies.Permission.READ;
import static org.eclipse.ditto.services.models.policies.Permission.WRITE;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyRevision;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policiesenforcers.EffectedSubjectIds;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcers;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.models.policies.PolicyCacheEntry;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommand;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.distributedcache.actors.RegisterForCacheUpdates;
import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.base.WithName;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.WithEntity;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyCommandToAccessExceptionRegistry;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyCommandToModifyExceptionRegistry;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommand;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.events.policies.PolicyCreated;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.policies.PolicyModified;

import akka.actor.AbstractActorWithStash;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Status;
import akka.cluster.ddata.LWWRegister;
import akka.cluster.ddata.ReplicatedData;
import akka.cluster.ddata.Replicator;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import scala.concurrent.duration.FiniteDuration;

/**
 * Base actor for policy enforcing actors. Provides functionality to keep a {@link Policy} in sync and handles incoming
 * {@link PolicyCommand}s.
 */
public abstract class AbstractPolicyEnforcerActor extends AbstractActorWithStash {

    private static final JsonPointer ROOT_RESOURCE = JsonFactory.newPointer("/");
    private static final String POLICY_ENFORCER_SYNC_CORRELATION_PREFIX = "policy-enforcer-sync-";
    private static final String POLICIES_SERVICE_NAME = "Policies";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final String policyId;
    private final ActorRef pubSubMediator;
    private final ActorRef policiesShardRegion;
    private final FiniteDuration cacheInterval;
    private final FiniteDuration askTimeout;
    private final Map<String, JsonFieldSelector> whitelistedJsonFields;

    private final Receive enforcingBehaviour;
    private final Receive queryingBehaviour;
    private final Receive synchronizingBehaviour;

    private PolicyEnforcer policyEnforcer;
    private Cancellable activityCheckCancellable;
    private Cancellable synchronizationTimeout;
    private Set<String> cachedThingReadSubjectsOnRoot;
    private Set<String> cachedPolicyReadSubjectsOnRoot;
    private int stashCount = 0;
    private long policyRevision = -1L;
    private boolean policyLoadWasAttempted = false;
    private long accessCounter;
    private ActorRef queryOriginalSender;
    private Cancellable queryTimeout;

    AbstractPolicyEnforcerActor(final ActorRef pubSubMediator, final ActorRef policiesShardRegion,
            final ActorRef policyCacheFacade, final FiniteDuration cacheInterval, final FiniteDuration askTimeout,
            final Map<String, JsonFieldSelector> whitelistedJsonFields) {
        try {
            this.policyId = URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException("Unsupported encoding", e);
        }

        this.pubSubMediator = pubSubMediator;
        this.policiesShardRegion = policiesShardRegion;
        this.cacheInterval = cacheInterval;
        this.askTimeout = askTimeout;
        this.whitelistedJsonFields = whitelistedJsonFields;

        enforcingBehaviour = buildEnforcingBehaviour();
        synchronizingBehaviour = buildSynchronizingBehaviour();
        queryingBehaviour = buildQueryingBehaviour();

        // subscribe for changes
        policyCacheFacade.tell(new RegisterForCacheUpdates(policyId, getSelf()), getSelf());

        synchronizePolicy();
        scheduleCheckForActivity();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchAny(m -> {
                    log.warning("Got message during init phase, dropping: {}", m);
                    unhandled(m);
                })
                .build();
    }

    @Override
    public void postStop() {
        cancelTimeouts();
    }

    /**
     * Returns a {@code ReceiveBuilder} for additional behaviour.
     */
    protected abstract void addEnforcingBehaviour(final ReceiveBuilder receiveBuilder);

    protected DiagnosticLoggingAdapter getLogger() {
        return log;
    }

    protected ActorRef getPubsubMediator() {
        return pubSubMediator;
    }

    protected String getPolicyId() {
        return policyId;
    }

    protected PolicyEnforcer getPolicyEnforcer() {
        return policyEnforcer;
    }

    protected JsonPointer getRootResource() {
        return ROOT_RESOURCE;
    }

    @SuppressWarnings("squid:S1172")
    protected boolean isEnforcerNull(final WithDittoHeaders withDittoHeaders) {
        return policyEnforcer == null;
    }

    protected boolean isEnforcerAvailable() {
        if (policyEnforcer == null) {
            log.debug("There is no policy enforcer available for the policy enforcer actor with policy id <{}>. "
                    + "Therefore the policy check returns false.", policyId);
            return false;
        }
        return true;
    }

    protected <T extends Signal> T enrichDittoHeaders(final Signal<T> signal,
            final JsonPointer resourcePath, final String resourceType) {

        final DittoHeadersBuilder headersBuilder = DittoHeaders.newBuilder(signal.getDittoHeaders());
        final DittoHeaders headers;

        if (signal instanceof CreatePolicy || signal instanceof ModifyPolicy) {
            final Policy policy = signal instanceof CreatePolicy ?
                    ((CreatePolicy) signal).getPolicy() : ((ModifyPolicy) signal).getPolicy();
            final String type = signal.getResourceType();

            headers = headersBuilder.readSubjects(PolicyEnforcers.defaultEvaluator(policy)
                    .getSubjectIdsWithPermission(ResourceKey.newInstance(type, resourcePath), READ)
                    .getGranted()
            ).build();
        } else {
            headers = headersBuilder
                    .readSubjects(retrieveReadSubjects(resourcePath, resourceType))
                    .build();
        }

        return signal.setDittoHeaders(headers);
    }

    protected void incrementAccessCounter() {
        accessCounter++;
    }

    protected void publishLiveSignal(final String topic, final Signal<?> signal) {
        publishLiveSignal(topic, signal, signal.getResourcePath());
    }

    protected void publishLiveSignal(final String topic, final Signal<?> signal, final JsonPointer resourcePath) {
        LogUtil.enhanceLogWithCorrelationId(log, signal);
        final Signal<?> commandWithReadSubjects = enrichDittoHeaders(signal, resourcePath, signal.getResourceType());
        log.debug("Publishing signal <{}> to topic <{}>.", signal.getName(), topic);
        accessCounter++;
        // using pub/sub to publish the command to any interested parties (e.g. a Websocket):
        pubSubMediator.tell(
                new DistributedPubSubMediator.Publish(topic, commandWithReadSubjects, true),
                getSender());
    }

    protected void synchronizePolicy() {
        final RetrievePolicy retrievePolicy = RetrievePolicy.of(policyId, DittoHeaders.newBuilder()
                .correlationId(POLICY_ENFORCER_SYNC_CORRELATION_PREFIX + policyId)
                .build());
        tellCommand(retrievePolicy);
        getContext().become(synchronizingBehaviour);
        synchronizationTimeout = scheduleTimeout();
    }

    protected void rebuildPolicyEnforcer(final Policy policy, final long revision) {
        final PolicyEnforcer newPolicyEnforcer = PolicyEnforcers.defaultEvaluator(policy);

        final EffectedSubjectIds effectedSubjectIdsOnThingResource =
                newPolicyEnforcer.getSubjectIdsWithPermission(PoliciesResourceType.thingResource(
                        ROOT_RESOURCE), READ);
        cachedThingReadSubjectsOnRoot = effectedSubjectIdsOnThingResource.getGranted();

        final EffectedSubjectIds effectedSubjectIdsOnPoliciesResource =
                newPolicyEnforcer.getSubjectIdsWithPermission(PoliciesResourceType.policyResource(
                        ROOT_RESOURCE), READ);
        cachedPolicyReadSubjectsOnRoot = effectedSubjectIdsOnPoliciesResource.getGranted();

        policyEnforcer = newPolicyEnforcer;
        policyRevision = revision;
        policyLoadWasAttempted = true;
    }

    protected void logForwardingOfReceivedSignal(final WithName signal, final String targetServiceName) {
        log.debug("Received <{}> command. Telling {} about it.", signal.getName(), targetServiceName);
    }

    protected void logUnauthorized(final Signal<?> signal, final DittoRuntimeException exception) {
        LogUtil.enhanceLogWithCorrelationId(log, signal);
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        log.info("The signal <{}> was not forwarded due to insufficient rights {}: {} - AuthorizationSubjects: {}",
                signal.getType(), getSimpleClassName(exception), exception.getMessage(),
                dittoHeaders.getAuthorizationSubjects());
        log.debug("The AuthorizationContext for the not allowed signal <{}> was: {} - the policyEnforcer was: {}",
                signal.getType(), dittoHeaders.getAuthorizationContext(), policyEnforcer);
    }

    protected void doStash() {
        stashCount++;
        stash();
    }

    protected void becomeQueryingBehaviour() {
        getContext().become(queryingBehaviour);
    }

    protected void scheduleQueryTimeout() {
        queryTimeout = scheduleTimeout();
    }

    protected void preserveQueryOriginalSender(final ActorRef sender) {
        queryOriginalSender = sender;
    }

    private Cancellable scheduleTimeout() {
        return getContext()
                .system()
                .scheduler()
                .scheduleOnce(askTimeout, getSelf(), new AskTimeoutException("Request timeout."),
                        getContext().dispatcher(), getSelf());
    }

    private Receive buildEnforcingBehaviour() {
        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create();
        addEnforcingBehaviour(receiveBuilder);
        return receiveBuilder
                /* directly forward all PolicySudoCommands */
                .match(SudoCommand.class, this::forwardPolicySudoCommand)

                /* PolicyCommands */
                .match(CreatePolicy.class, this::isEnforcerNull, this::forwardPolicyModifyCommand)
                .match(PolicyCommand.class, this::isEnforcerNull, command -> {
                    if (!policyLoadWasAttempted) {
                        doStash();
                        synchronizePolicy();
                    } else {
                        getSender().tell(PolicyNotAccessibleException
                                        .newBuilder(command.getId())
                                        .dittoHeaders(command.getDittoHeaders())
                                        .build(),
                                getSelf());
                    }
                })
                .match(PolicyModifyCommand.class, this::isAuthorized, this::forwardPolicyModifyCommand)
                .match(PolicyModifyCommand.class, this::unauthorized)
                .match(PolicyQueryCommand.class, this::isAuthorized, command -> {
                    tellCommand(command);
                    getContext().become(queryingBehaviour);
                    preserveQueryOriginalSender(getSender());
                    scheduleQueryTimeout();
                })
                .match(PolicyQueryCommand.class, this::unauthorized)

                /* PolicyEvents */
                .match(PolicyCreated.class, this::isApplicable,
                        policyCreated -> rebuildPolicyEnforcer(policyCreated.getPolicy(), policyCreated.getRevision()))
                .match(PolicyModified.class, this::isApplicable, policyModified ->
                        rebuildPolicyEnforcer(policyModified.getPolicy(), policyModified.getRevision()))
                .match(PolicyEvent.class, this::isApplicable, event -> {
                    log.debug("Got '{}', reloading Policy now...", event.getName());
                    policyEnforcer = null;
                    policyLoadWasAttempted = false;
                    synchronizePolicy();
                })
                .match(PolicyEvent.class, this::unhandled)

                /* policy cache updates */
                .match(Replicator.Changed.class, this::processChangedCacheEntry)

                /* other messages */
                .match(CheckActivity.class, check -> {
                    if (check.getAccessCounter() >= accessCounter) {
                        log.debug("Stopping due to inactivity.");
                        getContext().stop(getSelf());
                    } else {
                        scheduleCheckForActivity();
                    }
                })
                .match(RetrievePolicyResponse.class, resp -> log.debug(
                        "Received <{}> in 'EnforcingBehaviour'. Ignoring that one.", getSimpleClassName(resp)))
                .match(Status.Failure.class, failure -> {
                    Throwable cause = failure.cause();
                    if (cause instanceof JsonRuntimeException) {
                        // wrap JsonRuntimeExceptions
                        cause = new DittoJsonException((JsonRuntimeException) cause);
                    }
                    getSender().tell(cause, getSelf());
                })
                .matchAny(m -> {
                    final String correlationId = m instanceof WithDittoHeaders ?
                            ((WithDittoHeaders) m).getDittoHeaders().getCorrelationId().orElse("") : "";
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    log.warning("Received unknown message while in 'EnforcingBehaviour': <{}>!", m);
                })

                .build();
    }

    private Receive buildQueryingBehaviour() {
        return ReceiveBuilder.create()
                .match(CommandResponse.class, response -> response instanceof WithEntity, response -> {
                    queryTimeout.cancel();
                    final WithEntity withEntity = (WithEntity) response;
                    final JsonValue responseEntity = withEntity.getEntity();

                    if (responseEntity.isObject()) {
                        final JsonObject filterView = getJsonViewForResponse(response, responseEntity.asObject());
                        try {
                            queryOriginalSender.tell(withEntity.setEntity(filterView), getSelf());
                        } catch (final DittoRuntimeException e) {
                            log.warning("Received <{}> after building JsonView with PolicyEnforcer: <{}> ",
                                    getSimpleClassName(e), e.getMessage());
                            queryOriginalSender.tell(e, getSelf());
                        }
                    } else {
                        queryOriginalSender.tell(response, getSelf());
                    }
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                .match(AskTimeoutException.class, e -> {
                    log.warning("Waiting for response timed out!");
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                .match(DittoRuntimeException.class, cre -> {
                    queryTimeout.cancel();
                    log.info("An exception occurred while waiting for a response: <{}>", cre);
                    queryOriginalSender.tell(cre, getSelf());
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                .matchAny(msg -> doStash()) // stash all other messages
                .build();
    }

    private <T extends CommandResponse> JsonObject getJsonViewForResponse(final T commandResponse,
            final Iterable<JsonField> responseEntity) {

        final String resType = commandResponse.getResourceType();
        final ResourceKey resKey = ResourceKey.newInstance(resType, commandResponse.getResourcePath());
        final AuthorizationContext authorizationContext = commandResponse.getDittoHeaders().getAuthorizationContext();
        final JsonFieldSelector whitelist = whitelistedJsonFields.get(resType);

        return policyEnforcer.buildJsonView(resKey, responseEntity, authorizationContext, whitelist,
                Permissions.newInstance(READ));
    }

    private Receive buildSynchronizingBehaviour() {
        return ReceiveBuilder.create()
                /* and handle the PolicyCreated event */
                .match(PolicyCreated.class, this::isEnforcerNull, policyCreated -> {
                    LogUtil.enhanceLogWithCorrelationId(log, policyCreated);
                    synchronizationTimeout.cancel();
                    log.debug("Received <{}> event.", policyCreated.getType());
                    rebuildPolicyEnforcer(policyCreated.getPolicy(), policyCreated.getRevision());
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                /* expected response for an existing Policy after "synchronizePolicy" */
                .match(RetrievePolicyResponse.class, response -> {
                    LogUtil.enhanceLogWithCorrelationId(log, response);
                    synchronizationTimeout.cancel();
                    log.debug("Retrieved <{}> response.", response.getType());
                    rebuildPolicyEnforcer(response.getPolicy(), response.getPolicy()
                            .getRevision()
                            .map(PolicyRevision::toLong)
                            .orElse(policyRevision));
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                /* expected response for a non existing Policy after "synchronizePolicy" */
                .match(PolicyNotAccessibleException.class, e -> {
                    LogUtil.enhanceLogWithCorrelationId(log, e);
                    synchronizationTimeout.cancel();
                    log.debug("No policy accessible for ID <{}>.", policyId);
                    policyLoadWasAttempted = true;
                    getContext().become(enforcingBehaviour);
                    doUnstashAll();
                })
                .match(AskTimeoutException.class, e -> {
                    log.warning("Synchronization of policy <{}> timed out! Sending the PoisonPill to ourselves.",
                            policyId);

                    // We don't know if the Policy is accessible or not, stopping ourselves!
                    poisonThisActor();
                })
                .match(DittoRuntimeException.class, e -> {
                    LogUtil.enhanceLogWithCorrelationId(log, e);
                    log.warning("Received unexpected <{}> exception while synchronizing policy <{}>: <{}>!" +
                            " Sending PoisonPill to ourselves.", e.getErrorCode(), policyId, e.getMessage());
                    poisonThisActor();
                })
                .match(CheckActivity.class, check -> {
                    if (check.getAccessCounter() >= accessCounter) {
                        log.debug("Stopping due to inactivity.");
                        getContext().stop(getSelf());
                    } else {
                        scheduleCheckForActivity();
                    }
                })
                .matchAny(msg -> {
                    final String correlationId = msg instanceof WithDittoHeaders ?
                            ((WithDittoHeaders) msg).getDittoHeaders().getCorrelationId().orElse("") : "";
                    LogUtil.enhanceLogWithCorrelationId(log, correlationId);
                    if (stashCount < 1) {
                        // it's quite normal that one unexpected messages is processed when still in this behavior
                        // log to debug
                        log.debug("Received unknown message while in 'SynchronizingBehaviour': <{}>!" +
                                " Message will be stashed.", msg);
                    } else {
                        // if the stashCount is greater this is not normal - log warning!
                        log.warning("Received unknown message while in 'SynchronizingBehaviour': <{}>!" +
                                " Message will be stashed - current stashCount: <{}>", msg, stashCount);
                    }
                    doStash(); // stash all other messages
                })
                .build();
    }

    private void cancelTimeouts() {
        if (activityCheckCancellable != null) {
            activityCheckCancellable.cancel();
        }
        if (synchronizationTimeout != null) {
            synchronizationTimeout.cancel();
        }
        if (queryTimeout != null) {
            queryTimeout.cancel();
        }
    }

    private void processChangedCacheEntry(final Replicator.Changed changed) {
        final ReplicatedData replicatedData = changed.get(changed.key());
        if (replicatedData instanceof LWWRegister) {
            final LWWRegister<?> lwwRegister = (LWWRegister<?>) replicatedData;
            final Object value = lwwRegister.getValue();
            if (value instanceof PolicyCacheEntry) {
                processPolicyCacheEntry((CacheEntry) value);
            } else {
                log.warning("Received unknown cache entry <{}>.", value);
            }
        } else {
            log.warning("Received unknown cache ReplicatedData: <{}>.", replicatedData);
        }
    }

    private void processPolicyCacheEntry(final CacheEntry cacheEntry) {
        LogUtil.enhanceLogWithCorrelationId(log, "");
        log.debug("Received new <{}> with revision <{}>.", getSimpleClassName(cacheEntry), cacheEntry.getRevision());

        if (cacheEntry.isDeleted()) {
            log.info("<{}> was deleted: {}", getSimpleClassName(cacheEntry), cacheEntry);
            policyEnforcer = null;
            policyRevision = cacheEntry.getRevision();
            policyLoadWasAttempted = false;
        } else if (cacheEntry.getRevision() > policyRevision) {
            log.debug("The PolicyCacheEntry has the revision {} which is greater than the current actor's"
                    + " one <{}>.", cacheEntry.getRevision(), policyRevision);
            synchronizePolicy();
        }
    }

    private void doUnstashAll() {
        unstashAll();
        stashCount = 0;
    }

    private boolean isApplicable(final PolicyEvent event) {
        return event.getPolicyId().equals(policyId);
    }

    private boolean isAuthorized(final PolicyModifyCommand command) {
        return isEnforcerAvailable() &&
                policyEnforcer.hasUnrestrictedPermissions(
                        PoliciesResourceType.policyResource(command.getResourcePath()),
                        command.getDittoHeaders().getAuthorizationContext(),
                        WRITE);
    }

    private boolean isAuthorized(final PolicyQueryCommand command) {
        return isEnforcerAvailable() &&
                policyEnforcer.hasPartialPermissions(PoliciesResourceType.policyResource(command.getResourcePath()),
                        command.getDittoHeaders().getAuthorizationContext(), READ);
    }

    private void unauthorized(final PolicyModifyCommand command) {
        final PolicyCommandToModifyExceptionRegistry registry = PolicyCommandToModifyExceptionRegistry.getInstance();
        final DittoRuntimeException exception = registry.exceptionFrom(command);
        logUnauthorized(command, exception);
        getSender().tell(exception, getSelf());
    }

    private void unauthorized(final PolicyQueryCommand command) {
        final PolicyCommandToAccessExceptionRegistry registry = PolicyCommandToAccessExceptionRegistry.getInstance();
        final DittoRuntimeException exception = registry.exceptionFrom(command);
        logUnauthorized(command, exception);
        getSender().tell(exception, getSelf());
    }

    private Set<String> retrieveReadSubjects(final JsonPointer resourcePath, final String type) {
        if (policyEnforcer == null) {
            return new HashSet<>();
        } else if (ROOT_RESOURCE.equals(resourcePath) && type.equals(PoliciesResourceType.THING)) {
            return cachedThingReadSubjectsOnRoot;
        } else if (ROOT_RESOURCE.equals(resourcePath) && type.equals(PoliciesResourceType.POLICY)) {
            return cachedPolicyReadSubjectsOnRoot;
        } else {
            final EffectedSubjectIds effectedSubjectIds =
                    policyEnforcer.getSubjectIdsWithPermission(ResourceKey.newInstance(type, resourcePath), READ);
            return effectedSubjectIds.getGranted();
        }
    }

    private void forwardPolicySudoCommand(final Command command) {
        LogUtil.enhanceLogWithCorrelationId(log, command);
        logForwardingOfReceivedSignal(command, POLICIES_SERVICE_NAME);
        accessCounter++;
        policiesShardRegion.forward(command, getContext());
        if (command instanceof PolicyModifyCommand<?>) {
            synchronizePolicy();
        }
    }

    private void forwardPolicyCommandWithoutChangingActorState(final PolicyCommand<?> command) {
        LogUtil.enhanceLogWithCorrelationId(log, command);
        final PolicyCommand commandWithReadSubjects =
                enrichDittoHeaders(command, command.getResourcePath(), command.getResourceType());
        logForwardingOfReceivedSignal(commandWithReadSubjects, POLICIES_SERVICE_NAME);
        accessCounter++;
        policiesShardRegion.forward(commandWithReadSubjects, getContext());
    }

    private void forwardPolicyModifyCommand(final PolicyCommand command) {
        forwardPolicyCommandWithoutChangingActorState(command);
        synchronizePolicy();
    }

    private void tellCommand(final PolicyCommand<?> command) {
        LogUtil.enhanceLogWithCorrelationId(log, command);
        final PolicyCommand commandWithReadSubjects =
                enrichDittoHeaders(command, command.getResourcePath(), command.getResourceType());
        logForwardingOfReceivedSignal(command, POLICIES_SERVICE_NAME);
        accessCounter++;
        policiesShardRegion.tell(commandWithReadSubjects, getSelf());
    }

    private void scheduleCheckForActivity() {
        log.debug("Scheduling for Activity Check in <{}> minutes.", cacheInterval.toMinutes());
        if (activityCheckCancellable != null) {
            activityCheckCancellable.cancel();
        }
        // send a message to ourselves
        activityCheckCancellable = getContext().system().scheduler()
                .scheduleOnce(
                        cacheInterval,
                        getSelf(),
                        new CheckActivity(accessCounter), getContext().dispatcher(), getSelf());
    }

    private void poisonThisActor() {
        cancelTimeouts();

        // First handle stashed messages.
        getContext().become(enforcingBehaviour);
        doUnstashAll();

        // Afterwards, send the poison pill to ourselves.
        getSelf().tell(PoisonPill.getInstance(), getSelf());
    }

    static boolean isLiveSignal(final Signal<?> signal) {
        return signal.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
    }

    private static String getSimpleClassName(final Object o) {
        return o.getClass().getSimpleName();
    }

    /**
     * Message sent to self to check for activity.
     */
    @Immutable
    static final class CheckActivity {

        private final long accessCounter;

        CheckActivity(final long accessCounter) {
            this.accessCounter = accessCounter;
        }

        long getAccessCounter() {
            return accessCounter;
        }

    }

}
