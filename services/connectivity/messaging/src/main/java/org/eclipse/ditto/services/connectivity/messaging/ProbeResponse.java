 /*
  * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
 package org.eclipse.ditto.services.connectivity.messaging;

 import org.eclipse.ditto.model.base.common.HttpStatus;
 import org.eclipse.ditto.model.base.headers.DittoHeaders;
 import org.eclipse.ditto.model.base.headers.DittoHeadersSettable;
 import org.eclipse.ditto.signals.commands.base.WithHttpStatus;

 /**
  * The result of a published message, which is only purpose is internal diagnostics/connection-metrics.
  */
 public final class ProbeResponse implements WithHttpStatus, DittoHeadersSettable<ProbeResponse> {

     private final HttpStatus responseStatus;
     private final DittoHeaders dittoHeaders;

     public ProbeResponse(final HttpStatus responseStatus, final DittoHeaders dittoHeaders) {
         this.responseStatus = responseStatus;
         this.dittoHeaders = dittoHeaders;
     }

     @Override
     public ProbeResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
         return new ProbeResponse(responseStatus, dittoHeaders);
     }

     @Override
     public HttpStatus getHttpStatus() {
         return responseStatus;
     }

     @Override
     public DittoHeaders getDittoHeaders() {
         return dittoHeaders;
     }

 }
