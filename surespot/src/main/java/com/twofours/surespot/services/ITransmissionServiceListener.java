package com.twofours.surespot.services;


// These methods are used by the transmission service to push data/control flow back up to the activity
public interface ITransmissionServiceListener {
    void onConnected();
    void onReconnectFailed();
    void onCouldNotConnectToServer();
    void onNotConnected();
    void onAlreadyConnected();
}
