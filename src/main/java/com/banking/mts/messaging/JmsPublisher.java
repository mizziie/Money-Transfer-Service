package com.banking.mts.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JmsPublisher {

    private final JmsTemplate jmsTemplate;

    @Value("${ibm.mq.queue:TRANSFER.COMPLETED}")
    private String queueName;

    public void send(String message) {
        try {
            jmsTemplate.send(queueName, session -> session.createTextMessage(message));
            log.info("Published message to queue {}: {}", queueName, message);
        } catch (Exception e) {
            log.error("Failed to publish message to queue {}: {}", queueName, e.getMessage(), e);
            throw new RuntimeException("Failed to publish message to IBM MQ", e);
        }
    }
}
