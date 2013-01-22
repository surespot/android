package com.twofours.surespot.friends;

import java.util.HashMap;

import org.apache.http.client.HttpResponseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.InputFilter;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.LetterOrDigitInputFilter;
import com.twofours.surespot.MultiProgressDialog;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.Utils;
import com.twofours.surespot.chat.ChatActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.IConnectCallback;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.NetworkController;

public class FriendActivity extends SherlockActivity {
	private FriendAdapter mMainAdapter;
	private static final String TAG = "FriendActivity";
	private boolean mDisconnectSocket = true;
	private MultiProgressDialog mMpdPopulateList;
	private MultiProgressDialog mMpdInviteFriend;
	private BroadcastReceiver mInvitationReceiver;
	private BroadcastReceiver InviteResponseReceiver;
	private BroadcastReceiver mMessageReceiver;
	private HashMap<String, Integer> mLastViewedMessageIds;
	private ChatController mChatController;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(TAG, "onCreateView");

		Intent intent = getIntent();
		String action = intent.getAction();
		String type = intent.getType();
	
		if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
			//set the title
			getSupportActionBar().setTitle("share with");
		}
		else {
			getSupportActionBar().setTitle("surespot " + EncryptionController.getIdentityUsername());
		}

		mChatController = new ChatController(new IConnectCallback() {

			@Override
			public void connectStatus(boolean status) {
				if (!status) {
					Log.e(TAG, "Could not connect to chat server.");
				}
			}
		});
		setContentView(R.layout.activity_friend);
		mLastViewedMessageIds = new HashMap<String, Integer>();
		mMpdPopulateList = new MultiProgressDialog(this, "loading", 750);
		mMpdInviteFriend = new MultiProgressDialog(this, "inviting friend", 750);

		

		final ListView listView = (ListView) findViewById(R.id.main_list);
		mMainAdapter = new FriendAdapter(this);
		listView.setAdapter(mMainAdapter);

		// click on friend to join chat
		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Friend friend = (Friend) mMainAdapter.getItem(position);
				if (friend.isFriend()) {
					// start chat activity
					// don't disconnect the socket io

					// mDisconnectSocket = false;

					Intent newIntent = new Intent(FriendActivity.this, ChatActivity.class);
					newIntent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, friend.getName());
					//if we have a send intent, when we pick a user, propogate it
					// Get intent, action and MIME type
					Intent intent = getIntent();
					String action = intent.getAction();
					String type = intent.getType();
				
					if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
						newIntent.setAction(action);
						newIntent.setType(type);
						newIntent.putExtras(intent);
					}

					
					FriendActivity.this.startActivity(newIntent);
					//LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(newIntent);
				}
			}
		});

		Button addFriendButton = (Button) findViewById(R.id.bAddFriend);
		addFriendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inviteFriend();
			}
		});

		InviteResponseReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String jsonStringInviteResponse = intent.getStringExtra(SurespotConstants.ExtraNames.INVITE_RESPONSE);
				JSONObject jsonInviteResponse;
				try {
					jsonInviteResponse = new JSONObject(jsonStringInviteResponse);
					String name = jsonInviteResponse.getString("user");
					if (jsonInviteResponse.getString("response").equals("accept")) {
						mMainAdapter.addNewFriend(name);
					} else {
						mMainAdapter.removeFriend(name);
					}
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					Log.e(TAG, "Invite Response Handler error: " + e.getMessage());
				}

			}
		};

		// register for invites
		mInvitationReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				mMainAdapter.addFriendInviter(intent.getStringExtra(SurespotConstants.ExtraNames.NAME));
			}
		};

		mMessageReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				String message = intent.getExtras().getString(SurespotConstants.ExtraNames.MESSAGE);

				JSONObject messageJson;
				try {
					messageJson = new JSONObject(message);
					String name = Utils.getOtherUser(messageJson.getString("from"), messageJson.getString("to"));
					mMainAdapter.messageReceived(name);
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};

		EditText editText = (EditText) findViewById(R.id.etFriend);
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
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.v(TAG, "onResume");

		LocalBroadcastManager.getInstance(this).registerReceiver(InviteResponseReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.INVITE_RESPONSE));
		LocalBroadcastManager.getInstance(this).registerReceiver(mInvitationReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.INVITE_REQUEST));
		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.MESSAGE_RECEIVED));

		mChatController.connect();

		this.mMpdPopulateList.incrProgress();

		// get last message id's out of shared prefs
		String lastMessageIdJson = Utils.getSharedPrefsString(SurespotConstants.PrefNames.PREFS_LAST_VIEWED_MESSAGE_IDS);
		if (lastMessageIdJson != null) {
			try {
				mLastViewedMessageIds = Utils.jsonStringToMap(lastMessageIdJson);
				Log.v(TAG,"Loaded last viewed message ids: " + mLastViewedMessageIds);
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		// get the list of friends
		NetworkController.getFriends(new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONArray jsonArray) {
				Log.v(TAG, "getFriends success.");

				if (jsonArray.length() > 0) {

					mMainAdapter.refreshActiveChats();
					mMainAdapter.clearFriends(false);
					mMainAdapter.addFriends(jsonArray);

					// compute new message deltas
					NetworkController.getLastMessageIds(new JsonHttpResponseHandler() {

						public void onSuccess(int arg0, JSONObject arg1) {
							Log.v(TAG, "getLastmessageids success status jsonobject");

							HashMap<String, Integer> serverMessageIds = Utils.jsonToMap(arg1);

							// if we have counts
							if (mLastViewedMessageIds != null) {

								// set the deltas
								for (String user : serverMessageIds.keySet()) {

									// figure out new message counts
									int serverId = serverMessageIds.get(user);
									Integer localId = mLastViewedMessageIds.get(user);
									Log.v(TAG, "last localId for " + user + ": " + localId);
									Log.v(TAG, "last serverId for " + user + ": " + serverId);

									// new chat, all messages are new
									if (localId == null) {
										mLastViewedMessageIds.put(user, serverId);
										mMainAdapter.messageDeltaReceived(user, serverId);
									} else {

										// compute delta
										int messageDelta = serverId - localId;
										if (messageDelta > 0) {
											mMainAdapter.messageDeltaReceived(user, messageDelta);
										}
									}

								}
							}

							// if this is first time through store the last message ids
							else {
								mLastViewedMessageIds = serverMessageIds;

							}
						};

						public void onFailure(Throwable arg0, String arg1) {
							Log.e(TAG, "getLastMessageIds: " + arg0.toString());
						};
					});
				}
			}

			@Override
			public void onFailure(Throwable arg0, String content) {
				Log.e(TAG, "getFriends: " + content);

				Toast.makeText(FriendActivity.this.getApplicationContext(),
						"Could not load friends. Please check your network connection.", Toast.LENGTH_SHORT).show();
			}

			@Override
			public void onFinish() {
				((ListView) findViewById(R.id.main_list)).setEmptyView(findViewById(R.id.main_list_empty));
				FriendActivity.this.mMpdPopulateList.decrProgress();
			}
		});
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.v(TAG, "onPause");

		LocalBroadcastManager.getInstance(this).unregisterReceiver(InviteResponseReceiver);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mInvitationReceiver);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);

		Utils.putSharedPrefsString(SurespotConstants.PrefNames.LAST_CHAT, null);
		mChatController.disconnect();
		mChatController.destroy();

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

	}

	private void inviteFriend() {
		final EditText etFriend = ((EditText) findViewById(R.id.etFriend));
		final String friend = etFriend.getText().toString();

		if (friend.length() > 0) {
			if (friend.equals(EncryptionController.getIdentityUsername())) {
				Utils.makeToast("You can't be friends with yourself, bro.");
				return;
			}

			mMpdInviteFriend.incrProgress();
			NetworkController.invite(friend, new AsyncHttpResponseHandler() {
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
					Utils.makeToast(friend + " has been invited to be your friend.");
				}

				@Override
				public void onFailure(Throwable arg0, String content) {
					if (arg0 instanceof HttpResponseException) {
						HttpResponseException error = (HttpResponseException) arg0;
						int statusCode = error.getStatusCode();
						switch (statusCode) {
						case 404:
							Utils.makeToast("User does not exist.");
							break;
						case 409:
							Utils.makeToast("You are already friends.");
							break;
						case 403:
							Utils.makeToast("You have already invited this user.");
							break;
						default:
							Log.e(TAG, "inviteFriend: " + content);
							// Toast.makeText(FriendFragment.this.getActivity(), "Error inviting friend.");
						}
					} else {
						Log.e(TAG, "inviteFriend: " + content);
						// Toast.makeText(FriendFragment.this.getActivity(), "Error inviting friend.");
					}
				}

				@Override
				public void onFinish() {
					mMpdInviteFriend.decrProgress();
				}
			});
		}
	}

}
