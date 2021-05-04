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
package org.eclipse.ditto.connectivity.service.mapping.javascript;

import java.util.function.Function;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.MessageMappingFailedException;
import org.mozilla.javascript.RhinoException;

/**
 * Mapping interface that also provides common methods.
 *
 * @param <I> input data type
 * @param <O> output data type
 */
public interface MappingFunction<I, O> extends Function<I, O> {

    /**
     * Build {@link MessageMappingFailedException} from a {@link RhinoException}.
     * @param e the original exception thrown by the rhino engine
     * @param contentType the content type of the message which could not be mapped
     * @param dittoHeaders the {@link DittoHeaders} of the original message
     * @return a {@link MessageMappingFailedException} containing information about the javascript error that occurred
     */
    default MessageMappingFailedException buildMessageMappingFailedException(final RhinoException e,
            final String contentType, final DittoHeaders dittoHeaders) {
        final boolean sourceExists = e.lineSource() != null && !e.lineSource().isEmpty();
        final String lineSource = sourceExists ? (", source:\n" + e.lineSource()) : "";
        final boolean stackExists = e.getScriptStackTrace() != null && !e.getScriptStackTrace().isEmpty();
        final String scriptStackTrace = stackExists ? (", stack:\n" + e.getScriptStackTrace()) : "";
        return MessageMappingFailedException.newBuilder(contentType)
                .description(e.getMessage() + " - in line/column #" + e.lineNumber() + "/" + e.columnNumber() +
                        lineSource + scriptStackTrace)
                .dittoHeaders(dittoHeaders)
                .cause(e)
                .build();
    }
}
