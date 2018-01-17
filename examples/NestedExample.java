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
