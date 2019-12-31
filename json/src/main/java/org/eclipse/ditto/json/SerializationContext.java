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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

/**
 * A class that bundles State and Configuration for serialization.
 * It must be recreated for each serialization target.
 */
public class SerializationContext implements Closeable, Flushable {

    private JsonGenerator jacksonGenerator;
    private ControllableOutputStream outputStream;

    /**
     * Creates a Serialization context that writes to the designated target.
     * @param jacksonFactory The JsonFactory (from Jackson) to use during serialization. It defines among other things the Format to use.
     * @param outputStream The stream to write serialized data to. The stream is considered to be borrowed and will not be closed.
     */
    public SerializationContext(JsonFactory jacksonFactory, OutputStream outputStream) throws IOException {
        this.outputStream = new ControllableOutputStream(outputStream);
        jacksonGenerator = jacksonFactory.createGenerator(this.outputStream);
    }

    /**
     * Creates a Serialization context that writes to the designated target.
     * @param jacksonFactory The JsonFactory (from Jackson) to use during serialization. It defines among other things the Format to use.
     * @param targetBuffer The Buffer to write serialized data to. The Buffer is considered to be borrowed and will not be closed.
     */
    public SerializationContext(JsonFactory jacksonFactory, ByteBuffer targetBuffer) throws IOException {
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

    /**
     * Allows the caller to directly embed cached data in the Buffer.
     * This can only be used to write exactly one element.
     * @param cachedData The data to write in an appropriately sized array.
     */
    void writeCachedElement(byte[] cachedData) throws IOException {
        flush();
        outputStream.write(cachedData);
        informJacksonThatOneElementWasWritten();
    }

    private void informJacksonThatOneElementWasWritten() throws IOException {
        // Deactivating the output stream to write a pseudo element and ensure that the internal counter keeping track of array and object lengths is accurate.
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

        ControllableOutputStream(OutputStream target){
            this.target = target;
        }

        void enable(){
            this.enabled = true;
        }

        void disable(){
            this.enabled = false;
        }

        @Override
        public void write(final int b) throws IOException {
            if (enabled) target.write(b);
        }

        @Override
        public void write(final byte[] a, int b, int c) throws IOException {
            if (enabled) target.write(a,b,c);
        }

        @Override
        public void write(byte[] b) throws IOException {
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
