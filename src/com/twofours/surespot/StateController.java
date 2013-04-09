package com.twofours.surespot;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.AsyncTask;

import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class StateController {
	private static final String MESSAGES_PREFIX = "messages_";
	private static final String UNSENT_MESSAGES = "unsentMessages";
	private static final String ACTIVE_CHATS = "activeChats";
	private static final String FRIENDS = "friends";
	private static final String MESSAGE_ACTIVITY = "messageActivity";
	private static final String LAST_VIEWED_MESSAGE_IDS = "lastViewedMessageIds";
	private static final String LAST_RECEIVED_MESSAGE_IDS = "lastReceivedMessageIds";
	private static final String LAST_RECEIVED_MESSAGE_CONTROL_IDS = "lastReceivedMessageControlIds";
	private static final String STATE_EXTENSION = ".sss";

	private static final String TAG = "StateController";

	public class FriendState {
		public int userControlId;
		public List<Friend> friends;
	}
	
	public FriendState loadFriends() {
		String filename = getFilename(FRIENDS);
		ArrayList<Friend> friends = new ArrayList<Friend>();
		if (filename != null) {
			String sFriendsJson = readFile(filename);
			if (sFriendsJson != null) {
				SurespotLog.v(TAG, "Loaded friends: " + sFriendsJson);
				
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
					SurespotLog.w(TAG, "loadFriends", e);
				}
			}
		}
		return null;
	}

	public void saveFriends(int latestUserControlId, List<Friend> friends) {
		String filename = getFilename(FRIENDS);
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
					writeFile(filename, sFriends);
					SurespotLog.v(TAG, "Saved friends: " + sFriends);
				}
				catch (JSONException e) {
					SurespotLog.w(TAG, "saveFriends", e);
				}
			}
			else {
				new File(filename).delete();
			}
		}
	}

//	public Set<String> loadActiveChats() {
//		String filename = getFilename(ACTIVE_CHATS);
//		HashSet<String> activeChats = new HashSet<String>();
//		if (filename != null) {
//			String sActiveChatsJson = readFile(filename);
//			if (sActiveChatsJson != null) {
//				SurespotLog.v(TAG, "Loaded active chats: " + sActiveChatsJson);
//				JSONArray activeChatsJson;
//				try {
//					activeChatsJson = new JSONArray(sActiveChatsJson);
//					for (int i = 0; i < activeChatsJson.length(); i++) {
//						String chatName = activeChatsJson.getString(i);
//						activeChats.add(chatName);
//					}
//
//				}
//				catch (JSONException e) {
//					SurespotLog.w(TAG, "getACtiveChats", e);
//				}
//			}
//		}
//		return activeChats;
//	}
//
//	public void saveActiveChats(Collection<String> chats) {
//		String filename = getFilename(ACTIVE_CHATS);
//		if (filename != null) {
//			if (chats != null && chats.size() > 0) {
//
//				JSONArray jsonArray = new JSONArray(chats);
//				String sActivechats = jsonArray.toString();
//				writeFile(filename, sActivechats);
//				SurespotLog.v(TAG, "Saved active chats: " + sActivechats);
//			}
//			else {
//				new File(filename).delete();
//			}
//		}
//	}

//	public HashMap<String, Boolean> loadMessageActivity() {
//		String filename = getFilename(MESSAGE_ACTIVITY);
//		if (filename != null) {
//
//			String lastMessageIdJson = readFile(filename);
//			if (lastMessageIdJson != null) {
//
//				SurespotLog.v(TAG, "Loaded messageActivity: " + lastMessageIdJson);
//				return Utils.jsonStringToBooleanMap(lastMessageIdJson);
//			}
//
//		}
//		return new HashMap<String, Boolean>();
//
//	}
//
//	public void saveMessageActivity(Map<String, Boolean> messageActivity) {
//
//		String filename = getFilename(MESSAGE_ACTIVITY);
//		if (filename != null) {
//			if (messageActivity != null && messageActivity.size() > 0) {
//				String smessageIds = booleanMapToJsonString(messageActivity);
//				writeFile(filename, smessageIds);
//				SurespotLog.v(TAG, "saved message activity: " + smessageIds);
//
//			}
//			else {
//				new File(filename).delete();
//			}
//		}
//	}
//
//	public HashMap<String, Integer> loadLastViewMessageIds() {
//		String filename = getFilename(LAST_VIEWED_MESSAGE_IDS);
//		if (filename != null) {
//
//			String lastMessageIdJson = readFile(filename);
//			if (lastMessageIdJson != null) {
//
//				SurespotLog.v(TAG, "Loaded last viewed ids: " + lastMessageIdJson);
//				return Utils.jsonStringToMap(lastMessageIdJson);
//			}
//
//		}
//		return new HashMap<String, Integer>();
//
//	}
//
//	public void saveLastViewedMessageIds(Map<String, Integer> messageIds) {
//
//		String filename = getFilename(LAST_VIEWED_MESSAGE_IDS);
//		if (filename != null) {
//			if (messageIds != null && messageIds.size() > 0) {
//				String smessageIds = mapToJsonString(messageIds);
//				writeFile(filename, smessageIds);
//				SurespotLog.v(TAG, "saved last viewed messageIds: " + smessageIds);
//
//			}
//			else {
//				new File(filename).delete();
//			}
//		}
//	}
//
//	public HashMap<String, Integer> loadLastReceivedMessageIds() {
//		String filename = getFilename(LAST_RECEIVED_MESSAGE_IDS);
//		if (filename != null) {
//
//			String lastMessageIdJson = readFile(filename);
//			if (lastMessageIdJson != null) {
//
//				SurespotLog.v(TAG, "Loaded last received ids: " + lastMessageIdJson);
//				return Utils.jsonStringToMap(lastMessageIdJson);
//			}
//
//		}
//		return new HashMap<String, Integer>();
//
//	}
//
//	public void saveLastReceivedMessageIds(Map<String, Integer> messageIds) {
//
//		String filename = getFilename(LAST_RECEIVED_MESSAGE_IDS);
//		if (filename != null) {
//			if (messageIds != null && messageIds.size() > 0) {
//				String smessageIds = mapToJsonString(messageIds);
//				writeFile(filename, smessageIds);
//				SurespotLog.v(TAG, "saved last received messageIds: " + smessageIds);
//
//			}
//			else {
//				new File(filename).delete();
//			}
//		}
//	}
//
//	public HashMap<String, Integer> loadLastReceivedControlIds() {
//		String filename = getFilename(LAST_RECEIVED_MESSAGE_CONTROL_IDS);
//		if (filename != null) {
//
//			String lastControlIdJson = readFile(filename);
//			if (lastControlIdJson != null) {
//
//				SurespotLog.v(TAG, "Loaded last received ids: " + lastControlIdJson);
//				return Utils.jsonStringToMap(lastControlIdJson);
//			}
//
//		}
//		return new HashMap<String, Integer>();
//
//	}
//
//	public void saveLastReceivedControlIds(Map<String, Integer> controlIds) {
//
//		String filename = getFilename(LAST_RECEIVED_MESSAGE_CONTROL_IDS);
//		if (filename != null) {
//			if (controlIds != null && controlIds.size() > 0) {
//				String scontrolIds = mapToJsonString(controlIds);
//				writeFile(filename, scontrolIds);
//				SurespotLog.v(TAG, "saved last received controlIds: " + scontrolIds);
//
//			}
//			else {
//				new File(filename).delete();
//			}
//		}
//	}

	public void saveUnsentMessages(Collection<SurespotMessage> messages) {
		String filename = getFilename(UNSENT_MESSAGES);
		if (filename != null) {
			if (messages != null) {
				String messageString = ChatUtils.chatMessagesToJson(messages).toString();
				writeFile(filename, messageString);
			}
			else {
				new File(filename).delete();
			}
		}

	}

	public List<SurespotMessage> loadUnsentMessages() {
		String filename = getFilename(UNSENT_MESSAGES);
		ArrayList<SurespotMessage> messages = new ArrayList<SurespotMessage>();
		if (filename != null) {
			String sUnsentMessages = readFile(filename);
			if (sUnsentMessages != null) {
				Iterator<SurespotMessage> iterator = ChatUtils.jsonStringToChatMessages(sUnsentMessages).iterator();

				while (iterator.hasNext()) {
					messages.add(iterator.next());
				}
				SurespotLog.v(TAG, "loaded: " + messages.size() + " unsent messages.");
			}
		}
		return messages;

	}

	public void saveMessages(String spot, ArrayList<SurespotMessage> messages, int currentScrollPosition) {
		String filename = getFilename(MESSAGES_PREFIX + spot);
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
				
			    SurespotLog.v(TAG, "saving " + saveSize + " messages");
				String sMessages = ChatUtils.chatMessagesToJson(messagesSize <= saveSize ? messages : messages.subList(messagesSize - saveSize, messagesSize)).toString();
				writeFile(filename, sMessages);
			}
			else {
				new File(filename).delete();
			}
		}
	}

	public ArrayList<SurespotMessage> loadMessages(String spot) {
		String filename = getFilename(MESSAGES_PREFIX + spot);
		ArrayList<SurespotMessage> messages = new ArrayList<SurespotMessage>();
		if (filename != null) {
			String sMessages = readFile(filename);
			if (sMessages != null) {
				Iterator<SurespotMessage> iterator = ChatUtils.jsonStringToChatMessages(sMessages).iterator();
				while (iterator.hasNext()) {
					messages.add(iterator.next());
				}
				SurespotLog.v(TAG, "loaded: " + messages.size() + " messages.");
			}
		}
		return messages;
	}

	private static void writeFile(String filename, String data) {
		SurespotLog.v(TAG, "writeFile, " + filename + ": " + data.substring(0, data.length() > 100 ? 100 : data.length()));
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(filename);
			fos.write(data.getBytes());
			fos.close();
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "writeFile, " + filename, e);
		}
	}

	private static String readFile(String filename) {
		File file = new File(filename);
		if (file.exists()) {
			try {
				BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
				byte[] input = new byte[(int) file.length()];
				int read = bis.read(input);
				bis.close();
				if (read == file.length()) {
					return new String(input);
				}
			}
			catch (Exception e) {
				SurespotLog.w(TAG, "readFile, " + filename, e);
			}
		}
		return null;
	}

	private static String mapToJsonString(Map<String, Integer> map) {
		JSONObject jsonObject = new JSONObject(map);
		return jsonObject.toString();
	}

	private static String booleanMapToJsonString(Map<String, Boolean> map) {
		JSONObject jsonObject = new JSONObject(map);
		return jsonObject.toString();
	}

	private String getFilename(String filename) {
		String user = IdentityController.getLoggedInUser();
		if (user != null) {
			String dir = FileUtils.getStateDir(MainActivity.getContext()) + File.separator + user;
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
		String room = ChatUtils.getSpot(username, otherUsername);
		FileUtils.deleteRecursive(new File(publicKeyDir));
		
		String messageFile = FileUtils.getStateDir(context)+ File.separator + username + File.separator + "messages_" + room + STATE_EXTENSION;
		File file = new File(messageFile);
		file.delete();
		
		
				
	}
}
