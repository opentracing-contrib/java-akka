/*
 * Copyright 2017-2019 The OpenTracing Authors
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
package io.opentracing.contrib.akka;

import akka.actor.AbstractActor;
import akka.actor.AbstractActorWithStash;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

public abstract class TracedAbstractActorWithStash extends AbstractActorWithStash implements TracedActor {
    Tracer tracer;

    public TracedAbstractActorWithStash() {
        this(GlobalTracer.get());
    }

    public TracedAbstractActorWithStash(Tracer tracer) {
        this.tracer = tracer;
    }

    protected Tracer tracer() {
        return tracer;
    }

    @Override
    public void aroundReceive(PartialFunction<Object, BoxedUnit> receive, Object message) {
        Utils.aroundReceive(super::aroundReceive, tracer(), receive, message);
    }
}
