package com.twofours.surespot.chat;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.NotificationManager;
import android.content.Context;
import android.os.AsyncTask;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.MenuItem;

import com.twofours.surespot.R;
import com.twofours.surespot.StateController;
import com.twofours.surespot.StateController.FriendState;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.friends.AutoInviteData;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.friends.FriendAdapter;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.FileCacheController;
import com.twofours.surespot.images.MessageImageDownloader;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.MainThreadCallbackWrapper;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.services.CommunicationService;
import com.viewpagerindicator.TitlePageIndicator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChatController {

    private static final String TAG = "ChatController";

    private NotificationManager mNotificationManager;

    public HashMap<String, ChatAdapter> mChatAdapters;
    private HashMap<String, Integer> mEarliestMessage;

    private FriendAdapter mFriendAdapter;
    private ChatPagerAdapter mChatPagerAdapter;
    private ViewPager mViewPager;
    private TitlePageIndicator mIndicator;
    private FragmentManager mFragmentManager;
    private int mLatestUserControlId;
    private ArrayList<MenuItem> mMenuItems;
    private HashMap<String, LatestIdPair> mPreConnectIds;


    private static boolean mPaused = true;
    private NetworkController mNetworkController;

    private Context mContext;
    public static final int MODE_NORMAL = 0;
    public static final int MODE_SELECT = 1;
    //   private final StatusLine mImageStatusLine = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "");

    private int mMode = MODE_NORMAL;

    private IAsyncCallback<Boolean> mProgressCallback;
    private IAsyncCallback<Void> mSendIntentCallback;
    private IAsyncCallback<Friend> mTabShowingCallback;
    private AutoInviteData mAutoInviteData;
    private boolean mHandlingAutoInvite;
    private String mUsername;

    public ChatController(Context context, String username, FragmentManager fm, IAsyncCallback<Boolean> progressCallback, IAsyncCallback<Void> sendIntentCallback,
                          IAsyncCallback<Friend> tabShowingCallback) {
        SurespotLog.d(TAG, "constructor, username: %s", username);
        mContext = context;
        mUsername = username;
        mNetworkController = SurespotApplication.getNetworkController();

        mProgressCallback = progressCallback;
        mSendIntentCallback = sendIntentCallback;

        mTabShowingCallback = tabShowingCallback;
        mEarliestMessage = new HashMap<String, Integer>();
        mChatAdapters = new HashMap<String, ChatAdapter>();
        mFriendAdapter = new FriendAdapter(SurespotApplication.getCommunicationService());
        mPreConnectIds = new HashMap<String, ChatController.LatestIdPair>();
        loadState(mUsername);

        mFragmentManager = fm;
        mNotificationManager = (NotificationManager) SurespotApplication.getCommunicationService().getSystemService(Context.NOTIFICATION_SERVICE);

        // mViewPager.setOffscreenPageLimit(2);
    }

    // this has to be done outside of the contructor as it creates fragments, which need chat controller instance
    public void init(ViewPager viewPager, TitlePageIndicator pageIndicator, ArrayList<MenuItem> menuItems) {
        mChatPagerAdapter = new ChatPagerAdapter(mContext, mFragmentManager);
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

        mChatPagerAdapter.setChatFriends(mFriendAdapter.getActiveChatFriends());
        mFriendAdapter.registerFriendAliasChangedCallback(new IAsyncCallback<Void>() {

            @Override
            public void handleResponse(Void result) {
                mChatPagerAdapter.sort();
                mChatPagerAdapter.notifyDataSetChanged();
                mIndicator.notifyDataSetChanged();
            }
        });

        onResume(false);
    }

    public void setAutoInviteData(AutoInviteData autoInviteData) {
        mAutoInviteData = autoInviteData;
        if (getState() == CommunicationService.STATE_CONNECTED) {
            handleAutoInvite();
        }
    }

    private int getState() {
        return SurespotApplication.getCommunicationService().getConnectionState();
    }

    // this is wired up to listen for a message from the CommunicationService.  It's UI stuff
    public void connected() {
        getFriendsAndData();
        //resendMessages();
    }

    private void handleAutoInvite() {

        // if we need to invite someone then do it
        if (mAutoInviteData != null && !mHandlingAutoInvite) {
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

    public void handleMessage(final SurespotMessage message) {
        SurespotLog.d(TAG, "handleMessage %s", message);
        final String otherUser = message.getOtherUser();

        final ChatAdapter chatAdapter = mChatAdapters.get(otherUser);

        // if the adapter is open add the message
        if (chatAdapter != null) {

            // decrypt the message before adding it so the size is set properly
            new AsyncTask<Void, Void, Void>() {

                @Override
                protected Void doInBackground(Void... params) {
                    SurespotLog.d(TAG, "ChatAdapter open for user: %s", otherUser);
                    if (message.getMimeType().equals(SurespotConstants.MimeTypes.TEXT)) {

                        // decrypt it before adding
                        final String plainText = EncryptionController.symmetricDecrypt(message.getOurVersion(), message.getOtherUser(),
                                message.getTheirVersion(), message.getIv(), message.isHashed(), message.getData());

                        // substitute emoji
                        if (plainText != null) {
                            EmojiParser parser = EmojiParser.getInstance();
                            message.setPlainData(parser.addEmojiSpans(plainText));
                        }
                        else {
                            // error decrypting
                            SurespotLog.d(TAG, "could not decrypt message");
                            message.setPlainData(mContext.getString(R.string.message_error_decrypting_message));
                        }
                    }

                    else {
                        if (message.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE) ||
                                message.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
                            // if it's an image that i sent
                            // handle caching
                            if (ChatUtils.isMyMessage(message)) {
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
                    try {
                        boolean added = applyControlMessages(chatAdapter, message, false, true, true);
                        scrollToEnd(otherUser);

                        Friend friend = mFriendAdapter.getFriend(otherUser);
                        if (friend != null) {
                            int messageId = message.getId();

                            // always update the available id
                            friend.setAvailableMessageId(messageId, false);

                            // if the chat is showing set the last viewed id the id of the message we just received
                            if (otherUser.equals(SurespotApplication.getCommunicationService().mCurrentChat)) {

                                friend.setLastViewedMessageId(messageId);

                                // if it was a voice message from the other user set play flag
                                // TODO wrap in preference
                                if (!ChatUtils.isMyMessage(message) && message.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
                                    message.setPlayMedia(true);
                                }

                            }
                            // chat not showing
                            else {
                                // if it's my message increment the count by one to account for it as I may have unread messages from the
                                // other user; we
                                // can't just set the last viewed to the latest message
                                if (ChatUtils.isMyMessage(message) && added) {
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

                            SurespotApplication.getCommunicationService().saveIfMainActivityPaused();
                        }

                    }
                    catch (SurespotMessageSequenceException e) {
                        SurespotLog.d(TAG, "handleMessage: %s", e.getMessage());
                        getLatestMessagesAndControls(otherUser, e.getMessageId(), true);
                    }
                }
            }.execute();

        }
        else {
            SurespotLog.d(TAG, "ChatAdapter not open for user: %s", otherUser);
            //new AsyncTask<Void, Void, Void>() {

//				@Override
//				protected Void doInBackground(Void... params) {params
            Friend friend = mFriendAdapter.getFriend(otherUser);
            if (friend != null) {
                int messageId = message.getId();

                // always update the available id
                friend.setAvailableMessageId(messageId, false);
            }

//					return null;
//				};
//
//				@Override
//				protected void onPostExecute(Void aVoid) {
            mFriendAdapter.notifyDataSetChanged();
            mFriendAdapter.sort();

//				};
//			}.execute();

        }

    }

    private boolean applyControlMessages(ChatAdapter chatAdapter, SurespotMessage message, boolean checkSequence, boolean sort, boolean notify)
            throws SurespotMessageSequenceException {
        // see if we have applicable control messages and apply them if necessary
        ArrayList<SurespotControlMessage> controlMessages = chatAdapter.getControlMessages();
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
        if (localMessage != null) {
            synchronized (localMessage) {
                // if the data is different we haven't updated the url to point externally
                if (localMessage.getId() == null && !localMessage.getData().equals(message.getData())) {
                    // add the remote cache entry for the new url

                    String localUri = localMessage.getData();
                    String remoteUri = message.getData();

                    SurespotLog.d(TAG, "copying cache entries from %s to %s", localUri, remoteUri);
                    // update in memory image cache
                    if (message.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE) || message.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
                        SurespotApplication.getFileCacheController().moveCacheEntry(localUri, remoteUri);
                        MessageImageDownloader.moveCacheEntry(localUri, remoteUri);
                    }

                    // delete the file
                    try {
                        SurespotLog.d(TAG, "handleCachedImage deleting local file: %s", localUri);

                        File file = new File(new URI(localUri));
                        file.delete();
                    }
                    catch (URISyntaxException e) {
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
        if (SurespotApplication.getCommunicationService().getConnectionState() == CommunicationService.STATE_CONNECTED) {

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

    private void getLatestData(final boolean mayBeCacheClear) {
        SurespotLog.d(TAG, "getLatestData");
        // setMessagesLoading(true);

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
                        setProgress(null, false);
                    }

                    @Override
                    public void onResponse(Call call, Response response, String responseString) throws IOException {
                        if (response.isSuccessful()) {
                            JSONObject jsonResponse = null;
                            try {
                                jsonResponse = new JSONObject(responseString);
                                SurespotLog.d(TAG, "getlatestData success, response: %s, statusCode: %d", jsonResponse, response.code());
                            }
                            catch (JSONException e) {
                                Utils.makeToast(mContext, mContext.getString(R.string.loading_latest_messages_failed));
                                SurespotLog.w(TAG, e, "error getLatestData");
                                setProgress(null, false);
                                return;
                            }

                            final boolean hasSigs = jsonResponse.has("sigs");
                            new AsyncTask<Void, Void, Void>() {
                                @Override
                                protected Void doInBackground(Void... voids) {
                                    //see if we need to update signatures, will only have sigs property if we need to update
                                    if (hasSigs) {
                                        JSONObject sigs = IdentityController.updateSignatures(mContext);
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
                                                    SurespotLog.d(TAG, "Signatures update failed, code: ", response.code());
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
                                            friend.setAvailableMessageId(availableId, mayBeCacheClear);
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
                                            handleMessages(friendName, messages, mayBeCacheClear);
                                        }

                                    }
                                    catch (JSONException e) {
                                        SurespotLog.w(TAG, e, "getlatestData");
                                    }
                                }
                            }

                            if (friend != null) {
                                mFriendAdapter.sort();
                                mFriendAdapter.notifyDataSetChanged();
                            }

                            handleAutoInvite();
                            SurespotApplication.getCommunicationService().processNextMessage();
                            setProgress(null, false);
                        }
                        else {
                            SurespotLog.w(TAG, "error getLatestData, response code: %d", response.code());
                            setProgress(null, false);
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

    public void onBeforeConnect() {
        // copy the latest ids so that we don't miss any if we receive new messages during the time we request messages and when the
        // connection completes (if they
        // are received out of order for some reason)
        //
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

    public void dispose() {
        SurespotLog.d(TAG, "disposing of chat controller");
        // mChatAdapters.clear();
        // mFriendAdapter = null;
        // mFragmentManager = null;
    }

    private class LatestIdPair {
        public int latestMessageId;
        public int latestControlMessageId;
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

    private void getLatestMessagesAndControls(String username, int messageId, boolean forceMessageUpdate) {
        getLatestMessagesAndControls(username, messageId, -1, forceMessageUpdate);
    }

    private void getLatestMessagesAndControls(final String username, final int fetchMessageId, int fetchControlMessageId, final boolean forceMessageUpdate) {
        if (getState() != CommunicationService.STATE_CONNECTED) {
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

    public void handleControlMessage(ChatAdapter chatAdapter, SurespotControlMessage message, boolean notify, boolean reApplying) {
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
                        deleteMessageInternal(chatAdapter, dMessage, controlFromMe);
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
            String loggedInUser = mUsername;
            if (loggedInUser != null) {
                mNotificationManager.cancel(loggedInUser + ":" + friendName,
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
        String username = mUsername;

        Friend friend = mFriendAdapter.getFriend(deletedUser);

        boolean iDidTheDeleting = deleter.equals(username);
        if (iDidTheDeleting) {
            // won't be needing this anymore
            closeTab(deletedUser);

            // blow all the state associated with this user away
            StateController.wipeUserState(mContext, username, deletedUser);

            // clear in memory cached data
            SurespotApplication.getCachingService().clearUserData(deletedUser);

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

            // force the controls to update
            CommunicationService cts = SurespotApplication.getCommunicationServiceNoThrow();
            if (cts != null) {
                if (friend != null && cts.mCurrentChat != null && cts.mCurrentChat.equals(deletedUser)) {
                    mTabShowingCallback.handleResponse(friend);
                }
            }
        }

        enableMenuItems(friend);
    }

    public SurespotMessage handleErrorMessage(SurespotErrorMessage errorMessage) {
        SurespotMessage message = null;

        // this logic is also done in the chat transmission service (but after this call) so that if the UI is not available,
        // the mResendBuffer is still updated properly
        Iterator<SurespotMessage> iterator = SurespotApplication.getCommunicationService().getSendQueue().iterator();
        while (iterator.hasNext()) {
            message = iterator.next();
            if (message.getIv().equals(errorMessage.getId())) {
                iterator.remove();
                message.setErrorStatus(errorMessage.getStatus());
                break;
            }
        }

        if (message != null) {
            ChatAdapter chatAdapter = mChatAdapters.get(message.getOtherUser());
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
        }

        return message;
    }

    private void deleteMessageInternal(ChatAdapter chatAdapter, SurespotMessage dMessage, boolean initiatedByMe) {
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
                chatAdapter.deleteMessageById(dMessage.getId());
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

                    SurespotApplication.getCommunicationService().removeQueuedMessage(lastMessage);
                    if (added && myMessage) {
                        sentByMeCount++;
                    }
                }
            }
            catch (JSONException e) {
                SurespotLog.w(TAG, e, "jsonStringsToMessages");

            }
            catch (SurespotMessageSequenceException e) {
                // shouldn't happen
                SurespotLog.w(TAG, e, "handleMessages");
                // getLatestMessagesAndControls(username, e.getMessageId(), -1);
                // setProgress(username, false);
                return;
            }

            if (lastMessage != null) {
                Friend friend = mFriendAdapter.getFriend(username);

                int availableId = lastMessage.getId();
                friend.setAvailableMessageId(availableId, false);

                int lastViewedId = friend.getLastViewedMessageId();

                // how many new messages total are there
                int delta = availableId - lastViewedId;

                // if the current chat is showing or
                // all the new messages are mine then i've viewed them all
                if (username.equals(SurespotApplication.getCommunicationService().mCurrentChat) || sentByMeCount == delta) {
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

                scrollToEnd(username);
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

    public synchronized void loadMessages(String username, boolean replace) {
        SurespotLog.d(TAG, "loadMessages: " + username);

        if (!TextUtils.isEmpty(mUsername)) {
            String spot = ChatUtils.getSpot(mUsername, username);
            ChatAdapter chatAdapter = mChatAdapters.get(username);
            // TODO: will need to take into account "errored" messages here and show them
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
        onPause();
        SurespotApplication.getCommunicationService().userLoggedOut();

        // mViewPager = null;
        // mCallback401 = null;
        // mChatPagerAdapter = null;
        // mIndicator = null;
        // mFragmentManager = null;
        // mFriendAdapter = null;
        // mMenuItems = null;
        // mSocketCallback = null;
        mChatAdapters.clear();
        // mActiveChats.clear();
        // mReadSinceConnected.clear();
    }

    public void saveFriends() {
        SurespotApplication.getStateController().saveFriends(mUsername, mLatestUserControlId, mFriendAdapter.getFriends());
    }

    private void loadState(String username) {
        SurespotLog.d(TAG, "loadState");
        FriendState fs = SurespotApplication.getStateController().loadFriends(username);

        List<Friend> friends = null;
        if (fs != null) {
            mLatestUserControlId = fs.userControlId;
            friends = fs.friends;
        }

        mFriendAdapter.setFriends(friends);
        mFriendAdapter.setLoading(false);

        //SurespotApplication.getCommunicationService().loadUnsentMessages();
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

    public synchronized boolean isInProgress() {
        return mGlobalProgress || !mChatProgress.isEmpty();
    }

    public synchronized void onResume(boolean justSetFlag) {
        SurespotLog.d(TAG, "onResume, mPaused: %b, justSetFlag: %b", mPaused, justSetFlag);
        if (justSetFlag) {
            if (mPaused) {
                mPaused = false;
            }
            return;
        }

        mPaused = false;

        setProgress(null, true);

        // getFriendsAndIds();

        // load chat messages from disk that may have been added by gcm
        for (Entry<String, ChatAdapter> ca : mChatAdapters.entrySet()) {
            loadMessages(ca.getKey(), false);
        }

        if (SurespotApplication.getCommunicationService().connect(mUsername)) {
            setProgress(null, false);
        }

        // make sure to reload user state - we don't want to show old messages as "sending..." when they have been sent
        loadState(mUsername);

        // moved: mContext.registerReceiver(mConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        clearMessageNotification(mUsername, SurespotApplication.getCommunicationService().mCurrentChat);
    }

    public synchronized void onPause() {
        SurespotLog.d(TAG, "onPause, mPaused: %b", mPaused);
        mPaused = true;

        //moved to commservice main activity paused
//        if (SurespotApplication.getCommunicationServiceNoThrow() != null) {
//            SurespotApplication.getCommunicationService().save();
//
//        }
    }

    ChatAdapter getChatAdapter(String username) {
        return getChatAdapter(username, true);
    }

    public ChatAdapter getChatAdapter(String username, boolean create) {

        ChatAdapter chatAdapter = mChatAdapters.get(username);
        if (chatAdapter == null && create) {

            chatAdapter = new ChatAdapter(mContext);

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

        return chatAdapter;
    }

    public void destroyChatAdapter(String username) {
        SurespotLog.d(TAG, "destroying chat adapter for: %s", username);
        if (SurespotApplication.getCommunicationServiceNoThrow() != null) {
            SurespotApplication.getCommunicationService().saveState(username, false);
        }
        mChatAdapters.remove(username);
    }

    public synchronized void setCurrentChat(final String username) {

        SurespotLog.d(TAG, "setCurrentChat: %s", username);
        String loggedInUser = mUsername;
        if (loggedInUser == null) {
            return;
        }

        Friend friend = null;
        if (username != null) {
            friend = mFriendAdapter.getFriend(username);
        }

        mTabShowingCallback.handleResponse(friend);
        if (friend != null) {
            if (SurespotApplication.getCommunicationServiceNoThrow() != null) {
                SurespotApplication.getCommunicationService().mCurrentChat = username;
            }
            mChatPagerAdapter.addChatFriend(friend);
            friend.setChatActive(true);
            friend.setLastViewedMessageId(friend.getAvailableMessageId());

            // cancel associated notifications
            clearMessageNotification(loggedInUser, username);
            int wantedPosition = mChatPagerAdapter.getChatFragmentPosition(username);

            if (wantedPosition != mViewPager.getCurrentItem()) {
                mViewPager.setCurrentItem(wantedPosition, true);
            }

            if (mMode == MODE_SELECT) {
                mSendIntentCallback.handleResponse(null);
                setMode(MODE_NORMAL);
            }

        }
        else {
            SurespotApplication.getCommunicationService().mCurrentChat = null;
            mViewPager.setCurrentItem(0, true);
            mNotificationManager.cancel(loggedInUser + ":" + username, SurespotConstants.IntentRequestCodes.INVITE_REQUEST_NOTIFICATION);
            mNotificationManager.cancel(loggedInUser, SurespotConstants.IntentRequestCodes.INVITE_RESPONSE_NOTIFICATION);
        }

        mFriendAdapter.sort();
        mFriendAdapter.notifyDataSetChanged();

        // set menu item enable state
        enableMenuItems(friend);

    }

    private void clearMessageNotification(String loggedInUser, String username) {
        if (!TextUtils.isEmpty(loggedInUser) && !TextUtils.isEmpty(username)) {
            mNotificationManager.cancel(loggedInUser + ":" + ChatUtils.getSpot(loggedInUser, username),
                    SurespotConstants.IntentRequestCodes.NEW_MESSAGE_NOTIFICATION);
        }
    }

    private ChatFragment getChatFragment(String username) {
        String fragmentTag = Utils.makePagerFragmentName(mViewPager.getId(), username.hashCode());
        SurespotLog.d(TAG, "looking for fragment: %s", fragmentTag);
        ChatFragment chatFragment = null;//(ChatFragment) mFragmentManager.findFragmentByTag(fragmentTag);
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

            final SurespotMessage chatMessage = ChatUtils.buildPlainMessage(username, mimeType, EmojiParser.getInstance().addEmojiSpans(plainText), new String(
                    ChatUtils.base64EncodeNowrap(iv)));

            try {
                chatAdapter.addOrUpdateMessage(chatMessage, false, true, true);
                SurespotApplication.getCommunicationService().enqueueMessage(chatMessage);
            }
            catch (SurespotMessageSequenceException e) {
                // not gonna happen
                SurespotLog.w(TAG, e, "sendMessage");
            }

            // do encryption in background
            new AsyncTask<Void, Void, Boolean>() {

                @Override
                protected Boolean doInBackground(Void... arg0) {
                    String ourLatestVersion = IdentityController.getOurLatestVersion();
                    String theirLatestVersion = IdentityController.getTheirLatestVersion(username);

                    String result = EncryptionController.symmetricEncrypt(ourLatestVersion, username, theirLatestVersion, plainText, iv);

                    if (result != null) {
                        chatMessage.setData(result);
                        chatMessage.setFromVersion(ourLatestVersion);
                        chatMessage.setToVersion(theirLatestVersion);

                        SurespotLog.d(TAG, "sending message to chat controller iv: %s", chatMessage.getIv());
                        SurespotApplication.getCommunicationService().processNextMessage();
                        return true;
                    }
                    else {
                        SurespotLog.d(TAG, "could not encrypt message, iv: %s", chatMessage.getIv());
                        chatMessage.setErrorStatus(500);

                        return false;
                    }
                }

                protected void onPostExecute(Boolean success) {
                    // if success is false we will have set an error status in the message so notify
                    if (!success) {
                        chatAdapter.notifyDataSetChanged();
                    }
                }

                ;

            }.execute();
        }

    }

    void addMessage(Activity activity, SurespotMessage message) {
        if (mChatAdapters != null) {
            ChatAdapter chatAdapter = mChatAdapters.get(message.getTo());

            try {
                chatAdapter.addOrUpdateMessage(message, false, true, true);
                scrollToEnd(message.getTo());
                SurespotApplication.getCommunicationService().saveState(message.getTo(), false);
            }
            catch (Exception e) {
                SurespotLog.e(TAG, e, "addMessage");
            }
        }
        else {
            // TODO: we should tell the user there was an error sending the message if surespot UI is not actively running (via a notification)
            Utils.makeToast(activity, activity.getString(R.string.error_message_generic));
        }
    }

    public static String getCurrentChat() {
        if (SurespotApplication.getCommunicationServiceNoThrow() == null) {
            return null;
        }
        return SurespotApplication.getCommunicationService().mCurrentChat;
    }

    public static boolean isPaused() {
        return mPaused;
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

    public void deleteMessage(final SurespotMessage message) {


        //remove it from send queue
        SurespotApplication.getCommunicationService().removeQueuedMessage(message);

        // if it's on the server, send delete control message otherwise just delete it locally
        if (message.getId() != null) {

            final ChatAdapter chatAdapter = mChatAdapters.get(message.getOtherUser());
            setProgress("delete", true);
            if (chatAdapter != null) {
                mNetworkController.deleteMessage(message.getOtherUser(), message.getId(), new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        SurespotLog.i(TAG, e, "deleteMessage");
                        setProgress("delete", false);
                        Utils.makeToast(mContext, mContext.getString(R.string.could_not_delete_message));
                    }

                    @Override
                    public void onResponse(Call call, Response response, String responseString) throws IOException {
                        if (response.isSuccessful()) {
                            deleteMessageInternal(chatAdapter, message, true);
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
            String otherUser = message.getOtherUser();
            //	SurespotApplication.getCommunicationService().getSendQueue().remove(message);

            ChatAdapter chatAdapter = mChatAdapters.get(otherUser);
            chatAdapter.deleteMessageByIv(message.getIv());
            SurespotApplication.getCommunicationService().saveState(otherUser, false);

            // if it's an file message, delete the local file
            if (message.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE) || message.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
                if (message.getData().startsWith("file")) {
                    try {
                        new File(new URI(message.getData())).delete();
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

            SurespotApplication.getCommunicationServiceNoThrow().clearMessageQueue(username);

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
            String messageUsername = message.getOtherUser();

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

    public void resendMessage(SurespotMessage message) {
        if (SurespotApplication.getCommunicationServiceNoThrow() != null) {
            SurespotApplication.getCommunicationService().resendMessage(message);
        }
    }

    public void resendFileMessage(String to, final String messageIv) {
//		final ChatAdapter chatAdapter = mChatAdapters.get(to);
//		final SurespotMessage message = chatAdapter.getMessageByIv(messageIv);
//
//		// reset status flags
//		message.setErrorStatus(0);
//		message.setAlreadySent(false);
//		chatAdapter.notifyDataSetChanged();
//		setProgress("resend", true);
//		ChatUtils.resendFileMessage(mNetworkController, message, new IAsyncCallback<Integer>() {
//
//			@Override
//			public void handleResponse(Integer result) {
//				setProgress("resend", false);
//				if (result == 200) {
//					message.setErrorStatus(0);
//				}
//				else {
//					message.setErrorStatus(result);
//				}
//
//				message.setAlreadySent(true);
//				chatAdapter.notifyDataSetChanged();
//			}
//		});

    }

    public FriendAdapter getFriendAdapter() {
        return mFriendAdapter;
    }

    public boolean isFriendDeleted(String username) {
        return getFriendAdapter().getFriend(username).isDeleted();
    }

    public boolean isFriendDeleted() {
        return getFriendAdapter().getFriend(SurespotApplication.getCommunicationService().mCurrentChat).isDeleted();
    }

    private void getFriendsAndData() {
        if (mFriendAdapter.getCount() == 0 && mLatestUserControlId == 0) {
            mFriendAdapter.setLoading(true);
            // get the list of friends
            mNetworkController.getFriends(new MainThreadCallbackWrapper(new MainThreadCallbackWrapper.MainThreadCallback() {

                @Override
                public void onFailure(Call call, final IOException e) {
                    setProgress(null, false);
                    if (!mNetworkController.isUnauthorized()) {
                        mFriendAdapter.setLoading(false);
                        SurespotLog.w(TAG, e, "getFriendsAndData error");
                    }
                }

                @Override
                public void onResponse(Call call, final Response response, final String responseString) throws IOException {

                    if (response.isSuccessful()) {
                        SurespotLog.d(TAG, "getFriends success.");
                        ArrayList<Friend> friends = new ArrayList<Friend>();
                        boolean userSuddenlyHasFriends = false;
                        try {
                            JSONObject jsonObject = new JSONObject(responseString);
                            mLatestUserControlId = jsonObject.getInt("userControlId");
                            JSONArray friendsArray = jsonObject.optJSONArray("friends");

                            if (friendsArray != null) {
                                for (int i = 0; i < friendsArray.length(); i++) {
                                    JSONObject jsonFriend = friendsArray.getJSONObject(i);

                                    Friend friend = Friend.toFriend(jsonFriend);
                                    friends.add(friend);

                                    SurespotLog.d(TAG, "getFriendsAndData, adding friend: %s", friend);
                                }
                            }
                            if (friends.size() > 0) {
                                userSuddenlyHasFriends = true;
                            }
                        }
                        catch (JSONException e) {
                            SurespotLog.e(TAG, e, "getFriendsAndData error");
                            mFriendAdapter.setLoading(false);
                            setProgress(null, false);
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
                            setProgress(null, false);
                        }
                    }
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
                    SurespotLog.d(TAG, "closeTab, new tab name: %s, position: %d", SurespotApplication.getCommunicationService().mCurrentChat, position);
                }
            }
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
                    SurespotLog.d(TAG, "closeTab, new tab name: %s, position: %d", SurespotApplication.getCommunicationService().mCurrentChat, position);
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
        boolean enabled = mMode != MODE_SELECT && SurespotApplication.getCommunicationService().mCurrentChat != null;
        SurespotLog.v(TAG, "enableMenuItems, enabled: %b", enabled);

        boolean isDeleted = false;
        if (friend != null) {
            isDeleted = friend.isDeleted();
        }

        if (mMenuItems != null) {
            for (MenuItem menuItem : mMenuItems) {
                //if (menuItem.getItemId() != R.id.menu_purchase_voice) {

                // deleted users can't have images sent to them
                if (menuItem.getItemId() == R.id.menu_capture_image_bar || menuItem.getItemId() == R.id.menu_send_image_bar) {

                    menuItem.setVisible(enabled && !isDeleted);
                }
                else {
                    menuItem.setVisible(enabled);
                }
//				}
//				else {
//					boolean voiceEnabled = SurespotApplication.getBillingController().hasVoiceMessaging();
//					SurespotLog.d(TAG, "enableMenuItems, setting voice purchase menu visibility: %b", !voiceEnabled);
//					menuItem.setVisible(!voiceEnabled);
//				}
            }
        }
    }

    public void scrollToEnd(String to) {
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

    public void setFriendAlias(String name, String data, String version, String iv, boolean hashed) {
        final Friend friend = mFriendAdapter.getFriend(name);
        if (friend != null) {
            friend.setAliasData(data);
            friend.setAliasIv(iv);
            friend.setAliasVersion(version);
            friend.setAliasHashed(hashed);

            new AsyncTask<Void, Void, String>() {

                @Override
                protected String doInBackground(Void... params) {
                    String plainText = EncryptionController.symmetricDecrypt(friend.getAliasVersion(), IdentityController.getLoggedInUser(),
                            friend.getAliasVersion(), friend.getAliasIv(), friend.isAliasHashed(), friend.getAliasData());

                    return plainText;
                }

                protected void onPostExecute(String plainAlias) {

                    friend.setAliasPlain(plainAlias);
                    saveFriends();
                    mChatPagerAdapter.sort();
                    mChatPagerAdapter.notifyDataSetChanged();
                    mIndicator.notifyDataSetChanged();
                    mFriendAdapter.sort();
                    mFriendAdapter.notifyDataSetChanged();
                }
            }.execute();
        }
    }

    public SurespotMessage getLiveMessage(SurespotMessage message) {
        String otherUser = message.getOtherUser();
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

                // Handler handler = new Handler(Looper.getMainLooper());
                // handler.post(new Runnable() {
                //
                // @Override
                // public void run() {
                try {
                    return applyControlMessages(chatAdapter, message, false, true, false);
                }
                catch (SurespotMessageSequenceException e) {
                }
                // }
                // });
                return false;
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
        if (friend != null) {
            friend.setAliasData(null);
            friend.setAliasIv(null);
            friend.setAliasVersion(null);
            friend.setAliasPlain(null);
            saveFriends();
            mChatPagerAdapter.sort();
            mChatPagerAdapter.notifyDataSetChanged();
            mIndicator.notifyDataSetChanged();
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
        final String version = IdentityController.getOurLatestVersion();
        String username = IdentityController.getLoggedInUser();

        byte[] iv = EncryptionController.getIv();
        final String cipherAlias = EncryptionController.symmetricEncrypt(version, username, version, alias, iv);
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
}
