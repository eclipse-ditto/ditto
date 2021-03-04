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
package org.eclipse.ditto.json.cbor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.IOContext;

public final class JacksonSerializationContextIT {

    private boolean somethingWrittenDuringCreateWithOutputStreamWritesToStream = false;
    private boolean generatorCalledDuringJsonGeneratorIsCreatedByFactory = false;

    @Test
    public void jsonGeneratorIsCreatedByFactory() throws IOException {
        final JsonGenerator markedObject = new com.fasterxml.jackson.core.JsonFactory().createGenerator(System.out);
        final com.fasterxml.jackson.core.JsonFactory jacksonFactory = new com.fasterxml.jackson.core.JsonFactory() {
            @Override
            protected JsonGenerator _createGenerator(final Writer out, final IOContext ctxt) {
                return recordAndReturn();
            }

            @Override
            protected JsonGenerator _createUTF8Generator(final OutputStream out, final IOContext ctxt) {
                return recordAndReturn();
            }

            private JsonGenerator recordAndReturn() {
                generatorCalledDuringJsonGeneratorIsCreatedByFactory = true;
                return markedObject;
            }
        };
        final JacksonSerializationContext serializationContext =
                new JacksonSerializationContext(jacksonFactory, ByteBuffer.allocate(15));
        final JsonGenerator returnedGenerator = serializationContext.getJacksonGenerator();
        assertThat(generatorCalledDuringJsonGeneratorIsCreatedByFactory).isTrue();
        assertThat(returnedGenerator == markedObject).isTrue(); // deliberate reference comparison
    }

    @Test
    public void createdWithTargetBufferWritesToBuffer() throws IOException {
        final com.fasterxml.jackson.core.JsonFactory jacksonFactory = new JsonFactory();
        final ByteBuffer targetBuffer = ByteBuffer.allocate(20);
        final JacksonSerializationContext serializationContext =
                new JacksonSerializationContext(jacksonFactory, targetBuffer);
        writeSomething(serializationContext);

        assertThat(targetBuffer.position()).isGreaterThan(5); // something has been written to it.
    }

    @Test
    public void createdWithOutputStreamWritersToStream() throws IOException {
        final com.fasterxml.jackson.core.JsonFactory jacksonFactory = new com.fasterxml.jackson.core.JsonFactory();

        final OutputStream outputStream = new OutputStream() {
            @Override
            public void write(final int b) {
                somethingWrittenDuringCreateWithOutputStreamWritesToStream = true;
            }
        };
        final JacksonSerializationContext serializationContext =
                new JacksonSerializationContext(jacksonFactory, outputStream);
        writeSomething(serializationContext);

        assertThat(somethingWrittenDuringCreateWithOutputStreamWritesToStream).isTrue();
    }

    private static void writeSomething(final JacksonSerializationContext serializationContext) throws IOException {
        serializationContext.getJacksonGenerator().writeStartObject();
        serializationContext.getJacksonGenerator().writeBooleanField("key", false);
        serializationContext.getJacksonGenerator().writeEndObject();
        serializationContext.close();
    }
}
