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
import ch.boye.httpclientandroidlib.client.cache.HttpCacheEntry;
import ch.boye.httpclientandroidlib.cookie.Cookie;
import ch.boye.httpclientandroidlib.impl.client.cache.HeapResource;
import ch.boye.httpclientandroidlib.impl.cookie.DateUtils;
import ch.boye.httpclientandroidlib.message.BasicHeader;
import ch.boye.httpclientandroidlib.message.BasicStatusLine;

import com.actionbarsherlock.view.MenuItem;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.StateController;
import com.twofours.surespot.StateController.FriendState;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.friends.FriendAdapter;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.MessageImageDownloader;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;
import com.viewpagerindicator.TitlePageIndicator;

public class ChatController {

	private static final String TAG = "ChatController";
	private static final int STATE_CONNECTING = 0;
	private static final int STATE_CONNECTED = 1;
	private static final int STATE_DISCONNECTED = 2;

	private static final int MAX_RETRIES = 5;

	private final StatusLine mImageStatusLine = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "");
	private SocketIO socket;
	private int mRetries = 0;
	private Timer mBackgroundTimer;

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

	private IAsyncCallback<Void> mCallback401;
	private IAsyncCallback<Boolean> mProgressCallback;
	private IAsyncCallback<Void> mSendIntentCallback;
	private IAsyncCallback<Friend> mTabShowingCallback;

	public ChatController(Context context, NetworkController networkController, FragmentManager fm, IAsyncCallback<Void> callback401,
			IAsyncCallback<Boolean> progressCallback, IAsyncCallback<Void> sendIntentCallback, IAsyncCallback<Friend> tabShowingCallback) {
		SurespotLog.v(TAG, "constructor: " + this);
		mContext = context;
		mNetworkController = networkController;

		mCallback401 = callback401;
		mProgressCallback = progressCallback;
		mSendIntentCallback = sendIntentCallback;

		mTabShowingCallback = tabShowingCallback;
		mEarliestMessage = new HashMap<String, Integer>();
		mChatAdapters = new HashMap<String, ChatAdapter>();
		mFriendAdapter = new FriendAdapter(mContext);
		mPreConnectIds = new HashMap<String, ChatController.LatestIdPair>();
		loadState();

		mFragmentManager = fm;
		mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		setOnWifi();

		// mViewPager.setOffscreenPageLimit(2);

		mSocketCallback = new IOCallback() {

			@Override
			public void onMessage(JSONObject json, IOAcknowledge ack) {
				try {
					SurespotLog.v(TAG, "JSON Server said:" + json.toString(2));

				}
				catch (JSONException e) {
					SurespotLog.w(TAG, "onMessage", e);
				}
			}

			@Override
			public void onMessage(String data, IOAcknowledge ack) {
				SurespotLog.v(TAG, "Server said: " + data);
			}

			@Override
			public synchronized void onError(SocketIOException socketIOException) {
				// socket.io returns 403 for can't login
				if (socketIOException.getHttpStatus() == 403) {
					socket = null;
					logout();
					mCallback401.handleResponse(null);
					return;
				}

				SurespotLog.w(TAG, "an Error occured, attempting reconnect with exponential backoff, retries: " + mRetries,
						socketIOException);

				setOnWifi();
				// kick off another task
				if (mRetries < MAX_RETRIES) {

					if (mReconnectTask != null) {
						mReconnectTask.cancel();
					}

					int timerInterval = (int) (Math.pow(2, mRetries++) * 1000);
					SurespotLog.v(TAG, "Starting another task in: " + timerInterval);

					mReconnectTask = new ReconnectTask();
					if (mBackgroundTimer == null) {
						mBackgroundTimer = new Timer("backgroundTimer");
					}
					mBackgroundTimer.schedule(mReconnectTask, timerInterval);
				}
				else {
					// TODO tell user
					SurespotLog.w(TAG, "Socket.io reconnect retries exhausted, giving up.");
					// TODO more persistent error

					// Utils.makeLongToast(mContext, "could not connect to the server");

					mCallback401.handleResponse(null);
				}
			}

			@Override
			public void onDisconnect() {
				SurespotLog.v(TAG, "Connection terminated.");
				// socket = null;
			}

			@Override
			public void onConnect() {
				SurespotLog.v(TAG, "socket.io connection established");
				setState(STATE_CONNECTED);
				setOnWifi();
				mRetries = 0;

				if (mBackgroundTimer != null) {
					mBackgroundTimer.cancel();
					mBackgroundTimer = null;
				}

				if (mReconnectTask != null && mReconnectTask.cancel()) {
					SurespotLog.v(TAG, "Cancelled reconnect timer.");
					mReconnectTask = null;
				}

				connected();

			}

			@Override
			public void on(String event, IOAcknowledge ack, Object... args) {

				SurespotLog.v(TAG, "Server triggered event '" + event + "'");
				if (event.equals("control")) {
					try {
						SurespotControlMessage message = SurespotControlMessage.toSurespotControlMessage(new JSONObject((String) args[0]));
						handleControlMessage(null, message, true);
					}
					catch (JSONException e) {
						SurespotLog.w(TAG, "on control", e);
					}
				}
				else if (event.equals("message")) {

					// TODO check who from
					try {
						JSONObject jsonMessage = new JSONObject((String) args[0]);
						SurespotLog.v(TAG, "received message: " + jsonMessage.toString());
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
										handleControlMessage(null, dMessage, true);
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
				else if (event.equals("messageError")) {
					// TODO check who from
					try {
						JSONObject jsonMessage = (JSONObject) args[0];
						SurespotLog.v(TAG, "received messageError: " + jsonMessage.toString());
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
				SurespotLog.v(TAG, "Connectivity Action");
				ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
				if (networkInfo != null) {
					SurespotLog.v(TAG, "isconnected: " + networkInfo.isConnected());
					SurespotLog.v(TAG, "failover: " + networkInfo.isFailover());
					SurespotLog.v(TAG, "reason: " + networkInfo.getReason());
					SurespotLog.v(TAG, "type: " + networkInfo.getTypeName());

					// if it's not a failover and wifi is now active then initiate reconnect
					if (!networkInfo.isFailover() && (networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected())) {
						synchronized (ChatController.this) {
							// if we're not connecting, connect
							if (getState() != STATE_CONNECTING && !mOnWifi) {

								SurespotLog.v(TAG, "Network switch, Reconnecting...");

								setState(STATE_CONNECTING);

								mOnWifi = true;
								disconnect();
								connect();
							}
						}
					}
				}
				else {
					SurespotLog.v(TAG, "networkinfo null");
				}
			}
		};

	}

	// this has to be done outside of the contructor as it creates fragments, which need chat controller instance
	public void init(ViewPager viewPager, TitlePageIndicator pageIndicator, ArrayList<MenuItem> menuItems) {
		mChatPagerAdapter = new ChatPagerAdapter(mFragmentManager);
		mMenuItems = menuItems;

		mViewPager = viewPager;
		mViewPager.setAdapter(mChatPagerAdapter);
		mIndicator = pageIndicator;
		mIndicator.setViewPager(mViewPager);

		mIndicator.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				if (mChatPagerAdapter != null) {
					SurespotLog.v(TAG, "onPageSelected, position: " + position);
					String name = mChatPagerAdapter.getChatName(position);
					setCurrentChat(name);
				}

			}
		});
		mChatPagerAdapter.setChatNames(mFriendAdapter.getActiveChats());
		onResume();
	}

	private void connect() {
		SurespotLog.v(TAG, "connect, socket: " + socket + ", connected: " + (socket != null ? socket.isConnected() : false) + ", state: "
				+ mConnectionState);

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
			SurespotLog.v(TAG, "setting preconnectids for: " + username + ", latest message id:  " + idPair.latestMessageId
					+ ", latestcontrolid: " + idPair.latestControlMessageId);
			mPreConnectIds.put(username, idPair);

		}

		Cookie cookie = IdentityController.getCookie();

		if (cookie == null) {
			return;
		}

		try {
			HashMap<String, String> headers = new HashMap<String, String>();
			headers.put("cookie", cookie.getName() + "=" + cookie.getValue());
			socket = new SocketIO(SurespotConfiguration.getBaseUrl(), headers);
			socket.connect(mSocketCallback);
		}
		catch (Exception e) {

			SurespotLog.w(TAG, "connect", e);
		}

	}

	private void disconnect() {
		SurespotLog.v(TAG, "disconnect.");
		setState(STATE_DISCONNECTED);

		if (socket != null) {
			socket.disconnect();
			socket = null;
		}

	}

	private void connected() {

		getFriendsAndIds();
		resendMessages();
		// MainActivity.THREAD_POOL_EXECUTOR.execute(new UpdateDataTask());

	}

	private class UpdateDataTask implements Runnable {

		@Override
		public void run() {
			getFriendsAndIds();

		}

	}

	private void resendMessages() {
		// get the resend messages
		SurespotMessage[] resendMessages = getResendMessages();

		for (int i = 0; i < resendMessages.length; i++) {
			SurespotMessage message = resendMessages[i];
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

			SurespotLog.v(TAG, "setting resendId, otheruser: " + otherUser + ", id: " + lastMessageID);
			message.setResendId(lastMessageID);

			enqueueMessage(message);
			sendMessages();

		}
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
				SurespotLog.v(TAG, "Received and removed message from resend  buffer: " + message);
			}
		}
	}

	private SurespotMessage[] getResendMessages() {
		SurespotMessage[] messages = mResendBuffer.toArray(new SurespotMessage[0]);
		mResendBuffer.clear();
		return messages;

	}

	private void enqueueMessage(SurespotMessage message) {
		mSendBuffer.add(message);
	}

	private synchronized void sendMessages() {
		if (mBackgroundTimer == null) {
			mBackgroundTimer = new Timer("backgroundTimer");
		}

		SurespotLog.v(TAG, "Sending: " + mSendBuffer.size() + " messages.");

		Iterator<SurespotMessage> iterator = mSendBuffer.iterator();
		while (iterator.hasNext()) {
			SurespotMessage message = iterator.next();
			if (isMessageReadyToSend(message)) {
				iterator.remove();
				sendMessage(message);
			}
			else {
				break;
			}
		}

	}

	private boolean isMessageReadyToSend(SurespotMessage message) {
		return !TextUtils.isEmpty(message.getData()) && !TextUtils.isEmpty(message.getFromVersion())
				&& !TextUtils.isEmpty(message.getToVersion());
	}

	private void sendMessage(final SurespotMessage message) {
		SurespotLog.v(TAG, "sendmessage adding message to ResendBuffer, text: " + message.getPlainData() + ", iv: " + message.getIv());

		mResendBuffer.add(message);
		if (getState() == STATE_CONNECTED) {
			// TODO handle different mime types
			SurespotLog.v(TAG, "sendmessage, socket: " + socket);
			JSONObject json = message.toJSONObject();
			SurespotLog.v(TAG, "sendmessage, json: " + json);
			String s = json.toString();
			SurespotLog.v(TAG, "sendmessage, message string: " + s);

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
			SurespotLog.v(TAG, "Reconnect task run.");
			connect();
		}
	}

	private void handleMessage(final SurespotMessage message) {
		SurespotLog.v(TAG, "handleMessage %s", message);
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
								message.getTheirVersion(), message.getIv(), message.getData());

						// substitute emoji
						EmojiParser parser = EmojiParser.getInstance();
						message.setPlainData(parser.addEmojiSpans(plainText));
					}

					else {
						// if it's an image that i sent
						// get the local message
						if (ChatUtils.isMyMessage(message)) {
							handleCachedImage(chatAdapter, message);
						}
						else {

							InputStream imageStream = MainActivity.getNetworkController().getFileStream(MainActivity.getContext(),
									message.getData());

							Bitmap bitmap = null;
							PipedOutputStream out = new PipedOutputStream();
							PipedInputStream inputStream;
							try {
								inputStream = new PipedInputStream(out);

								EncryptionController.runDecryptTask(message.getOurVersion(), message.getOtherUser(),
										message.getTheirVersion(), message.getIv(), new BufferedInputStream(imageStream), out);

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
								message.setHeight(bitmap.getHeight());
								MessageImageDownloader.addBitmapToCache(message.getData(), bitmap);
							}
						}

					}
					return null;
				}

				protected void onPostExecute(Void result) {
					try {
						boolean added = chatAdapter.addOrUpdateMessage(message, true, true, true);
						scrollToEnd(otherUser);

						Friend friend = mFriendAdapter.getFriend(otherUser);
						if (friend != null) {
							int messageId = message.getId();

							// always update the available id
							friend.setAvailableMessageId(messageId);

							// if the chat is showing or we sent the message update the last viewed id
							if (otherUser.equals(mCurrentChat)) {

								friend.setLastViewedMessageId(messageId);
							}
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
						SurespotLog.v(TAG, "handleMessage: %s", e.getMessage());
						getLatestMessagesAndControls(otherUser, e.getMessageId());
					}
				};

			}.execute();

		}
		else {
			Friend friend = mFriendAdapter.getFriend(otherUser);
			if (friend != null) {
				int messageId = message.getId();

				// always update the available id
				friend.setAvailableMessageId(messageId);

				mFriendAdapter.sort();
				mFriendAdapter.notifyDataSetChanged();
			}
		}

	}

	// add entry to http cache for image we sent so we don't download it again
	private void handleCachedImage(ChatAdapter chatAdapter, SurespotMessage message) {
		SurespotLog.v(TAG, "handleCachedImage");
		SurespotMessage localMessage = chatAdapter.getMessageByIv(message.getIv());

		// if the data is different we haven't updated the url to point externally
		if (localMessage != null && localMessage.getId() == null && !localMessage.getData().equals(message.getData())) {
			// add the remote cache entry for the new url

			String localUri = localMessage.getData();

			FileInputStream fis;
			try {
				fis = new FileInputStream(new File(new URI(localUri)));
				byte[] imageData = Utils.inputStreamToBytes(fis);

				String remoteUri = message.getData();
				HeapResource resource = new HeapResource(imageData);
				Date date = new Date();
				String sDate = DateUtils.formatDate(date);

				Header[] cacheHeaders = new Header[3];

				// create fake cache entry
				cacheHeaders[0] = new BasicHeader("Last-Modified", sDate);
				cacheHeaders[1] = new BasicHeader("Cache-Control", "public, max-age=31557600");
				cacheHeaders[2] = new BasicHeader("Date", sDate);

				HttpCacheEntry cacheEntry = new HttpCacheEntry(date, date, mImageStatusLine, cacheHeaders, resource);

				SurespotLog.v(TAG, "creating http cache entry for: %s", remoteUri);
				mNetworkController.addCacheEntry(remoteUri, cacheEntry);

				// update image cache
				MessageImageDownloader.copyAndRemoveCacheEntry(localUri, remoteUri);

				// update message to point to real location
				localMessage.setData(remoteUri);

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
				SurespotLog.v(TAG, "handleCachedImage deleting local file: %s", localUri);

				File file = new File(new URI(localUri));
				file.delete();
			}
			catch (URISyntaxException e) {
				SurespotLog.w(TAG, e, "handleMessage");
			}

		}
	}

	// message handling shiznit

	void loadEarlierMessages(final String username, final IAsyncCallback<Void> callback) {

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

				SurespotLog.v(TAG, username + ": asking server for messages before messageId: " + firstMessageId);
				// final int fMessageId = firstMessageId;
				final ChatAdapter chatAdapter = mChatAdapters.get(username);

				mNetworkController.getEarlierMessages(username, firstMessageId, new JsonHttpResponseHandler() {
					@Override
					public void onSuccess(final JSONArray jsonArray) {

						// if (getActivity() != null) {
						SurespotMessage message = null;

						try {
							for (int i = jsonArray.length() - 1; i >= 0; i--) {
								JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
								message = SurespotMessage.toSurespotMessage(jsonMessage);
								chatAdapter.insertMessage(message, false);
							}
						}
						catch (JSONException e) {
							SurespotLog.e(TAG, e, "%s: error creating chat message", username);
						}

						SurespotLog.v(TAG, "%s: loaded: %d earlier messages from the server.", username, jsonArray.length());
						if (message != null) {
							mEarliestMessage.put(username, message.getId());
							// chatAdapter.notifyDataSetChanged();
						}

						// chatAdapter.setLoading(false);
						callback.handleResponse(null);

					}

					@Override
					public void onFailure(Throwable error, String content) {
						SurespotLog.w(TAG, error, "%s: getEarlierMessages", username);
						// chatAdapter.setLoading(false);
						callback.handleResponse(null);
					}
				});
			}
			else {
				SurespotLog.v(TAG, "%s: getEarlierMessages: no more messages.", username);
				callback.handleResponse(null);
				// ChatFragment.this.mNoEarlierMessages = true;
			}

		}
	}

	private void getLatestIds() {
		SurespotLog.v(TAG, "getLatestIds");
		// setMessagesLoading(true);

		mNetworkController.getLatestIds(mLatestUserControlId, new JsonHttpResponseHandler() {

			@Override
			public void onSuccess(int statusCode, final JSONObject jsonResponse) {
				SurespotLog.v(TAG, "getlatestIds success, response: %s, statusCode: %d", jsonResponse, statusCode);
				JSONArray conversationIds = jsonResponse.optJSONArray("conversationIds");

				Friend friend = null;
				if (conversationIds != null) {
					for (int i = 0; i < conversationIds.length(); i++) {
						try {
							JSONObject jsonObject = conversationIds.getJSONObject(i);
							String spot = jsonObject.getString("conversation");
							Integer availableId = jsonObject.getInt("id");
							String user = ChatUtils.getOtherSpotUser(spot, IdentityController.getLoggedInUser());
							// update available ids
							friend = mFriendAdapter.getFriend(user);
							if (friend != null) {
								friend.setAvailableMessageId(availableId);
							}

						}
						catch (JSONException e) {
							SurespotLog.w(TAG, "getlatestIds", e);
						}
					}
				}

				JSONArray controlIds = jsonResponse.optJSONArray("controlIds");
				if (controlIds != null) {
					for (int i = 0; i < controlIds.length(); i++) {
						try {
							JSONObject jsonObject = controlIds.getJSONObject(i);
							String spot = jsonObject.getString("conversation");
							Integer availableId = jsonObject.getInt("id");
							String user = ChatUtils.getOtherSpotUser(spot, IdentityController.getLoggedInUser());
							// update available ids
							friend = mFriendAdapter.getFriend(user);
							if (friend != null) {
								friend.setAvailableMessageControlId(availableId);
							}
						}
						catch (JSONException e) {
							SurespotLog.w(TAG, "getlatestIds", e);
						}
					}
				}

				JSONArray userControlMessages = jsonResponse.optJSONArray("userControlMessages");
				if (userControlMessages != null) {
					handleControlMessages(IdentityController.getLoggedInUser(), userControlMessages);
				}

				if (friend != null) {

					mFriendAdapter.notifyDataSetChanged();
				}

				getLatestMessagesAndControls();

			}

			@Override
			public void onFailure(Throwable error, String content) {
				// setMessagesLoading(false);
				Utils.makeToast(mContext, "loading latest messages failed: " + content);
				setProgress(null, false);
			}
		});

	}

	private class LatestIdPair {
		public int latestMessageId;
		public int latestControlMessageId;
	}

	private void getLatestMessagesAndControls() {
		for (Entry<String, ChatAdapter> entry : mChatAdapters.entrySet()) {
			getLatestMessagesAndControls(entry.getKey());
		}

		// done with "global" updates
		setProgress(null, false);
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

	private void getLatestMessagesAndControls(final String username) {

		LatestIdPair ids = getLatestIds(username);

		getLatestMessagesAndControls(username, ids.latestMessageId, ids.latestControlMessageId);

	}

	private void getLatestMessagesAndControls(String username, int messageId) {
		getLatestMessagesAndControls(username, messageId, -1);

	}

	private void getLatestMessagesAndControls(final String username, int fetchMessageId, int fetchControlMessageId) {
		SurespotLog.v(TAG, "getLatestMessagesAndControls: name %s, fetchMessageId: %d, fetchControlMessageId: %d", username,
				fetchMessageId, fetchControlMessageId);
		if (fetchMessageId > -1 || fetchControlMessageId > -1) {
			setProgress(username, true);

			// mChatAdapters.get(username).setLoading(true);
			mNetworkController.getMessageData(username, fetchMessageId, fetchControlMessageId, new JsonHttpResponseHandler() {
				@Override
				public void onSuccess(int statusCode, JSONObject response) {

					JSONArray controlMessages = response.optJSONArray("controlMessages");
					if (controlMessages != null) {
						handleControlMessages(username, controlMessages);
					}
					String messages = response.optString("messages", null);
					if (messages != null) {
						handleMessages(username, messages);
					}
					else {
						if (username.equals(mCurrentChat)) {
							ChatFragment chatFragment = getChatFragment(username);
							if (chatFragment != null) {
								chatFragment.scrollToState();
								// chatFragment.requestFocus();

							}
						}

						setProgress(username, false);
					}

				}
			});
		}
		else {
			if (username.equals(mCurrentChat)) {
				ChatFragment chatFragment = getChatFragment(username);
				if (chatFragment != null) {
					chatFragment.scrollToState();
					// chatFragment.requestFocus();

				}
			}
		}
	}

	private void handleControlMessages(String username, JSONArray jsonArray) {
		SurespotLog.v(TAG, "%s: handleControlMessages", username);
		final ChatAdapter chatAdapter = mChatAdapters.get(username);

		SurespotControlMessage message = null;
		boolean messageActivity = false;
		boolean userActivity = false;
		for (int i = 0; i < jsonArray.length(); i++) {
			try {
				JSONObject jsonMessage = new JSONObject(jsonArray.getString(i));
				message = SurespotControlMessage.toSurespotControlMessage(jsonMessage);
				handleControlMessage(chatAdapter, message, false);
				// if it's a system message from another user then check version
				if (message.getType().equals("user")) {
					userActivity = true;
				}
				else if (message.getType().equals("message")) {
					messageActivity = true;
				}

			}
			catch (JSONException e) {
				SurespotLog.w(TAG, e, "%s: error creating chat message", username);
			}

		}

		if (message != null) {

			SurespotLog.v(TAG, username + "%s: loaded: %d latest control messages from the server.", username, jsonArray.length());

			if (messageActivity || userActivity) {
				Friend friend = mFriendAdapter.getFriend(username);
				if (friend != null) {

					if (messageActivity) {

						friend.setLastReceivedMessageControlId(message.getId());
						friend.setAvailableMessageControlId(message.getId());
						mFriendAdapter.notifyDataSetChanged();
						chatAdapter.sort();
						chatAdapter.notifyDataSetChanged();
					}

					if (userActivity) {
						friend.setLastReceivedUserControlId(message.getId());
						mFriendAdapter.notifyDataSetChanged();
					}
				}
			}
		}

		// chatAdapter.setLoading(false);
	}

	private void handleControlMessage(ChatAdapter chatAdapter, SurespotControlMessage message, boolean notify) {
		// if it's a system message from another user then check version
		if (message.getType().equals("user")) {
			handleUserControlMessage(message, notify);
		}
		else if (message.getType().equals("message")) {
			String otherUser = ChatUtils.getOtherSpotUser(message.getData(), IdentityController.getLoggedInUser());
			Friend friend = mFriendAdapter.getFriend(otherUser);

			if (chatAdapter == null) {
				chatAdapter = mChatAdapters.get(otherUser);
			}

			if (chatAdapter != null) {
				boolean controlFromMe = message.getFrom().equals(IdentityController.getLoggedInUser());
				if (message.getAction().equals("delete")) {
					int messageId = Integer.parseInt(message.getMoreData());
					SurespotMessage dMessage = chatAdapter.getMessageById(messageId);

					if (dMessage != null) {
						deleteMessageInternal(chatAdapter, dMessage, controlFromMe);
					}
				}
				else {
					if (message.getAction().equals("deleteAll")) {
						if (controlFromMe) {
							chatAdapter.deleteAllMessages();
						}
						else {
							chatAdapter.deleteTheirMessages();
						}
					}
					else {
						if (message.getAction().equals("shareable") || message.getAction().equals("notshareable")) {
							int messageId = Integer.parseInt(message.getMoreData());
							SurespotMessage dMessage = chatAdapter.getMessageById(messageId);
							if (dMessage != null) {
								SurespotLog.v(TAG, "setting message " + message.getAction());
								dMessage.setShareable(message.getAction().equals("shareable") ? true : false);
							}
						}
					}
				}
			}

			if (notify) {
				if (friend != null) {
					friend.setLastReceivedMessageControlId(message.getId());
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
		else if (message.getAction().equals("invited")) {
			user = message.getData();
			mFriendAdapter.addFriendInvited(user);
		}
		else if (message.getAction().equals("added")) {
			user = message.getData();
			mFriendAdapter.addNewFriend(user);
			ChatAdapter chatAdapter = mChatAdapters.get(user);

			if (chatAdapter != null) {
				chatAdapter.userDeleted(false);
			}
		}
		else if (message.getAction().equals("invite")) {
			user = message.getData();
			mFriendAdapter.addFriendInviter(user);
		}
		else if (message.getAction().equals("ignore")) {
			String friendName = message.getData();
			Friend friend = mFriendAdapter.getFriend(friendName);

			// if they're not deleted, remove them
			if (friend != null && !friend.isDeleted()) {
				mFriendAdapter.removeFriend(friendName);
			}
			else {
				// they've been deleted, just remove the invite flags
				friend.setInviter(false);
				friend.setInvited(false);

			}

		}
		else if (message.getAction().equals("delete")) {
			String friendName = message.getData();
			Friend friend = mFriendAdapter.getFriend(friendName);

			// if it was just a delete of an invite
			if (friend.isInviter() || friend.isInvited()) {

				// if they're not deleted, remove them
				if (friend != null && !friend.isDeleted()) {
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
				user = message.getData();
				handleDeleteUser(user, message.getMoreData());
			}
		}

		if (notify) {

			Friend friend = mFriendAdapter.getFriend(user);
			if (friend != null) {
				friend.setLastReceivedUserControlId(message.getId());
			}
			mFriendAdapter.notifyDataSetChanged();

		}

	}

	private void handleDeleteUser(String deletedUser, String deleter) {
		String username = IdentityController.getLoggedInUser();

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
				chatAdapter.userDeleted(true);
				chatAdapter.notifyDataSetChanged();
			}

			// and mark you as deleted until I want to delete you
			Friend friend = mFriendAdapter.setFriendDeleted(deletedUser);

			// force the controls to update
			if (friend != null && mCurrentChat.equals(deletedUser)) {
				mTabShowingCallback.handleResponse(friend);
			}
		}
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
			if (dMessage.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
				mNetworkController.purgeCacheUrl(dMessage.getData());
			}

			boolean myMessage = dMessage.getFrom().equals(IdentityController.getLoggedInUser());

			// if i sent the delete, or it's not my message then delete it
			// (if someone else deleted my message we don't care)
			if (initiatedByMe || !myMessage) {
				SurespotLog.v(TAG, "deleting message");
				chatAdapter.deleteMessageById(dMessage.getId());
			}
		}
	}

	private void handleMessages(String username, String jsonMessageString) {
		SurespotLog.v(TAG, "%s: handleMessages", username);
		final ChatAdapter chatAdapter = mChatAdapters.get(username);
		int sentByMeCount = 0;

		SurespotMessage lastMessage = null;
		try {
			JSONArray jsonUM = new JSONArray(jsonMessageString);
			SurespotLog.v(TAG, "%s: loaded: %d messages from the server: %s", username, jsonUM.length(), jsonMessageString);
			for (int i = 0; i < jsonUM.length(); i++) {

				lastMessage = SurespotMessage.toSurespotMessage(new JSONObject(jsonUM.getString(i)));
				boolean myMessage = lastMessage.getFrom().equals(IdentityController.getLoggedInUser());

				if (myMessage && lastMessage.getMimeType().equals(SurespotConstants.MimeTypes.IMAGE)) {
					handleCachedImage(chatAdapter, lastMessage);
				}

				boolean added = chatAdapter.addOrUpdateMessage(lastMessage, false, false, false);
				mResendBuffer.remove(lastMessage);
				if (added && myMessage) {
					sentByMeCount++;
				}
			}
		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "jsonStringsToMessages", e);

		}
		catch (SurespotMessageSequenceException e) {
			// shouldn't happen
			SurespotLog.w(TAG, "handleMessages", e);
			// getLatestMessagesAndControls(username, e.getMessageId(), -1);
			setProgress(username, false);
			return;
		}

		boolean newMessage = false;

		if (lastMessage != null) {
			Friend friend = mFriendAdapter.getFriend(username);

			int availableId = lastMessage.getId();
			friend.setAvailableMessageId(availableId);

			int lastViewedId = friend.getLastViewedMessageId();

			// how many new messages total are there
			int delta = availableId - lastViewedId;

			// if all the new messages are mine then i've viewed them all
			if (sentByMeCount == delta) {
				friend.setLastViewedMessageId(availableId);
			}
			else {
				// set the last viewed id to the difference caused by their messages
				friend.setLastViewedMessageId(availableId - (delta - sentByMeCount));
			}
			newMessage = friend.isMessageActivity();

			chatAdapter.sort();
			chatAdapter.notifyDataSetChanged();
			chatAdapter.doneCheckingSequence();
			mFriendAdapter.notifyDataSetChanged();

		}

		if (username.equals(mCurrentChat)) {
			ChatFragment chatFragment = getChatFragment(username);
			if (chatFragment != null) {

				if (newMessage) {
					chatFragment.scrollToEnd();
				}
				else {
					chatFragment.scrollToState();
				}
				// chatFragment.requestFocus();

			}
		}

		setProgress(username, false);
	}

	// tell the chat adapters we've loaded their data (even if they didn't have any)
	public void setMessagesLoading(boolean loading) {
		for (ChatAdapter ca : mChatAdapters.values()) {
			ca.setLoading(loading);
		}
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

	private synchronized void loadMessages(String username) {
		SurespotLog.v(TAG, "loadMessages: " + username);
		String spot = ChatUtils.getSpot(IdentityController.getLoggedInUser(), username);
		ChatAdapter chatAdapter = mChatAdapters.get(username);
		chatAdapter.setMessages(SurespotApplication.getStateController().loadMessages(spot));
		// ChatFragment chatFragment = getChatFragment(username);
	}

	private synchronized void saveMessages() {
		// save last 30? messages
		SurespotLog.v(TAG, "saveMessages");
		if (IdentityController.getLoggedInUser() != null) {
			for (Entry<String, ChatAdapter> entry : mChatAdapters.entrySet()) {
				String them = entry.getKey();
				String spot = ChatUtils.getSpot(IdentityController.getLoggedInUser(), them);
				SurespotApplication.getStateController().saveMessages(spot, entry.getValue().getMessages(),
						entry.getValue().getCurrentScrollPositionId());
			}
		}
	}

	private synchronized void saveMessages(String username) {
		// save last 30? messages
		SurespotLog.v(TAG, "saveMessages, username:" + username);
		ChatAdapter chatAdapter = mChatAdapters.get(username);

		if (chatAdapter != null) {
			SurespotApplication.getStateController().saveMessages(ChatUtils.getSpot(IdentityController.getLoggedInUser(), username),
					chatAdapter.getMessages(), chatAdapter.getCurrentScrollPositionId());
		}

	}

	private void saveUnsentMessages() {
		mResendBuffer.addAll(mSendBuffer);
		// SurespotLog.v(TAG, "saving: " + mResendBuffer.size() + " unsent messages.");
		SurespotApplication.getStateController().saveUnsentMessages(mResendBuffer);
	}

	private void loadUnsentMessages() {
		Iterator<SurespotMessage> iterator = SurespotApplication.getStateController().loadUnsentMessages().iterator();
		while (iterator.hasNext()) {
			mResendBuffer.add(iterator.next());
		}
		// SurespotLog.v(TAG, "loaded: " + mSendBuffer.size() + " unsent messages.");
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

		SurespotLog.v(TAG, "saveState");

		if (username == null) {
			saveUnsentMessages();
			saveMessages();
			SurespotLog.v(TAG, "saving last chat: %s", mCurrentChat);
			Utils.putSharedPrefsString(mContext, SurespotConstants.PrefNames.LAST_CHAT, mCurrentChat);
			SurespotApplication.getStateController().saveFriends(mLatestUserControlId, mFriendAdapter.getFriends());
		}
		else {
			saveMessages(username);
		}
	}

	private void loadState() {
		SurespotLog.v(TAG, "loadState");
		FriendState fs = SurespotApplication.getStateController().loadFriends();

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
		SurespotLog.v(TAG, "setProgress, isInProgress(): %b", progress);

		if (mProgressCallback != null) {
			mProgressCallback.handleResponse(progress);
		}
	}

	public synchronized boolean isInProgress() {
		return mGlobalProgress || !mChatProgress.isEmpty();
	}

	public synchronized void onResume() {
		SurespotLog.v(TAG, "onResume, mPaused: %b", mPaused);
		if (mPaused) {
			mPaused = false;

			setProgress(null, true);
			// getFriendsAndIds();
			connect();
			mContext.registerReceiver(mConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		}
	}

	public synchronized void onPause() {
		SurespotLog.v(TAG, "onResume, mPaused: %b", mPaused);
		if (!mPaused) {
			mPaused = true;
			saveState(null);
		}

		disconnect();

		if (mBackgroundTimer != null) {
			mBackgroundTimer.cancel();
			mBackgroundTimer = null;
		}
		if (mReconnectTask != null) {
			boolean cancel = mReconnectTask.cancel();
			mReconnectTask = null;
			SurespotLog.v(TAG, "Cancelled reconnect task: " + cancel);
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
				chatAdapter.userDeleted(friend.isDeleted());
			}

			SurespotLog.v(TAG, "getChatAdapter created chat adapter for: %s", username);
			mChatAdapters.put(username, chatAdapter);

			// load savedmessages
			loadMessages(username);

			LatestIdPair idPair = new LatestIdPair();
			idPair.latestMessageId = getLatestMessageId(username);
			idPair.latestControlMessageId = getLatestMessageControlId(username);
			SurespotLog.v(TAG, "setting preconnectids for: %s, latest message id: %d, latestcontrolid: %d", username,
					idPair.latestMessageId, idPair.latestControlMessageId);
			mPreConnectIds.put(username, idPair);

			// get latest messages from server
			getLatestMessagesAndControls(username);

		}
		return chatAdapter;
	}

	public void destroyChatAdapter(String username) {
		SurespotLog.v(TAG, "destroying chat adapter for: %s", username);
		saveState(username);
		mChatAdapters.remove(username);
	}

	public synchronized void setCurrentChat(final String username) {

		SurespotLog.v(TAG, "setCurrentChat: %s", username);
		String loggedInUser = IdentityController.getLoggedInUser();
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
			mChatPagerAdapter.addChatName(username);
			friend.setChatActive(true);
			friend.setLastViewedMessageId(friend.getAvailableMessageId());
			mFriendAdapter.sort();
			mFriendAdapter.notifyDataSetChanged();

			// cancel associated notifications
			mNotificationManager.cancel(ChatUtils.getSpot(loggedInUser, username),
					SurespotConstants.IntentRequestCodes.NEW_MESSAGE_NOTIFICATION);
			int wantedPosition = mChatPagerAdapter.getChatFragmentPosition(username);

			if (wantedPosition != mViewPager.getCurrentItem()) {
				mViewPager.setCurrentItem(wantedPosition, true);
			}

			// ChatFragment chatFragment = getChatFragment(username);
			// if (chatFragment != null) {
			// chatFragment.requestFocus();
			// }
			//
			if (mMode == MODE_SELECT) {
				mSendIntentCallback.handleResponse(null);
				setMode(MODE_NORMAL);
			}

		}
		else {
			mCurrentChat = null;
			mViewPager.setCurrentItem(0, true);
			mNotificationManager.cancel(loggedInUser, SurespotConstants.IntentRequestCodes.INVITE_REQUEST_NOTIFICATION);
			mNotificationManager.cancel(loggedInUser, SurespotConstants.IntentRequestCodes.INVITE_RESPONSE_NOTIFICATION);

		}
		// disable menu items
		enableMenuItems();

	}

	private ChatFragment getChatFragment(String username) {
		String fragmentTag = Utils.makePagerFragmentName(mViewPager.getId(), username.hashCode());
		SurespotLog.v(TAG, "looking for fragment: %s", fragmentTag);
		ChatFragment chatFragment = (ChatFragment) mFragmentManager.findFragmentByTag(fragmentTag);
		SurespotLog.v(TAG, "fragment: %s", chatFragment);
		return chatFragment;
	}

	public void sendMessage(final String plainText, final String mimeType) {
		if (plainText.length() > 0) {

			// display the message immediately
			final byte[] iv = EncryptionController.getIv();

			// build a message without the encryption values set as they could take a while

			final SurespotMessage chatMessage = ChatUtils.buildPlainMessage(mCurrentChat, mimeType, EmojiParser.getInstance()
					.addEmojiSpans(plainText), new String(ChatUtils.base64EncodeNowrap(iv)));
			ChatAdapter chatAdapter = mChatAdapters.get(mCurrentChat);

			try {

				chatAdapter.addOrUpdateMessage(chatMessage, false, true, true);
				enqueueMessage(chatMessage);
			}
			catch (SurespotMessageSequenceException e) {
				// not gonna happen
				SurespotLog.v(TAG, "sendMessage", e);
			}

			// do encryption in background
			new AsyncTask<Void, Void, SurespotMessage>() {

				@Override
				protected SurespotMessage doInBackground(Void... arg0) {
					String ourLatestVersion = IdentityController.getOurLatestVersion();
					String theirLatestVersion = IdentityController.getTheirLatestVersion(mCurrentChat);

					String result = EncryptionController
							.symmetricEncrypt(ourLatestVersion, mCurrentChat, theirLatestVersion, plainText, iv);

					if (result != null) {
						chatMessage.setData(result);
						chatMessage.setFromVersion(ourLatestVersion);
						chatMessage.setToVersion(theirLatestVersion);

						SurespotLog.v(TAG, "sending message to chat controller, text: %s, iv: %s", chatMessage.getPlainData(),
								chatMessage.getIv());

					}

					sendMessages();
					return null;
				}

			}.execute();
		}

	}

	void addMessage(SurespotMessage message) {
		ChatAdapter chatAdapter = mChatAdapters.get(message.getTo());

		try {

			chatAdapter.addOrUpdateMessage(message, false, true, true);
			scrollToEnd(message.getTo());
			saveState(message.getTo());
		}
		catch (SurespotMessageSequenceException e) {
			// not gonna happen
			SurespotLog.v(TAG, "addMessage", e);
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
			// SurespotControlMessage dmessage = new SurespotControlMessage();
			// String me = IdentityController.getLoggedInUser();
			// dmessage.setFrom(me);
			// dmessage.setType("message");
			// dmessage.setAction("delete");

			// dmessage.setData(ChatUtils.getSpot(message));
			// dmessage.setMoreData(message.getId().toString());
			// dmessage.setLocalId(me + Integer.toString(getLatestMessageControlId(message.getOtherUser()) + 1));

			// sendControlMessage(dmessage);

			// String spot = ChatUtils.getSpot(message);
			final ChatAdapter chatAdapter = mChatAdapters.get(message.getOtherUser());
			setProgress("delete", true);
			if (chatAdapter != null) {
				mNetworkController.deleteMessage(message.getOtherUser(), message.getId(), new AsyncHttpResponseHandler() {
					@Override
					public void onSuccess(int statusCode, String content) {
						// MainActivity.getMainHandler().post(new Runnable() {
						//
						// @Override
						// public void run() {
						deleteMessageInternal(chatAdapter, message, true);
						setProgress("delete", false);
						// }
						// });

					}

					@Override
					public void onFailure(Throwable error, String content) {
						SurespotLog.w(TAG, error, "deleteMessage");
						// MainActivity.getMainHandler().post(new Runnable() {
						//
						// @Override
						// public void run() {
						setProgress("delete", false);
						Utils.makeToast(mContext, "could not delete message");
						// }
						// });
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

	public void deleteMessages(Friend friend) {
		// if it's on the server, send delete control message otherwise just delete it locally

		if (friend != null) {
			String username = friend.getName();

			setProgress("deleteMessages", true);
			final ChatAdapter chatAdapter = mChatAdapters.get(username);
			if (chatAdapter != null) {

				mNetworkController.deleteMessages(username, new AsyncHttpResponseHandler() {
					@Override
					public void onSuccess(int statusCode, String content) {
						// MainActivity.getMainHandler().post(new Runnable() {
						//
						// @Override
						// public void run() {
						chatAdapter.deleteAllMessages();
						chatAdapter.notifyDataSetChanged();
						setProgress("deleteMessages", false);
						// }
						// });

					}

					@Override
					public void onFailure(Throwable error, String content) {
						setProgress("deleteMessages", false);
						// MainActivity.getMainHandler().post(new Runnable() {
						//
						// @Override
						// public void run() {
						Utils.makeToast(mContext, "could not delete messages");

						// }
						// });

					}

				});
			}

			// mChatAdapters.get(username).deleteMyMessages();

			// SurespotControlMessage dmessage = new SurespotControlMessage();
			// String me = IdentityController.getLoggedInUser();
			// dmessage.setFrom(me);
			// dmessage.setType("message");
			// dmessage.setAction("deleteAll");
			// dmessage.setData(ChatUtils.getSpot(me, username));
			// dmessage.setMoreData(String.valueOf(friend.getLastViewedMessageId()));
			// dmessage.setLocalId(me + Integer.toString(getLatestMessageControlId(username) + 1));

			// sendControlMessage(dmessage);
		}
	}

	public void deleteFriend(Friend friend) {

		if (friend != null) {
			final String username = friend.getName();
			setProgress("deleteFriend", true);
			mNetworkController.deleteFriend(username, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(int statusCode, String content) {
					handleDeleteUser(username, IdentityController.getLoggedInUser());
					setProgress("deleteFriend", false);
				}

				@Override
				public void onFailure(Throwable error, String content) {
					SurespotLog.w(TAG, "deleteFriend", error);
					setProgress("deleteFriend", false);
					Utils.makeToast(mContext, "could not delete friend");
				}
			});

		}

	}

	public void toggleMessageShareable(final SurespotMessage message) {
		if (message != null && message.getId() > 0) {
			String username = message.getOtherUser();
			final ChatAdapter chatAdapter = mChatAdapters.get(username);
			if (chatAdapter != null) {

				setProgress("shareable", true);
				mNetworkController.setMessageShareable(username, message.getId(), !message.isShareable(), new AsyncHttpResponseHandler() {
					@Override
					public void onSuccess(int statusCode, String content) {
						message.setShareable(!message.isShareable());
						chatAdapter.notifyDataSetChanged();
						setProgress("shareable", false);
					}

					@Override
					public void onFailure(Throwable error, String content) {
						SurespotLog.w(TAG, "toggleMessageShareable", error);
						setProgress("shareable", false);
						Utils.makeToast(mContext, "could not set message lock state");

					}

				});
			}

			//
			//
			// SurespotControlMessage dmessage = new SurespotControlMessage();
			//
			// String me = IdentityController.getLoggedInUser();
			// dmessage.setFrom(me);
			// dmessage.setType("message");
			// dmessage.setAction(!message.isShareable() ? "shareable" : "notshareable");
			// dmessage.setData(ChatUtils.getSpot(message));
			// dmessage.setMoreData(message.getId().toString());
			// dmessage.setLocalId(me + Integer.toString(getLatestMessageControlId(message.getOtherUser()) + 1));
			// sendControlMessage(dmessage);
		}
	}

	public void resendPictureMessage(final SurespotMessage message) {

		message.setErrorStatus(0);

		final ChatAdapter chatAdapter = mChatAdapters.get(message.getTo());
		chatAdapter.notifyDataSetChanged();

		setProgress("resend", true);
		ChatUtils.resendPictureMessage(mContext, mNetworkController, message, new IAsyncCallback<Boolean>() {

			@Override
			public void handleResponse(Boolean result) {
				setProgress("resend", false);
				if (!result) {
					message.setErrorStatus(500);
					chatAdapter.notifyDataSetChanged();
				}
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

	private void getFriendsAndIds() {
		if (mFriendAdapter.getCount() == 0 && mLatestUserControlId == 0) {
			mFriendAdapter.setLoading(true);
			// get the list of friends
			mNetworkController.getFriends(new JsonHttpResponseHandler() {
				@Override
				public void onSuccess(JSONObject jsonObject) {
					SurespotLog.v(TAG, "getFriends success.");
					ArrayList<Friend> friends = new ArrayList<Friend>();
					try {
						mLatestUserControlId = jsonObject.getInt("userControlId");
						JSONArray friendsArray = jsonObject.getJSONArray("friends");

						if (friendsArray != null) {
							for (int i = 0; i < friendsArray.length(); i++) {
								JSONObject jsonFriend = friendsArray.getJSONObject(i);
								// String name = jsonFriend.getString("name");
								// jsonFriend.put("name", name);
								// jsonFriend.put("flags", jsonFriend.getInt("flags"));
								Friend friend = Friend.toFriend(jsonFriend);
								friends.add(friend);

								SurespotLog.v(TAG, "getFriendsAndIds,  adding friend: %s", friend);
							}
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

					getLatestIds();
				}

				@Override
				public void onFailure(Throwable arg0, String content) {
					// if we didn't get a 401
					if (!mNetworkController.isUnauthorized()) {
						mFriendAdapter.setLoading(false);
						SurespotLog.w(TAG, arg0, "getFriends: %s", content);
						setProgress(null, false);
					}
				}
			});
		}
		else {
			getLatestIds();
		}
	}

	public void closeTab() {
		if (mChatPagerAdapter.getCount() > 0) {

			int position = mViewPager.getCurrentItem();
			if (position > 0) {

				String name = mChatPagerAdapter.getChatName(position);
				SurespotLog.v(TAG, "closeTab, name: %s, position: %d", name, position);

				mChatPagerAdapter.removeChat(mViewPager.getId(), position);
				mFriendAdapter.setChatActive(name, false);
				mEarliestMessage.remove(name);
				destroyChatAdapter(name);
				mIndicator.notifyDataSetChanged();

				position = mViewPager.getCurrentItem();
				mCurrentChat = mChatPagerAdapter.getChatName(position);

				SurespotLog.v(TAG, "closeTab, new tab name: %s, position: %d", mCurrentChat, position);
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
				SurespotLog.v(TAG, "closeTab, name: %s, position: %d", name, position);

				mChatPagerAdapter.removeChat(mViewPager.getId(), position);
				mEarliestMessage.remove(name);
				destroyChatAdapter(name);
				mIndicator.notifyDataSetChanged();
			}
		}
	}

	public synchronized void setMode(int mode) {
		mMode = mode;
	}

	public int getMode() {
		return mMode;
	}

	public void enableMenuItems() {
		boolean enabled = mMode != MODE_SELECT && mCurrentChat != null;
		SurespotLog.v(TAG, "enableMenuItems, enabled: %b", enabled);
		if (mMenuItems != null) {
			for (MenuItem menuItem : mMenuItems) {
				menuItem.setVisible(enabled);
			}
		}
	}

	public void scrollToEnd(String to) {
		ChatFragment chatFragment = getChatFragment(to);
		if (chatFragment != null) {
			chatFragment.scrollToEnd();
		}

	}

	public void setImageUrl(String name, String url, String version, String iv) {
		Friend friend = mFriendAdapter.getFriend(name);
		if (friend != null) {
			String oldUrl = friend.getImageUrl();
			if (!TextUtils.isEmpty(oldUrl)) {
				mNetworkController.removeCacheEntry(oldUrl);
			}

			friend.setImageUrl(url);
			friend.setImageIv(iv);
			friend.setImageVersion(version);
			mFriendAdapter.notifyDataSetChanged();
		}
	}
}
