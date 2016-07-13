package com.ethercamp.harmony.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Created by Stan Reshetnyk on 11.07.16.
 *
 * Encapsulates specific code for sending messages to client side.
 */
@Service
public class ClientMessageService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void sendToTopic(String topic, Object dto) {
        messagingTemplate.convertAndSend(topic, dto);
    }
}
