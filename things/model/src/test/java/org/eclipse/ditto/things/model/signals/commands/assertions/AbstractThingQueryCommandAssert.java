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
package org.eclipse.ditto.things.model.signals.commands.assertions;

import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.base.model.signals.commands.assertions.AbstractCommandAssert;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;

/**
 * An abstract Assert for {@link org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand}s.
 */
public abstract class AbstractThingQueryCommandAssert<S extends AbstractThingQueryCommandAssert<S, C>, C extends
        ThingQueryCommand<?>> extends AbstractThingCommandAssert<S, C> {

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
