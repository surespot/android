package com.twofours.surespot.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat.Builder;
import android.text.TextUtils;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatAdapter;
import com.twofours.surespot.chat.SurespotControlMessage;
import com.twofours.surespot.chat.SurespotErrorMessage;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.CookieResponseHandler;
import com.twofours.surespot.network.IAsyncCallbackTuple;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.network.NetworkHelper;
import com.twofours.surespot.ui.UIUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import ch.boye.httpclientandroidlib.HttpStatus;
import ch.boye.httpclientandroidlib.HttpVersion;
import ch.boye.httpclientandroidlib.StatusLine;
import ch.boye.httpclientandroidlib.cookie.Cookie;
import ch.boye.httpclientandroidlib.message.BasicStatusLine;
import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

@SuppressLint("NewApi")
public class ChatTransmissionService extends Service {
    private static final String TAG = "ChatTransmissionService";

    private NetworkController mNetworkController = null;

    private final IBinder mBinder = new ChatTransmissionServiceBinder();
    private ITransmissionServiceListener mListener;
    public ConcurrentLinkedQueue<SurespotMessage> mSendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();
    public ConcurrentLinkedQueue<SurespotMessage> mResendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();
    private BroadcastReceiver mConnectivityReceiver;
    private String mUsername;

    public static final int STATE_CONNECTING = 0;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_DISCONNECTED = 2;

    private static final int MAX_RETRIES = 60;
    // maximum time before reconnecting in seconds
    private static final int MAX_RETRY_DELAY = 30;

    public SocketIO socket;
    private int mRetries = 0;
    private Timer mBackgroundTimer;
    private Object BACKGROUND_TIMER_LOCK = new Object();

    private int mConnectionState;
    private boolean mOnWifi;

    private IOCallback mSocketCallback;


    @Override
    public void onCreate() {
        SurespotLog.i(TAG, "onCreate");

        Notification notification = null;

        // if we're < 4.3 then start foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // if this is the first time using the app don't use foreground service
            // TODO: have Adam explain what this does, why it's used
            boolean alreadyPrevented = Utils.getSharedPrefsBoolean(this, "firstTimePreventedForegroundServiceChatTransmission");
            if (alreadyPrevented) {
                // in 4.3 and above they decide to fuck us by showing the notification
                // so make the text meaningful at least
                PendingIntent contentIntent = PendingIntent.getActivity(this, SurespotConstants.IntentRequestCodes.BACKGROUND_CHAT_SERVICE_NOTIFICATION,
                        new Intent(this, MainActivity.class), 0);
                notification = UIUtils.generateNotification(new Builder(this), contentIntent, getPackageName(), R.drawable.surespot_logo_grey,
                        getString(R.string.caching_service_notification_title).toString(), "starting secure chat transmission service");
                notification.priority = Notification.PRIORITY_MIN;
            } else {
                Utils.putSharedPrefsBoolean(this, "firstTimePreventedForegroundServiceChatTransmission", true);
            }
        }
        else {
            notification = new Notification(0, null, System.currentTimeMillis());
            notification.flags |= Notification.FLAG_NO_CLEAR;
        }

        if (notification != null) {
            startForeground(SurespotConstants.IntentRequestCodes.FOREGROUND_NOTIFICATION, notification);
        }

        setOnWifi();

        mSocketCallback = new IOCallback() {

            @Override
            public void onMessage(JSONObject json, IOAcknowledge ack) {
                try {
                    SurespotLog.d(TAG, "JSON Server said: %s", json.toString(2));

                }
                catch (JSONException e) {
                    SurespotLog.w(TAG, "onMessage", e);
                }
            }

            @Override
            public void onMessage(String data, IOAcknowledge ack) {
                SurespotLog.d(TAG, "Server said: %s", data);
            }

            @Override
            public synchronized void onError(SocketIOException socketIOException) {
                boolean reAuthing = false;
                // socket.io returns 403 for can't login
                if (socketIOException.getHttpStatus() == 403) {
                    SurespotLog.d(TAG, "got 403 from websocket");

                    reAuthing = NetworkHelper.reLogin(ChatTransmissionService.this, mNetworkController, mUsername, new CookieResponseHandler() {

                        @Override
                        public void onSuccess(int responseCode, String result, Cookie cookie) {
                            connect();
                        }

                        @Override
                        public void onFailure(Throwable arg0, String content) {
                            // if we got http error bail
                            // if (arg0 instanceof HttpResponseException) {
                            // HttpResponseException error = (HttpResponseException) arg0;
                            // int statusCode = error.getStatusCode();
                            // SurespotLog.i(TAG, error, "http error on relogin - bailing, status: %d, message: %s", statusCode, error.getMessage());

                            socket = null;
                            // TODO: what is the appropriate behavior here?
                            if (mListener != null) {
                                mListener.reconnectFailed();
                            }
                            // what internal book-keeping needs to be done?
                            // WAS: logout();
                            // WAS: mCallback401.handleResponse(null, false);
                            return;
                            // }
                            //
                            // // if it's not an http error try again
                            // SurespotLog.i(TAG, arg0, "non http error on relogin - reconnecting, message: %s", arg0.getMessage());
                            // connect();
                        }
                    });

                    if (!reAuthing) {

                        socket = null;
                        // TODO: what is the appropriate behavior here?
                        if (mListener != null) {
                            mListener.reconnectFailed();
                        }
                        // what internal book-keeping needs to be done?
                        // WAS: logout();
                        // WAS: mCallback401.handleResponse(null, false);
                        return;
                    }
                }

                if (reAuthing)
                    return;

                SurespotLog.i(TAG, socketIOException, "an Error occured, attempting reconnect with exponential backoff, retries: %d", mRetries);

                setOnWifi();
                // kick off another task
                if (mRetries < MAX_RETRIES) {

                    int timerInterval = generateInterval(mRetries++);
                    SurespotLog.d(TAG, "try %d starting another task in: %d", mRetries - 1, timerInterval);

                    synchronized (BACKGROUND_TIMER_LOCK) {
                        if (mReconnectTask != null) {
                            mReconnectTask.cancel();
                        }

                        // TODO: Is there ever a case where we don't want to try a reconnect?
                        //if (!mPaused) {
                            ReconnectTask reconnectTask = new ReconnectTask();
                            if (mBackgroundTimer == null) {
                                mBackgroundTimer = new Timer("backgroundTimer");
                            }
                            mBackgroundTimer.schedule(reconnectTask, timerInterval);
                            mReconnectTask = reconnectTask;
                        //}
                    }
                }
                else {
                    SurespotLog.i(TAG, "Socket.io reconnect retries exhausted, giving up.");
                    if (mListener != null) {
                        mListener.couldNotConnectToServer();
                    }
                    // WAS: mCallback401.handleResponse(mContext.getString(R.string.could_not_connect_to_server), true);
                }
            }

            @Override
            public void onDisconnect() {
                SurespotLog.d(TAG, "Connection terminated.");
                // socket = null;
            }

            @Override
            public void onConnect() {
                SurespotLog.d(TAG, "socket.io connection established");

                setOnWifi();
                mRetries = 0;

                synchronized (BACKGROUND_TIMER_LOCK) {

                    if (mBackgroundTimer != null) {
                        mBackgroundTimer.cancel();
                        mBackgroundTimer = null;
                    }

                    if (mReconnectTask != null && mReconnectTask.cancel()) {
                        SurespotLog.d(TAG, "Cancelled reconnect timer.");
                        mReconnectTask = null;
                    }
                }
                connected();
                setState(STATE_CONNECTED);
            }

            @Override
            public void on(String event, IOAcknowledge ack, Object... args) {

                // these are all receipt of information.  No need to handle them, just pass them up to the listener
                // TODO: not sure this is totally true - handleErrorMessage(errorMessage); works with/modifies the resend buffer :P
                if (mListener != null) {
                    mListener.onEventReceived(event, ack, args);
                }

            }

        };

        mConnectivityReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                SurespotLog.d(TAG, "Connectivity Action");
                ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                if (networkInfo != null) {
                    SurespotLog.d(TAG, "isconnected: " + networkInfo.isConnected());
                    SurespotLog.d(TAG, "failover: " + networkInfo.isFailover());
                    SurespotLog.d(TAG, "reason: " + networkInfo.getReason());
                    SurespotLog.d(TAG, "type: " + networkInfo.getTypeName());

                    // if it's not a failover and wifi is now active then initiate reconnect
                    if (!networkInfo.isFailover() && (networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected())) {
                        synchronized (ChatTransmissionService.this) {
                            // if we're not connecting, connect
                            if (getState() != STATE_CONNECTING && !mOnWifi) {

                                SurespotLog.d(TAG, "Network switch, Reconnecting...");

                                setState(STATE_CONNECTING);

                                mOnWifi = true;
                                disconnect();
                                connect();
                            }
                        }
                    }
                }
                else {
                    SurespotLog.d(TAG, "networkinfo null");
                }
            }
        };
    }

    private void connected() {
        // tell any listeners that we're connected
        if (mListener != null) {
            mListener.connected();
        }
    }

    public void saveUnsentMessages() {
        mResendBuffer.addAll(mSendBuffer);
        // SurespotLog.d(TAG, "saving: " + mResendBuffer.size() + " unsent messages.");
        SurespotApplication.getStateController().saveUnsentMessages(mUsername, mResendBuffer);
    }

    public void loadUnsentMessages() {
        Iterator<SurespotMessage> iterator = SurespotApplication.getStateController().loadUnsentMessages(mUsername).iterator();
        while (iterator.hasNext()) {
            mResendBuffer.add(iterator.next());
        }
        // SurespotLog.d(TAG, "loaded: " + mSendBuffer.size() + " unsent messages.");
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

    public void shutdown() {
        disconnect();

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

        // socket = null;

        // workaround unchecked exception: https://code.google.com/p/android/issues/detail?id=18147
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
        // }
    }

    public void setUsername(String username) {
        mUsername = username;
    }

    public NetworkController getNetworkController() {
        return mNetworkController;
    }

    public void initNetworkController(String mUser, IAsyncCallbackTuple<String, Boolean> m401Handler) throws Exception {
        // TODO: HEREHERE: cleanup of existing network controller?  Is this where we might save off unsent messages to disk (for the previous user, etc)?
        mNetworkController = new NetworkController(this, mUser, m401Handler);
    }


    public int getState() {
        return mConnectionState;
    }

    private synchronized void setState(int state) {
        mConnectionState = state;
    }

    private ReconnectTask mReconnectTask;

    private class ReconnectTask extends TimerTask {

        @Override
        public void run() {
            SurespotLog.d(TAG, "Reconnect task run.");
            connect();
        }
    }

    public void connect() {
        SurespotLog.d(TAG, "connect, socket: " + socket + ", connected: " + (socket != null ? socket.isConnected() : false) + ", state: " + mConnectionState);

        // TODO: HEREHERE: ASK ADAM - chat adapters - we may not want this tight coupling with a UI element/adapter

        // copy the latest ids so that we don't miss any if we receive new messages during the time we request messages and when the
        // connection completes (if they
        // are received out of order for some reason)
        //
        /*mPreConnectIds.clear();
        for (Map.Entry<String, ChatAdapter> entry : mChatAdapters.entrySet()) {
            String username = entry.getKey();
            LatestIdPair idPair = new LatestIdPair();
            idPair.latestMessageId = getLatestMessageId(username);
            idPair.latestControlMessageId = getLatestMessageControlId(username);
            SurespotLog.d(TAG, "setting preconnectids for: " + username + ", latest message id:  " + idPair.latestMessageId + ", latestcontrolid: "
                    + idPair.latestControlMessageId);
            mPreConnectIds.put(username, idPair);
        }*/

        Cookie cookie = IdentityController.getCookieForUser(mUsername);

        try {
            HashMap<String, String> headers = new HashMap<String, String>();
            if (cookie != null) {
                headers.put("cookie", cookie.getName() + "=" + cookie.getValue());
            }
            socket = new SocketIO(SurespotConfiguration.getBaseUrl(), headers);
            socket.connect(mSocketCallback);
        }
        catch (Exception e) {

            SurespotLog.w(TAG, "connect", e);
        }

    }

    private void disconnect() {
        SurespotLog.d(TAG, "disconnect.");
        setState(STATE_DISCONNECTED);

        if (socket != null) {
            socket.disconnect();
            socket = null;
        }
    }

    public void checkAndSendNextMessage(SurespotMessage message) {
        sendMessages();

        if (mResendBuffer.size() > 0) {
            if (mResendBuffer.remove(message)) {
                SurespotLog.d(TAG, "Received and removed message from resend  buffer: " + message);
            }
        }
    }

    public SurespotMessage[] getResendMessages() {
        SurespotMessage[] messages = mResendBuffer.toArray(new SurespotMessage[0]);
        // mResendBuffer.clear();
        return messages;

    }

    public void enqueueMessage(SurespotMessage message) {
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
        while (iterator.hasNext()) {
            SurespotMessage message = iterator.next();
            if (isMessageReadyToSend(message)) {
                iterator.remove();
                sendMessage(message);
            }
        }
    }

    private boolean isMessageReadyToSend(SurespotMessage message) {
        return !TextUtils.isEmpty(message.getData()) && !TextUtils.isEmpty(message.getFromVersion()) && !TextUtils.isEmpty(message.getToVersion());
    }

    private void sendMessage(final SurespotMessage message) {
        SurespotLog.d(TAG, "sendmessage adding message to ResendBuffer, text: %s, iv: %s", message.getPlainData(), message.getIv());

        mResendBuffer.add(message);
        if (getState() == STATE_CONNECTED) {
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

    public class ChatTransmissionServiceBinder extends Binder {
        public ChatTransmissionService getService() {
            return ChatTransmissionService.this;
        }

        public void setServiceListener(ITransmissionServiceListener listener) {
            ChatTransmissionService.this.mListener = listener;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        SurespotLog.i(TAG, "onDestroy");
        shutdown();
    }

    public void initializeService() {
        SurespotLog.d(TAG, "initializeService: ", this.getClass().getSimpleName());
        this.registerReceiver(mConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }
}

