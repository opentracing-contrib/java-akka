package io.opentracing.akka;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

public final class TracedMessage<T> {
    private T message;
    private Span activeSpan;

    private TracedMessage(T message, Span activeSpan) {
        this.message = message;
        this.activeSpan = activeSpan;
    }

    public static Object wrap(Object message) {
        return wrap(GlobalTracer.get().activeSpan(), message);
    }

    public static <T> Object wrap(Span activeSpan, T message) {
        if (message == null)
            throw new IllegalArgumentException("message cannot be null");

        if (activeSpan == null)
            return message;

        return new TracedMessage<T>(message, activeSpan);
    }

    public Span activeSpan() {
        return activeSpan;
    }

    public T message() {
        return message;
    }
}
