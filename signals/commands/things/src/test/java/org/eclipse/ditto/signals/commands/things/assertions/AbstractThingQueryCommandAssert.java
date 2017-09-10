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
package org.eclipse.ditto.signals.commands.things.assertions;

import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.signals.commands.base.assertions.AbstractCommandAssert;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;

/**
 * An abstract Assert for {@link ThingQueryCommand}s.
 */
public abstract class AbstractThingQueryCommandAssert<S extends AbstractThingQueryCommandAssert<S, C>, C extends
        ThingQueryCommand> extends AbstractThingCommandAssert<S, C> {

    /**
     * Constructs a new {@code AbstractThingQueryCommandAssert} object.
     *
     * @param actual the command to be checked.
     * @param selfType the type of the actual Assert.
     */
    protected AbstractThingQueryCommandAssert(final C actual, final Class<? extends AbstractCommandAssert> selfType) {
        super(actual, selfType);
    }

    public S withSelectedFields(final JsonFieldSelector expectedSelectedFields) {
        isNotNull();
        @SuppressWarnings("unchecked") final Optional<JsonFieldSelector> actualSelectedFields =
                actual.getSelectedFields();

        if (null != expectedSelectedFields) {
            Assertions.assertThat(actualSelectedFields)
                    .overridingErrorMessage("Expected Command to have selected fields\n<%s> but it had\n<%s>",
                            expectedSelectedFields, actualSelectedFields.orElse(null))
                    .contains(expectedSelectedFields);
        } else {
            Assertions.assertThat(actualSelectedFields)
                    .overridingErrorMessage("Expected Command not to have selected fields but it had\n<%s>",
                            actualSelectedFields.orElse(null))
                    .isEmpty();
        }
        return myself;
    }

}
