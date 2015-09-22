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
import android.util.Log;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.MainActivity;
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
import com.twofours.surespot.ui.UIUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import ch.boye.httpclientandroidlib.cookie.Cookie;
import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

@SuppressLint("NewApi")
public class ChatTransmissionService extends Service {
    private static final String TAG = "ChatTransmissionService";

    private final IBinder mBinder = new ChatTransmissionServiceBinder();
    private ITransmissionServiceListener mListener;
    private ConcurrentLinkedQueue<SurespotMessage> mSendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();
    private ConcurrentLinkedQueue<SurespotMessage> mResendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();
    private BroadcastReceiver mConnectivityReceiver;
    private String mUsername;
    private boolean mMainActivityPaused = false;
    private ReconnectTask mReconnectTask;
    private DisconnectTask mDisconnectTask;

    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_DISCONNECTED = 0;
    private static final int MAX_RETRIES = 60;
    // maximum time before reconnecting in seconds
    private static final int MAX_RETRY_DELAY = 30;

    private static final int DISCONNECT_DELAY_SECONDS = 60 * 3; // 3 minutes

    private SocketIO socket;
    private int mRetries = 0;
    private Timer mBackgroundTimer;
    private Object BACKGROUND_TIMER_LOCK = new Object();
    private Timer mDisconnectTimer;
    private Object DISCONNECT_TIMER_LOCK = new Object();
    private int mConnectionState;
    private boolean mOnWifi;
    private IOCallback mSocketCallback;

    @Override
    public void onCreate() {
        SurespotLog.i(TAG, "onCreate");

        /*
        Notification notification = null;

        // if we're < 4.3 then start foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // if this is the first time using the app don't use foreground service
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
        */

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

                    reAuthing = NetworkHelper.reLogin(ChatTransmissionService.this, SurespotApplication.getNetworkController(), mUsername, new CookieResponseHandler() {

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

                            if (mListener != null) {
                                mListener.reconnectFailed();
                            }

                            userLoggedOut();
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

                        if (mListener != null) {
                            mListener.reconnectFailed();
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
                    // TODO: is this appropriate to call?  I believe so, we make the user log in again anyway in this scenario
                    userLoggedOut();
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

                // we need to be careful here about what is UI and what needs to be done to confirm receipt of sent message, error, etc

                SurespotLog.d(TAG, "Server triggered event '" + event + "'");
                if (event.equals("control")) {
                    try {
                        SurespotControlMessage message = SurespotControlMessage.toSurespotControlMessage(new JSONObject((String) args[0]));
                        if (mListener != null) {
                            mListener.handleControlMessage(null, message, true, false);
                        }
                        // no need to do anything here - does not involve mResendBuffer, etc
                    }
                    catch (JSONException e) {
                        SurespotLog.w(TAG, "on control", e);
                    }
                }
                else
                if (event.equals("message")) {
                    try {
                        JSONObject jsonMessage = new JSONObject((String) args[0]);
                        SurespotLog.d(TAG, "received message: " + jsonMessage.toString());
                        SurespotMessage message = SurespotMessage.toSurespotMessage(jsonMessage);
                        if (mListener != null) {
                            mListener.handleMessage(message);
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
                    }
                    catch (JSONException e) {
                        SurespotLog.w(TAG, "on message", e);
                    }
                }
                else
                if (event.equals("messageError")) {
                    try {
                        JSONObject jsonMessage = (JSONObject) args[0];
                        SurespotLog.d(TAG, "received messageError: " + jsonMessage.toString());
                        SurespotErrorMessage errorMessage = SurespotErrorMessage.toSurespotErrorMessage(jsonMessage);
                        if (mListener != null) {
                            mListener.handleErrorMessage(errorMessage);
                        }

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
                    }
                    catch (JSONException e) {
                        SurespotLog.w(TAG, "on messageError", e);
                    }
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

    public void setMainActivityPaused(boolean paused) {
        mMainActivityPaused = paused;
        checkDisconnect();
        checkReconnect();
    }

    private void checkDisconnect() {
        if (mMainActivityPaused && mSendBuffer.size() == 0) {
            // setup a disconnect N minutes from now
            synchronized (DISCONNECT_TIMER_LOCK) {
                if (mDisconnectTask != null) {
                    mDisconnectTask.cancel();
                }

                DisconnectTask disconnectTask = new DisconnectTask();
                if (mDisconnectTimer == null) {
                    mDisconnectTimer = new Timer("disconnectTimer");
                }
                mDisconnectTimer.schedule(disconnectTask, DISCONNECT_DELAY_SECONDS * 1000);
                mDisconnectTask = disconnectTask;
            }
        }
    }

    private void checkReconnect() {
        if (!mMainActivityPaused) {
            if (getState() == STATE_DISCONNECTED) {
                connect();
            }
        }
    }

    private void connected() {
        // tell any listeners that we're connected
        if (mListener != null) {
            mListener.connected();
        }
    }

    // Notify the service that the user logged out
    public void userLoggedOut() {
        if (mUsername != null) {
            saveUnsentMessages();
            mResendBuffer.clear();
            mSendBuffer.clear();
            mUsername = null;
            shutdownConnection();
            checkShutdownService();
        }
    }

    public SurespotMessage[] getResendMessages() {
        SurespotMessage[] messages = mResendBuffer.toArray(new SurespotMessage[0]);
        List<SurespotMessage> list = Arrays.asList(messages);
        removeDuplicates(list);
        // mResendBuffer.clear();
        return list.toArray(new SurespotMessage[0]);
    }

    private List<SurespotMessage> removeDuplicates(List<SurespotMessage> messages) {
        ArrayList<SurespotMessage> messagesSeen = new ArrayList<SurespotMessage>();
        for (int i = messages.size()-1; i >= 0; i--) {
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

    public void postFileStream(final String ourVersion, final String user, final String theirVersion, final String id,
                                      final InputStream fileInputStream, final String mimeType, final IAsyncCallback<Integer> callback) {
        SurespotApplication.getNetworkController().postFileStream(ourVersion, user, theirVersion, id, fileInputStream, mimeType, callback);
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

    public void shutdownConnection() {
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
        unregisterReceiver();

        checkShutdownService();
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

    private void checkShutdownService() {
        if (mSendBuffer.size() == 0 && mListener == null) {
            Log.d(TAG, "shutting down service!");
            this.stopSelf();
        }
    }

    public void setUsername(String username) {
        mUsername = username;
    }

    public void initNetworkController(String mUser, IAsyncCallbackTuple<String, Boolean> m401Handler) throws Exception {
        setUsername(mUser);
        SurespotApplication.setNetworkController(new NetworkController(this, mUser, m401Handler));
    }

    public int getState() {
        return mConnectionState;
    }

    private synchronized void setState(int state) {
        mConnectionState = state;
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

    public void connect() {
        SurespotLog.d(TAG, "connect, socket: " + socket + ", connected: " + (socket != null ? socket.isConnected() : false) + ", state: " + mConnectionState);

        cancelDisconnectTimer();

        if (mConnectionState == STATE_CONNECTED && socket != null && socket.isConnected())
        {
            return;
        }

        // gives the UI a chance to copy out pre-connect ids
        if (mListener != null) {
            mListener.onBeforeConnect();
        }

        if (mConnectionState == STATE_CONNECTED || mConnectionState == STATE_CONNECTING)
        {
            connected();
            return;
        }

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
        cancelDisconnectTimer();

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
            mResendBuffer.remove(message);
        }
    }

    public void enqueueMessage(SurespotMessage message) {
        if (getState() == STATE_DISCONNECTED) {
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

        checkDisconnect();
        checkShutdownService();

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

    public void clearServiceListener() {
        mListener = null;
        checkShutdownService();
    }

    public class ChatTransmissionServiceBinder extends Binder {
        public ChatTransmissionService getService() {
            return ChatTransmissionService.this;
        }
    }

    public void setServiceListener(ITransmissionServiceListener listener) {
        mListener = listener;
        checkShutdownService();
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
        userLoggedOut();
    }

    public void initializeService() {
        if (mConnectivityReceiver != null) {
            unregisterReceiver();
        }
        SurespotLog.d(TAG, "initializeService: ", this.getClass().getSimpleName());
        this.registerReceiver(mConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }
}

