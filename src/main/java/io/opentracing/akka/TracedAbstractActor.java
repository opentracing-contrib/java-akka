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

import akka.actor.AbstractActor;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

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
