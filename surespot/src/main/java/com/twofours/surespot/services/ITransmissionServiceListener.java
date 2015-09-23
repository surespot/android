package com.twofours.surespot.services;

import com.twofours.surespot.chat.ChatAdapter;
import com.twofours.surespot.chat.SurespotControlMessage;
import com.twofours.surespot.chat.SurespotErrorMessage;
import com.twofours.surespot.chat.SurespotMessage;

import io.socket.IOAcknowledge;

// These methods are used by the transmission service to push data/control flow back up to the activity
public interface ITransmissionServiceListener {
    void onConnected();
    void onReconnectFailed();
    void onCouldNotConnectToServer();
    void handleMessage(SurespotMessage message);
}
