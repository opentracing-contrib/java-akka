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

import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;

public final class TracedMessage<T> {

  private T message;
  private Span activeSpan;

  private TracedMessage(T message, Span activeSpan) {
    this.message = message;
    this.activeSpan = activeSpan;
  }

  public static Object wrap(Object message) {
    return wrap(GlobalTracer.get().activeSpan(), message);
  }

  public static <T> Object wrap(Span activeSpan, T message) {
    if (message == null) {
      throw new IllegalArgumentException("message cannot be null");
    }

    if (activeSpan == null) {
      return message;
    }

    return new TracedMessage<>(message, activeSpan);
  }

  public Span activeSpan() {
    return activeSpan;
  }

  public T message() {
    return message;
  }

  @Override
  public String toString() {
    return "TracedMessage{" +
        "message=" + message +
        '}';
  }
}
