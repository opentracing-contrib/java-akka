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
import akka.actor.Props;
import akka.actor.Status;

import java.util.ArrayList;
import java.util.List;

public class MockActor extends AbstractActor
{
    List<MockMessage> testMessages = new ArrayList<MockMessage>();
    List<Object> unhandledMessages = new ArrayList<Object>();

    public static Props props() {
        return Props.create(MockActor.class, () -> new MockActor());
    }

    static String getResponse(Object message) {
        return message.toString() + "::response";
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(MockMessage.class, message -> {
                testMessages.add(message);
                getSender().tell(getResponse(message), getSelf());
            })
            .match(ErrorMessage.class, message -> {
                getSender().tell(new Status.Failure(new Exception("Error at runtime")), getSelf());
            })
            .matchAny(message -> {
                unhandledMessages.add(message);
                getSender().tell(getResponse(message), getSelf());
            })
            .build();
    }

    public static class MockMessage {
        final Object value;

        public MockMessage() {
            this(null);
        }

        public MockMessage(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }
    }

    public static class ErrorMessage {
    }
}
