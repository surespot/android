package com.twofours.surespot;

import java.io.IOException;
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
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.google.android.gcm.GCMBaseIntentService;
import com.google.android.gcm.GCMRegistrar;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.SyncHttpClient;
import com.twofours.surespot.activities.StartupActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotConstants.IntentFilters;
import com.twofours.surespot.common.SurespotConstants.IntentRequestCodes;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class GCMIntentService extends GCMBaseIntentService {
	private static final String TAG = "GCMIntentService";
	public static final String SENDER_ID = "428168563991";

	public GCMIntentService() {
		super(SENDER_ID);
		SurespotLog.v(TAG, "GCMIntentService");
	}

	@Override
	protected void onError(Context arg0, String arg1) {
		// TODO Auto-generated method stubgb

	}

	@Override
	protected void onRegistered(final Context context, final String id) {
		// shoved it in shared prefs
		SurespotLog.v(TAG, "Received gcm id, saving it in shared prefs.");
		Utils.putSharedPrefsString(SurespotApplication.getContext(), SurespotConstants.PrefNames.GCM_ID_RECEIVED, id);

		// TODO use password instead of session?
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
				ACRA.getErrorReporter().handleException(e);
				return;
			}

			client.setCookieStore(SurespotApplication.getNetworkController().getCookieStore());

			Map<String, String> params = new HashMap<String, String>();
			params.put("gcmId", id);

			String result = client.post(SurespotConfiguration.getBaseUrl() + "/registergcm", new RequestParams(params));
			// success returns 204 = null result
			if (result == null) {
				SurespotLog.v(TAG, "Successfully saved GCM id on surespot server.");

				// the server and client match, we're golden
				Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.GCM_ID_SENT, id);
				GCMRegistrar.setRegisteredOnServer(context, true);

			}
		}
		else {
			SurespotLog.v(TAG, "Can't save GCM id on surespot server as user is not logged in.");
		}
	}

	@Override
	protected void onUnregistered(Context arg0, String arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void onMessage(Context context, Intent intent) {
		SurespotLog.v(TAG, "received GCM message, extras: " + intent.getExtras());

		String type = intent.getStringExtra("type");
		String to = intent.getStringExtra("to");
		String from = intent.getStringExtra("sentfrom");

		if (type.equals("message")) {
			// if the chat is currently showing don't show a notification
			// TODO setting for this
			if (IdentityController.hasLoggedInUser() && ChatController.getTrackChat() && !ChatController.isPaused()
					&& from.equals(ChatController.getCurrentChat())) {
				SurespotLog.v(TAG, "not displaying notification because the tab is open for it.");
				return;
			}

			String spot = ChatUtils.getSpot(from, to);
			generateNotification(context, IntentFilters.MESSAGE_RECEIVED, from, to, "surespot", to + ": new message from " + from, spot,
					IntentRequestCodes.NEW_MESSAGE_NOTIFICATION);
		}
		else {
			if (type.equals("invite")) {
				generateNotification(context, IntentFilters.INVITE_REQUEST, from, to, "surespot", to + ": friend invite from " + from,
						from, IntentRequestCodes.INVITE_REQUEST_NOTIFICATION);
			}
			else {
				generateNotification(context, IntentFilters.INVITE_RESPONSE, from, to, "surespot", to + ": " + from
						+ " has accepted your friend invite", to, IntentRequestCodes.INVITE_RESPONSE_NOTIFICATION);
			}
		}
	}

	private void generateNotification(Context context, String type, String from, String to, String title, String message, String tag, int id) {

		// get shared prefs
		SharedPreferences pm = context.getSharedPreferences(to, Context.MODE_PRIVATE);
		if (!pm.getBoolean(getString(R.string.pref_notifications_enabled), true)) {
			return;
		}

		int icon = R.drawable.ic_launcher;

		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context).setSmallIcon(icon).setContentTitle(title)
				.setContentText(message);
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		// if we're logged in, go to the chat, otherwise go to login
		Intent mainIntent = null;
		if (to.equals(IdentityController.getLoggedInUser()) && id == IntentRequestCodes.NEW_MESSAGE_NOTIFICATION) {
			SurespotLog.v(TAG, "user already logged in, going directly to chat activity");
			mainIntent = new Intent(context, StartupActivity.class);
			mainIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		}
		else {
			mainIntent = new Intent(context, StartupActivity.class);
		}

		// mainIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

		// mainIntent.setAction("android.intent.action.MAIN");
		// mainIntent.addCategory("android.intent.category.LAUNCHER");

		mainIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_TO, to);
		mainIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_FROM, from);
		mainIntent.putExtra(SurespotConstants.ExtraNames.NOTIFICATION_TYPE, type);

		// stackBuilder.addParentStack(FriendActivity.class);
		stackBuilder.addNextIntent(mainIntent);

		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent((int) new Date().getTime(), PendingIntent.FLAG_CANCEL_CURRENT);

		builder.setContentIntent(resultPendingIntent);

		Notification notification = builder.build();
		notification.flags = Notification.FLAG_AUTO_CANCEL;
		if (pm.getBoolean(getString(R.string.pref_notifications_led), true)) {
			notification.defaults |= Notification.DEFAULT_LIGHTS;
		}
		if (pm.getBoolean(getString(R.string.pref_notifications_sound), true)) {
			notification.defaults |= Notification.DEFAULT_SOUND;
		}
		if (pm.getBoolean(getString(R.string.pref_notifications_vibration), true)) {
			notification.defaults |= Notification.DEFAULT_VIBRATE;
		}

		notificationManager.notify(tag, id, notification);
	}
}
