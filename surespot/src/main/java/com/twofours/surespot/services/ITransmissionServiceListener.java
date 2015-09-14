package com.twofours.surespot.services;

import io.socket.IOAcknowledge;

// These methods are used by the transmission service to push data/control flow back up to the activity
public interface ITransmissionServiceListener {
    void connected();
    void reconnectFailed();
    void couldNotConnectToServer();
    void onEventReceived(String event, IOAcknowledge ack, Object... args);
}
