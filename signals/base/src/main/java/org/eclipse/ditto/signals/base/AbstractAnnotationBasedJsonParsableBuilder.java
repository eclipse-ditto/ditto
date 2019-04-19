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
 package org.eclipse.ditto.signals.base;

 import java.lang.annotation.Annotation;

 public abstract class AbstractAnnotationBasedJsonParsableBuilder<T, A extends Annotation> {

     abstract protected String getV1FallbackKeyFor(A annotation);

     abstract protected String getKeyFor(A annotation);

     abstract protected String getMethodNameFor(A annotation);

     public AnnotationBasedJsonParsable<T> fromAnnotation(A annotation, Class<? extends T> classToParse) {
         final String methodName = getMethodNameFor(annotation);
         final String key = getKeyFor(annotation);
         final String v1FallbackKey = getV1FallbackKeyFor(annotation);

         return new AnnotationBasedJsonParsable<>(key, v1FallbackKey, classToParse, methodName);
     }
 }
