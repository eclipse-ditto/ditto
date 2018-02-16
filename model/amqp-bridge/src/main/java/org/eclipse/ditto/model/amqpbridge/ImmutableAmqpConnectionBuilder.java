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

import java.util.Set;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;

/**
 * Builder for {@code ImmutableAmqpConnection}.
 */
class ImmutableAmqpConnectionBuilder implements AmqpConnectionBuilder {

    final String id;
    final ConnectionType connectionType;
    final AuthorizationSubject authorizationSubject;
    final Set<String> sources;
    final String uri;
    boolean failoverEnabled = true;
    boolean validateCertificate = true;
    int throttle = -1;
    int consumerCount = 1;
    int processorPoolSize = 5;

    private ImmutableAmqpConnectionBuilder(final String id, final ConnectionType connectionType,
            final String uri, final AuthorizationSubject authorizationSubject, final Set<String> sources) {
        this.id = checkNotNull(id, "ID");
        this.connectionType = checkNotNull(connectionType, "Connection Type");
        this.uri = checkNotNull(uri, "URI");
        this.authorizationSubject = checkNotNull(authorizationSubject, "Authorization Subject");
        this.sources = checkNotNull(sources, "Sources");
    }

    /**
     * Instantiates a new {@code ImmutableAmqpConnectionBuilder}.
     *
     * @param id the connection id
     * @param connectionType the connection type
     * @param uri the uri
     * @param authorizationSubject the authorization subject
     * @param sources the sources
     * @return new instance of {@code ImmutableAmqpConnectionBuilder}
     */
    static ImmutableAmqpConnectionBuilder of(final String id, final ConnectionType connectionType,
            final String uri, final AuthorizationSubject authorizationSubject, final Set<String> sources) {
        return new ImmutableAmqpConnectionBuilder(id, connectionType, uri, authorizationSubject, sources);
    }

    @Override
    public ImmutableAmqpConnectionBuilder failoverEnabled(boolean failoverEnabled) {
        this.failoverEnabled = failoverEnabled;
        return this;
    }

    @Override
    public ImmutableAmqpConnectionBuilder validateCertificate(boolean validateCertificate) {
        this.validateCertificate = validateCertificate;
        return this;
    }

    @Override
    public ImmutableAmqpConnectionBuilder throttle(int throttle) {
        this.throttle = throttle;
        return this;
    }

    @Override
    public ImmutableAmqpConnectionBuilder consumerCount(int consumerCount) {
        this.consumerCount = checkArgument(consumerCount, c -> c > 0, () -> "consumerCount must be positiv");
        return this;
    }

    @Override
    public ImmutableAmqpConnectionBuilder processorPoolSize(int processorPoolSize) {
        this.processorPoolSize = checkArgument(processorPoolSize, ps -> ps > 0, () -> "consumerCount must be positiv");
        ;
        return this;
    }

    @Override
    public ImmutableAmqpConnection build() {
        return new ImmutableAmqpConnection(this);
    }
}
