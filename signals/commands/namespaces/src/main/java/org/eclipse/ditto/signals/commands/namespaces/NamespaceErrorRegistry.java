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

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.signals.base.AbstractErrorRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;

/**
 * An {@link org.eclipse.ditto.signals.base.ErrorRegistry} aware of namespace related
 * {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException}s.
 */
@Immutable
public final class NamespaceErrorRegistry extends AbstractErrorRegistry<DittoRuntimeException> {

    private NamespaceErrorRegistry(final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies) {
        super(parseStrategies);
    }

    public static NamespaceErrorRegistry getInstance() {
        final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies = new HashMap<>();
        parseStrategies.put(NamespaceBlockedException.ERROR_CODE, NamespaceBlockedException::fromJson);

        return new NamespaceErrorRegistry(parseStrategies);
    }

}
