package com.twofours.surespot;

public class SurespotConstants {
	public class IntentFilters {
		public static final String INVITE_REQUEST = "invite_request_intent";
		public static final String INVITE_RESPONSE = "invite_response_intent";
		public static final String MESSAGE_RECEIVED = "message_added_event";
		public static final String FRIEND_INVITE_RESPONSE_EVENT = "friend_invite_event";
		public static final String SOCKET_CONNECTION_STATUS_CHANGED = "socket_io_connection_status_changed";
		public static final String INVITE_NOTIFICATION = "invite_notification";
	}

	public class ExtraNames {
		public static final String NAME = "notification_data";
		public static final String FRIEND_ADDED = "friend_added_data";
		public static final String MESSAGE = "message_data";
		public static final String INVITE_RESPONSE = "friend_invite_response";
		public static final String SHOW_CHAT_NAME = "show_chat_name";
		public static final String GCM_CHANGED = "gcm_changed";
		public static final String CONNECTED = "connected";
	}

	public final static String BASE_URL = "http://alpha.surespot.me:8080";
	public final static String WEBSOCKET_URL = "http://alpha.surespot.me:443";
	
	public final static int MAX_IMAGE_DIMENSION = 480;
	
	//TODO change by screen size
	public final static int IMAGE_DISPLAY_HEIGHT = 320;

	// public final static String BASE_URL = "http://192.168.10.68:3000";
	 //public final static String WEBSOCKET_URL = "http://192.168.10.68:3000";

	public class PrefNames {
		public final static String PREFS_FILE = "surespot_preferences";
		public final static String GCM_ID_RECEIVED = "gcm_id_received";
		public final static String GCM_ID_SENT = "gcm_id_sent";
		public final static String PREFS_ACTIVE_CHATS = "active_chats";
		public final static String PREFS_LAST_VIEWED_MESSAGE_IDS = "last_message_ids";
		public static final String LAST_CHAT = "last_chat";
	}
	
	public class MimeTypes {
		public final static String TEXT = "text/plain";
		public final static String IMAGE = "image/";
	}
}
