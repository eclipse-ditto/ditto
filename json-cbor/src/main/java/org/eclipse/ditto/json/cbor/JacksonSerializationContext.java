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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.eclipse.ditto.json.SerializationContext;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

/**
 * Implementation of {@link SerializationContext} backed by Jackson's {@link JsonGenerator}.
 */
final class JacksonSerializationContext implements SerializationContext {

    private final JsonGenerator jacksonGenerator;
    private final ControllableOutputStream outputStream;

    /**
     * Creates a JacksonSerializationContext that writes to the designated target.
     *
     * @param jacksonFactory The JsonFactory (from Jackson) to use during serialization. It defines among other things
     * the Format to use.
     * @param outputStream The stream to write serialized data to. The stream is considered to be borrowed and will not
     * be closed.
     */
    public JacksonSerializationContext(final com.fasterxml.jackson.core.JsonFactory jacksonFactory,
            final OutputStream outputStream) throws IOException {
        this.outputStream = new ControllableOutputStream(outputStream);
        jacksonGenerator = jacksonFactory.createGenerator(this.outputStream);
    }

    JacksonSerializationContext(final OutputStream outputStream) throws IOException {
        this(new CBORFactory(), outputStream);
    }

    /**
     * Creates a JacksonSerializationContext that writes to the designated target.
     *
     * @param jacksonFactory The JsonFactory (from Jackson) to use during serialization. It defines among other things
     * the Format to use.
     * @param targetBuffer The Buffer to write serialized data to. The Buffer is considered to be borrowed and will not
     * be closed.
     */
    public JacksonSerializationContext(final com.fasterxml.jackson.core.JsonFactory jacksonFactory,
            final ByteBuffer targetBuffer) throws IOException {
        this(jacksonFactory, new ByteBufferOutputStream(targetBuffer));
    }

    JsonGenerator getJacksonGenerator() {
        return jacksonGenerator;
    }

    /**
     * Closes internal objects that need to be closed. Targets will be closed according to the promise during creation.
     */
    @Override
    public void close() throws IOException {
        jacksonGenerator.close();
    }

    /**
     * Flushes everything including targets.
     */
    @Override
    public void flush() throws IOException {
        jacksonGenerator.flush();
    }

    @Override
    public void writeCachedElement(final byte[] cachedData) throws IOException {
        flush();
        outputStream.write(cachedData);
        informJacksonThatOneElementWasWritten();
    }

    @Override
    public void writeNull() throws IOException {
        jacksonGenerator.writeNull();
    }

    @Override
    public void writeBoolean(final boolean state) throws IOException {
        jacksonGenerator.writeBoolean(state);
    }

    @Override
    public void writeNumber(final float number) throws IOException {
        jacksonGenerator.writeNumber(number);
    }

    @Override
    public void writeNumber(final double number) throws IOException {
        jacksonGenerator.writeNumber(number);
    }

    @Override
    public void writeNumber(final long number) throws IOException {
        jacksonGenerator.writeNumber(number);
    }

    @Override
    public void writeNumber(final int number) throws IOException {
        jacksonGenerator.writeNumber(number);
    }

    @Override
    public void writeString(final String text) throws IOException {
        jacksonGenerator.writeString(text);
    }

    @Override
    public void writeFieldName(final String name) throws IOException {
        jacksonGenerator.writeFieldName(name);
    }

    private void informJacksonThatOneElementWasWritten() throws IOException {
        // Deactivating the output stream to write a pseudo element and ensure that the internal counter keeping track
        // of array and object lengths is accurate.
        outputStream.disable();
        jacksonGenerator.writeNull();
        jacksonGenerator.flush();
        outputStream.enable();
    }

    /**
     * An output stream that can be switched to avoid writing unwanted data to the wrapped stream.
     */
    static final class ControllableOutputStream extends OutputStream {

        private final OutputStream target;
        private boolean enabled = true;

        ControllableOutputStream(final OutputStream target) {
            this.target = target;
        }

        void enable() {
            this.enabled = true;
        }

        void disable() {
            this.enabled = false;
        }

        @Override
        public void write(final int b) throws IOException {
            if (enabled) target.write(b);
        }

        @Override
        public void write(final byte[] a, int b, int c) throws IOException {
            if (enabled) target.write(a, b, c);
        }

        @Override
        public void write(final byte[] b) throws IOException {
            if (enabled) target.write(b);
        }

        @Override
        public void flush() throws IOException {
            target.flush();
        }

        @Override
        public void close() throws IOException {
            target.close();
        }
    }
}
