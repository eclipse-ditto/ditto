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
package org.eclipse.ditto.edge.service.dispatching.signaltransformer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformer;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Pre-Enforcer which sets additional headers to a signal.
 */
@Immutable
public final class OriginatorSetterSignalTransformer implements SignalTransformer {

    /**
     * Constructs a new instance of HeaderSetterPreEnforcer extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    @SuppressWarnings("unused")
    public OriginatorSetterSignalTransformer(final ActorSystem actorSystem, final Config config) {
        // no-op
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal) {
        return CompletableFuture.completedFuture(setOriginatorHeader(signal));
    }

    /**
     * Set the "ditto-originator" header to the primary authorization subject of a signal.
     *
     * @param originalSignal A signal with authorization context.
     * @param <T> the type of the {@code originalSignal} to preserve in the response.
     * @return A copy of the signal with the header "ditto-originator" set.
     */
    @SuppressWarnings("unchecked")
    public static <T extends DittoHeadersSettable<?>> T setOriginatorHeader(final T originalSignal) {
        final DittoHeaders dittoHeaders = originalSignal.getDittoHeaders();
        final AuthorizationContext authorizationContext = dittoHeaders.getAuthorizationContext();
        return authorizationContext.getFirstAuthorizationSubject()
                .map(AuthorizationSubject::getId)
                .map(originatorSubjectId -> DittoHeaders.newBuilder(dittoHeaders)
                        .putHeader(DittoHeaderDefinition.ORIGINATOR.getKey(), originatorSubjectId)
                        .build())
                .map(originatorHeader -> (T) originalSignal.setDittoHeaders(originatorHeader))
                .orElse(originalSignal);
    }
}
