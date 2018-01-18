/*
 * Copyright 2017-2018 The OpenTracing Authors
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
package io.opentracing.akka.examples;

import java.util.concurrent.Callable;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import io.opentracing.Tracer;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.akka.TracedExecutionContext;

import static akka.dispatch.Futures.future;

public class NestedExample {
    public static void main(String [] args) {
        Tracer tracer = null; // Set your tracer here.
        ExecutionContext ec = new TracedExecutionContext(ExecutionContext.global(), tracer);

        // Start a Span and manually finish it when the last Future/task is done.
        try (Scope scope = tracer.buildSpan("parent").startActive(false)) {
            future(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    future(new Callable<Integer>() {
                        @Override
                        public Integer call() {
                            tracer.scopeManager().active().span().setTag("step2.value", 17);
                            tracer.scopeManager().active().span().finish();
                            return new Integer(201);
                        }
                    }, ec);

                    tracer.scopeManager().active().span().setTag("step1.value", 10);
                    return Boolean.TRUE;
                }
            }, ec);
        }
    }
}
