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
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.messages.MessageDirection.FROM;
import static org.eclipse.ditto.model.messages.MessageDirection.TO;

import org.assertj.core.api.OptionalAssert;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.junit.Test;

/**
 * Unit tests for {@link MessagePath}.
 */
public class MessagePathTest {

    @Test
    public void parseDirection() {
        assertDirection("/outbox/message/ask").contains(FROM);
        assertDirection("/attributes/hello").isEmpty();
        assertDirection("/features/water-tank/properties/temperature").isEmpty();
        assertDirection("/features/water-tank/inbox/messages/heatUp").contains(TO);
    }

    @Test
    public void parseFeatureId() {
        assertFeatureId("/outbox/message/ask").isEmpty();
        assertFeatureId("/attributes/hello").isEmpty();
        assertFeatureId("/features/water-tank/properties/temperature").contains("water-tank");
        assertFeatureId("features/water-tank/inbox/messages/heatUp").contains("water-tank");
    }

    private static OptionalAssert<MessageDirection> assertDirection(final String jsonPointer) {
        return assertThat(new MessagePath(JsonPointer.of(jsonPointer)).getDirection());
    }

    private static OptionalAssert<String> assertFeatureId(final String jsonPointer) {
        return assertThat(new MessagePath(JsonPointer.of(jsonPointer)).getFeatureId());
    }
}
