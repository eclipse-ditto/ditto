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
package org.eclipse.ditto.signals.commands.thingsearch.exceptions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.signals.base.AbstractErrorRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.CommonErrorRegistry;

/**
 * A {@link org.eclipse.ditto.signals.base.ErrorRegistry} aware of all {@link ThingSearchException}s.
 */
@Immutable
public final class ThingSearchErrorRegistry extends AbstractErrorRegistry<DittoRuntimeException> {

    private ThingSearchErrorRegistry(final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code SearchErrorRegistry}.
     *
     * @return the error registry.
     */
    public static ThingSearchErrorRegistry newInstance() {
        return newInstance(Collections.emptyMap());
    }

    /**
     * Returns a new {@code SearchErrorRegistry} providing {@code additionalParseStrategies} as argument - that way
     * the user of this SearchErrorRegistry can register additional parsers for his own extensions of
     * {@link DittoRuntimeException}.
     *
     * @param additionalParseStrategies a map containing of DittoRuntimeException ERROR_CODE and JsonParsable of
     * DittoRuntimeException as values.
     * @return the error registry.
     */
    public static ThingSearchErrorRegistry newInstance(
            final Map<String, JsonParsable<DittoRuntimeException>> additionalParseStrategies) {
        final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies =
                new HashMap<>(additionalParseStrategies);

        final CommonErrorRegistry commonErrorRegistry = CommonErrorRegistry.newInstance();
        commonErrorRegistry.getTypes()
                .forEach(type -> parseStrategies.put(type, commonErrorRegistry::parse));

        parseStrategies.put(InvalidFilterException.ERROR_CODE, InvalidFilterException::fromJson);
        parseStrategies.put(InvalidOptionException.ERROR_CODE, InvalidOptionException::fromJson);

        return new ThingSearchErrorRegistry(parseStrategies);
    }
}
