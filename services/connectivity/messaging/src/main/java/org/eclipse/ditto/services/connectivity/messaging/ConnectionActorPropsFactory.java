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
package org.eclipse.ditto.services.connectivity.messaging;

import org.eclipse.ditto.model.connectivity.Connection;

import akka.actor.Props;

/**
 * Creates actor {@link Props} based on the given {@link Connection}.
 */
public interface ConnectionActorPropsFactory {

    /**
     * Create actor {@link Props} for a connection.
     *
     * @param connection the connection
     * @return the actor props
     */
    Props getActorPropsForType(Connection connection);

}
