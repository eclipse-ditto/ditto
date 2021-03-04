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
package com.eclipsesource.json;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Stack;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * This implementation of {@link JsonWriter} differs from its parent class in writing empty JSON objects as {@code "{}"}
 * and empty JSON arrays as {@code "[]"}.
 */
@NotThreadSafe
public final class PrettyPrintEmptyElementsWriter extends JsonWriter {

    private final char[] indentChars;
    private final Stack<AbstractJsonWriter> jsonWriterStack;
    private int indent;

    private PrettyPrintEmptyElementsWriter(final Writer writer, final char[] theIndentChars) {
        super(writer);
        indentChars = theIndentChars;
        jsonWriterStack = new Stack<>();
        jsonWriterStack.push(new GeneralJsonWriter(writer));
        indent = 0;
    }

    /**
     * Returns a WriterConfig which creates a {@code PrettyPrintEmptyElementsWriter} which uses the specified
     * number of spaces for indentation.
     *
     * @param number the number of spaces to be used for indentation.
     * @return the WriterConfig.
     */
    public static WriterConfig indentWithSpaces(final int number) {
        return new WriterConfig() {
            @Override
            JsonWriter createWriter(final Writer writer) {
                final char[] chars = new char[number];
                Arrays.fill(chars, ' ');

                return new PrettyPrintEmptyElementsWriter(writer, chars);
            }
        };
    }

    @Override
    protected void writeArrayOpen() throws IOException {
        onCurrentWriter().writeArrayOpen();
    }

    @Override
    protected void writeArrayClose() throws IOException {
        onCurrentWriter().writeArrayClose();
    }

    @Override
    protected void writeArraySeparator() throws IOException {
        onCurrentWriter().writeArraySeparator();
    }

    @Override
    protected void writeObjectOpen() throws IOException {
        onCurrentWriter().writeObjectOpen();
    }

    @Override
    protected void writeObjectClose() throws IOException {
        onCurrentWriter().writeObjectClose();
    }

    @Override
    protected void writeMemberName(final String name) throws IOException {
        onCurrentWriter().writeMemberName(name);
    }

    @Override
    protected void writeString(final String string) throws IOException {
        onCurrentWriter().writeString(string);
    }

    @Override
    protected void writeJsonString(final String string) throws IOException {
        onCurrentWriter().writeJsonString(string);
    }

    @Override
    protected void writeNumber(final String string) throws IOException {
        onCurrentWriter().writeNumber(string);
    }

    @Override
    protected void writeLiteral(final String value) throws IOException {
        onCurrentWriter().writeLiteral(value);
    }

    @Override
    protected void writeMemberSeparator() throws IOException {
        onCurrentWriter().writeMemberSeparator();
    }

    @Override
    protected void writeObjectSeparator() throws IOException {
        onCurrentWriter().writeObjectSeparator();
    }

    private AbstractJsonWriter onCurrentWriter() {
        return jsonWriterStack.peek();
    }

    /**
     * This implementation of {@code JsonWriter} provides common implementation which can be used by its sub-classes.
     */
    @SuppressWarnings("AbstractClassExtendsConcreteClass")
    @NotThreadSafe
    private abstract class AbstractJsonWriter extends JsonWriter {

        /**
         * Constructs a new {@code AbstractWriter} object.
         *
         * @param writer the writer to be used for actual writing.
         */
        protected AbstractJsonWriter(final Writer writer) {
            super(writer);
        }

        protected void writeNewLine() throws IOException {
            if (null == indentChars) {
                return;
            }
            writer.write('\n');
            for (int i = 0; i < indent; i++) {
                writer.write(indentChars);
            }
        }

    }

    /**
     * Default implementation of {@code AbstractWriter}. Its purpose is to use dedicated writers for JSON objects and
     * JSON arrays.
     */
    @NotThreadSafe
    private final class GeneralJsonWriter extends AbstractJsonWriter {

        /**
         * Constructs a new {@code GeneralJsonWriter} object.
         *
         * @param writer the writer to be used for actual writing.
         */
        GeneralJsonWriter(final Writer writer) {
            super(writer);
        }

        @Override
        protected void writeArrayOpen() throws IOException {
            new JsonArrayWriter(writer).writeArrayOpen();
        }

        @Override
        protected void writeObjectOpen() throws IOException {
            new JsonObjectWriter(writer).writeObjectOpen();
        }

    }

    /**
     * A dedicated JsonWriter for writing JSON objects. Its purpose is to write empty objects as {@code "{}"}.
     */
    @NotThreadSafe
    private final class JsonObjectWriter extends AbstractJsonWriter {

        private boolean memberWritten;

        /**
         * Constructs a new {@code JsonObjectWriter} object.
         *
         * @param writer the writer to be used for actual writing.
         */
        JsonObjectWriter(final Writer writer) throws IOException {
            super(writer);
            memberWritten = false;
        }

        @Override
        protected void writeObjectOpen() throws IOException {
            indent++;
            writer.write('{');
            jsonWriterStack.push(new JsonObjectWriter(writer));
        }

        @Override
        protected void writeArrayOpen() throws IOException {
            new JsonArrayWriter(writer).writeArrayOpen();
        }

        @Override
        protected void writeMemberName(final String name) throws IOException {
            writeNewLine();
            super.writeMemberName(name);
            memberWritten = true;
        }

        @Override
        protected void writeMemberSeparator() throws IOException {
            writer.write(':');
            writer.write(' ');
        }

        @Override
        protected void writeObjectSeparator() throws IOException {
            writer.write(',');
        }

        @Override
        protected void writeObjectClose() throws IOException {
            indent--;
            if (memberWritten) {
                writeNewLine();
            }
            writer.write('}');
            jsonWriterStack.pop();
        }

    }

    /**
     * A dedicated JsonWriter for writing JSON arrays. Its purpose is to write empty arrays as {@code "[]"}.
     */
    @NotThreadSafe
    private final class JsonArrayWriter extends AbstractJsonWriter {

        private boolean valueWritten;

        /**
         * Constructs a new {@code JsonArrayWriter} object.
         *
         * @param writer the writer to be used for actual writing.
         */
        JsonArrayWriter(final Writer writer) {
            super(writer);
            valueWritten = false;
        }

        @Override
        protected void writeArrayOpen() throws IOException {
            indent++;
            writer.write('[');
            jsonWriterStack.push(new JsonArrayWriter(writer));
        }

        @Override
        protected void writeObjectOpen() throws IOException {
            new JsonObjectWriter(writer).writeObjectOpen();
        }

        @Override
        protected void writeString(final String string) throws IOException {
            if (!valueWritten) {
                writeNewLine();
            }
            super.writeString(string);
            valueWritten = true;
        }

        @Override
        protected void writeJsonString(final String string) throws IOException {
            super.writeJsonString(string);
            valueWritten = true;
        }

        @Override
        protected void writeLiteral(final String value) throws IOException {
            if (!valueWritten) {
                writeNewLine();
            }
            super.writeLiteral(value);
            valueWritten = true;
        }

        @Override
        protected void writeNumber(final String string) throws IOException {
            if (!valueWritten) {
                writeNewLine();
            }
            super.writeNumber(string);
            valueWritten = true;
        }

        @Override
        protected void writeArraySeparator() throws IOException {
            writer.write(',');
            writeNewLine();
        }

        @Override
        protected void writeArrayClose() throws IOException {
            indent--;
            if (valueWritten) {
                writeNewLine();
            }
            writer.write(']');
            jsonWriterStack.pop();
        }

    }

}
