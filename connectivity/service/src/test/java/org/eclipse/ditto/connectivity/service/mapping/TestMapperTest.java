package org.eclipse.ditto.connectivity.service.mapping;
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
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
public class TestMapperTest {

    private static final ThingId THING_ID = ThingId.of("thing:id");
    private static final ProtocolAdapter ADAPTER = DittoProtocolAdapter.newInstance();

    String payload = "{\"specversion\": \"1.0\", \"id\":\"3212e\", \"source\":\"http:somesite.com\",\"type\":\"com.site.com\"}";
    private TestMapper underTest;

    @Before
    public void setUp() {
        underTest = new TestMapper();
    }

    @Test
    public void validatePayload(){
        Boolean expected = true;
        Boolean actual = underTest.validatePayload(payload);
        assertEquals(expected,actual);
    }
}
