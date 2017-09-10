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
package org.eclipse.ditto.signals.commands.base.assertions;

import java.util.Comparator;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

/**
 * An Assert for {@link CommandResponse}s.
 */
public class CommandResponseAssert extends AbstractCommandResponseAssert<CommandResponseAssert, CommandResponse> {

    /**
     * Constructs a new {@code CommandResponseAssert} object.
     *
     * @param actual the command response to be checked.
     */
    public CommandResponseAssert(final CommandResponse actual) {
        super(actual, CommandResponseAssert.class);
    }

    public CommandResponseAssert isEqualTo(final CommandResponse<?> expectedCommandResponse) {
        Assertions.assertThat(actual)
                .usingComparatorForType(new JsonObjectComparator(), JsonObject.class)
                .isEqualToComparingFieldByFieldRecursively(expectedCommandResponse);
        return myself;
    }

    private static final class JsonObjectComparator implements Comparator<JsonObject> {

        @Override
        public int compare(final JsonObject o1, final JsonObject o2) {
            // Without this try/catch useful information is not logged but only the AssertionError of the below assert.
            try {
                DittoJsonAssertions.assertThat(o1).isEqualToIgnoringFieldDefinitions(o2);
            } catch (final AssertionError e) {
                System.err.println(e.getMessage());
                return -1;
            }
            return 0;
        }

    }

}
