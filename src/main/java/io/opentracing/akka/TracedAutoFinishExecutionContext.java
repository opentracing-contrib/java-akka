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
