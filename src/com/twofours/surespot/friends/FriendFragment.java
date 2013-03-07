package com.twofours.surespot.friends;

import java.util.Timer;
import java.util.TimerTask;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
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
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;

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
		
		//mListView.setEmptyView(view.findViewById(R.id.progressBar));
		// mListView.setEmptyView(view.findViewById(R.id.main_list_empty));
		// click on friend to join chat
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Friend friend = (Friend) mMainAdapter.getItem(position);
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
		});

		Button addFriendButton = (Button) view.findViewById(R.id.bAddFriend);
		addFriendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inviteFriend();
			}
		});

		EditText editText = (EditText) view.findViewById(R.id.etFriend);
		editText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_USERNAME_LENGTH), new LetterOrDigitInputFilter() });
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
			mListView.setAdapter(mMainAdapter);

			if (!mMainAdapter.isLoaded()) {
				view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
			}
			
			SurespotLog.v(TAG, "friend adapter set, : " + mMainAdapter);
			SurespotLog.v(TAG, "setting loading callback");
			mMainAdapter.setLoadingCallback(new IAsyncCallback<Boolean>() {

				@Override
				public void handleResponse(final Boolean loading) {

					if (loading) {
						// view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
						// view.findViewById(R.id.main_list_empty).setVisibility(View.GONE);
						// only show the dialog if we haven't loaded within 500 ms
						mTimer = new Timer();
						mTimer.schedule(new TimerTask() {

							@Override
							public void run() {

								Handler handler = MainActivity.getMainHandler();
								if (handler != null) {
									handler.post(new Runnable() {

										@Override
										public void run() {
											if (loading) {
												view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
											}
										}
									});
								}

							}
						}, 200);

					}
					else {
						if (mTimer != null) {
							mTimer.cancel();
							mTimer = null;
						}

						mListView.setEmptyView(view.findViewById(R.id.main_list_empty));
						view.findViewById(R.id.progressBar).setVisibility(View.GONE);
					}
				}
			});
		}

		return view;
	}

	// populate the edit box
	private void sendFromIntent(String username) {
		Intent intent = getActivity().getIntent();
		String action = intent.getAction();
		String type = intent.getType();
		Bundle extras = intent.getExtras();

		if (action.equals(Intent.ACTION_SEND)) {
			// if (SurespotConstants.MimeTypes.TEXT.equals(type)) {
			// String sharedText = extras.getString(Intent.EXTRA_TEXT);
			// SurespotLog.v(TAG, "received action send, data: " + sharedText);
			// mEditText.append(sharedText);
			// requestFocus();
			// }
			// else
			if (type.startsWith(SurespotConstants.MimeTypes.IMAGE)) {

				final Uri imageUri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);

				Utils.makeToast(getActivity(), getString(R.string.uploading_image));

				SurespotLog.v(TAG, "received image data, upload image, uri: " + imageUri);
				final FragmentActivity activity = getActivity();
				ChatUtils.uploadPictureMessageAsync(activity, imageUri, username, true, null, new IAsyncCallback<Boolean>() {

					@Override
					public void handleResponse(final Boolean result) {
						SurespotLog.v(TAG, "upload picture response: " + result);
						activity.runOnUiThread(new Runnable() {

							@Override
							public void run() {
								Utils.makeToast(activity, getString(result ? R.string.image_successfully_uploaded
										: R.string.could_not_upload_image));

							}
						});

						getActivity().getIntent().setAction(null);
						getActivity().getIntent().setType(null);

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
	

	private MainActivity getMainActivity() {
		 return (MainActivity) getActivity();
	}

}
