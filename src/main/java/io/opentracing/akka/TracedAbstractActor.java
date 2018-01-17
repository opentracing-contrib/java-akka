package io.opentracing.akka;

import akka.actor.AbstractActor;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

public abstract class TracedAbstractActor extends AbstractActor {
    Tracer tracer;

    public TracedAbstractActor() {
        this(GlobalTracer.get());
    }

    public TracedAbstractActor(Tracer tracer) {
        this.tracer = tracer;
    }

    protected Tracer tracer() {
        return tracer;
    }

    @Override
    public void aroundReceive(PartialFunction<Object, BoxedUnit> receive, Object message) {
        if (!(message instanceof TracedMessage)) {
            super.aroundReceive(receive, message);
            return;
        }

        TracedMessage tracedMessage = (TracedMessage)message;
        Span span = tracedMessage.activeSpan();
        Object originalMessage = tracedMessage.message();

        try (Scope scope = tracer.scopeManager().activate(span, false)) {
            super.aroundReceive(receive, originalMessage);
        }
    }
}
