package com.twofours.surespot.chat;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.encryption.MessageDecryptor;
import com.twofours.surespot.images.MessageImageDownloader;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.voice.VoiceController;
import com.twofours.surespot.voice.VoiceMessageDownloader;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ListIterator;

public class ChatAdapter extends BaseAdapter {
    private final static String TAG = "ChatAdapter";
    private ArrayList<SurespotMessage> mMessages = new ArrayList<SurespotMessage>();
    private Context mContext;
    private final static int TYPE_US = 0;
    private final static int TYPE_THEM = 1;
    private boolean mLoading;
    private IAsyncCallback<Boolean> mAllLoadedCallback;
    private boolean mCheckingSequence;
    private boolean mDebugMode;
    private int mCurrentScrollPositionId;
    private MessageDecryptor mMessageDecryptor;
    private MessageImageDownloader mMessageImageDownloader;
    private boolean mLoaded;
    private VoiceMessageDownloader mMessageVoiceDownloader;
    private ArrayList<SurespotControlMessage> mControlMessages = new ArrayList<SurespotControlMessage>();
    private String mOurUsername;

    public ChatAdapter(Context context, String ourUsername) {
        SurespotLog.d(TAG, "Constructor, ourUsername: %s", ourUsername);
        mContext = context;
        mOurUsername = ourUsername;

        SharedPreferences pm = context.getSharedPreferences(mOurUsername, Context.MODE_PRIVATE);
        mDebugMode = pm.getBoolean("pref_debug_mode", false);

        mMessageDecryptor = new MessageDecryptor(mOurUsername, this);
        mMessageImageDownloader = new MessageImageDownloader(mOurUsername, this);
        mMessageVoiceDownloader = new VoiceMessageDownloader(mOurUsername, this);
    }

    public void doneCheckingSequence() {
        mCheckingSequence = false;
    }

    public void setAllLoadedCallback(IAsyncCallback<Boolean> callback) {
        mAllLoadedCallback = callback;
    }

    public synchronized ArrayList<SurespotMessage> getMessages() {
        return mMessages;
    }

    // get the last message that has an id
    public synchronized SurespotMessage getLastMessageWithId() {
        for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(mMessages.size()); iterator.hasPrevious(); ) {
            SurespotMessage message = iterator.previous();
            if (message.getId() != null && message.getId() > 0 && !message.isGcm()) {
                return message;
            }
        }
        return null;
    }

    public synchronized SurespotMessage getFirstMessageWithId() {
        for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(0); iterator.hasNext(); ) {
            SurespotMessage message = iterator.next();
            if (message.getId() != null && message.getId() > 0 && !message.isGcm()) {
                return message;
            }
        }
        return null;
    }

    // update the id and sent status of the message once we received
    private synchronized boolean addOrUpdateMessage(SurespotMessage message, boolean checkSequence, boolean sort) {

        // SurespotLog.v(TAG, "addMessage, could not find message");

        // make sure message is in sequence
        //
        // if (!mCheckingSequence && checkSequence && (message.getId() != null)) {
        // SurespotMessage previousMessage = getLastMessageWithId();
        //
        // int previousId = 0;
        // if (previousMessage != null) {
        // previousId = previousMessage.getId();
        // }
        //
        // if (previousId != (message.getId() - 1)) {
        // throw new SurespotMessageSequenceException(previousId);
        // }
        //
        // SurespotLog.v(TAG, "addOrUpdateMessage: %s", message);

        int index = mMessages.indexOf(message);
        boolean added = false;
        if (index == -1) {
            mMessages.add(message);
            added = true;
        } else {
            // SurespotLog.v(TAG, "addMessage, updating message");
            SurespotMessage updateMessage = mMessages.get(index);

            if (updateMessage != null) {
                SurespotLog.v(TAG, "updating message: %s", updateMessage);
                SurespotLog.v(TAG, "new message: %s", message);

                // don't update unless we have an id
                if (message.getId() != null) {
                    // if the id is null 'tis the same as adding the message
                    added = updateMessage.getId() == null;
                    updateMessage.setId(message.getId());

                    if (message.getDateTime() != null) {
                        updateMessage.setDateTime(message.getDateTime());
                    }
                    if (!TextUtils.isEmpty(message.getData())) {
                        updateMessage.setData(message.getData());
                    }
                    if (!message.isGcm()) {
                        updateMessage.setGcm(message.isGcm());
                    }
                    if (message.getDataSize() != null) {
                        updateMessage.setDataSize(message.getDataSize());
                    }

                    // clear error status
                    updateMessage.setErrorStatus(0);
                }
                //
                else {
                    //message updated by communication controller after encryption
                    //update plain data, their version, our version if the local message doesn't have an id
                    //(ie. hasn't roundtripped)
                    if (updateMessage.getId() == null) {
                        if (!TextUtils.isEmpty(message.getToVersion())) {
                            updateMessage.setToVersion(message.getToVersion());
                        }

                        if (!TextUtils.isEmpty(message.getFromVersion())) {
                            updateMessage.setFromVersion(message.getFromVersion());
                        }

                        //if local message has an id don't overwrite the data
                        if (!TextUtils.isEmpty(message.getData())) {
                            updateMessage.setData(message.getData());
                        }
                    }

                    if (updateMessage.getErrorStatus() != message.getErrorStatus()) {
                        updateMessage.setErrorStatus(message.getErrorStatus());
                    }
                }
                SurespotLog.v(TAG, "updated message: %s", updateMessage);
            }
        }

        if (sort) {
            sort();
        }
        return added;
    }

    private synchronized void insertMessage(SurespotMessage message) {
        if (mMessages.indexOf(message) == -1) {
            mMessages.add(0, message);
        } else {
            SurespotLog.v(TAG, "insertMessage, message already present: %s", message);
        }
    }

    public synchronized void setMessages(ArrayList<SurespotMessage> messages) {
        if (messages.size() > 0) {
            mMessages.clear();
            mMessages.addAll(messages);
        }
    }

    public synchronized void addOrUpdateMessages(ArrayList<SurespotMessage> messages) {
        for (SurespotMessage message : messages) {
            addOrUpdateMessage(message, false, false);

        }
        sort();
    }

    @Override
    public synchronized int getCount() {
        return mMessages.size();
    }

    @Override
    public synchronized Object getItem(int position) {
        return mMessages.get(position);
    }

    @Override
    public synchronized int getItemViewType(int position) {
        SurespotMessage message = mMessages.get(position);
        return getTypeForMessage(mOurUsername, message);
    }

    public int getTypeForMessage(String ourUser, SurespotMessage message) {
        String otherUser = ChatUtils.getOtherUser(ourUser, message.getFrom(), message.getTo());
        if (otherUser.equals(message.getFrom())) {
            return TYPE_THEM;
        } else {
            return TYPE_US;
        }
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public int getCurrentScrollPositionId() {
        return mCurrentScrollPositionId;
    }

    public void setCurrentScrollPositionId(int currentScrollPositionId) {
        mCurrentScrollPositionId = currentScrollPositionId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // SurespotLog.v(TAG, "getView, pos: " + position);
        final int type = getItemViewType(position);
        // boolean bgImageSet = SurespotConfiguration.isBackgroundImageSet();
        ChatMessageViewHolder chatMessageViewHolder = null;

        // check type again based on http://stackoverflow.com/questions/12018997/why-does-getview-return-wrong-convertview-objects-on-separatedlistadapter
        // and NPE I was getting that would only happen with wrong type

        if (convertView != null) {
            ChatMessageViewHolder currentViewHolder = (ChatMessageViewHolder) convertView.getTag();
            if (currentViewHolder.type != type) {
                SurespotLog.v(TAG, "types do not match, creating new view for the row");
                convertView = null;
            } else {
                chatMessageViewHolder = currentViewHolder;
            }
        }

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            chatMessageViewHolder = new ChatMessageViewHolder();
            chatMessageViewHolder.type = type;

            switch (type) {
                case TYPE_US:
                    convertView = inflater.inflate(R.layout.message_list_item_us, parent, false);
                    chatMessageViewHolder.vMessageSending = convertView.findViewById(R.id.messageSending);
                    chatMessageViewHolder.vMessageSent = convertView.findViewById(R.id.messageSent);
                    break;
                case TYPE_THEM:
                    convertView = inflater.inflate(R.layout.message_list_item_them, parent, false);
                    chatMessageViewHolder.voicePlay = (ImageView) convertView.findViewById(R.id.voicePlay);
                    break;
            }

            chatMessageViewHolder.tvTime = (TextView) convertView.findViewById(R.id.messageTime);
            chatMessageViewHolder.tvText = (TextView) convertView.findViewById(R.id.messageText);
            chatMessageViewHolder.imageView = (ImageView) convertView.findViewById(R.id.messageImage);
            chatMessageViewHolder.imageView.getLayoutParams().height = SurespotConfiguration.getImageDisplayHeight();
            chatMessageViewHolder.voiceView = convertView.findViewById(R.id.messageVoice);
            chatMessageViewHolder.ivNotShareable = (ImageView) convertView.findViewById(R.id.messageImageNotShareable);
            chatMessageViewHolder.ivShareable = (ImageView) convertView.findViewById(R.id.messageImageShareable);
            chatMessageViewHolder.messageSize = (TextView) convertView.findViewById(R.id.messageSize);
            chatMessageViewHolder.voiceSeekBar = (SeekBar) convertView.findViewById(R.id.seekBarVoice);
            chatMessageViewHolder.voiceSeekBar.setEnabled(false);
            chatMessageViewHolder.voicePlayed = (ImageView) convertView.findViewById(R.id.voicePlayed);
            chatMessageViewHolder.voiceStop = (ImageView) convertView.findViewById(R.id.voiceStop);

            if (mDebugMode) {
                chatMessageViewHolder.tvId = (TextView) convertView.findViewById(R.id.messageId);
                chatMessageViewHolder.tvToVersion = (TextView) convertView.findViewById(R.id.messageToVersion);
                chatMessageViewHolder.tvFromVersion = (TextView) convertView.findViewById(R.id.messageFromVersion);
                chatMessageViewHolder.tvIv = (TextView) convertView.findViewById(R.id.messageIv);
                chatMessageViewHolder.tvData = (TextView) convertView.findViewById(R.id.messageData);
                chatMessageViewHolder.tvMimeType = (TextView) convertView.findViewById(R.id.messageMimeType);
            }

            convertView.setTag(chatMessageViewHolder);
        }

        final SurespotMessage item = (SurespotMessage) getItem(position);

        SurespotLog.d(TAG, "color: %d", SurespotApplication.getTextColor());
        chatMessageViewHolder.tvText.setTextColor(SurespotApplication.getTextColor());
        chatMessageViewHolder.tvTime.setTextColor(SurespotApplication.getTextColor());
        chatMessageViewHolder.messageSize.setTextColor(SurespotApplication.getTextColor());

//        if (!SurespotConstants.MimeTypes.TEXT.equals(item.getMimeType())) {
//            SurespotLog.v(TAG, "rendering item: %s", item);
//        }

        if (item.getErrorStatus() > 0) {
            SurespotLog.v(TAG, "item has error: %s", item);
            ChatUtils.setMessageErrorText(mContext, chatMessageViewHolder.tvTime, item);
        } else {
            if (item.getId() == null) {
                // if it's a text message or we're sending
                //     if (item.getMimeType().equals(SurespotConstants.MimeTypes.TEXT)) {

                chatMessageViewHolder.tvTime.setText(R.string.message_sending);
                //
//                    SurespotLog.v(TAG, "getView, item.getId() is null, a text message or not loaded from disk, setting status text to sending...");
//                }
//                else {
//                    if (item.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE) || item.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
//                        chatMessageViewHolder.tvTime.setText(R.string.message_loading_and_decrypting);
//                        SurespotLog.v(TAG, "getView, item.getId() is null, an image or voice message, setting status text to loading and decrypting...");
//                    }
//                }
            } else {
                if (item.getPlainData() == null && item.getPlainBinaryData() == null) {
                    chatMessageViewHolder.tvTime.setText(R.string.message_loading_and_decrypting);
                } else {

                    if (item.getDateTime() != null) {
                        chatMessageViewHolder.tvTime.setText(DateFormat.getDateFormat(mContext).format(item.getDateTime()) + " "
                                + DateFormat.getTimeFormat(mContext).format(item.getDateTime()));
                    } else {
                        chatMessageViewHolder.tvTime.setText("");
                        SurespotLog.v(TAG, "getView, item: %s", item);
                    }
                }
            }
        }

        if (item.getMimeType().equals(SurespotConstants.MimeTypes.TEXT)) {
            chatMessageViewHolder.tvText.setVisibility(View.VISIBLE);

            chatMessageViewHolder.voiceView.setVisibility(View.GONE);
            chatMessageViewHolder.messageSize.setVisibility(View.GONE);
            chatMessageViewHolder.imageView.setVisibility(View.GONE);
            chatMessageViewHolder.imageView.clearAnimation();
            chatMessageViewHolder.imageView.setImageBitmap(null);
            if (item.getPlainData() != null) {
                chatMessageViewHolder.tvText.clearAnimation();
                chatMessageViewHolder.tvText.setText(item.getPlainData());
            } else {
                chatMessageViewHolder.tvText.setText("");
                mMessageDecryptor.decrypt(chatMessageViewHolder.tvText, item);
            }
            chatMessageViewHolder.ivNotShareable.setVisibility(View.GONE);
            chatMessageViewHolder.ivShareable.setVisibility(View.GONE);
        } else {
            if (item.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
                chatMessageViewHolder.imageView.setVisibility(View.VISIBLE);
                chatMessageViewHolder.voiceView.setVisibility(View.GONE);
                chatMessageViewHolder.messageSize.setVisibility(View.GONE);
                chatMessageViewHolder.tvText.clearAnimation();
                chatMessageViewHolder.tvText.setVisibility(View.GONE);
                chatMessageViewHolder.tvText.setText("");
                if (!TextUtils.isEmpty(item.getData()) || !TextUtils.isEmpty(item.getPlainData())) {
                    mMessageImageDownloader.download(chatMessageViewHolder.imageView, item);
                }

                if (item.isShareable()) {

                    chatMessageViewHolder.ivNotShareable.setVisibility(View.GONE);
                    chatMessageViewHolder.ivShareable.setVisibility(View.VISIBLE);
                } else {
                    chatMessageViewHolder.ivNotShareable.setVisibility(View.VISIBLE);
                    chatMessageViewHolder.ivShareable.setVisibility(View.GONE);
                }
            } else {
                if (item.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
                    chatMessageViewHolder.imageView.setVisibility(View.GONE);
                    chatMessageViewHolder.voiceView.setVisibility(View.VISIBLE);
                    chatMessageViewHolder.messageSize.setVisibility(View.GONE);

                    if (type == TYPE_US) {
                        chatMessageViewHolder.voicePlayed.setVisibility(View.VISIBLE);
                    }
                    // //if it's ours we don't care if it's been played or not
                    else {

                        if (item.isVoicePlayed()) {
                            SurespotLog.v(TAG, "chatAdapter setting played to visible");
                            chatMessageViewHolder.voicePlayed.setVisibility(View.VISIBLE);
                            chatMessageViewHolder.voicePlay.setVisibility(View.GONE);
                        } else {
                            SurespotLog.v(TAG, "chatAdapter setting played to gone");
                            chatMessageViewHolder.voicePlayed.setVisibility(View.GONE);
                            chatMessageViewHolder.voicePlay.setVisibility(View.VISIBLE);
                        }
                    }
                    chatMessageViewHolder.voiceStop.setVisibility(View.GONE);
                    chatMessageViewHolder.tvText.clearAnimation();
                    chatMessageViewHolder.tvText.setVisibility(View.GONE);
                    chatMessageViewHolder.tvText.setText("");
                    chatMessageViewHolder.ivNotShareable.setVisibility(View.GONE);
                    chatMessageViewHolder.ivShareable.setVisibility(View.GONE);

                    mMessageVoiceDownloader.download(convertView, item);
                    chatMessageViewHolder.voiceSeekBar.setTag(R.id.tagMessage, new WeakReference<SurespotMessage>(item));
                    VoiceController.attach(chatMessageViewHolder.voiceSeekBar);
                }
            }
        }

        if (type == TYPE_US) {
            chatMessageViewHolder.vMessageSending.setVisibility(item.getId() == null ? View.VISIBLE : View.GONE);
            chatMessageViewHolder.vMessageSent.setVisibility(item.getId() != null ? View.VISIBLE : View.GONE);
        }

        if (mDebugMode) {
            chatMessageViewHolder.tvId.setVisibility(View.VISIBLE);
            chatMessageViewHolder.tvToVersion.setVisibility(View.VISIBLE);
            chatMessageViewHolder.tvFromVersion.setVisibility(View.VISIBLE);
            chatMessageViewHolder.tvIv.setVisibility(View.VISIBLE);
            chatMessageViewHolder.tvData.setVisibility(View.VISIBLE);
            chatMessageViewHolder.tvMimeType.setVisibility(View.VISIBLE);

            chatMessageViewHolder.tvId.setText("id: " + item.getId());
            chatMessageViewHolder.tvToVersion.setText("toVersion: " + item.getToVersion());
            chatMessageViewHolder.tvFromVersion.setText("fromVersion: " + item.getFromVersion());
            chatMessageViewHolder.tvIv.setText("iv: " + item.getIv());
            chatMessageViewHolder.tvData.setText("data: " + item.getData());
            chatMessageViewHolder.tvMimeType.setText("mimeType: " + item.getMimeType());
        }

        return convertView;
    }

    public static class ChatMessageViewHolder {
        public TextView tvText;
        public TextView tvUser;
        public View vMessageSending;
        public View vMessageSent;
        public ImageView imageView;
        public TextView tvTime;
        public TextView tvId;
        public TextView tvToVersion;
        public TextView tvFromVersion;
        public TextView tvIv;
        public TextView tvData;
        public TextView tvMimeType;
        public ImageView ivShareable;
        public ImageView ivNotShareable;
        public TextView messageSize;
        public int type;
        public View voiceView;
        public SeekBar voiceSeekBar;
        public ImageView voicePlayed;
        public ImageView voicePlay;
        public ImageView voiceStop;

    }

    public synchronized boolean addOrUpdateMessage(SurespotMessage message, boolean checkSequence, boolean sort, boolean notify) {
        boolean added = addOrUpdateMessage(message, checkSequence, sort);

        if (notify) {
            notifyDataSetChanged();
        }
        return added;
    }

    public synchronized void insertMessage(SurespotMessage message, boolean notify) {
        insertMessage(message);
        if (notify) {
            notifyDataSetChanged();
        }
    }

    public synchronized SurespotMessage deleteMessageByIv(String iv) {
        SurespotMessage message = null;
        for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext(); ) {
            message = iterator.next();

            if (message.getIv().equals(iv)) {
                iterator.remove();
                message.setDeleted(true);
                notifyDataSetChanged();
                return message;
            }
        }


        return null;
    }

    public synchronized SurespotMessage deleteMessageById(Integer id) {
        SurespotMessage message = null;
        for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(mMessages.size()); iterator.hasPrevious(); ) {
            message = iterator.previous();

            Integer localId = message.getId();
            if (localId != null && localId.equals(id)) {
                SurespotLog.v(TAG, "deleting message");
                message.setDeleted(true);
                iterator.remove();
                notifyDataSetChanged();
                return message;
            }
        }

        return null;
    }

    public synchronized SurespotMessage getMessageById(Integer id) {
        SurespotMessage message = null;
        for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext(); ) {
            message = iterator.next();

            Integer localId = message.getId();
            if (localId != null && localId.equals(id)) {

                return message;
            }
        }
        return null;
    }

    public synchronized SurespotMessage getMessageByIv(String iv) {
        SurespotMessage message = null;
        for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(mMessages.size()); iterator.hasPrevious(); ) {
            message = iterator.previous();

            String localIv = message.getIv();
            if (localIv != null && localIv.equals(iv)) {
                return message;
            }
        }

        return null;
    }

    public synchronized void sort() {
        Collections.sort(mMessages);
    }

    public synchronized void deleteAllMessages(int utaiMessageId) {
        //
        // mMessages.clear();
        for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext(); ) {
            SurespotMessage message = iterator.next();

            if (message.getId() == null || (message.getId() != null && message.getId() <= utaiMessageId)) {
                message.setDeleted(true);
                iterator.remove();
            }
        }
    }

    public synchronized void deleteTheirMessages(int utaiMessageId) {

        for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext(); ) {
            SurespotMessage message = iterator.next();

            // if it's not our message, delete it
            if (message.getId() != null && message.getId() <= utaiMessageId && !message.getFrom().equals(mOurUsername)) {
                message.setDeleted(true);
                iterator.remove();
            }
        }
    }

    public void userDeleted() {
        deleteTheirMessages(Integer.MAX_VALUE);
    }

    // the first time we load the listview doesn't know
    // where to scroll because the items change size
    // so we keep track of which messages we're loading
    // so we know when they're done, and when they are
    // we can scroll to where we need to be
    public synchronized void checkLoaded() {

        if (!mLoaded) {

            for (SurespotMessage message : mMessages) {
                if (message.isLoading() && !message.isLoaded()) {
                    return;
                }
            }

            mAllLoadedCallback.handleResponse(true);
            mLoaded = true;
        }
    }

    public boolean isLoaded() {
        return mLoaded;
    }

    public Context getContext() {
        return mContext;
    }

    public synchronized void addControlMessage(SurespotControlMessage message) {
        mControlMessages.add(message);
    }

    public synchronized ArrayList<SurespotControlMessage> getControlMessages() {
        return mControlMessages;
    }
}
