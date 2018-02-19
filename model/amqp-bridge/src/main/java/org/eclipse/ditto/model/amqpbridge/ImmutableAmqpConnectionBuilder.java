/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.model.amqpbridge;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;

/**
 * Builder for {@code ImmutableAmqpConnection}.
 */
class ImmutableAmqpConnectionBuilder implements AmqpConnectionBuilder {

    final String id;
    final ConnectionType connectionType;
    final AuthorizationSubject authorizationSubject;
    final String uri;
    @Nullable Set<String> sources;
    @Nullable String eventTarget;
    @Nullable String replyTarget;
    boolean failoverEnabled = true;
    boolean validateCertificate = true;
    int throttle = -1;
    int consumerCount = 1;
    int processorPoolSize = 5;

    private ImmutableAmqpConnectionBuilder(final String id, final ConnectionType connectionType,
            final String uri, final AuthorizationSubject authorizationSubject) {
        this.id = checkNotNull(id, "ID");
        this.connectionType = checkNotNull(connectionType, "Connection Type");
        this.uri = checkNotNull(uri, "URI");
        this.authorizationSubject = checkNotNull(authorizationSubject, "Authorization Subject");
    }

    /**
     * Instantiates a new {@code ImmutableAmqpConnectionBuilder}.
     *
     * @param id the connection id
     * @param connectionType the connection type
     * @param uri the uri
     * @param authorizationSubject the authorization subject
     * @return new instance of {@code ImmutableAmqpConnectionBuilder}
     */
    static AmqpConnectionBuilder of(final String id, final ConnectionType connectionType,
            final String uri, final AuthorizationSubject authorizationSubject) {
        return new ImmutableAmqpConnectionBuilder(id, connectionType, uri, authorizationSubject);
    }

    @Override
    public AmqpConnectionBuilder failoverEnabled(final boolean failoverEnabled) {
        this.failoverEnabled = failoverEnabled;
        return this;
    }

    @Override
    public AmqpConnectionBuilder validateCertificate(final boolean validateCertificate) {
        this.validateCertificate = validateCertificate;
        return this;
    }

    @Override
    public AmqpConnectionBuilder throttle(final int throttle) {
        this.throttle = throttle;
        return this;
    }

    @Override
    public AmqpConnectionBuilder consumerCount(final int consumerCount) {
        this.consumerCount = checkArgument(consumerCount, c -> c > 0, () -> "consumerCount must be positive");
        return this;
    }

    @Override
    public AmqpConnectionBuilder processorPoolSize(final int processorPoolSize) {
        this.processorPoolSize = checkArgument(processorPoolSize, ps -> ps > 0, () -> "consumerCount must be positive");
        return this;
    }

    @Override
    public AmqpConnectionBuilder sources(final String... sources) {
        checkNotNull(sources, "Sources");
        this.sources = new HashSet<>(Arrays.asList(sources));
        return this;
    }

    @Override
    public AmqpConnectionBuilder sources(final Set<String> sources) {
        checkNotNull(sources, "Sources");
        this.sources = new HashSet<>(sources);
        return this;
    }

    @Override
    public AmqpConnectionBuilder eventTarget(final String eventTarget) {
        checkNotNull(eventTarget, "eventTarget");
        this.eventTarget = eventTarget;
        return this;
    }

    @Override
    public AmqpConnectionBuilder replyTarget(final String replyTarget) {
        checkNotNull(replyTarget, "replyTarget");
        this.replyTarget = replyTarget;
        return this;
    }

    @Override
    public AmqpConnection build() {
        return new ImmutableAmqpConnection(this);
    }
}
