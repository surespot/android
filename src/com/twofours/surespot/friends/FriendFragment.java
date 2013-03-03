package com.twofours.surespot.friends;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.method.TextKeyListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import ch.boye.httpclientandroidlib.client.HttpResponseException;

import com.actionbarsherlock.app.SherlockFragment;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.LetterOrDigitInputFilter;
import com.twofours.surespot.MultiProgressDialog;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;

public class FriendFragment extends SherlockFragment {
	private FriendAdapter mMainAdapter;

	protected static final String TAG = "FriendFragment";
	private MultiProgressDialog mMpdInviteFriend;
	private ChatController mChatController;
	private ListView mListView;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		final View view = inflater.inflate(R.layout.friend_fragment, container, false);

		mMpdInviteFriend = new MultiProgressDialog(this.getActivity(), "inviting friend", 750);

		mListView = (ListView) view.findViewById(R.id.main_list);

		// click on friend to join chat
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Friend friend = (Friend) mMainAdapter.getItem(position);
				if (friend.isFriend()) {

					if (mChatController.getMode() == ChatController.MODE_SELECT) {
						// reset action bar header
						Utils.configureActionBar(FriendFragment.this.getSherlockActivity(), "surespot",
								IdentityController.getLoggedInUser(), false);
					}
					mChatController.setCurrentChat(friend.getName());

				}
			}
		});

		Button addFriendButton = (Button) view.findViewById(R.id.bAddFriend);
		addFriendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inviteFriend();
			}
		});

		EditText editText = (EditText) view.findViewById(R.id.etFriend);
		editText.setFilters(new InputFilter[] { new LetterOrDigitInputFilter() });
		editText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					//
					inviteFriend();
					handled = true;
				}
				return handled;
			}
		});

		mChatController = MainActivity.getChatController();
		if (mChatController != null) {
			mMainAdapter = mChatController.getFriendAdapter();
			mListView.setAdapter(mMainAdapter);
			SurespotLog.v(TAG, "friend adapter set, : " + mMainAdapter);
			if (!mMainAdapter.isLoaded()) {
				SurespotLog.v(TAG, "setting friend observer");

				mMainAdapter.setLoadedCallback(new IAsyncCallback<Void>() {

					@Override
					public void handleResponse(Void result) {

						SurespotLog.v(TAG, "homeAdapter loaded");

						view.findViewById(R.id.progressBar).setVisibility(View.GONE);
						mListView.setEmptyView(view.findViewById(R.id.main_list_empty));
					}
				});

				view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);

			}

		}
		else {
			SurespotLog.v(TAG, "friend adapter not set");
		}

		// TODO adapter observer
		// mListView.setEmptyView(view.findViewById(R.id.main_list_empty));

		return view;
	};

	@Override
	public void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		SurespotLog.v(TAG, "onResume");
		// mChatController = MainActivity.getChatController();
		// if (mChatController != null) {
		// mMainAdapter = mChatController.getFriendAdapter();
		// mListView.setAdapter(mMainAdapter);

		// view.findViewById(R.id.progressBar).setVisibility(View.GONE);
		// SurespotLog.v(TAG, "friend adapter set, : " + mMainAdapter);
		// }
		// else {
		// SurespotLog.v(TAG, "friend adapter not set");
		// }

	}

	@Override
	public void onDestroyView() {
		// TODO Auto-generated method stub
		super.onDestroyView();

		//
	}

	private void inviteFriend() {
		final EditText etFriend = ((EditText) getView().findViewById(R.id.etFriend));
		final String friend = etFriend.getText().toString();

		if (friend.length() > 0) {
			if (friend.equals(IdentityController.getLoggedInUser())) {
				// TODO let them be friends with themselves?
				Utils.makeToast(this.getActivity(), "You can't be friends with yourself, bro.");
				return;
			}

			mMpdInviteFriend.incrProgress();
			MainActivity.getNetworkController().invite(friend, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(int statusCode, String arg0) { // TODO
																		// indicate
																		// in
																		// the
																		// UI
					// that the request is
					// pending somehow
					TextKeyListener.clear(etFriend.getText());
					mMainAdapter.addFriendInvited(friend);
					Utils.makeToast(FriendFragment.this.getActivity(), friend + " has been invited to be your friend.");
				}

				@Override
				public void onFailure(Throwable arg0, String content) {
					if (arg0 instanceof HttpResponseException) {
						HttpResponseException error = (HttpResponseException) arg0;
						int statusCode = error.getStatusCode();
						switch (statusCode) {
						case 404:
							Utils.makeToast(FriendFragment.this.getActivity(), "User does not exist.");
							break;
						case 409:
							Utils.makeToast(FriendFragment.this.getActivity(), "You are already friends.");
							break;
						case 403:
							Utils.makeToast(FriendFragment.this.getActivity(), "You have already invited this user.");
							break;
						default:
							SurespotLog.w(TAG, "inviteFriend: " + content, arg0);
							Utils.makeToast(FriendFragment.this.getActivity(), "Could not invite friend, please try again later.");
						}
					}
					else {
						SurespotLog.w(TAG, "inviteFriend: " + content, arg0);
						Utils.makeToast(FriendFragment.this.getActivity(), "Could not invite friend, please try again later.");
					}
				}

				@Override
				public void onFinish() {
					mMpdInviteFriend.decrProgress();
				}
			});
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {

	}
}
