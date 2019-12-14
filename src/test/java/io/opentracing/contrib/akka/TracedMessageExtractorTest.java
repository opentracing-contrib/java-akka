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

public class TracedMessageExtractorTest {

    private final MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager());

    static class MessageExtractorAdapter implements ShardRegion.MessageExtractor {
        @Override
        public String entityId(Object message)
        {
            return message.getClass().toString();
        }

        @Override
        public Object entityMessage(Object message)
        {
            return message.getClass().toString();
        }

        @Override
        public String shardId(Object message)
        {
            return message.getClass().toString();
        }
    }

    @Before
    public void before()
    {
        mockTracer.reset();
        GlobalTracer.registerIfAbsent(mockTracer);
    }

    @Before
    public void after()
    {
        GlobalTracerTestUtil.resetGlobalTracer();
    }

    @Test
    public void testEntityIdWithoutTracedMessage()
    {
        MessageExtractorAdapter messageExtractorAdapter = new MessageExtractorAdapter();
        TracedMessageExtractor tracedMessageExtractor = new TracedMessageExtractor(messageExtractorAdapter);

        String message = "foo";
        String entityId = tracedMessageExtractor.entityId(message);
        assertEquals(entityId, message.getClass().toString());
    }

    @Test
    public void testEntityIdWithTracedMessage()
    {
        MessageExtractorAdapter messageExtractorAdapter = new MessageExtractorAdapter();
        TracedMessageExtractor tracedMessageExtractor = new TracedMessageExtractor(messageExtractorAdapter);

        String message = "foo";
        Span span = mockTracer.buildSpan("one").start();
        Object tracedMessage = TracedMessage.wrap(span, message);
        String entityId = tracedMessageExtractor.entityId(tracedMessage);
        assertEquals(entityId, message.getClass().toString());
    }

    @Test
    public void testShardIdWithoutTracedMessage()
    {
        MessageExtractorAdapter messageExtractorAdapter = new MessageExtractorAdapter();
        TracedMessageExtractor tracedMessageExtractor = new TracedMessageExtractor(messageExtractorAdapter);

        String message = "foo";
        String shardId = tracedMessageExtractor.shardId(message);
        assertEquals(shardId, message.getClass().toString());
    }

    @Test
    public void testShardIdWithTracedMessage()
    {
        MessageExtractorAdapter messageExtractorAdapter = new MessageExtractorAdapter();
        TracedMessageExtractor tracedMessageExtractor = new TracedMessageExtractor(messageExtractorAdapter);

        String message = "foo";
        Span span = mockTracer.buildSpan("one").start();
        Object tracedMessage = TracedMessage.wrap(span, message);
        String shardId = tracedMessageExtractor.shardId(tracedMessage);
        assertEquals(shardId, message.getClass().toString());
    }

    @Test
    public void testEntityMessageWithoutTracedMessage()
    {
        MessageExtractorAdapter messageExtractorAdapter = new MessageExtractorAdapter();
        TracedMessageExtractor tracedMessageExtractor = new TracedMessageExtractor(messageExtractorAdapter);

        String message = "foo";
        String shardId = tracedMessageExtractor.shardId(message);
        assertEquals(message.getClass().toString(), shardId);
    }

    @Test
    public void testEntityMessageWithTracedMessage()
    {
        MessageExtractorAdapter messageExtractorAdapter = new MessageExtractorAdapter() {
            @Override
            public Object entityMessage(Object message)
            {
                Span span = mockTracer.buildSpan("two").start();
                return TracedMessage.wrap(span, "other message");
            }
        };
        TracedMessageExtractor tracedMessageExtractor = new TracedMessageExtractor(messageExtractorAdapter);

        String message = "foo";
        Span span = mockTracer.buildSpan("one").start();
        Object tracedMessage = TracedMessage.wrap(span, message);
        Object extractedMessage = tracedMessageExtractor.entityMessage(tracedMessage);
        assertEquals(TracedMessage.class, extractedMessage.getClass());
        assertNotEquals(((TracedMessage) tracedMessage).activeSpan(), ((TracedMessage) extractedMessage).activeSpan());
    }
}
