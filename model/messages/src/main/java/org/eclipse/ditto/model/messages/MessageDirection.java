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
package org.eclipse.ditto.model.messages;

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
    TO

}
