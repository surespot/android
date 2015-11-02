package com.twofours.surespot.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.TextUtils;
import android.util.Log;

import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatAdapter;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.chat.FileStreamMessage;
import com.twofours.surespot.chat.SurespotControlMessage;
import com.twofours.surespot.chat.SurespotErrorMessage;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.common.WebClientDevWrapper;
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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import ch.boye.httpclientandroidlib.conn.ssl.SSLSocketFactory;
import ch.boye.httpclientandroidlib.cookie.Cookie;
import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.Transport;
import io.socket.engineio.client.transports.WebSocket;

@SuppressLint("NewApi")
public class CommunicationService extends Service {
    private static final String TAG = "CommunicationService";

    private final IBinder mBinder = new CommunicationServiceBinder();
    private ITransmissionServiceListener mListener;
    private ConcurrentLinkedQueue<SurespotMessage> mSendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();
    private ConcurrentLinkedQueue<SurespotMessage> mResendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();
    private ConcurrentLinkedQueue<FileStreamMessage> mFileStreamsToSend = new ConcurrentLinkedQueue<FileStreamMessage>();
    private BroadcastReceiver mConnectivityReceiver;
    private String mUsername;
    private boolean mMainActivityPaused = false;
    private SendUnsentMaterialTask mSendUnsentMaterialTask;
    private ReconnectTask mReconnectTask;
    private ReloginTask mReloginTask;
    public static String mCurrentChat;
    Handler mHandler = new Handler(Looper.getMainLooper());

    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_DISCONNECTED = 0;
    private static final int MAX_RETRIES_SEND_VIA_HTTP = 30;
    private static final int MAX_RETRIES = 60;
    private static final int MAX_RETRIES_MAIN_ACTIVITY_PAUSED = 20;
    private static final int MAX_RELOGIN_RETRIES = 60;

    // maximum time before reconnecting in seconds
    private static final int MAX_RETRY_DELAY = 10;

    private int mResendViaHttpTries = 0;
    private int mTriesRelogin = 0;
    private Socket mSocket;
    private int mRetries = 0;
    private Timer mResendViaHttpTimer;
    private Object RESEND_HTTP_TIMER_LOCK = new Object();
    private Timer mBackgroundTimer;
    private Object BACKGROUND_TIMER_LOCK = new Object();
    private Timer mReloginTimer;
    private Object RELOGIN_TIMER_LOCK = new Object();
    private int mConnectionState;
    private boolean mOnWifi;
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mBuilder;
    private boolean mPromptingResendErroredMessages = false;

    @Override
    public void onCreate() {
        SurespotLog.i(TAG, "onCreate");
        setOnWifi();
        mConnectivityReceiver = new BroadcastReceiverHandler();
        resetState();
        mNotificationManager = (NotificationManager) CommunicationService.this.getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(CommunicationService.this);
    }

    private void resetState() {
        mRetries = 0;
        mTriesRelogin = 0;
        mResendViaHttpTries = 0;
        mPromptingResendErroredMessages = false;

    }

    private synchronized void disposeSocket() {
        if (mSocket != null) {
            mSocket.off(Socket.EVENT_CONNECT);
            mSocket.off(Socket.EVENT_DISCONNECT);
            mSocket.off(Socket.EVENT_ERROR);
            mSocket.off(Socket.EVENT_CONNECT_ERROR);
            mSocket.off(Socket.EVENT_CONNECT_TIMEOUT);
            mSocket.off(Socket.EVENT_MESSAGE);
            mSocket.off("messageError");
            mSocket.off("control");
            mSocket.io().off(Manager.EVENT_TRANSPORT);
            mSocket = null;
        }
    }

    private Socket createSocket() {
        if (mSocket == null) {
            IO.Options opts = new IO.Options();
            opts.sslContext = WebClientDevWrapper.getSSLContext();
            opts.secure = true;
            opts.hostnameVerifier = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
            opts.transports = new String[]{WebSocket.NAME};

            try {
                mSocket = IO.socket(SurespotConfiguration.getBaseUrl(), opts);
            }
            catch (URISyntaxException e) {
                mSocket = null;
                return null;
            }

            mSocket.on(Socket.EVENT_CONNECT, onConnect);
            mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect);
            mSocket.on(Socket.EVENT_ERROR, onConnectError);
            mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
            mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
            mSocket.on(Socket.EVENT_MESSAGE, onMessage);
            mSocket.on("messageError", onMessageError);
            mSocket.on("control", onControl);
            mSocket.io().on(Manager.EVENT_TRANSPORT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {

                    Transport transport = (Transport) args[0];
                    SurespotLog.d(TAG, "socket.io EVENT_TRANSPORT");
                    transport.once(Transport.EVENT_REQUEST_HEADERS, new Emitter.Listener() {
                        @Override
                        public void call(Object... args) {
                            SurespotLog.d(TAG, "socket.io EVENT_REQUEST_HEADERS");
                            @SuppressWarnings("unchecked")
                            Map<String, List> headers = (Map<String, List>) args[0];
                            // set header
                            Cookie cookie = IdentityController.getCookieForUser(mUsername);
                            //TODO not sure why it's null seems like we need to handle this
                            if (cookie != null) {
                                ArrayList<String> cookies = new ArrayList<String>();
                                cookies.add(cookie.getName() + "=" + cookie.getValue());
                                headers.put("cookie", cookies);
                            }
                        }
                    });
                }
            });

        }
        return mSocket;
    }

    // sets if the main activity is paused or not
    public void setMainActivityPaused(boolean paused) {
        mMainActivityPaused = paused;
        // changed with not keeping socket open: checkScheduleDisconnect();
        // changed with not keeping socket open: checkReconnect();
        if (paused) {
            // changed with not keeping socket open:
            /*
            if (getConnectionState() != STATE_CONNECTED && mEverConnected) {
                scheduleGiveUpReconnecting();
            }
            */
            // make sure to save everything just in case... ?
            // save();
            disconnect();
        }
        else {
            connect();
            // changed with not keeping socket open: cancelDisconnectTimer();
            // changed with not keeping socket open: cancelGiveUpReconnectingTimer();
        }
    }

    // sets the current user name
    public void setUsername(String username) {
        SurespotLog.d(TAG, "setUsername, mUsername: %s, username: %s", mUsername, username);
        if (username != null && !username.equals(mUsername)) {
            disconnect();
            mUsername = username;
        }
    }

    // Notify the service that the user logged out
    public void userLoggedOut() {
        if (mUsername != null) {
            SurespotLog.d(TAG, "user logging out: " + mUsername);
            sendFileStreamMessages(true); // this could be a save if we want to persist across runs
            save();
            mResendBuffer.clear();
            mSendBuffer.clear();
            mUsername = null;
            shutdownConnection();
            checkShutdownService(true, false, false);
            SurespotApplication.getChatController().dispose();
        }
    }

    // initializes the network controller
    public void initNetworkController(String mUser, IAsyncCallbackTuple<String, Boolean> m401Handler) throws Exception {
        SurespotLog.d(TAG, "initNetworkController: setting user name to " + mUser + " and m401 handler");
        setUsername(mUser);
        SurespotApplication.setNetworkController(new NetworkController(this, mUser, m401Handler));
    }

    public synchronized boolean connect(String username) {
        if (mUsername != null && !mUsername.equals(username)) {
            SurespotLog.d(TAG, "Username did not match existing username on connect.  Setting user name to " + username + " and creating new network controller with saved m401Handler");
            disconnect();
            setUsername(username);
        }

        return connect();
    }

    public synchronized boolean connect() {

        if (mMainActivityPaused) {
            // if the communication service wants to stay connected again any time in the future, disable the below statement
            return true;
        }

        SurespotLog.d(TAG, "connect, mSocket: " + mSocket + ", connected: " + (mSocket != null ? mSocket.connected() : false) + ", state: " + mConnectionState);

        if (getConnectionState() == STATE_CONNECTED && mSocket != null && mSocket.connected()) {
            onAlreadyConnected();
            return true;
        }

        if (getConnectionState() == STATE_CONNECTING) {
            // do NOT call already connected here, since we're not already connected
            // need to test to see if the program flow is good returning true here, or if we should allow things to continue
            // and try to connect()...
            return true;
        }

        setState(STATE_CONNECTING);

        SurespotApplication.getChatController().onBeforeConnect();

        if (getConnectionState() == STATE_CONNECTED) {
            onConnected();
            return true;
        }

        try {
            createSocket();
            mSocket.connect();
        }
        catch (Exception e) {
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


        Iterator<SurespotMessage> iterator = mSendBuffer.iterator();
        ArrayList<SurespotMessage> sentMessages = new ArrayList<SurespotMessage>();
        while (iterator.hasNext()) {
            SurespotMessage message = iterator.next();
            if (isMessageReadyToSend(message)) {
                iterator.remove();
                if (!isMessageEqualToAny(message, sentMessages)) {
                    sendMessage(message);
                    sentMessages.add(message);
                }
                else {
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

//    public void sendMessages(JSONArray json) {
//        if (mSocket != null) {
//            mSocket.send(json);
//        }
//    }

    // saves all data and current state for user, general
    public synchronized void save() {
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
    }

    public void saveIfMainActivityPaused() {
        if (mMainActivityPaused) {
            save();
        }
    }

    private void sendFileStreamMessages(boolean flushIfNotConnected) {
        Iterator<FileStreamMessage> iterator = mFileStreamsToSend.iterator();
        while (iterator.hasNext()) {
            FileStreamMessage message = iterator.next();
            if (flushIfNotConnected) {
                iterator.remove();
                sendFileStreamMessageImmediately(message);
            }
            else {
                if (trySendFileStreamMessage(message)) {
                    iterator.remove();
                }
            }
        }
    }

    private boolean trySendFileStreamMessage(FileStreamMessage fileStreamMessage) {
        if (getConnectionState() == STATE_CONNECTED) {
            sendFileStreamMessageImmediately(fileStreamMessage);
            return true;
        }

        return false;
    }

    public void sendFileStreamMessage(FileStreamMessage fileStreamMessage) {
        if (!trySendFileStreamMessage(fileStreamMessage)) {
            // "offline mode" - queue file for sending as soon as we reconnect
            mFileStreamsToSend.add(fileStreamMessage);
        }
    }

    private void sendFileStreamMessageImmediately(FileStreamMessage fileStreamMessage) {
        SurespotApplication.getCommunicationService().postFileStream(IdentityController.getOurLatestVersion(mUsername), fileStreamMessage.mTo, IdentityController.getTheirLatestVersion(fileStreamMessage.mTo),
                fileStreamMessage.mIv, fileStreamMessage.mStream, fileStreamMessage.mMimeType, fileStreamMessage.mAsyncCallback);
    }

    public boolean isConnected() {
        return getConnectionState() == CommunicationService.STATE_CONNECTED;
    }

    public void resendMessage(SurespotMessage message) {
        // user wants to resend the message.  Retry connection if we have given up/are not connected.
        // If message is in resend buffer, simply retry connection and it will be sent once connected.
        // If message is not in resend buffer, then simply attempt to send the message again

        if (getConnectionState() == STATE_DISCONNECTED) {
            mRetries = 0; // start over in terms of retries (?)
            connect();
        }

        if (mResendBuffer.contains(message) || mSendBuffer.contains(message)) {
            // do nothing - message will transmit when we reconnect
        } else {
            // resend the message
            sendMessage(message);
        }
    }

    public void setPromptingResendErroredMessages() {
        mPromptingResendErroredMessages = true;
    }

    public void setResendErroredMessages() {
        mPromptingResendErroredMessages = false;
        if (!isConnected() && getConnectionState() != STATE_CONNECTING) {
            mRetries = 0;
            connect();
        }

        if (isConnected()) {
            handleUnsentMaterial();
        }
    }

    public void setDoNotResendErroredMessages() {
        mPromptingResendErroredMessages = false;
        // confirm this is sufficient - simply clear the resend/send buffers
        mResendBuffer.clear();
        mSendBuffer.clear();

        invalidateAllChatAdapters();
    }

    private void invalidateAllChatAdapters() {
        if (mUsername != null) {
            for (Map.Entry<String, ChatAdapter> entry : SurespotApplication.getChatController().mChatAdapters.entrySet()) {
                entry.getValue().notifyDataSetInvalidated();
            }
        }
    }

    public boolean messageIsInResendBuffer(SurespotMessage message) {
        if (mSendBuffer.contains(message) || mResendBuffer.contains(message))
            return true;

        return false;
    }

    public boolean hasUnsentMessages() {
        return (mResendBuffer.size() > 0 || mSendBuffer.size() > 0);
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
        //checkShutdownService(false, false, false);
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
        disposeSocket();
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
        }
        else if (!fromSave) {
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

        SurespotMessage[] messages = mResendBuffer.toArray(new SurespotMessage[0]);
        List<SurespotMessage> list = new LinkedList<>(Arrays.asList(messages));
        removeDuplicates(list);
        mResendBuffer.clear();
        for (SurespotMessage m : list) {
            mResendBuffer.add(m);
        }

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
            int timerInterval = generateInterval(mTriesRelogin + 1);
            SurespotLog.d(TAG, "startReloginTimer, try %d starting another login task in: %d", mTriesRelogin, timerInterval);
            mReloginTimer.schedule(reloginTask, timerInterval);
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

    private synchronized void handleUnsentMaterial() {

        // if we're asking the user if they want to resend errored messages, don't just go blindly sending them without user input
        if (mPromptingResendErroredMessages)
            return;

        if (mUsername != null && !mUsername.equals("")) {
            sendFileStreamMessages(false);
        }

        final ArrayList<SurespotMessage> toSend = new ArrayList<SurespotMessage>();

        if (mResendBuffer.size() > 0) {
            for (SurespotMessage message : getResendMessages()) {
                if (message.getId() != null) {
                    continue;
                }

                if (SurespotApplication.getChatController() != null) {

                    // set the last received id so the server knows which messages to check
                    String otherUser = message.getOtherUser();

                    // String username = message.getFrom();
                    Integer lastMessageID = null;
                    // ideally get the last id from the fragment's chat adapter
                    ChatAdapter chatAdapter = SurespotApplication.getChatController().mChatAdapters.get(otherUser);
                    if (chatAdapter != null) {
                        SurespotMessage lastMessage = chatAdapter.getLastMessageWithId();
                        if (lastMessage != null) {
                            lastMessageID = lastMessage.getId();
                        }
                    }

                    // failing that use the last viewed id
                    if (lastMessageID == null) {
                        if (SurespotApplication.getChatController().getFriendAdapter() != null) {
                            SurespotApplication.getChatController().getFriendAdapter().getFriend(otherUser).getLastViewedMessageId();
                        }
                    }

                    if (lastMessageID == null) {
                        lastMessageID = 0;
                    }
                    SurespotLog.d(TAG, "setting resendId, otheruser: " + otherUser + ", id: " + lastMessageID);
                    message.setResendId(lastMessageID);

                }
                // use new POST to /messages to post messages using HTTP(S)
                toSend.add(message);
            }

            mResendBuffer.clear();
        }

        if (!isConnected()) {
            if (SurespotApplication.getNetworkControllerNoThrow() != null && toSend.size() > 0) {
                // send via http(s) since the web socket is not connected
                SurespotApplication.getNetworkController().postMessages(toSend, new JsonHttpResponseHandler() {

                    @Override
                    public void onSuccess(JSONObject jsonObject) {
                        // update message id, chat adapter based on response.  chat adapter is not updating its UI either without user interaction
                        try {
                            JSONArray messages = jsonObject.getJSONArray("messageStatus");
                            for (int n = 0; n < messages.length(); n++) {
                                JSONObject messageAndStatus = messages.getJSONObject(n);
                                JSONObject jsonMessage = messageAndStatus.getJSONObject("message");
                                // JSONObject jsonStatus = messageAndStatus.getJSONObject("status");
                                // status should be 204 - TODO: will it ever be the case that some messages return 204 and others do not?
                                SurespotMessage messageReceived = SurespotMessage.toSurespotMessage(jsonMessage);
                                // TODO: do we need to do more here?
                                SurespotApplication.getChatController().handleMessage(messageReceived);
                            }
                        } catch (JSONException e) {
                            SurespotLog.w(TAG, e, "JSON received from server");
                        }
                    }

                    @Override
                    public void onSuccess(JSONArray jsonArray) {
                        SurespotLog.w(TAG, "Unexpected condition - JSON array received from server");
                    }

                    @Override
                    public void onSuccess(int statusCode, String content) {
                        super.onSuccess(statusCode, content);
                    }

                    @Override
                    public void onFailure(Throwable error, String content) {
                        // re-add to resend buffer
                        mResendBuffer.addAll(toSend);
                        scheduleSendUnsentMaterialTimer();
                    }

                    @Override
                    public void onFailure(Throwable e, JSONObject errorResponse) {
                        // re-add to resend buffer
                        // do we need to be more fine-grained about what failed?  will the server ever succeed for some messages but fail for others?
                        mResendBuffer.addAll(toSend);
                        scheduleSendUnsentMaterialTimer();
                    }

                    @Override
                    public void onFailure(Throwable e, JSONArray errorResponse) {
                        // re-add to resend buffer
                        // do we need to be more fine-grained about what failed?  will the server ever succeed for some messages but fail for others?
                        mResendBuffer.addAll(toSend);
                        scheduleSendUnsentMaterialTimer();
                    }
                });
            }
        } else {

            // send via socket since we're connected
            mSendBuffer.addAll(toSend);
            if (mSendBuffer.size() > 0) {
                sendMessages();
            }
        }
    }




    private void onAlreadyConnected() {
        SurespotLog.d(TAG, "onAlreadyConnected");

        // tell any listeners that we're connected
        if (mListener != null) {
            mListener.onAlreadyConnected();
        }

        handleUnsentMaterial();
    }

    // notify listeners that we've connected
    private void onConnected() {
        SurespotLog.d(TAG, "onConnected");

        // tell any listeners that we're connected
        if (mListener != null) {
            SurespotLog.d(TAG, "onConnected, mListener calling onConnected()");
            mListener.onConnected();
        } else {
            SurespotLog.d(TAG, "onConnected, mListener was null");
        }

        handleUnsentMaterial();
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
            }
            else {
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

    private void scheduleSendUnsentMaterialTimer() {
        if (mResendViaHttpTries >= MAX_RETRIES_SEND_VIA_HTTP) {
            raiseNotificationForUnsentMessages();
            //checkShutdownService(false, false, true);
            return;
        }
        int timerInterval = generateInterval(mResendViaHttpTries++);
        SurespotLog.d(TAG, "try %d sending unsent material starting another task in: %d", mResendViaHttpTries - 1, timerInterval);

        synchronized (RESEND_HTTP_TIMER_LOCK) {
            if (mSendUnsentMaterialTask != null) {
                mSendUnsentMaterialTask.cancel();
                mSendUnsentMaterialTask = null;
            }

            if (mResendViaHttpTimer != null) {
                mResendViaHttpTimer.cancel();
                mResendViaHttpTimer = null;
            }

            // Is there ever a case where we don't want to try a reconnect?
            SendUnsentMaterialTask retrySendViaHttpTask = new SendUnsentMaterialTask();
            mResendViaHttpTimer = new Timer("sendUnsentMaterialTimer");
            mResendViaHttpTimer.schedule(retrySendViaHttpTask, timerInterval);
            mSendUnsentMaterialTask = retrySendViaHttpTask;
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

            // Is there ever a case where we don't want to try a reconnect?
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
    }

    private void unregisterReceiver() {
        try {
            unregisterReceiver(mConnectivityReceiver);
        }
        catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Receiver not registered")) {
                // Ignore this exception. This is exactly what is desired
            }
            else {
                // unexpected, re-throw
                throw e;
            }
        }
    }

    private void checkShutdownService(boolean justCalledUserLoggedOut, boolean justDisconnecting, boolean timeoutTimerJustExpired) {
        if (mSendBuffer.size() == 0 &&
                (mMainActivityPaused && mListener == null) &&
                !justDisconnecting &&
                ((timeoutTimerJustExpired && mMainActivityPaused) || mListener == null) &&
                (timeoutTimerJustExpired || mResendBuffer.size() == 0)) {
            Log.d(TAG, "shutting down service!");
            if (!justCalledUserLoggedOut) {
                userLoggedOut();
            }
            resetState();
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

    private class SendUnsentMaterialTask extends TimerTask {

        @Override
        public void run() {
            SurespotLog.d(TAG, "SendUnsentMaterial task run.");
            handleUnsentMaterial();
        }
    }


    private void raiseNotificationForUnsentMessages() {
        mBuilder.setAutoCancel(true).setOnlyAlertOnce(true);
        SharedPreferences pm = null;
        if (mUsername != null) {
            pm = getSharedPreferences(mUsername, Context.MODE_PRIVATE);
        }

        int icon = R.drawable.surespot_logo;

        // need to use same builder for only alert once to work:
        // http://stackoverflow.com/questions/6406730/updating-an-ongoing-notification-quietly
        mBuilder.setSmallIcon(icon).setContentTitle(getString(R.string.error_sending_messages)).setAutoCancel(true).setOnlyAlertOnce(false).setContentText(getString(R.string.error_sending_detail));
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);

        Intent mainIntent = null;
        mainIntent = new Intent(this, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mainIntent.putExtra(SurespotConstants.ExtraNames.UNSENT_MESSAGES, "true");
        mainIntent.putExtra(SurespotConstants.ExtraNames.NAME, mUsername);

        stackBuilder.addNextIntent(mainIntent);

        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent((int) new Date().getTime(), PendingIntent.FLAG_CANCEL_CURRENT);

        mBuilder.setContentIntent(resultPendingIntent);
        int defaults = 0;

        boolean showLights = pm == null ? true : pm.getBoolean("pref_notifications_led", true);
        boolean makeSound = pm == null ? true : pm.getBoolean("pref_notifications_sound", true);
        boolean vibrate = pm == null ? true : pm.getBoolean("pref_notifications_vibration", true);
        int color = pm == null ? 0xff0000FF : pm.getInt("pref_notification_color", getResources().getColor(R.color.surespotBlue));

        if (showLights) {
            SurespotLog.v(TAG, "showing notification led");
            mBuilder.setLights(color, 500, 5000);
            defaults |= Notification.FLAG_SHOW_LIGHTS;
        }
        else {
            mBuilder.setLights(color, 0, 0);
        }

        if (makeSound) {
            SurespotLog.v(TAG, "making notification sound");
            defaults |= Notification.DEFAULT_SOUND;
        }

        if (vibrate) {
            SurespotLog.v(TAG, "vibrating notification");
            defaults |= Notification.DEFAULT_VIBRATE;
        }

        mBuilder.setDefaults(defaults);
        mNotificationManager.notify(SurespotConstants.ExtraNames.UNSENT_MESSAGES, SurespotConstants.IntentRequestCodes.UNSENT_MESSAGE_NOTIFICATION, mBuilder.build());

        // mNotificationManager.notify(tag, id, mBuilder.build());
        // Notification notification = UIUtils.generateNotification(mBuilder, contentIntent, getPackageName(), title, message);
        // mNotificationManager.notify(tag, id, notification);
    }

    private class ReloginTask extends TimerTask {

        @Override
        public void run() {
            SurespotLog.d(TAG, "Relogin task run.");
            boolean reAuthing = tryReLogin();

            if (!reAuthing) {
                disposeSocket();

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
        SurespotLog.d(TAG, "stopReloginTimer");
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

    private void disconnect() {

        if (SurespotApplication.getChatController() != null) {
            SurespotApplication.getChatController().onPause();
        }
        else {
            save();
        }

        SurespotLog.d(TAG, "disconnect.");
        setState(STATE_DISCONNECTED);

        if (mSocket != null) {
            mSocket.disconnect();
            disposeSocket();
        }

        // attempt to send all remaining items via http(s)
        handleUnsentMaterial();
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
        SurespotLog.d(TAG, "sendmessage adding message to ResendBuffer, iv: %s", message.getIv());

        mResendBuffer.add(message);

        // for now, send all messages using http for testing
        if (mMainActivityPaused) {
            sendMessageUsingHttp(message);
        } else {
            if (getConnectionState() == STATE_CONNECTED) {
                SurespotLog.d(TAG, "sendmessage, mSocket: %s", mSocket);
                JSONObject json = message.toJSONObjectSocket();
                SurespotLog.d(TAG, "sendmessage, json: %s", json);
                //String s = json.toString();
                //SurespotLog.d(TAG, "sendmessage, message string: %s", s);

                if (mSocket != null) {
                    mSocket.send(json);
                }
            }
        }
    }

    private void sendMessageUsingHttp(final SurespotMessage message) {
        ArrayList<SurespotMessage> toSend = new ArrayList<SurespotMessage>();
        toSend.add(message);
        SurespotApplication.getNetworkController().postMessages(toSend, new JsonHttpResponseHandler() {

            @Override
            public void onSuccess(JSONObject jsonObject) {
                try {
                    JSONArray messages = jsonObject.getJSONArray("messageStatus");
                    for (int n = 0; n < messages.length(); n++) {
                        JSONObject messageAndStatus = messages.getJSONObject(n);
                        JSONObject jsonMessage = messageAndStatus.getJSONObject("message");
                        // JSONObject jsonStatus = messageAndStatus.getJSONObject("status");
                        // status should be 204 - TODO: will it ever be the case that some messages return 204 and others do not?
                        SurespotMessage messageReceived = SurespotMessage.toSurespotMessage(jsonMessage);
                        // TODO: do we need to do more here?
                        SurespotApplication.getChatController().handleMessage(messageReceived);
                        mResendBuffer.remove(messageReceived);
                    }
                    mResendBuffer.remove(message);
                } catch (JSONException e) {
                    SurespotLog.w(TAG, e, "JSON received from server");
                }
            }

            @Override
            public void onSuccess(JSONArray jsonArray) {
                SurespotLog.w(TAG, "Unexpected condition - JSON array received from server");
            }

            @Override
            public void onSuccess(int statusCode, String content) {
                super.onSuccess(statusCode, content);
                // not sure this is a legit condition
                mResendBuffer.remove(message);
            }

            @Override
            public void onFailure(Throwable error, String content) {
                super.onFailure(error, content);
            }

            @Override
            public void onFailure(Throwable e, JSONObject errorResponse) {
                super.onFailure(e, errorResponse);
            }

            @Override
            public void onFailure(Throwable e, JSONArray errorResponse) {
                super.onFailure(e, errorResponse);
            }

        });
    }

    private void setOnWifi() {
        // get the initial state...sometimes when the app starts it says "hey i'm on wifi" which creates a reconnect
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null) {
            mOnWifi = (networkInfo.getType() == ConnectivityManager.TYPE_WIFI);
            SurespotLog.d(TAG, "setOnWifi, set mOnWifi to: %b", mOnWifi);
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
                disposeSocket();

                if (mListener != null) {
                    mListener.onReconnectFailed();
                }

                userLoggedOut();
                mTriesRelogin = 0;
            }
            else {
                startReloginTimer();
            }
        }
    }

    private boolean tryReLogin() {
        SurespotLog.d(TAG, "trying to relogin " + mUsername);
        return NetworkHelper.reLogin(CommunicationService.this, SurespotApplication.getNetworkController(), mUsername, new ReLoginCookieResponseHandler());
    }

    private class BroadcastReceiverHandler extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            SurespotLog.d(TAG, "onReceive");
            debugIntent(intent, TAG);
            Bundle extras = intent.getExtras();
            if (extras.containsKey("networkInfo")) {
                NetworkInfo networkInfo2 = (NetworkInfo) extras.get("networkInfo");
                if (networkInfo2.getState() == NetworkInfo.State.CONNECTED) {
                    SurespotLog.d(TAG, "onReceive,  CONNECTED");
                    synchronized (CommunicationService.this) {
                        boolean wasOnWifi = mOnWifi;
                        setOnWifi();


                        /*if (getConnectionState() == STATE_CONNECTED && mSocket != null && mSocket.isConnected()) {
                            SurespotLog.d(TAG, "onReceive, mSocket already connected doing nothing");
                            return;
                        }*/

                        //if we our wifi state changed reconnect
                        if (wasOnWifi != mOnWifi) {
                            SurespotLog.d(TAG, "onReceive, (re)connecting the mSocket");
                            disconnect();
                            connect();
                        }
                    }
                    return;
                }
            }
//            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
//            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
//            if (networkInfo != null) {
//                SurespotLog.d(TAG, "isconnected: " + networkInfo.isConnected());
//                SurespotLog.d(TAG, "failover: " + networkInfo.isFailover());
//                SurespotLog.d(TAG, "reason: " + networkInfo.getReason());
//                SurespotLog.d(TAG, "type: " + networkInfo.getTypeName());
//
//                // if it's not a failover and wifi is now active then initiate reconnect
//                if (!networkInfo.isFailover() && (networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected())) {
//                    synchronized (CommunicationService.this) {
//                        // if we're not connecting, connect
//                        if (getConnectionState() != STATE_CONNECTING && !mOnWifi) {
//                            mOnWifi = true;
//
//                            if (!mMainActivityPaused && mListener != null) {
//                                SurespotLog.d(TAG, "Network switch, Reconnecting...");
//
//                                setState(STATE_CONNECTING);
//
//                                disconnect();
//                                connect();
//                            }
//                        }
//                    }
//                }
//            } else {
//                SurespotLog.d(TAG, "networkinfo null");
//            }
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
        }
        else {
            Log.v(tag, "no extras");
        }
    }

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            SurespotLog.d(TAG, "mSocket.io connection established");
            setOnWifi();
            stopReconnectionAttempts();
            setState(STATE_CONNECTED);
            if (SurespotApplication.getChatController() != null) {
                SurespotApplication.getChatController().onResume(true);
            }
            onConnected();
        }
    };

    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            SurespotLog.d(TAG, "Connection terminated.");
            setState(STATE_DISCONNECTED);
            mRetries = 0;
        }
    };

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if (args.length > 0) {
                SurespotLog.d(TAG, "onConnectError: args: %s", args[0]);
            }
            boolean reAuthing = false;
            setState(STATE_DISCONNECTED);
            onNotConnected();


            if (args.length > 0) {
                if ("not authorized".equals(args[0])) {
                    SurespotLog.d(TAG, "got not authorized from websocket");

                    reAuthing = tryReLogin();

                    if (!reAuthing) {
                        disposeSocket();

                        if (mListener != null) {
                            mListener.onReconnectFailed();
                        }

                        //userLoggedOut();
                        return;
                    }
                }
            }

            if (reAuthing) {
                return;
            }

            SurespotLog.i(TAG, "an Error occured, attempting reconnect with exponential backoff, retries: %d", mRetries);

            setOnWifi();
            // kick off another task
            if ((!mMainActivityPaused && mRetries < MAX_RETRIES) || (mMainActivityPaused && mRetries < MAX_RETRIES_MAIN_ACTIVITY_PAUSED)) {
                scheduleReconnectionAttempt();
            }
            else {
                SurespotLog.i(TAG, "Socket.io reconnect retries exhausted, giving up.");

                if (mListener != null) {
                    mListener.onCouldNotConnectToServer();
                }

                // raise Android notifications for unsent messages so the user can re-enter the app and retry sending
                if (mMainActivityPaused && (!CommunicationService.this.mResendBuffer.isEmpty() || !CommunicationService.this.mSendBuffer.isEmpty())) {
                    raiseNotificationForUnsentMessages();
                }

                // TODO: is this appropriate to call?  I believe so, we make the user log in again anyway in this scenario
                //userLoggedOut();
            }
        }
    };
    private Emitter.Listener onMessageError = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    SurespotLog.d(TAG, "onMessageError, args: %s", args[0]);
                    try {
                        JSONObject jsonMessage = (JSONObject) args[0];
                        SurespotLog.d(TAG, "received messageError: " + jsonMessage.toString());
                        SurespotErrorMessage errorMessage = SurespotErrorMessage.toSurespotErrorMessage(jsonMessage);
                        SurespotMessage message = SurespotApplication.getChatController().handleErrorMessage(errorMessage);
                    }
                    catch (JSONException e) {
                        SurespotLog.w(TAG, "on messageError", e);
                    }
                }
            };

            mHandler.post(runnable);
        }

    };

    private Emitter.Listener onControl = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    SurespotLog.d(TAG, "onControl, args: %s", args[0]);

                    try {
                        SurespotControlMessage message = SurespotControlMessage.toSurespotControlMessage((JSONObject) args[0]);
                        SurespotApplication.getChatController().handleControlMessage(null, message, true, false);
                    }
                    catch (JSONException e) {
                        SurespotLog.w(TAG, "on control", e);
                    }
                }
            };
            mHandler.post(runnable);
        }


    };

    private Emitter.Listener onMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    SurespotLog.d(TAG, "onMessage, args: %s", args[0]);
                    // we need to be careful here about what is UI and what needs to be done to confirm receipt of sent message, error, etc
                    try {
                        JSONObject jsonMessage = (JSONObject) args[0];
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
                                    }
                                    catch (JSONException e) {
                                        SurespotLog.w(TAG, "on control", e);
                                    }
                                }
                            }
                        }

                        // the UI might have already removed the message from the resend buffer.  That's okay.
                        Iterator<SurespotMessage> iterator = mResendBuffer.iterator();
                        while (iterator.hasNext()) {
                            message = iterator.next();
                            if (message.getIv().equals(message.getIv())) {
                                iterator.remove();
                                break;
                            }
                        }

                        if (mMainActivityPaused) {
                            // make sure to save out messages because main activity will reload and base message status on saved messages
                            save();
                        }

                        checkAndSendNextMessage(message);
                    }
                    catch (JSONException e) {
                        SurespotLog.w(TAG, "on message", e);
                    }
                }
            };

            mHandler.post(runnable);
        }
    };
}

