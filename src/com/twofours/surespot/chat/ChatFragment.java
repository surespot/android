package com.twofours.surespot.chat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
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
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;

public class ChatFragment extends SherlockFragment {
	private ChatAdapter mChatAdapter;
	private String mUsername;
	private ListView mListView;
	private static final String TAG = "ChatFragment";
	private EditText mEditText;
	private BroadcastReceiver mSocketConnectionStatusReceiver;
	private IAsyncCallback<Boolean> mLatestMessageHandler;
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
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		SurespotLog.v(TAG, mUsername + ": onCreate");

		Utils.logIntent(getActivity().getIntent());

		mChatAdapter = new ChatAdapter(getActivity());
		setUsername(getArguments().getString("username"));
		// load messages from local storage
		loadMessages();

		mLatestMessageHandler = new IAsyncCallback<Boolean>() {

			@Override
			public void handleResponse(Boolean result) {
				// TODO Auto-generated method stub
				if (getActivity() != null) {
					// mEditText.requestFocus();
					// TODO move "last viewed" logic to ChatActivity
					String lastMessageId = getLatestMessageId();
					if (lastMessageId != null) {
						((ChatActivity) getActivity()).updateLastViewedMessageId(mUsername, Integer.parseInt(lastMessageId));
					}

					if (getView() != null) {
						getView().findViewById(R.id.progressBar).setVisibility(View.GONE);
						mListView.setEmptyView(getView().findViewById(R.id.message_list_empty));
					}
					mChatAdapter.notifyDataSetChanged();
				}
			}
		};

		SurespotLog.v(TAG, mUsername + ": onCreate, username: " + mUsername + ", messageCount: " + mChatAdapter.getCount());

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		final View view = inflater.inflate(R.layout.chat_fragment, container, false);
		mListView = (ListView) view.findViewById(R.id.message_list);
		mListView.setAdapter(mChatAdapter);
		mListView.setDividerHeight(1);
		mListView.setEmptyView(view.findViewById(R.id.progressBar));

		Button sendButton = (Button) view.findViewById(R.id.bSend);
		sendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sendTextMessage();
			}
		});
		mEditText = (EditText) view.findViewById(R.id.etMessage);
		mEditText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_SEND) {
					//
					sendTextMessage();
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

		// if the connection status changed we need to reload any messages we missed, without showing a progress dialog
		// (sshh)

		mSocketConnectionStatusReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				boolean connected = intent.getBooleanExtra(SurespotConstants.ExtraNames.CONNECTED, false);
				if (connected) {
					getLatestMessages(mLatestMessageHandler);

				}
			}
		};

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

				if (!mLoading && !mNoEarlierMessages && firstVisibleItem <= 10) {
					// SurespotLog.v(TAG, mUsername + ": onScroll: Loading more messages.");
					// SurespotLog.v(TAG, mUsername + ": onScroll, totalItemCount: " + totalItemCount + ", firstVisibleItem: " +
					// firstVisibleItem
					// + ", visibleItemCount: " + visibleItemCount);
					mLoading = true;
					getEarlierMessages();

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

		Utils.logIntent(getActivity().getIntent());

		// reget the messages in case any were added while we were gone
		SurespotLog.v(TAG, mUsername + ": onResume, mUsername:  " + mUsername);
		SurespotLog.v(TAG, mUsername + ": message count: " + mChatAdapter.getCount());

		LocalBroadcastManager.getInstance(this.getActivity()).registerReceiver(mSocketConnectionStatusReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.SOCKET_CONNECTION_STATUS_CHANGED));

		// make sure the public key is there
		// TODO move this into network controller

		// if the socket is connected, load new messages
		// if it's not connected we'll load them when it connects
		// ChatActivity chatActivity = (ChatActivity) getActivity();

		// if (chatActivity.chatConnected()) {
		getLatestMessages(mLatestMessageHandler);
		// }

		if (isVisible()) {
			SurespotLog.v(TAG, mUsername + ": onResume " + mUsername + " visible");
			requestFocus();
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		SurespotLog.v(TAG, mUsername + ": onPause, mUsername:  " + mUsername);
		LocalBroadcastManager.getInstance(this.getActivity()).unregisterReceiver(mSocketConnectionStatusReceiver);
		saveMessages();
		// mChatAdapter.evictCache();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		SurespotLog.v(TAG, mUsername + ": onDestroy");
	}

	private void getLatestMessages(final IAsyncCallback<Boolean> callback) {

		// get the list of messages
		String lastMessageId = getLatestMessageId();

		SurespotLog.v(TAG, mUsername + ": Asking server for messages after messageId: " + lastMessageId);
		SurespotApplication.getNetworkController().getMessages(mUsername, lastMessageId, new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONArray jsonArray) {
				// on async http request, response seems to come back
				// after app is destroyed sometimes
				// (ie. on rotation on gingerbread)
				// so check for null here

				if (getActivity() != null) {
					SurespotMessage message = null;
					try {
						for (int i = 0; i < jsonArray.length(); i++) {
							JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
							message = SurespotMessage.toSurespotMessage(jsonMessage);
							mChatAdapter.addOrUpdateMessage(message, false);
						}
					}
					catch (JSONException e) {
						SurespotLog.e(TAG, mUsername + ": Error creating chat message: " + e.toString(), e);
					}

					SurespotLog.v(TAG, mUsername + ": loaded: " + jsonArray.length() + " messages from the server.");

					if (callback != null) {
						callback.handleResponse(true);
					}

				}
			}

			@Override
			public void onFailure(Throwable error, String content) {
				if (!SurespotApplication.getNetworkController().isUnauthorized()) {
					SurespotLog.w(TAG, mUsername + ": getMessages", error);

					if (callback != null) {
						callback.handleResponse(false);
					}

					// TODO only show for visible fragment
					// Utils.makeToast(ChatFragment.this.getActivity(), "Could not load messages, please try again later.");
				}
			}
		});
	}

	private void getEarlierMessages() {

		mLoading = true;
		// get the list of messages
		String firstMessageId = getEarliestMessageId();

		if (firstMessageId != null) {
			// TODO make all the ints #s
			if (Integer.parseInt(firstMessageId) > 1) {
				SurespotLog.v(TAG, mUsername + ": Asking server for messages before messageId: " + firstMessageId);
				SurespotApplication.getNetworkController().getEarlierMessages(mUsername, firstMessageId, new JsonHttpResponseHandler() {
					@Override
					public void onSuccess(JSONArray jsonArray) {
						// on async http request, response seems to come back
						// after app is destroyed sometimes
						// (ie. on rotation on gingerbread)
						// so check for null here

						if (getActivity() != null) {
							SurespotMessage message = null;
							try {
								for (int i = jsonArray.length() - 1; i >= 0; i--) {
									JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
									message = SurespotMessage.toSurespotMessage(jsonMessage);
									mChatAdapter.insertMessage(message, false);

								}
							}
							catch (JSONException e) {
								SurespotLog.e(TAG, mUsername + ": Error creating chat message: " + e.toString(), e);
							}

							SurespotLog.v(TAG, mUsername + ": loaded: " + jsonArray.length() + " messages from the server.");

							mChatAdapter.notifyDataSetChanged();

						}
					}

					@Override
					public void onFailure(Throwable error, String content) {
						if (!SurespotApplication.getNetworkController().isUnauthorized()) {
							SurespotLog.w(TAG, mUsername + ": getEarlierMessages", error);
						}
					}
				});
			}
			else {
				SurespotLog.v(TAG, mUsername + ": getEarlierMessages: no more messages.");
				ChatFragment.this.mNoEarlierMessages = true;
			}
		}
	}

	public String getLatestMessageId() {
		SurespotMessage lastMessage = mChatAdapter.getLastMessageWithId();
		String lastMessageId = null;

		if (lastMessage != null) {
			lastMessageId = lastMessage.getId();
		}
		return lastMessageId;

	}

	public String getEarliestMessageId() {
		SurespotMessage firstMessage = mChatAdapter.getFirstMessageWithId();
		String firstMessageId = null;

		if (firstMessage != null) {
			firstMessageId = firstMessage.getId();
		}
		return firstMessageId;
	}

	private void sendTextMessage() {
		final EditText etMessage = ((EditText) getView().findViewById(R.id.etMessage));
		final String message = etMessage.getText().toString();
		sendMessage(message, SurespotConstants.MimeTypes.TEXT);

		// TODO only clear on success
		TextKeyListener.clear(etMessage.getText());
	}

	private void sendMessage(final String plainText, final String mimeType) {

		if (plainText.length() > 0) {

			EncryptionController.symmetricEncrypt(mUsername, plainText, new IAsyncCallback<String[]>() {
				@Override
				public void handleResponse(String[] result) {
					if (result != null) {
						SurespotMessage chatMessage = ChatUtils.buildMessage(mUsername, mimeType, plainText, result[0], result[1]);
						mChatAdapter.addOrUpdateMessage(chatMessage, true);
						if (getActivity() != null) {
							((ChatActivity) getActivity()).sendMessage(chatMessage);
						}
					}
					else {
						// TODO handle encryption error
					}
				}
			});
		}
	}

	public void addMessage(final SurespotMessage message) {
		SurespotLog.v(TAG, mUsername + ": addMessage: " + message.getTo());
		mChatAdapter.addOrUpdateMessage(message, true);
	}

	private void saveMessages() {
		// save last 30? messages
		SurespotApplication.getStateController().saveMessages(mUsername, mChatAdapter.getMessages());
	}

	private void loadMessages() {
		// SurespotLog.v(TAG, mUsername + ": Loaded: " + messages.size() + " messages from local storage.");
		mChatAdapter.addMessages(SurespotApplication.getStateController().loadMessages(mUsername));
	}

	// populate the edit box
	private void handleSendIntent(String action, final String type, Bundle extras) {
		if (action.equals(Intent.ACTION_SEND)) {
			if (SurespotConstants.MimeTypes.TEXT.equals(type)) {
				String sharedText = extras.getString(Intent.EXTRA_TEXT);
				SurespotLog.v(TAG, mUsername + ": received action send, data: " + sharedText);
				mEditText.append(sharedText);
				requestFocus();
			}
			else if (type.startsWith(SurespotConstants.MimeTypes.IMAGE)) {

				final Uri imageUri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);

				Utils.makeToast(getActivity(), getString(R.string.uploading_image));
				Runnable runnable = new Runnable() {

					@Override
					public void run() {
						SurespotLog.v(TAG, mUsername + ": received image data, upload image");
						ChatUtils.uploadPictureMessageAsync(getActivity(), imageUri, mUsername, true, null, new IAsyncCallback<Boolean>() {

							@Override
							public void handleResponse(final Boolean result) {
								SurespotLog.v(TAG, mUsername + ": upload picture response: " + result);

								if (!SurespotApplication.getNetworkController().isUnauthorized()) {
									ChatFragment.this.getActivity().runOnUiThread(new Runnable() {

										@Override
										public void run() {
											Utils.makeToast(ChatFragment.this.getActivity(),
													getString(result ? R.string.image_successfully_uploaded
															: R.string.could_not_upload_image));

										}
									});
								}
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
