/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.json;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.IOContext;

public class SerializationContextIT {

    private boolean somethingWrittenDuringCreateWithOutputStreamWritesToStream = false;


    @Test
    public void jsonGeneratorIsCreatedByFactory() throws IOException {
        final Object markedObject = new Object();
        final JsonFactory jacksonFactory = new JsonFactory() {
            @Override
            protected JsonGenerator _createGenerator(final Writer out, final IOContext ctxt) {
                return (JsonGenerator) markedObject;
            }
        };
        final SerializationContext serializationContext =
                new SerializationContext(jacksonFactory, ByteBuffer.allocate(15));
        final JsonGenerator returnedGenerator = serializationContext.getJacksonGenerator();
        assertThat(returnedGenerator == markedObject).isTrue(); // deliberate reference comparison
    }

    @Test
    public void createdWithTargetBufferWritesToBuffer() throws IOException {
        final JsonFactory jacksonFactory = new JsonFactory();
        final ByteBuffer targetBuffer = ByteBuffer.allocate(20);
        final SerializationContext serializationContext = new SerializationContext(jacksonFactory, targetBuffer);
        writeSomething(serializationContext);

        assertThat(targetBuffer.position()).isGreaterThan(5); // something has been written to it.
    }

    @Test
    public void createdWithOutputStreamWritersToStream() throws IOException {
        final JsonFactory jacksonFactory = new JsonFactory();

        final OutputStream outputStream = new OutputStream() {
            @Override
            public void write(final int b) {
                somethingWrittenDuringCreateWithOutputStreamWritesToStream = true;
            }
        };
        final SerializationContext serializationContext = new SerializationContext(jacksonFactory, outputStream);
        writeSomething(serializationContext);

        assertThat(somethingWrittenDuringCreateWithOutputStreamWritesToStream).isTrue();
    }

    private static void writeSomething(SerializationContext serializationContext) throws IOException {
        serializationContext.getJacksonGenerator().writeStartObject();
        serializationContext.getJacksonGenerator().writeBooleanField("key", false);
        serializationContext.getJacksonGenerator().writeEndObject();
        serializationContext.close();
    }
}
