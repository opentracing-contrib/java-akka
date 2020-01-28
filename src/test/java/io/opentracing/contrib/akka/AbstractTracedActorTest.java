/*
 * Copyright 2017-2020 The OpenTracing Authors
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
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.GlobalTracerTestUtil;
import io.opentracing.util.ThreadLocalScopeManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

public abstract class AbstractTracedActorTest {

  private final MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager());
  private ActorSystem system;

  abstract Props tracedCheckActorProps();

  abstract Props tracedCheckActorProps(Tracer tracer);

  abstract Props spanNullTracedActorProps();

  abstract Props spanCheckTracedActorProps();

  @Before
  public void before() {
    GlobalTracerTestUtil.resetGlobalTracer(); // safe start, just in case.

    mockTracer.reset();
    GlobalTracer.registerIfAbsent(mockTracer);

    system = ActorSystem.create("testSystem");
  }

  @After
  public void after() throws Exception {
    GlobalTracerTestUtil.resetGlobalTracer(); // clean up.

    Await.result(system.terminate(), getDefaultDuration());
  }

  @Test
  public void testNoActiveSpan() throws Exception {
    ActorRef actorRef = system.actorOf(spanNullTracedActorProps(), "one");
    Timeout timeout = new Timeout(getDefaultDuration());
    Future<Object> future = ask(actorRef, TracedMessage.wrap("foo"), timeout);

    Boolean isSpanNull = (Boolean) Await.result(future, getDefaultDuration());
    assertTrue(isSpanNull);
  }

  @Test
  public void testActiveSpan() throws Exception {
    ActorRef actorRef = system.actorOf(spanCheckTracedActorProps(), "actorOne");
    Timeout timeout = new Timeout(getDefaultDuration());

    Future<Object> future;
    final MockSpan parent = mockTracer.buildSpan("one").start();
    try (Scope ignored = mockTracer.activateSpan(parent)) {
      Object message = TracedMessage.wrap(mockTracer.activeSpan() /* message */);
      future = ask(actorRef, message, timeout);
    }
    parent.finish();

    Boolean isSpanSame = (Boolean) Await.result(future, getDefaultDuration());
    assertTrue(isSpanSame);
  }

  @Test
  public void testNoWrapMessage() throws Exception {
    ActorRef actorRef = system.actorOf(spanCheckTracedActorProps(), "actorOne");
    Timeout timeout = new Timeout(getDefaultDuration());

    Future<Object> future;
    final MockSpan parent = mockTracer.buildSpan("one").start();
    try (Scope ignored = mockTracer.activateSpan(parent)) {
      /* Since scope.span() is not TracedMessage, the active Span
       * won't be propagated */
      future = ask(actorRef, mockTracer.activeSpan(), timeout);
    }
    parent.finish();

    Boolean isSpanSame = (Boolean) Await.result(future, getDefaultDuration());
    assertFalse(isSpanSame);
  }

  @Test
  public void testExplicitTracer() throws Exception {
    ActorRef actorRef = system.actorOf(tracedCheckActorProps(mockTracer), "one");
    Timeout timeout = new Timeout(getDefaultDuration());

    Future<Object> future;
    final MockSpan parent = mockTracer.buildSpan("one").start();
    try (Scope ignored = mockTracer.activateSpan(parent)) {
      future = ask(actorRef, mockTracer, timeout);
    }
    parent.finish();

    Boolean isTracerSame = (Boolean) Await.result(future, getDefaultDuration());
    assertTrue(isTracerSame);
  }

  @Test
  public void testGlobalTracer() throws Exception {
    ActorRef actorRef = system.actorOf(tracedCheckActorProps(), "one");
    Timeout timeout = new Timeout(getDefaultDuration());

    Future<Object> future;
    final MockSpan parent = mockTracer.buildSpan("one").start();
    try (Scope ignored = mockTracer.activateSpan(parent)) {
      future = ask(actorRef, GlobalTracer.get(), timeout);
    }
    parent.finish();

    Boolean isTracerSame = (Boolean) Await.result(future, getDefaultDuration());
    assertTrue(isTracerSame);
  }

  private static FiniteDuration getDefaultDuration() {
    return Duration.create(3, "seconds");
  }
}
