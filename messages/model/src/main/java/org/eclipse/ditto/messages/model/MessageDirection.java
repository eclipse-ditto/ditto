/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.messages.model;

/**
 * Direction specifies if a message has been sent <em>FROM</em> a {@code Thing} (or its {@code Feature}), or
 * <em>TO</em> a {@code Thing} (or its {@code Feature}).
 */
public enum MessageDirection {

    /**
     * Direction showing that the message was sent <em>FROM</em> a {@code Thing} or a {@code Feature}.
     */
    FROM,

    /**
     * Direction showing that the message was sent <em>TO</em> a {@code Thing} or a {@code Feature}.
     */
    TO;

}
