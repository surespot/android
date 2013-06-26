package com.twofours.surespot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.acra.ACRA;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.google.android.gcm.GCMBaseIntentService;
import com.google.android.gcm.GCMRegistrar;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.SyncHttpClient;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotConstants.IntentFilters;
import com.twofours.surespot.common.SurespotConstants.IntentRequestCodes;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;

public class GCMIntentService extends GCMBaseIntentService {
	private static final String TAG = "GCMIntentService";
	public static final String SENDER_ID = "428168563991";
	private PowerManager mPm;
	NotificationCompat.Builder mBuilder;
	NotificationManager mNotificationManager;

	public GCMIntentService() {
		super(SENDER_ID);
		SurespotLog.v(TAG, "GCMIntentService");

	}

	@Override
	public void onCreate() {
		super.onCreate();
		mPm = (PowerManager) getSystemService(Context.POWER_SERVICE);

		mBuilder = new NotificationCompat.Builder(this);
		mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	@Override
	protected void onError(Context arg0, String arg1) {
		SurespotLog.w(TAG, "onError: " + arg1);
	}

	@Override
	protected void onRegistered(final Context context, final String id) {
		// shoved it in shared prefs
		SurespotLog.v(TAG, "Received gcm id, saving it in shared prefs.");
		Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.GCM_ID_RECEIVED, id);
		GCMRegistrar.setRegisteredOnServer(context, true);
		// TODO retries?
		if (IdentityController.hasLoggedInUser()) {
			SurespotLog.v(TAG, "Attempting to register gcm id on surespot server.");
			// do this synchronously so android doesn't kill the service thread before it's done

			SyncHttpClient client = null;
			try {
				client = new SyncHttpClient(this) {

					@Override
					public String onRequestFailed(Throwable arg0, String arg1) {
						SurespotLog.v(TAG, "Error saving gcmId on surespot server: " + arg1);
						return "failed";
					}
				};
			}
			catch (IOException e) {
				// TODO tell user shit is fucked
				// get shared prefs
				SharedPreferences pm = context.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
				if (pm.getBoolean("pref_crash_reporting", false)) {
					ACRA.getErrorReporter().handleSilentException(e);
				}
				return;
			}

			client.setCookieStore(MainActivity.getNetworkController().getCookieStore());

			Map<String, String> params = new HashMap<String, String>();
			params.put("gcmId", id);

			String result = client.post(SurespotConfiguration.getBaseUrl() + "/registergcm", new RequestParams(params));
			// success returns 204 = null result
			if (result == null) {
				SurespotLog.v(TAG, "Successfully saved GCM id on surespot server.");

				// the server and client match, we're golden
				Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.GCM_ID_SENT, id);

			}
		}
		else {
			SurespotLog.v(TAG, "Can't save GCM id on surespot server as user is not logged in.");
		}
	}

	@Override
	protected void onUnregistered(Context context, String arg1) {

		Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.GCM_ID_SENT, null);
		Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.GCM_ID_RECEIVED, null);

	}

	@Override
	protected void onMessage(Context context, Intent intent) {
		SurespotLog.v(TAG, "received GCM message, extras: " + intent.getExtras());
		String to = intent.getStringExtra("to");

		// make sure to is someone on this phone otherwise do not do a damn thing
		if (!IdentityController.getIdentityNames(context).contains(to)) {
			return;
		}

		String type = intent.getStringExtra("type");
		String from = intent.getStringExtra("sentfrom");

		if (type.equals("message")) {
			// if the chat is currently showing don't show a notification
			// TODO setting for this

			boolean isScreenOn = false;
			if (mPm != null) {
				isScreenOn = mPm.isScreenOn();
			}
			boolean hasLoggedInUser = IdentityController.hasLoggedInUser();
			boolean sameUser = to.equals(IdentityController.getLoggedInUser());
			boolean tabOpenToUser = from.equals(ChatController.getCurrentChat());
			boolean paused = ChatController.isPaused();

			SurespotLog.v(TAG, "is screen on: %b, paused: %b, hasLoggedInUser: %b, sameUser: %b, tabOpenToUser: %b", isScreenOn, paused, hasLoggedInUser,
					sameUser, tabOpenToUser);

			if (hasLoggedInUser && isScreenOn && sameUser && tabOpenToUser && !paused) {
				SurespotLog.v(TAG, "not displaying notification because the tab is open for it.");
				return;
			}

			String spot = ChatUtils.getSpot(from, to);

			// add the message if it came in the GCM
			String message = intent.getStringExtra("message");
			if (message != null) {
				ArrayList<SurespotMessage> messages = SurespotApplication.getStateController().loadMessages(to, spot);
				SurespotMessage sm = SurespotMessage.toSurespotMessage(message);
				sm.setGcm(true);
				if (sm != null) {
					messages.add(sm);
				}
				SurespotApplication.getStateController().saveMessages(to, spot, messages, -1);
			}

			generateNotification(context, IntentFilters.MESSAGE_RECEIVED, from, to, context.getString(R.string.notification_title),
					context.getString(R.string.notification_message, to, from), to + ":" + spot, IntentRequestCodes.NEW_MESSAGE_NOTIFICATION);
		}
		else {
			if (type.equals("invite")) {
				generateNotification(context, IntentFilters.INVITE_REQUEST, from, to, context.getString(R.string.notification_title),
						context.getString(R.string.notification_invite, to, from), to + ":" + from, IntentRequestCodes.INVITE_REQUEST_NOTIFICATION);
			}
			else {
				generateNotification(context, IntentFilters.INVITE_RESPONSE, from, to, context.getString(R.string.notification_title),
						context.getString(R.string.notification_invite_accept, to, from), to, IntentRequestCodes.INVITE_RESPONSE_NOTIFICATION);
			}
		}
	}

	private void generateNotification(Context context, String type, String from, String to, String title, String message, String tag, int id) {

		// get shared prefs
		SharedPreferences pm = context.getSharedPreferences(to, Context.MODE_PRIVATE);
		if (!pm.getBoolean("pref_notifications_enabled", true)) {
			return;
		}

		int icon = R.drawable.surespot_logo;

		// need to use same builder for only alert once to work:
		// http://stackoverflow.com/questions/6406730/updating-an-ongoing-notification-quietly
		mBuilder.setSmallIcon(icon).setContentTitle(title).setAutoCancel(true).setOnlyAlertOnce(true).setContentText(message);
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		// if we're logged in, go to the chat, otherwise go to login
		Intent mainIntent = null;
		mainIntent = new Intent(context, MainActivity.class);
		mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

		// mainIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

		// mainIntent.setAction("android.intent.action.MAIN");
		// mainIntent.addCategory("android.intent.category.LAUNCHER");

		mainIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_TO, to);
		mainIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_FROM, from);
		mainIntent.putExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE, type);

		// stackBuilder.addParentStack(FriendActivity.class);
		stackBuilder.addNextIntent(mainIntent);

		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent((int) new Date().getTime(), PendingIntent.FLAG_CANCEL_CURRENT);

		mBuilder.setContentIntent(resultPendingIntent);
		int defaults = 0;

		boolean showLights = pm.getBoolean("pref_notifications_led", true);
		boolean makeSound = pm.getBoolean("pref_notifications_sound", true);
		boolean vibrate = pm.getBoolean("pref_notifications_vibration", true);

		if (showLights) {
			SurespotLog.v(TAG, "showing notification led");
			mBuilder.setLights(0xff0000FF, 500, 5000);
		}
		else {
			mBuilder.setLights(0xff0000FF, 0, 0);
		}

		if (makeSound) {
			SurespotLog.v(TAG, "making notification sound");
			defaults |= Notification.DEFAULT_SOUND;
		}

		if (vibrate) {
			SurespotLog.v(TAG, "vibrating notification");
			defaults |= Notification.DEFAULT_VIBRATE;
		}

		mBuilder.setDefaults(defaults);
		mNotificationManager.notify(tag, id, mBuilder.build());
	}
}
