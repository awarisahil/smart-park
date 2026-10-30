package com.smartparking.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Broadcasts the live slot board to all connected clients.
 * Messages are pushed after the current transaction commits so clients
 * never see state that could still roll back.
 */
@Component
@RequiredArgsConstructor
public class SlotNotifier {

    public static final String SLOT_BOARD_TOPIC = "/topic/slots";

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastSlotBoard(Object board) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend(SLOT_BOARD_TOPIC, board);
                }
            });
        } else {
            messagingTemplate.convertAndSend(SLOT_BOARD_TOPIC, board);
        }
    }
}
