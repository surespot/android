package com.twofours.surespot;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class StateController {
	private static final String MESSAGES_PREFIX = "messages_";
	private static final String UNSENT_MESSAGES = "unsentMessages";
	private static final String ACTIVE_CHATS = "activeChats";
	private static final String MESSAGE_IDS = "messageIds";
	private static final String STATE_EXTENSION = ".sss";

	private static final String TAG = "StateController";

	public ArrayList<String> loadActiveChats() {
		String filename = getFilename(ACTIVE_CHATS);
		ArrayList<String> activeChats = new ArrayList<String>();
		if (filename != null) {
			String sActiveChatsJson = readFile(filename);
			if (sActiveChatsJson != null) {
				SurespotLog.v(TAG, "Loaded active chats: " + sActiveChatsJson);
				JSONArray activeChatsJson;
				try {
					activeChatsJson = new JSONArray(sActiveChatsJson);
					for (int i = 0; i < activeChatsJson.length(); i++) {
						String chatName = activeChatsJson.getString(i);
						activeChats.add(chatName);
					}

				}
				catch (JSONException e) {
					SurespotLog.w(TAG, "getACtiveChats", e);
				}
			}
		}
		return activeChats;
	}

	public void saveActiveChats(Collection<String> chats) {
		String filename = getFilename(ACTIVE_CHATS);
		if (filename != null) {
			if (chats != null && chats.size() > 0) {
				JSONArray jsonArray = new JSONArray(chats);
				writeFile(filename, jsonArray.toString());
			}
			else {
				new File(filename).delete();
			}
		}
	}

	public HashMap<String, Integer> loadLastViewMessageIds() {
		String filename = getFilename(MESSAGE_IDS);
		if (filename != null) {

			String lastMessageIdJson = readFile(filename);
			if (lastMessageIdJson != null) {

				SurespotLog.v(TAG, "Loaded last viewed ids: " + lastMessageIdJson);
				return Utils.jsonStringToMap(lastMessageIdJson);
			}

		}
		return new HashMap<String, Integer>();

	}

	public void saveLastViewedMessageIds(Map<String, Integer> messageIds) {

		String filename = getFilename(MESSAGE_IDS);
		if (filename != null) {
			if (messageIds != null && messageIds.size() > 0) {
				String smessageIds = mapToJsonString(messageIds);
				writeFile(filename, smessageIds);
				SurespotLog.v(TAG, "saved last viewed messageIds: " + smessageIds);

			}
			else {
				new File(filename).delete();
			}
		}
	}

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

	public void saveMessages(String spot, ArrayList<SurespotMessage> messages) {
		String filename = getFilename(MESSAGES_PREFIX + spot);
		if (filename != null) {
			if (messages != null) {
				int messagesSize = messages.size();
				SurespotLog.v(TAG, "saving " + (messagesSize > 30 ? 30 : messagesSize) + " messages");
				String sMessages = ChatUtils.chatMessagesToJson(
						messagesSize <= 30 ? messages : messages.subList(messagesSize - 30, messagesSize)).toString();
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

	private String getFilename(String filename) {
		String user = IdentityController.getLoggedInUser();
		if (user != null) {
			String dir = FileUtils.getStateDir(SurespotApplication.getContext()) + File.separator + user;
			if (FileUtils.ensureDir(dir)) {
				return dir + File.separator + filename + STATE_EXTENSION;
			}

		}
		return null;

	}

	public static synchronized void wipeState(Context context, String identityName) {

		deleteRecursive(new File(FileUtils.getStateDir(context) + File.separator + identityName));

	}

	private static void deleteRecursive(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory()) {
			File[] files = fileOrDirectory.listFiles();
			if (files != null) {
				for (File child : files)
					deleteRecursive(child);
			}
			fileOrDirectory.delete();
		}

	}
}
