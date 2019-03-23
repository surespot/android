package com.twofours.surespot.chat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConfiguration;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.filetransfer.FileMessageDecryptor;
import com.twofours.surespot.filetransfer.FileTransferManager;
import com.twofours.surespot.gifs.GifMessageDownloader;
import com.twofours.surespot.images.MessageImageDownloader;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.utils.PBFileUtils;
import com.twofours.surespot.utils.UIUtils;
import com.twofours.surespot.utils.Utils;
import com.twofours.surespot.voice.VoiceController;
import com.twofours.surespot.voice.VoiceMessageDownloader;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;

import pl.droidsonroids.gif.GifImageView;

public class ChatAdapter extends BaseAdapter {
    private String TAG = "ChatAdapter";
    private List<SurespotMessage> mMessages = Collections.synchronizedList(new ArrayList<SurespotMessage>());
    private Context mContext;
    private final static int TYPE_US = 0;
    private final static int TYPE_THEM = 1;
    private boolean mLoading;
    private IAsyncCallback<Boolean> mAllLoadedCallback;
    private boolean mCheckingSequence;
    private boolean mDebugMode;
    private int mCurrentScrollPositionId = -1;

    private boolean mLoaded;

    private CopyOnWriteArrayList<SurespotControlMessage> mControlMessages = new CopyOnWriteArrayList<>();
    private String mOurUsername;

    private int mSelectedTop;

    private MessageDecryptor mMessageDecryptor;
    private MessageImageDownloader mMessageImageDownloader;
    private VoiceMessageDownloader mMessageVoiceDownloader;
    private GifMessageDownloader mGifDownloader;
    private FileMessageDecryptor mFileMessageDecryptor;

    public ChatAdapter(Context context, String ourUsername, String theirUsername) {
        TAG = String.format("ChatAdapter:%s:%s", ourUsername, theirUsername);
        SurespotLog.d(TAG, "Constructor");
        mContext = context;
        mOurUsername = ourUsername;

        SharedPreferences pm = context.getSharedPreferences(mOurUsername, Context.MODE_PRIVATE);
        mDebugMode = pm.getBoolean("pref_debug_mode", false);

        mMessageDecryptor = new MessageDecryptor(context, mOurUsername, this);
        mMessageImageDownloader = new MessageImageDownloader(context, mOurUsername, this);
        mMessageVoiceDownloader = new VoiceMessageDownloader(context, mOurUsername, this);
        mGifDownloader = new GifMessageDownloader(context, ourUsername, this);
        mFileMessageDecryptor = new FileMessageDecryptor(context, ourUsername, this);
    }

    public void doneCheckingSequence() {
        mCheckingSequence = false;
    }

    public void setAllLoadedCallback(IAsyncCallback<Boolean> callback) {
        mAllLoadedCallback = callback;
    }

    public List<SurespotMessage> getMessages() {
        return mMessages;
    }

    // get the last message that has an id
    public SurespotMessage getLastMessageWithId() {
        synchronized (mMessages) {
            for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(mMessages.size()); iterator.hasPrevious(); ) {
                SurespotMessage message = iterator.previous();
                if (message.getId() != null && message.getId() > 0 && !message.isGcm()) {
                    return message;
                }
            }
        }
        return null;
    }

    public SurespotMessage getFirstMessageWithId() {
        synchronized (mMessages) {
            for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(0); iterator.hasNext(); ) {
                SurespotMessage message = iterator.next();
                if (message.getId() != null && message.getId() > 0 && !message.isGcm()) {
                    return message;
                }
            }
        }
        return null;
    }

    // update the id and sent status of the message once we received
    private boolean addOrUpdateMessage(SurespotMessage message, boolean checkSequence, boolean sort) {

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
        }
        else {
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

                    if (message.getFileMessageData() != null) {
                        if (message.getFileMessageData().getSize() > 0) {
                            updateMessage.getFileMessageData().setSize(message.getFileMessageData().getSize());
                        }
                        if (message.getFileMessageData().getMimeType() != null) {
                            updateMessage.getFileMessageData().setMimeType(message.getFileMessageData().getMimeType());
                        }
                        if (message.getFileMessageData().getCloudUrl() != null) {
                            updateMessage.getFileMessageData().setCloudUrl(message.getFileMessageData().getCloudUrl());
                        }
                        if (message.getFileMessageData().getFilename() != null) {
                            updateMessage.getFileMessageData().setFilename(message.getFileMessageData().getFilename());
                        }
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
                //  SurespotLog.v(TAG, "updated message: %s", updateMessage);
            }
        }

        if (sort) {
            sort();
        }
        return added;
    }

    private void insertMessage(SurespotMessage message) {
        if (mMessages.indexOf(message) == -1) {
            mMessages.add(0, message);
        }
        else {
            SurespotLog.v(TAG, "insertMessage, message already present: %s", message);
        }
    }

    public void setMessages(ArrayList<SurespotMessage> messages) {
        if (messages.size() > 0) {
            mMessages.clear();
            mMessages.addAll(messages);
            mCurrentScrollPositionId = -1;
        }
    }

    public void addOrUpdateMessages(ArrayList<SurespotMessage> messages) {
        for (SurespotMessage message : messages) {
            addOrUpdateMessage(message, false, false);

        }
        sort();
    }

    @Override
    public int getCount() {
        return mMessages.size();
    }

    @Override
    public Object getItem(int position) {
        return mMessages.get(position);
    }

    @Override
    public int getItemViewType(int position) {
        SurespotMessage message = mMessages.get(position);
        return getTypeForMessage(mOurUsername, message);
    }

    public int getTypeForMessage(String ourUser, SurespotMessage message) {
        String otherUser = ChatUtils.getOtherUser(ourUser, message.getFrom(), message.getTo());
        if (otherUser.equals(message.getFrom())) {
            return TYPE_THEM;
        }
        else {
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
        SurespotLog.d(TAG, "setCurrentScrollPositionId: %d", currentScrollPositionId);
        mCurrentScrollPositionId = currentScrollPositionId;
    }

    public void setSelectedTop(int i) {
        SurespotLog.d(TAG, "setSelectedTop: %d", i);
        mSelectedTop = i;

    }

    public int getSelectedTop() {
        return mSelectedTop;
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
            }
            else {
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
            chatMessageViewHolder.imageView = (GifImageView) convertView.findViewById(R.id.messageImage);
            chatMessageViewHolder.imageView.getLayoutParams().height = SurespotConfiguration.getImageDisplayHeight();
            chatMessageViewHolder.voiceView = convertView.findViewById(R.id.messageVoice);
            chatMessageViewHolder.ivNotShareable = (ImageView) convertView.findViewById(R.id.messageImageNotShareable);
            chatMessageViewHolder.ivShareable = (ImageView) convertView.findViewById(R.id.messageImageShareable);
            chatMessageViewHolder.messageSize = (TextView) convertView.findViewById(R.id.messageSize);
            chatMessageViewHolder.voiceSeekBar = (SeekBar) convertView.findViewById(R.id.seekBarVoice);
            chatMessageViewHolder.voiceSeekBar.setEnabled(false);
            chatMessageViewHolder.voicePlayed = (ImageView) convertView.findViewById(R.id.voicePlayed);
            chatMessageViewHolder.voiceStop = (ImageView) convertView.findViewById(R.id.voiceStop);

            chatMessageViewHolder.mFileDownloadButton = (Button) convertView.findViewById(R.id.fileDownload);
            chatMessageViewHolder.mFileDownloadButton.setTag("download");
            chatMessageViewHolder.mFileDownloadButton.setOnClickListener(FileClickListener);
            chatMessageViewHolder.mFilename = (TextView) convertView.findViewById(R.id.fileFilename);
            chatMessageViewHolder.mFileOpenButton = (Button) convertView.findViewById(R.id.fileOpen);
            chatMessageViewHolder.mFileOpenButton.setTag("open");
            chatMessageViewHolder.mFileOpenButton.setOnClickListener(FileClickListener);
            chatMessageViewHolder.mFileView = convertView.findViewById(R.id.fileLayout);


            chatMessageViewHolder.tvToVersion = (TextView) convertView.findViewById(R.id.messageToVersion);
            chatMessageViewHolder.tvFromVersion = (TextView) convertView.findViewById(R.id.messageFromVersion);

            if (mDebugMode) {
                chatMessageViewHolder.tvId = (TextView) convertView.findViewById(R.id.messageId);

                chatMessageViewHolder.tvIv = (TextView) convertView.findViewById(R.id.messageIv);
                chatMessageViewHolder.tvData = (TextView) convertView.findViewById(R.id.messageData);
                chatMessageViewHolder.tvMimeType = (TextView) convertView.findViewById(R.id.messageMimeType);
            }

            convertView.setTag(chatMessageViewHolder);
        }

        final SurespotMessage item = (SurespotMessage) getItem(position);

        //SurespotLog.d(TAG, "color: %d", SurespotApplication.getTextColor());
        chatMessageViewHolder.tvText.setTextColor(SurespotApplication.getTextColor());
        chatMessageViewHolder.tvTime.setTextColor(SurespotApplication.getTextColor());
        chatMessageViewHolder.messageSize.setTextColor(SurespotApplication.getTextColor());

//        if (!SurespotConstants.MimeTypes.TEXT.equals(item.getMimeType())) {
//            SurespotLog.v(TAG, "rendering item: %s", item);
//        }

        if (item.getErrorStatus() > 0) {
            SurespotLog.v(TAG, "item has error: %s", item);
            ChatUtils.setMessageErrorText(mContext, chatMessageViewHolder.tvTime, item);
        }
        else {
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
            }
            else {
                if (item.getPlainData() == null && item.getPlainBinaryData() == null && item.getFileMessageData() == null) {
                    chatMessageViewHolder.tvTime.setText(R.string.message_loading_and_decrypting);
                }
                else {

                    if (item.getDateTime() != null) {
                        chatMessageViewHolder.tvTime.setText(DateFormat.getDateFormat(mContext).format(item.getDateTime()) + " "
                                + DateFormat.getTimeFormat(mContext).format(item.getDateTime()));
                    }
                    else {
                        chatMessageViewHolder.tvTime.setText("");
                        SurespotLog.v(TAG, "getView, item: %s", item);
                    }
                }
            }
        }


        switch (item.getMimeType()) {

            case SurespotConstants.MimeTypes.TEXT:

                chatMessageViewHolder.tvText.setVisibility(View.VISIBLE);
                chatMessageViewHolder.imageView.setVisibility(View.GONE);
                chatMessageViewHolder.voiceView.setVisibility(View.GONE);
                chatMessageViewHolder.messageSize.setVisibility(View.GONE);
                chatMessageViewHolder.mFileView.setVisibility(View.GONE);

                chatMessageViewHolder.imageView.clearAnimation();
                chatMessageViewHolder.imageView.setImageBitmap(null);
                if (item.getPlainData() != null) {
                    chatMessageViewHolder.tvText.clearAnimation();
                    chatMessageViewHolder.tvText.setText(item.getPlainData());
                }
                else {
                    chatMessageViewHolder.tvText.setText("");
                    mMessageDecryptor.decrypt(chatMessageViewHolder.tvText, item);
                }
                chatMessageViewHolder.ivNotShareable.setVisibility(View.GONE);
                chatMessageViewHolder.ivShareable.setVisibility(View.GONE);
                break;
            case SurespotConstants.MimeTypes.IMAGE:
                chatMessageViewHolder.tvText.setVisibility(View.GONE);
                chatMessageViewHolder.imageView.setVisibility(View.VISIBLE);
                chatMessageViewHolder.voiceView.setVisibility(View.GONE);
                chatMessageViewHolder.messageSize.setVisibility(View.GONE);
                chatMessageViewHolder.mFileView.setVisibility(View.GONE);

                //chatMessageViewHolder.tvText.clearAnimation();

                chatMessageViewHolder.tvText.setText("");
                if (!TextUtils.isEmpty(item.getData()) || !TextUtils.isEmpty(item.getPlainData())) {
                    mMessageImageDownloader.download(chatMessageViewHolder.imageView, item);
                }

                if (item.isShareable()) {

                    chatMessageViewHolder.ivNotShareable.setVisibility(View.GONE);
                    chatMessageViewHolder.ivShareable.setVisibility(View.VISIBLE);
                }
                else {
                    chatMessageViewHolder.ivNotShareable.setVisibility(View.VISIBLE);
                    chatMessageViewHolder.ivShareable.setVisibility(View.GONE);
                }

                break;
            case SurespotConstants.MimeTypes.M4A:
                chatMessageViewHolder.imageView.setVisibility(View.GONE);
                chatMessageViewHolder.voiceView.setVisibility(View.VISIBLE);
                chatMessageViewHolder.messageSize.setVisibility(View.GONE);
                chatMessageViewHolder.mFileView.setVisibility(View.GONE);

                if (type == TYPE_US) {
                    chatMessageViewHolder.voicePlayed.setVisibility(View.VISIBLE);
                }
                else {
                    if (item.isVoicePlayed()) {
                        SurespotLog.v(TAG, "chatAdapter setting played to visible");
                        chatMessageViewHolder.voicePlayed.setVisibility(View.VISIBLE);
                        chatMessageViewHolder.voicePlay.setVisibility(View.GONE);
                    }
                    else {
                        SurespotLog.v(TAG, "chatAdapter setting played to gone");
                        chatMessageViewHolder.voicePlayed.setVisibility(View.GONE);
                        chatMessageViewHolder.voicePlay.setVisibility(View.VISIBLE);
                    }
                }
                chatMessageViewHolder.voiceStop.setVisibility(View.GONE);
                //chatMessageViewHolder.tvText.clearAnimation();
                //chatMessageViewHolder.imageView.clearAnimation();
                chatMessageViewHolder.tvText.setVisibility(View.GONE);
                chatMessageViewHolder.tvText.setText("");
                chatMessageViewHolder.ivNotShareable.setVisibility(View.GONE);
                chatMessageViewHolder.ivShareable.setVisibility(View.GONE);

                mMessageVoiceDownloader.download(convertView, item);
                chatMessageViewHolder.voiceSeekBar.setTag(R.id.tagMessage, new WeakReference<SurespotMessage>(item));
                VoiceController.attach(chatMessageViewHolder.voiceSeekBar);
                break;

            case SurespotConstants.MimeTypes.GIF_LINK:
                if (type == TYPE_US) {
                    item.setDownloadGif(true);
                }

                if (!item.isDownloadGif()) {
                    boolean downloadGifs = Utils.getUserSharedPrefsBoolean(mContext, mOurUsername, "pref_download_gifs");
                    if (!downloadGifs) {
                        chatMessageViewHolder.imageView.setScaleType(ImageView.ScaleType.CENTER);
                        chatMessageViewHolder.imageView.setImageResource(UIUtils.isDarkTheme(mContext) ? R.drawable.play_circle_outline_light : R.drawable.play_circle_outline_dark);
                    }
                    else {
                        item.setDownloadGif(true);
                    }
                }

                chatMessageViewHolder.imageView.setVisibility(View.VISIBLE);
                chatMessageViewHolder.voiceView.setVisibility(View.GONE);
                chatMessageViewHolder.messageSize.setVisibility(View.GONE);
                chatMessageViewHolder.mFileView.setVisibility(View.GONE);
                //chatMessageViewHolder.tvText.clearAnimation();

                chatMessageViewHolder.tvText.setVisibility(View.GONE);
                chatMessageViewHolder.tvText.setText("");
                if (!TextUtils.isEmpty(item.getData()) || !TextUtils.isEmpty(item.getPlainData())) {
                    mGifDownloader.download(chatMessageViewHolder.imageView, item);
                }

                chatMessageViewHolder.ivNotShareable.setVisibility(View.GONE);
                chatMessageViewHolder.ivShareable.setVisibility(View.GONE);

                //won't be decrypted until they click on it if they don't have auto download enabled so set date/time here if we have it
                if (item.getDateTime() != null) {
                    chatMessageViewHolder.tvTime.setText(DateFormat.getDateFormat(mContext).format(item.getDateTime()) + " "
                            + DateFormat.getTimeFormat(mContext).format(item.getDateTime()));
                }
                break;
            case SurespotConstants.MimeTypes.FILE:

                chatMessageViewHolder.tvText.setVisibility(View.VISIBLE);
                chatMessageViewHolder.imageView.setVisibility(View.GONE);
                chatMessageViewHolder.voiceView.setVisibility(View.GONE);
                chatMessageViewHolder.messageSize.setVisibility(View.GONE);
                chatMessageViewHolder.mFileView.setVisibility(View.GONE);
                chatMessageViewHolder.imageView.clearAnimation();
                chatMessageViewHolder.imageView.setImageBitmap(null);
                chatMessageViewHolder.ivNotShareable.setVisibility(View.GONE);
                chatMessageViewHolder.ivShareable.setVisibility(View.GONE);
                chatMessageViewHolder.tvText.setText(mContext.getString(R.string.upgrade_for_files));
                if (item.getDateTime() != null) {
                    chatMessageViewHolder.tvTime.setText(DateFormat.getDateFormat(mContext).format(item.getDateTime()) + " "
                            + DateFormat.getTimeFormat(mContext).format(item.getDateTime()));
                }
                else {
                    chatMessageViewHolder.tvTime.setText("");
                    SurespotLog.v(TAG, "getView, item: %s", item);
                }
                break;

        }

        if (type == TYPE_US) {
            chatMessageViewHolder.vMessageSending.setVisibility(item.getId() == null ? View.VISIBLE : View.GONE);
            chatMessageViewHolder.vMessageSent.setVisibility(item.getId() != null ? View.VISIBLE : View.GONE);
        }

//        chatMessageViewHolder.tvToVersion.setVisibility(View.VISIBLE);
//        chatMessageViewHolder.tvFromVersion.setVisibility(View.VISIBLE);
//        chatMessageViewHolder.tvToVersion.setText(item.getToVersion());
//        chatMessageViewHolder.tvFromVersion.setText(item.getFromVersion());


        if (mDebugMode) {
            chatMessageViewHolder.tvId.setVisibility(View.VISIBLE);
            chatMessageViewHolder.tvIv.setVisibility(View.VISIBLE);
            chatMessageViewHolder.tvData.setVisibility(View.VISIBLE);
            chatMessageViewHolder.tvMimeType.setVisibility(View.VISIBLE);

            chatMessageViewHolder.tvId.setText("id: " + item.getId());
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
        public GifImageView imageView;
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


        public View mFileView;
        public TextView mFilename;
        public TextView mFilesize;
        public Button mFileOpenButton;
        public Button mFileDownloadButton;
    }

    private View.OnClickListener FileClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View v) {
            final String iv = ((View) v.getParent()).getTag().toString();
            final SurespotMessage message = getMessageByIv(iv);

            if (v.getTag().equals("download")) {
                SurespotLog.d(TAG, "FileClickListener, downloading file message: %s", message);
                FileTransferManager.download(mContext, mOurUsername, message, new IAsyncCallback<String>() {

                    @Override
                    public void handleResponse(String uri) {
                        SurespotMessage sm = getMessageByIv(iv);
                        if (sm != null) {
                            SurespotMessage.FileMessageData fmd = sm.getFileMessageData();
                            if (fmd != null) {
                                fmd.setLocalUri(uri);
                                v.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        notifyDataSetChanged();
                                    }
                                });

                            }
                        }
                    }
                });
            }

            if (v.getTag().equals("open")) {
                SurespotMessage.FileMessageData fmd = message.getFileMessageData();

                if (fmd != null) {
                    SurespotLog.d(TAG, "Opening file message, data: %s", fmd);
                    String path = fmd.getLocalUri();


                    if (path != null) {
                        Uri uri = Uri.parse(path);
                        File file = PBFileUtils.getFile(mContext, uri);
                        SurespotLog.d(TAG, "Opening from file, uri: %s, file: %s", uri, file);

                        Intent i = new Intent();
                        i.setAction(android.content.Intent.ACTION_VIEW);
                        i.setDataAndType(Uri.fromFile(file), fmd.getMimeType());
                        mContext.startActivity(i);
                    }
                }
            }
        }

    };

    public boolean addOrUpdateMessage(SurespotMessage message, boolean checkSequence, boolean sort, boolean notify) {
        boolean added = addOrUpdateMessage(message, checkSequence, sort);

        if (notify) {
            notifyDataSetChanged();
        }
        return added;
    }

    public void insertMessage(SurespotMessage message, boolean notify) {
        insertMessage(message);
        if (notify) {
            notifyDataSetChanged();
        }
    }

    public SurespotMessage deleteMessageByIv(String iv) {
        SurespotMessage message = null;
        synchronized (mMessages) {
            for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext(); ) {
                message = iterator.next();

                if (message.getIv().equals(iv)) {
                    iterator.remove();
                    message.setDeleted(true);
                    notifyDataSetChanged();
                    return message;
                }
            }
        }


        return null;
    }

    public SurespotMessage deleteMessageById(Integer id, boolean notify) {
        SurespotMessage message = null;
        synchronized (mMessages) {
            for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(mMessages.size()); iterator.hasPrevious(); ) {
                message = iterator.previous();

                Integer localId = message.getId();
                if (localId != null && localId.equals(id)) {
                    SurespotLog.v(TAG, "deleting message");
                    message.setDeleted(true);
                    iterator.remove();
                    if (notify) {
                        notifyDataSetChanged();
                    }
                    return message;
                }
            }
        }

        return null;
    }

    public SurespotMessage getMessageById(Integer id) {
        SurespotMessage message = null;
        synchronized (mMessages) {
            for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext(); ) {
                message = iterator.next();

                Integer localId = message.getId();
                if (localId != null && localId.equals(id)) {
                    return message;
                }
            }
        }
        return null;
    }

    public SurespotMessage getMessageByIv(String iv) {
        SurespotMessage message = null;
        synchronized (mMessages) {
            for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(mMessages.size()); iterator.hasPrevious(); ) {
                message = iterator.previous();

                String localIv = message.getIv();
                if (localIv != null && localIv.equals(iv)) {
                    return message;
                }
            }
        }

        return null;
    }

    public void sort() {
        synchronized (mMessages) {
            Collections.sort(mMessages);
        }
    }

    public void deleteAllMessages(int utaiMessageId) {
        //
        // mMessages.clear();
        synchronized (mMessages) {
            for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext(); ) {
                SurespotMessage message = iterator.next();

                if (message.getId() == null || (message.getId() != null && message.getId() <= utaiMessageId)) {
                    message.setDeleted(true);
                    iterator.remove();
                }
            }
        }
    }

    public void deleteTheirMessages(int utaiMessageId) {

        synchronized (mMessages) {
            for (ListIterator<SurespotMessage> iterator = mMessages.listIterator(); iterator.hasNext(); ) {
                SurespotMessage message = iterator.next();

                // if it's not our message, delete it
                if (message.getId() != null && message.getId() <= utaiMessageId && !message.getFrom().equals(mOurUsername)) {
                    message.setDeleted(true);
                    iterator.remove();
                }
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
    public void checkLoaded() {

        if (!mLoaded) {
            synchronized (mMessages) {
                for (SurespotMessage message : mMessages) {
                    if (message.isLoading() && !message.isLoaded()) {
                        return;
                    }
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

    public void addControlMessage(SurespotControlMessage message) {
        mControlMessages.add(message);
    }

    public CopyOnWriteArrayList<SurespotControlMessage> getControlMessages() {
        return mControlMessages;
    }
}
