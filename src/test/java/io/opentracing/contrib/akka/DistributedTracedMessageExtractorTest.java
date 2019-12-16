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
import static org.junit.Assert.assertNotEquals;

import akka.cluster.sharding.ShardRegion;
import io.opentracing.Span;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.GlobalTracerTestUtil;
import io.opentracing.util.ThreadLocalScopeManager;
import org.junit.Before;
import org.junit.Test;

public class DistributedTracedMessageExtractorTest {

  private final MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager());

  static class MessageExtractorAdapter implements ShardRegion.MessageExtractor {
    @Override
    public String entityId(Object message) {
      return message.getClass().toString();
    }

    @Override
    public Object entityMessage(Object message) {
      return message.getClass().toString();
    }

    @Override
    public String shardId(Object message) {
      return message.getClass().toString();
    }
  }

  @Before
  public void before() {
    mockTracer.reset();
    GlobalTracer.registerIfAbsent(mockTracer);
  }

  @Before
  public void after() {
    GlobalTracerTestUtil.resetGlobalTracer();
  }

  @Test
  public void testEntityIdWithoutDistributedTracedMessage() {
    MessageExtractorAdapter messageExtractorAdapter = new MessageExtractorAdapter();
    DistributedTracedMessageExtractor extractor = new DistributedTracedMessageExtractor(
        messageExtractorAdapter);

    String message = "foo";
    String entityId = extractor.entityId(message);
    assertEquals(entityId, message.getClass().toString());
  }

  @Test
  public void testEntityIdWithDistributedTracedMessage() {
    MessageExtractorAdapter messageExtractorAdapter = new MessageExtractorAdapter();
    DistributedTracedMessageExtractor extractor = new DistributedTracedMessageExtractor(
        messageExtractorAdapter);

    String message = "foo";
    Span span = mockTracer.buildSpan("one").start();
    Object distributedTracedMessage = DistributedTracedMessage.wrap(span, message);
    String entityId = extractor.entityId(distributedTracedMessage);
    assertEquals(entityId, message.getClass().toString());
  }

  @Test
  public void testShardIdWithoutDistributedTracedMessage() {
    MessageExtractorAdapter messageExtractorAdapter = new MessageExtractorAdapter();
    DistributedTracedMessageExtractor extractor = new DistributedTracedMessageExtractor(
        messageExtractorAdapter);

    String message = "foo";
    String shardId = extractor.shardId(message);
    assertEquals(shardId, message.getClass().toString());
  }

  @Test
  public void testShardIdWithDistributedTracedMessage() {
    MessageExtractorAdapter messageExtractorAdapter = new MessageExtractorAdapter();
    DistributedTracedMessageExtractor extractor = new DistributedTracedMessageExtractor(
        messageExtractorAdapter);

    String message = "foo";
    Span span = mockTracer.buildSpan("one").start();
    Object distributedTracedMessage = DistributedTracedMessage.wrap(span, message);
    String shardId = extractor.shardId(distributedTracedMessage);
    assertEquals(shardId, message.getClass().toString());
  }

  @Test
  public void testEntityMessageWithoutDistributedTracedMessage() {
    MessageExtractorAdapter messageExtractorAdapter = new MessageExtractorAdapter();
    DistributedTracedMessageExtractor distributedTracedMessageExtractor = new DistributedTracedMessageExtractor(
        messageExtractorAdapter);

    String message = "foo";
    String shardId = distributedTracedMessageExtractor.shardId(message);
    assertEquals(message.getClass().toString(), shardId);
  }

  @Test
  public void testEntityMessageWithDistributedTracedMessage() {
    MessageExtractorAdapter messageExtractorAdapter = new MessageExtractorAdapter() {
      @Override
      public Object entityMessage(Object message) {
        Span span = mockTracer.buildSpan("two").start();
        return DistributedTracedMessage.wrap(span, "other message");
      }
    };
    DistributedTracedMessageExtractor extractor = new DistributedTracedMessageExtractor(
        messageExtractorAdapter);

    String message = "foo";
    Span span = mockTracer.buildSpan("one").start();
    Object tracedMessage = DistributedTracedMessage.wrap(span, message);
    Object extractedMessage = extractor.entityMessage(tracedMessage);
    assertEquals(DistributedTracedMessage.class, extractedMessage.getClass());
    assertNotEquals(((DistributedTracedMessage) tracedMessage).activeSpan().context().toTraceId(),
        ((DistributedTracedMessage) extractedMessage).activeSpan().context().toTraceId());
  }
}
