package com.twofours.surespot.friends;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatManager;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.IAsyncCallbackTriplet;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.utils.UIUtils;

public class FriendFragment extends Fragment {
    private FriendAdapter mMainAdapter;

    protected static final String TAG = "FriendFragment";
    // private MultiProgressDialog mMpdInviteFriend;
    // private ChatController mChatController;
    private ListView mListView;
    private AlertDialog mDialog;
    private String mUsername;

    public static FriendFragment newInstance(String username) {
        FriendFragment cf = new FriendFragment();

        Bundle bundle = new Bundle();
        bundle.putString("username", username);
        cf.setArguments(bundle);
        return cf;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        SurespotLog.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        mUsername = getArguments().getString("username");
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        final View view = inflater.inflate(R.layout.friend_fragment, container, false);

        Button tvShareLink = (Button) view.findViewById(R.id.tvShareInvite);
        tvShareLink.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG);
        tvShareLink.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                UIUtils.sendInvitation(getActivity(), NetworkManager.getNetworkController(getActivity(), mUsername), mUsername);

            }
        });

        Button tvHelp = (Button) view.findViewById(R.id.tvHelp);
        tvHelp.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG);
        tvHelp.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                mDialog = UIUtils.showHelpDialog(getActivity(), false);

            }
        });
        // mMpdInviteFriend = new MultiProgressDialog(this.getActivity(), "inviting friend", 750);

        mListView = (ListView) view.findViewById(R.id.main_list);
        mListView.setEmptyView(view.findViewById(R.id.main_list_empty));

        TextView tvWelcome = (TextView) view.findViewById(R.id.tvWelcome);
        UIUtils.setHtml(getActivity(), tvWelcome, R.string.welcome_to_surespot);


        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ChatController chatController = ChatManager.getChatController(mUsername);
        if (chatController != null) {
            mMainAdapter = chatController.getFriendAdapter();

            mListView.setAdapter(mMainAdapter);
            mListView.setOnItemClickListener(mClickListener);
            mListView.setOnItemLongClickListener(mLongClickListener);
        }
    }

    AdapterView.OnItemClickListener mClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            Friend friend = ((FriendAdapter.FriendViewHolder) view.getTag()).friend;
            if (friend.isFriend()) {

                ChatController chatController = ChatManager.getChatController(mUsername);
                if (chatController != null) {

                    chatController.setCurrentChat(friend.getName());
                }
            }
        }
    };

    AdapterView.OnItemLongClickListener mLongClickListener = new AdapterView.OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {

            Friend friend = ((FriendAdapter.FriendViewHolder) view.getTag()).friend;

            if (!friend.isInviter()) {
                FriendMenuFragment dialog = new FriendMenuFragment();

                dialog.setActivityAndFriend(friend, new IAsyncCallbackTriplet<DialogInterface, Friend, Integer>() {
                    public void handleResponse(DialogInterface dialogi, Friend friend, Integer selection) {
                        handleMenuSelection(dialogi, friend, selection);
                    }

                    ;
                });

                try {
                    dialog.show(getActivity().getFragmentManager(), "FriendMenuFragment");
                }
                catch (IllegalStateException e) {
                    //swallow this fucker
                    //AOSP bug
                    //https://stackoverflow.com/questions/27329913/dialogfragshow-from-a-fragment-throwing-illegalstateexception-can-not-perfo
                }
            }
            return true;


        }

    };

    private void handleMenuSelection(final DialogInterface dialogi, final Friend friend, int selection) {
        final MainActivity activity = this.getMainActivity();

        final ChatController cc = ChatManager.getChatController(mUsername);

        switch (selection) {
            case R.string.mute:
                if (cc != null) {
                    cc.mute(friend.getName());
                }
                break;
            case R.string.unmute:
                if (cc != null) {
                    cc.unmute(friend.getName());
                }
                break;
            case R.string.menu_close_tab:
                if (cc != null) {
                    cc.closeTab(friend.getName());
                }
                break;
            case R.string.menu_assign_image:
                activity.uploadFriendImage(friend.getName(), friend.getNameOrAlias());
                break;
            case R.string.menu_remove_friend_image:
                activity.removeFriendImage(friend.getName());
                break;
            case R.string.menu_assign_alias:
                activity.assignFriendAlias(friend.getName());
                break;
            case R.string.menu_remove_friend_alias:
                activity.removeFriendAlias(friend.getName());
                break;
            case R.string.verify_key_fingerprints:
                UIUtils.showKeyFingerprintsDialog(activity, mUsername, friend.getName(), friend.getAliasPlain());
                break;
            case R.string.menu_delete_all_messages:

                SharedPreferences sp = activity.getSharedPreferences(mUsername, Context.MODE_PRIVATE);
                boolean confirm = sp.getBoolean("pref_delete_all_messages", true);
                if (confirm) {
                    mDialog = UIUtils.createAndShowConfirmationDialog(activity, getString(R.string.delete_all_confirmation),
                            getMainActivity().getString(R.string.delete_all_title), getString(R.string.ok), getString(R.string.cancel),
                            new IAsyncCallback<Boolean>() {
                                public void handleResponse(Boolean result) {
                                    if (result) {
                                        if (cc != null) {
                                            cc.deleteMessages(friend);
                                        }
                                    }

                                }

                                ;
                            });
                }
                else {
                    if (cc != null) {
                        cc.deleteMessages(friend);
                    }
                }

                break;
            case R.string.menu_delete_friend:
                mDialog = UIUtils.createAndShowConfirmationDialog(
                        activity,
                        getMainActivity().getString(R.string.delete_friend_confirmation,
                                UIUtils.buildAliasString(friend.getName(), friend.getAliasPlain())),
                        getMainActivity().getString(R.string.menu_delete_friend), getString(R.string.ok), getString(R.string.cancel),
                        new IAsyncCallback<Boolean>() {
                            public void handleResponse(Boolean result) {
                                if (result) {
                                    if (cc != null) {
                                        cc.deleteFriend(friend);
                                    }
                                    else {
                                        dialogi.cancel();
                                    }
                                }
                                else {
                                    dialogi.cancel();
                                }
                            }

                            ;
                        });
                break;


        }

    }

    private MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }


    @Override
    public void onPause() {
        super.onPause();
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }
}
