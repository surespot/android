package com.twofours.surespot;

public class SurespotConstants {

	private static final String SERVER_PUBLIC_KEY_LOCAL = "-----BEGIN PUBLIC KEY-----\n" + "MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQA93Acih23m8Jy65gLo8A9t0/snVXe\n"
			+ "Rm+6ucIp56cXPgYvBwKDxT30z/HU84HPm2T8lnKQjFGMTUKHnIW+vqKFZicAokkW\n" + "J/GoFMDGz5tEDGEQrHk/tswEysri5V++kzwlORA+kAxAasdx7Hezl0QfvkPScr3N\n"
			+ "5ifR7m1J+RFNqK0bulQ=\n" + "-----END PUBLIC KEY-----";

	private static final String SERVER_PUBLIC_KEY_PROD = "-----BEGIN PUBLIC KEY-----\n" + "MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQA/mqxm0092ovWqQluMYWJXc7iE+0v\n"
			+ "mrA8vJNUo1bAEe9dWY9FucDnZIbNNNGKh8soA9Ej7gyW9Yc6D7llh52LhscBpGd6\n" + "bX+FNZEROhIDJP2KgTTKVX+ASB0WtPT3V9AbyoAAxEse8IP5Wec5ZGQG1B/mOlGm\n"
			+ "Z/aaRkB1bwl9eCNojpw=\n" + "-----END PUBLIC KEY-----";

	public final static boolean PRODUCTION = true;
	public static final String SERVER_PUBLIC_KEY =  PRODUCTION ? SERVER_PUBLIC_KEY_PROD : SERVER_PUBLIC_KEY_LOCAL;

	public final static boolean LOGGING = false;
	public final static int MAX_IDENTITIES = 3;

	public final static boolean SSL_STRICT = true;

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
		// public static final String SHOW_CHAT_NAME = "show_chat_name";
		public static final String MESSAGE_FROM = "message_from";
		public static final String MESSAGE_TO = "message_to";
		public static final String JUST_RESTORED_IDENTITY = "just_restored_identity";

		public static final String GCM_CHANGED = "gcm_changed";
		public static final String CONNECTED = "connected";
		public static final String IMAGE_MESSAGE = "image_message";
		public static final String NOTIFICATION_TYPE = "notification_type";
		public static final String UNSENT_MESSAGES = "unsent_messages";


	}

	public class PrefNames {
		public final static String PREFS_FILE = "surespot_preferences";
		public final static String GCM_ID_RECEIVED = "gcm_id_received";
		public final static String GCM_ID_SENT = "gcm_id_sent";
		public static final String LAST_USER = "last_user";
		public static final String LAST_CHAT = "last_chat";
		public static final String REFERRERS = "referrers";
		public static final String APP_VERSION = "app_version";
		public static final String KEYSTORE_ENABLED = "pref_enable_keystore";
		public static final String VOICE_DISABLED = "pref_disable_voice";
		public static final String BLACK = "pref_black";
		public static final String RECENTLY_USED_GIFS = "recently_used_gifs";
	}

	public class MimeTypes {
		public final static String TEXT = "text/plain";
		public final static String IMAGE = "image/";
		public final static String GIF_LINK = "gif/https";
		public final static String M4A = "audio/mp4";
        public final static String FILE = "application/octet-stream";
		public final static String DRIVE_FOLDER = "application/vnd.google-apps.folder";
		public final static String DRIVE_FILE = "application/vnd.google-apps.file";
		public final static String SURESPOT_IDENTITY = "application/ssi";
	}

	public class IntentRequestCodes {
		public final static int NEW_MESSAGE_NOTIFICATION = 0;
		public final static int INVITE_REQUEST_NOTIFICATION = 1;
		public final static int INVITE_RESPONSE_NOTIFICATION = 2;
		public final static int FOREGROUND_NOTIFICATION = 3;
		public final static int REQUEST_EXISTING_IMAGE = 4;
		public final static int REQUEST_SELECT_IMAGE = 5;
		public final static int REQUEST_SETTINGS = 6;
		public final static int LOGIN = 7;
		public final static int REQUEST_CAPTURE_IMAGE = 8;
		public final static int PICK_CONTACT = 9;
		public final static int REQUEST_SELECT_FRIEND_IMAGE = 10;
		public final static int BACKUP_NOTIFICATION = 11;
		public final static int CHOOSE_GOOGLE_ACCOUNT = 12;
		public final static int REQUEST_GOOGLE_AUTH = 13;
		public final static int SYSTEM_NOTIFICATION = 14;
		public final static int PURCHASE = 15;
		public final static int BACKGROUND_CACHE_NOTIFICATION = 16;
		public final static int BACKGROUND_CHAT_SERVICE_NOTIFICATION = 17;
		public final static int UNSENT_MESSAGE_NOTIFICATION = 18;
		public final static int READ_EXTERNAL_STORAGE = 19;
		public final static int WRITE_EXTERNAL_STORAGE = 20;
		public final static int REQUEST_SELECT_FILE = 21;
		public final static int REQUEST_GET_ACCOUNTS = 22;
		public final static int REQUEST_MICROPHONE = 23;
		public static final int FCM_REGISTRATION = 24;
	}
	
	
	public class Products {
		public final static String PWYL_PREFIX = "pwyl_";
		public final static String VOICE_MESSAGING = "voice_messaging";
	}
}
