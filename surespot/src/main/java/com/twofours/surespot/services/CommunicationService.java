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
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.TextUtils;
import android.util.Log;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.Tuple;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatAdapter;
import com.twofours.surespot.chat.ChatController;
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
import com.twofours.surespot.network.MainThreadCallbackWrapper;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.network.NetworkHelper;

import org.apache.http.conn.ssl.SSLSocketFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.Transport;
import io.socket.engineio.client.transports.WebSocket;
import okhttp3.Call;
import okhttp3.Cookie;
import okhttp3.Response;


@SuppressLint("NewApi")
public class CommunicationService extends Service {
    private static final String TAG = "CommunicationService";

    private final IBinder mBinder = new CommunicationServiceBinder();
    private ITransmissionServiceListener mListener;
    private ConcurrentLinkedQueue<SurespotMessage> mSendQueue = new ConcurrentLinkedQueue<SurespotMessage>();

    private BroadcastReceiver mConnectivityReceiver;
    private String mUsername;
    private static boolean mMainActivityPaused = false;

    private ReconnectTask mReconnectTask;
    public static String mCurrentChat;
    Handler mHandler = new Handler(Looper.getMainLooper());

    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_DISCONNECTED = 0;
    private static final int MAX_RETRIES_SEND_VIA_HTTP = 30;
    private static final int MAX_RETRIES_MAIN_ACTIVITY_PAUSED = 20;
    private static final int MAX_RETRIES = 60;

    // maximum time before reconnecting in seconds
    private static final int MAX_RETRY_DELAY = 10;

    private int mResendTries = 0;
    private Socket mSocket;
    private int mSocketReconnectRetries = 0;
    private Timer mResendViaHttpTimer;
    private Object SEND_LOCK = new Object();
    private Timer mBackgroundTimer;
    private Object BACKGROUND_TIMER_LOCK = new Object();
    private int mConnectionState;
    private boolean mOnWifi;
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mBuilder;
    private String mCurrentSendIv;
    private ProcessNextMessageTask mResendTask;


    @Override
    public void onCreate() {
        SurespotLog.i(TAG, "onCreate");
        setOnWifi();
        mConnectivityReceiver = new BroadcastReceiverHandler();
        resetState();
        mNotificationManager = (NotificationManager) CommunicationService.this.getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(CommunicationService.this);

        loadUnsentMessages();
    }

    private void resetState() {
        mSocketReconnectRetries = 0;
        mResendTries = 0;

    }

    private synchronized void disposeSocket() {
        SurespotLog.d(TAG, "disposeSocket");
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
        SurespotLog.d(TAG, "createSocket, mSocket == null: %b", mSocket == null);
        if (mSocket == null) {
            IO.Options opts = new IO.Options();
            //TODO
            // opts.sslContext = WebClientDevWrapper.getSSLContext();
            opts.secure = true;
            opts.reconnection = false;
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
                    transport.on(Transport.EVENT_REQUEST_HEADERS, new Emitter.Listener() {
                        @Override
                        public void call(Object... args) {
                            SurespotLog.d(TAG, "socket.io EVENT_REQUEST_HEADERS");
                            @SuppressWarnings("unchecked")
                            Map<String, List> headers = (Map<String, List>) args[0];
                            // set header
                            Cookie cookie = IdentityController.getCookieForUser(mUsername);
                            if (cookie != null) {
                                ArrayList<String> cookies = new ArrayList<String>();
                                cookies.add(cookie.name() + "=" + cookie.value());
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

            save();
            disconnect();
        }
        else {
            connect();
            // changed with not keeping socket open: cancelDisconnectTimer();
            // changed with not keeping socket open: cancelGiveUpReconnectingTimer();
        }
    }


    // Notify the service that the user logged out
    public void userLoggedOut() {
        if (mUsername != null) {
            SurespotLog.d(TAG, "user logging out: " + mUsername);

            save();
            mUsername = null;
            shutdownConnection();
            mSendQueue.clear();
            SurespotApplication.getChatController().dispose();
        }
    }

    public synchronized boolean connect(String username) {
        if (!username.equals(mUsername)) {
            SurespotLog.d(TAG, "Setting user name to " + username + " and connecting");
            //don't need to disconnect 1st time through when mUsername will be null
            if (mUsername != null) {
                disconnect();
            }
            mUsername = username;
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
            onConnected();
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
            SurespotLog.w(TAG, e, "connect");
        }

        return false;
    }

    public void enqueueMessage(SurespotMessage message) {
        if (getConnectionState() == STATE_DISCONNECTED) {
            connect();
        }

        if (!mSendQueue.contains(message)) {
            mSendQueue.add(message);
        }
    }

    public synchronized void processNextMessage() {

//        synchronized (BACKGROUND_TIMER_LOCK) {
//            if (mBackgroundTimer == null) {
//                mBackgroundTimer = new Timer("backgroundTimer");
//            }
//        }
        synchronized (SEND_LOCK) {


            SurespotLog.d(TAG, "processNextMessage, messages in queue: %d", mSendQueue.size());


            SurespotMessage nextMessage = mSendQueue.peek();
            if (nextMessage != null) {

                if (mCurrentSendIv == nextMessage.getIv()) {
                    if (nextMessage.getId() != null) {
                        SurespotLog.i(TAG, "processNextMessage() still sending message, iv: %s", nextMessage.getIv());
                        //set the resend id


                        if (SurespotApplication.getChatController() != null) {

                            // set the last received id so the server knows which messages to check
                            String otherUser = nextMessage.getOtherUser();

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
                            nextMessage.setResendId(lastMessageID);

                        }

                    }


                }


                //message processed successfully, onto the next
                SurespotLog.i(TAG, "processNextMessage() sending message, iv: %s", nextMessage.getIv());
                mCurrentSendIv = nextMessage.getIv();
                switch (nextMessage.getMimeType()) {
                    case SurespotConstants.MimeTypes.TEXT:
                        if (isMessageReadyToSend(nextMessage)) {
                            sendTextMessage(nextMessage);
                        }
                        else {
                            //start timer and try in a bit
                        }
                        break;
                    case SurespotConstants.MimeTypes.IMAGE:
                    case SurespotConstants.MimeTypes.M4A:
                        sendImageMessage(nextMessage);

                        break;
                }
//


            }
        }

    }

    private void sendTextMessage(final SurespotMessage message) {
        SurespotLog.d(TAG, "sendTextMessage, iv: %s", message.getIv());
        if (mMainActivityPaused) {
            sendMessageUsingHttp(message);
        }
        else {
            if (getConnectionState() == STATE_CONNECTED) {
                SurespotLog.d(TAG, "sendTextMessage, mSocket: %s", mSocket);
                JSONObject json = message.toJSONObjectSocket();
                SurespotLog.d(TAG, "sendTextMessage, json: %s", json);
                //String s = json.toString();
                //SurespotLog.d(TAG, "sendmessage, message string: %s", s);

                if (mSocket != null) {
                    mSocket.send(json);
                }
            }
            else {
                //not connected, clear current iv so queue can proceed

                mCurrentSendIv = null;

            }
        }
    }

    private void sendImageMessage(final SurespotMessage message) {
        SurespotLog.d(TAG, "sendImageMessage, iv: %s", message.getIv());
        new AsyncTask<Void, Void, Tuple<Integer, JSONObject>>() {
            @Override
            protected Tuple<Integer, JSONObject> doInBackground(Void... voids) {
                //post message via http if we have network controller for the from user
                NetworkController networkController = SurespotApplication.getNetworkController();
                if (networkController != null && message.getFrom().equals(networkController.getUsername())) {

                    FileInputStream uploadStream;
                    try {
                        uploadStream = new FileInputStream(URI.create(message.getData()).getPath());
                        return networkController.postFileStreamSync(IdentityController.getOurLatestVersion(message.getFrom()), message.getTo(), IdentityController.getTheirLatestVersion(message.getTo()),
                                message.getIv(), uploadStream, message.getMimeType());

                    }
                    catch (FileNotFoundException e) {
                        SurespotLog.w(TAG, e, "sendImageMessage");
                        return new Tuple<>(500, null);
                    }
                    catch (JSONException e) {
                        SurespotLog.w(TAG, e, "sendImageMessage");
                        return new Tuple<>(500, null);
                    }


                }
                else {
                    SurespotLog.i(TAG, "network controller null or different user");
                    return new Tuple<>(500, null);
                }
            }

            @Override
            protected void onPostExecute(Tuple<Integer, JSONObject> result) {
                //if message errored
                int status = result.first;
                if (status != 200) {
                    //if we've hit retry limit stop retrying and set messages errored
                    SurespotLog.d(TAG, "sendImageMessage error status: %d, mResendTries: %d, MAX_RETRIES: %d", status, mResendTries, MAX_RETRIES_SEND_VIA_HTTP);
                    if (mResendTries++ >= MAX_RETRIES_SEND_VIA_HTTP) {
                        //TODO set all messages in queue errored
                        message.setErrorStatus(status);
                        ChatController chatController = SurespotApplication.getChatController();
                        if (chatController != null) {
                            ChatAdapter chatAdapter = chatController.getChatAdapter(message.getTo(), false);
                            if (chatAdapter != null) {
                                SurespotMessage adapterMessage = chatAdapter.getMessageByIv(message.getIv());
                                if (adapterMessage != null) {
                                    adapterMessage.setErrorStatus(status);
                                    chatAdapter.notifyDataSetChanged();
                                }
                            }
                        }
                    }
                    else {
                        //try and send next message again
                        scheduleResendTimer();
                    }
                }
                else {
                    mResendTries = 0;
                    mSendQueue.remove(message);
                    //clear current iv so queue can proceed
                    synchronized (SEND_LOCK) {
                        mCurrentSendIv = null;
                    }

                    //update local message with server data
                    SurespotLog.d(TAG, "sendImageMessage received response: %s", result.second);

                    JSONObject serverData = result.second;
                    try {
                        int serverId = serverData.getInt("id");
                        //   String url = serverData.getString("url");
                        long datetime = serverData.getLong("time");
                        int size = serverData.getInt("size");

                        //create a new message purely to update cached file location
//                        SurespotMessage newMessage = new SurespotMessage();
//                        newMessage.setIv(message.getIv());
//                        newMessage.setData(url);
//                        newMessage.setDateTime(new Date(datetime));
//                        newMessage.setId(serverId);
//                        newMessage.setDataSize(size);
//                        newMessage.setMimeType(message.getMimeType());

                        //set server data in original message
//                        message.setData(url);
//                        message.setDateTime(new Date(datetime));
//                        message.setId(serverId);
//                        message.setDataSize(size);

//                        ChatController chatController = SurespotApplication.getChatController();
//                        if (chatController != null) {
//                            ChatAdapter chatAdapter = chatController.getChatAdapter(message.getTo(), false);
//                            if (chatAdapter != null) {
//                                SurespotMessage adapterMessage = chatAdapter.getMessageByIv(message.getIv());
//                                if (adapterMessage != null) {
//
//                                    chatController.handleCachedFile(chatAdapter, message);
//                                    //set server data in adapter message
//                                    adapterMessage.setDateTime(new Date(datetime));
//                                    adapterMessage.setId(serverId);
//                                    adapterMessage.setDataSize(size);
//                                    chatAdapter.notifyDataSetChanged();
//                                }
//                            }
//                        }


                    }
                    catch (JSONException e) {
                        //TODO notification
                    }


                    processNextMessage();
                }
            }
        }.execute();


    }

    private void sendMessageUsingHttp(final SurespotMessage message) {
        ArrayList<SurespotMessage> toSend = new ArrayList<SurespotMessage>();
        toSend.add(message);
        SurespotApplication.getNetworkController().postMessages(toSend, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {

            @Override
            public void onFailure(Call call, IOException e) {
                SurespotLog.w(TAG, e, "sendMessagesUsingHttp onFailure");
            }

            @Override
            public void onResponse(Call call, Response response, String responseString) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(responseString);
                        JSONArray messages = json.getJSONArray("messageStatus");
                        JSONObject messageAndStatus = messages.getJSONObject(0);
                        JSONObject jsonMessage = messageAndStatus.getJSONObject("message");
                        int status = messageAndStatus.getInt("status");

                        if (status == 204) {
                            SurespotMessage messageReceived = SurespotMessage.toSurespotMessage(jsonMessage);
                            mSendQueue.remove(messageReceived);
                            SurespotApplication.getChatController().handleMessage(messageReceived);
                            processNextMessage();
                        }
                        else {
                            //if we've hit retry limit stop retrying and set messages errored
                            if (mResendTries++ >= MAX_RETRIES_SEND_VIA_HTTP) {
                                //TODO set all messages in queue errored
                                message.setErrorStatus(status);
                                ChatController chatController = SurespotApplication.getChatController();
                                if (chatController != null) {
                                    ChatAdapter chatAdapter = chatController.getChatAdapter(message.getTo(), false);
                                    if (chatAdapter != null) {
                                        SurespotMessage adapterMessage = chatAdapter.getMessageByIv(message.getIv());
                                        if (adapterMessage != null) {
                                            adapterMessage.setErrorStatus(status);
                                            chatAdapter.notifyDataSetChanged();
                                        }
                                    }
                                }
                            }
                            else {
                                //try and send next message again
                                scheduleResendTimer();
                            }
                        }
                    }
                    catch (JSONException e) {
                        SurespotLog.w(TAG, e, "JSON received from server");
                    }

                }
                else {
                    SurespotLog.w(TAG, "sendMessagesUsingHttp response error code: %d", response.code());
                }
            }


        }));
    }


    public int getConnectionState() {
        return mConnectionState;
    }

    public ConcurrentLinkedQueue<SurespotMessage> getSendQueue() {
        return mSendQueue;
    }


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

    public boolean isConnected() {
        return getConnectionState() == CommunicationService.STATE_CONNECTED;
    }

    public void resendMessage(SurespotMessage message) {
        // user wants to resend the message.  Retry connection if we have given up/are not connected.
        // If message is in resend buffer, simply retry connection and it will be sent once connected.
        // If message is not in resend buffer, then simply attempt to send the message again

        if (getConnectionState() == STATE_DISCONNECTED) {
            mSocketReconnectRetries = 0; // start over in terms of retries (?)
            connect();
        }

        if (mSendQueue.contains(message)) {
            // do nothing - message will transmit when we reconnect
        }
        else {
            // resend the message
            //  sendMessage(message);
        }
    }


    public void clearMessageQueue(String friendname) {
        Iterator<SurespotMessage> iterator = mSendQueue.iterator();
        while (iterator.hasNext()) {
            SurespotMessage message = iterator.next();
            if (message.getTo().equals(friendname)) {
                iterator.remove();
            }
        }
    }

    public void removeQueuedMessage(SurespotMessage message) {
        synchronized (SEND_LOCK) {
            mSendQueue.remove(message);
        }
    }


    public class CommunicationServiceBinder extends Binder {
        public CommunicationService getService() {
            return CommunicationService.this;
        }
    }

    public void setServiceListener(ITransmissionServiceListener listener) {
        mListener = listener;
        if (mListener != null) {
            mSocketReconnectRetries = 0; // clear state related to retrying connections
        }
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


    public void saveUnsentMessages() {
        SurespotLog.d(TAG, "saving: " + mSendQueue.size() + " unsent messages.");
        SurespotApplication.getStateController().saveUnsentMessages(mUsername, mSendQueue);
    }

    private void loadUnsentMessages() {
        Iterator<SurespotMessage> iterator = SurespotApplication.getStateController().loadUnsentMessages(mUsername).iterator();
        while (iterator.hasNext()) {
            SurespotMessage message = iterator.next();

            if (!mSendQueue.contains(message)) {
                mSendQueue.add(message);
            }
        }
        SurespotLog.d(TAG, "loaded: " + mSendQueue.size() + " unsent messages.");
    }

    private void saveFriends() {
        if (SurespotApplication.getChatController().getFriendAdapter() != null && SurespotApplication.getChatController().getFriendAdapter().getCount() > 0) {
            SurespotApplication.getChatController().saveFriends();
        }
    }

    // notify listeners that we've connected
    private void onConnected() {
        SurespotLog.d(TAG, "onConnected");

        stopReconnectionAttempts();
        stopResendTimer();

        //tell chat controller


        // tell any listeners that we're connected
        if (mListener != null) {
            SurespotLog.d(TAG, "onConnected, mListener calling onConnected()");
            mListener.onConnected();
        }
        else {
            SurespotLog.d(TAG, "onConnected, mListener was null");
        }



        processNextMessage();
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

    private void scheduleReconnectionAttempt() {
        int timerInterval = generateInterval(mSocketReconnectRetries++);
        SurespotLog.d(TAG, "reconnection timer try %d starting another task in: %d", mSocketReconnectRetries - 1, timerInterval);

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

    private void scheduleResendTimer() {
        int timerInterval = generateInterval(mResendTries++);
        SurespotLog.d(TAG, "resend timer try %d starting another task in: %d", mResendTries - 1, timerInterval);

        synchronized (SEND_LOCK) {
            if (mResendTask != null) {
                mResendTask.cancel();
                mResendTask = null;
            }


            if (mResendViaHttpTimer != null) {
                mResendViaHttpTimer.cancel();
                mResendViaHttpTimer = null;
            }


            // Is there ever a case where we don't want to try a reconnect?
            ProcessNextMessageTask reconnectTask = new ProcessNextMessageTask();
            mResendViaHttpTimer = new Timer("processNextMessageTimer");
            mResendViaHttpTimer.schedule(reconnectTask, timerInterval);
            mResendTask = reconnectTask;
        }
    }

    private void stopResendTimer() {
        synchronized (BACKGROUND_TIMER_LOCK) {
            if (mResendViaHttpTimer != null) {
                mResendViaHttpTimer.cancel();
                mResendViaHttpTimer = null;
            }
            if (mResendTask != null) {
                boolean cancel = mResendTask.cancel();
                mResendTask = null;
                SurespotLog.d(TAG, "Cancelled resend task: " + cancel);
            }
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

    private synchronized void setState(int state) {
        mConnectionState = state;
        if (state == STATE_CONNECTED) {
            mSocketReconnectRetries = 0;
        }
    }

    private class ReconnectTask extends TimerTask {

        @Override
        public void run() {
            SurespotLog.d(TAG, "Reconnect task run.");
            connect();
        }
    }

    private class ProcessNextMessageTask extends TimerTask {

        @Override
        public void run() {
            SurespotLog.d(TAG, "ProcessNextMessage task run.");
            processNextMessage();
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


    private void disconnect() {

//        if (SurespotApplication.getChatController() != null) {
//            SurespotApplication.getChatController().onPause();
//        } else {
        save();
        //}

        SurespotLog.d(TAG, "disconnect.");
        setState(STATE_DISCONNECTED);

        if (mSocket != null) {
            mSocket.disconnect();
            disposeSocket();
        }


        processNextMessage();

    }

    private boolean isMessageReadyToSend(SurespotMessage message) {
        return !TextUtils.isEmpty(message.getData()) && !TextUtils.isEmpty(message.getFromVersion()) && !TextUtils.isEmpty(message.getToVersion());
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



    private void tryReLogin() {
        SurespotLog.d(TAG, "trying to relogin " + mUsername);
        NetworkHelper.reLogin(CommunicationService.this, SurespotApplication.getNetworkController(), mUsername, new CookieResponseHandler() {
            private String TAG = "ReLoginCookieResponseHandler";

            @Override
            public void onSuccess(int responseCode, String result, Cookie cookie) {
                //try again
                disposeSocket();
                connect();
            }

            @Override
            public void onFailure(Throwable arg0, int code, String content) {


                //if we're getting 401 bail
                if (code == 401) {
                    // give up

                    disposeSocket();

                    if (mListener != null) {

                        SurespotLog.i(TAG, "401 on reconnect, giving up.");
                        mListener.on401();

                    }

                    userLoggedOut();
                }
                else {
                    //try and connect again
                    disposeSocket();
                    connect();
                }
            }
        });
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

                        //if our wifi state changed reconnect
                        if (wasOnWifi != mOnWifi) {
                            SurespotLog.d(TAG, "onReceive, (re)connecting the mSocket");
                            disconnect();
                            connect();
                        }

                        processNextMessage();
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
            mSocketReconnectRetries = 0;
        }
    };

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if (args.length > 0) {
                SurespotLog.d(TAG, "onConnectError: args: %s", args[0]);
            }

            setState(STATE_DISCONNECTED);
            onNotConnected();


            if (args.length > 0) {
                if ("not authorized".equals(args[0])) {
                    SurespotLog.d(TAG, "got not authorized from websocket");
                    disposeSocket();
                    tryReLogin();
                    return;
                }
            }



            SurespotLog.i(TAG, "an Error occured, attempting reconnect with exponential backoff, retries: %d", mSocketReconnectRetries);

            setOnWifi();
            // kick off another task
            if ((!mMainActivityPaused && mSocketReconnectRetries < MAX_RETRIES) || (mMainActivityPaused && mSocketReconnectRetries < MAX_RETRIES_MAIN_ACTIVITY_PAUSED)) {
                scheduleReconnectionAttempt();
            }
            else {
                SurespotLog.i(TAG, "Socket.io reconnect retries exhausted, giving up.");

                if (mListener != null) {
                    mListener.onCouldNotConnectToServer();
                }

                // raise Android notifications for unsent messages so the user can re-enter the app and retry sending
                if (mMainActivityPaused && !CommunicationService.this.mSendQueue.isEmpty()) {
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
                        Iterator<SurespotMessage> iterator = mSendQueue.iterator();
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


                    }
                    catch (JSONException e) {
                        SurespotLog.w(TAG, "on message", e);
                    }
                    processNextMessage();
                }
            };

            mHandler.post(runnable);
        }
    };

    public static boolean isUIAttached() {
        return !mMainActivityPaused;
    }

}

