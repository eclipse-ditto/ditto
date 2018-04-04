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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.utils.akka.functional.ImmutableActor;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Contains self-type requirements for aspects of enforcer actor dealing with specific commands.
 * Do NOT call the methods outside this package.
 */
public abstract class Enforcement<T extends WithDittoHeaders> {

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
     * Extend a signal by read-subjects header given by an enforcer.
     *
     * @param signal the signal to extend.
     * @param enforcer the enforcer.
     * @return the extended signal.
     */
    protected static <T extends Signal> T addReadSubjectsToSignal(final Signal<T> signal,
            final Enforcer enforcer) {

        return addReadSubjectsToSignal(signal, getReadSubjects(signal, enforcer));
    }

    /**
     * Extend a signal by read-subjects header given explicitly.
     *
     * @param <T> type of the signal.
     * @param signal the signal to extend.
     * @param readSubjects explicitly-given read subjects.
     * @return the extended signal.
     */
    protected static <T extends Signal> T addReadSubjectsToSignal(final Signal<T> signal,
            final Set<String> readSubjects) {

        final DittoHeaders newHeaders = signal.getDittoHeaders()
                .toBuilder()
                .readSubjects(readSubjects)
                .build();

        return signal.setDittoHeaders(newHeaders);
    }

    /**
     * Get read subjects from an enforcer.
     *
     * @param signal the signal to get read subjects for.
     * @param enforcer the enforcer.
     * @return read subjects of the signal.
     */
    protected static Set<String> getReadSubjects(final Signal<?> signal, final Enforcer enforcer) {
        final ResourceKey resourceKey =
                ResourceKey.newInstance(ThingCommand.RESOURCE_TYPE, signal.getResourcePath());
        return enforcer.getSubjectIdsWithPermission(resourceKey, Permission.READ).getGranted();
    }

    /**
     * @return Timeout duration for asking entity shard regions.
     */
    protected Duration getAskTimeout() {
        return context.askTimeout;
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
     * @return Akka pubsub mediator.
     */
    protected ActorRef pubSubMediator() {
        return context.pubSubMediator;
    }

    /**
     * @return actor reference of the enforcer actor this object belongs to.
     */
    protected ActorRef self() {
        return context.self;
    }

    public static final class Context {

        private final ActorRef pubSubMediator;
        private final Duration askTimeout;

        @Nullable
        private final EntityId entityId;

        @Nullable
        private final DiagnosticLoggingAdapter log;

        @Nullable
        private final ActorRef self;

        public Context(
                final ActorRef pubSubMediator,
                final Duration askTimeout) {

            this(pubSubMediator, askTimeout, null, null, null);
        }

        public Context(
                final ActorRef pubSubMediator,
                final Duration askTimeout,
                @Nullable final EntityId entityId,
                @Nullable final DiagnosticLoggingAdapter log,
                @Nullable final ActorRef self) {

            this.pubSubMediator = pubSubMediator;
            this.askTimeout = askTimeout;
            this.entityId = entityId;
            this.log = log;
            this.self = self;
        }

        public Context withActorUtils(final ImmutableActor.ActorUtils actorUtils) {
            final ActorRef self = actorUtils.context().self();
            return new Context(pubSubMediator, askTimeout, decodeEntityId(self), actorUtils.log(), self);
        }

        private static EntityId decodeEntityId(final ActorRef self) {
            final String name = self.path().name();
            try {
                final String typeWithPath = URLDecoder.decode(name, StandardCharsets.UTF_8.name());
                return EntityId.readFrom(typeWithPath);
            } catch (final UnsupportedEncodingException e) {
                throw new IllegalStateException("Unsupported encoding", e);
            }
        }
    }
}
