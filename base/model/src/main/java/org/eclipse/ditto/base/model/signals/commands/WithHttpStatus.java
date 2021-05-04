/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.commands;

import org.eclipse.ditto.base.model.common.HttpStatus;

/**
 * Implementations of this interface have a Status, e.g. any type of responses.
 *
 * This is helpful for intermediate types which are only used inside one service and should not leave the service.
 * Such a type is not meant to be send around in the cluster.
 */
public interface WithHttpStatus {

    /**
     * Returns the HTTP status of the issued command.
     *
     * @return the HTTP status.
     * @since 2.0.0
     */
    HttpStatus getHttpStatus();

}
