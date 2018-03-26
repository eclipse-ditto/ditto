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
package org.eclipse.ditto.services.authorization.util.enforcement;

import java.time.Duration;
import java.util.Set;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Contains self-type requirements for aspects of enforcer actor dealing with specific commands.
 * Do NOT call the methods outside this package.
 */
public abstract class Enforcement<T extends Command> {

    private final Context context;

    protected Enforcement(final Context context) {
        this.context = context;
    }

    /**
     * Authorize a command.
     *
     * @param command the command to authorize.
     * @param sender sender of the command.
     */
    public abstract void enforce(final T command, final ActorRef sender);

    /**
     * Retrieve the things-shard-region from the entity region map.
     *
     * @return the things shard region if it exists in the entity region map, dead letters otherwise.
     */
    protected ActorRef thingsShardRegion() {
        return shardRegionForResourceType(ThingCommand.RESOURCE_TYPE);
    }

    /**
     * Retrieve the policies-shard-region from the entity region map.
     *
     * @return the policies shard region if it exists in the entity region map, deadletters otherwise.
     */
    protected ActorRef policiesShardRegion() {
        return shardRegionForResourceType(PolicyCommand.RESOURCE_TYPE);
    }

    /**
     * Convenience method: retrieve shard region for a resource type.
     *
     * @param resourceType the resource type.
     * @return the shard region.
     */
    protected ActorRef shardRegionForResourceType(final String resourceType) {
        return entityRegionMap().lookup(resourceType).orElseThrow(() -> new IllegalStateException("Unknown resource " +
                "type: " + resourceType));
    }

    /**
     * Reply a message to sender.
     *
     * @param message message to forward.
     * @param sender whom to reply to.
     * @return true.
     */
    protected boolean replyToSender(final Object message, final ActorRef sender) {
        sender.tell(message, self());
        return true;
    }

    /**
     * Report unexpected error or unknown response.
     */
    protected void reportUnexpectedErrorOrResponse(final String hint,
            final ActorRef sender,
            final Object response,
            final Throwable error) {

        if (error != null) {
            reportUnexpectedError(hint, sender, error);
        } else {
            reportUnknownResponse(hint, sender, response);
        }
    }

    /**
     * Report unknown error.
     */
    protected void reportUnexpectedError(final String hint, final ActorRef sender, final Throwable error) {
        log().error(error, "Unexpected error {}", hint);

        // TODO CR-5400: set headers.
        sender.tell(GatewayInternalErrorException.newBuilder().build(), self());
    }

    /**
     * Report unknown response.
     */
    protected void reportUnknownResponse(final String hint, final ActorRef sender, final Object response) {
        log().error("Unexpected response {}: <{}>", hint, response);

        // TODO CR-5400: set headers.
        sender.tell(GatewayInternalErrorException.newBuilder().build(), self());
    }

    /**
     * Extend a command by read-subjects header given by an enforcer.
     *
     * @param command the command to extend.
     * @param enforcer the enforcer.
     * @return the extended command.
     */
    protected static <T extends Command> T addReadSubjectsToCommand(final Command<T> command,
            final Enforcer enforcer) {

        return addReadSubjectsToCommand(command, getReadSubjects(command, enforcer));
    }

    /**
     * Extend a command by read-subjects header given explicitly.
     *
     * @param <T> type of the thing command.
     * @param command the command to extend.
     * @param readSubjects explicitly-given read subjects.
     * @return the extended command.
     */
    protected static <T extends Command> T addReadSubjectsToCommand(final Command<T> command,
            final Set<String> readSubjects) {

        final DittoHeaders newHeaders = command.getDittoHeaders()
                .toBuilder()
                .readSubjects(readSubjects)
                .build();

        return command.setDittoHeaders(newHeaders);
    }

    /**
     * Get read subjects from an enforcer.
     *
     * @param command the command to get read subjects for.
     * @param enforcer the enforcer.
     * @return read subjects of the command.
     */
    protected static Set<String> getReadSubjects(final Command<?> command, final Enforcer enforcer) {
        final ResourceKey resourceKey =
                ResourceKey.newInstance(ThingCommand.RESOURCE_TYPE, command.getResourcePath());
        return enforcer.getSubjectIdsWithPermission(resourceKey, Permission.READ).getGranted();
    }

    /**
     * @return Timeout duration for asking entity shard regions.
     */
    protected Duration getAskTimeout() {
        return context.askTimeout;
    }

    /**
     * @return the entity region map.
     */
    protected EntityRegionMap entityRegionMap() {
        return context.entityRegionMap;
    }

    /**
     * @return the entity ID.
     */
    protected EntityId entityId() {
        return context.entityId;
    }

    /**
     * @return the diagnostic logging adapter.
     */
    protected DiagnosticLoggingAdapter log() {
        return context.log;
    }

    /**
     * @return the authorization caches.
     */
    protected AuthorizationCaches caches() {
        return context.caches;
    }

    /**
     * @return actor reference of the enforcer actor this object belongs to.
     */
    protected ActorRef self() {
        return context.self;
    }

    public static final class Context {

        private final Duration askTimeout;
        private final EntityRegionMap entityRegionMap;
        private final EntityId entityId;
        private final DiagnosticLoggingAdapter log;
        private final AuthorizationCaches caches;
        private final ActorRef self;

        public Context(final Duration askTimeout,
                final EntityRegionMap entityRegionMap,
                final EntityId entityId,
                final DiagnosticLoggingAdapter log,
                final AuthorizationCaches caches,
                final ActorRef self) {

            this.askTimeout = askTimeout;
            this.entityRegionMap = entityRegionMap;
            this.entityId = entityId;
            this.log = log;
            this.caches = caches;
            this.self = self;
        }
    }
}
