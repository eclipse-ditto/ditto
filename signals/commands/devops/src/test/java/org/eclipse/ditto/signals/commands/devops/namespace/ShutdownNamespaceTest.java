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
package org.eclipse.ditto.signals.commands.devops.namespace;

/**
 * Tests {@link ShutdownNamespace}.
 */
public final class ShutdownNamespaceTest extends NamespaceCommandTestCases<ShutdownNamespace> {

    @Override
    Class<ShutdownNamespace> classUnderTest() {
        return ShutdownNamespace.class;
    }

    @Override
    NamespaceCommand<ShutdownNamespace> fromNamespace(final String namespace) {
        return ShutdownNamespace.of(namespace);
    }
}
