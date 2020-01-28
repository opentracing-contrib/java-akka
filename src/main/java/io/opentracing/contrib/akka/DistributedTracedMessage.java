/*
 * Copyright 2017-2020 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.akka;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;

public final class DistributedTracedMessage<T> {

  private T message;
  private Map<String, String> headers;

  private DistributedTracedMessage(T message, Map<String, String> headers) {
    this.message = message;
    this.headers = headers;
  }

  public static Object wrap(Tracer tracer, Object message) {
    return wrap(tracer, tracer.activeSpan(), message);
  }

  public static Object wrap(Object message) {
    Tracer tracer = GlobalTracer.get();
    return wrap(tracer, tracer.activeSpan(), message);
  }

  public static <T> Object wrap(Span activeSpan, T message) {
    return wrap(GlobalTracer.get(), activeSpan, message);
  }

  public static <T> Object wrap(Tracer tracer, Span activeSpan, T message) {
    if (message == null) {
      throw new IllegalArgumentException("message cannot be null");
    }

    if (activeSpan == null) {
      return message;
    }

    final Map<String, String> headers = new HashMap<>();
    tracer.inject(activeSpan.context(), Format.Builtin.TEXT_MAP_INJECT, headers::put);
    return new DistributedTracedMessage<>(message, headers);
  }

  private SpanContext spanContext(Tracer tracer) {
    return tracer.extract(Format.Builtin.TEXT_MAP_EXTRACT, () -> headers.entrySet().iterator());
  }

  Span activeSpan() {
    return activeSpan(GlobalTracer.get());
  }

  Span activeSpan(final Tracer tracer) {
    Tracer.SpanBuilder spanBuilder = tracer.buildSpan("receive")
        .ignoreActiveSpan()
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER)
        .withTag(Tags.COMPONENT, "java-akka");

    final SpanContext context = spanContext(tracer);
    if (context != null) {
      spanBuilder = spanBuilder.addReference(References.FOLLOWS_FROM, context);
    }
    return spanBuilder.start();
  }

  public T message() {
    return message;
  }

  @Override
  public String toString() {
    return "DistributedTracedMessage{" +
            "message=" + message +
            '}';
  }
}
