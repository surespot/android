package com.twofours.surespot.friends;

import java.util.Timer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.InputFilter;
import android.text.method.TextKeyListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import ch.boye.httpclientandroidlib.client.HttpResponseException;

import com.actionbarsherlock.app.SherlockFragment;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.LetterOrDigitInputFilter;
import com.twofours.surespot.ui.MultiProgressDialog;

public class FriendFragment extends SherlockFragment {
	private FriendAdapter mMainAdapter;

	protected static final String TAG = "FriendFragment";
	private MultiProgressDialog mMpdInviteFriend;
	// private ChatController mChatController;
	private ListView mListView;
	private Timer mTimer;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		final View view = inflater.inflate(R.layout.friend_fragment, container, false);

		mMpdInviteFriend = new MultiProgressDialog(this.getActivity(), "inviting friend", 750);

		mListView = (ListView) view.findViewById(R.id.main_list);

		// mListView.setEmptyView(view.findViewById(R.id.progressBar));
		// mListView.setEmptyView(view.findViewById(R.id.main_list_empty));
		// click on friend to join chat
		// mListView.setOnItemClickListener(new OnItemClickListener() {
		// @Override
		// public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		// Friend friend = (Friend) mMainAdapter.getItem(position);
		// if (friend.isFriend()) {
		//
		// ChatController chatController = getMainActivity().getChatController();
		// if (chatController != null) {
		// if (chatController.getMode() == ChatController.MODE_SELECT) {
		// // reset action bar header
		// Utils.configureActionBar(FriendFragment.this.getSherlockActivity(), "surespot",
		// IdentityController.getLoggedInUser(), false);
		//
		// // handle send intent
		// sendFromIntent(friend.getName());
		//
		// }
		// chatController.setCurrentChat(friend.getName());
		// }
		//
		// }
		// }
		// });
		//
		// mListView.setOnItemLongClickListener(new OnItemLongClickListener() {
		// @Override
		// public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		// Friend friend = (Friend) mMainAdapter.getItem(position);
		//
		// if (!friend.isInviter()) {
		// FriendMenuFragment dialog = new FriendMenuFragment();
		// dialog.setActivityAndFriend(getMainActivity(), friend);
		// dialog.show(getActivity().getSupportFragmentManager(), "FriendMenuFragment");
		// }
		// return true;
		//
		// }
		// });

		Button addFriendButton = (Button) view.findViewById(R.id.bAddFriend);
		addFriendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inviteFriend();
			}
		});

		EditText editText = (EditText) view.findViewById(R.id.etFriend);
		editText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_USERNAME_LENGTH),
				new LetterOrDigitInputFilter() });
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

		ChatController chatController = getMainActivity().getChatController();
		if (chatController != null) {
			mMainAdapter = chatController.getFriendAdapter();
			mMainAdapter.setItemListeners(mClickListener, mLongClickListener);

			mListView.setAdapter(mMainAdapter);

			if (!mMainAdapter.isLoaded()) {
				SurespotLog.v(TAG, "setting progressbarvisible");
				view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
			}

			SurespotLog.v(TAG, "friend adapter set, : " + mMainAdapter);
			SurespotLog.v(TAG, "setting loading callback");
			mMainAdapter.setLoadingCallback(new IAsyncCallback<Boolean>() {

				@Override
				public void handleResponse(final Boolean loading) {

					if (!loading) {
						// view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
						// view.findViewById(R.id.main_list_empty).setVisibility(View.GONE);
						// only show the dialog if we haven't loaded within 500 ms
						// mTimer = new Timer();
						// mTimer.schedule(new TimerTask() {
						//
						// @Override
						// public void run() {
						//
						// Handler handler = MainActivity.getMainHandler();
						// if (handler != null) {
						// handler.post(new Runnable() {
						//
						// @Override
						// public void run() {
						// if (loading) {
						// SurespotLog.v(TAG, "showing progress");
						// view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
						// }
						// }
						// });
						// }
						//
						// }
						// }, 200);

						// }
						// else {
						if (mTimer != null) {
							mTimer.cancel();
							mTimer = null;
						}

						SurespotLog.v(TAG, "tearing progress down");
						mListView.setEmptyView(view.findViewById(R.id.main_list_empty));
						view.findViewById(R.id.progressBar).setVisibility(View.GONE);
					}
				}
			});
		}

		return view;
	}

	OnClickListener mClickListener = new OnClickListener() {
		@Override
		public void onClick(View view) {
			Friend friend = (Friend) view.getTag();
			if (friend.isFriend()) {

				ChatController chatController = getMainActivity().getChatController();
				if (chatController != null) {
					if (chatController.getMode() == ChatController.MODE_SELECT) {
						// reset action bar header
						Utils.configureActionBar(FriendFragment.this.getSherlockActivity(), "surespot",
								IdentityController.getLoggedInUser(), false);

						// handle send intent
						sendFromIntent(friend.getName());

					}
					chatController.setCurrentChat(friend.getName());
				}

			} 
		}
	};

	OnLongClickListener mLongClickListener = new OnLongClickListener() {
		@Override
		public boolean onLongClick(View view) {
			Friend friend = (Friend) view.getTag();

			if (!friend.isInviter()) {
				FriendMenuFragment dialog = new FriendMenuFragment();
				dialog.setActivityAndFriend(getMainActivity(), friend);
				dialog.show(getActivity().getSupportFragmentManager(), "FriendMenuFragment");
			}
			return true;

		}

	};

	// populate the edit box
	private void sendFromIntent(String username) {
		Intent intent = getActivity().getIntent();
		String action = intent.getAction();
		String type = intent.getType();
		Bundle extras = intent.getExtras();

		if (action.equals(Intent.ACTION_SEND)) {
			if (type.startsWith(SurespotConstants.MimeTypes.IMAGE)) {

				final Uri imageUri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);

				// Utils.makeToast(getActivity(), getString(R.string.uploading_image));

				SurespotLog.v(TAG, "received image data, upload image, uri: " + imageUri);
				final FragmentActivity activity = getActivity();
				ChatUtils.uploadPictureMessageAsync(activity, getMainActivity().getChatController(), getMainActivity()
						.getNetworkController(), imageUri, username, true, new IAsyncCallback<Boolean>() {

					@Override
					public void handleResponse(final Boolean result) {
						SurespotLog.v(TAG, "upload picture response: " + result);

						if (!result) {
							activity.runOnUiThread(new Runnable() {

								@Override
								public void run() {
									Utils.makeToast(activity, getString(R.string.could_not_upload_image));
									// clear the intent

								}
							});
						}

						activity.getIntent().setAction(null);
						activity.getIntent().setType(null);
						if (activity.getIntent().getExtras() != null) {
							activity.getIntent().getExtras().clear();
						}

						// scrollToEnd();
					}
				});
				// }
			}
		}
		else {
			if (action.equals(Intent.ACTION_SEND_MULTIPLE)) {
				// TODO implement
			}
		}
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
					mMpdInviteFriend.decrProgress();
					TextKeyListener.clear(etFriend.getText());
					if (mMainAdapter.addFriendInvited(friend)) {
						Utils.makeToast(FriendFragment.this.getActivity(), friend + " has been invited to be your friend.");
					}
					else {
						Utils.makeToast(FriendFragment.this.getActivity(), friend + " has accepted your friend request.");
					}

				}

				@Override
				public void onFailure(Throwable arg0, String content) {
					mMpdInviteFriend.decrProgress();
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
			});
		}
	}

	private MainActivity getMainActivity() {
		return (MainActivity) getActivity();
	}

}
