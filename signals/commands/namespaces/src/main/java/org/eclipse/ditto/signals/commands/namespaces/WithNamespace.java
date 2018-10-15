/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.namespaces;

/**
 * Common interface for all types which provide access to a namespace.
 */
public interface WithNamespace {

    /**
     * Returns the namespace this command affects.
     *
     * @return the target namespace.
     */
    String getNamespace();

}
