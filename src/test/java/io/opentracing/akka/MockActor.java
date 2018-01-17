package io.opentracing.akka;

import java.util.ArrayList;
import java.util.List;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.actor.Status;

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
