package com.twofours.surespot;

public class SurespotConstants {
	public class IntentFilters {
		public static final String INVITE_REQUEST = "notification_event";
		public static final String INVITE_RESPONSE = "friend_added_event";
		public static final String MESSAGE_RECEIVED = "message_added_event";
		public static final String FRIEND_INVITE_RESPONSE_EVENT = "friend_invite_event";
		public static final String SOCKET_CONNECTION_STATUS_CHANGED = "socket_io_connection_status_changed";
	}

	public class ExtraNames {
		public static final String NAME = "notification_data";
		public static final String FRIEND_ADDED = "friend_added_data";
		public static final String MESSAGE = "message_data";
		public static final String INVITE_RESPONSE = "friend_invite_response";		
		public static final String SHOW_CHAT_NAME = "show_chat_name";
		public static final String GCM_CHANGED = "gcm_changed";
	}

	// public final static String BASE_URL = "http://alpha.surespot.me:8080";
	// public final static String WEBSOCKET_URL = "http://alpha.surespot.me:443";

	public final static String BASE_URL = "http://192.168.10.68:3000";
	public final static String WEBSOCKET_URL = "http://192.168.10.68:3000";

	public class PrefNames {
		public final static String PREFS_FILE = "surespot_preferences";
		public final static String GCM_ID_RECEIVED = "gcm_id_received";
		public final static String GCM_ID_SENT = "gcm_id_sent";
		public final static String PREFS_ACTIVE_CHATS = "active_chats";
		public final static String PREFS_LAST_MESSAGE_IDS = "last_message_ids";
	}
}
