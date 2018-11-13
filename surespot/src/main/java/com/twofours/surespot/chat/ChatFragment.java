package com.twofours.surespot.chat;


import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.gifs.GifMessageMenuFragment;
import com.twofours.surespot.images.ImageMessageMenuFragment;
import com.twofours.surespot.images.ImageViewActivity;
import com.twofours.surespot.images.MessageImageDownloader;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.voice.VoiceController;
import com.twofours.surespot.voice.VoiceMessageMenuFragment;

public class ChatFragment extends Fragment {
    private String TAG = "ChatFragment";
    private String mTheirUsername;
    private String mOurUsername;
    private ListView mListView;

    private boolean mLoading;
    private int mPreviousTotal;

    private int mSelectedItem = -1;
    private int mSelectedTop = 0;
    private boolean mJustLoaded;
    private ChatAdapter mChatAdapter;
    private boolean mHasEarlier = true;

    public String getTheirUsername() {
        if (mTheirUsername == null) {
            mTheirUsername = getArguments().getString("theirUsername");
        }
        return mTheirUsername;
    }

    public void setOurUsername(String ourUsername) {
        this.mOurUsername = ourUsername;
    }

    public String getOurUsername() {
        if (mOurUsername == null) {
            mOurUsername = getArguments().getString("ourUsername");
        }
        return mOurUsername;
    }

    public void setTheirUsername(String mTheirUsername) {
        this.mTheirUsername = mTheirUsername;
    }


    public static ChatFragment newInstance(String ourUsername, String theirUsername) {
        String tTAG = "ChatFragment:" + ourUsername + ":" + theirUsername;
        SurespotLog.d(tTAG, "newInstance");
        ChatFragment cf = new ChatFragment();

        Bundle bundle = new Bundle();
        bundle.putString("theirUsername", theirUsername);
        bundle.putString("ourUsername", ourUsername);
        cf.setArguments(bundle);
        return cf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheirUsername(getArguments().getString("theirUsername"));
        setOurUsername(getArguments().getString("ourUsername"));
        TAG = "ChatFragment:" + getOurUsername() + ":" + getTheirUsername();
        SurespotLog.d(TAG, "onCreate");

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        SurespotLog.v(TAG, "onCreateView, username: %s", mTheirUsername);

        final View view = inflater.inflate(R.layout.chat_fragment, container, false);
        mListView = (ListView) view.findViewById(R.id.message_list);
        mListView.setEmptyView(view.findViewById(R.id.message_list_empty));

        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                SurespotMessage message = (SurespotMessage) mChatAdapter.getItem(position);

                // pull the message out
                if (message != null) {

                    if (message.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
                        ImageView imageView = (ImageView) view.findViewById(R.id.messageImage);
                        if (!(imageView.getDrawable() instanceof MessageImageDownloader.DownloadedDrawable)) {

                            Intent newIntent = new Intent(ChatFragment.this.getActivity(), ImageViewActivity.class);
                            newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            newIntent.putExtra(SurespotConstants.ExtraNames.IMAGE_MESSAGE, message.toJSONObject(false).toString());
                            newIntent.putExtra("ourUsername", mOurUsername);
                            ChatFragment.this.getActivity().startActivity(newIntent);
                        }
                    }
                    else {
                        if (message.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
                            SeekBar seekBar = (SeekBar) view.findViewById(R.id.seekBarVoice);
                            VoiceController.playVoiceMessage(ChatFragment.this.getActivity(), seekBar, message);
                        }
                        else {
                            if (message.getMimeType().equals(SurespotConstants.MimeTypes.GIF_LINK)) {
                                message.setDownloadGif(true);
                                mChatAdapter.notifyDataSetChanged();
                            }
                        }
                    }
                }
            }
        });

        mListView.setOnItemLongClickListener(new OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

                SurespotMessage message = (SurespotMessage) mChatAdapter.getItem(position);
                try {
                    DialogFragment dialog;
                    switch (message.getMimeType()) {
                        case SurespotConstants.MimeTypes.TEXT:
                            dialog = TextMessageMenuFragment.newInstance(mOurUsername, message);
                            dialog.show(getActivity().getSupportFragmentManager(), "TextMessageMenuFragment");
                            break;
                        case SurespotConstants.MimeTypes.IMAGE:
                            dialog = ImageMessageMenuFragment.newInstance(mOurUsername, message);
                            dialog.show(getActivity().getSupportFragmentManager(), "ImageMessageMenuFragment");
                            break;
                        case SurespotConstants.MimeTypes.M4A:
                            dialog = VoiceMessageMenuFragment.newInstance(mOurUsername, message);
                            dialog.show(getActivity().getSupportFragmentManager(), "VoiceMessageMenuFragment");
                            break;
                        case SurespotConstants.MimeTypes.GIF_LINK:
                        case SurespotConstants.MimeTypes.FILE:
                        default:
                            dialog = GifMessageMenuFragment.newInstance(mOurUsername, message);
                            dialog.show(getActivity().getSupportFragmentManager(), "GifMessageMenuFragment");
                            break;
                    }

                    return true;
                }
                catch (IllegalStateException e) {
                    //swallow this fucker
                    //AOSP bug
                    //https://stackoverflow.com/questions/27329913/dialogfragshow-from-a-fragment-throwing-illegalstateexception-can-not-perfo
                }
                return false;
            }
        });

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ChatController chatController = ChatManager.getChatController(getOurUsername());
        if (chatController != null) {
            mChatAdapter = chatController.getChatAdapter(mTheirUsername);
            mChatAdapter.setAllLoadedCallback(new IAsyncCallback<Boolean>() {

                @Override
                public void handleResponse(Boolean result) {

                    SurespotLog.v(TAG, "messages completed loading, scrolling to state, count: %d", mChatAdapter.getCount());
                    scrollToState();
                }
            });
            SurespotLog.v(TAG, "onActivityCreated settingChatAdapter for: " + mTheirUsername);

            mListView.setAdapter(mChatAdapter);
            mListView.setDividerHeight(1);
            mListView.setOnScrollListener(mOnScrollListener);

        }
    }

    private MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    private OnScrollListener mOnScrollListener = new OnScrollListener() {

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            // SurespotLog.v(TAG, "onScroll, mLoadiNG : " + mLoading + ", totalItemCount: " + totalItemCount + ", firstVisibleItem: "
            // + firstVisibleItem + ", visibleItemCount: " + visibleItemCount);

            if (!mLoading) {
                boolean hint = getUserVisibleHint();
                // SurespotLog.v(TAG, "hint: " + hint);
                if (hint) {
                    MainActivity mainActivity = getMainActivity();
                    if (mainActivity == null) {
                        return;
                    }
                    ChatController chatController = ChatManager.getChatController(getOurUsername());
                    if (chatController == null) {
                        return;
                    }
                    boolean hasEarlier = chatController.hasEarlierMessages(mTheirUsername);
                    // SurespotLog.v(TAG, "hasEarlier: " + hasEarlier);
                    if (hasEarlier && mHasEarlier && (firstVisibleItem > 0 && firstVisibleItem < 20)) {

                        //  SurespotLog.v(TAG, "onScroll, totalItemCount: " + totalItemCount + ", firstVisibleItem: " + firstVisibleItem
                        //+ ", visibleItemCount: " + visibleItemCount);

                        // immediately after setting the position above, mLoading is false with "firstVisibleItem" set to the pre loading
                        // value for what seems like one call
                        // so handle that here
                        if (mJustLoaded) {
                            mJustLoaded = false;
                            return;
                        }
                        else {

                            mLoading = true;
                            mPreviousTotal = mChatAdapter.getCount();
                            // mSelection = firstVisibleItem;
                            // View v = mListView.getChildAt(0);
                            // mTop = (v == null) ? 0 : v.getTop();

                            chatController.loadEarlierMessages(mTheirUsername, new IAsyncCallback<Boolean>() {

                                @Override
                                public void handleResponse(Boolean loadedNew) {

                                    int selection = mListView.getFirstVisiblePosition();
                                    // mSelection = firstVisibleItem;
                                    View v = mListView.getChildAt(0);
                                    int top = (v == null) ? 0 : v.getTop();
                                    int totalItemCount = mChatAdapter.getCount();
                                    // will have more items if we loaded them
                                    if (mLoading && mPreviousTotal > 0 && totalItemCount > mPreviousTotal) {
                                        // SurespotLog.v(TAG, "mPreviousTotal: " + mPreviousTotal + ", totalItemCount: " + totalItemCount);

                                        // mChatAdapter.notifyDataSetChanged();

                                        int loaded = totalItemCount - mPreviousTotal;
                                        // SurespotLog.v(TAG, "loaded: " + loaded + ", setting selection: " + (mSelection + loaded));
                                        mListView.setSelectionFromTop(selection + loaded, top);

                                        // mPreviousTotal = totalItemCount;
                                        mJustLoaded = true;
                                        mLoading = false;
                                        return;
                                    }
                                    else {
                                        mJustLoaded = false;
                                        mLoading = false;

                                    }

                                    if (!loadedNew) {
                                        mHasEarlier = false;
                                    }
                                }
                            });
                        }
                    }
                }
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {

        }
    };

    @Override
    public void onResume() {
        super.onResume();
        SurespotLog.v(TAG, "onResume: " + mTheirUsername);

        ChatController chatController = ChatManager.getChatController(getOurUsername());

        if (chatController != null) {
            Friend friend = chatController.getFriendAdapter().getFriend(mTheirUsername);

            if (friend != null) {
                SurespotLog.v(TAG, "onResume, selectedItem: " + mSelectedItem);
                mChatAdapter = chatController.getChatAdapter(mTheirUsername, false);
                if (mChatAdapter != null && mChatAdapter.isLoaded()) {
                    SurespotLog.v(TAG, "chat adapter loaded already, scrolling, count: %d", mChatAdapter.getCount());
                    mSelectedItem = mChatAdapter.getCurrentScrollPositionId();
                    mSelectedTop = mChatAdapter.getSelectedTop();
                    scrollToState();
                }
                else {
                    //if we're creating chat adapter anew, scroll to the bottom
                    mSelectedItem = -1;
                    mSelectedTop = 0;
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        SurespotLog.v(TAG, "onPause");
        updateScrollState();
    }

    public void updateScrollState() {
        if (mListView != null) {
            int firstVisiblePosition = mListView.getFirstVisiblePosition();
            int lastVisiblePosition = mListView.getLastVisiblePosition();
            int listViewCountMinusOne = mListView.getCount() - 1;

            SurespotLog.v(TAG, "updateScrollState, firstVisiblePosition: %d, lastVisiblePosition: %d, listView Count - 1: %d",
                    firstVisiblePosition, lastVisiblePosition, listViewCountMinusOne);

            if (lastVisiblePosition == -1 || lastVisiblePosition == listViewCountMinusOne) {
                mSelectedItem = -1;
                mSelectedTop = 0;
            }
            else {
                mSelectedItem = firstVisiblePosition;

                View v = mListView.getChildAt(0);
                int top = (v == null) ? 0 : v.getTop();
                mSelectedTop = top;

                SurespotLog.v(TAG, "updateScrollState we are not scrolled to bottom - saving selected item: %d, top: %d", mSelectedItem, mSelectedTop);
            }

            if (mChatAdapter != null) {
                mChatAdapter.setCurrentScrollPositionId(mSelectedItem);
                mChatAdapter.setSelectedTop(mSelectedTop);
            }
        }
    }

    public void scrollToEnd() {
        SurespotLog.d(TAG, "scrollToEnd: %s", mTheirUsername);
        if (mChatAdapter != null && mListView != null) {
            mListView.postDelayed(new Runnable() {

                @Override
                public void run() {
                    mListView.setSelection(mChatAdapter.getCount() - 1);
                }
            }, 400);
        }
    }

    public void scrollToState() {
        SurespotLog.v(TAG, "scrollToState, mSelectedItem: %s, selectedTop: %s", mSelectedItem, mSelectedTop);
        if (mChatAdapter != null && mListView != null) {
            if (mSelectedItem > -1 && mSelectedItem != mListView.getSelectedItemPosition()) {
                mListView.post(new Runnable() {

                    @Override
                    public void run() {
                        mListView.setSelectionFromTop(mSelectedItem, mSelectedTop);
                    }

                });
            }
            else {
                scrollToEnd();
            }
        }
    }
}
