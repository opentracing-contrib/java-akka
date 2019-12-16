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

import akka.actor.Props;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

public class TracedAbstractActorWithStashTest extends AbstractTracedActorTest {
  abstract static class TestActor extends TracedAbstractActorWithStash {

    public TestActor() {
      super(GlobalTracer.get());
    }

    public TestActor(Tracer tracer) {
      super(tracer);
    }
  }

  static class SpanNullCheckActor extends TestActor {

    public static Props props() {
      return Props.create(SpanNullCheckActor.class, SpanNullCheckActor::new);
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .matchAny(x -> getSender().tell(tracer().scopeManager().activeSpan() == null, getSelf()))
          .build();
    }
  }

  static class SpanCheckActor extends TestActor {

    public static Props props() {
      return Props.create(SpanCheckActor.class, SpanCheckActor::new);
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .matchAny(x -> {
            Span span = tracer().scopeManager().activeSpan();
            boolean isSameSpan = span != null && span.equals(x);
            getSender().tell(isSameSpan, getSelf());
          })
          .build();
    }
  }

  static class TracerCheckActor extends TestActor {

    public TracerCheckActor() {
      super();
    }

    public TracerCheckActor(Tracer tracer) {
      super(tracer);
    }

    public static Props props() {
      return Props.create(TracerCheckActor.class, TracerCheckActor::new);
    }

    public static Props props(Tracer tracer) {
      return Props.create(TracerCheckActor.class, () -> new TracerCheckActor(tracer));
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .matchAny(x -> getSender().tell(tracer() == x, getSelf()))
          .build();
    }
  }

  @Override
  Props tracedCheckActorProps() {
    return TracerCheckActor.props();
  }

  @Override
  Props tracedCheckActorProps(Tracer tracer) {
    return TracerCheckActor.props(tracer);
  }

  @Override
  Props spanNullTracedActorProps() {
    return SpanNullCheckActor.props();
  }

  @Override
  Props spanCheckTracedActorProps() {
    return SpanCheckActor.props();
  }

}
