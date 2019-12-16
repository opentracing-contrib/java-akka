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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.GlobalTracerTestUtil;
import io.opentracing.util.ThreadLocalScopeManager;
import org.junit.Before;
import org.junit.Test;

public class TracedMessageTest {

  private final MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager());

  @Before
  public void before() {
    mockTracer.reset();
    GlobalTracer.registerIfAbsent(mockTracer);
  }

  @Before
  public void after() {
    GlobalTracerTestUtil.resetGlobalTracer();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNullMessage() {
    TracedMessage.wrap(null);
  }

  @Test
  public void testExplicitNoActiveSpan() {
    String originalMessage = "foo";
    Object message = TracedMessage.wrap(null, originalMessage);
    assertEquals(message, originalMessage);
  }

  @Test
  public void testImplicitNoActiveSpan() {
    String originalMessage = "foo";
    Object message = TracedMessage.wrap(originalMessage);
    assertEquals(message, originalMessage);
  }

  @Test
  public void testImplicitActiveSpan() {
    String originalMessage = "foo";
    Object message;
    Span span = mockTracer.buildSpan("one").start();

    try (Scope ignored = mockTracer.scopeManager().activate(span)) {
      message = TracedMessage.wrap(originalMessage);
    }
    assertTrue(message instanceof TracedMessage);

    TracedMessage tracedMessage = (TracedMessage) message;
    assertEquals(span, tracedMessage.activeSpan());
    assertEquals(originalMessage, tracedMessage.message());
  }

  @Test
  public void testExplicitActiveSpan() {
    String originalMessage = "foo";
    Span span = mockTracer.buildSpan("one").start();

    Object message = TracedMessage.wrap(span, originalMessage);
    assertTrue(message instanceof TracedMessage);

    TracedMessage tracedMessage = (TracedMessage) message;
    assertEquals(span, tracedMessage.activeSpan());
    assertEquals(originalMessage, tracedMessage.message());
  }
}
