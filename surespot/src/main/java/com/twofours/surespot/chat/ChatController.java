package com.twofours.surespot.chat;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import ch.boye.httpclientandroidlib.Header;
import ch.boye.httpclientandroidlib.HttpStatus;
import ch.boye.httpclientandroidlib.HttpVersion;
import ch.boye.httpclientandroidlib.StatusLine;
import ch.boye.httpclientandroidlib.client.HttpResponseException;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheEntry;
import ch.boye.httpclientandroidlib.cookie.Cookie;
import ch.boye.httpclientandroidlib.impl.client.cache.HeapResource;
import ch.boye.httpclientandroidlib.impl.cookie.DateUtils;
import ch.boye.httpclientandroidlib.message.BasicHeader;
import ch.boye.httpclientandroidlib.message.BasicStatusLine;

import com.actionbarsherlock.view.MenuItem;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.StateController;
import com.twofours.surespot.StateController.FriendState;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.friends.AutoInviteData;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.friends.FriendAdapter;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.MessageImageDownloader;
import com.twofours.surespot.network.CookieResponseHandler;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.IAsyncCallbackTuple;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.network.NetworkHelper;
import com.viewpagerindicator.TitlePageIndicator;

public class ChatController {

	private static final String TAG = "ChatController";
	private static final int STATE_CONNECTING = 0;
	private static final int STATE_CONNECTED = 1;
	private static final int STATE_DISCONNECTED = 2;

	private static final int MAX_RETRIES = 60;
	// maximum time before reconnecting in seconds
	private static final int MAX_RETRY_DELAY = 30;

	private final StatusLine mImageStatusLine = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "");
	private SocketIO socket;
	private int mRetries = 0;
	private Timer mBackgroundTimer;
	private Object BACKGROUND_TIMER_LOCK = new Object();

	private IOCallback mSocketCallback;

	private ConcurrentLinkedQueue<SurespotMessage> mSendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();
	private ConcurrentLinkedQueue<SurespotMessage> mResendBuffer = new ConcurrentLinkedQueue<SurespotMessage>();

	private int mConnectionState;
	private boolean mOnWifi;
	private NotificationManager mNotificationManager;
	private BroadcastReceiver mConnectivityReceiver;
	private HashMap<String, ChatAdapter> mChatAdapters;
	private HashMap<String, Integer> mEarliestMessage;

	private FriendAdapter mFriendAdapter;
	private ChatPagerAdapter mChatPagerAdapter;
	private ViewPager mViewPager;
	private TitlePageIndicator mIndicator;
	private FragmentManager mFragmentManager;
	private int mLatestUserControlId;
	private ArrayList<MenuItem> mMenuItems;
	private HashMap<String, LatestIdPair> mPreConnectIds;

	private static String mCurrentChat;
	private static boolean mPaused = true;
	private NetworkController mNetworkController;

	private Context mContext;
	public static final int MODE_NORMAL = 0;
	public static final int MODE_SELECT = 1;

	private int mMode = MODE_NORMAL;

	private IAsyncCallbackTuple<String, Boolean> mCallback401;
	private IAsyncCallback<Boolean> mProgressCallback;
	private IAsyncCallback<Void> mSendIntentCallback;
	private IAsyncCallback<Friend> mTabShowingCallback;
	private AutoInviteData mAutoInviteData;
	private boolean mHandlingAutoInvite;
	private String mUsername;

	public ChatController(Context context, String username, NetworkController networkController, FragmentManager fm,
			IAsyncCallbackTuple<String, Boolean> m401Handler, IAsyncCallback<Boolean> progressCallback, IAsyncCallback<Void> sendIntentCallback,
			IAsyncCallback<Friend> tabShowingCallback) {
		SurespotLog.d(TAG, "constructor: %s", username);
		mContext = context;
		mUsername = username;
		mNetworkController = networkController;

		mCallback401 = m401Handler;
		mProgressCallback = progressCallback;
		mSendIntentCallback = sendIntentCallback;

		mTabShowingCallback = tabShowingCallback;
		mEarliestMessage = new HashMap<String, Integer>();
		mChatAdapters = new HashMap<String, ChatAdapter>();
		mFriendAdapter = new FriendAdapter(mContext);
		mPreConnectIds = new HashMap<String, ChatController.LatestIdPair>();
		loadState(mUsername);

		mFragmentManager = fm;
		mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		setOnWifi();

		// mViewPager.setOffscreenPageLimit(2);

		mSocketCallback = new IOCallback() {

			@Override
			public void onMessage(JSONObject json, IOAcknowledge ack) {
				try {
					SurespotLog.d(TAG, "JSON Server said: %s", json.toString(2));

				}
				catch (JSONException e) {
					SurespotLog.w(TAG, "onMessage", e);
				}
			}

			@Override
			public void onMessage(String data, IOAcknowledge ack) {
				SurespotLog.d(TAG, "Server said: %s", data);
			}

			@Override
			public synchronized void onError(SocketIOException socketIOException) {
				boolean reAuthing = false;
				// socket.io returns 403 for can't login
				if (socketIOException.getHttpStatus() == 403) {
					SurespotLog.d(TAG, "got 403 from websocket");

					reAuthing = NetworkHelper.reLogin(mContext, mNetworkController, mUsername, new CookieResponseHandler() {

						@Override
						public void onSuccess(int responseCode, String result, Cookie cookie) {
							connect();
						}

						@Override
						public void onFailure(Throwable arg0, String content) {
							// if we got http error bail
							// if (arg0 instanceof HttpResponseException) {
							// HttpResponseException error = (HttpResponseException) arg0;
							// int statusCode = error.getStatusCode();
							// SurespotLog.i(TAG, error, "http error on relogin - bailing, status: %d, message: %s", statusCode, error.getMessage());

							socket = null;
							logout();
							mCallback401.handleResponse(null, false);
							return;
							// }
							//
							// // if it's not an http error try again
							// SurespotLog.i(TAG, arg0, "non http error on relogin - reconnecting, message: %s", arg0.getMessage());
							// connect();
						}
					});

					if (!reAuthing) {

						socket = null;
						logout();
						mCallback401.handleResponse(null, false);
						return;
					}
				}

				if (reAuthing)
					return;

				SurespotLog.i(TAG, socketIOException, "an Error occured, attempting reconnect with exponential backoff, retries: %d", mRetries);

				setOnWifi();
				// kick off another task
				if (mRetries < MAX_RETRIES) {

					int timerInterval = generateInterval(mRetries++);
					SurespotLog.d(TAG, "try %d starting another task in: %d", mRetries - 1, timerInterval);

					synchronized (BACKGROUND_TIMER_LOCK) {
						if (mReconnectTask != null) {
							mReconnectTask.cancel();
						}

						if (!mPaused) {
							ReconnectTask reconnectTask = new ReconnectTask();
							if (mBackgroundTimer == null) {
								mBackgroundTimer = new Timer("backgroundTimer");
							}
							mBackgroundTimer.schedule(reconnectTask, timerInterval);
							mReconnectTask = reconnectTask;
						}
					}
				}
				else {
					SurespotLog.i(TAG, "Socket.io reconnect retries exhausted, giving up.");
					mCallback401.handleResponse(mContext.getString(R.string.could_not_connect_to_server), true);
				}
			}

			@Override
			public void onDisconnect() {
				SurespotLog.d(TAG, "Connection terminated.");
				// socket = null;
			}

			@Override
			public void onConnect() {
				SurespotLog.d(TAG, "socket.io connection established");

				setOnWifi();
				mRetries = 0;

				synchronized (BACKGROUND_TIMER_LOCK) {

					if (mBackgroundTimer != null) {
						mBackgroundTimer.cancel();
						mBackgroundTimer = null;
					}

					if (mReconnectTask != null && mReconnectTask.cancel()) {
						SurespotLog.d(TAG, "Cancelled reconnect timer.");
						mReconnectTask = null;
					}
				}
				connected();
				setState(STATE_CONNECTED);
			}

			@Override
			public void on(String event, IOAcknowledge ack, Object... args) {

				SurespotLog.d(TAG, "Server triggered event '" + event + "'");
				if (event.equals("control")) {
					try {
						SurespotControlMessage message = SurespotControlMessage.toSurespotControlMessage(new JSONObject((String) args[0]));
						handleControlMessage(null, message, true, false);
					}
					catch (JSONException e) {
						SurespotLog.w(TAG, "on control", e);
					}
				}
				else
					if (event.equals("message")) {
						try {
							JSONObject jsonMessage = new JSONObject((String) args[0]);
							SurespotLog.d(TAG, "received message: " + jsonMessage.toString());
							SurespotMessage message = SurespotMessage.toSurespotMessage(jsonMessage);
							handleMessage(message);
							checkAndSendNextMessage(message);

							// see if we have deletes
							String sDeleteControlMessages = jsonMessage.optString("deleteControlMessages", null);
							if (sDeleteControlMessages != null) {
								JSONArray deleteControlMessages = new JSONArray(sDeleteControlMessages);

								if (deleteControlMessages.length() > 0) {
									for (int i = 0; i < deleteControlMessages.length(); i++) {
										try {
											SurespotControlMessage dMessage = SurespotControlMessage.toSurespotControlMessage(new JSONObject(
													deleteControlMessages.getString(i)));
											handleControlMessage(null, dMessage, true, false);
										}
										catch (JSONException e) {
											SurespotLog.w(TAG, "on control", e);
										}
									}
								}

							}

						}
						catch (JSONException e) {
							SurespotLog.w(TAG, "on message", e);
						}

					}
					else
						if (event.equals("messageError")) {
							try {
								JSONObject jsonMessage = (JSONObject) args[0];
								SurespotLog.d(TAG, "received messageError: " + jsonMessage.toString());
								SurespotErrorMessage errorMessage = SurespotErrorMessage.toSurespotErrorMessage(jsonMessage);
								handleErrorMessage(errorMessage);

							}
							catch (JSONException e) {
								SurespotLog.w(TAG, "on messageError", e);
							}
						}
			}

		};

		mConnectivityReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				SurespotLog.d(TAG, "Connectivity Action");
				ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
				if (networkInfo != null) {
					SurespotLog.d(TAG, "isconnected: " + networkInfo.isConnected());
					SurespotLog.d(TAG, "failover: " + networkInfo.isFailover());
					SurespotLog.d(TAG, "reason: " + networkInfo.getReason());
					SurespotLog.d(TAG, "type: " + networkInfo.getTypeName());

					// if it's not a failover and wifi is now active then initiate reconnect
					if (!networkInfo.isFailover() && (networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected())) {
						synchronized (ChatController.this) {
							// if we're not connecting, connect
							if (getState() != STATE_CONNECTING && !mOnWifi) {

								SurespotLog.d(TAG, "Network switch, Reconnecting...");

								setState(STATE_CONNECTING);

								mOnWifi = true;
								disconnect();
								connect();
							}
						}
					}
				}
				else {
					SurespotLog.d(TAG, "networkinfo null");
				}
			}
		};

	}

	private int generateInterval(int k) {
		int timerInterval = (int) (Math.pow(2, k) * 1000);
		if (timerInterval > MAX_RETRY_DELAY * 1000) {
			timerInterval = MAX_RETRY_DELAY * 1000;
		}

		int reconnectTime = (int) (Math.random() * timerInterval);
		SurespotLog.d(TAG, "generated reconnect time: %d for k: %d", reconnectTime, k);
		return reconnectTime;
	}

	// this has to be done outside of the contructor as it creates fragments, which need chat controller instance
	public void init(ViewPager viewPager, TitlePageIndicator pageIndicator, ArrayList<MenuItem> menuItems) {
		mChatPagerAdapter = new ChatPagerAdapter(mContext, mFragmentManager);
		mMenuItems = menuItems;

		mViewPager = viewPager;
		mViewPager.setAdapter(mChatPagerAdapter);
		mIndicator = pageIndicator;
		mIndicator.setViewPager(mViewPager);

		mIndicator.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				if (mChatPagerAdapter != null) {
					SurespotLog.d(TAG, "onPageSelected, position: " + position);
					String name = mChatPagerAdapter.getChatName(position);
					setCurrentChat(name);
				}

			}
		});

		mChatPagerAdapter.setChatFriends(mFriendAdapter.getActiveChatFriends());
		mFriendAdapter.registerFriendAliasChangedCallback(new IAsyncCallback<Void>() {

			@Override
			public void handleResponse(Void result) {
				mChatPagerAdapter.sort();
				mChatPagerAdapter.notifyDataSetChanged();
				mIndicator.notifyDataSetChanged();
			}
		});

		onResume();
	}

	public void setAutoInviteData(AutoInviteData autoInviteData) {
		mAutoInviteData = autoInviteData;
		if (getState() == STATE_CONNECTED) {
			handleAutoInvite();
		}
	}

	private void connect() {
		SurespotLog.d(TAG, "connect, socket: " + socket + ", connected: " + (socket != null ? socket.isConnected() : false) + ", state: " + mConnectionState);

		// copy the latest ids so that we don't miss any if we receive new messages during the time we request messages and when the
		// connection completes (if they
		// are received out of order for some reason)
		//
		mPreConnectIds.clear();
		for (Entry<String, ChatAdapter> entry : mChatAdapters.entrySet()) {
			String username = entry.getKey();
			LatestIdPair idPair = new LatestIdPair();
			idPair.latestMessageId = getLatestMessageId(username);
			idPair.latestControlMessageId = getLatestMessageControlId(username);
			SurespotLog.d(TAG, "setting preconnectids for: " + username + ", latest message id:  " + idPair.latestMessageId + ", latestcontrolid: "
					+ idPair.latestControlMessageId);
			mPreConnectIds.put(username, idPair);

		}

		Cookie cookie = IdentityController.getCookieForUser(mUsername);

		try {
			HashMap<String, String> headers = new HashMap<String, String>();
			if (cookie != null) {
				headers.put("cookie", cookie.getName() + "=" + cookie.getValue());
			}
			socket = new SocketIO(SurespotConfiguration.getBaseUrl(), headers);
			socket.connect(mSocketCallback);
		}
		catch (Exception e) {

			SurespotLog.w(TAG, "connect", e);
		}

	}

	private void disconnect() {
		SurespotLog.d(TAG, "disconnect.");
		setState(STATE_DISCONNECTED);

		if (socket != null) {
			socket.disconnect();
			socket = null;
		}

	}

	private void connected() {
		getFriendsAndData();
		resendMessages();
	}

	private void handleAutoInvite() {

		// if we need to invite someone then do it
		if (mAutoInviteData != null && !mHandlingAutoInvite) {
			if (mFriendAdapter.getFriend(mAutoInviteData.getUsername()) == null) {
				SurespotLog.d(TAG, "auto inviting user: %s", mAutoInviteData.getUsername());
				mNetworkController.invite(mAutoInviteData.getUsername(), mAutoInviteData.getSource(), new AsyncHttpResponseHandler() {
					@Override
					public void onSuccess(int statusCode, String arg0) {
						getFriendAdapter().addFriendInvited(mAutoInviteData.getUsername());
						// scroll to home page
						setCurrentChat(null);
						mAutoInviteData = null;

					}
				});
			}
			else {
				Utils.makeToast(mContext, mContext.getString(R.string.autoinvite_user_exists, mAutoInviteData.getUsername()));
				mAutoInviteData = null;
			}
		}
	}

	private void resendMessages() {
		// get the resend messages
		SurespotMessage[] resendMessages = getResendMessages();
		JSONArray sMessageList = new JSONArray();

		for (int i = 0; i < resendMessages.length; i++) {
			SurespotMessage message = resendMessages[i];

			// if it has an id don't send it again
			if (message.getId() != null) {
				mResendBuffer.remove(message);
				continue;
			}

			// set the last received id so the server knows which messages to check
			String otherUser = message.getOtherUser();

			// String username = message.getFrom();
			Integer lastMessageID = 0;
			// ideally get the last id from the fragment's chat adapter
			ChatAdapter chatAdapter = mChatAdapters.get(otherUser);
			if (chatAdapter != null) {
				SurespotMessage lastMessage = chatAdapter.getLastMessageWithId();
				if (lastMessage != null) {
					lastMessageID = lastMessage.getId();
				}
			}

			// failing that use the last viewed id
			if (lastMessageID == null) {
				mFriendAdapter.getFriend(otherUser).getLastViewedMessageId();
			}

			SurespotLog.d(TAG, "setting resendId, otheruser: " + otherUser + ", id: " + lastMessageID);
			message.setResendId(lastMessageID);

			sMessageList.put(message.toJSONObjectSocket());
		}

		socket.send(sMessageList.toString());
	}

	private void setOnWifi() {
		// get the initial state...sometimes when the app starts it says "hey i'm on wifi" which creates a reconnect
		ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
		if (networkInfo != null) {
			mOnWifi = (networkInfo.getType() == ConnectivityManager.TYPE_WIFI);
		}

	}

	private void checkAndSendNextMessage(SurespotMessage message) {
		sendMessages();

		if (mResendBuffer.size() > 0) {
			if (mResendBuffer.remove(message)) {
				SurespotLog.d(TAG, "Received and removed message from resend  buffer: " + message);
			}
		}
	}

	private SurespotMessage[] getResendMessages() {
		SurespotMessage[] messages = mResendBuffer.toArray(new SurespotMessage[0]);
		// mResendBuffer.clear();
		return messages;

	}

	private void enqueueMessage(SurespotMessage message) {
		mSendBuffer.add(message);
	}

	private synchronized void sendMessages() {
		synchronized (BACKGROUND_TIMER_LOCK) {
			if (mBackgroundTimer == null) {
				mBackgroundTimer = new Timer("backgroundTimer");
			}
		}

		SurespotLog.d(TAG, "Sending: " + mSendBuffer.size() + " messages.");

		Iterator<SurespotMessage> iterator = mSendBuffer.iterator();
		while (iterator.hasNext()) {
			SurespotMessage message = iterator.next();
			if (isMessageReadyToSend(message)) {
				iterator.remove();
				sendMessage(message);
			}
		}
	}

	private boolean isMessageReadyToSend(SurespotMessage message) {
		return !TextUtils.isEmpty(message.getData()) && !TextUtils.isEmpty(message.getFromVersion()) && !TextUtils.isEmpty(message.getToVersion());
	}

	private void sendMessage(final SurespotMessage message) {
		SurespotLog.d(TAG, "sendmessage adding message to ResendBuffer, text: %s, iv: %s", message.getPlainData(), message.getIv());

		mResendBuffer.add(message);
		if (getState() == STATE_CONNECTED) {
			SurespotLog.d(TAG, "sendmessage, socket: %s", socket);
			JSONObject json = message.toJSONObjectSocket();
			SurespotLog.d(TAG, "sendmessage, json: %s", json);
			String s = json.toString();
			SurespotLog.d(TAG, "sendmessage, message string: %s", s);

			if (socket != null) {
				socket.send(s);
			}
		}
	}

	private int getState() {
		return mConnectionState;
	}

	private synchronized void setState(int state) {
		mConnectionState = state;
	}

	private ReconnectTask mReconnectTask;

	private class ReconnectTask extends TimerTask {

		@Override
		public void run() {
			SurespotLog.d(TAG, "Reconnect task run.");
			connect();
		}
	}

	private void handleMessage(final SurespotMessage message) {
		SurespotLog.d(TAG, "handleMessage %s", message);
		final String otherUser = message.getOtherUser();

		final ChatAdapter chatAdapter = mChatAdapters.get(otherUser);

		// if the adapter is open add the message
		if (chatAdapter != null) {

			// decrypt the message before adding it so the size is set properly
			new AsyncTask<Void, Void, Void>() {

				@Override
				protected Void doInBackground(Void... params) {
					if (message.getMimeType().equals(SurespotConstants.MimeTypes.TEXT)) {

						// decrypt it before adding
						final String plainText = EncryptionController.symmetricDecrypt(message.getOurVersion(), message.getOtherUser(),
								message.getTheirVersion(), message.getIv(), message.isHashed(), message.getData());

						// substitute emoji
						if (plainText != null) {
							EmojiParser parser = EmojiParser.getInstance();
							message.setPlainData(parser.addEmojiSpans(plainText));
						}
						else {
							// error decrypting
							SurespotLog.d(TAG, "could not decrypt message");
							message.setPlainData(mContext.getString(R.string.message_error_decrypting_message));
						}
					}

					else {
						if (message.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {

							// if it's an image that i sent
							// get the local message
							if (ChatUtils.isMyMessage(message)) {
								handleCachedFile(chatAdapter, message);
							}
							else {

								InputStream imageStream = MainActivity.getNetworkController().getFileStream(MainActivity.getContext(), message.getData());

								Bitmap bitmap = null;
								PipedOutputStream out = new PipedOutputStream();
								PipedInputStream inputStream;
								try {
									inputStream = new PipedInputStream(out);

									EncryptionController.runDecryptTask(message.getOurVersion(), message.getOtherUser(), message.getTheirVersion(),
											message.getIv(), message.isHashed(), new BufferedInputStream(imageStream), out);

									byte[] bytes = Utils.inputStreamToBytes(inputStream);

									bitmap = ChatUtils.getSampledImage(bytes);
								}
								catch (InterruptedIOException ioe) {

									SurespotLog.w(TAG, ioe, "handleMessage");
								}
								catch (IOException e) {
									SurespotLog.w(TAG, e, "handleMessage");
								}

								if (bitmap != null) {
									MessageImageDownloader.addBitmapToCache(message.getData(), bitmap);
								}
							}
						}
						else {
							if (message.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
								if (ChatUtils.isMyMessage(message)) {
									handleCachedFile(chatAdapter, message);
								}
								else {

									InputStream encryptedVoiceStream = MainActivity.getNetworkController().getFileStream(MainActivity.getContext(),
											message.getData());

									PipedOutputStream out = new PipedOutputStream();
									PipedInputStream inputStream = null;
									try {
										inputStream = new PipedInputStream(out);

										EncryptionController.runDecryptTask(message.getOurVersion(), message.getOtherUser(), message.getTheirVersion(),
												message.getIv(), message.isHashed(), new BufferedInputStream(encryptedVoiceStream), out);

										byte[] bytes = Utils.inputStreamToBytes(inputStream);
										message.setPlainBinaryData(bytes);
									}
									catch (InterruptedIOException ioe) {

										SurespotLog.w(TAG, ioe, "handleMessage");

									}
									catch (IOException e) {
										SurespotLog.w(TAG, e, "handleMessage");
									}
									finally {

										try {
											if (inputStream != null) {
												inputStream.close();
											}
										}
										catch (IOException e) {
											SurespotLog.w(TAG, e, "handleMessage");
										}

										try {
											if (encryptedVoiceStream != null) {
												encryptedVoiceStream.close();
											}
										}
										catch (IOException e) {
											SurespotLog.w(TAG, e, "handleMessage");
										}
									}
								}
							}
							else {
								message.setPlainData("unknown message mime type");
							}
						}
					}
					return null;
				}

				protected void onPostExecute(Void result) {
					try {
						boolean added = applyControlMessages(chatAdapter, message, false, true, true);
						scrollToEnd(otherUser);

						Friend friend = mFriendAdapter.getFriend(otherUser);
						if (friend != null) {
							int messageId = message.getId();

							// always update the available id
							friend.setAvailableMessageId(messageId, false);

							// if the chat is showing set the last viewed id the id of the message we just received
							if (otherUser.equals(mCurrentChat)) {

								friend.setLastViewedMessageId(messageId);

								// if it was a voice message from the other user set play flag
								// TODO wrap in preference
								if (!ChatUtils.isMyMessage(message) && message.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
									message.setPlayMedia(true);
								}

							}
							// chat not showing
							else {
								// if it's my message increment the count by one to account for it as I may have unread messages from the
								// other user; we
								// can't just set the last viewed to the latest message
								if (ChatUtils.isMyMessage(message) && added) {
									int adjustedLastViewedId = friend.getLastViewedMessageId() + 1;
									if (adjustedLastViewedId < messageId) {
										friend.setLastViewedMessageId(adjustedLastViewedId);
									}
									else {
										friend.setLastViewedMessageId(messageId);
									}
								}
							}

							mFriendAdapter.sort();
							mFriendAdapter.notifyDataSetChanged();
						}

					}
					catch (SurespotMessageSequenceException e) {
						SurespotLog.d(TAG, "handleMessage: %s", e.getMessage());
						getLatestMessagesAndControls(otherUser, e.getMessageId(), true);
					}
				};

			}.execute();

		}
		else {
			Friend friend = mFriendAdapter.getFriend(otherUser);
			if (friend != null) {
				int messageId = message.getId();

				// always update the available id
				friend.setAvailableMessageId(messageId, false);

				mFriendAdapter.sort();
				mFriendAdapter.notifyDataSetChanged();
			}
		}

	}

	private boolean applyControlMessages(ChatAdapter chatAdapter, SurespotMessage message, boolean checkSequence, boolean sort, boolean notify)
			throws SurespotMessageSequenceException {
		// see if we have applicable control messages and apply them if necessary
		ArrayList<SurespotControlMessage> controlMessages = chatAdapter.getControlMessages();
		ArrayList<SurespotControlMessage> applicableControlMessages = new ArrayList<SurespotControlMessage>();
		for (SurespotControlMessage controlMessage : controlMessages) {
			int messageId = Integer.parseInt(controlMessage.getMoreData());
			if (message.getId() == messageId) {
				applicableControlMessages.add(controlMessage);
			}
		}
		boolean added = false;

		if (applicableControlMessages.size() == 0) {

			added = chatAdapter.addOrUpdateMessage(message, checkSequence, sort, notify);

		}
		else {
			added = chatAdapter.addOrUpdateMessage(message, checkSequence, false, false);

			for (SurespotControlMessage controlMessage : applicableControlMessages) {
				SurespotLog.d(TAG, "applying control message %s: to message %s", controlMessage, message);
				handleControlMessage(chatAdapter, controlMessage, false, true);
			}

			if (notify) {
				chatAdapter.notifyDataSetChanged();
			}
		}

		return added;
	}

	// add entry to http cache for image we sent so we don't download it again
	private void handleCachedFile(ChatAdapter chatAdapter, SurespotMessage message) {
		SurespotLog.d(TAG, "handleCachedFile");
		SurespotMessage localMessage = chatAdapter.getMessageByIv(message.getIv());

		// if the data is different we haven't updated the url to point externally
		if (localMessage != null && localMessage.getId() == null && !localMessage.getData().equals(message.getData())) {
			// add the remote cache entry for the new url

			String localUri = localMessage.getData();
			String remoteUri = message.getData();

			FileInputStream fis;
			try {
				fis = new FileInputStream(new File(new URI(localUri)));
				byte[] imageData = Utils.inputStreamToBytes(fis);

				HeapResource resource = new HeapResource(imageData);
				Date date = new Date();
				String sDate = DateUtils.formatDate(date);

				Header[] cacheHeaders = new Header[3];

				// create fake cache entry
				cacheHeaders[0] = new BasicHeader("Last-Modified", sDate);
				cacheHeaders[1] = new BasicHeader("Cache-Control", "public, max-age=31557600");
				cacheHeaders[2] = new BasicHeader("Date", sDate);

				HttpCacheEntry cacheEntry = new HttpCacheEntry(date, date, mImageStatusLine, cacheHeaders, resource);

				SurespotLog.d(TAG, "creating http cache entry for: %s", remoteUri);
				mNetworkController.addCacheEntry(remoteUri, cacheEntry);

				// update image cache
				if (message.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
					MessageImageDownloader.copyAndRemoveCacheEntry(localUri, remoteUri);
				}

			}
			catch (FileNotFoundException e1) {
				SurespotLog.w(TAG, e1, "onMessage");
			}
			catch (URISyntaxException e1) {
				SurespotLog.w(TAG, e1, "onMessage");
			}
			catch (IOException e) {
				SurespotLog.w(TAG, e, "onMessage");
			}

			// delete the file

			try {
				SurespotLog.d(TAG, "handleCachedImage deleting local file: %s", localUri);

				File file = new File(new URI(localUri));
				file.delete();
			}
			catch (URISyntaxException e) {
				SurespotLog.w(TAG, e, "handleMessage");
			}

			// update message to point to real location
			localMessage.setData(remoteUri);

		}
	}

	// message handling shiznit
	void loadEarlierMessages(final String username, final IAsyncCallback<Boolean> callback) {
		if (mConnectionState == STATE_CONNECTED) {

			// mLoading = true;
			// get the list of messages

			Integer firstMessageId = mEarliestMessage.get(username);
			if (firstMessageId == null) {
				firstMessageId = getEarliestMessageId(username);
				mEarliestMessage.put(username, firstMessageId);
			}
			// else {
			// firstMessageId -= 60;
			// if (firstMessageId < 1) {
			// firstMessageId = 1;
			// }
			// }

			if (firstMessageId != null) {

				if (firstMessageId > 1) {

					SurespotLog.d(TAG, username + ": asking server for messages before messageId: " + firstMessageId);
					// final int fMessageId = firstMessageId;
					final ChatAdapter chatAdapter = mChatAdapters.get(username);

					mNetworkController.getEarlierMessages(username, firstMessageId, new JsonHttpResponseHandler() {
						@Override
						public void onSuccess(final JSONArray jsonArray) {

							// if (getActivity() != null) {
							SurespotMessage message = null;

							try {
								for (int i = jsonArray.length() - 1; i >= 0; i--) {
									JSONObject jsonMessage = jsonArray.getJSONObject(i);
									message = SurespotMessage.toSurespotMessage(jsonMessage);
									chatAdapter.insertMessage(message, false);
								}
							}
							catch (JSONException e) {
								SurespotLog.e(TAG, e, "%s: error creating chat message", username);
							}

							SurespotLog.d(TAG, "%s: loaded: %d earlier messages from the server.", username, jsonArray.length());
							if (message != null) {
								mEarliestMessage.put(username, message.getId());
								// chatAdapter.notifyDataSetChanged();
							}

							// chatAdapter.setLoading(false);
							callback.handleResponse(jsonArray.length() > 0);

						}

						@Override
						public void onFailure(Throwable error, String content) {
							SurespotLog.i(TAG, error, "%s: getEarlierMessages", username);
							// chatAdapter.setLoading(false);
							callback.handleResponse(false);
						}
					});
				}
				else {
					SurespotLog.d(TAG, "%s: getEarlierMessages: no more messages.", username);
					callback.handleResponse(false);
					// ChatFragment.this.mNoEarlierMessages = true;
				}

			}
		}
	}

	private void getLatestData(final boolean mayBeCacheClear) {
		SurespotLog.d(TAG, "getLatestData");
		// setMessagesLoading(true);

		JSONArray spotIds = new JSONArray();
		for (Entry<String, ChatAdapter> entry : mChatAdapters.entrySet()) {
			JSONObject spot = new JSONObject();
			String username = entry.getKey();
			try {
				LatestIdPair p = getPreConnectIds(username);
				if (p != null) {
					spot.put("u", username);
					spot.put("m", p.latestMessageId);
					spot.put("cm", p.latestControlMessageId);
					spotIds.put(spot);
				}
			}
			catch (JSONException e) {
				continue;
			}
		}

		mNetworkController.getLatestData(mLatestUserControlId, spotIds, new JsonHttpResponseHandler() {

			@Override
			public void onSuccess(int statusCode, final JSONObject jsonResponse) {
				SurespotLog.d(TAG, "getlatestData success, response: %s, statusCode: %d", jsonResponse, statusCode);

				new AsyncTask<Void, Void, Void>() {
					@Override
					protected Void doInBackground(Void... voids) {
						//see if we need to update signatures, will only have sigs property if we need to update
						if (jsonResponse.has("sigs")) {
							JSONObject sigs = IdentityController.updateSignatures(mContext);
							mNetworkController.updateSigs(sigs, new AsyncHttpResponseHandler());
						}
						return null;
					}
				}.execute();

				JSONObject conversationIds = jsonResponse.optJSONObject("conversationIds");

				Friend friend = null;
				if (conversationIds != null) {
					Iterator i = conversationIds.keys();
					while (i.hasNext()) {
						String spot = (String) i.next();
						try {
							Integer availableId = conversationIds.getInt(spot);
							String user = ChatUtils.getOtherSpotUser(spot, mUsername);
							// update available ids
							friend = mFriendAdapter.getFriend(user);
							if (friend != null) {
								friend.setAvailableMessageId(availableId, mayBeCacheClear);
							}
						}
						catch (Exception e) {
							SurespotLog.w(TAG, e, "getlatestData");
						}
					}
				}

				JSONObject controlIds = jsonResponse.optJSONObject("controlIds");
				if (controlIds != null) {
					Iterator i = conversationIds.keys();
					while (i.hasNext()) {
						String spot = (String) i.next();
						try {
							Integer availableId = controlIds.getInt(spot);
							String user = ChatUtils.getOtherSpotUser(spot, mUsername);
							// update available ids
							friend = mFriendAdapter.getFriend(user);
							if (friend != null) {
								friend.setAvailableMessageControlId(availableId);
							}
						}
						catch (JSONException e) {
							SurespotLog.w(TAG, e, "getlatestData");
						}
					}
				}

				JSONArray userControlMessages = jsonResponse.optJSONArray("userControlMessages");
				if (userControlMessages != null) {
					handleControlMessages(mUsername, userControlMessages);
				}

				JSONArray messageDatas = jsonResponse.optJSONArray("messageData");
				if (messageDatas != null) {
					for (int i = 0; i < messageDatas.length(); i++) {
						try {
							JSONObject messageData = messageDatas.getJSONObject(i);
							String friendName = messageData.getString("username");

							JSONArray controlMessages = messageData.optJSONArray("controlMessages");
							if (controlMessages != null) {
								handleControlMessages(friendName, controlMessages);
							}

							JSONArray messages = messageData.optJSONArray("messages");
							if (messages != null) {
								handleMessages(friendName, messages, mayBeCacheClear);
							}

						}
						catch (JSONException e) {
							SurespotLog.w(TAG, e, "getlatestData");
						}
					}
				}

				if (friend != null) {
					mFriendAdapter.sort();
					mFriendAdapter.notifyDataSetChanged();
				}

				handleAutoInvite();
				setProgress(null, false);
			}

			@Override
			public void onFailure(Throwable arg0, String content) {
				// setMessagesLoading(false);
				SurespotLog.i(TAG, arg0, "loading latest messages failed");
				setProgress(null, false);

				if (arg0 instanceof HttpResponseException) {
					HttpResponseException error = (HttpResponseException) arg0;
					int statusCode = error.getStatusCode();
					switch (statusCode) {
					case 401:
						// don't show toast on 401 as we are going to be going bye bye
						return;
					}
				}

				Utils.makeToast(mContext, mContext.getString(R.string.loading_latest_messages_failed));

			}
		});

	}

	private class LatestIdPair {
		public int latestMessageId;
		public int latestControlMessageId;
	}

	private LatestIdPair getPreConnectIds(String username) {
		LatestIdPair idPair = mPreConnectIds.get(username);

		if (idPair == null) {
			idPair = new LatestIdPair();
			idPair.latestControlMessageId = 0;
			idPair.latestMessageId = 0;
		}

		return idPair;
	}

	private LatestIdPair getLatestIds(String username) {
		Friend friend = getFriendAdapter().getFriend(username);
		LatestIdPair idPair = mPreConnectIds.get(username);

		Integer latestMessageId = idPair.latestMessageId > -1 ? idPair.latestMessageId : 0;
		int latestAvailableId = friend.getAvailableMessageId();

		int latestControlId = idPair.latestControlMessageId > -1 ? idPair.latestControlMessageId : friend.getLastReceivedMessageControlId();
		int latestAvailableControlId = friend.getAvailableMessageControlId();

		int fetchMessageId = 0;
		if (latestMessageId > 0) {
			fetchMessageId = latestAvailableId > latestMessageId ? latestMessageId : -1;
		}

		int fetchControlMessageId = 0;
		if (latestControlId > 0) {
			fetchControlMessageId = latestAvailableControlId > latestControlId ? latestControlId : -1;
		}

		LatestIdPair intPair = new LatestIdPair();
		intPair.latestMessageId = fetchMessageId;
		intPair.latestControlMessageId = fetchControlMessageId;

		return intPair;
	}

	private void getLatestMessagesAndControls(final String username, boolean forceMessageUpdate) {
		LatestIdPair ids = getLatestIds(username);
		getLatestMessagesAndControls(username, ids.latestMessageId, ids.latestControlMessageId, forceMessageUpdate);
	}

	private void getLatestMessagesAndControls(String username, int messageId, boolean forceMessageUpdate) {
		getLatestMessagesAndControls(username, messageId, -1, forceMessageUpdate);
	}

	private void getLatestMessagesAndControls(final String username, final int fetchMessageId, int fetchControlMessageId, final boolean forceMessageUpdate) {
		if (getState() != STATE_CONNECTED) {
			return;
		}
		SurespotLog.d(TAG, "getLatestMessagesAndControls: name %s, fetchMessageId: %d, fetchControlMessageId: %d", username, fetchMessageId,
				fetchControlMessageId);
		if (fetchMessageId > -1 || fetchControlMessageId > -1) {
			setProgress(username, true);

			mNetworkController.getMessageData(username, fetchMessageId, fetchControlMessageId, new JsonHttpResponseHandler() {
				@Override
				public void onSuccess(int statusCode, JSONObject response) {

					JSONArray controlMessages = response.optJSONArray("controlMessages");
					if (controlMessages != null) {
						handleControlMessages(username, controlMessages);
					}

					JSONArray messages = response.optJSONArray("messages");

					// don't update messages if we didn't query for them
					// this prevents setting message state to error before we get the true result
					if (fetchMessageId > -1 || forceMessageUpdate) {
						handleMessages(username, messages, false);
					}

					setProgress(username, false);

				}
			});
		}

	}

	private void handleControlMessages(String username, JSONArray jsonArray) {
		SurespotLog.d(TAG, "%s: handleControlMessages", username);
		final ChatAdapter chatAdapter = mChatAdapters.get(username);

		SurespotControlMessage message = null;
		boolean messageActivity = false;
		boolean userActivity = false;
		for (int i = 0; i < jsonArray.length(); i++) {
			try {
				JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
				message = SurespotControlMessage.toSurespotControlMessage(jsonMessage);
				handleControlMessage(chatAdapter, message, false, false);
				// if it's a system message from another user then check version
				if (message.getType().equals("user")) {
					userActivity = true;
				}
				else
					if (message.getType().equals("message")) {
						messageActivity = true;
					}

			}
			catch (JSONException e) {
				SurespotLog.w(TAG, e, "%s: error creating chat message", username);
			}

		}

		if (message != null) {

			SurespotLog.d(TAG, "%s: loaded: %d latest control messages from the server.", username, jsonArray.length());

			if (messageActivity || userActivity) {
				Friend friend = mFriendAdapter.getFriend(username);
				if (friend != null) {

					if (messageActivity) {

						if (chatAdapter != null) {
							friend.setLastReceivedMessageControlId(message.getId());
							chatAdapter.sort();
							chatAdapter.notifyDataSetChanged();
						}

						friend.setAvailableMessageControlId(message.getId());
						mFriendAdapter.notifyDataSetChanged();

					}

					if (userActivity) {
						saveFriends();
						mFriendAdapter.notifyDataSetChanged();
					}
				}
			}
		}

		// chatAdapter.setLoading(false);
	}

	private void handleControlMessage(ChatAdapter chatAdapter, SurespotControlMessage message, boolean notify, boolean reApplying) {
		// if it's a system message from another user then check version
		if (message.getType().equals("user")) {
			handleUserControlMessage(message, notify);
		}
		else
			if (message.getType().equals("message")) {
				String otherUser = ChatUtils.getOtherSpotUser(message.getData(), mUsername);
				Friend friend = mFriendAdapter.getFriend(otherUser);

				if (chatAdapter == null) {
					chatAdapter = mChatAdapters.get(otherUser);
				}

				if (chatAdapter != null) {
					// if we're not re applying this control message
					if (!reApplying) {
						// add control message to check messages against later for this session
						chatAdapter.addControlMessage(message);
					}

					boolean controlFromMe = message.getFrom().equals(mUsername);
					if (message.getAction().equals("delete")) {
						int messageId = Integer.parseInt(message.getMoreData());
						SurespotMessage dMessage = chatAdapter.getMessageById(messageId);

						if (dMessage != null) {
							deleteMessageInternal(chatAdapter, dMessage, controlFromMe);
						}
					}
					else {
						if (message.getAction().equals("deleteAll")) {
							if (message.getMoreData() != null) {
								if (controlFromMe) {
									chatAdapter.deleteAllMessages(Integer.parseInt(message.getMoreData()));
								}
								else {
									chatAdapter.deleteTheirMessages(Integer.parseInt(message.getMoreData()));
								}
							}
						}
						else {
							if (message.getAction().equals("shareable") || message.getAction().equals("notshareable")) {
								int messageId = Integer.parseInt(message.getMoreData());
								SurespotMessage dMessage = chatAdapter.getMessageById(messageId);
								if (dMessage != null) {
									SurespotLog.d(TAG, "setting message " + message.getAction());
									dMessage.setShareable(message.getAction().equals("shareable") ? true : false);
								}
							}
						}
					}
				}

				if (notify) {
					if (friend != null) {
						// if the chat adapter is open we will have acted upon the control message
						if (chatAdapter != null) {
							friend.setLastReceivedMessageControlId(message.getId());
						}

						friend.setAvailableMessageControlId(message.getId());
					}

					if (chatAdapter != null) {
						chatAdapter.notifyDataSetChanged();
					}
				}
			}
	}

	private void handleUserControlMessage(SurespotControlMessage message, boolean notify) {

		mLatestUserControlId = message.getId();
		String user = null;

		if (message.getAction().equals("revoke")) {
			IdentityController.updateLatestVersion(mContext, message.getData(), message.getMoreData());
		}
		else
			if (message.getAction().equals("invited")) {
				user = message.getData();
				mFriendAdapter.addFriendInvited(user);
			}
			else
				if (message.getAction().equals("added")) {
					user = message.getData();
					mFriendAdapter.addNewFriend(user);
				}
				else
					if (message.getAction().equals("invite")) {
						user = message.getData();
						mFriendAdapter.addFriendInviter(user);
					}
					else
						if (message.getAction().equals("ignore")) {
							String friendName = message.getData();
							Friend friend = mFriendAdapter.getFriend(friendName);

							// if they're not deleted, remove them
							if (friend != null) {
								if (!friend.isDeleted()) {

									mFriendAdapter.removeFriend(friendName);
								}
								else {
									// they've been deleted, just remove the invite flags
									friend.setInviter(false);
									friend.setInvited(false);

								}
							}

						}
						else
							if (message.getAction().equals("delete")) {
								String friendName = message.getData();

								Friend friend = mFriendAdapter.getFriend(friendName);

								if (friend != null) {
									// if it was just a delete of an invite
									if (friend.isInviter() || friend.isInvited()) {

										// if they're not deleted, remove them
										if (!friend.isDeleted()) {
											mFriendAdapter.removeFriend(friendName);
										}
										else {
											// they've been deleted, just remove the invite flags
											friend.setInviter(false);
											friend.setInvited(false);
										}
									}
									// they really deleted us boo hoo
									else {
										handleDeleteUser(friendName, message.getMoreData(), notify);
									}
								}

								// clear any associated invite notification
								String loggedInUser = mUsername;
								if (loggedInUser != null) {
									mNotificationManager.cancel(loggedInUser + ":" + friendName,
											SurespotConstants.IntentRequestCodes.INVITE_REQUEST_NOTIFICATION);
								}

							}
							else
								if (message.getAction().equals("friendImage")) {
									String friendName = message.getData();
									Friend friend = mFriendAdapter.getFriend(friendName);

									if (friend != null) {

										String moreData = message.getMoreData();

										if (moreData != null) {

											JSONObject jsonData = null;
											try {
												jsonData = new JSONObject(moreData);
												String iv = jsonData.getString("iv");
												String url = jsonData.getString("url");
												String version = jsonData.getString("version");
												boolean hashed = jsonData.optBoolean("imageHashed", false);
												setImageUrl(friendName, url, version, iv, hashed);
											}
											catch (JSONException e) {
												SurespotLog.e(TAG, e, "could not parse friend image control message json");

											}
										}
										else {
											removeFriendImage(friendName);
										}
									}
								}
								else
									if (message.getAction().equals("friendAlias")) {
										String friendName = message.getData();
										Friend friend = mFriendAdapter.getFriend(friendName);

										if (friend != null) {

											String moreData = message.getMoreData();

											if (moreData != null) {
												JSONObject jsonData = null;
												try {
													jsonData = new JSONObject(moreData);
													String iv = jsonData.getString("iv");
													String data = jsonData.getString("data");
													String version = jsonData.getString("version");
													boolean hashed = jsonData.optBoolean("aliasHashed", false);
													setFriendAlias(friendName, data, version, iv, hashed);
												}
												catch (JSONException e) {
													SurespotLog.e(TAG, e, "could not parse friend alias control message json");
												}
											}
											else {
												removeFriendAlias(friendName);
											}
										}
									}
		if (notify) {
			mFriendAdapter.notifyDataSetChanged();
			saveFriends();
		}

	}

	private void handleDeleteUser(String deletedUser, String deleter, boolean notify) {
		SurespotLog.d(TAG, "handleDeleteUser,  deletedUser: %s, deleter: %s", deletedUser, deleter);
		String username = mUsername;

		Friend friend = mFriendAdapter.getFriend(deletedUser);

		boolean iDidTheDeleting = deleter.equals(username);
		if (iDidTheDeleting) {
			// won't be needing this anymore
			closeTab(deletedUser);

			// blow all the state associated with this user away
			StateController.wipeUserState(mContext, username, deletedUser);

			// clear in memory cached data
			SurespotApplication.getCachingService().clearUserData(deletedUser);

			// clear the http cache
			mNetworkController.clearCache();
			// or you
			mFriendAdapter.removeFriend(deletedUser);
		}
		// you deleted me, you bastard!!
		else {
			ChatAdapter chatAdapter = mChatAdapters.get(deleter);

			// i'll delete all your messages then
			if (chatAdapter != null) {
				chatAdapter.userDeleted();
				if (notify) {
					chatAdapter.notifyDataSetChanged();
				}
			}

			// and mark you as deleted until I want to delete you
			friend.setDeleted();

			// force the controls to update
			if (friend != null && mCurrentChat != null && mCurrentChat.equals(deletedUser)) {
				mTabShowingCallback.handleResponse(friend);
			}
		}

		enableMenuItems(friend);
	}

	private void handleErrorMessage(SurespotErrorMessage errorMessage) {
		SurespotMessage message = null;
		Iterator<SurespotMessage> iterator = mResendBuffer.iterator();
		while (iterator.hasNext()) {
			message = iterator.next();
			if (message.getIv().equals(errorMessage.getId())) {
				iterator.remove();

				message.setErrorStatus(errorMessage.getStatus());
				break;
			}
		}

		if (message != null) {
			ChatAdapter chatAdapter = mChatAdapters.get(message.getOtherUser());
			if (chatAdapter != null) {
				chatAdapter.notifyDataSetChanged();
			}
		}

	}

	private void deleteMessageInternal(ChatAdapter chatAdapter, SurespotMessage dMessage, boolean initiatedByMe) {
		// if it's an image blow the http cache entry away
		if (dMessage.getMimeType() != null) {
			if (dMessage.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE) || dMessage.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
				mNetworkController.purgeCacheUrl(dMessage.getData());
			}

			boolean myMessage = dMessage.getFrom().equals(mUsername);

			// if i sent the delete, or it's not my message then delete it
			// (if someone else deleted my message we don't care)
			if (initiatedByMe || !myMessage) {
				SurespotLog.d(TAG, "deleting message");
				chatAdapter.deleteMessageById(dMessage.getId());
			}
		}
	}

	private void handleMessages(String username, JSONArray jsonMessages, boolean mayBeCacheClear) {
		SurespotLog.d(TAG, "%s: handleMessages", username);
		final ChatAdapter chatAdapter = mChatAdapters.get(username);
		if (chatAdapter == null) {
			return;
		}

		// if we received new messages
		if (jsonMessages != null) {

			int sentByMeCount = 0;

			SurespotMessage lastMessage = null;
			try {
				SurespotLog.d(TAG, "%s: loaded: %d messages from the server", username, jsonMessages.length());
				for (int i = 0; i < jsonMessages.length(); i++) {

					lastMessage = SurespotMessage.toSurespotMessage(jsonMessages.getJSONObject(i));
					boolean myMessage = lastMessage.getFrom().equals(mUsername);

					if (myMessage) {
						if (lastMessage.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
							handleCachedFile(chatAdapter, lastMessage);
						}
						else {
							if (lastMessage.getMimeType().equals(SurespotConstants.MimeTypes.M4A)) {
								handleCachedFile(chatAdapter, lastMessage);
							}
						}
					}

					boolean added = applyControlMessages(chatAdapter, lastMessage, false, false, false);

					mResendBuffer.remove(lastMessage);
					if (added && myMessage) {
						sentByMeCount++;
					}
				}
			}
			catch (JSONException e) {
				SurespotLog.w(TAG, e, "jsonStringsToMessages");

			}
			catch (SurespotMessageSequenceException e) {
				// shouldn't happen
				SurespotLog.w(TAG, e, "handleMessages");
				// getLatestMessagesAndControls(username, e.getMessageId(), -1);
				// setProgress(username, false);
				return;
			}

			if (lastMessage != null) {
				Friend friend = mFriendAdapter.getFriend(username);

				int availableId = lastMessage.getId();
				friend.setAvailableMessageId(availableId, false);

				int lastViewedId = friend.getLastViewedMessageId();

				// how many new messages total are there
				int delta = availableId - lastViewedId;

				// if the current chat is showing or
				// all the new messages are mine then i've viewed them all
				if (username.equals(mCurrentChat) || sentByMeCount == delta) {
					friend.setLastViewedMessageId(availableId);
				}
				else {
					// set the last viewed id to the difference caused by their messages
					friend.setLastViewedMessageId(availableId - (delta - sentByMeCount));
				}

				if (mayBeCacheClear) {
					friend.setLastViewedMessageId(lastMessage.getId());
				}

				mFriendAdapter.sort();
				mFriendAdapter.notifyDataSetChanged();

				scrollToEnd(username);
			}
		}

		chatAdapter.sort();
		chatAdapter.doneCheckingSequence();
		// mark messages left in chatAdapter with no id as errored
		chatAdapter.markErrored();
		chatAdapter.notifyDataSetChanged();
	}

	private Integer getEarliestMessageId(String username) {

		ChatAdapter chatAdapter = mChatAdapters.get(username);
		Integer firstMessageId = null;
		if (chatAdapter != null) {
			SurespotMessage firstMessage = chatAdapter.getFirstMessageWithId();

			if (firstMessage != null) {
				firstMessageId = firstMessage.getId();
			}

		}
		return firstMessageId;
	}

	private int getLatestMessageId(String username) {
		Integer lastMessageId = 0;
		ChatAdapter chatAdapter = mChatAdapters.get(username);
		if (chatAdapter != null) {

			SurespotMessage lastMessage = chatAdapter.getLastMessageWithId();
			if (lastMessage != null) {
				lastMessageId = lastMessage.getId();
			}
		}
		return lastMessageId;

	}

	private Integer getLatestMessageControlId(String username) {
		Friend friend = mFriendAdapter.getFriend(username);
		Integer lastControlId = null;
		if (friend != null) {
			lastControlId = friend.getLastReceivedMessageControlId();
		}
		return lastControlId == null ? 0 : lastControlId;
	}

	public synchronized void loadMessages(String username, boolean replace) {
		SurespotLog.d(TAG, "loadMessages: " + username);

		if (!TextUtils.isEmpty(mUsername)) {
			String spot = ChatUtils.getSpot(mUsername, username);
			ChatAdapter chatAdapter = mChatAdapters.get(username);
			if (replace) {
				chatAdapter.setMessages(SurespotApplication.getStateController().loadMessages(mUsername, spot));
			}
			else {
				chatAdapter.addOrUpdateMessages(SurespotApplication.getStateController().loadMessages(mUsername, spot));
			}
		}

	}

	private synchronized void saveMessages() {
		// save last 30? messages
		SurespotLog.d(TAG, "saveMessages");
		if (mUsername != null) {
			for (Entry<String, ChatAdapter> entry : mChatAdapters.entrySet()) {
				String them = entry.getKey();
				String spot = ChatUtils.getSpot(mUsername, them);
				SurespotApplication.getStateController().saveMessages(mUsername, spot, entry.getValue().getMessages(),
						entry.getValue().getCurrentScrollPositionId());
			}
		}
	}

	public synchronized void saveMessages(String username) {
		// save last 30? messages
		SurespotLog.d(TAG, "saveMessages, username: %s", username);
		ChatAdapter chatAdapter = mChatAdapters.get(username);

		if (chatAdapter != null) {
			SurespotApplication.getStateController().saveMessages(mUsername, ChatUtils.getSpot(mUsername, username), chatAdapter.getMessages(),
					chatAdapter.getCurrentScrollPositionId());
		}

	}

	private void saveUnsentMessages() {
		mResendBuffer.addAll(mSendBuffer);
		// SurespotLog.d(TAG, "saving: " + mResendBuffer.size() + " unsent messages.");
		SurespotApplication.getStateController().saveUnsentMessages(mUsername, mResendBuffer);
	}

	private void loadUnsentMessages() {
		Iterator<SurespotMessage> iterator = SurespotApplication.getStateController().loadUnsentMessages(mUsername).iterator();
		while (iterator.hasNext()) {
			mResendBuffer.add(iterator.next());
		}
		// SurespotLog.d(TAG, "loaded: " + mSendBuffer.size() + " unsent messages.");
	}

	public synchronized void logout() {
		mCurrentChat = null;
		onPause();
		// mViewPager = null;
		// mCallback401 = null;
		// mChatPagerAdapter = null;
		// mIndicator = null;
		// mFragmentManager = null;
		// mFriendAdapter = null;
		// mMenuItems = null;
		// mSocketCallback = null;
		mChatAdapters.clear();
		// mActiveChats.clear();
		// mReadSinceConnected.clear();
		mResendBuffer.clear();
		mSendBuffer.clear();
	}

	private void saveState(String username) {

		SurespotLog.d(TAG, "saveState");

		if (username == null) {
			saveUnsentMessages();
			saveMessages();
			SurespotLog.d(TAG, "saving last chat: %s", mCurrentChat);
			Utils.putSharedPrefsString(mContext, SurespotConstants.PrefNames.LAST_CHAT, mCurrentChat);
			saveFriends();
		}
		else {
			saveMessages(username);
		}
	}

	private void saveFriends() {
		SurespotApplication.getStateController().saveFriends(mUsername, mLatestUserControlId, mFriendAdapter.getFriends());
	}

	private void loadState(String username) {
		SurespotLog.d(TAG, "loadState");
		FriendState fs = SurespotApplication.getStateController().loadFriends(username);

		List<Friend> friends = null;
		if (fs != null) {
			mLatestUserControlId = fs.userControlId;
			friends = fs.friends;
		}

		mFriendAdapter.setFriends(friends);
		mFriendAdapter.setLoading(false);

		loadUnsentMessages();
	}

	private boolean mGlobalProgress;
	private HashMap<String, Boolean> mChatProgress = new HashMap<String, Boolean>();

	private synchronized void setProgress(String key, boolean inProgress) {

		if (key == null) {
			mGlobalProgress = inProgress;
		}

		else {
			if (inProgress) {
				mChatProgress.put(key, true);
			}
			else {
				mChatProgress.remove(key);
			}
		}

		boolean progress = isInProgress();
		SurespotLog.d(TAG, "setProgress, isInProgress(): %b", progress);

		if (mProgressCallback != null) {
			mProgressCallback.handleResponse(progress);
		}
	}

	public synchronized boolean isInProgress() {
		return mGlobalProgress || !mChatProgress.isEmpty();
	}

	public synchronized void onResume() {
		SurespotLog.d(TAG, "onResume, mPaused: %b", mPaused);
		if (mPaused) {
			mPaused = false;

			setProgress(null, true);
			// getFriendsAndIds();

			// load chat messages from disk that may have been added by gcm
			for (Entry<String, ChatAdapter> ca : mChatAdapters.entrySet()) {
				loadMessages(ca.getKey(), false);
			}
			connect();
			mContext.registerReceiver(mConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

			clearMessageNotification(mUsername, mCurrentChat);
		}
	}

	public synchronized void onPause() {
		SurespotLog.d(TAG, "onPause, mPaused: %b", mPaused);
		if (!mPaused) {
			mPaused = true;
			saveState(null);
		}

		disconnect();

		synchronized (BACKGROUND_TIMER_LOCK) {

			if (mBackgroundTimer != null) {
				mBackgroundTimer.cancel();
				mBackgroundTimer = null;
			}
			if (mReconnectTask != null) {
				boolean cancel = mReconnectTask.cancel();
				mReconnectTask = null;
				SurespotLog.d(TAG, "Cancelled reconnect task: " + cancel);
			}
		}

		// socket = null;

		// workaround unchecked exception: https://code.google.com/p/android/issues/detail?id=18147
		try {
			mContext.unregisterReceiver(mConnectivityReceiver);
		}
		catch (IllegalArgumentException e) {
			if (e.getMessage().contains("Receiver not registered")) {
				// Ignore this exception. This is exactly what is desired
			}
			else {
				// unexpected, re-throw
				throw e;
			}
		}
		// }

	}

	ChatAdapter getChatAdapter(Context context, String username) {

		ChatAdapter chatAdapter = mChatAdapters.get(username);
		if (chatAdapter == null) {

			chatAdapter = new ChatAdapter(context);

			Friend friend = mFriendAdapter.getFriend(username);
			if (friend != null) {
				if (friend.isDeleted()) {
					chatAdapter.userDeleted();
				}
			}

			SurespotLog.d(TAG, "getChatAdapter created chat adapter for: %s", username);
			mChatAdapters.put(username, chatAdapter);

			// load savedmessages
			loadMessages(username, true);

			LatestIdPair idPair = new LatestIdPair();
			idPair.latestMessageId = getLatestMessageId(username);
			idPair.latestControlMessageId = getLatestMessageControlId(username);
			SurespotLog.d(TAG, "setting preconnectids for: %s, latest message id: %d, latestcontrolid: %d", username, idPair.latestMessageId,
					idPair.latestControlMessageId);
			mPreConnectIds.put(username, idPair);

			// get latest messages from server
			getLatestMessagesAndControls(username, false);
		}

		return chatAdapter;
	}

	public void destroyChatAdapter(String username) {
		SurespotLog.d(TAG, "destroying chat adapter for: %s", username);
		saveState(username);
		mChatAdapters.remove(username);
	}

	public synchronized void setCurrentChat(final String username) {

		SurespotLog.d(TAG, "setCurrentChat: %s", username);
		String loggedInUser = mUsername;
		if (loggedInUser == null) {
			return;
		}

		Friend friend = null;
		if (username != null) {
			friend = mFriendAdapter.getFriend(username);
		}

		mTabShowingCallback.handleResponse(friend);
		if (friend != null) {
			mCurrentChat = username;
			mChatPagerAdapter.addChatFriend(friend);
			friend.setChatActive(true);
			friend.setLastViewedMessageId(friend.getAvailableMessageId());

			// cancel associated notifications
			clearMessageNotification(loggedInUser, username);
			int wantedPosition = mChatPagerAdapter.getChatFragmentPosition(username);

			if (wantedPosition != mViewPager.getCurrentItem()) {
				mViewPager.setCurrentItem(wantedPosition, true);
			}

			if (mMode == MODE_SELECT) {
				mSendIntentCallback.handleResponse(null);
				setMode(MODE_NORMAL);
			}

		}
		else {
			mCurrentChat = null;
			mViewPager.setCurrentItem(0, true);
			mNotificationManager.cancel(loggedInUser + ":" + username, SurespotConstants.IntentRequestCodes.INVITE_REQUEST_NOTIFICATION);
			mNotificationManager.cancel(loggedInUser, SurespotConstants.IntentRequestCodes.INVITE_RESPONSE_NOTIFICATION);
		}

		mFriendAdapter.sort();
		mFriendAdapter.notifyDataSetChanged();

		// set menu item enable state
		enableMenuItems(friend);

	}

	private void clearMessageNotification(String loggedInUser, String username) {
		if (!TextUtils.isEmpty(loggedInUser) && !TextUtils.isEmpty(username)) {
			mNotificationManager.cancel(loggedInUser + ":" + ChatUtils.getSpot(loggedInUser, username),
					SurespotConstants.IntentRequestCodes.NEW_MESSAGE_NOTIFICATION);
		}
	}

	private ChatFragment getChatFragment(String username) {
		String fragmentTag = Utils.makePagerFragmentName(mViewPager.getId(), username.hashCode());
		SurespotLog.d(TAG, "looking for fragment: %s", fragmentTag);
		ChatFragment chatFragment = (ChatFragment) mFragmentManager.findFragmentByTag(fragmentTag);
		SurespotLog.d(TAG, "fragment: %s", chatFragment);
		return chatFragment;
	}

	public void sendMessage(final String username, final String plainText, final String mimeType) {
		if (plainText.length() > 0) {
			final ChatAdapter chatAdapter = mChatAdapters.get(username);
			if (chatAdapter == null) {
				return;
			}
			// display the message immediately
			final byte[] iv = EncryptionController.getIv();

			// build a message without the encryption values set as they could take a while

			final SurespotMessage chatMessage = ChatUtils.buildPlainMessage(username, mimeType, EmojiParser.getInstance().addEmojiSpans(plainText), new String(
					ChatUtils.base64EncodeNowrap(iv)));

			try {

				chatAdapter.addOrUpdateMessage(chatMessage, false, true, true);
				enqueueMessage(chatMessage);
			}
			catch (SurespotMessageSequenceException e) {
				// not gonna happen
				SurespotLog.w(TAG, e, "sendMessage");
			}

			// do encryption in background
			new AsyncTask<Void, Void, Boolean>() {

				@Override
				protected Boolean doInBackground(Void... arg0) {
					String ourLatestVersion = IdentityController.getOurLatestVersion();
					String theirLatestVersion = IdentityController.getTheirLatestVersion(username);

					String result = EncryptionController.symmetricEncrypt(ourLatestVersion, username, theirLatestVersion, plainText, iv);

					if (result != null) {
						chatMessage.setData(result);
						chatMessage.setFromVersion(ourLatestVersion);
						chatMessage.setToVersion(theirLatestVersion);

						SurespotLog.d(TAG, "sending message to chat controller iv: %s", chatMessage.getIv());
						sendMessages();
						return true;
					}
					else {
						SurespotLog.d(TAG, "could not encrypt message, iv: %s", chatMessage.getIv());
						chatMessage.setErrorStatus(500);

						return false;
					}
				}

				protected void onPostExecute(Boolean success) {
					// if success is false we will have set an error status in the message so notify
					if (!success) {
						chatAdapter.notifyDataSetChanged();
					}
				};

			}.execute();
		}

	}

	public void sendVoiceMessage(final String username, final byte[] plainData, final String mimeType) {
		if (plainData.length > 0) {
			final ChatAdapter chatAdapter = mChatAdapters.get(username);
			if (chatAdapter == null) {
				return;
			}
			// display the message immediately
			final byte[] iv = EncryptionController.getIv();

			// build a message without the encryption values set as they could take a while

			final SurespotMessage chatMessage = ChatUtils.buildPlainBinaryMessage(username, mimeType, plainData, new String(ChatUtils.base64EncodeNowrap(iv)));

			try {

				chatAdapter.addOrUpdateMessage(chatMessage, false, true, true);
				enqueueMessage(chatMessage);
			}
			catch (SurespotMessageSequenceException e) {
				// not gonna happen
				SurespotLog.w(TAG, e, "sendMessage");
			}

			// do encryption in background
			new AsyncTask<Void, Void, Boolean>() {

				@Override
				protected Boolean doInBackground(Void... arg0) {
					String ourLatestVersion = IdentityController.getOurLatestVersion();
					String theirLatestVersion = IdentityController.getTheirLatestVersion(username);

					byte[] result = EncryptionController.symmetricEncrypt(ourLatestVersion, username, theirLatestVersion, plainData, iv);

					if (result != null) {

						// set data for sending
						chatMessage.setData(new String(ChatUtils.base64EncodeNowrap(result)));
						chatMessage.setFromVersion(ourLatestVersion);
						chatMessage.setToVersion(theirLatestVersion);

						SurespotLog.d(TAG, "sending message to chat controller iv: %s", chatMessage.getIv());
						sendMessages();
						return true;
					}
					else {
						SurespotLog.d(TAG, "could not encrypt message, iv: %s", chatMessage.getIv());
						chatMessage.setErrorStatus(500);

						return false;
					}
				}

				protected void onPostExecute(Boolean success) {
					// if success is false we will have set an error status in the message so notify
					if (!success) {
						chatAdapter.notifyDataSetChanged();
					}
				};

			}.execute();
		}

	}

	void addMessage(Activity activity, SurespotMessage message) {
		if (mChatAdapters != null) {
			ChatAdapter chatAdapter = mChatAdapters.get(message.getTo());

			try {
				chatAdapter.addOrUpdateMessage(message, false, true, true);
				scrollToEnd(message.getTo());
				saveState(message.getTo());
			}
			catch (Exception e) {
				SurespotLog.e(TAG, e, "addMessage");
			}
		}
		else {
			Utils.makeToast(activity, activity.getString(R.string.error_message_generic));
		}
	}

	public static String getCurrentChat() {
		return mCurrentChat;
	}

	public static boolean isPaused() {
		return mPaused;
	}

	public boolean hasEarlierMessages(String username) {
		Integer id = mEarliestMessage.get(username);
		if (id == null) {
			id = getEarliestMessageId(username);
		}

		if (id != null && id > 1) {
			return true;
		}

		return false;
	}

	public void deleteMessage(final SurespotMessage message) {
		// if it's on the server, send delete control message otherwise just delete it locally
		if (message.getId() != null) {

			final ChatAdapter chatAdapter = mChatAdapters.get(message.getOtherUser());
			setProgress("delete", true);
			if (chatAdapter != null) {
				mNetworkController.deleteMessage(message.getOtherUser(), message.getId(), new AsyncHttpResponseHandler() {
					@Override
					public void onSuccess(int statusCode, String content) {
						deleteMessageInternal(chatAdapter, message, true);
						setProgress("delete", false);
					}

					@Override
					public void onFailure(Throwable error, String content) {
						SurespotLog.i(TAG, error, "deleteMessage");
						setProgress("delete", false);
						Utils.makeToast(mContext, mContext.getString(R.string.could_not_delete_message));

					}

				});
			}

		}
		else {
			// remove the local message
			String otherUser = message.getOtherUser();
			mResendBuffer.remove(message);
			mSendBuffer.remove(message);

			ChatAdapter chatAdapter = mChatAdapters.get(otherUser);
			chatAdapter.deleteMessageByIv(message.getIv());
			saveState(otherUser);

			// if it's an image, delete the local image file
			if (message.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
				if (message.getData().startsWith("file")) {
					try {
						new File(new URI(message.getData())).delete();
					}
					catch (URISyntaxException e) {
						SurespotLog.w(TAG, e, "deleteMessage");
					}
				}
			}
		}
	}

	public void deleteMessages(String name) {
		Friend friend = mFriendAdapter.getFriend(name);
		if (friend != null) {
			deleteMessages(friend);
		}
	}

	public void deleteMessages(final Friend friend) {
		// if it's on the server, send delete control message otherwise just delete it locally

		if (friend != null) {
			String username = friend.getName();

			setProgress("deleteMessages", true);
			int lastReceivedMessageId = 0;
			final ChatAdapter chatAdapter = mChatAdapters.get(username);
			if (chatAdapter != null) {
				lastReceivedMessageId = getLatestMessageId(username);
			}
			else {
				lastReceivedMessageId = friend.getLastViewedMessageId();
			}

			final int finalMessageId = lastReceivedMessageId;
			mNetworkController.deleteMessages(username, lastReceivedMessageId, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(int statusCode, String content) {

					if (chatAdapter != null) {
						chatAdapter.deleteAllMessages(finalMessageId);
						chatAdapter.notifyDataSetChanged();
					} else {
						// tell friend there's a new control message so they get it when the tab is opened
						friend.setAvailableMessageControlId(friend.getAvailableMessageControlId() + 1);
						saveFriends();
					}

					setProgress("deleteMessages", false);
				}

				@Override
				public void onFailure(Throwable error, String content) {
					setProgress("deleteMessages", false);
					Utils.makeToast(mContext, mContext.getString(R.string.could_not_delete_messages));
				}
			});
		}
	}

	public void deleteFriend(Friend friend) {

		if (friend != null) {
			final String username = friend.getName();
			setProgress("deleteFriend", true);
			mNetworkController.deleteFriend(username, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(int statusCode, String content) {
					handleDeleteUser(username, mUsername, true);
					setProgress("deleteFriend", false);
				}

				@Override
				public void onFailure(Throwable error, String content) {
					SurespotLog.i(TAG, error, "deleteFriend");
					setProgress("deleteFriend", false);
					Utils.makeToast(mContext, mContext.getString(R.string.could_not_delete_friend));
				}
			});

		}

	}

	public void toggleMessageShareable(String to, final String messageIv) {
		final ChatAdapter chatAdapter = mChatAdapters.get(to);
		final SurespotMessage message = chatAdapter.getMessageByIv(messageIv);
		if (message != null && message.getId() > 0) {
			String messageUsername = message.getOtherUser();

			if (!messageUsername.equals(to)) {
				Utils.makeToast(mContext, mContext.getString(R.string.could_not_set_message_lock_state));
				return;
			}

			if (chatAdapter != null) {

				setProgress("shareable", true);
				mNetworkController.setMessageShareable(to, message.getId(), !message.isShareable(), new AsyncHttpResponseHandler() {
					@Override
					public void onSuccess(int statusCode, String status) {
						setProgress("shareable", false);

						if (status == null) {
							return;
						}

						SurespotLog.d(TAG, "setting message sharable via http: %s", status);
						if (status.equals("shareable")) {
							message.setShareable(true);
						}
						else
							if (status.equals("notshareable")) {
								message.setShareable(false);
							}

						chatAdapter.notifyDataSetChanged();
					}

					@Override
					public void onFailure(Throwable error, String content) {
						SurespotLog.i(TAG, error, "toggleMessageShareable");
						setProgress("shareable", false);
						Utils.makeToast(mContext, mContext.getString(R.string.could_not_set_message_lock_state));
					}
				});
			}
		}
	}

	public void resendFileMessage(String to, final String messageIv) {
		final ChatAdapter chatAdapter = mChatAdapters.get(to);
		final SurespotMessage message = chatAdapter.getMessageByIv(messageIv);

		// reset status flags
		message.setErrorStatus(0);
		message.setAlreadySent(false);
		chatAdapter.notifyDataSetChanged();
		setProgress("resend", true);
		ChatUtils.resendFileMessage(mContext, mNetworkController, message, new IAsyncCallback<Integer>() {

			@Override
			public void handleResponse(Integer result) {
				setProgress("resend", false);
				if (result == 200) {
					message.setErrorStatus(0);
				}
				else {
					message.setErrorStatus(result);
				}

				message.setAlreadySent(true);
				chatAdapter.notifyDataSetChanged();
			}
		});

	}

	public FriendAdapter getFriendAdapter() {
		return mFriendAdapter;
	}

	public boolean isFriendDeleted(String username) {
		return getFriendAdapter().getFriend(username).isDeleted();
	}

	public boolean isFriendDeleted() {
		return getFriendAdapter().getFriend(mCurrentChat).isDeleted();
	}

	private void getFriendsAndData() {
		if (mFriendAdapter.getCount() == 0 && mLatestUserControlId == 0) {
			mFriendAdapter.setLoading(true);
			// get the list of friends
			mNetworkController.getFriends(new JsonHttpResponseHandler() {
				@Override
				public void onSuccess(JSONObject jsonObject) {
					SurespotLog.d(TAG, "getFriends success.");
					ArrayList<Friend> friends = new ArrayList<Friend>();
					boolean userSuddenlyHasFriends = false;
					try {
						mLatestUserControlId = jsonObject.getInt("userControlId");
						JSONArray friendsArray = jsonObject.optJSONArray("friends");

						if (friendsArray != null) {
							for (int i = 0; i < friendsArray.length(); i++) {
								JSONObject jsonFriend = friendsArray.getJSONObject(i);

								Friend friend = Friend.toFriend(jsonFriend);
								friends.add(friend);

								SurespotLog.d(TAG, "getFriendsAndIds,  adding friend: %s", friend);
							}
						}
						if (friends.size() > 0) {
							userSuddenlyHasFriends = true;
						}
					}
					catch (JSONException e) {
						SurespotLog.e(TAG, e, "getFriendsAndIds");
						mFriendAdapter.setLoading(false);
						return;
					}

					if (mFriendAdapter != null) {
						mFriendAdapter.addFriends(friends);
						mFriendAdapter.setLoading(false);
					}

					getLatestData(userSuddenlyHasFriends);
				}

				@Override
				public void onFailure(Throwable arg0, String content) {
					// if we didn't get a 401
					if (!mNetworkController.isUnauthorized()) {
						mFriendAdapter.setLoading(false);
						SurespotLog.i(TAG, arg0, "getFriends: %s", content);
						setProgress(null, false);
					}
				}
			});
		}
		else {
			getLatestData(false);
		}
	}

	public void closeTab() {
		if (mChatPagerAdapter.getCount() > 0) {

			int position = mViewPager.getCurrentItem();
			if (position > 0) {

				String name = mChatPagerAdapter.getChatName(position);
				if (name != null) {
					SurespotLog.d(TAG, "closeTab, name: %s, position: %d", name, position);

					mChatPagerAdapter.removeChat(mViewPager.getId(), position);
					mFriendAdapter.setChatActive(name, false);
					mEarliestMessage.remove(name);
					destroyChatAdapter(name);
					mIndicator.notifyDataSetChanged();

					position = mViewPager.getCurrentItem();
					setCurrentChat(mChatPagerAdapter.getChatName(position));
					SurespotLog.d(TAG, "closeTab, new tab name: %s, position: %d", mCurrentChat, position);
				}
			}
		}
	}

	/**
	 * Called when a user has been deleted
	 * 
	 * @param username
	 */

	public void closeTab(String username) {
		if (mChatPagerAdapter.getCount() > 0) {

			int position = mChatPagerAdapter.getChatFragmentPosition(username);
			if (position > 0) {

				String name = mChatPagerAdapter.getChatName(position);
				if (name != null) {
					SurespotLog.d(TAG, "closeTab, name: %s, position: %d", name, position);

					mChatPagerAdapter.removeChat(mViewPager.getId(), position);
					mFriendAdapter.setChatActive(name, false);
					mEarliestMessage.remove(name);
					destroyChatAdapter(name);

					mIndicator.notifyDataSetChanged();

					position = mViewPager.getCurrentItem();
					setCurrentChat(mChatPagerAdapter.getChatName(position));
					SurespotLog.d(TAG, "closeTab, new tab name: %s, position: %d", mCurrentChat, position);
				}
			}
		}
	}

	public synchronized boolean setMode(int mode) {
		// can only select a user if we have users
		if (mode == MODE_SELECT) {
			if (mFriendAdapter.getFriendCount() == 0) {
				return false;
			}
		}

		mMode = mode;
		return true;
	}

	public int getMode() {
		return mMode;
	}

	public void enableMenuItems(Friend friend) {
		boolean enabled = mMode != MODE_SELECT && mCurrentChat != null;
		SurespotLog.v(TAG, "enableMenuItems, enabled: %b", enabled);

		boolean isDeleted = false;
		if (friend != null) {
			isDeleted = friend.isDeleted();
		}

		if (mMenuItems != null) {
			for (MenuItem menuItem : mMenuItems) {
				if (menuItem.getItemId() != R.id.menu_purchase_voice) {

					// deleted users can't have images sent to them
					if (menuItem.getItemId() == R.id.menu_capture_image_bar || menuItem.getItemId() == R.id.menu_send_image_bar) {

						menuItem.setVisible(enabled && !isDeleted);
					}
					else {
						menuItem.setVisible(enabled);
					}
				}
				else {
					boolean voiceEnabled = SurespotApplication.getBillingController().hasVoiceMessaging();
					SurespotLog.d(TAG, "enableMenuItems, setting voice purchase menu visibility: %b", !voiceEnabled);
					menuItem.setVisible(!voiceEnabled);
				}
			}
		}
	}

	public void scrollToEnd(String to) {
		ChatFragment chatFragment = getChatFragment(to);
		if (chatFragment != null) {
			chatFragment.scrollToEnd();
		}

	}

	public void setImageUrl(String name, String url, String version, String iv, boolean hashed) {
		Friend friend = mFriendAdapter.getFriend(name);
		if (friend != null) {
			String oldUrl = friend.getImageUrl();
			if (!TextUtils.isEmpty(oldUrl)) {
				mNetworkController.removeCacheEntry(oldUrl);
			}

			friend.setImageUrl(url);
			friend.setImageIv(iv);
			friend.setImageVersion(version);
			friend.setImageHashed(hashed);
			saveFriends();
			mFriendAdapter.notifyDataSetChanged();
		}
	}

	public void setFriendAlias(String name, String data, String version, String iv, boolean hashed) {
		final Friend friend = mFriendAdapter.getFriend(name);
		if (friend != null) {
			friend.setAliasData(data);
			friend.setAliasIv(iv);
			friend.setAliasVersion(version);
			friend.setAliasHashed(hashed);

			new AsyncTask<Void, Void, String>() {

				@Override
				protected String doInBackground(Void... params) {
					String plainText = EncryptionController.symmetricDecrypt(friend.getAliasVersion(), IdentityController.getLoggedInUser(),
							friend.getAliasVersion(), friend.getAliasIv(), friend.isAliasHashed(), friend.getAliasData());

					return plainText;
				}

				protected void onPostExecute(String plainAlias) {

					friend.setAliasPlain(plainAlias);
					saveFriends();
					mChatPagerAdapter.sort();
					mChatPagerAdapter.notifyDataSetChanged();
					mIndicator.notifyDataSetChanged();
					mFriendAdapter.sort();
					mFriendAdapter.notifyDataSetChanged();
				}
			}.execute();
		}
	}

	public SurespotMessage getLiveMessage(SurespotMessage message) {
		String otherUser = message.getOtherUser();
		ChatAdapter chatAdapter = mChatAdapters.get(otherUser);
		if (chatAdapter != null) {
			return chatAdapter.getMessageByIv(message.getIv());
		}

		return null;
	}

	// called from GCM service
	public boolean addMessageExternal(final SurespotMessage message) {
		// might not be same user so check that to is the currently logged in user
		boolean sameUser = message.getTo().equals(mUsername);
		if (!sameUser) {
			return false;
		}
		else {
			final ChatAdapter chatAdapter = mChatAdapters.get(message.getFrom());
			if (chatAdapter == null) {
				return false;
			}
			else {

				// Handler handler = new Handler(Looper.getMainLooper());
				// handler.post(new Runnable() {
				//
				// @Override
				// public void run() {
				try {
					return applyControlMessages(chatAdapter, message, false, true, false);
				}
				catch (SurespotMessageSequenceException e) {
				}
				// }
				// });
				return false;
			}
		}
	}

	public String getAliasedName(String name) {
		Friend friend = mFriendAdapter.getFriend(name);
		if (friend != null) {
			return friend.getNameOrAlias();
		}
		return null;
	}

	private void removeFriendAlias(String name) {
		final Friend friend = mFriendAdapter.getFriend(name);
		if (friend != null) {
			friend.setAliasData(null);
			friend.setAliasIv(null);
			friend.setAliasVersion(null);
			friend.setAliasPlain(null);
			saveFriends();
			mChatPagerAdapter.sort();
			mChatPagerAdapter.notifyDataSetChanged();
			mIndicator.notifyDataSetChanged();
			mFriendAdapter.sort();
			mFriendAdapter.notifyDataSetChanged();
		}
	}
	
	public void removeFriendAlias(final String name, final IAsyncCallback<Boolean> iAsyncCallback) {
		setProgress("removeFriendAlias", true);
		mNetworkController.deleteFriendAlias(name, new AsyncHttpResponseHandler() {

			@Override
			public void onSuccess(int responseCode, String result) {
				removeFriendAlias(name);
				setProgress("removeFriendAlias", false);
				iAsyncCallback.handleResponse(true);
			}

			@Override
			public void onFailure(Throwable arg0, String arg1) {
				SurespotLog.w(TAG, arg0, "error removing friend alias: %s", arg1);
				setProgress("removeFriendAlias", false);
				iAsyncCallback.handleResponse(false);
			}
		});

	}
	
	private void removeFriendImage(String name) {
		final Friend friend = mFriendAdapter.getFriend(name);
		if (friend != null) {
			String oldUrl = friend.getImageUrl();
			if (!TextUtils.isEmpty(oldUrl)) {
				mNetworkController.removeCacheEntry(oldUrl);
			}
			friend.setImageIv(null);
			friend.setImageUrl(null);
			friend.setImageVersion(null);
			saveFriends();
			mChatPagerAdapter.sort();
			mChatPagerAdapter.notifyDataSetChanged();
			mIndicator.notifyDataSetChanged();
			mFriendAdapter.sort();
			mFriendAdapter.notifyDataSetChanged();
		}
	}
	
	public void removeFriendImage(final String name, final IAsyncCallback<Boolean> iAsyncCallback) {
		setProgress("removeFriendImage", true);
		mNetworkController.deleteFriendImage(name, new AsyncHttpResponseHandler() {

			@Override
			public void onSuccess(int responseCode, String result) {
				removeFriendImage(name);
				setProgress("removeFriendImage", false);
				iAsyncCallback.handleResponse(true);
			}

			@Override
			public void onFailure(Throwable arg0, String arg1) {
				SurespotLog.w(TAG, arg0, "error removing friend image: %s", arg1);
				setProgress("removeFriendImage", false);
				iAsyncCallback.handleResponse(false);
			}
		});

	}

	public void assignFriendAlias(final String name, String alias, final IAsyncCallback<Boolean> iAsyncCallback) {
		if (TextUtils.isEmpty(name) || TextUtils.isEmpty(alias)) return;
		
		setProgress("assignFriendAlias", true);
		final String version = IdentityController.getOurLatestVersion();
		String username = IdentityController.getLoggedInUser();

		byte[] iv = EncryptionController.getIv();
		final String cipherAlias = EncryptionController.symmetricEncrypt(version, username, version, alias, iv);
		final String ivString = new String(ChatUtils.base64EncodeNowrap(iv));

		mNetworkController.assignFriendAlias(name, version, cipherAlias, ivString, new AsyncHttpResponseHandler() {

			@Override
			public void onSuccess(int responseCode, String result) {
				setFriendAlias(name, cipherAlias, version, ivString, true);
				setProgress("assignFriendAlias", false);
				iAsyncCallback.handleResponse(true);
			}

			@Override
			public void onFailure(Throwable arg0, String arg1) {
				SurespotLog.w(TAG, arg0, "error assigning friend alias: %s", arg1);
				setProgress("assignFriendAlias", false);
				iAsyncCallback.handleResponse(false);
			}
		});
		
	}
}
