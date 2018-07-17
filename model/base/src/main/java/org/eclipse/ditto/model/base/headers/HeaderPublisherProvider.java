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
package org.eclipse.ditto.model.base.headers;

/**
 * Provider of a header publisher to be loaded dynamically. All classes implementing this interface should be
 * top-level classes (i. e., not inner classes) for ease of dynamic loading.
 */
public interface HeaderPublisherProvider {

    /**
     * Create a header publisher.
     *
     * @return a new header publisher.
     */
    HeaderPublisher get();
}
