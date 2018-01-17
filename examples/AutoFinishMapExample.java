package io.opentracing.akka.examples;

import java.util.concurrent.Callable;
import akka.dispatch.OnComplete;
import akka.dispatch.Mapper;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import io.opentracing.Tracer;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.AutoFinishScopeManager;
import io.opentracing.akka.TracedAutoFinishExecutionContext;

import static akka.dispatch.Futures.future;

public class AutoFinishingMapExampleTest {
    public static void main(String [] args) {
        Tracer tracer = null; // Set your tracer here, and make sure AutoFinishScopeManager is used.
        ExecutionContext ec = new TracedAutoFinishExecutionContext(ExecutionContext.global(), tracer);

        // The Span will be finished once the last callback is done - that is,
        // the OnComplete invocation, thus no need to manually finish it.
        try (Scope scope = tracer.buildSpan("one").startActive()) {
            future(new Callable<Integer>() {
                @Override
                public Integer call() {
                    int result = 127;
                    tracer.scopeManager().active().span().setTag("original.value", 127);
                    return result;
                }
            }, ec)
            .map(new Mapper<Integer, String>() {
                @Override
                public String apply(Integer n) {
                    String result = n.toString();
                    tracer.scopeManager().active().span().setTag("final.value", result);
                    return result;
                }
            }, ec)
            .onComplete(new OnComplete<String>() {
                @Override
                public void onComplete(Throwable failure, String result) {
                    tracer.scopeManager().active().span().setTag("error", Boolean.FALSE);
                }
            }, ec);
        }
    }
}
