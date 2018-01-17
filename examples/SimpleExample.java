package io.opentracing.akka.examples;

import akka.dispatch.OnComplete;
import java.util.concurrent.Callable;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import io.opentracing.Tracer;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.akka.TracedExecutionContext;

import static akka.dispatch.Futures.future;

public class SimpleExample {
    public static void main(String [] args) {
        Tracer tracer = null; // Set your tracer here.
        ExecutionContext ec = new TracedExecutionContext(ExecutionContext.global(), tracer);

        try (Scope scope = tracer.buildSpan("parent").startActive(false)) {
            future(new Callable<Integer>() {
                @Override
                public Integer call() {
                    int statusCode = 201;
                    Scope scope = tracer.scopeManager().active();
                    scope.span().setTag("status.code", statusCode);
                    return new Integer(statusCode);
                }
            }, ec)
            .onComplete(new OnComplete<Integer>() {
                @Override
                public void onComplete(Throwable failure, Integer result) {
                    tracer.scopeManager().active().span().finish();
                }
            }, ec);
        }
    }
}
