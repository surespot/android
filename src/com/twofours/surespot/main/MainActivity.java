package com.twofours.surespot.main;

import java.util.ArrayList;

import org.apache.http.client.HttpResponseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
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

import com.actionbarsherlock.app.SherlockActivity;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.GCMIntentService;
import com.twofours.surespot.GCMIntentService.GCMBinder;
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
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.network.NetworkController;

public class MainActivity extends SherlockActivity {
	private MainAdapter mMainAdapter;
	private static final String TAG = "MainActivity";
	private boolean mDisconnectSocket = true;
	private MultiProgressDialog mMpdPopulateList;
	private MultiProgressDialog mMpdInviteFriend;
	private BroadcastReceiver mInvitationReceiver;
	private BroadcastReceiver mFriendAddedReceiver;
	private BroadcastReceiver mMessageReceiver;
	private GCMIntentService mGCMService;
	private boolean mBound = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(TAG, "onCreateView");
		setContentView(R.layout.activity_main);
		mMpdPopulateList = new MultiProgressDialog(this, "loading", 750);
		mMpdInviteFriend = new MultiProgressDialog(this, "inviting friend", 750);

		getSupportActionBar().setTitle("surespot " + EncryptionController.getIdentityUsername());

		final ListView listView = (ListView) findViewById(R.id.main_list);
		mMainAdapter = new MainAdapter(this);
		listView.setAdapter(mMainAdapter);

		// click on friend to join chat
		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// start chat activity

				// don't disconnect the socket io
				mDisconnectSocket = false;

				Intent intent = new Intent(MainActivity.this, ChatActivity.class);

				intent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, ((Friend) mMainAdapter.getItem(position)).getName());
				MainActivity.this.startActivity(intent);
				LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);

			}
		});

		Button addFriendButton = (Button) findViewById(R.id.bAddFriend);
		addFriendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inviteFriend();
			}
		});

		mFriendAddedReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				mMainAdapter.addNewFriend(intent.getStringExtra(SurespotConstants.ExtraNames.FRIEND_ADDED));
			}
		};
		// register for friend aded
		LocalBroadcastManager.getInstance(this).registerReceiver(mFriendAddedReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.FRIEND_ADDED_EVENT));

		// register for invites
		mInvitationReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				mMainAdapter.addFriendInvite(intent.getStringExtra(SurespotConstants.ExtraNames.INVITATION));
			}
		};

		LocalBroadcastManager.getInstance(this).registerReceiver(mInvitationReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.INVITATION_INTENT));

		mMessageReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				String message = intent.getExtras().getString(SurespotConstants.ExtraNames.MESSAGE);

				JSONObject messageJson;
				try {
					messageJson = new JSONObject(message);
					String name = Utils.getOtherUser(messageJson.getString("from"), messageJson.getString("to"));
					mMainAdapter.messageReceived(name);
				}
				catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};

		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.MESSAGE_RECEIVED_EVENT));

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

		Intent intent = new Intent(MainActivity.this, GCMIntentService.class);
		bindService(intent, mConnection, Context.BIND_ALLOW_OOM_MANAGEMENT);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mConnection);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mFriendAddedReceiver);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mInvitationReceiver);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
	}

	/** Defines callbacks for service binding, passed to bindService() */
	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {

			Log.v(TAG, "GCMService connected.");
			// We've bound to LocalService, cast the IBinder and get LocalService instance
			GCMBinder binder = (GCMBinder) service;
			mGCMService = binder.getService();

		
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			Log.v(TAG, "GCMService disconnected.");
	

		}
	};

	@Override
	protected void onPause() {
		super.onPause();
		Log.v(TAG, "onPause");

		if (mGCMService != null) {
			mGCMService.setStoreMessages(true);

		}
		ChatController.disconnect();

	}

	@Override
	public void onResume() {
		super.onResume();
		Log.v(TAG, "onResume");

		ChatController.connect(new IConnectCallback() {

			@Override
			public void connectStatus(boolean status) {
				if (!status) {
					Log.e(TAG, "Could not connect to chat server.");
				}
			}
		});

		this.mMpdPopulateList.incrProgress();
		// TODO combine into 1 web service call
		// get the list of notifications
		NetworkController.getNotifications(new JsonHttpResponseHandler() {

			public void onSuccess(JSONArray jsonArray) {

				for (int i = 0; i < jsonArray.length(); i++) {
					try {
						JSONObject json = jsonArray.getJSONObject(i);
						mMainAdapter.addFriendInvite(json.getString("data"));
					}
					catch (JSONException e) {
						Log.e(TAG, e.toString());
					}
				}
			}

			@Override
			public void onFailure(Throwable arg0, String content) {
				Log.e(TAG, "getNotifications: " + content);
				// Toast.makeText(FriendFragment.this.getActivity(), "Error getting notifications.");

			}

			@Override
			public void onFinish() {
				MainActivity.this.mMpdPopulateList.decrProgress();
			}
		});

		this.mMpdPopulateList.incrProgress();
		// get the list of friends
		NetworkController.getFriends(new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONArray jsonArray) {
				Log.v(TAG, "getFriends success.");

				if (jsonArray.length() > 0) {
					ArrayList<String> friends = null;
					try {
						friends = new ArrayList<String>(jsonArray.length());
						for (int i = 0; i < jsonArray.length(); i++) {
							friends.add(jsonArray.getString(i));
						}
					}
					catch (JSONException e) {
						Log.e(TAG, e.toString());
					}

					mMainAdapter.refreshActiveChats();
					mMainAdapter.clearFriends(false);
					mMainAdapter.addFriends(friends);

					Log.v(TAG, "mBound: " + mBound + ", mGCMService exists: " + (mGCMService != null));
					if (mGCMService != null) {
						ArrayList<JSONObject> storedMessages = mGCMService.getStoredMessages();
						// iterate throught the stored messages and flag friends with new message
						// TODO display count?
						Log.v(TAG,"there were: " + storedMessages.size() + " stored messages while you were gone, mr main activity sir.");
						for (JSONObject jsonMessage : storedMessages) {
							try {
								String username = jsonMessage.getString("otheruser");
								mMainAdapter.messageReceived(username);
							}
							catch (JSONException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						mGCMService.setStoreMessages(false);
					}
					

				}
			}

			@Override
			public void onFailure(Throwable arg0, String content) {
				Log.e(TAG, "getFriends: " + content);
				// Toast.makeText(FriendFragment.this.getActivity(), "Error getting friends.");
			}

			@Override
			public void onFinish() {
				((ListView) findViewById(R.id.main_list)).setEmptyView(findViewById(R.id.main_list_empty));
				MainActivity.this.mMpdPopulateList.decrProgress();
			}
		});
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
					}
					else {
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
