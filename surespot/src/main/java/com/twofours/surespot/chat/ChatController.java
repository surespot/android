package com.twofours.surespot.chat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.view.ViewPager;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SeekBar;

import com.rockerhieu.emojicon.EmojiconHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.StateController;
import com.twofours.surespot.StateController.FriendState;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConfiguration;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.Tuple;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.backup.DriveHelper;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.filetransfer.FileTransferUtils;
import com.twofours.surespot.friends.AutoInviteData;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.friends.FriendAdapter;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.FileCacheController;
import com.twofours.surespot.images.MessageImageDownloader;
import com.twofours.surespot.network.CookieResponseHandler;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.MainThreadCallbackWrapper;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.network.NetworkHelper;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.utils.Utils;
import com.twofours.surespot.voice.VoiceController;
import com.viewpagerindicator.TitlePageIndicator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.EngineIOException;
import io.socket.engineio.client.Transport;
import io.socket.engineio.client.transports.WebSocket;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Cookie;
import okhttp3.Response;

@SuppressLint("StaticFieldLeak")
public class ChatController {

    private static final String TAG = "ChatController";

    private HashMap<String, ChatAdapter> mChatAdapters;
    private HashMap<String, Integer> mEarliestMessage;

    private FriendAdapter mFriendAdapter;
    private ChatPagerAdapter mChatPagerAdapter;
    private ViewPager mViewPager;
    private TitlePageIndicator mIndicator;
    private FragmentManager mFragmentManager;
    private int mLatestUserControlId;
    private ArrayList<MenuItem> mMenuItems;
    private HashMap<String, LatestIdPair> mPreConnectIds;

    private NetworkController mNetworkController;

    private Activity mContext;
    public static final int MODE_NORMAL = 0;
    public static final int MODE_SELECT = 1;

    private int mMode = MODE_NORMAL;

    private IAsyncCallback<Boolean> mProgressCallback;
    private IAsyncCallback<Void> mSendIntentCallback;
    private IAsyncCallback<Friend> mTabShowingCallback;
    private IAsyncCallback<Object> m401Handler;
    private AutoInviteData mAutoInviteData;

    private String mCurrentChat;

    private ConcurrentLinkedQueue<SurespotMessage> mSendQueue = new ConcurrentLinkedQueue<SurespotMessage>();

    private String mUsername;
    private boolean mMainActivityPaused = false;

    private ReconnectTask mReconnectTask;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private static final int STATE_CONNECTING = 2;
    private static final int STATE_CONNECTED = 1;
    private static final int STATE_DISCONNECTED = 0;
    private static final int MAX_RETRIES = 60;

    // maximum time before reconnecting in seconds
    private static final int MAX_RETRY_DELAY = 10;

    private int mHttpResendTries = 0;
    private Socket mSocket;
    private int mSocketReconnectRetries = 0;
    private Timer mResendViaHttpTimer;
    private Timer mBackgroundTimer;
    private int mConnectionState;
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mBuilder;
    private String mCurrentSendIv;
    private ProcessNextMessageTask mResendTask;
    private boolean mErrored;

    private DriveHelper mDriveHelper;

    ChatController(Activity context, String username) {
        SurespotLog.d(TAG, "constructor, username: %s", username);

        mContext = context;
        mUsername = username;
        mNetworkController = NetworkManager.getNetworkController(context, mUsername);
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(mContext);
        mDriveHelper = new DriveHelper(context, true);

        mEarliestMessage = new HashMap<String, Integer>();
        mChatAdapters = new HashMap<String, ChatAdapter>();
        mPreConnectIds = new HashMap<String, ChatController.LatestIdPair>();

        // mViewPager.setOffscreenPageLimit(2);
    }

    // this has to be done outside of the contructor as it creates fragments, which need chat controller instance
    void attach(
            Activity context,
            ViewPager viewPager,
            FragmentManager fm,
            TitlePageIndicator pageIndicator, ArrayList<MenuItem> menuItems,
            IAsyncCallback<Boolean> progressCallback,
            IAsyncCallback<Void> sendIntentCallback,
            IAsyncCallback<Friend> tabShowingCallback,
            IAsyncCallback<Object> four01handler) {
        SurespotLog.d(TAG, "attach, username: %s", mUsername);
        mFragmentManager = fm;
        mContext = context;
        mProgressCallback = progressCallback;
        mSendIntentCallback = sendIntentCallback;
        mTabShowingCallback = tabShowingCallback;
        m401Handler = four01handler;
        mChatPagerAdapter = new ChatPagerAdapter(mContext, mFragmentManager, mUsername);
        mMenuItems = menuItems;

        mViewPager = viewPager;
        mViewPager.setAdapter(mChatPagerAdapter);
        mIndicator = pageIndicator;
        mIndicator.setViewPager(mViewPager);

        mIndicator.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (mChatPagerAdapter != null) {
                    SurespotLog.d(TAG, "onPageSelected, position: " + position);
                    String name = mChatPagerAdapter.getChatName(position);
                    setCurrentChat(name);
                }
            }
        });


        mFriendAdapter = new FriendAdapter(mContext, mUsername);
        loadFriendState();

        mChatPagerAdapter.setChatFriends(mFriendAdapter.getActiveChatFriends());
        mFriendAdapter.registerFriendAliasChangedCallback(new IAsyncCallback<Void>() {

            @Override
            public void handleResponse(Void result) {
                mChatPagerAdapter.sort();
                mChatPagerAdapter.notifyDataSetChanged();
                mIndicator.notifyDataSetChanged();
            }
        });
    }

    public void setAutoInviteData(AutoInviteData autoInviteData) {
        mAutoInviteData = autoInviteData;
        if (getState() == STATE_CONNECTED) {
            handleAutoInvite();
        }
    }

    private int getState() {
        return getConnectionState();
    }

    // this is wired up to listen for a message from the   It's UI stuff
    public void connected() {
        setProgress("connect", false);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                getFriendsAndData();
            }
        });
    }

    private void handleAutoInvite() {

        // if we need to invite someone then do it
        if (mAutoInviteData != null) {
            if (mFriendAdapter.getFriend(mAutoInviteData.getUsername()) == null) {
                SurespotLog.d(TAG, "auto inviting user: %s", mAutoInviteData.getUsername());
                mNetworkController.invite(mAutoInviteData.getUsername(), mAutoInviteData.getSource(), new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        //TODO handle?

                    }

                    @Override
                    public void onResponse(Call call, Response response, String responseString) throws IOException {
                        if (response.isSuccessful()) {
                            getFriendAdapter().addFriendInvited(mAutoInviteData.getUsername());
                            // scroll to home page
                            setCurrentChat(null);
                            mAutoInviteData = null;
                        }
                    }

                }));
            }
            else {
                Utils.makeToast(mContext, mContext.getString(R.string.autoinvite_user_exists, mAutoInviteData.getUsername()));
                mAutoInviteData = null;
            }
        }
    }

    public void handleMessage(final SurespotMessage message, final IAsyncCallback<Object> callback) {
        SurespotLog.d(TAG, "handleMessage %s", message);
        final String otherUser = message.getOtherUser(mUsername);

        final ChatAdapter chatAdapter = mChatAdapters.get(otherUser);

        // if the adapter is open add the message
        if (chatAdapter != null) {

            // decrypt the message before adding it so the size is set properly
            new AsyncTask<Void, Void, Void>() {

                @Override
                protected Void doInBackground(Void... params) {
                    SurespotLog.d(TAG, "ChatAdapter open for user: %s", otherUser);
                    if (message.getMimeType().equals(SurespotConstants.MimeTypes.TEXT) ||
                            message.getMimeType().equals(SurespotConstants.MimeTypes.GIF_LINK) ||
                            message.getMimeType().equals(SurespotConstants.MimeTypes.FILE)) {


                        // decrypt it before adding
                        final String plainText = EncryptionController.symmetricDecrypt(mContext, mUsername, message.getOurVersion(mUsername), message.getOtherUser(mUsername),
                                message.getTheirVersion(mUsername), message.getIv(), message.isHashed(), message.getData());

                        //SurespotLog.d(TAG, "handle message, decrypted plain text: %s", plainText);
                        if (plainText != null) {
                            // set plaintext in message so we don't have to decrypt again
                            switch (message.getMimeType()) {
                                case SurespotConstants.MimeTypes.TEXT:
                                    // substitute emoji
                                    SpannableStringBuilder builder = new SpannableStringBuilder(plainText);
                                    EmojiconHandler.addEmojis(mContext, builder, 30);
                                    message.setPlainData(builder.toString());
                                    break;
                                case SurespotConstants.MimeTypes.GIF_LINK:
                                    message.setPlainData(plainText);
                                    break;
                                case SurespotConstants.MimeTypes.FILE:
                                    SurespotMessage.FileMessageData fcm = SurespotMessage.FileMessageData.fromJSONString(plainText);
                                    SurespotLog.d(TAG, "handleMessage, server FileMessageData: %s", fcm);
                                    if (message.getFileMessageData() == null) {
                                        message.setFileMessageData(new SurespotMessage.FileMessageData());
                                    }
                                    message.getFileMessageData().setCloudUrl(fcm.getCloudUrl());
                                    message.getFileMessageData().setFilename(fcm.getFilename());
                                    message.getFileMessageData().setSize(fcm.getSize());
                                    message.getFileMessageData().setMimeType(fcm.getMimeType());
                                    SurespotLog.d(TAG, "handleMessage, after FileMessageData: %s", message.getFileMessageData());
                                    break;
                            }
                        }
                        else {
                            // error decrypting
                            SurespotLog.d(TAG, "could not decrypt message");
                            message.setPlainData(mContext.getString(R.string.message_error_decrypting_message));
                        }
                    }
                    else

                    {
                        if (message.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE) ||
                                message.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
                            // if it's an image that i sent
                            // handle caching
                            if (ChatUtils.isMyMessage(mUsername, message)) {
                                handleCachedFile(chatAdapter, message);
                            }
                        }
                        else {
                            message.setPlainData(mContext.getString(R.string.unknown_message_mime_type));
                        }
                    }
                    return null;
                }

                protected void onPostExecute(Void result) {

                    boolean added = applyControlMessages(chatAdapter, message, false, true, true);
                    if (added) {
                        scrollToEnd(otherUser);
                    }

                    Friend friend = mFriendAdapter.getFriend(otherUser);
                    if (friend != null) {
                        int messageId = message.getId();

                        // always update the available id
                        friend.setAvailableMessageId(messageId, false);

                        // if the chat is showing set the last viewed id the id of the message we just received
                        if (otherUser.equals(mCurrentChat)) {

                            friend.setLastViewedMessageId(messageId);

                            // if it was a voice message from the other user set play flag
                            // TODO wrap in preference
                            if (!ChatUtils.isMyMessage(mUsername, message) && message.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
                                message.setPlayMedia(true);
                            }

                        }
                        // chat not showing
                        else {
                            // if it's my message increment the count by one to account for it as I may have unread messages from the
                            // other user; we
                            // can't just set the last viewed to the latest message
                            if (ChatUtils.isMyMessage(mUsername, message) && added) {
                                int adjustedLastViewedId = friend.getLastViewedMessageId() + 1;
                                if (adjustedLastViewedId < messageId) {
                                    friend.setLastViewedMessageId(adjustedLastViewedId);
                                }
                                else {
                                    friend.setLastViewedMessageId(messageId);
                                }
                            }
                        }

                        mFriendAdapter.sort();
                        mFriendAdapter.notifyDataSetChanged();
                    }

                    callback.handleResponse(null);
                }
            }.execute();
        }
        else

        {
            SurespotLog.d(TAG, "ChatAdapter not open for user: %s", otherUser);

            Friend friend = mFriendAdapter.getFriend(otherUser);
            if (friend != null) {
                int messageId = message.getId();

                // always update the available id
                friend.setAvailableMessageId(messageId, false);
            }

            mFriendAdapter.notifyDataSetChanged();
            mFriendAdapter.sort();
            callback.handleResponse(null);
        }
    }


    private boolean applyControlMessages(ChatAdapter chatAdapter, SurespotMessage message, boolean checkSequence, boolean sort, boolean notify) {
        // see if we have applicable control messages and apply them if necessary
        CopyOnWriteArrayList<SurespotControlMessage> controlMessages = chatAdapter.getControlMessages();
        ArrayList<SurespotControlMessage> applicableControlMessages = new ArrayList<SurespotControlMessage>();
        for (SurespotControlMessage controlMessage : controlMessages) {
            int messageId = Integer.parseInt(controlMessage.getMoreData());
            if (message.getId() == messageId) {
                applicableControlMessages.add(controlMessage);
            }
        }
        boolean added = false;

        if (applicableControlMessages.size() == 0) {

            added = chatAdapter.addOrUpdateMessage(message, checkSequence, sort, notify);

        }
        else {
            added = chatAdapter.addOrUpdateMessage(message, checkSequence, false, false);

            for (SurespotControlMessage controlMessage : applicableControlMessages) {
                SurespotLog.d(TAG, "applying control message %s: to message %s", controlMessage, message);
                handleControlMessage(chatAdapter, controlMessage, false, true);
            }

            if (notify) {
                chatAdapter.notifyDataSetChanged();
            }
        }

        return added;
    }

    // add entry to http cache for image we sent so we don't download it again
    public void handleCachedFile(ChatAdapter chatAdapter, SurespotMessage message) {
        SurespotLog.d(TAG, "handleCachedFile");
        SurespotMessage localMessage = chatAdapter.getMessageByIv(message.getIv());
        if (localMessage != null && localMessage.getData() != null) {
            synchronized (localMessage) {
                // if the data is different we haven't updated the url to point externally
                if (localMessage.getId() == null && !localMessage.getData().equals(message.getData())) {
                    // add the remote cache entry for the new url

                    String localUri = localMessage.getData();
                    String remoteUri = message.getData();

                    SurespotLog.d(TAG, "copying cache entries from %s to %s", localUri, remoteUri);
                    // update in memory image cache
                    if (message.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE) || message.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
                        FileCacheController fcc = SurespotApplication.getFileCacheController();
                        if (fcc != null) {
                            fcc.moveCacheEntry(localUri, remoteUri);
                        }
                        MessageImageDownloader.moveCacheEntry(localUri, remoteUri);
                    }

                    // delete the file
                    try {
                        SurespotLog.d(TAG, "handleCachedImage deleting local file: %s", localUri);

                        File file = new File(new URI(localUri));
                        file.delete();
                    }
                    catch (Exception e) {
                        SurespotLog.w(TAG, e, "error deleting local file");
                    }

                    // update message to point to real location
                    localMessage.setData(remoteUri);

                }
            }
        }
    }

    // message handling shiznit
    void loadEarlierMessages(final String username, final IAsyncCallback<Boolean> callback) {
        if (getConnectionState() == STATE_CONNECTED) {

            // mLoading = true;
            // get the list of messages

            Integer firstMessageId = mEarliestMessage.get(username);
            if (firstMessageId == null) {
                firstMessageId = getEarliestMessageId(username);
                mEarliestMessage.put(username, firstMessageId);
            }
            // else {
            // firstMessageId -= 60;
            // if (firstMessageId < 1) {
            // firstMessageId = 1;
            // }
            // }

            if (firstMessageId != null) {

                if (firstMessageId > 1) {

                    SurespotLog.d(TAG, username + ": asking server for messages before messageId: " + firstMessageId);
                    // final int fMessageId = firstMessageId;
                    final ChatAdapter chatAdapter = mChatAdapters.get(username);

                    mNetworkController.getEarlierMessages(username, firstMessageId, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            SurespotLog.i(TAG, e, "%s: getEarlierMessages", username);
                            // chatAdapter.setLoading(false);
                            callback.handleResponse(false);
                        }

                        @Override
                        public void onResponse(Call call, Response response, String responseString) throws IOException {
                            if (response.isSuccessful()) {
                                // if (getActivity() != null) {
                                SurespotMessage message = null;

                                try {
                                    JSONArray jsonArray = new JSONArray(responseString);

                                    for (int i = jsonArray.length() - 1; i >= 0; i--) {
                                        JSONObject jsonMessage = jsonArray.getJSONObject(i);
                                        message = SurespotMessage.toSurespotMessage(jsonMessage);
                                        chatAdapter.insertMessage(message, false);
                                    }

                                    SurespotLog.d(TAG, "%s: loaded: %d earlier messages from the server.", username, jsonArray.length());
                                    if (message != null) {
                                        mEarliestMessage.put(username, message.getId());
                                        // chatAdapter.notifyDataSetChanged();
                                    }

                                    // chatAdapter.setLoading(false);
                                    callback.handleResponse(jsonArray.length() > 0);
                                }
                                catch (JSONException e) {
                                    SurespotLog.e(TAG, e, "%s: error loading earlier messages", username);
                                    callback.handleResponse(false);
                                }


                            }
                            else {
                                SurespotLog.i(TAG, "%s: getEarlierMessages error", username);
                                // chatAdapter.setLoading(false);
                                callback.handleResponse(false);
                            }
                        }


                    }));
                }
                else {
                    SurespotLog.d(TAG, "%s: getEarlierMessages: no more messages.", username);
                    callback.handleResponse(false);
                    // ChatFragment.this.mNoEarlierMessages = true;
                }

            }
        }
    }

    private void getLatestData(final boolean fetchedFriends) {
        SurespotLog.v(TAG, "getLatestData, mLatestUserControlId: %d, fetchedFriends: %b", mLatestUserControlId, fetchedFriends);
        // setMessagesLoading(true);
        setProgress("getLatestData", true);

        //get messages from server for open tabs
        JSONArray spotIds = new JSONArray();
        for (Entry<String, ChatAdapter> entry : mChatAdapters.entrySet()) {
            JSONObject spot = new JSONObject();
            String username = entry.getKey();
            try {
                LatestIdPair p = getPreConnectIds(username);
                if (p != null) {
                    spot.put("u", username);
                    spot.put("m", p.latestMessageId);
                    spot.put("cm", p.latestControlMessageId);
                    spotIds.put(spot);
                }
            }
            catch (JSONException e) {
                continue;
            }
        }

        mNetworkController.getLatestData(mLatestUserControlId, spotIds, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Utils.makeToast(mContext, mContext.getString(R.string.loading_latest_messages_failed));
                        SurespotLog.w(TAG, e, "error getLatestData");
                        setProgress("getLatestData", false);
                    }

                    @Override
                    public void onResponse(Call call, Response response, String responseString) throws IOException {
                        if (response.isSuccessful()) {
                            JSONObject jsonResponse = null;
                            try {
                                jsonResponse = new JSONObject(responseString);
                                SurespotLog.v(TAG, "getlatestData success, response: %s, statusCode: %d", jsonResponse, response.code());
                            }
                            catch (JSONException e) {
                                Utils.makeToast(mContext, mContext.getString(R.string.loading_latest_messages_failed));
                                SurespotLog.w(TAG, e, "error getLatestData");
                                setProgress("getLatestData", false);
                                return;
                            }

                            final boolean hasSigs = jsonResponse.has("sigs2");
                            new AsyncTask<Void, Void, Void>() {
                                @Override
                                protected Void doInBackground(Void... voids) {
                                    //see if we need to update signatures, will only have sigs property if we need to update
                                    if (hasSigs) {
                                        JSONObject sigs = IdentityController.updateSignatures(mContext, mUsername);
                                        mNetworkController.updateSigs(sigs, new Callback() {
                                            @Override
                                            public void onFailure(Call call, IOException e) {
                                                SurespotLog.i(TAG, e, "Signatures update failed");
                                            }

                                            @Override
                                            public void onResponse(Call call, Response response) throws IOException {
                                                if (response.isSuccessful()) {
                                                    SurespotLog.d(TAG, "Signatures updated");
                                                }
                                                else {
                                                    SurespotLog.d(TAG, "Signatures update failed, code: %d", response.code());
                                                }
                                            }
                                        });
                                    }
                                    return null;
                                }
                            }.execute();

                            JSONObject conversationIds = jsonResponse.optJSONObject("conversationIds");
                            Friend friend = null;
                            if (conversationIds != null) {
                                Iterator i = conversationIds.keys();
                                while (i.hasNext()) {
                                    String spot = (String) i.next();
                                    try {
                                        Integer availableId = conversationIds.getInt(spot);
                                        String user = ChatUtils.getOtherSpotUser(spot, mUsername);
                                        // update available ids
                                        friend = mFriendAdapter.getFriend(user);
                                        if (friend != null) {
                                            friend.setAvailableMessageId(availableId, fetchedFriends);
                                        }
                                    }
                                    catch (Exception e) {
                                        SurespotLog.w(TAG, e, "getlatestData");
                                    }
                                }
                            }

                            JSONObject controlIds = jsonResponse.optJSONObject("controlIds");
                            if (controlIds != null) {
                                Iterator i = conversationIds.keys();
                                while (i.hasNext()) {
                                    String spot = (String) i.next();
                                    try {
                                        if (controlIds.has(spot)) {
                                            Integer availableId = controlIds.getInt(spot);
                                            String user = ChatUtils.getOtherSpotUser(spot, mUsername);
                                            // update available ids
                                            friend = mFriendAdapter.getFriend(user);
                                            if (friend != null) {
                                                friend.setAvailableMessageControlId(availableId);
                                            }
                                        }
                                    }
                                    catch (JSONException e) {
                                        SurespotLog.w(TAG, e, "getlatestData");
                                    }
                                }
                            }

                            JSONArray userControlMessages = jsonResponse.optJSONArray("userControlMessages");
                            if (userControlMessages != null) {
                                handleControlMessages(mUsername, userControlMessages);
                            }

                            JSONArray messageDatas = jsonResponse.optJSONArray("messageData");
                            if (messageDatas != null) {
                                for (int i = 0; i < messageDatas.length(); i++) {
                                    try {
                                        JSONObject messageData = messageDatas.getJSONObject(i);
                                        String friendName = messageData.getString("username");

                                        JSONArray controlMessages = messageData.optJSONArray("controlMessages");
                                        if (controlMessages != null) {
                                            handleControlMessages(friendName, controlMessages);
                                        }

                                        JSONArray messages = messageData.optJSONArray("messages");
                                        if (messages != null) {
                                            handleMessages(friendName, messages, fetchedFriends);
                                        }

                                    }
                                    catch (JSONException e) {
                                        SurespotLog.w(TAG, e, "getlatestData");
                                    }
                                }
                            }

                            mFriendAdapter.sort();
                            mFriendAdapter.notifyDataSetChanged();

                            handleAutoInvite();
                            processNextMessage();
                            setProgress("getLatestData", false);
                        }
                        else {
                            SurespotLog.w(TAG, "error getLatestData, response code: %d", response.code());
                            setProgress("getLatestData", false);
                            switch (response.code()) {
                                case 401:
                                    // don't show toast on 401 as we are going to be going bye bye
                                    return;
                                default:
                                    Utils.makeToast(mContext, mContext.getString(R.string.loading_latest_messages_failed));
                            }
                        }
                    }
                })
        );
    }

    private void onBeforeConnect() {
        // copy the latest ids so that we don't miss any if we receive new messages during the time we request messages and when the
        // connection completes (if they
        // are received out of order for some reason)
        //
        setProgress("connect", true);

        mPreConnectIds.clear();
        for (Map.Entry<String, ChatAdapter> entry : mChatAdapters.entrySet()) {
            String username = entry.getKey();
            LatestIdPair idPair = new LatestIdPair();
            idPair.latestMessageId = getLatestMessageId(username);
            idPair.latestControlMessageId = getLatestMessageControlId(username);
            SurespotLog.d(TAG, "setting preconnectids for: " + username + ", latest message id:  " + idPair.latestMessageId + ", latestcontrolid: "
                    + idPair.latestControlMessageId);
            mPreConnectIds.put(username, idPair);
        }
    }

    public void mute(String name) {
        Friend friend = getFriendAdapter().getFriend(name);
        friend.setMuted(true);
        saveFriends();
        getFriendAdapter().notifyDataSetChanged();
    }

    public void unmute(String name) {
        Friend friend = getFriendAdapter().getFriend(name);
        friend.setMuted(false);
        saveFriends();
        getFriendAdapter().notifyDataSetChanged();
    }


    private class LatestIdPair {
        int latestMessageId;
        int latestControlMessageId;
    }

    private LatestIdPair getPreConnectIds(String username) {
        LatestIdPair idPair = mPreConnectIds.get(username);

        if (idPair == null) {
            idPair = new LatestIdPair();
            idPair.latestControlMessageId = 0;
            idPair.latestMessageId = 0;
        }

        return idPair;
    }

    private LatestIdPair getLatestIds(String username) {
        Friend friend = getFriendAdapter().getFriend(username);
        LatestIdPair idPair = mPreConnectIds.get(username);

        Integer latestMessageId = idPair.latestMessageId > -1 ? idPair.latestMessageId : 0;
        int latestAvailableId = friend.getAvailableMessageId();

        int latestControlId = idPair.latestControlMessageId > -1 ? idPair.latestControlMessageId : friend.getLastReceivedMessageControlId();
        int latestAvailableControlId = friend.getAvailableMessageControlId();

        int fetchMessageId = 0;
        if (latestMessageId > 0) {
            fetchMessageId = latestAvailableId > latestMessageId ? latestMessageId : -1;
        }

        int fetchControlMessageId = 0;
        if (latestControlId > 0) {
            fetchControlMessageId = latestAvailableControlId > latestControlId ? latestControlId : -1;
        }

        LatestIdPair intPair = new LatestIdPair();
        intPair.latestMessageId = fetchMessageId;
        intPair.latestControlMessageId = fetchControlMessageId;

        return intPair;
    }

    private void getLatestMessagesAndControls(final String username, boolean forceMessageUpdate) {
        LatestIdPair ids = getLatestIds(username);
        getLatestMessagesAndControls(username, ids.latestMessageId, ids.latestControlMessageId, forceMessageUpdate);
    }

    private void getLatestMessagesAndControls(final String username, final int fetchMessageId, int fetchControlMessageId, final boolean forceMessageUpdate) {
        if (getState() != STATE_CONNECTED) {
            return;
        }
        SurespotLog.d(TAG, "getLatestMessagesAndControls: name %s, fetchMessageId: %d, fetchControlMessageId: %d", username, fetchMessageId,
                fetchControlMessageId);
        if (fetchMessageId > -1 || fetchControlMessageId > -1) {
            setProgress(username, true);

            mNetworkController.getMessageData(username, fetchMessageId, fetchControlMessageId, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    SurespotLog.w(TAG, e, "error getting latest message data for user: %s", username);
                    setProgress(username, false);
                }

                @Override
                public void onResponse(Call call, Response response, String responseString) throws IOException {
                    if (response.isSuccessful()) {
                        JSONObject json;
                        try {
                            json = new JSONObject(responseString);
                        }
                        catch (JSONException e) {
                            SurespotLog.w(TAG, e, "error getting latest message data for user: %s", username);
                            setProgress(username, false);
                            return;
                        }

                        JSONArray controlMessages = json.optJSONArray("controlMessages");
                        if (controlMessages != null) {
                            handleControlMessages(username, controlMessages);
                        }

                        JSONArray messages = json.optJSONArray("messages");

                        // don't update messages if we didn't query for them
                        // this prevents setting message state to error before we get the true result
                        if (fetchMessageId > -1 || forceMessageUpdate) {
                            handleMessages(username, messages, false);
                        }

                        setProgress(username, false);
                    }
                }


            }));
        }

    }

    private void handleControlMessages(String username, JSONArray jsonArray) {
        SurespotLog.d(TAG, "%s: handleControlMessages", username);
        final ChatAdapter chatAdapter = mChatAdapters.get(username);

        SurespotControlMessage message = null;
        boolean messageActivity = false;
        boolean userActivity = false;
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
                message = SurespotControlMessage.toSurespotControlMessage(jsonMessage);
                handleControlMessage(chatAdapter, message, false, false);
                // if it's a system message from another user then check version
                if (message.getType().equals("user")) {
                    userActivity = true;
                }
                else if (message.getType().equals("message")) {
                    messageActivity = true;
                }

            }
            catch (JSONException e) {
                SurespotLog.w(TAG, e, "%s: error creating chat message", username);
            }

        }

        if (message != null) {

            SurespotLog.d(TAG, "%s: loaded: %d latest control messages from the server.", username, jsonArray.length());

            if (messageActivity || userActivity) {
                Friend friend = mFriendAdapter.getFriend(username);
                if (friend != null) {

                    if (messageActivity) {

                        if (chatAdapter != null) {
                            friend.setLastReceivedMessageControlId(message.getId());
                            chatAdapter.sort();
                            chatAdapter.notifyDataSetChanged();
                        }

                        friend.setAvailableMessageControlId(message.getId());
                        mFriendAdapter.notifyDataSetChanged();

                    }

                    if (userActivity) {
                        saveFriends();
                        mFriendAdapter.notifyDataSetChanged();
                    }
                }
            }
        }

        // chatAdapter.setLoading(false);
    }

    private void handleControlMessage(ChatAdapter chatAdapter, SurespotControlMessage message, boolean notify, boolean reApplying) {
        // if it's a system message from another user then check version
        if (message.getType().equals("user")) {
            handleUserControlMessage(message, notify);
        }
        else if (message.getType().equals("message")) {
            String otherUser = ChatUtils.getOtherSpotUser(message.getData(), mUsername);
            Friend friend = mFriendAdapter.getFriend(otherUser);

            if (chatAdapter == null) {
                chatAdapter = mChatAdapters.get(otherUser);
            }

            if (chatAdapter != null) {
                // if we're not re applying this control message
                if (!reApplying) {
                    // add control message to check messages against later for this session
                    chatAdapter.addControlMessage(message);
                }

                boolean controlFromMe = message.getFrom().equals(mUsername);
                if (message.getAction().equals("delete")) {
                    int messageId = Integer.parseInt(message.getMoreData());
                    SurespotMessage dMessage = chatAdapter.getMessageById(messageId);

                    if (dMessage != null) {
                        deleteMessageInternal(chatAdapter, dMessage, controlFromMe, false);
                    }
                }
                else {
                    if (message.getAction().equals("deleteAll")) {
                        if (message.getMoreData() != null) {
                            if (controlFromMe) {
                                chatAdapter.deleteAllMessages(Integer.parseInt(message.getMoreData()));
                            }
                            else {
                                chatAdapter.deleteTheirMessages(Integer.parseInt(message.getMoreData()));
                            }
                        }
                    }
                    else {
                        if (message.getAction().equals("shareable") || message.getAction().equals("notshareable")) {
                            int messageId = Integer.parseInt(message.getMoreData());
                            SurespotMessage dMessage = chatAdapter.getMessageById(messageId);
                            if (dMessage != null) {
                                SurespotLog.d(TAG, "setting message " + message.getAction());
                                dMessage.setShareable(message.getAction().equals("shareable") ? true : false);
                            }
                        }
                    }
                }
            }

            if (notify) {
                if (friend != null) {
                    // if the chat adapter is open we will have acted upon the control message
                    if (chatAdapter != null) {
                        friend.setLastReceivedMessageControlId(message.getId());
                    }

                    friend.setAvailableMessageControlId(message.getId());
                }

                if (chatAdapter != null) {
                    chatAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    private void handleUserControlMessage(SurespotControlMessage message, boolean notify) {

        mLatestUserControlId = message.getId();
        String user = null;

        if (message.getAction().equals("revoke")) {
            SurespotLog.d(TAG, "message action is revoke");
            IdentityController.updateLatestVersion(mContext, message.getData(), message.getMoreData());
        }
        else if (message.getAction().equals("invited")) {
            user = message.getData();
            mFriendAdapter.addFriendInvited(user);
        }
        else if (message.getAction().equals("added")) {
            user = message.getData();
            mFriendAdapter.addNewFriend(user);
        }
        else if (message.getAction().equals("invite")) {
            user = message.getData();
            mFriendAdapter.addFriendInviter(user);
        }
        else if (message.getAction().equals("ignore")) {
            String friendName = message.getData();
            Friend friend = mFriendAdapter.getFriend(friendName);

            // if they're not deleted, remove them
            if (friend != null) {
                if (!friend.isDeleted()) {

                    mFriendAdapter.removeFriend(friendName);
                }
                else {
                    // they've been deleted, just remove the invite flags
                    friend.setInviter(false);
                    friend.setInvited(false);

                }
            }

        }
        else if (message.getAction().equals("delete")) {
            String friendName = message.getData();

            Friend friend = mFriendAdapter.getFriend(friendName);

            if (friend != null) {
                // if it was just a delete of an invite
                if (friend.isInviter() || friend.isInvited()) {

                    // if they're not deleted, remove them
                    if (!friend.isDeleted()) {
                        mFriendAdapter.removeFriend(friendName);
                    }
                    else {
                        // they've been deleted, just remove the invite flags
                        friend.setInviter(false);
                        friend.setInvited(false);
                    }
                }
                // they really deleted us boo hoo
                else {
                    handleDeleteUser(friendName, message.getMoreData(), notify);
                }
            }

            // clear any associated invite notification
            if (mUsername != null) {
                mNotificationManager.cancel(mUsername + ":" + friendName,
                        SurespotConstants.IntentRequestCodes.INVITE_REQUEST_NOTIFICATION);
            }

        }
        else if (message.getAction().equals("friendImage")) {
            String friendName = message.getData();
            Friend friend = mFriendAdapter.getFriend(friendName);

            if (friend != null) {

                String moreData = message.getMoreData();

                if (moreData != null) {

                    JSONObject jsonData = null;
                    try {
                        jsonData = new JSONObject(moreData);
                        String iv = jsonData.getString("iv");
                        String url = jsonData.getString("url");
                        String version = jsonData.getString("version");
                        boolean hashed = jsonData.optBoolean("imageHashed", false);
                        setImageUrl(friendName, url, version, iv, hashed);
                    }
                    catch (JSONException e) {
                        SurespotLog.e(TAG, e, "could not parse friend image control message json");

                    }
                }
                else {
                    removeFriendImage(friendName);
                }
            }
        }
        else if (message.getAction().equals("friendAlias")) {
            String friendName = message.getData();
            Friend friend = mFriendAdapter.getFriend(friendName);

            if (friend != null) {

                String moreData = message.getMoreData();

                if (moreData != null) {
                    JSONObject jsonData = null;
                    try {
                        jsonData = new JSONObject(moreData);
                        String iv = jsonData.getString("iv");
                        String data = jsonData.getString("data");
                        String version = jsonData.getString("version");
                        boolean hashed = jsonData.optBoolean("aliasHashed", false);
                        setFriendAlias(friendName, data, version, iv, hashed);
                    }
                    catch (JSONException e) {
                        SurespotLog.e(TAG, e, "could not parse friend alias control message json");
                    }
                }
                else {
                    removeFriendAlias(friendName);
                }
            }
        }
        if (notify) {
            mFriendAdapter.notifyDataSetChanged();
            saveFriends();
        }

    }

    private void handleDeleteUser(String deletedUser, String deleter, boolean notify) {
        SurespotLog.d(TAG, "handleDeleteUser,  deletedUser: %s, deleter: %s", deletedUser, deleter);

        Friend friend = mFriendAdapter.getFriend(deletedUser);

        boolean iDidTheDeleting = deleter.equals(mUsername);
        if (iDidTheDeleting) {
            // won't be needing this anymore
            closeTab(deletedUser);

            // blow all the state associated with this user away
            StateController.wipeUserState(mContext, mUsername, deletedUser);

            // clear in memory cached data
            CredentialCachingService ccs = SurespotApplication.getCachingService(mContext);
            if (ccs != null) {
                ccs.clearUserData(deleter, deletedUser);
            }

            // clear the http cache
            mNetworkController.clearCache();

            // clear file cache
            FileCacheController fcc = SurespotApplication.getFileCacheController();
            if (fcc != null) {
                fcc.clearCache();
            }

            // or you
            mFriendAdapter.removeFriend(deletedUser);
        }
        // you deleted me, you bastard!!
        else {
            ChatAdapter chatAdapter = mChatAdapters.get(deleter);

            // i'll delete all your messages then
            if (chatAdapter != null) {
                chatAdapter.userDeleted();
                if (notify) {
                    chatAdapter.notifyDataSetChanged();
                }
            }

            // and mark you as deleted until I want to delete you
            friend.setDeleted();

            if (friend != null && mCurrentChat != null && mCurrentChat.equals(deletedUser)) {
                mTabShowingCallback.handleResponse(friend);
            }
        }

        enableMenuItems(friend);
    }


    private void deleteMessageInternal(ChatAdapter chatAdapter, SurespotMessage dMessage, boolean initiatedByMe, boolean notify) {
        // if it's an image blow the http cache entry away
        if (dMessage.getMimeType() != null) {
            if (dMessage.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE) || dMessage.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
                mNetworkController.removeCacheEntry(dMessage.getData());
            }

            boolean myMessage = dMessage.getFrom().equals(mUsername);

            // if i sent the delete, or it's not my message then delete it
            // (if someone else deleted my message we don't care)
            if (initiatedByMe || !myMessage) {
                SurespotLog.d(TAG, "deleting message");
                chatAdapter.deleteMessageById(dMessage.getId(), notify);
            }
        }
    }

    private void handleMessages(String username, JSONArray jsonMessages, boolean mayBeCacheClear) {
        SurespotLog.d(TAG, "%s: handleMessages", username);
        final ChatAdapter chatAdapter = mChatAdapters.get(username);
        if (chatAdapter == null) {
            return;
        }

        // if we received new messages
        if (jsonMessages != null) {

            int sentByMeCount = 0;

            SurespotMessage lastMessage = null;
            try {
                SurespotLog.d(TAG, "%s: loaded: %d messages from the server", username, jsonMessages.length());
                for (int i = 0; i < jsonMessages.length(); i++) {

                    lastMessage = SurespotMessage.toSurespotMessage(jsonMessages.getJSONObject(i));
                    boolean myMessage = lastMessage.getFrom().equals(mUsername);

                    if (myMessage) {
                        if (lastMessage.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE) || lastMessage.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
                            handleCachedFile(chatAdapter, lastMessage);
                        }
                    }

                    boolean added = applyControlMessages(chatAdapter, lastMessage, false, false, false);

                    messageSendCompleted(lastMessage);
                    removeQueuedMessage(lastMessage);
                    if (added && myMessage) {
                        sentByMeCount++;
                    }
                }
            }
            catch (JSONException e) {
                SurespotLog.w(TAG, e, "jsonStringsToMessages");

            }

            if (lastMessage != null) {
                Friend friend = mFriendAdapter.getFriend(username);

                int availableId = lastMessage.getId();
                friend.setAvailableMessageId(availableId, false);

                //might have been less than what the friend knew from the message counters so we'll let the friend decide
                availableId = friend.getAvailableMessageId();

                int lastViewedId = friend.getLastViewedMessageId();

                // how many new messages total are there
                int delta = availableId - lastViewedId;

                // if the current chat is showing or
                // all the new messages are mine then i've viewed them all
                if (username.equals(mCurrentChat) || sentByMeCount == delta) {
                    friend.setLastViewedMessageId(availableId);
                }
                else {
                    // set the last viewed id to the difference caused by their messages
                    friend.setLastViewedMessageId(availableId - (delta - sentByMeCount));
                }

                if (mayBeCacheClear) {
                    friend.setLastViewedMessageId(lastMessage.getId());
                }

                mFriendAdapter.sort();
                mFriendAdapter.notifyDataSetChanged();

                if (sentByMeCount != delta) {
                    scrollToEnd(username);
                }
            }
        }

        chatAdapter.sort();
        chatAdapter.doneCheckingSequence();
        // mark messages left in chatAdapter with no id as errored
        // chatAdapter.markErrored();
        chatAdapter.notifyDataSetChanged();
    }

    private Integer getEarliestMessageId(String username) {

        ChatAdapter chatAdapter = mChatAdapters.get(username);
        Integer firstMessageId = null;
        if (chatAdapter != null) {
            SurespotMessage firstMessage = chatAdapter.getFirstMessageWithId();

            if (firstMessage != null) {
                firstMessageId = firstMessage.getId();
            }

        }
        return firstMessageId;
    }

    private int getLatestMessageId(String username) {
        Integer lastMessageId = 0;
        ChatAdapter chatAdapter = mChatAdapters.get(username);
        if (chatAdapter != null) {

            SurespotMessage lastMessage = chatAdapter.getLastMessageWithId();
            if (lastMessage != null) {
                lastMessageId = lastMessage.getId();
            }
        }
        return lastMessageId;

    }

    private Integer getLatestMessageControlId(String username) {
        Friend friend = mFriendAdapter.getFriend(username);
        Integer lastControlId = null;
        if (friend != null) {
            lastControlId = friend.getLastReceivedMessageControlId();
        }
        return lastControlId == null ? 0 : lastControlId;
    }

    private synchronized void loadMessages(String username, boolean replace) {
        SurespotLog.d(TAG, "loadMessages: " + username);

        if (!TextUtils.isEmpty(mUsername)) {
            String spot = ChatUtils.getSpot(mUsername, username);
            ChatAdapter chatAdapter = mChatAdapters.get(username);
            if (replace) {
                chatAdapter.setMessages(SurespotApplication.getStateController().loadMessages(mUsername, spot));
            }
            else {
                chatAdapter.addOrUpdateMessages(SurespotApplication.getStateController().loadMessages(mUsername, spot));
            }
        }

    }


    public synchronized void logout() {
        // save before we clear the chat adapters

        SurespotLog.d(TAG, "user logging out: " + mUsername);

        save();

        shutdownConnection();
        mSendQueue.clear();
        mChatAdapters.clear();
    }

    private void saveFriends() {
        if (mFriendAdapter != null) {
            SurespotApplication.getStateController().saveFriends(mUsername, mLatestUserControlId, mFriendAdapter.getFriends());
        }
    }

    private void loadFriendState() {
        SurespotLog.d(TAG, "loadFriendState");
        FriendState fs = SurespotApplication.getStateController().loadFriends(mUsername);

        List<Friend> friends = null;
        if (fs != null) {
            mLatestUserControlId = fs.userControlId;
            friends = fs.friends;
        }

        mFriendAdapter.setFriends(friends);
        mFriendAdapter.setLoading(false);
    }

    private boolean mGlobalProgress;
    private HashMap<String, Boolean> mChatProgress = new HashMap<String, Boolean>();

    private synchronized void setProgress(String key, boolean inProgress) {

        if (key == null) {
            mGlobalProgress = inProgress;
        }
        else {
            if (inProgress) {
                mChatProgress.put(key, true);
            }
            else {
                mChatProgress.remove(key);
            }
        }

        boolean progress = isInProgress();
        SurespotLog.d(TAG, "setProgress %s, isInProgress(): %b", key == null ? "null" : key, progress);

        if (mProgressCallback != null) {
            mProgressCallback.handleResponse(progress);
        }
    }

    private synchronized boolean isInProgress() {
        return mGlobalProgress || !mChatProgress.isEmpty();
    }

    synchronized void resume() {
        mMainActivityPaused = false;

        // load chat messages from disk that may have been added by gcm
        for (Entry<String, ChatAdapter> ca : mChatAdapters.entrySet()) {
            loadMessages(ca.getKey(), false);
        }

        connect();
        clearMessageNotification(mCurrentChat);
    }

    ChatAdapter getChatAdapter(String username) {
        return getChatAdapter(username, true);
    }

    ChatAdapter getChatAdapter(String username, boolean create) {


        ChatAdapter chatAdapter = mChatAdapters.get(username);

        if (chatAdapter == null && create) {

            chatAdapter = new ChatAdapter(mContext, mUsername, username);

            Friend friend = mFriendAdapter.getFriend(username);
            if (friend != null) {
                if (friend.isDeleted()) {
                    chatAdapter.userDeleted();
                }
            }

            SurespotLog.d(TAG, "getChatAdapter created chat adapter for: %s", username);
            mChatAdapters.put(username, chatAdapter);


            // load savedmessages
            loadMessages(username, true);

            LatestIdPair idPair = new LatestIdPair();
            idPair.latestMessageId = getLatestMessageId(username);
            idPair.latestControlMessageId = getLatestMessageControlId(username);
            SurespotLog.d(TAG, "setting preconnectids for: %s, latest message id: %d, latestcontrolid: %d", username, idPair.latestMessageId,
                    idPair.latestControlMessageId);
            mPreConnectIds.put(username, idPair);

            // get latest messages from server
            getLatestMessagesAndControls(username, false);
        }
        else {
            SurespotLog.d(TAG, "getChatAdapter adapter already created for: %s", username);
        }

        return chatAdapter;
    }

    private void destroyChatAdapter(String username) {
        SurespotLog.d(TAG, "destroying chat adapter for: %s", username);
        saveMessages(username);
        mChatAdapters.remove(username);
    }

    public synchronized void setCurrentChat(final String username) {

        SurespotLog.d(TAG, "setCurrentChat: %s", username);


        Friend friend = null;
        if (username != null) {
            friend = mFriendAdapter.getFriend(username);
        }

        mTabShowingCallback.handleResponse(friend);
        if (friend != null) {
            mCurrentChat = username;
            mChatPagerAdapter.addChatFriend(friend);
            friend.setChatActive(true);
            friend.setLastViewedMessageId(friend.getAvailableMessageId());

            // cancel associated notifications
            clearMessageNotification(username);
            int wantedPosition = mChatPagerAdapter.getChatFragmentPosition(username);

            if (wantedPosition != mViewPager.getCurrentItem()) {
                mViewPager.setCurrentItem(wantedPosition, true);
            }

            if (mMode == MODE_SELECT) {
                mSendIntentCallback.handleResponse(null);
                setMode(MODE_NORMAL);
            }

            //restore scroll position
            ChatFragment cfNew = getChatFragment(username);
            if (cfNew != null) {
                cfNew.scrollToState();
            }
        }
        else {
            mCurrentChat = null;
            mViewPager.setCurrentItem(0, true);
            mNotificationManager.cancel(mUsername + ":" + username, SurespotConstants.IntentRequestCodes.INVITE_REQUEST_NOTIFICATION);
            mNotificationManager.cancel(mUsername, SurespotConstants.IntentRequestCodes.INVITE_RESPONSE_NOTIFICATION);
        }

        mFriendAdapter.sort();
        mFriendAdapter.notifyDataSetChanged();

        // set menu item enable state
        enableMenuItems(friend);

    }

    private void clearMessageNotification(String username) {
        if (!TextUtils.isEmpty(username)) {
            mNotificationManager.cancel(mUsername + ":" + ChatUtils.getSpot(mUsername, username),
                    SurespotConstants.IntentRequestCodes.NEW_MESSAGE_NOTIFICATION);
        }
    }

    private ChatFragment getChatFragment(String username) {
        String fragmentTag = Utils.makePagerFragmentName(mViewPager.getId(), username.hashCode());
        SurespotLog.d(TAG, "looking for fragment: %s", fragmentTag);
        ChatFragment chatFragment = (ChatFragment) mFragmentManager.findFragmentByTag(fragmentTag);
        SurespotLog.d(TAG, "fragment: %s", chatFragment);
        return chatFragment;
    }

    public void sendMessage(final String username, final String plainText, final String mimeType) {
        if (plainText.length() > 0) {
            final ChatAdapter chatAdapter = mChatAdapters.get(username);
            if (chatAdapter == null) {
                return;
            }
            // display the message immediately
            final byte[] iv = EncryptionController.getIv();

            // build a message without the encryption values set as they could take a while

            final SurespotMessage chatMessage = ChatUtils.buildPlainMessage(mUsername, username, mimeType, plainText, new String(
                    ChatUtils.base64EncodeNowrap(iv)));


            chatAdapter.addOrUpdateMessage(chatMessage, false, true, true);
            enqueueMessage(chatMessage);
        }
    }


    public void addMessage(SurespotMessage message) {
        if (message.getFrom().equals(mUsername)) {
            if (mChatAdapters != null) {
                ChatAdapter chatAdapter = mChatAdapters.get(message.getTo());
                if (chatAdapter != null) {
                    try {
                        boolean added = chatAdapter.addOrUpdateMessage(message, false, true, true);
                        saveMessages(message.getTo());
                        if (added) {
                            scrollToEnd(message.getTo());
                        }
                    }
                    catch (Exception e) {
                        SurespotLog.e(TAG, e, "addMessage");
                    }
                }
            }
        }
    }


    public String getCurrentChat() {
        return mCurrentChat;
    }

    public boolean hasEarlierMessages(String username) {
        Integer id = mEarliestMessage.get(username);
        if (id == null) {
            id = getEarliestMessageId(username);
        }

        if (id != null && id > 1) {
            return true;
        }

        return false;
    }

    public void deleteMessage(final SurespotMessage message, final boolean notify) {
        //remove it from send queue
        removeQueuedMessage(message);

        // if it's on the server, send delete control message otherwise just delete it locally
        if (message.getId() != null) {

            final ChatAdapter chatAdapter = mChatAdapters.get(message.getOtherUser(mUsername));
            setProgress("delete", true);
            if (chatAdapter != null) {
                mNetworkController.deleteMessage(message.getOtherUser(mUsername), message.getId(), new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        SurespotLog.i(TAG, e, "deleteMessage");
                        setProgress("delete", false);
                        Utils.makeToast(mContext, mContext.getString(R.string.could_not_delete_message));
                    }

                    @Override
                    public void onResponse(Call call, Response response, String responseString) throws IOException {
                        if (response.isSuccessful()) {
                            deleteMessageInternal(chatAdapter, message, true, notify);
                            setProgress("delete", false);
                        }
                        else {
                            SurespotLog.i(TAG, "deleteMessage statusCode: %d", response.code());
                            setProgress("delete", false);
                            Utils.makeToast(mContext, mContext.getString(R.string.could_not_delete_message));
                        }
                    }
                }));
            }

        }
        else {
            // remove the local message
            String otherUser = message.getOtherUser(mUsername);
            //	getSendQueue().remove(message);

            ChatAdapter chatAdapter = mChatAdapters.get(otherUser);
            chatAdapter.deleteMessageByIv(message.getIv());
            saveMessages(otherUser);

            // if it's an file message, delete the local file
            if (message.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE) || message.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
                if (message.getData() != null && message.getData().startsWith("file")) {
                    try {
                        new File(new URI(message.getData())).delete();
                    }
                    catch (URISyntaxException e) {
                        SurespotLog.w(TAG, e, "deleteMessage");
                    }
                }
                if (message.getPlainData() != null && message.getPlainData().toString().startsWith("file")) {
                    try {
                        new File(new URI(message.getPlainData().toString())).delete();
                    }
                    catch (URISyntaxException e) {
                        SurespotLog.w(TAG, e, "deleteMessage");
                    }
                }
            }
        }
    }

    public void deleteMessages(String name) {
        Friend friend = mFriendAdapter.getFriend(name);
        if (friend != null) {
            deleteMessages(friend);
        }
    }

    public void deleteMessages(final Friend friend) {
        // if it's on the server, send delete control message otherwise just delete it locally

        if (friend != null) {
            String username = friend.getName();

            setProgress("deleteMessages", true);
            int lastReceivedMessageId = 0;
            final ChatAdapter chatAdapter = mChatAdapters.get(username);
            if (chatAdapter != null) {
                lastReceivedMessageId = getLatestMessageId(username);
            }
            else {
                lastReceivedMessageId = friend.getLastViewedMessageId();
            }

            final int finalMessageId = lastReceivedMessageId;
            //get rid of messages for this isure in chat controller queue

            clearMessageQueue(username);

            mNetworkController.deleteMessages(username, lastReceivedMessageId, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    setProgress("deleteMessages", false);
                    Utils.makeToast(mContext, mContext.getString(R.string.could_not_delete_messages));
                }

                @Override
                public void onResponse(Call call, Response response, String responseString) throws IOException {
                    if (response.isSuccessful()) {
                        if (chatAdapter != null) {
                            chatAdapter.deleteAllMessages(finalMessageId);
                            chatAdapter.notifyDataSetChanged();
                        }
                        else {
                            // tell friend there's a new control message so they get it when the tab is opened
                            friend.setAvailableMessageControlId(friend.getAvailableMessageControlId() + 1);
                            saveFriends();
                        }

                        setProgress("deleteMessages", false);
                    }
                    else {
                        setProgress("deleteMessages", false);
                        Utils.makeToast(mContext, mContext.getString(R.string.could_not_delete_messages));
                    }
                }
            }));
        }
    }

    public void deleteFriend(Friend friend) {

        if (friend != null) {
            final String username = friend.getName();
            setProgress("deleteFriend", true);
            mNetworkController.deleteFriend(username, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    SurespotLog.i(TAG, e, "deleteFriend");
                    setProgress("deleteFriend", false);
                    Utils.makeToast(mContext, mContext.getString(R.string.could_not_delete_friend));
                }

                @Override
                public void onResponse(Call call, Response response, String responseString) throws IOException {
                    if (response.isSuccessful()) {
                        handleDeleteUser(username, mUsername, true);
                        setProgress("deleteFriend", false);
                    }
                    else {
                        SurespotLog.i(TAG, "deleteFriend error, response code: %d" + response.code());
                        setProgress("deleteFriend", false);
                        Utils.makeToast(mContext, mContext.getString(R.string.could_not_delete_friend));
                    }
                }
            }));
        }
    }

    public void toggleMessageShareable(String to, final String messageIv) {
        final ChatAdapter chatAdapter = mChatAdapters.get(to);
        final SurespotMessage message = chatAdapter.getMessageByIv(messageIv);
        if (message != null && message.getId() > 0) {
            String messageUsername = message.getOtherUser(mUsername);

            if (!messageUsername.equals(to)) {
                Utils.makeToast(mContext, mContext.getString(R.string.could_not_set_message_lock_state));
                return;
            }

            if (chatAdapter != null) {

                setProgress("shareable", true);
                mNetworkController.setMessageShareable(to, message.getId(), !message.isShareable(), new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        SurespotLog.i(TAG, e, "toggleMessageShareable");
                        setProgress("shareable", false);
                        Utils.makeToast(mContext, mContext.getString(R.string.could_not_set_message_lock_state));
                    }

                    @Override
                    public void onResponse(Call call, Response response, String responseString) throws IOException {
                        if (response.isSuccessful()) {
                            setProgress("shareable", false);
                            String status = responseString;

                            //TODO check for empty string
                            if (status == null) {
                                return;
                            }

                            SurespotLog.d(TAG, "setting message sharable via http: %s", status);
                            if (status.equals("shareable")) {
                                message.setShareable(true);
                            }
                            else if (status.equals("notshareable")) {
                                message.setShareable(false);
                            }

                            chatAdapter.notifyDataSetChanged();
                        }
                        else {
                            SurespotLog.i(TAG, "toggleMessageShareable error response code: %d", response.code());
                            setProgress("shareable", false);
                            Utils.makeToast(mContext, mContext.getString(R.string.could_not_set_message_lock_state));
                        }
                    }
                }));
            }
        }
    }

    public FriendAdapter getFriendAdapter() {
        return mFriendAdapter;
    }

    public boolean isFriendDeleted(String username) {
        return getFriendAdapter().getFriend(username).isDeleted();
    }

    //needs to be run on UI thread
    private void getFriendsAndData() {
        SurespotLog.d(TAG, "getFriendsAndData: friend count: %d, mLatestUserControlId: %d", mFriendAdapter.getCount(), mLatestUserControlId);
        if (mFriendAdapter.getCount() == 0 || mLatestUserControlId == 0) {
            setProgress("friendsAndData", true);
            mFriendAdapter.setLoading(true);
            // get the list of friends
            mNetworkController.getFriends(new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {

                @Override
                public void onFailure(Call call, final IOException e) {
                    setProgress("friendsAndData", false);
                    if (!mNetworkController.isUnauthorized()) {
                        mFriendAdapter.setLoading(false);
                        SurespotLog.w(TAG, e, "getFriendsAndData error");
                    }
                }

                @Override
                public void onResponse(Call call, final Response response, final String responseString) throws IOException {

                    if (response.isSuccessful()) {
                        SurespotLog.v(TAG, "getFriends success.");
                        ArrayList<Friend> friends = new ArrayList<Friend>();
                        boolean userSuddenlyHasFriends = false;
                        try {
                            JSONObject jsonObject = new JSONObject(responseString);
                            JSONArray friendsArray = jsonObject.optJSONArray("friends");

                            //set latest user control id
                            mLatestUserControlId = jsonObject.optInt("userControlId", mLatestUserControlId);
                            SurespotLog.v(TAG, "getFriendsAndData setting mLatestUserControlId to: %d", mLatestUserControlId);

                            if (friendsArray != null) {
                                for (int i = 0; i < friendsArray.length(); i++) {
                                    JSONObject jsonFriend = friendsArray.getJSONObject(i);

                                    Friend friend = Friend.toFriend(jsonFriend);
                                    friends.add(friend);

                                    //    SurespotLog.v(TAG, "getFriendsAndData, adding friend: %s", friend);
                                }
                            }
                            if (friends.size() > 0) {
                                userSuddenlyHasFriends = true;
                            }
                        }
                        catch (JSONException e) {
                            SurespotLog.e(TAG, e, "getFriendsAndData error");
                            mFriendAdapter.setLoading(false);
                            setProgress("friendsAndData", false);
                            return;
                        }

                        if (mFriendAdapter != null) {
                            mFriendAdapter.addFriends(friends);
                            mFriendAdapter.setLoading(false);
                        }

                        getLatestData(userSuddenlyHasFriends);
                    }
                    else {
                        if (!mNetworkController.isUnauthorized()) {
                            mFriendAdapter.setLoading(false);
                            SurespotLog.w(TAG, "getFriendsAndData error");
                        }
                    }
                    setProgress("friendsAndData", false);
                }
            }));
        }
        else {
            getLatestData(false);
        }
    }

    public void closeTab() {
        if (mChatPagerAdapter.getCount() > 0) {

            int position = mViewPager.getCurrentItem();
            if (position > 0) {

                String name = mChatPagerAdapter.getChatName(position);
                if (name != null) {
                    SurespotLog.d(TAG, "closeTab, name: %s, position: %d", name, position);

                    mChatPagerAdapter.removeChat(mViewPager.getId(), position);
                    mFriendAdapter.setChatActive(name, false);
                    mEarliestMessage.remove(name);
                    destroyChatAdapter(name);
                    mIndicator.notifyDataSetChanged();

                    position = mViewPager.getCurrentItem();
                    setCurrentChat(mChatPagerAdapter.getChatName(position));
                    SurespotLog.d(TAG, "closeTab, new tab name: %s, position: %d", mCurrentChat, position);
                }
            }
        }
    }

    public void closeAllTabs() {
        int count = mChatPagerAdapter.getCount() - 1;
        if (count > 0) {
            for (int position = count; position > 0; position--) {
                String name = mChatPagerAdapter.getChatName(position);
                if (name != null) {
                    SurespotLog.d(TAG, "closeTab, name: %s, position: %d", name, position);

                    mChatPagerAdapter.removeChat(mViewPager.getId(), position);
                    mFriendAdapter.setChatActive(name, false);
                    mEarliestMessage.remove(name);
                    destroyChatAdapter(name);
                    SurespotLog.d(TAG, "closeTab, new tab name: %s, position: %d", mCurrentChat, position);
                }
            }

            mIndicator.notifyDataSetChanged();
            setCurrentChat(null);
        }
    }


    /**
     * Called when a user has been deleted
     *
     * @param username
     */

    public void closeTab(String username) {
        if (mChatPagerAdapter.getCount() > 0) {

            int position = mChatPagerAdapter.getChatFragmentPosition(username);
            if (position > 0) {

                String name = mChatPagerAdapter.getChatName(position);
                if (name != null) {
                    SurespotLog.d(TAG, "closeTab, name: %s, position: %d", name, position);

                    mChatPagerAdapter.removeChat(mViewPager.getId(), position);
                    mFriendAdapter.setChatActive(name, false);
                    mEarliestMessage.remove(name);
                    destroyChatAdapter(name);

                    mIndicator.notifyDataSetChanged();

                    position = mViewPager.getCurrentItem();
                    setCurrentChat(mChatPagerAdapter.getChatName(position));
                    SurespotLog.d(TAG, "closeTab, new tab name: %s, position: %d", mCurrentChat, position);
                }
            }
        }
    }

    public synchronized boolean setMode(int mode) {
        // can only select a user if we have users
        if (mode == MODE_SELECT) {
            if (mFriendAdapter.getFriendCount() == 0) {
                return false;
            }
        }

        mMode = mode;
        return true;
    }

    public int getMode() {
        return mMode;
    }

    public void enableMenuItems(Friend friend) {
        boolean enabled = mMode != MODE_SELECT && mCurrentChat != null;
        SurespotLog.v(TAG, "enableMenuItems, enabled: %b", enabled);

        boolean isDeleted = false;
        if (friend != null) {
            isDeleted = friend.isDeleted();
        }

        if (mMenuItems != null) {
            for (MenuItem menuItem : mMenuItems) {
                //close all tabs only on home tab
                if ((menuItem.getItemId() == R.id.menu_close_all_tabs)) {
                    menuItem.setVisible(mCurrentChat == null);
                }
                // deleted users can't have images sent to them
//                if (menuItem.getItemId() == R.id.menu_capture_image_bar || menuItem.getItemId() == R.id.menu_send_image_bar) {
//
//                    menuItem.setVisible(enabled && !isDeleted);
//                }
                else {
                    menuItem.setVisible(enabled);
                }
            }
        }
    }

    public void scrollToEnd(String to) {
        SurespotLog.d(TAG, "scrollToEnd %s", to);
        ChatFragment chatFragment = getChatFragment(to);
        if (chatFragment != null) {
            chatFragment.scrollToEnd();
        }

    }

    public void setImageUrl(String name, String url, String version, String iv, boolean hashed) {
        Friend friend = mFriendAdapter.getFriend(name);
        if (friend != null) {
            String oldUrl = friend.getImageUrl();
            if (!TextUtils.isEmpty(oldUrl)) {
                mNetworkController.removeCacheEntry(oldUrl);
            }

            friend.setImageUrl(url);
            friend.setImageIv(iv);
            friend.setImageVersion(version);
            friend.setImageHashed(hashed);
            saveFriends();
            mFriendAdapter.notifyDataSetChanged();
        }
    }

    public void setFriendAlias(final String name, String data, String version, String iv, boolean hashed) {
        final Friend friend = mFriendAdapter.getFriend(name);
        if (friend != null) {
            friend.setAliasData(data);
            friend.setAliasIv(iv);
            friend.setAliasVersion(version);
            friend.setAliasHashed(hashed);

            new AsyncTask<Void, Void, String>() {

                @Override
                protected String doInBackground(Void... params) {
                    String plainText = EncryptionController.symmetricDecrypt(mContext, mUsername, friend.getAliasVersion(), mUsername,
                            friend.getAliasVersion(), friend.getAliasIv(), friend.isAliasHashed(), friend.getAliasData());
                    Utils.putAlias(mContext, mUsername, name, plainText);
                    return plainText;
                }

                protected void onPostExecute(String plainAlias) {

                    friend.setAliasPlain(plainAlias);

                    saveFriends();
                    mFriendAdapter.notifyFriendAliasChanged();
                    mFriendAdapter.sort();
                    mFriendAdapter.notifyDataSetChanged();
                }
            }.execute();
        }
    }

    public SurespotMessage getLiveMessage(SurespotMessage message) {
        String otherUser = message.getOtherUser(mUsername);
        ChatAdapter chatAdapter = mChatAdapters.get(otherUser);
        if (chatAdapter != null) {
            return chatAdapter.getMessageByIv(message.getIv());
        }

        return null;
    }

    // called from GCM service
    public boolean addMessageExternal(final SurespotMessage message) {
        // might not be same user so check that to is the currently logged in user
        boolean sameUser = message.getTo().equals(mUsername);
        if (!sameUser) {
            SurespotLog.d(TAG, "addMessageExternal: different user, not adding message");
            return false;
        }
        else {
            final ChatAdapter chatAdapter = mChatAdapters.get(message.getFrom());
            if (chatAdapter == null) {
                SurespotLog.d(TAG, "addMessageExternal: chatAdapter null, not adding message");
                return false;
            }
            else {
                return applyControlMessages(chatAdapter, message, false, true, false);
            }
        }
    }

    public String getAliasedName(String name) {
        Friend friend = mFriendAdapter.getFriend(name);
        if (friend != null) {
            return friend.getNameOrAlias();
        }
        return null;
    }

    private void removeFriendAlias(String name) {
        final Friend friend = mFriendAdapter.getFriend(name);
        Utils.removeAlias(mContext, mUsername, name);
        if (friend != null) {
            friend.setAliasData(null);
            friend.setAliasIv(null);
            friend.setAliasVersion(null);
            friend.setAliasPlain(null);
            saveFriends();
            mFriendAdapter.notifyFriendAliasChanged();
            mFriendAdapter.sort();
            mFriendAdapter.notifyDataSetChanged();
        }
    }

    public void removeFriendAlias(final String name, final IAsyncCallback<Boolean> iAsyncCallback) {
        setProgress("removeFriendAlias", true);
        mNetworkController.deleteFriendAlias(name, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {

            @Override
            public void onFailure(Call call, IOException e) {
                SurespotLog.w(TAG, e, "error removing friend alias: %s", name);
                setProgress("removeFriendAlias", false);
                iAsyncCallback.handleResponse(false);
            }

            @Override
            public void onResponse(Call call, Response response, String responseString) throws IOException {
                if (response.isSuccessful()) {
                    removeFriendAlias(name);
                    setProgress("removeFriendAlias", false);
                    iAsyncCallback.handleResponse(true);
                }
                else {
                    SurespotLog.w(TAG, "error removing friend alias, response code: %d", response.code());
                    setProgress("removeFriendAlias", false);
                    iAsyncCallback.handleResponse(false);
                }
            }
        }));
    }

    private void removeFriendImage(String name) {
        final Friend friend = mFriendAdapter.getFriend(name);
        if (friend != null) {
            String oldUrl = friend.getImageUrl();
            if (!TextUtils.isEmpty(oldUrl)) {
                mNetworkController.removeCacheEntry(oldUrl);
            }
            friend.setImageIv(null);
            friend.setImageUrl(null);
            friend.setImageVersion(null);
            saveFriends();
            mChatPagerAdapter.sort();
            mChatPagerAdapter.notifyDataSetChanged();
            mIndicator.notifyDataSetChanged();
            mFriendAdapter.sort();
            mFriendAdapter.notifyDataSetChanged();
        }
    }

    public void removeFriendImage(final String name, final IAsyncCallback<Boolean> iAsyncCallback) {
        setProgress("removeFriendImage", true);
        mNetworkController.deleteFriendImage(name, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {

            @Override
            public void onFailure(Call call, IOException e) {
                SurespotLog.w(TAG, e, "error removing friend image for: %s", name);
                setProgress("removeFriendImage", false);
                iAsyncCallback.handleResponse(false);
            }

            @Override
            public void onResponse(Call call, Response response, String responseString) throws IOException {
                if (response.isSuccessful()) {
                    removeFriendImage(name);
                    setProgress("removeFriendImage", false);
                    iAsyncCallback.handleResponse(true);
                }
                else {
                    SurespotLog.w(TAG, "error removing friend image, response code: %d", response.code());
                    setProgress("removeFriendImage", false);
                    iAsyncCallback.handleResponse(false);
                }
            }
        }));
    }

    public void assignFriendAlias(final String name, String alias, final IAsyncCallback<Boolean> iAsyncCallback) {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(alias)) {
            return;
        }

        setProgress("assignFriendAlias", true);
        final String version = IdentityController.getOurLatestVersion(mContext, mUsername);

        byte[] iv = EncryptionController.getIv();
        final String cipherAlias = EncryptionController.symmetricEncrypt(mContext, mUsername, version, mUsername, version, alias, iv);
        final String ivString = new String(ChatUtils.base64EncodeNowrap(iv));

        mNetworkController.assignFriendAlias(name, version, cipherAlias, ivString, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {

            @Override
            public void onFailure(Call call, IOException e) {
                SurespotLog.w(TAG, e, "error assigning friend alias: %s", name);
                setProgress("assignFriendAlias", false);
                iAsyncCallback.handleResponse(false);
            }

            @Override
            public void onResponse(Call call, Response response, String responseString) throws IOException {
                if (response.isSuccessful()) {
                    setFriendAlias(name, cipherAlias, version, ivString, true);
                    setProgress("assignFriendAlias", false);
                    iAsyncCallback.handleResponse(true);
                }
                else {
                    SurespotLog.w(TAG, "error assigning friend alias, response code: %d", response.code());
                    setProgress("assignFriendAlias", false);
                    iAsyncCallback.handleResponse(false);
                }
            }
        }));
    }

    public String getUsername() {
        return mUsername;
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


            //override ssl context for self signed certs for dev
            if (!SurespotConfiguration.isSslCheckingStrict()) {
                IO.setDefaultOkHttpCallFactory(mNetworkController.getClient());
                IO.setDefaultOkHttpWebSocketFactory(mNetworkController.getClient());
                //opts.sslContext = mNetworkController.getSSLContext();
                //opts.hostnameVerifier = mNetworkController.getHostnameVerifier();
            }

            opts.reconnection = false;
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
                            Cookie cookie = IdentityController.getCookieForUser(mContext, mUsername);
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

    synchronized void connect() {

        if (mMainActivityPaused) {
            SurespotLog.d(TAG, "connect, mMainActivityPaused true, doing nothing");

            // if the communication service wants to stay connected again any time in the future, disable the below statement
            return;
        }

        SurespotLog.d(TAG, "connect, mSocket: " + mSocket + ", connected: " + (mSocket != null ? mSocket.connected() : false) + ", state: " + mConnectionState);

        if (mSocket != null && getConnectionState() == STATE_CONNECTED) {
            //onConnected();
            return;
        }

        if (mSocket != null && getConnectionState() == STATE_CONNECTING) {
            // do NOT call already connected here, since we're not already connected
            // need to test to see if the program flow is good returning true here, or if we should allow things to continue
            // and try to connect()...
            return;
        }

        setState(STATE_CONNECTING);
        loadMessageQueue();
        onBeforeConnect();

        try {
            createSocket();
            mSocket.connect();
        }
        catch (Exception e) {
            SurespotLog.w(TAG, e, "connect");
        }

        return;
    }

    public synchronized void enqueueMessage(SurespotMessage message) {
        if (getConnectionState() == STATE_DISCONNECTED) {
            connect();
        }

        if (!mSendQueue.contains(message)) {
            mSendQueue.add(message);
            saveMessageQueue();
        }

        processNextMessage();
    }

    public synchronized void processNextMessage() {

        //if we're ERRORED do nothing
        if (mErrored) {
            SurespotLog.d(TAG, "processNextMessage in ERRORED state, doing nothing");
            return;
        }

        //SurespotLog.d(TAG, "processNextMessage, messages in queue: %d", mSendQueue.size());
        SurespotMessage nextMessage = mSendQueue.peek();
        //if the message is errored don't resend it, remove from queue
        while (nextMessage != null && nextMessage.getErrorStatus() > 0) {
            SurespotLog.d(TAG, "processNextMessage, removing errored message: %s", nextMessage.getIv());
            removeQueuedMessage(nextMessage, false);
            nextMessage = mSendQueue.peek();
        }

        if (nextMessage != null) {
            SurespotLog.d(TAG, "processNextMessage, currentIv: %s, next message iv: %s", mCurrentSendIv, nextMessage.getIv());
            if (nextMessage.getIv().equals(mCurrentSendIv)) {
                SurespotLog.i(TAG, "processNextMessage() still sending message, iv: %s", nextMessage.getIv());
            }
            else {
                mCurrentSendIv = nextMessage.getIv();

                //message processed successfully, onto the next
                SurespotLog.i(TAG, "processNextMessage() sending message, iv: %s", nextMessage.getIv());

                switch (nextMessage.getMimeType()) {
                    case SurespotConstants.MimeTypes.TEXT:
                    case SurespotConstants.MimeTypes.GIF_LINK:
                        prepAndSendTextMessage(nextMessage);
                        break;
                    case SurespotConstants.MimeTypes.IMAGE:
                    case SurespotConstants.MimeTypes.M4A:
                        prepAndSendFileMessage(nextMessage);
                        break;

                    case SurespotConstants.MimeTypes.FILE:
                        prepAndSendCloudMessage(nextMessage);
                }
            }
        }
    }

    private boolean isMessageReadyToSend(SurespotMessage message) {
        return !TextUtils.isEmpty(message.getData()) && !TextUtils.isEmpty(message.getFromVersion()) && !TextUtils.isEmpty(message.getToVersion());
    }


    private void prepAndSendTextMessage(final SurespotMessage message) {
        SurespotLog.d(TAG, "prepAndSendTextMessage, iv: %s", message.getIv());

        //make sure message is encrypted
        if (!isMessageReadyToSend(message)) {
            // do encryption in background
            Runnable runnable = new Runnable() {
                @Override
                public void run() {

                    synchronized (ChatController.this) {
                        final Boolean success = encryptMessage(message);

                        if (success != null) {
                            //update on ui thread
                            Runnable runnableUi = new Runnable() {
                                @Override
                                public void run() {
                                    addMessage(message);
                                    if (success) {
                                        sendTextMessage(message);
                                    }
                                    else {
                                        messageSendCompleted(message);
                                        if (!scheduleResendTimer()) {
                                            errorMessageQueue();
                                        }
                                    }
                                }
                            };

                            mHandler.post(runnableUi);
                        }
                    }
                }
            };

            SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);
        }
        else {
            sendTextMessage(message);
        }
    }

    private Boolean encryptMessage(SurespotMessage message) {
        //if plain data is null, already being handled, do nothing
        CharSequence plainData = message.getPlainData();
        if (plainData == null) {
            return null;
        }

        String ourLatestVersion = IdentityController.getOurLatestVersion(mContext, message.getFrom());
        String theirLatestVersion = IdentityController.getTheirLatestVersion(mContext, message.getFrom(), message.getTo());
        synchronized (ChatController.this) {

            if (theirLatestVersion == null) {
                SurespotLog.d(TAG, "could not encrypt message - could not get latest version, iv: %s", message.getIv());
                //retry
                message.setErrorStatus(0);
                return false;
            }


            byte[] iv = ChatUtils.base64DecodeNowrap(message.getIv());
            String result = EncryptionController.symmetricEncrypt(mContext, message.getFrom(), ourLatestVersion, message.getTo(), theirLatestVersion, plainData.toString(), iv);

            if (result != null) {
                //update unsent message
                message.setData(result);
                message.setFromVersion(ourLatestVersion);
                message.setToVersion(theirLatestVersion);
                return true;
            }
            else {
                SurespotLog.d(TAG, "could not encrypt message, iv: %s", message.getIv());
                message.setErrorStatus(500);
                return false;
            }
        }
    }

    private Boolean encryptCloudMessage(SurespotMessage message) {
        //if plain data is null, already being handled, do nothing
        SurespotMessage.FileMessageData fmd = message.getFileMessageData();
        if (fmd == null) {
            return null;
        }

        String ourLatestVersion = message.getOurVersion(message.getFrom());
        String theirLatestVersion = message.getTheirVersion(message.getFrom());
        synchronized (ChatController.this) {

            if (theirLatestVersion == null) {
                SurespotLog.d(TAG, "could not encrypt message - could not get latest version, iv: %s", message.getIv());
                //retry
                message.setErrorStatus(0);
                return false;
            }


            byte[] iv = ChatUtils.base64DecodeNowrap(message.getIv());
            String result = EncryptionController.symmetricEncrypt(mContext, message.getFrom(), ourLatestVersion, message.getTo(), theirLatestVersion, fmd.toJSONStringSocket(), iv);

            if (result != null) {
                //update unsent message
                message.setData(result);
                return true;
            }
            else {
                SurespotLog.d(TAG, "could not encrypt message, iv: %s", message.getIv());
                message.setErrorStatus(500);
                return false;
            }
        }
    }

    private synchronized void sendTextMessage(SurespotMessage message) {
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
            sendMessageUsingHttp(message);
        }
    }

    private void prepAndSendFileMessage(final SurespotMessage message) {
        SurespotLog.d(TAG, "prepAndSendFileMessage, current thread: %s", Thread.currentThread().getName());
        if (!isMessageReadyToSend(message)) {
            new AsyncTask<Void, Void, Boolean>() {

                @Override
                protected Boolean doInBackground(Void... arg0) {
                    //make sure it's pointing to a local file

                    synchronized (ChatController.this) {
                        //could be null because it's already being processed
                        CharSequence cs = message.getPlainData();
                        SurespotLog.d(TAG, "prepAndSendFileMessage: plainData: %s", cs);
                        if (cs == null) {
                            SurespotLog.d(TAG, "prepAndSendFileMessage: plainData null, already processed, doing nothing");
                            return null;
                        }

                        String plainData = cs.toString();

                        if (!plainData.startsWith("file")) {
                            message.setErrorStatus(500);
                            return false;
                        }

                        try {

                            final String ourVersion = IdentityController.getOurLatestVersion(mContext, message.getFrom());
                            final String theirVersion = IdentityController.getTheirLatestVersion(mContext, message.getFrom(), message.getTo());

                            if (theirVersion == null) {
                                SurespotLog.d(TAG, "prepAndSendFileMessage: could not encrypt file message - could not get latest version, iv: %s", message.getIv());
                                //retry
                                message.setErrorStatus(0);
                                return false;
                            }
                            final String iv = message.getIv();


                            // save encrypted image to disk
                            InputStream fileInputStream = mContext.getContentResolver().openInputStream(Uri.parse(plainData));
                            File localImageFile = ChatUtils.getTempImageUploadFile(mContext);
                            OutputStream fileSaveStream = new FileOutputStream(localImageFile);
                            String localImageUri = Uri.fromFile(localImageFile).toString();
                            SurespotLog.d(TAG, "prepAndSendFileMessage: encrypting file iv: %s, from %s to encrypted file %s", iv, plainData, localImageUri);

                            //encrypt
                            PipedOutputStream encryptionOutputStream = new PipedOutputStream();
                            final PipedInputStream encryptionInputStream = new PipedInputStream(encryptionOutputStream);
                            EncryptionController.runEncryptTask(mContext, mUsername, ourVersion, message.getTo(), theirVersion, iv, new BufferedInputStream(fileInputStream), encryptionOutputStream);

                            int bufferSize = 1024;
                            byte[] buffer = new byte[bufferSize];

                            int len = 0;
                            while ((len = encryptionInputStream.read(buffer)) != -1) {
                                fileSaveStream.write(buffer, 0, len);
                            }
                            fileSaveStream.close();
                            encryptionInputStream.close();

                            //move bitmap cache
                            if (message.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
                                MessageImageDownloader.moveCacheEntry(plainData, localImageUri);
                            }

                            //add encrypted local file to file cache
                            FileCacheController fcc = SurespotApplication.getFileCacheController();
                            if (fcc != null) {
                                fcc.putEntry(localImageUri, new FileInputStream(localImageFile));
                            }


                            boolean deleted = new File(Uri.parse(plainData).getPath()).delete();
                            SurespotLog.d(TAG, "prepAndSendFileMessage: deleting unencrypted file %s, iv: %s, success: %b", plainData, iv, deleted);


                            message.setPlainData(null);
                            message.setData(localImageUri);
                            message.setFromVersion(ourVersion);
                            message.setToVersion(theirVersion);

                            return true;
                        }
                        catch (IOException e) {
                            SurespotLog.w(TAG, e, "prepAndSendFileMessage");
                            message.setErrorStatus(500);
                            return false;
                        }
                    }
                }

                protected void onPostExecute(Boolean success) {
                    if (success != null) {
                        addMessage(message);
                        if (success) {
                            sendFileMessage(message);
                        }
                        else {
                            messageSendCompleted(message);
                            if (!scheduleResendTimer()) {
                                errorMessageQueue();
                            }
                        }
                    }
                }
            }.execute();
        }
        else {
            sendFileMessage(message);
        }
    }


    private void sendFileMessage(final SurespotMessage message) {
        SurespotLog.d(TAG, "sendFileMessage: %s", message);
        new AsyncTask<Void, Void, Tuple<Integer, JSONObject>>() {
            @Override
            protected Tuple<Integer, JSONObject> doInBackground(Void... voids) {
                //post message via http if we have network controller for the from user


                FileInputStream uploadStream;
                try {
                    SurespotLog.d(TAG, "sendFileMessage in thread: %s", message);
                    uploadStream = new FileInputStream(URI.create(message.getData()).getPath());

                    return mNetworkController.postFileStreamSync(
                            message.getOurVersion(message.getFrom()),
                            message.getTo(),
                            message.getTheirVersion(message.getFrom()),
                            message.getIv(),
                            uploadStream,
                            message.getMimeType());

                }
                catch (Exception e) {
                    SurespotLog.w(TAG, e, "sendFileMessage");
                    return new Tuple<>(500, null);
                }

            }

            @Override
            protected void onPostExecute(Tuple<Integer, JSONObject> result) {
                synchronized (this) {
                    messageSendCompleted(message);

                    //if message errored
                    int status = result.first;
                    SurespotMessage newMessage = null;
                    switch (status) {
                        case 401:
                            //401
                            //don't try and resend, just error
                            errorMessageQueue();
                            break;
                        case 200:
                            //update the message with returned data
                            SurespotLog.d(TAG, "sendFileMessage received 200, response: %s, updating UI", result.second);
                            JSONObject fileData = result.second;

                            //create a new message and set returned data so handle message works properly
                            try {
                                newMessage = SurespotMessage.toSurespotMessage(message.toJSONObject(false));
                                newMessage.setId(fileData.getInt("id"));
                                newMessage.setData(fileData.getString("url"));
                                newMessage.setDataSize(fileData.getInt("size"));
                                newMessage.setDateTime(new Date(fileData.getLong("time")));
                            }
                            catch (JSONException e) {
                                //json error
                                SurespotLog.w(TAG, e, "sendFileMessage: json error parsing file http response.");
                            }
                            //deliberate fall through to 409
                        case 409:
                            SurespotLog.d(TAG, "sendFileMessage received 409");
                            //success
                            clearError();

                            //update ui
                            if (newMessage != null) {
                                handleMessage(newMessage, new IAsyncCallback<Object>() {
                                    @Override
                                    public void handleResponse(Object result) {
                                        saveIfMainActivityPaused(message.getTo());

                                    }
                                });
                            }
                            //need to remove the message from the queue before setting the current send iv to null
                            removeQueuedMessage(message);
                            break;
                        default:
                            //try and send next message again
                            if (!scheduleResendTimer()) {
                                errorMessageQueue();
                            }
                            break;
                    }
                }
            }
        }.execute();
    }

    private void sendMessageUsingHttp(final SurespotMessage message) {
        SurespotLog.d(TAG, "sendMessagesUsingHttp, iv: %s", message.getIv());

        ArrayList<SurespotMessage> toSend = new ArrayList<SurespotMessage>();
        toSend.add(message);
        mNetworkController.postMessages(toSend, new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {

            @Override
            public void onFailure(Call call, IOException e) {
                messageSendCompleted(message);

                SurespotLog.w(TAG, e, "sendMessagesUsingHttp onFailure");
                //try and send next message again
                if (!scheduleResendTimer()) {
                    errorMessageQueue();
                }
            }

            @Override
            public void onResponse(Call call, Response response, String responseString) throws IOException {
                messageSendCompleted(message);


                if (response.isSuccessful()) {
                    clearError();
                    try {
                        JSONObject json = new JSONObject(responseString);
                        JSONArray messages = json.getJSONArray("messageStatus");
                        JSONObject messageAndStatus = messages.getJSONObject(0);
                        JSONObject jsonMessage = messageAndStatus.getJSONObject("message");
                        int status = messageAndStatus.getInt("status");

                        if (status == 204) {
                            final SurespotMessage messageReceived = SurespotMessage.toSurespotMessage(jsonMessage);
                            //update the UI
                            handleMessage(messageReceived, new IAsyncCallback<Object>() {
                                @Override
                                public void handleResponse(Object result) {
                                    saveIfMainActivityPaused(message.getTo());

                                    //need to remove the message from the queue before setting the current send iv to null
                                    removeQueuedMessage(messageReceived);
                                }
                            });
                        }
                        else {
                            //try and send next message again
                            if (!scheduleResendTimer()) {
                                errorMessageQueue();
                            }
                        }
                    }
                    catch (JSONException e) {
                        SurespotLog.w(TAG, e, "JSON received from server");
                        //try and send next message again
                        if (!scheduleResendTimer()) {
                            errorMessageQueue();
                        }
                    }

                }
                else {
                    SurespotLog.w(TAG, "sendMessagesUsingHttp response error code: %d", response.code());
                    //try and send next message again
                    if (!scheduleResendTimer()) {
                        errorMessageQueue();
                    }
                }
            }
        }));
    }

    public synchronized void messageSendCompleted(SurespotMessage message) {
        //if we're not onto a different message, set the current message pointer to null

        if (message.getIv().equals(mCurrentSendIv)) {
            //SurespotLog.d(TAG, "messageSendCompleted iv's the same, setting to null, mCurrentSendIv: %s, messageIv: %s", mCurrentSendIv, message.getIv());
            mCurrentSendIv = null;
        }
        else {
            //SurespotLog.d(TAG, "messageSendCompleted iv's not the same, doing nothing, mCurrentSendIv: %s, messageIv: %s", mCurrentSendIv, message.getIv());
        }

    }

    public synchronized int getConnectionState() {
        return mConnectionState;
    }

    // saves all data and current state for user, general
    public synchronized void save() {
        SurespotLog.d(TAG, "save");
        saveMessages();
        saveFriends();
        saveMessageQueue();


        SurespotLog.d(TAG, "saving last chat: %s", getCurrentChat());
        Utils.putUserSharedPrefsString(mContext, mUsername, SurespotConstants.PrefNames.LAST_CHAT, getCurrentChat());
    }

    private void saveIfMainActivityPaused(String theirUsername) {
        if (mMainActivityPaused) {
            saveMessages(theirUsername);
        }
    }

    public synchronized boolean isConnected() {
        return getConnectionState() == STATE_CONNECTED;
    }

    public synchronized void errorMessageQueue() {
        SurespotLog.d(TAG, "errorMessageQueue");

        saveMessageQueue();
        saveMessages();

        // raise Android notifications for unsent messages so the user can re-enter the app and retry sending if we haven't already
        if (!mErrored && !mSendQueue.isEmpty()) {
            raiseNotificationForUnsentMessages();
        }

        //cancel timers
        stopReconnectionAttempts();
        stopResendTimer();

        mErrored = true;
        mCurrentSendIv = null;
    }

    public synchronized void clearMessageQueue(String friendname) {
        Iterator<SurespotMessage> iterator = mSendQueue.iterator();
        while (iterator.hasNext()) {
            SurespotMessage message = iterator.next();
            if (message.getTo().equals(friendname)) {
                iterator.remove();
            }
        }
        saveMessageQueue();
    }

    synchronized void removeQueuedMessage(SurespotMessage message) {
        removeQueuedMessage(message, true);
    }

    synchronized void removeQueuedMessage(SurespotMessage message, boolean process) {
        boolean removed = false;

        if (mSendQueue.size() > 0) {
            Iterator<SurespotMessage> iterator = mSendQueue.iterator();
            while (iterator.hasNext()) {
                SurespotMessage m = iterator.next();
                if (m.getIv().equals(message.getIv())) {
                    iterator.remove();
                    removed = true;
                }
            }
        }

        if (removed) {
            saveMessageQueue();
        }

        if (process) {
            processNextMessage();
        }
        //SurespotLog.d(TAG, "removedQueuedMessage, iv: %s, removed: %b", message.getIv(), removed);
    }


    // chat adapters and state
    private synchronized void saveMessages() {
        // save last 30? messages
        SurespotLog.d(TAG, "saveMessages, mUsername: %s", mUsername);
        if (mUsername != null && mChatAdapters != null) {
            for (Map.Entry<String, ChatAdapter> entry : mChatAdapters.entrySet()) {
                String them = entry.getKey();
                String spot = ChatUtils.getSpot(mUsername, them);
                ChatAdapter adapter = entry.getValue();
                SurespotApplication.getStateController().saveMessages(mUsername, spot, adapter.getMessages());
            }
        }
    }

    public synchronized void saveMessages(String username) {
        // save last 30? messages
        SurespotLog.d(TAG, "saveMessages, username: %s", username);
        ChatAdapter chatAdapter = mChatAdapters.get(username);

        if (chatAdapter != null) {
            SurespotApplication.getStateController().saveMessages(mUsername, ChatUtils.getSpot(mUsername, username), chatAdapter.getMessages());
        }
    }

    private synchronized void saveMessageQueue() {
        SurespotLog.d(TAG, "saving: " + mSendQueue.size() + " unsent messages.");
        SurespotApplication.getStateController().saveUnsentMessages(mUsername, mSendQueue);
    }

    private synchronized void loadMessageQueue() {
        // if we do below we create a different instance of the message in the queue, which borks file sending because
        // we are using a property in the message to figure out it's state
        List<SurespotMessage> unsentMessages = SurespotApplication.getStateController().loadUnsentMessages(mUsername);
        Iterator<SurespotMessage> iterator = unsentMessages.iterator();
        while (iterator.hasNext()) {
            final SurespotMessage message = iterator.next();

            if (!mSendQueue.contains(message)) {
                mSendQueue.add(message);
            }

            //make sure the message is in the adapter so we can see it
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    addMessage(message);
                }
            };
            mHandler.post(runnable);

        }
        SurespotLog.d(TAG, "loaded: " + mSendQueue.size() + " unsent messages.");
    }

    // notify listeners that we've connected
    private void onConnected() {

        SurespotLog.d(TAG, "onConnected, mErrored: %b", mErrored);
        setState(STATE_CONNECTED);

        //if we reconnected after error
        clearError();

        stopReconnectionAttempts();
        stopResendTimer();

        connected();
        processNextMessage();
    }

    private int generateInterval(int k) {
        int timerInterval = (int) (Math.pow(2, k) * 1000);
        if (timerInterval > MAX_RETRY_DELAY * 1000) {
            timerInterval = MAX_RETRY_DELAY * 1000;
        }

        int reconnectTime = (int) (Math.random() * timerInterval);
        SurespotLog.d(TAG, "generated interval: %d for k: %d", reconnectTime, k);
        return reconnectTime;
    }

    // stop reconnection attempts
    private synchronized void stopReconnectionAttempts() {
        if (mBackgroundTimer != null) {
            mBackgroundTimer.cancel();
            mBackgroundTimer = null;
        }
        if (mReconnectTask != null) {
            boolean cancel = mReconnectTask.cancel();
            mReconnectTask = null;
            SurespotLog.d(TAG, "Cancelled reconnect task: " + cancel);
        }
        mSocketReconnectRetries = 0;
    }

    private synchronized void scheduleReconnectionAttempt() {
        int timerInterval = generateInterval(mSocketReconnectRetries++);
        SurespotLog.d(TAG, "reconnection timer try %d starting another task in: %d", mSocketReconnectRetries - 1, timerInterval);

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

    private synchronized boolean scheduleResendTimer() {
        SurespotLog.d(TAG, "scheduleResendTimer, mHttpResendTries: %d, MAX_RETRIES: %d", mHttpResendTries, MAX_RETRIES);

        if (mHttpResendTries++ < MAX_RETRIES) {
            int timerInterval = generateInterval(mHttpResendTries);
            SurespotLog.d(TAG, "resend timer try %d starting another task in: %d", mHttpResendTries - 1, timerInterval);


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

            return true;
        }
        else {
            return false;
        }
    }

    private synchronized void stopResendTimer() {
        if (mResendViaHttpTimer != null) {
            mResendViaHttpTimer.cancel();
            mResendViaHttpTimer = null;
        }
        if (mResendTask != null) {
            boolean cancel = mResendTask.cancel();
            mResendTask = null;
            SurespotLog.d(TAG, "Cancelled resend task: " + cancel);
        }
        mHttpResendTries = 0;

    }


    // shutdown any connection we have open to the server, close sockets, check if service should shut down
    private void shutdownConnection() {
        disconnect();
        stopReconnectionAttempts();
    }

    private synchronized void setState(int state) {
        mConnectionState = state;
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


    @SuppressWarnings("ResourceAsColor")
    private void raiseNotificationForUnsentMessages() {
        mBuilder.setAutoCancel(true).setOnlyAlertOnce(true);
        SharedPreferences pm = null;
        if (mUsername != null) {
            pm = mContext.getSharedPreferences(mUsername, Context.MODE_PRIVATE);
        }

        int icon = R.drawable.surespot_logo;

        // need to use same builder for only alert once to work:
        // http://stackoverflow.com/questions/6406730/updating-an-ongoing-notification-quietly
        mBuilder.setSmallIcon(icon).setContentTitle(mContext.getString(R.string.error_sending_messages)).setAutoCancel(true).setOnlyAlertOnce(false).setContentText(mContext.getString(R.string.error_sending_detail));
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(mContext);

        Intent mainIntent = null;
        mainIntent = new Intent(mContext, MainActivity.class);
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
        int color = pm == null ? 0xff0000FF : pm.getInt("pref_notification_color", mContext.getResources().getColor(R.color.surespotBlue));

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
    }


    public synchronized void disconnect() {
        SurespotLog.d(TAG, "disconnect.");
        if (mConnectionState != STATE_DISCONNECTED) {
            setState(STATE_DISCONNECTED);
        }
        if (mSocket != null) {
            mSocket.disconnect();
            disposeSocket();
        }
    }


    private void tryReLogin() {
        SurespotLog.d(TAG, "trying to relogin " + mUsername);
        NetworkHelper.reLogin(mContext, mUsername, new CookieResponseHandler() {
            private String TAG = "ReLoginCookieResponseHandler";

            @Override
            public void onSuccess(int responseCode, String result, Cookie cookie) {
                //try again
                connect();
            }

            @Override
            public void onFailure(Throwable arg0, int code, String content) {
                //if we're getting 401 bail
                if (code == 401) {
                    // give up
                    if (m401Handler != null) {

                        SurespotLog.i(TAG, "401 on reconnect, giving up.");
                        m401Handler.handleResponse(null);

                    }

                    logout();
                }
                else {
                    //try and connect again
                    connect();
                }
            }
        });
    }


    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            SurespotLog.d(TAG, "mSocket.io connection established");
            mCurrentSendIv = null;
            onConnected();
        }
    };

    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            SurespotLog.d(TAG, "Connection terminated.");
            mCurrentSendIv = null;
            disconnect();

            if (args.length > 0) {
                if ("io server disconnect".equals(args[0])) {
                    SurespotLog.d(TAG, "got server disconnect from websocket");
                    tryReLogin();
                    return;
                }
            }


            connect();
            processNextMessage();
        }
    };

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if (args.length > 0) {
                String reason = args[0].toString();
                if (args[0] instanceof EngineIOException) {
                    reason = ((EngineIOException) args[0]).getCause().toString();
                }
                SurespotLog.d(TAG, "onConnectError: args: %s", reason);
            }

            //force queue
            mCurrentSendIv = null;
            disconnect();

            if (args.length > 0) {
                if ("not authorized".equals(args[0])) {
                    SurespotLog.d(TAG, "got not authorized from websocket");
                    tryReLogin();
                    return;
                }
            }

            SurespotLog.i(TAG, "an Error occured, attempting reconnect with exponential backoff, retries: %d", mSocketReconnectRetries);

            // kick off another task
            if (mSocketReconnectRetries < MAX_RETRIES) {
                if (!mMainActivityPaused) {
                    scheduleReconnectionAttempt();
                }

                //try and send messages via http
                processNextMessage();
            }
            else {
                SurespotLog.i(TAG, "Socket.io reconnect retries exhausted, giving up.");

                //mark all messages errored
                errorMessageQueue();
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

                        //if the server says it errored we're fucked so don't bother trying to send it again
                        SurespotMessage message = null;
                        Iterator<SurespotMessage> iterator = mSendQueue.iterator();
                        while (iterator.hasNext()) {
                            message = iterator.next();
                            if (message.getIv().equals(errorMessage.getId())) {
                                iterator.remove();
                                message.setErrorStatus(errorMessage.getStatus());
                                break;
                            }
                        }

                        if (message != null) {
                            //update chat controller message
                            addMessage(message);
                        }
                        processNextMessage();
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
                        handleControlMessage(null, message, true, false);
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
                    try {
                        final JSONObject jsonMessage = (JSONObject) args[0];
                        SurespotLog.d(TAG, "received message: " + jsonMessage.toString());
                        final SurespotMessage message = SurespotMessage.toSurespotMessage(jsonMessage);
                        handleMessage(message, new IAsyncCallback<Object>() {
                            @Override
                            public void handleResponse(Object result) {
                                // see if we have deletes
                                String sDeleteControlMessages = jsonMessage.optString("deleteControlMessages", null);
                                if (sDeleteControlMessages != null) {
                                    try {
                                        JSONArray deleteControlMessages = new JSONArray(sDeleteControlMessages);

                                        if (deleteControlMessages.length() > 0) {
                                            for (int i = 0; i < deleteControlMessages.length(); i++) {
                                                try {
                                                    SurespotControlMessage dMessage = SurespotControlMessage.toSurespotControlMessage(new JSONObject(deleteControlMessages.getString(i)));
                                                    handleControlMessage(null, dMessage, true, false);
                                                }
                                                catch (JSONException e) {
                                                    SurespotLog.w(TAG, e, "on control");
                                                }
                                            }
                                        }
                                    }
                                    catch (JSONException e) {
                                        SurespotLog.w(TAG, e, "on control");
                                    }
                                }

                                messageSendCompleted(message);
                                removeQueuedMessage(message);
                                saveIfMainActivityPaused(message.getOtherUser(mUsername));
                            }
                        });


                    }
                    catch (JSONException e) {
                        SurespotLog.w(TAG, "on message", e);
                        processNextMessage();
                    }
                }
            };

            mHandler.post(runnable);
        }
    };


    public void clearError() {
        mErrored = false;
    }

    synchronized void setMainActivityPaused() {
        mMainActivityPaused = true;
    }

    public boolean notifyChatAdapterDataSetChanged(String username) {
        final ChatAdapter chatAdapter = mChatAdapters.get(username);
        if (chatAdapter != null) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    chatAdapter.notifyDataSetChanged();
                }

            };
            mHandler.post(runnable);
            return true;
        }
        return false;
    }

    private void prepAndSendCloudMessage(final SurespotMessage message) {
        SurespotLog.d(TAG, "prepAndSendCloudMessage, current thread: %s", Thread.currentThread().getName());
        if (!isMessageReadyToSend(message)) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {

                    //make sure it's pointing to a local file

                    synchronized (message) {
                        //could be null because it's already being processed
                        SurespotMessage.FileMessageData fmd = message.getFileMessageData();
                        SurespotLog.d(TAG, "prepAndSendCloudMessage: plainData: %s", fmd.toString());
                        if (fmd == null) {
                            SurespotLog.d(TAG, "prepAndSendCloudMessage: fmd null");
                            return;
                        }

                        if (!TextUtils.isEmpty(fmd.getCloudUrl())) {
                            SurespotLog.d(TAG, "prepAndSendCloudMessage: data already encrypted, doing nothing");
                            return;
                        }

                        final String ourVersion = IdentityController.getOurLatestVersion(mContext, message.getFrom());
                        final String theirVersion = IdentityController.getTheirLatestVersion(mContext, message.getFrom(), message.getTo());
                        final String iv = message.getIv();

                        if (theirVersion == null) {
                            SurespotLog.d(TAG, "prepAndSendCloudMessage: could not encrypt file message - could not get latest version, iv: %s", message.getIv());
                            //retry
                            message.setErrorStatus(0);
                            return;
                        }

                        PipedInputStream encryptionInputStream;
                        InputStream fileInputStream;
                        try {
                            fileInputStream = mContext.getContentResolver().openInputStream(Uri.parse(fmd.getLocalUri()));
                            //encrypt
                            PipedOutputStream encryptionOutputStream = new PipedOutputStream();
                            encryptionInputStream = new PipedInputStream(encryptionOutputStream);
                            EncryptionController.runEncryptTask(mContext, message.getFrom(), ourVersion, message.getTo(), theirVersion, iv, new BufferedInputStream(fileInputStream), encryptionOutputStream);

                        }
                        catch (Exception e) {
                            //TODO this could be ugly, don't have access to the content anymore ie if process stops which is how we'd end up here
                            SurespotLog.w(TAG, "exception opening data: %s", fmd.getLocalUri());
                            //TODO error?
//                            message.setPlainData(plainData);
//                            messageSendCompleted(message);
//                            if (!scheduleResendTimer()) {
//                                errorMessageQueue();
//                            }
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    ChatController.this.deleteMessage(message, false);
                                }
                            });

                            return;
                        }

                        FileTransferUtils.createFile(
                                mContext,
                                mDriveHelper,
                                message.getFrom(),
                                encryptionInputStream,
                                new IAsyncCallback<SurespotMessage.FileMessageData>() {

                                    @Override
                                    public void handleResponse(final SurespotMessage.FileMessageData fileMessageData) {
                                        if (fileMessageData != null) {
                                            SurespotLog.d(TAG, "received file message data: %s", fileMessageData);

                                            message.getFileMessageData().setCloudUrl(fileMessageData.getCloudUrl());
                                            message.getFileMessageData().setSize(fileMessageData.getSize());
                                            message.setFromVersion(ourVersion);
                                            message.setToVersion(theirVersion);

                                            //encrypt the url to the encrypted drive data
                                            final Boolean success = encryptCloudMessage(message);

                                            SurespotLog.d(TAG, "success: %b", success);
                                            if (success != null) {
                                                mHandler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        addMessage(message);
                                                        if (success) {

                                                            //set the url to the encrypted drive data

                                                            sendTextMessage(message);
                                                        }
                                                        else {
                                                            //save the link to the local file so we can open it
                                                            //   message.setPlainData(plainData);
                                                            messageSendCompleted(message);
                                                            if (!scheduleResendTimer()) {
                                                                errorMessageQueue();
                                                            }
                                                        }
                                                    }
                                                });
                                            }
                                        }
                                    }
                                });
                    }
                }
            };

            SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);
        }
        else {
            sendTextMessage(message);
        }
    }

    public void handleVoiceMessagePlayed(SurespotMessage playedMessage) {
        ChatFragment cf = getChatFragment(playedMessage.getFrom());
        if (cf != null) {
            ListView messageListView = cf.getView().findViewById(R.id.message_list);
            ChatAdapter chatAdapter = getChatAdapter(playedMessage.getFrom(), false);
            if (chatAdapter != null) {
                if (playedMessage.getTo().equals(mUsername) && playedMessage.getFrom().equals(mCurrentChat)) {
                    int lastPlayedId = playedMessage.getId();
                    for (SurespotMessage message : chatAdapter.getMessages()) {
                        if (message.getId() != null && message.getId() > lastPlayedId && message.getMimeType().equals(SurespotConstants.MimeTypes.M4A) && !message.isVoicePlayed()) {
                            VoiceController.playVoiceMessage(mContext, getSeekBarForMessage(messageListView, message), message);
                            break;
                        }
                    }
                }
            }
        }
    }

    private SeekBar getSeekBarForMessage(ListView listView, SurespotMessage message) {
        final int firstListItemPosition = 0;
        final int lastListItemPosition = listView.getChildCount();

        for (int pos = firstListItemPosition; pos <= lastListItemPosition; pos++) {
            View listItemView = listView.getChildAt(pos);
            if (listItemView != null) {
                SeekBar seekBar = listItemView.findViewById(R.id.seekBarVoice);
                if (seekBar != null && VoiceController.getSeekbarMessage(seekBar) == message) {
                    return seekBar;
                }
            }
        }

        return null;
    }
}
