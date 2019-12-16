/*
 * Copyright 2017-2019 The OpenTracing Authors
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

import akka.actor.Actor;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import java.util.function.BiConsumer;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

public interface DistributedTracedActor extends Actor {
  class Utils {
    private Utils() {
    }

    public static void aroundReceive(
        BiConsumer<PartialFunction<Object, BoxedUnit>, Object> superConsumer, Tracer tracer,
        PartialFunction<Object, BoxedUnit> receive, Object message) {
      if (!(message instanceof DistributedTracedMessage)) {
        superConsumer.accept(receive, message);
        return;
      }

      final DistributedTracedMessage<?> tracedMessage = (DistributedTracedMessage<?>) message;
      final Span span = tracedMessage.activeSpan(tracer);
      final Object originalMessage = tracedMessage.message();

      try (Scope ignored = tracer.scopeManager().activate(span)) {
        superConsumer.accept(receive, originalMessage);
      }
    }
  }
}
