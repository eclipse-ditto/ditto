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
package org.eclipse.ditto.base.model.signals.commands.assertions;

import java.util.Comparator;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;

/**
 * An Assert for {@link org.eclipse.ditto.base.model.signals.commands.CommandResponse}s.
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
