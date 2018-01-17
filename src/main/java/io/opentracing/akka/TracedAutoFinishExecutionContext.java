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
import io.opentracing.util.AutoFinishScope;
import io.opentracing.util.AutoFinishScopeManager;
import io.opentracing.util.GlobalTracer;

public final class TracedAutoFinishExecutionContext implements ExecutionContextExecutor {
    final ExecutionContext ec;
    final Tracer tracer;

    public TracedAutoFinishExecutionContext(ExecutionContext ec) {
        this(ec, GlobalTracer.get());
    }

    public TracedAutoFinishExecutionContext(ExecutionContext ec, Tracer tracer) {
        if (ec == null)
            throw new IllegalArgumentException("ec");
        if (tracer == null)
            throw new IllegalArgumentException("tracer");

        this.ec = ec;
        this.tracer = tracer;
    }

    @Override
    public ExecutionContext prepare() {
        if (tracer.scopeManager().active() == null)
            return ec; // Nothing to propagate/do.

        return new TracedAutoFinishExecutionContextImpl();
    }

    @Override
    public void execute(Runnable runnable) {
        ec.execute(runnable);
    }

    @Override
    public void reportFailure(Throwable cause) {
        ec.reportFailure(cause);
    }

    class TracedAutoFinishExecutionContextImpl implements ExecutionContextExecutor {
        AutoFinishScope.Continuation continuation;

        public TracedAutoFinishExecutionContextImpl() {
            Scope scope = tracer.scopeManager().active();
            if (!(scope instanceof AutoFinishScope))
                throw new IllegalStateException("Usage of AutoFinishScopeManager required.");

            continuation = ((AutoFinishScope)scope).capture();
        }

        @Override
        public void execute(Runnable runnable) {
            ec.execute(new Runnable() {
                @Override
                public void run() {
                    //try (Scope scope = tracer.scopeManager().activate(span, true)) {
                    try (Scope scope = continuation.activate()) {
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
