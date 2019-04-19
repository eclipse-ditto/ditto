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
 import java.util.HashMap;
 import java.util.Map;

 import org.atteo.classindex.ClassIndex;

 public abstract class AbstractGlobalJsonParsableRegistry<T, A extends Annotation> {

     private final Map<String, JsonParsable<T>> parseStrategies;

     protected AbstractGlobalJsonParsableRegistry(final Class<T> parsedClass, final Class<A> annotationClass) {
         this.parseStrategies = initParseStrategies(parsedClass, annotationClass);
     }

     private Map<String, JsonParsable<T>> initParseStrategies(final Class<T> parsedClass,
             final Class<A> annotationClass) {
         final Map<String, JsonParsable<T>> parseRegistries = new HashMap<>();
         final Iterable<Class<?>> annotatedClasses = ClassIndex.getAnnotated(annotationClass);
         annotatedClasses.forEach(parsableException -> {
             final A fromJsonAnnotation = parsableException.getAnnotation(annotationClass);
             final String methodName = getMethodNameFor(fromJsonAnnotation);
             final String key = getKeyFor(fromJsonAnnotation);
             final String v1FallbackKey = getV1FallbackKeyFor(fromJsonAnnotation);

             final AnnotationBasedJsonParsable<T>
                     strategy = new AnnotationBasedJsonParsable<>(key, v1FallbackKey, parsedClass,
                     methodName);
             parseRegistries.put(strategy.getKey(), strategy);
             parseRegistries.put(strategy.getV1FallbackKey(), strategy);
         });
         return parseRegistries;
     }

     abstract protected String getV1FallbackKeyFor(A annotation);

     abstract protected String getKeyFor(A annotation);

     abstract protected String getMethodNameFor(A annotation);

     public Map<String, JsonParsable<T>> getParseStrategies() {
         return parseStrategies;
     }
 }
