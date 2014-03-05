package com.twofours.surespot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.AsyncTask;
import ch.boye.httpclientandroidlib.cookie.Cookie;

import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.services.CredentialCachingService.SharedSecretKey;
import com.twofours.surespot.services.CredentialCachingService.VersionMap;

public class StateController {
	private static final String MESSAGES_PREFIX = "messages_";
	private static final String UNSENT_MESSAGES = "unsentMessages";
	private static final String FRIENDS = "friends";
	private static final String COOKIE = "cookie";
	private static final String STATE_EXTENSION = ".sss";
	private static final String SECRETS_EXTENSION = ".sse";
	private static final String TAG = "StateController";
	private Context mContext;

	public class FriendState {
		public int userControlId;
		public List<Friend> friends;
	}

	public StateController(Context context) {
		mContext = context;
	}

	public FriendState loadFriends(String username) {
		String filename = getFilename(username, FRIENDS);
		ArrayList<Friend> friends = new ArrayList<Friend>();
		if (filename != null) {
			String sFriendsJson = null;

			try {

				sFriendsJson = new String(FileUtils.readFile(filename));

			}
			catch (FileNotFoundException f) {
				SurespotLog.v(TAG, "loadFriends, no friends file found");
			}
			catch (IOException e1) {
				SurespotLog.w(TAG, e1, "loadFriends");
			}

			if (sFriendsJson != null) {
				SurespotLog.v(TAG, "Loaded friends: %s", sFriendsJson);

				try {
					JSONObject jsonFriendState = new JSONObject(sFriendsJson);

					int userControlId = jsonFriendState.getInt("userControlId");
					JSONArray friendsJson = jsonFriendState.getJSONArray("friends");
					for (int i = 0; i < friendsJson.length(); i++) {
						Friend friend = Friend.toFriend(friendsJson.getJSONObject(i));
						friends.add(friend);
					}

					FriendState friendState = new FriendState();
					friendState.userControlId = userControlId;
					friendState.friends = friends;
					return friendState;

				}
				catch (JSONException e) {
					SurespotLog.w(TAG, e, "loadFriends");
				}
			}
		}
		return null;
	}

	public synchronized void saveFriends(String username, int latestUserControlId, List<Friend> friends) {
		String filename = getFilename(username, FRIENDS);
		if (filename != null) {
			if (friends != null && friends.size() > 0) {

				JSONArray jsonArray = new JSONArray();
				ListIterator<Friend> iterator = friends.listIterator();

				while (iterator.hasNext()) {
					Friend friend = iterator.next();
					jsonArray.put(friend.toJSONObject());
				}

				JSONObject jsonFriendState = new JSONObject();
				try {
					jsonFriendState.put("userControlId", latestUserControlId);
					jsonFriendState.put("friends", jsonArray);
					String sFriends = jsonFriendState.toString();
					FileUtils.writeFile(filename, sFriends);
					SurespotLog.v(TAG, "Saved friends: %s", sFriends);
				}
				catch (JSONException e) {
					SurespotLog.w(TAG, e, "saveFriends");
				}
				catch (IOException e) {
					SurespotLog.w(TAG, e, "saveFriends");
				}
			}
			else {
				new File(filename).delete();
			}
		}
	}

	public synchronized void saveUnsentMessages(String username, Collection<SurespotMessage> messages) {
		String filename = getFilename(username, UNSENT_MESSAGES);
		if (filename != null) {
			if (messages != null) {
				if (messages.size() > 0) {

					String messageString = ChatUtils.chatMessagesToJson(messages).toString();

					try {
						FileUtils.writeFile(filename, messageString);
					}
					catch (IOException e) {
						SurespotLog.w(TAG, e, "saveUnsentMessages");
					}
				}
				else {
					new File(filename).delete();
				}
			}
			else {
				new File(filename).delete();
			}
		}

	}

	public List<SurespotMessage> loadUnsentMessages(String username) {
		String filename = getFilename(username, UNSENT_MESSAGES);
		ArrayList<SurespotMessage> messages = new ArrayList<SurespotMessage>();
		if (filename != null) {
			String sUnsentMessages = null;

			try {
				sUnsentMessages = new String(FileUtils.readFile(filename));
			}
			catch (FileNotFoundException f) {
				SurespotLog.v(TAG, "loadUnsentMessages, no unsent messages file found");
			}
			catch (IOException e) {
				SurespotLog.w(TAG, e, "loadUnsentMessages");
			}
			if (sUnsentMessages != null) {
				Iterator<SurespotMessage> iterator = ChatUtils.jsonStringToChatMessages(sUnsentMessages).iterator();

				while (iterator.hasNext()) {
					messages.add(iterator.next());
				}
				SurespotLog.v(TAG, "loaded: %d unsent messages.", messages.size());
			}
		}
		return messages;

	}

	public synchronized void saveMessages(String user, String spot, ArrayList<SurespotMessage> messages, int currentScrollPosition) {
		String filename = getFilename(user, MESSAGES_PREFIX + spot);
		if (filename != null) {
			if (messages != null) {
				int messagesSize = messages.size();
				int saveSize = messagesSize - currentScrollPosition;
				if (saveSize + SurespotConstants.SAVE_MESSAGE_BUFFER < SurespotConstants.SAVE_MESSAGE_MINIMUM) {
					saveSize = SurespotConstants.SAVE_MESSAGE_MINIMUM;
				}
				else {
					saveSize += SurespotConstants.SAVE_MESSAGE_BUFFER;
				}

				SurespotLog.v(TAG, "saving %s messages", saveSize);
				String sMessages = ChatUtils.chatMessagesToJson(messagesSize <= saveSize ? messages : messages.subList(messagesSize - saveSize, messagesSize))
						.toString();
				try {
					FileUtils.writeFile(filename, sMessages);
				}
				catch (IOException e) {
					SurespotLog.w(TAG, e, "saveMessages");
				}
			}
			else {
				new File(filename).delete();
			}
		}
	}

	public ArrayList<SurespotMessage> loadMessages(String user, String spot) {
		String filename = getFilename(user, MESSAGES_PREFIX + spot);
		ArrayList<SurespotMessage> messages = new ArrayList<SurespotMessage>();
		if (filename != null) {
			String sMessages = null;

			try {
				sMessages = new String(FileUtils.readFile(filename));
			}
			catch (FileNotFoundException f) {
				SurespotLog.v(TAG, "loadMessages, no messages file found for: %s", spot);
			}
			catch (IOException e) {
				SurespotLog.w(TAG, e, "loadMessages");
			}
			if (sMessages != null) {
				Iterator<SurespotMessage> iterator = ChatUtils.jsonStringToChatMessages(sMessages).iterator();
				while (iterator.hasNext()) {
					SurespotMessage message = iterator.next();
					message.setAlreadySent(true);
					messages.add(message);
				}
				SurespotLog.v(TAG, "loaded: %d messages.", messages.size());
			}
		}
		return messages;
	}

	private String getFilename(String user, String filename) {

		if (user != null) {
			String dir = FileUtils.getStateDir(mContext) + File.separator + user;
			if (FileUtils.ensureDir(dir)) {
				return dir + File.separator + filename + STATE_EXTENSION;
			}

		}
		return null;
	}

	public static synchronized void wipeAllState(Context context) {
		FileUtils.deleteRecursive(new File(FileUtils.getStateDir(context)));
		FileUtils.deleteRecursive(new File(FileUtils.getPublicKeyDir(context)));
	}

	public static synchronized void wipeState(Context context, String identityName) {
		FileUtils.deleteRecursive(new File(FileUtils.getStateDir(context) + File.separator + identityName));
	}

	public static void clearCache(final Context context, final IAsyncCallback<Void> callback) {
		new AsyncTask<Void, Void, Void>() {
			protected Void doInBackground(Void... params) {
				// clear out some shiznit
				SurespotLog.v(TAG, "clearing local cache");

				// state
				wipeAllState(context);

				// last chat and user we had open
				Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.LAST_CHAT, null);
				Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.LAST_USER, null);

				// network caches
				NetworkController networkController = MainActivity.getNetworkController();
				if (networkController != null) {
					networkController.clearCache();
				}

				// captured image dir
				FileUtils.wipeImageCaptureDir(context);

				// uploaded images dir
				String localImageDir = FileUtils.getImageUploadDir(context);
				FileUtils.deleteRecursive(new File(localImageDir));

				SurespotApplication.getCachingService().clear();

				return null;
			}

			protected void onPostExecute(Void result) {
				callback.handleResponse(null);
			};

		}.execute();
	}

	public static void wipeUserState(Context context, String username, String otherUsername) {
		String publicKeyDir = FileUtils.getPublicKeyDir(context) + File.separator + otherUsername;
		FileUtils.deleteRecursive(new File(publicKeyDir));

		String room = ChatUtils.getSpot(username, otherUsername);
		String messageFile = FileUtils.getStateDir(context) + File.separator + username + File.separator + "messages_" + room + STATE_EXTENSION;
		File file = new File(messageFile);
		file.delete();

	}

	public void saveSharedSecrets(Context context, String username, String password, Map<SharedSecretKey, byte[]> secrets) {
		Map<String, byte[]> map = new HashMap<String, byte[]>();

		// TODO encrypt
		for (SharedSecretKey key : secrets.keySet()) {
			// save only secreds for this user
			if (key.getOurUsername().equals(username)) {
				String skey = key.getOurUsername() + ":" + key.getOurVersion() + ":" + key.getTheirUsername() + ":" + key.getTheirVersion();
				SurespotLog.d(TAG, "saving shared secret: %s", skey);
				map.put(skey, secrets.get(key));
			}
		}

		String filename = FileUtils.getSecretsDir(context) + File.separator + username + SECRETS_EXTENSION;

		FileOutputStream fos;
		try {
			fos = new FileOutputStream(filename);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(map);
			oos.close();
		}

		catch (IOException e) {
			SurespotLog.e(TAG, e, "error saving shared secrets for %s", username);
		}
	}

	@SuppressWarnings("unchecked")
	public Map<SharedSecretKey, byte[]> loadSharedSecrets(Context context, String username, String password) {

		String filename = FileUtils.getSecretsDir(context) + File.separator + username + SECRETS_EXTENSION;		
		Map<String, byte[]> loadedMap = null;
		try {
			FileInputStream fis = new FileInputStream(filename);
			ObjectInputStream ois = new ObjectInputStream(fis);
			loadedMap = (Map<String, byte[]>) ois.readObject();
			ois.close();
			fis.close();
		}
		catch (IOException e) {
			SurespotLog.e(TAG, e, "error loading saved secrets for %s", username);
			return null;
		}
		catch (ClassNotFoundException e) {
			SurespotLog.e(TAG, e, "error loading saved secrets for %s", username);
			return null;
		}		

		Map<SharedSecretKey, byte[]> map = new HashMap<SharedSecretKey, byte[]>();

		for (String key : loadedMap.keySet()) {
			String[] split = key.split(":");

			SharedSecretKey ssk = new SharedSecretKey(new VersionMap(split[0], split[1]), new VersionMap(split[2], split[3]));
			SurespotLog.d(TAG, "loading shared secret: %s", key);
			map.put(ssk, loadedMap.get(key));
		}

		return map;
	}

	public Cookie loadCookie(String username, String password) {
		if (username == null || password == null) {
			return null;
		}
		
		String filename = getFilename(username, COOKIE);		
		
		if (!new File(filename).exists()) {
			return null;
		}
		
		SurespotLog.d(TAG, "loading cookie for username: %s", username);
		Cookie cookie = null;
		try {
			FileInputStream fis = new FileInputStream(filename);
			ObjectInputStream ois = new ObjectInputStream(fis);
			cookie = (Cookie) ois.readObject();
			ois.close();
			fis.close();
			return cookie;
		}
		catch (IOException e) {
			SurespotLog.e(TAG, e, "error loading cookie for %s", username);		
			return null;
		}
		catch (ClassNotFoundException e) {
			SurespotLog.e(TAG, e, "error loading cookie for %s", username);
			return null;
		}		
	}

	public void saveCookie(String username, String password, Cookie cookie) {
		if (username == null || password == null || cookie == null) {
			return;
		}
		
		SurespotLog.d(TAG, "saving cookie for username: %s, cookie: %s", username, cookie);
		
		String filename = getFilename(username, COOKIE);

		FileOutputStream fos;
		try {
			fos = new FileOutputStream(filename);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(cookie);
			oos.close();
		}

		catch (IOException e) {
			SurespotLog.e(TAG, e, "error saving cookie for %s", username);
		}
	}
}
