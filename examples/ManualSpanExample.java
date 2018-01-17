package io.opentracing.akka.examples;

import akka.dispatch.OnComplete;
import java.util.Arrays;
import java.util.concurrent.Callable;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import io.opentracing.Tracer;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.akka.TracedExecutionContext;

import static akka.dispatch.Futures.future;
import static akka.dispatch.Futures.sequence;

public class SimpleExampleTest {
    public static void main(String [] args) {
        Tracer tracer = null; // Set your tracer here.
        ExecutionContext ec = new TracedExecutionContext(ExecutionContext.global(), tracer);

        try (Scope scope = tracer.scopeManager().activate(span, false)) {
            Future f1 = future(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    // 'parent' will automatically propagated and set active.
                    try (Scope childScope = tracer.buildSpan("child1").startActive()) {
                    }
                    return Boolean.TRUE;
                }
            }, ec);

            Future f2 = future(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    // 'parent' will automatically propagated and set active.
                    try (Scope childScope = tracer.buildSpan("child2").startActive()) {
                    }
                    return Boolean.TRUE;
                }
            }, ec);

            sequence(Arrays.asList(f1, f2), ec).onComplete(new OnComplete<Object>() {
                @Override
                public void onComplete(Throwable failure, Object result) {
                    // Finish the parent Span once the children were completed.
                    scope.span().finish();
                }
            }, ec);
        }
    }
}
