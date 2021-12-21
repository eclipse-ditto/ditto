/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.connectivity.model.GenericTarget;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.OutboundSignal;

/**
 * Context information for publishing a message to a generic target.
 */
@NotThreadSafe
final class SendingContext {

    private final OutboundSignal.Mapped outboundSignal;
    private final ExternalMessage externalMessage;
    private final GenericTarget genericTarget;
    private final ConnectionMonitor publishedMonitor;
    @Nullable private final ConnectionMonitor acknowledgedMonitor;
    private final ConnectionMonitor droppedMonitor;
    @Nullable private final Target autoAckTarget;
    @Nullable private final AuthorizationContext targetAuthorizationContext;

    private SendingContext(final OutboundSignal.Mapped outboundSignal,
            final ExternalMessage externalMessage,
            final GenericTarget genericTarget,
            final ConnectionMonitor publishedMonitor,
            @Nullable final ConnectionMonitor acknowledgedMonitor,
            final ConnectionMonitor droppedMonitor,
            @Nullable final Target autoAckTarget,
            @Nullable final AuthorizationContext targetAuthorizationContext) {

        this.outboundSignal = outboundSignal;
        this.externalMessage = externalMessage;
        this.genericTarget = genericTarget;
        this.publishedMonitor = publishedMonitor;
        this.acknowledgedMonitor = acknowledgedMonitor;
        this.droppedMonitor = droppedMonitor;
        this.autoAckTarget = autoAckTarget;
        this.targetAuthorizationContext = targetAuthorizationContext;
    }

    private SendingContext(final Builder builder) {
        outboundSignal = checkNotNull(builder.outboundSignal, "outboundSignal");
        externalMessage = checkNotNull(builder.externalMessage, "externalMessage");
        genericTarget = checkNotNull(builder.genericTarget, "genericTarget");
        publishedMonitor = checkNotNull(builder.publishedMonitor, "publishedMonitor");
        acknowledgedMonitor = builder.acknowledgedMonitor;
        droppedMonitor = checkNotNull(builder.droppedMonitor, "droppedMonitor");
        autoAckTarget = builder.autoAckTarget;
        targetAuthorizationContext = builder.targetAuthorizationContext;
    }

    static Builder newBuilder() {
        return new Builder();
    }

    OutboundSignal.Mapped getMappedOutboundSignal() {
        return outboundSignal;
    }

    ExternalMessage getExternalMessage() {
        return externalMessage;
    }

    GenericTarget getGenericTarget() {
        return genericTarget;
    }

    ConnectionMonitor getPublishedMonitor() {
        return publishedMonitor;
    }

    Optional<ConnectionMonitor> getAcknowledgedMonitor() {
        return Optional.ofNullable(acknowledgedMonitor);
    }

    ConnectionMonitor getDroppedMonitor() {
        return droppedMonitor;
    }

    Optional<Target> getAutoAckTarget() {
        return Optional.ofNullable(autoAckTarget);
    }

    Optional<AuthorizationContext> getTargetAuthorizationContext() {
        return Optional.ofNullable(targetAuthorizationContext);
    }

    boolean shouldAcknowledge() {
        return null != autoAckTarget && null != acknowledgedMonitor;
    }

    SendingContext setExternalMessage(final ExternalMessage externalMessage) {
        return new SendingContext(outboundSignal,
                externalMessage,
                genericTarget,
                publishedMonitor,
                acknowledgedMonitor,
                droppedMonitor,
                autoAckTarget,
                targetAuthorizationContext);
    }

    /**
     * Mutable builder with a fluent API for creating a SendingContext.
     */
    @NotThreadSafe
    static final class Builder {

        private OutboundSignal.Mapped outboundSignal;
        private ExternalMessage externalMessage;
        private GenericTarget genericTarget;
        private ConnectionMonitor publishedMonitor;
        @Nullable private ConnectionMonitor acknowledgedMonitor;
        private ConnectionMonitor droppedMonitor;
        @Nullable private Target autoAckTarget;
        @Nullable private AuthorizationContext targetAuthorizationContext;

        private Builder() {
            outboundSignal = null;
            externalMessage = null;
            genericTarget = null;
            publishedMonitor = null;
            acknowledgedMonitor = null;
            droppedMonitor = null;
            autoAckTarget = null;
            targetAuthorizationContext = null;
        }

        SendingContext build() {
            return new SendingContext(this);
        }

        Builder mappedOutboundSignal(final OutboundSignal.Mapped mappedOutboundSignal) {
            outboundSignal = checkNotNull(mappedOutboundSignal, "mappedOutboundSignal");
            return this;
        }

        Builder externalMessage(final ExternalMessage externalMessage) {
            this.externalMessage = checkNotNull(externalMessage, "externalMessage");
            return this;
        }

        Builder genericTarget(final GenericTarget genericTarget) {
            this.genericTarget = checkNotNull(genericTarget, "genericTarget");
            return this;
        }

        Builder publishedMonitor(final ConnectionMonitor publishedMonitor) {
            this.publishedMonitor = checkNotNull(publishedMonitor, "publishedMonitor");
            return this;
        }

        Builder acknowledgedMonitor(@Nullable final ConnectionMonitor acknowledgedMonitor) {
            this.acknowledgedMonitor = acknowledgedMonitor;
            return this;
        }

        Builder droppedMonitor(final ConnectionMonitor droppedMonitor) {
            this.droppedMonitor = checkNotNull(droppedMonitor, "droppedMonitor");
            return this;
        }

        Builder autoAckTarget(@Nullable final Target autoAckTarget) {
            this.autoAckTarget = autoAckTarget;
            return this;
        }

        Builder targetAuthorizationContext(@Nullable final AuthorizationContext targetAuthorizationContext) {
            this.targetAuthorizationContext = targetAuthorizationContext;
            return this;
        }

    }

}
