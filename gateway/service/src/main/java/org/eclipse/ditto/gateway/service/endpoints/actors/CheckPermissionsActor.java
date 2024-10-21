/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.actors;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.pattern.PatternsCS;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions.CheckPermissions;
import org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions.CheckPermissionsResponse;
import org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions.ImmutablePermissionCheck;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourcePermissionFactory;
import org.eclipse.ditto.policies.model.ResourcePermissions;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyCheckPermissionsCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyCheckPermissionsResponse;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.ThingId;

import software.amazon.awssdk.utils.Pair;

public class CheckPermissionsActor extends AbstractActor {

    private final ThreadSafeDittoLogger logger = DittoLoggerFactory.getThreadSafeLogger(getClass());
    protected final ActorRef edgeCommandForwarder;
    protected final ActorRef sender;
    private final Duration defaultAskTimeout;

    public static final String ACTOR_NAME = "checkPermissionsActor";
    public static final String POLICY_RESOURCE = "policy";

    public CheckPermissionsActor(final ActorRef edgeCommandForwarder, ActorRef sender, Duration defaultTimeout) {
        this.edgeCommandForwarder = edgeCommandForwarder;
        this.sender = sender;
        this.defaultAskTimeout = defaultTimeout;
    }

    public static Props props(ActorRef edgeCommandForwarder, ActorRef sender, Duration defaultTimeout) {
        return Props.create(CheckPermissionsActor.class, edgeCommandForwarder, sender, defaultTimeout);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(CheckPermissions.class, this::handleCheckPermissions)
                .matchAny(msg -> logger.warn("Unknown message: <{}>", msg))
                .build();
    }

    private void handleCheckPermissions(CheckPermissions command) {
        Map<Pair<String, String>, List<Map.Entry<String, ImmutablePermissionCheck>>> groupedByEntityIdAndResource =
                groupByEntityAndResource(command);

        Map<String, Boolean> permissionResults = new HashMap<>();

        CompletableFuture<Void> allChecks = CompletableFuture.allOf(
                groupedByEntityIdAndResource.entrySet().stream().map(entry -> {
                    Pair<String, String> entityAndResource = entry.getKey();
                    String entityId = entityAndResource.left();
                    String resource = entityAndResource.right();
                    List<Map.Entry<String, ImmutablePermissionCheck>> permissionChecks = entry.getValue();

                    return performPermissionCheck(entityId, resource, permissionChecks, command, permissionResults);
                }).toArray(CompletableFuture[]::new)
        );

        allChecks.thenRun(() -> {
            CheckPermissionsResponse response =
                    CheckPermissionsResponse.of(permissionResults, command.getDittoHeaders());
            sender.tell(response, getSelf());
        }).exceptionally(ex -> {
            HttpResponse errorResponse = createHttpResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred: " + ex.getMessage());
            getSender().tell(errorResponse, getSelf());
            return null;
        });
    }

    private Map<Pair<String, String>, List<Map.Entry<String, ImmutablePermissionCheck>>> groupByEntityAndResource(
            CheckPermissions command) {
        return command.getPermissionChecks().entrySet().stream()
                .collect(Collectors.groupingBy(entry -> Pair.of(
                        entry.getValue().getEntityId(),
                        entry.getValue().getResource()
                )));
    }

    private CompletionStage<Void> performPermissionCheck(String entityId, String resource,
            List<Map.Entry<String, ImmutablePermissionCheck>> permissionChecks,
            CheckPermissions command, Map<String, Boolean> permissionResults) {

        Duration askTimeout = command.getDittoHeaders().getTimeout().orElse(defaultAskTimeout);

        return retrievePolicyIdForEntity(entityId, resource, command.getDittoHeaders())
                .thenCompose(policyId -> {
                    if (policyId != null) {
                        Map<String, ResourcePermissions> resourcePermissionsMap = permissionChecks.stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        checkEntry -> createResourcePermissions(checkEntry.getValue())
                                ));

                        PolicyCheckPermissionsCommand policyCommand = PolicyCheckPermissionsCommand.of(
                                policyId, resourcePermissionsMap, command.getDittoHeaders());

                        return Patterns.ask(edgeCommandForwarder, policyCommand, askTimeout)
                                .thenAccept(result -> processPolicyCommandResult(result, permissionChecks,
                                        permissionResults))
                                .exceptionally(ex -> {
                                    logError(ex, entityId, resource);
                                    return null;
                                });
                    } else {
                        setPermissionCheckFailure(permissionChecks, permissionResults);
                        return CompletableFuture.completedFuture(null);
                    }
                }).exceptionally(ex -> {
                    setPermissionCheckFailure(permissionChecks, permissionResults);
                    logError(ex, entityId, resource);
                    return null;
                });
    }

    private void processPolicyCommandResult(Object result,
            List<Map.Entry<String, ImmutablePermissionCheck>> permissionChecks,
            Map<String, Boolean> permissionResults) {
        if (result instanceof PolicyCheckPermissionsResponse response) {
            permissionResults.putAll(PolicyCheckPermissionsResponse.toMap(response.getPermissionsResults()));
        } else {
            setPermissionCheckFailure(permissionChecks, permissionResults);
        }
    }

    private void setPermissionCheckFailure(List<Map.Entry<String, ImmutablePermissionCheck>> permissionChecks,
            Map<String, Boolean> permissionResults) {
        permissionChecks.forEach(check -> permissionResults.put(check.getKey(), false));
    }


    private void logError(Throwable ex, String entityId, String resource) {
        logger.error("Permission check failed for entity: {} and resource: {}. Error: {}", entityId, resource,
                ex.getMessage());
    }

    private ResourcePermissions createResourcePermissions(ImmutablePermissionCheck check) {
        String resourceType = check.getResource().split(":")[0];
        String resourcePath = check.getResource();
        List<String> permissions = check.getHasPermissions();

        return ResourcePermissionFactory.newInstance(resourceType, resourcePath, permissions);
    }

    private CompletionStage<PolicyId> retrievePolicyIdForEntity(String entityId, String resource,
            DittoHeaders headers) {

        if (resource.contains(POLICY_RESOURCE)) {
            return CompletableFuture.completedFuture(PolicyId.of(entityId));
        } else {
            SudoRetrieveThing retrieveThing =
                    SudoRetrieveThing.of(ThingId.of(entityId), JsonFieldSelector.newInstance("policyId"), headers);
            return PatternsCS.ask(edgeCommandForwarder, retrieveThing, Duration.ofSeconds(1))
                    .thenApply(result -> {
                        if (result instanceof SudoRetrieveThingResponse thingResponse) {
                            return thingResponse.getThing().getPolicyId().orElse(null);
                        }
                        return null;
                    });
        }
    }

    private HttpResponse createHttpResponse(HttpStatus status, String message) {
        return HttpResponse.create().withStatus(status.getCode()).withEntity(ContentTypes.TEXT_PLAIN_UTF8, message);
    }

}
