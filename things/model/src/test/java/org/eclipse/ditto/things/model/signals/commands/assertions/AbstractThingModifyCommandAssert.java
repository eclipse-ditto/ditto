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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.signals.commands.assertions.AbstractCommandAssert;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;

/**
 * An abstract Assert for {@link org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand}s.
 */
public abstract class AbstractThingModifyCommandAssert<S extends AbstractThingModifyCommandAssert<S, C>,
        C extends ThingModifyCommand<?>> extends AbstractThingCommandAssert<S, C> {

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
                    .overridingErrorMessage(
                            "Expected Command to have entity\n<%s> but it had\n<%s>", expectedEntity,
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
