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
package org.eclipse.ditto.base.model.acks;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;

/**
 * Mark exceptions that signal the failure of an outstanding or existing request to Ditto pubsub.
 * Receivers are expected to terminate or restart.
 *
 * @since 1.5.0
 */
@Immutable
public interface FatalPubSubException {

    /**
     * Return this object as a Ditto runtime exception.
     * Work-around for the lack of self-type constraints.
     *
     * @return this object as a Ditto runtime exception.
     */
    DittoRuntimeException asDittoRuntimeException();
}
