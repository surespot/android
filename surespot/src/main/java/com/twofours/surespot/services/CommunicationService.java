package com.twofours.surespot.services;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.chat.ChatAdapter;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.chat.SurespotControlMessage;
import com.twofours.surespot.chat.SurespotErrorMessage;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.CookieResponseHandler;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.IAsyncCallbackTuple;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.network.NetworkHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import ch.boye.httpclientandroidlib.cookie.Cookie;
import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

@SuppressLint("NewApi")
public class CommunicationService extends Service {
    private static final String TAG = "CommunicationService";

    private final IBinder mBinder = new CommunicationServiceBinder();
    private ITransmissionServiceListener mListener;
    private ConcurrentLinkedQueue<SurespotMessage> mSendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();
    private ConcurrentLinkedQueue<SurespotMessage> mResendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();
    private BroadcastReceiver mConnectivityReceiver;
    private String mUsername;
    private boolean mMainActivityPaused = false;
    private ReconnectTask mReconnectTask;
    private ReloginTask mReloginTask;
    private DisconnectTask mDisconnectTask;
    public static String mCurrentChat;

    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_DISCONNECTED = 0;
    private static final int MAX_RETRIES = 60;
    private static final int MAX_RELOGIN_RETRIES = 20;

    // maximum time before reconnecting in seconds
    private static final int MAX_RETRY_DELAY = 30;

    private static final int DISCONNECT_DELAY_SECONDS = 60 * 3; // probably want 3, 1 is good for testing
    private static final int RELOGIN_DELAY_SECONDS = 5; // 5 seconds between retries

    private int mTriesRelogin = 0;
    private SocketIO socket;
    private int mRetries = 0;
    private Timer mBackgroundTimer;
    private Object BACKGROUND_TIMER_LOCK = new Object();
    private Timer mDisconnectTimer;
    private Object DISCONNECT_TIMER_LOCK = new Object();
    private Timer mReloginTimer;
    private Object RELOGIN_TIMER_LOCK = new Object();
    private int mConnectionState;
    private boolean mOnWifi;
    private IOCallback mSocketCallback;

    @Override
    public void onCreate() {
        SurespotLog.i(TAG, "onCreate");
        setOnWifi();
        mSocketCallback = new SocketCallbackHandler();
        mConnectivityReceiver = new BroadcastReceiverHandler();
    }

    // sets if the main activity is paused or not
    public void setMainActivityPaused(boolean paused) {
        mMainActivityPaused = paused;
        checkScheduleDisconnect();
        checkReconnect();
    }

    // sets the current user name
    public void setUsername(String username) {
        mUsername = username;
    }

    // Notify the service that the user logged out
    public void userLoggedOut() {
        if (mUsername != null) {
            SurespotLog.d(TAG, "user logging out: " + mUsername);
            save();
            mResendBuffer.clear();
            mSendBuffer.clear();
            mUsername = null;
            shutdownConnection();
            checkShutdownService(true);
            SurespotApplication.getChatController().dispose();
        }
    }

    // initializes the network controller
    public void initNetworkController(String mUser, IAsyncCallbackTuple<String, Boolean> m401Handler) throws Exception {
        setUsername(mUser);
        SurespotApplication.setNetworkController(new NetworkController(this, mUser, m401Handler));
    }

    public synchronized boolean connect() {
        SurespotLog.d(TAG, "connect, socket: " + socket + ", connected: " + (socket != null ? socket.isConnected() : false) + ", state: " + mConnectionState);

        cancelDisconnectTimer();

        if (getConnectionState() == STATE_CONNECTED && socket != null && socket.isConnected()) {
            return true;
        }

        if (getConnectionState() == STATE_CONNECTING) {
            return true;
        }

        setState(STATE_CONNECTING);

        SurespotApplication.getChatController().onBeforeConnect();

        if (getConnectionState() == STATE_CONNECTED) {
            onConnected();
            return true;
        }

        Cookie cookie = IdentityController.getCookieForUser(mUsername);

        try {
            HashMap<String, String> headers = new HashMap<String, String>();
            if (cookie != null) {
                headers.put("cookie", cookie.getName() + "=" + cookie.getValue());
            }
            socket = new SocketIO(SurespotConfiguration.getBaseUrl(), headers);
            socket.connect(mSocketCallback);
        } catch (Exception e) {
            SurespotLog.w(TAG, "connect", e);
        }

        return false;
    }

    public void enqueueMessage(SurespotMessage message) {
        if (getConnectionState() == STATE_DISCONNECTED) {
            connect();
        }
        mSendBuffer.add(message);
    }

    public synchronized void sendMessages() {
        synchronized (BACKGROUND_TIMER_LOCK) {
            if (mBackgroundTimer == null) {
                mBackgroundTimer = new Timer("backgroundTimer");
            }
        }

        SurespotLog.d(TAG, "Sending: " + mSendBuffer.size() + " messages.");

        checkScheduleDisconnect();
        checkShutdownService(false);

        Iterator<SurespotMessage> iterator = mSendBuffer.iterator();
        ArrayList<SurespotMessage> sentMessages = new ArrayList<SurespotMessage>();
        while (iterator.hasNext()) {
            SurespotMessage message = iterator.next();
            if (isMessageReadyToSend(message)) {
                iterator.remove();
                if (!isMessageEqualToAny(message, sentMessages)) {
                    sendMessage(message);
                    sentMessages.add(message);
                } else {
                    SurespotLog.d(TAG, "Prevented sending duplicate message: " + message.toString());
                }
            }
        }
    }

    public int getConnectionState() {
        return mConnectionState;
    }

    public ConcurrentLinkedQueue<SurespotMessage> getResendBuffer() {
        return mResendBuffer;
    }

    public ConcurrentLinkedQueue<SurespotMessage> getSendBuffer() {
        return mSendBuffer;
    }

    public void sendOnSocket(String json) {
        if (socket != null) {
            socket.send(json);
        }
    }

    // saves all data and current state for user, general
    public void save() {
        if (mUsername != null) {
            saveFriends();
            saveMessages(mUsername);
            saveState(mUsername, true);
        }
        saveState(null, true);
        saveMessages();
        saveUnsentMessages();
    }

    public void clearServiceListener() {
        mListener = null;
        checkShutdownService(false);
    }

    public class CommunicationServiceBinder extends Binder {
        public CommunicationService getService() {
            return CommunicationService.this;
        }
    }

    public void setServiceListener(ITransmissionServiceListener listener) {
        mListener = listener;
        if (mListener != null) {
            mRetries = 0; // clear state related to retrying connections
        }
        checkShutdownService(false);
    }

    public ITransmissionServiceListener getServiceListener() {
        return mListener;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // as long as the main activity isn't forced to be destroyed right away, we don't really need to run as STICKY
        // At some point in the future if we want to poll the server for notifications, we may need to run as STICKY
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        SurespotLog.i(TAG, "onDestroy");
        unregisterReceiver();
    }

    public void initializeService() {
        if (mConnectivityReceiver != null) {
            unregisterReceiver();
        }
        SurespotLog.d(TAG, "initializeService: ", this.getClass().getSimpleName());
        this.registerReceiver(mConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    // chat adapters and state

    public synchronized void saveMessages() {
        // save last 30? messages
        SurespotLog.d(TAG, "saveMessages");
        if (mUsername != null) {
            for (Map.Entry<String, ChatAdapter> entry : SurespotApplication.getChatController().mChatAdapters.entrySet()) {
                String them = entry.getKey();
                String spot = ChatUtils.getSpot(mUsername, them);
                SurespotApplication.getStateController().saveMessages(mUsername, spot, entry.getValue().getMessages(),
                        entry.getValue().getCurrentScrollPositionId());
            }
        }
    }

    public synchronized void saveMessages(String username) {
        // save last 30? messages
        SurespotLog.d(TAG, "saveMessages, username: %s", username);
        ChatAdapter chatAdapter = SurespotApplication.getChatController().mChatAdapters.get(username);

        if (chatAdapter != null) {
            SurespotApplication.getStateController().saveMessages(mUsername, ChatUtils.getSpot(mUsername, username), chatAdapter.getMessages(),
                    chatAdapter.getCurrentScrollPositionId());
        }
    }

    public void saveState(String username, boolean fromSave) {

        SurespotLog.d(TAG, "saveState");

        if (username == null) {
            if (!fromSave) {
                saveMessages();
            }
            SurespotLog.d(TAG, "saving last chat: %s", mCurrentChat);
            Utils.putSharedPrefsString(this, SurespotConstants.PrefNames.LAST_CHAT, mCurrentChat);
            if (!fromSave) {
                saveFriends();
            }
        } else if (!fromSave) {
            saveMessages(username);
        }
    }

    // gets messages to resend (without duplicates)
    public SurespotMessage[] getResendMessages() {
        SurespotMessage[] messages = mResendBuffer.toArray(new SurespotMessage[0]);
        List<SurespotMessage> list = new LinkedList<>(Arrays.asList(messages));
        removeDuplicates(list);
        return list.toArray(new SurespotMessage[0]);
    }


    public void saveUnsentMessages() {
        mResendBuffer.addAll(mSendBuffer);
        if (mResendBuffer.size() > 0) {
            SurespotLog.d(TAG, "saving: " + mResendBuffer.size() + " unsent messages.");
        }
        SurespotApplication.getStateController().saveUnsentMessages(mUsername, mResendBuffer);
    }

    public void loadUnsentMessages() {
        Iterator<SurespotMessage> iterator = SurespotApplication.getStateController().loadUnsentMessages(mUsername).iterator();
        while (iterator.hasNext()) {
            mResendBuffer.add(iterator.next());
        }
        // SurespotLog.d(TAG, "loaded: " + mSendBuffer.size() + " unsent messages.");
    }

    public void postFileStream(final String ourVersion, final String user, final String theirVersion, final String id,
                               final InputStream fileInputStream, final String mimeType, final IAsyncCallback<Integer> callback) {
        SurespotApplication.getNetworkController().postFileStream(ourVersion, user, theirVersion, id, fileInputStream, mimeType, callback);
    }

    private void saveFriends() {
        if (SurespotApplication.getChatController().getFriendAdapter() != null && SurespotApplication.getChatController().getFriendAdapter().getCount() > 0) {
            SurespotApplication.getChatController().saveFriends();
        }
    }

    // see if we should schedule a disconnect or not
    private void checkScheduleDisconnect() {
        // if main activity is paused and we're not sending anything
        if (mMainActivityPaused && mSendBuffer.size() == 0) {
            // setup a disconnect N minutes from now
            scheduleDisconnect();
        }
    }

    // setup a disconnect N minutes from now
    private void scheduleDisconnect() {
        synchronized (DISCONNECT_TIMER_LOCK) {
            if (mDisconnectTask != null) {
                mDisconnectTask.cancel();
                mDisconnectTask = null;
            }

            DisconnectTask disconnectTask = new DisconnectTask();
            if (mDisconnectTimer != null) {
                mDisconnectTimer.cancel();
                mDisconnectTimer = null;
            }
            mDisconnectTimer = new Timer("disconnectTimer");
            mDisconnectTimer.schedule(disconnectTask, DISCONNECT_DELAY_SECONDS * 1000);
            mDisconnectTask = disconnectTask;
        }
    }

    // setup a disconnect N minutes from now
    private void startReloginTimer() {
        synchronized (RELOGIN_TIMER_LOCK) {
            if (mReloginTask != null) {
                mReloginTask.cancel();
                mReloginTask = null;
            }

            ReloginTask reloginTask = new ReloginTask();
            if (mReloginTimer != null) {
                mReloginTimer.cancel();
                mReloginTimer = null;
            }
            mReloginTimer = new Timer("reloginTimer");
            mReloginTimer.schedule(reloginTask, RELOGIN_DELAY_SECONDS * 1000);
            mReloginTask = reloginTask;
        }
    }

    // see if it's an appropriate time to reconnect, and if so, try reconnecting
    private void checkReconnect() {
        if (!mMainActivityPaused) {
            if (getConnectionState() == STATE_DISCONNECTED) {
                connect();
            }
        }
    }

    // notify listeners that we've connected
    private void onConnected() {
        // tell any listeners that we're connected
        if (mListener != null) {
            mListener.onConnected();
        }
    }

    // notify listeners that we've connected
    private void onNotConnected() {
        // tell any listeners that we're connected
        if (mListener != null) {
            mListener.onNotConnected();
        }
    }

    // remove duplicate messages
    private List<SurespotMessage> removeDuplicates(List<SurespotMessage> messages) {
        ArrayList<SurespotMessage> messagesSeen = new ArrayList<SurespotMessage>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            SurespotMessage message = messages.get(i);
            if (isMessageEqualToAny(message, messagesSeen)) {
                messages.remove(i);
                SurespotLog.d(TAG, "Prevented sending duplicate message: " + message.toString());
            } else {
                messagesSeen.add(message);
            }
        }
        return messages;
    }

    private boolean isMessageEqualToAny(SurespotMessage message, List<SurespotMessage> messages) {
        for (SurespotMessage msg : messages) {
            if (SurespotMessage.areMessagesEqual(msg, message)) {
                return true;
            }
        }
        return false;
    }

    private int generateInterval(int k) {
        int timerInterval = (int) (Math.pow(2, k) * 1000);
        if (timerInterval > MAX_RETRY_DELAY * 1000) {
            timerInterval = MAX_RETRY_DELAY * 1000;
        }

        int reconnectTime = (int) (Math.random() * timerInterval);
        SurespotLog.d(TAG, "generated reconnect time: %d for k: %d", reconnectTime, k);
        return reconnectTime;
    }

    // stop reconnection attempts
    private void stopReconnectionAttempts() {
        synchronized (BACKGROUND_TIMER_LOCK) {
            if (mBackgroundTimer != null) {
                mBackgroundTimer.cancel();
                mBackgroundTimer = null;
            }
            if (mReconnectTask != null) {
                boolean cancel = mReconnectTask.cancel();
                mReconnectTask = null;
                SurespotLog.d(TAG, "Cancelled reconnect task: " + cancel);
            }
        }
    }

    private void scheduleReconnectionAttempt() {
        int timerInterval = generateInterval(mRetries++);
        SurespotLog.d(TAG, "try %d starting another task in: %d", mRetries - 1, timerInterval);

        synchronized (BACKGROUND_TIMER_LOCK) {
            if (mReconnectTask != null) {
                mReconnectTask.cancel();
                mReconnectTask = null;
            }

            if (mBackgroundTimer != null) {
                mBackgroundTimer.cancel();
                mBackgroundTimer = null;
            }

            // TODO: Is there ever a case where we don't want to try a reconnect?
            ReconnectTask reconnectTask = new ReconnectTask();
            mBackgroundTimer = new Timer("backgroundTimer");
            mBackgroundTimer.schedule(reconnectTask, timerInterval);
            mReconnectTask = reconnectTask;
        }
    }

    // shutdown any connection we have open to the server, close sockets, check if service should shut down
    private void shutdownConnection() {
        disconnect();
        stopReconnectionAttempts();
        unregisterReceiver();
        checkShutdownService(false);
    }

    private void unregisterReceiver() {
        try {
            unregisterReceiver(mConnectivityReceiver);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Receiver not registered")) {
                // Ignore this exception. This is exactly what is desired
            } else {
                // unexpected, re-throw
                throw e;
            }
        }
    }

    private void checkShutdownService(boolean justCalledUserLoggedOut) {
        if (mSendBuffer.size() == 0 && mListener == null) {
            Log.d(TAG, "shutting down service!");
            if (!justCalledUserLoggedOut) {
                userLoggedOut();
            }
            this.stopSelf();
        }
    }

    private synchronized void setState(int state) {
        mConnectionState = state;
        if (state == STATE_CONNECTED) {
            mRetries = 0;
        }
    }

    private class ReconnectTask extends TimerTask {

        @Override
        public void run() {
            SurespotLog.d(TAG, "Reconnect task run.");
            connect();
        }
    }

    private class DisconnectTask extends TimerTask {

        @Override
        public void run() {
            SurespotLog.d(TAG, "Disconnect task run.");
            disconnect();
        }
    }

    private class ReloginTask extends TimerTask {

        @Override
        public void run() {
            SurespotLog.d(TAG, "Relogin task run.");
            boolean reAuthing = tryReLogin();

            if (!reAuthing) {
                socket = null;

                if (mListener != null) {
                    mListener.onReconnectFailed();
                }

                userLoggedOut();
                mTriesRelogin = 0;
            }
        }
    }

    private void stopReloginTimer() {
        // cancel any disconnect that's been scheduled
        synchronized (RELOGIN_TIMER_LOCK) {
            if (mReloginTask != null) {
                mReloginTask.cancel();
                mReloginTask = null;
            }

            if (mReloginTimer != null) {
                mReloginTimer.cancel();
                mReloginTimer = null;
            }
        }
    }

    private void cancelDisconnectTimer() {
        // cancel any disconnect that's been scheduled
        synchronized (DISCONNECT_TIMER_LOCK) {
            if (mDisconnectTask != null) {
                mDisconnectTask.cancel();
                mDisconnectTask = null;
            }

            if (mDisconnectTimer != null) {
                mDisconnectTimer.cancel();
                mDisconnectTimer = null;
            }
        }
    }

    private void disconnect() {
        cancelDisconnectTimer();

        if (SurespotApplication.getChatController() != null) {
            SurespotApplication.getChatController().onPause();
        } else {
            save();
        }

        SurespotLog.d(TAG, "disconnect.");
        setState(STATE_DISCONNECTED);

        if (socket != null) {
            socket.disconnect();
            socket = null;
        }
    }

    private void checkAndSendNextMessage(SurespotMessage message) {
        sendMessages();

        if (mResendBuffer.size() > 0) {
            mResendBuffer.remove(message);
        }
    }

    private boolean isMessageReadyToSend(SurespotMessage message) {
        return !TextUtils.isEmpty(message.getData()) && !TextUtils.isEmpty(message.getFromVersion()) && !TextUtils.isEmpty(message.getToVersion());
    }

    private void sendMessage(final SurespotMessage message) {
        SurespotLog.d(TAG, "sendmessage adding message to ResendBuffer, text: %s, iv: %s", message.getPlainData(), message.getIv());

        mResendBuffer.add(message);
        if (getConnectionState() == STATE_CONNECTED) {
            SurespotLog.d(TAG, "sendmessage, socket: %s", socket);
            JSONObject json = message.toJSONObjectSocket();
            SurespotLog.d(TAG, "sendmessage, json: %s", json);
            String s = json.toString();
            SurespotLog.d(TAG, "sendmessage, message string: %s", s);

            if (socket != null) {
                socket.send(s);
            }
        }
    }

    private void setOnWifi() {
        // get the initial state...sometimes when the app starts it says "hey i'm on wifi" which creates a reconnect
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null) {
            mOnWifi = (networkInfo.getType() == ConnectivityManager.TYPE_WIFI);
        }
    }

    private class SocketCallbackHandler implements IOCallback {

        @Override
        public void onMessage(JSONObject json, IOAcknowledge ack) {
            try {
                SurespotLog.d(TAG, "JSON Server said: %s", json.toString(2));
            } catch (JSONException e) {
                SurespotLog.w(TAG, "onMessage", e);
            }
        }

        @Override
        public void onMessage(String data, IOAcknowledge ack) {
            SurespotLog.d(TAG, "Server said: %s", data);
        }

        @Override
        public synchronized void onError(SocketIOException socketIOException) {
            handleSocketException(socketIOException);
        }

        @Override
        public void onDisconnect() {
            SurespotLog.d(TAG, "Connection terminated.");
            setState(STATE_DISCONNECTED);
            mRetries = 0;
        }

        @Override
        public void onConnect() {
            SurespotLog.d(TAG, "socket.io connection established");
            setOnWifi();
            stopReconnectionAttempts();
            setState(STATE_CONNECTED);
            onConnected();
        }

        @Override
        public void on(String event, IOAcknowledge ack, Object... args) {

            // we need to be careful here about what is UI and what needs to be done to confirm receipt of sent message, error, etc

            SurespotLog.d(TAG, "Server triggered event '" + event + "'");
            if (event.equals("control")) {
                try {
                    SurespotControlMessage message = SurespotControlMessage.toSurespotControlMessage(new JSONObject((String) args[0]));
                    SurespotApplication.getChatController().handleControlMessage(null, message, true, false);
                } catch (JSONException e) {
                    SurespotLog.w(TAG, "on control", e);
                }
            } else if (event.equals("message")) {
                try {
                    JSONObject jsonMessage = new JSONObject((String) args[0]);
                    SurespotLog.d(TAG, "received message: " + jsonMessage.toString());
                    SurespotMessage message = SurespotMessage.toSurespotMessage(jsonMessage);
                    SurespotApplication.getChatController().handleMessage(message);

                    // see if we have deletes
                    String sDeleteControlMessages = jsonMessage.optString("deleteControlMessages", null);
                    if (sDeleteControlMessages != null) {
                        JSONArray deleteControlMessages = new JSONArray(sDeleteControlMessages);

                        if (deleteControlMessages.length() > 0) {
                            for (int i = 0; i < deleteControlMessages.length(); i++) {
                                try {
                                    SurespotControlMessage dMessage = SurespotControlMessage.toSurespotControlMessage(new JSONObject(deleteControlMessages.getString(i)));
                                    SurespotApplication.getChatController().handleControlMessage(null, dMessage, true, false);
                                } catch (JSONException e) {
                                    SurespotLog.w(TAG, "on control", e);
                                }
                            }
                        }
                    }

                    // the UI might have already removed the message from the resend buffer.  That's okay.
                    Iterator<SurespotMessage> iterator = mResendBuffer.iterator();
                    while (iterator.hasNext()) {
                        message = iterator.next();
                        if (message.getIv().equals(message.getId())) {
                            iterator.remove();
                            break;
                        }
                    }

                    checkAndSendNextMessage(message);
                } catch (JSONException e) {
                    SurespotLog.w(TAG, "on message", e);
                }
            } else if (event.equals("messageError")) {
                try {
                    JSONObject jsonMessage = (JSONObject) args[0];
                    SurespotLog.d(TAG, "received messageError: " + jsonMessage.toString());
                    SurespotErrorMessage errorMessage = SurespotErrorMessage.toSurespotErrorMessage(jsonMessage);
                    SurespotApplication.getChatController().handleErrorMessage(errorMessage);

                    // the UI might have already removed the message from the resend buffer.  That's okay.
                    SurespotMessage message = null;

                    Iterator<SurespotMessage> iterator = mResendBuffer.iterator();
                    while (iterator.hasNext()) {
                        message = iterator.next();
                        if (message.getIv().equals(errorMessage.getId())) {
                            iterator.remove();
                            message.setErrorStatus(errorMessage.getStatus());
                            break;
                        }
                    }
                } catch (JSONException e) {
                    SurespotLog.w(TAG, "on messageError", e);
                }
            }
        }
    }

    private void handleSocketException(SocketIOException socketIOException) {
        boolean reAuthing = false;
        setState(STATE_DISCONNECTED);
        onNotConnected();

        // socket.io returns 403 for can't login
        if (socketIOException.getHttpStatus() == 403) {
            SurespotLog.d(TAG, "got 403 from websocket");

            reAuthing = tryReLogin();

            if (!reAuthing) {
                socket = null;

                if (mListener != null) {
                    mListener.onReconnectFailed();
                }

                userLoggedOut();
                return;
            }
        }

        if (reAuthing)
            return;

        SurespotLog.i(TAG, socketIOException, "an Error occured, attempting reconnect with exponential backoff, retries: %d", mRetries);

        setOnWifi();
        // kick off another task
        if (mRetries < MAX_RETRIES) {
            scheduleReconnectionAttempt();
        } else {
            SurespotLog.i(TAG, "Socket.io reconnect retries exhausted, giving up.");
            if (mListener != null) {
                mListener.onCouldNotConnectToServer();
            }
            // TODO: is this appropriate to call?  I believe so, we make the user log in again anyway in this scenario
            userLoggedOut();
        }
    }


    private class ReLoginCookieResponseHandler extends CookieResponseHandler {
        @Override
        public void onSuccess(int responseCode, String result, Cookie cookie) {
            stopReloginTimer();
            mTriesRelogin = 0;
            connect();
        }

        @Override
        public void onFailure(Throwable arg0, String content) {
            stopReloginTimer();
            if (mTriesRelogin++ > MAX_RELOGIN_RETRIES) {
                // give up
                SurespotLog.i(TAG, "Max login retries exceeded.  Giving up");
                socket = null;

                if (mListener != null) {
                    mListener.onReconnectFailed();
                }

                userLoggedOut();
                mTriesRelogin = 0;
            } else {
                startReloginTimer();
            }
        }
    }

    private boolean tryReLogin() {
        return NetworkHelper.reLogin(CommunicationService.this, SurespotApplication.getNetworkController(), mUsername, new ReLoginCookieResponseHandler());
    }

    private class BroadcastReceiverHandler extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            SurespotLog.d(TAG, "Connectivity Action");
            debugIntent(intent, TAG);
            Bundle extras = intent.getExtras();
            if (extras.containsKey("networkInfo")) {
                NetworkInfo networkInfo2 = (NetworkInfo) extras.get("networkInfo");
                if (networkInfo2.getState() == NetworkInfo.State.CONNECTED) {
                    SurespotLog.d(TAG, "Network newly connected, reconnecting...");
                    synchronized (CommunicationService.this) {
                        setOnWifi();
                        if (!mMainActivityPaused && mListener != null) {
                            if (getConnectionState() == STATE_CONNECTED && socket != null && socket.isConnected()) {
                                return;
                            }
                            disconnect();
                            connect();
                        }
                    }
                    return;
                }
            }
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                SurespotLog.d(TAG, "isconnected: " + networkInfo.isConnected());
                SurespotLog.d(TAG, "failover: " + networkInfo.isFailover());
                SurespotLog.d(TAG, "reason: " + networkInfo.getReason());
                SurespotLog.d(TAG, "type: " + networkInfo.getTypeName());

                // if it's not a failover and wifi is now active then initiate reconnect
                if (!networkInfo.isFailover() && (networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected())) {
                    synchronized (CommunicationService.this) {
                        // if we're not connecting, connect
                        if (getConnectionState() != STATE_CONNECTING && !mOnWifi) {
                            mOnWifi = true;

                            if (!mMainActivityPaused && mListener != null) {
                                SurespotLog.d(TAG, "Network switch, Reconnecting...");

                                setState(STATE_CONNECTING);

                                disconnect();
                                connect();
                            }
                        }
                    }
                }
            } else {
                SurespotLog.d(TAG, "networkinfo null");
            }
        }

        private void debugIntent(Intent intent, String tag) {
            Log.v(tag, "action: " + intent.getAction());
            Log.v(tag, "component: " + intent.getComponent());
            Bundle extras = intent.getExtras();
            if (extras != null) {
                for (String key : extras.keySet()) {
                    Log.d(tag, "key [" + key + "]: " +
                            extras.get(key));
                }
            } else {
                Log.v(tag, "no extras");
            }
        }
    }
}

