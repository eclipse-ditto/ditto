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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.utils.akka.functional.AsyncPartial;
import org.eclipse.ditto.services.utils.akka.functional.ImmutableActor;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Utility class to create actors that enforce authorization.
 */
public final class EnforcerActor {

    private static final Duration askTimeout = Duration.ofSeconds(10); // TODO: make configurable

    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders) {

        return props(pubSubMediator, enforcementProviders, null);
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param preEnforcer a function executed before actual enforcement, may be {@code null}.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            @Nullable Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {

        final ImmutableActor.Builder<Enforcement.Context, WithDittoHeaders, Object> preEnforcerBuilder;

        if (preEnforcer == null) {
            preEnforcerBuilder = context -> actorUtils -> sender -> AsyncPartial.fromTotal(Function.identity());
        } else {
            preEnforcerBuilder = context -> actorUtils -> {
                final DiagnosticLoggingAdapter log = actorUtils.log();
                final ActorRef self = actorUtils.context().self();
                return sender -> message ->
                        preEnforcer.apply(message)
                                .<Optional<Object>>thenApply(Optional::of)
                                .exceptionally(t -> {
                                    final Throwable rootCause = extractRootCause(t);
                                    if (rootCause instanceof DittoRuntimeException) {
                                        log.debug("Got DittoRuntimeException, sending back to sender: <{}>.",
                                                rootCause);
                                        sender.tell(rootCause, self);
                                    } else {
                                        log.error(rootCause, "Got unexpected exception.");
                                        final GatewayInternalErrorException responseEx =
                                                GatewayInternalErrorException.newBuilder()
                                                        .dittoHeaders(message.getDittoHeaders())
                                                        .cause(rootCause)
                                                        .build();
                                        sender.tell(responseEx, self);
                                    }
                                    return Optional.empty();
                                });
            };
        }

        return props(pubSubMediator, createEnforcementBuilder(enforcementProviders.stream()), preEnforcerBuilder);
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param preEnforcer a function executed before actual enforcement, may be {@code null}.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ImmutableActor.Builder<Enforcement.Context, Object, Void> enforcementProviders,
            final ImmutableActor.Builder<Enforcement.Context, WithDittoHeaders, Object> preEnforcer) {

        return Props.create(ImmutableActor.class, () -> {
            final Enforcement.Context context = new Enforcement.Context(pubSubMediator, askTimeout);
            final ImmutableActor.Builder<Enforcement.Context, WithDittoHeaders, Void> actorBuilder =
                    preEnforcer.then(enforcementProviders);

            return actorBuilder.build(WithDittoHeaders.class, context);
        });
    }

    private static ImmutableActor.Builder<Enforcement.Context, Object, Void> createEnforcementBuilder(
            final Stream<EnforcementProvider<?>> enforcementProviders) {

        return enforcementProviders.map(EnforcementProvider::toActorBuilder)
                .reduce(ImmutableActor.Builder::orElse)
                .orElseGet(ImmutableActor.Builder::empty);
    }

    private static Throwable extractRootCause(final Throwable t) {
        if (t instanceof CompletionException) {
            return extractRootCause(t.getCause());
        }
        return t;
    }
}
