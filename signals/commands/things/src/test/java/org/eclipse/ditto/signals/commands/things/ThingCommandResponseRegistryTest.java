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
package org.eclipse.ditto.signals.commands.things;

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.junit.Test;

/**
 * Unit test for {@link ThingCommandResponseRegistry}.
 */
public class ThingCommandResponseRegistryTest {


    @Test
    public void parseThingModifyCommandResponse() {
        final ThingCommandResponseRegistry commandResponseRegistry = ThingCommandResponseRegistry.newInstance();

        final ModifyThingResponse commandResponse =
                ModifyThingResponse.created(TestConstants.Thing.THING, TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = commandResponse.toJson(FieldType.regularOrSpecial());

        final CommandResponse parsedCommandResponse =
                commandResponseRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedCommandResponse).isEqualTo(commandResponse);
    }


    @Test
    public void parseThingQueryCommandResponse() {
        final ThingCommandResponseRegistry commandResponseRegistry = ThingCommandResponseRegistry.newInstance();

        final RetrieveThingResponse commandResponse =
                RetrieveThingResponse.of(TestConstants.Thing.THING_ID, TestConstants.Thing.THING,
                        TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = commandResponse.toJson(FieldType.regularOrSpecial());

        final CommandResponse parsedCommandResponse =
                commandResponseRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedCommandResponse).isEqualTo(commandResponse);
    }

}
