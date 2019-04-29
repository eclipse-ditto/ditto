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

 public abstract class AbstractAnnotationBasedJsonParsableFactory<T, A extends Annotation> {

     AnnotationBasedJsonParsable<T> fromAnnotation(final A annotation, final Class<? extends T> classToParse) {
         final String methodName = getMethodNameFor(annotation);
         final String key = getKeyFor(annotation);
         final String v1FallbackKey = getV1FallbackKeyFor(annotation);

         return new AnnotationBasedJsonParsable<>(key, v1FallbackKey, classToParse, methodName);
     }

     protected abstract String getV1FallbackKeyFor(A annotation);

     protected abstract String getKeyFor(A annotation);

     protected abstract String getMethodNameFor(A annotation);
 }
