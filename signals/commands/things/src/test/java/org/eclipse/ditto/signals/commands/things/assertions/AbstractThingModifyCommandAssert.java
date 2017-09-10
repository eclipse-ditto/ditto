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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.commands.base.assertions.AbstractCommandAssert;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;

/**
 * An abstract Assert for {@link ThingModifyCommand}s.
 */
public abstract class AbstractThingModifyCommandAssert<S extends AbstractThingModifyCommandAssert<S, C>, C extends
        ThingModifyCommand> extends AbstractThingCommandAssert<S, C> {

    /**
     * Constructs a new {@code AbstractThingModifyCommandAssert} object.
     *
     * @param actual the command to be checked.
     * @param selfType the type of the actual Assert.
     */
    protected AbstractThingModifyCommandAssert(final C actual, final Class<? extends AbstractCommandAssert> selfType) {
        super(actual, selfType);
    }

    public S withEntity(final JsonValue expectedEntity) {
        isNotNull();
        final Optional<JsonValue> actualEntity = actual.getEntity();
        if (null != expectedEntity) {
            Assertions.assertThat(actualEntity)
                    .overridingErrorMessage("Expected Command to have entity\n<%s> but it had\n<%s>", expectedEntity,
                            actualEntity.orElse(null))
                    .contains(expectedEntity);
            return myself;
        }
        return withoutEntity();
    }

    public S withoutEntity() {
        isNotNull();
        final Optional<JsonValue> actualEntity = actual.getEntity();
        Assertions.assertThat(actualEntity)
                .overridingErrorMessage("Expected Command not to have an entity but it had\n<%s>",
                        actualEntity.orElse(null))
                .isEmpty();
        return myself;
    }

}
