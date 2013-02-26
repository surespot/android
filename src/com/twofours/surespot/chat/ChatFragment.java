package com.twofours.surespot.chat;

import android.content.Intent;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.method.TextKeyListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.actionbarsherlock.app.SherlockFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;

public class ChatFragment extends SherlockFragment {
	private String TAG = "ChatFragment";
	private ChatController mChatController;
	private String mUsername;
	private ListView mListView;
	private EditText mEditText;
	private boolean mLoading;
	private int mPreviousTotal;
	private boolean mNoEarlierMessages = false;

	public String getUsername() {
		if (mUsername == null) {
			mUsername = getArguments().getString("username");
		}
		return mUsername;
	}

	public void setUsername(String mUsername) {
		this.mUsername = mUsername;
	}

	public static ChatFragment newInstance(String username) {
		ChatFragment cf = new ChatFragment();
		Bundle bundle = new Bundle();
		bundle.putString("username", username);
		cf.setArguments(bundle);
		return cf;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mChatController = SurespotApplication.getChatController();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);

		setUsername(getArguments().getString("username"));
		TAG = TAG + ":" + getUsername();
		SurespotLog.v(TAG, "onCreate, username: " + mUsername);

		final View view = inflater.inflate(R.layout.chat_fragment, container, false);
		//

		mListView = (ListView) view.findViewById(R.id.message_list);
		final ChatAdapter chatAdapter = mChatController.getChatAdapter(this.getSherlockActivity().getBaseContext(), mUsername);

		// listen for first change then set empty list view
		chatAdapter.registerDataSetObserver(new DataSetObserver() {
			@Override
			public void onChanged() {
				view.findViewById(R.id.progressBar).setVisibility(View.GONE);

				mListView.setEmptyView(view.findViewById(R.id.message_list_empty));
				// view.findViewById(R.id.message_list_empty).setVisibility(View.VISIBLE);

				// else {
				// mListView.setEmptyView(view.findViewById(R.id.message_list_empty));
				chatAdapter.unregisterDataSetObserver(this);
				// }
			}
		});
		mListView.setAdapter(chatAdapter);
		mListView.setDividerHeight(1);
		// mListView.setEmptyView(view.findViewById(R.id.message_list_empty));
		view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
		// view.findViewById(R.id.message_list_empty).setVisibility(View.GONE);
		// mListView.setEmptyView(view.findViewById(R.id.progressBar));

		Button sendButton = (Button) view.findViewById(R.id.bSend);
		sendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sendMessage();
			}
		});
		mEditText = (EditText) view.findViewById(R.id.etMessage);
		mEditText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_SEND) {
					//
					sendMessage();
					handled = true;
				}
				return handled;
			}
		});

		Intent intent = getActivity().getIntent();
		String action = intent.getAction();
		String type = intent.getType();

		String intentName = intent.getStringExtra(SurespotConstants.ExtraNames.MESSAGE_FROM);

		// if the intent is meant for this chat
		if (intentName != null && intentName.equals(mUsername)) {

			if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
				// we have a send action so populate the edit box with the data
				handleSendIntent(action, type, intent.getExtras());

				// remove intent data so we don't upload an image on restart
				intent.setAction(null);
				intent.setType(null);
				intent.removeExtra(SurespotConstants.ExtraNames.MESSAGE_FROM);
			}
		}

		// listen to scroll changes
		mListView.setOnScrollListener(new OnScrollListener() {

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

				if (mLoading) {
					// will have more items if we loaded them
					if (totalItemCount > mPreviousTotal) {
						mPreviousTotal = totalItemCount;
						mLoading = false;
					}
				}

				if (!mLoading && mChatController.hasEarlierMessages(mUsername) && firstVisibleItem <= 10) {
					// SurespotLog.v(TAG, "onScroll: Loading more messages.");
					// SurespotLog.v(TAG, "onScroll, totalItemCount: " + totalItemCount + ", firstVisibleItem: " +
					// firstVisibleItem
					// + ", visibleItemCount: " + visibleItemCount);
					mLoading = true;
					mChatController.loadEarlierMessages(mUsername);

				}
			}

			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {

			}
		});

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		Utils.logIntent(TAG, getActivity().getIntent());

	}

	@Override
	public void onPause() {
		super.onPause();
		SurespotLog.v(TAG, "onPause, mUsername:  " + mUsername);
		// mChatController.saveMessages(mUsername);
		// mChatAdapter.evictCache();
	}

	public void onDestroy() {
		super.onDestroy();
		SurespotLog.v(TAG, "onDestroy");

	}

	private void sendMessage() {
		final EditText etMessage = ((EditText) getView().findViewById(R.id.etMessage));
		final String message = etMessage.getText().toString();
		mChatController.sendMessage(mUsername, message, SurespotConstants.MimeTypes.TEXT);

		// TODO only clear on success
		TextKeyListener.clear(etMessage.getText());
	}

	// populate the edit box
	private void handleSendIntent(String action, final String type, Bundle extras) {
		if (action.equals(Intent.ACTION_SEND)) {
			if (SurespotConstants.MimeTypes.TEXT.equals(type)) {
				String sharedText = extras.getString(Intent.EXTRA_TEXT);
				SurespotLog.v(TAG, "received action send, data: " + sharedText);
				mEditText.append(sharedText);
				requestFocus();
			}
			else if (type.startsWith(SurespotConstants.MimeTypes.IMAGE)) {

				final Uri imageUri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);

				Utils.makeToast(getActivity(), getString(R.string.uploading_image));
				Runnable runnable = new Runnable() {

					@Override
					public void run() {
						SurespotLog.v(TAG, "received image data, upload image, uri: " + imageUri);
						final FragmentActivity activity = getActivity();
						ChatUtils.uploadPictureMessageAsync(activity, imageUri, mUsername, true, null, new IAsyncCallback<Boolean>() {

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

							}
						});
					}
				};

				SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);
			}
		}
		else {
			if (action.equals(Intent.ACTION_SEND_MULTIPLE)) {
				// TODO implement
			}
		}
	}

	public void requestFocus() {
		mEditText.requestFocus();

	}

}
