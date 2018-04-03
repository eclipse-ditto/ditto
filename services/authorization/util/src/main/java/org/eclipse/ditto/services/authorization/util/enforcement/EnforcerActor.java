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

import static java.util.Objects.requireNonNull;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that enforces authorization.
 */
public final class EnforcerActor extends AbstractActor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final Enforcement.Context context;

    private final Duration askTimeout = Duration.ofSeconds(10); // TODO: make configurable

    private final Set<EnforcementProvider<?>> enforcementProviders;
    @Nullable
    private final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer;

    private EnforcerActor(
            final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            @Nullable Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {
        final EntityId entityId = decodeEntityId(getSelf());

        this.enforcementProviders = requireNonNull(enforcementProviders);
        this.preEnforcer = preEnforcer;

        this.context = new Enforcement.Context(
                pubSubMediator,
                askTimeout,
                entityId,
                log,
                getSelf());
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders) {

        return Props.create(EnforcerActor.class,
                () -> new EnforcerActor(pubSubMediator, enforcementProviders, null));
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param preEnforcer a function executed before actual enforcement, may be {@code null}.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final Set<EnforcementProvider<?>> enforcementProviders,
            @Nullable Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {

        return Props.create(EnforcerActor.class,
                () -> new EnforcerActor(pubSubMediator, enforcementProviders, preEnforcer));
    }

    @Override
    public Receive createReceive() {
        final ReceiveBuilder receiveBuilder = ReceiveBuilder.create();

        enforcementProviders.forEach(provider -> addToReceiveBuilder(receiveBuilder, provider));

        receiveBuilder.matchAny(message -> {
            log.warning("Unexpected message: <{}>", message);
            unhandled(message);
        });

        return receiveBuilder.build();
    }

    private <T extends WithDittoHeaders> void addToReceiveBuilder(
            final ReceiveBuilder receiveBuilder,
            final EnforcementProvider<T> provider) {

        receiveBuilder.match(provider.getCommandClass(), provider::isApplicable, cmd -> handleMessage(provider, cmd));
    }

    private CompletionStage<Void> handleMessage(final EnforcementProvider provider, final WithDittoHeaders message) {
        final ActorRef sender = sender();
        final CompletionStage<Void> cs;
        if (preEnforcer != null) {
            cs = preEnforcer.apply(message)
                    .thenCompose(response -> handleEnforcement(provider, message, sender));
        } else {
            cs = handleEnforcement(provider, message, sender);
        }

        return cs.exceptionally(t -> {
            final Throwable rootCause = extractRootCause(t);
            if (rootCause instanceof DittoRuntimeException) {
                log.debug("Got DittoRuntimeException, sending back to sender: <{}>.", rootCause);
                sender.tell(rootCause, getSelf());
            } else {
                log.error(rootCause, "Got unexpected exception.");
                final GatewayInternalErrorException responseEx =
                        GatewayInternalErrorException.newBuilder()
                                .dittoHeaders(message.getDittoHeaders())
                                .cause(rootCause)
                                .build();
                sender.tell(responseEx, getSelf());
            }
            return null;
        });
    }

    private static Throwable extractRootCause(final Throwable t) {
        if (t instanceof CompletionException) {
            return extractRootCause(t.getCause());
        }
        return t;
    }

    private CompletionStage<Void> handleEnforcement(final EnforcementProvider provider, final WithDittoHeaders message,
            final ActorRef sender) {
        return CompletableFuture.supplyAsync(() -> {
            @SuppressWarnings("unchecked") final Enforcement<WithDittoHeaders> enforcement =
                    provider.createEnforcement(context);
            enforcement.enforce(message, sender);
            return null;
        });
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
