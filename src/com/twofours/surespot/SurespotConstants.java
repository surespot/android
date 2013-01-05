package com.twofours.surespot;

public class SurespotConstants {
	public class EventFilters {
		public static final String NOTIFICATION_EVENT = "notification_event";
		public static final String FRIEND_ADDED_EVENT = "friend_added_event";
		public static final String MESSAGE_RECEIVED_EVENT = "message_added_event";
		public static final String FRIEND_INVITE_RESPONSE_EVENT = "friend_invite_event";	
	}
	
	public class ExtraNames {
		public static final String NOTIFICATION = "notification_data";
		public static final String FRIEND_ADDED = "friend_added_data";
		public static final String MESSAGE = "message_data";
		public static final String FRIEND_INVITE_RESPONSE_NAME = "friend_invite_name";
		public static final String FRIEND_INVITE_RESPONSE_ACTION = "friend_invite_action";
		public static final String SHOW_CHAT_NAME = "show_chat_name";
	}
	
	//public final static String BASE_URL = "http://alpha.surespot.me";
	//public final static String WEBSOCKET_URL = "http://alpha.surespot.me:443";
	public final static String BASE_URL = "http://192.168.10.68:3000";
	public final static String WEBSOCKET_URL = "http://192.168.10.68:3000";
	
	public final static String PREFS_FILE = "surespot_preferences";
}
