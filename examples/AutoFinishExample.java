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
import io.opentracing.util.AutoFinishScopeManager;
import io.opentracing.akka.TracedAutoFinishExecutionContext;

import static akka.dispatch.Futures.future;

public class AutoFinishExample {
    public static void main(String [] args) {
        Tracer tracer = null; // Set your tracer here, and make sure AutoFinishScopeManager is used.
        ExecutionContext ec = new TracedAutoFinishExecutionContext(ExecutionContext.global(), tracer);

        // Span will be propagated and finished when all the 3 tasks are done.
        try (Scope scope = tracer.buildSpan("one").startActive()) {
            future(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    tracer.scopeManager().active().span().setTag("server1.result", 7);
                    return Boolean.TRUE;
                }
            }, ec);

            future(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    future(new Callable<Boolean>() {
                        @Override
                        public Boolean call() {
                            tracer.scopeManager().active().span().setTag("server3.result", 54);
                            return Boolean.TRUE;
                        }
                    }, ec);

                    tracer.scopeManager().active().span().setTag("server2.result", 37);
                    return Boolean.TRUE;
                }
            }, ec);
        }
    }
}
