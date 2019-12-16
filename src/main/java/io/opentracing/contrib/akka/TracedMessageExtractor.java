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

import akka.cluster.sharding.ShardRegion;

public class TracedMessageExtractor implements ShardRegion.MessageExtractor {
  private ShardRegion.MessageExtractor target;

  public TracedMessageExtractor(final ShardRegion.MessageExtractor messageExtractor) {
    this.target = messageExtractor;
  }

  @Override
  public String entityId(final Object message) {
    if (message instanceof TracedMessage) {
      final TracedMessage<?> tracedMessage = (TracedMessage<?>) message;
      return this.target.entityId(tracedMessage.message());
    }
    return this.target.entityId(message);
  }

  @Override
  public Object entityMessage(Object message) {
    if (message instanceof TracedMessage) {
      final TracedMessage<?> tracedMessage = (TracedMessage<?>) message;
      Object result = this.target.entityMessage(tracedMessage.message());
      if (result instanceof TracedMessage) {
        return result;
      }
      return TracedMessage.wrap(tracedMessage.activeSpan(), result);
    }
    return this.target.entityMessage(message);
  }

  @Override
  public String shardId(Object message) {
    if (message instanceof TracedMessage) {
      final TracedMessage<?> tracedMessage = (TracedMessage<?>) message;
      return this.target.shardId(tracedMessage.message());
    }
    return this.target.shardId(message);
  }
}
