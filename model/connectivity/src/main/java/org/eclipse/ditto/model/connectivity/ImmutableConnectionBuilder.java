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
package org.eclipse.ditto.model.connectivity;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;

/**
 * Builder for {@code ImmutableConnection}.
 */
class ImmutableConnectionBuilder implements ConnectionBuilder {

    final String id;
    final ConnectionType connectionType;
    final AuthorizationContext authorizationContext;
    final String uri;
    boolean failoverEnabled = true;
    boolean validateCertificate = true;
    final Set<Source> sources = new HashSet<>();
    final Set<Target> targets = new HashSet<>();
    int clientCount = 1;
    int throttle = -1;
    int processorPoolSize = 5;

    private ImmutableConnectionBuilder(final String id, final ConnectionType connectionType,
            final String uri, final AuthorizationContext authorizationContext) {
        this.id = checkNotNull(id, "ID");
        this.connectionType = checkNotNull(connectionType, "Connection Type");
        this.uri = checkNotNull(uri, "URI");
        this.authorizationContext = checkNotNull(authorizationContext, "Authorization Context");
    }

    /**
     * Instantiates a new {@code ImmutableConnectionBuilder}.
     *
     * @param id the connection id
     * @param connectionType the connection type
     * @param uri the uri
     * @param authorizationContext the authorization context
     * @return new instance of {@code ImmutableConnectionBuilder}
     */
    static ConnectionBuilder of(final String id, final ConnectionType connectionType,
            final String uri, final AuthorizationContext authorizationContext) {
        return new ImmutableConnectionBuilder(id, connectionType, uri, authorizationContext);
    }

    @Override
    public ConnectionBuilder failoverEnabled(final boolean failoverEnabled) {
        this.failoverEnabled = failoverEnabled;
        return this;
    }

    @Override
    public ConnectionBuilder validateCertificate(final boolean validateCertificate) {
        this.validateCertificate = validateCertificate;
        return this;
    }

    @Override
    public ConnectionBuilder throttle(final int throttle) {
        this.throttle = throttle;
        return this;
    }

    @Override
    public ConnectionBuilder processorPoolSize(final int processorPoolSize) {
        this.processorPoolSize = checkArgument(processorPoolSize, ps -> ps > 0, () -> "consumerCount must be positive");
        return this;
    }

    @Override
    public ConnectionBuilder sources(final Set<Source> sources) {
        checkNotNull(sources, "Sources");
        this.sources.addAll(sources);
        return this;
    }

    @Override
    public ConnectionBuilder targets(final Set<Target> targets) {
        checkNotNull(targets, "Targets");
        this.targets.addAll(targets);
        return this;
    }

    @Override
    public ConnectionBuilder clientCount(final int clientCount) {
        this.clientCount = checkArgument(clientCount, ps -> ps > 0, () -> "clientCount must > 0");
        return this;
    }

    @Override
    public Connection build() {
        return new ImmutableConnection(this);
    }
}
