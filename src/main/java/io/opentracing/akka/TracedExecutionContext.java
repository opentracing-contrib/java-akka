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
package io.opentracing.akka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public final class TracedExecutionContext implements ExecutionContextExecutor {
    final ExecutionContext ec;
    final Tracer tracer;
    final boolean createSpans;

    public TracedExecutionContext(ExecutionContext ec) {
        this(ec, GlobalTracer.get(), false);
    }

    public TracedExecutionContext(ExecutionContext ec, Tracer tracer) {
        this(ec, tracer, false);
    }

    public TracedExecutionContext(ExecutionContext ec, Tracer tracer, boolean createSpans) {
        if (ec == null)
            throw new IllegalArgumentException("ec");
        if (tracer == null)
            throw new IllegalArgumentException("tracer");

        this.ec = ec;
        this.tracer = tracer;
        this.createSpans = createSpans;
    }

    @Override
    public ExecutionContext prepare() {
        if (!createSpans && tracer.scopeManager().active() == null)
            return ec; // Nothing to propagate/do.

        return new TracedExecutionContextImpl();
    }

    @Override
    public void execute(Runnable runnable) {
        ec.execute(runnable);
    }

    @Override
    public void reportFailure(Throwable cause) {
        ec.reportFailure(cause);
    }

    class TracedExecutionContextImpl implements ExecutionContextExecutor {
        Span activeSpan;

        public TracedExecutionContextImpl() {
            if (createSpans)
                activeSpan = tracer.buildSpan(Constants.EXECUTE_OPERATION_NAME).startManual();
            else
                activeSpan = tracer.scopeManager().active().span();
        }

        @Override
        public void execute(Runnable runnable) {
            ec.execute(new Runnable() {
                @Override
                public void run() {
                    // Only deactivate the active Span if we created/own it.
                    boolean deactivate = createSpans;

                    try (Scope scope = tracer.scopeManager().activate(activeSpan, deactivate)) {
                        runnable.run();
                    }
                }
            });
        }

        @Override
        public void reportFailure(Throwable cause) {
            ec.reportFailure(cause);
        }
    }
}
