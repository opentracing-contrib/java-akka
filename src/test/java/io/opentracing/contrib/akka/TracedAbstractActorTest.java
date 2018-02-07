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
package io.opentracing.contrib.akka;

import static akka.pattern.Patterns.ask;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.util.Timeout;
import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.ThreadLocalScopeManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.Future;

public class TracedAbstractActorTest {

    final MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager());
    ActorSystem system;

    @Before
    public void before() {
        TestUtils.resetGlobalTracer(); // safe start, just in case.

        mockTracer.reset();
        GlobalTracer.register(mockTracer);

        system = ActorSystem.create("testSystem");
    }

    @After
    public void after() throws Exception {
        TestUtils.resetGlobalTracer(); // clean up.

        Await.result(system.terminate(), TestUtils.getDefaultDuration());
    }

    static abstract class TestActor extends TracedAbstractActor {

        public TestActor() {
            super(GlobalTracer.get());
        }

        public TestActor(Tracer tracer) {
            super(tracer);
        }
    }

    static class SpanNullCheckActor extends TestActor {

        public static Props props() {
            return Props.create(SpanNullCheckActor.class, () -> new SpanNullCheckActor());
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchAny(x -> {
                        getSender().tell(tracer().scopeManager().active() == null, getSelf());
                    })
                    .build();
        }
    }

    @Test
    public void testNoActiveSpan() throws Exception {
        ActorRef actorRef = system.actorOf(SpanNullCheckActor.props(), "one");
        Timeout timeout = new Timeout(TestUtils.getDefaultDuration());
        Future<Object> future = ask(actorRef, TracedMessage.wrap("foo"), timeout);

        Boolean isSpanNull = (Boolean) Await.result(future, TestUtils.getDefaultDuration());
        assertTrue(isSpanNull);
    }

    static class SpanCheckActor extends TestActor {

        public static Props props() {
            return Props.create(SpanCheckActor.class, () -> new SpanCheckActor());
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchAny(x -> {
                        Scope scope = tracer().scopeManager().active();
                        boolean isSameSpan = scope == null ? false : scope.span().equals(x);
                        getSender().tell(isSameSpan, getSelf());
                    })
                    .build();
        }
    }

    @Test
    public void testActiveSpan() throws Exception {
        ActorRef actorRef = system.actorOf(SpanCheckActor.props(), "actorOne");
        Timeout timeout = new Timeout(TestUtils.getDefaultDuration());

        Future<Object> future = null;
        try (Scope scope = mockTracer.buildSpan("one").startActive(true)) {
            Object message = TracedMessage.wrap(scope.span() /* message */);
            future = ask(actorRef, message, timeout);
        }

        Boolean isSpanSame = (Boolean) Await.result(future, TestUtils.getDefaultDuration());
        assertTrue(isSpanSame);
    }

    @Test
    public void testNoWrapMessage() throws Exception {
        ActorRef actorRef = system.actorOf(SpanCheckActor.props(), "actorOne");
        Timeout timeout = new Timeout(TestUtils.getDefaultDuration());

        Future<Object> future = null;
        try (Scope scope = mockTracer.buildSpan("one").startActive(true)) {
            /* Since scope.span() is not TracedMessage, the active Span
             * won't be propagated */
            future = ask(actorRef, scope.span(), timeout);
        }

        Boolean isSpanSame = (Boolean) Await.result(future, TestUtils.getDefaultDuration());
        assertFalse(isSpanSame);
    }

    static class TracerCheckActor extends TestActor {

        public TracerCheckActor() {
            super();
        }

        public TracerCheckActor(Tracer tracer) {
            super(tracer);
        }

        public static Props props() {
            return Props.create(TracerCheckActor.class, () -> new TracerCheckActor());
        }

        public static Props props(Tracer tracer) {
            return Props.create(TracerCheckActor.class, () -> new TracerCheckActor(tracer));
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchAny(x -> {
                        getSender().tell(tracer() == x, getSelf());
                    })
                    .build();
        }
    }

    @Test
    public void testExplicitTracer() throws Exception {
        ActorRef actorRef = system.actorOf(TracerCheckActor.props(mockTracer), "one");
        Timeout timeout = new Timeout(TestUtils.getDefaultDuration());

        Future<Object> future = null;
        try (Scope scope = mockTracer.buildSpan("one").startActive(true)) {
            future = ask(actorRef, mockTracer, timeout);
        }

        Boolean isTracerSame = (Boolean) Await.result(future, TestUtils.getDefaultDuration());
        assertTrue(isTracerSame);
    }

    @Test
    public void testGlobalTracer() throws Exception {
        ActorRef actorRef = system.actorOf(TracerCheckActor.props(), "one");
        Timeout timeout = new Timeout(TestUtils.getDefaultDuration());

        Future<Object> future = null;
        try (Scope scope = mockTracer.buildSpan("one").startActive(true)) {
            future = ask(actorRef, GlobalTracer.get(), timeout);
        }

        Boolean isTracerSame = (Boolean) Await.result(future, TestUtils.getDefaultDuration());
        assertTrue(isTracerSame);
    }
}
