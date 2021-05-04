/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals.commands;

import java.util.function.Predicate;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.junit.Test;

/**
 * Unit tests for {@link AbstractCommand}.
 */
public final class AbstractCommandTest {

    @Test
    public void ensureThatQueryCommandWithResponseRequiredFalseThrowsException() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .responseRequired(false)
                .build();

        Assertions.assertThatExceptionOfType(CommandHeaderInvalidException.class).isThrownBy(() ->
                new DummyQueryCommand("type", dittoHeaders));
    }

    @Test
    public void ensureThatQueryCommandWithResponseRequiredTrueSucceeds() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .responseRequired(true)
                .build();

        Assertions.assertThat(new DummyQueryCommand("type", dittoHeaders)).isNotNull();
    }

    private final static class DummyQueryCommand extends AbstractCommand<DummyQueryCommand> {

        private DummyQueryCommand(final String type, final DittoHeaders dittoHeaders) {
            super(type, dittoHeaders);
        }

        @Override
        public JsonPointer getResourcePath() {
            return JsonPointer.empty();
        }

        @Override
        public String getResourceType() {
            return "resourceType";
        }

        @Override
        public String getTypePrefix() {
            return "foo";
        }

        @Override
        public Category getCategory() {
            return Category.QUERY;
        }

        @Override
        public DummyQueryCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }

        @Override
        protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
                final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
            // no-op
        }
    }
}
