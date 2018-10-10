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
package org.eclipse.ditto.signals.commands.things;

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandRegistry;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandRegistry;
import org.junit.Test;

/**
 * Unit test for {@link ThingCommandRegistry}.
 */
public final class ThingCommandRegistryTest {


    @Test
    public void parseThingModifyCommand() {
        final ThingModifyCommandRegistry commandRegistry = ThingModifyCommandRegistry.newInstance();

        final CreateThing command = CreateThing.of(TestConstants.Thing.THING, null,
                TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = command.toJson(FieldType.regularOrSpecial());

        final Command parsedCommand = commandRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedCommand).isEqualTo(command);
    }


    @Test
    public void parseThingQueryCommand() {
        final ThingQueryCommandRegistry commandRegistry = ThingQueryCommandRegistry.newInstance();

        final RetrieveThing command = RetrieveThing.of(
                TestConstants.Thing.THING_ID, TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = command.toJson(FieldType.regularOrSpecial());

        final Command parsedCommand = commandRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedCommand).isEqualTo(command);
    }

}
