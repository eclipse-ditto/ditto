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
package org.eclipse.ditto.services.utils.akka.streaming;

/**
 * Defines a callback for a forwarder.
 */
public interface ForwarderCallback {

    /**
     * Has to be called for each forwarded element - <strong>before</strong> the element is actually forwarded,
     * otherwise messages may not be acknowledged by the forwarder.
     * If you split up an incoming element to {@code n} elements, you have to make sure to call this method {@code n}
     * times with unique identifiers. Otherwise the forwarder cannot
     * make sure that each element has been handled by its receiver.
     *
     * @param elementIdentifier the identifier of the forwarded element
     */
    void forwarded(String elementIdentifier);
}
