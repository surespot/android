package com.twofours.surespot;

import java.util.HashMap;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;
import com.google.android.gcm.GCMRegistrar;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.SyncHttpClient;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.ui.activities.StartupActivity;

public class GCMIntentService extends GCMBaseIntentService

{
	private static final String TAG = "GCMIntentService";
	public static final String SENDER_ID = "428168563991";

	public GCMIntentService() {
		super(SENDER_ID);
		Log.v(TAG, "GCMIntentService");

	}

	@Override
	protected void onError(Context arg0, String arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void onMessage(Context context, Intent intent) {
		Log.v(TAG, "received GCM message, extras: " + intent.getExtras());

		String type = intent.getStringExtra("type");
		if (type.equals("message")) {
			String to = intent.getStringExtra("to");
			String from = intent.getStringExtra("sentfrom");
			String otherUser = Utils.getOtherUser(from, to);
			generateMessageNotification(context, otherUser, "surespot", "new message from " + otherUser);
		}
		else {
			String user = intent.getStringExtra("user");
			generateInviteNotification(context, user, "surespot", "friend invite from " + user);
		}

	}

	@Override
	protected void onRegistered(final Context context, final String id) {
		// shoved it in shared prefs
		Log.v(TAG, "Received gcm id, saving it in shared prefs.");
		Utils.putSharedPrefsString(SurespotConstants.PrefNames.GCM_ID_RECEIVED, id);

		// TODO use password instead of session?
		// TODO retries?
		if (NetworkController.hasSession()) {
			Log.v(TAG, "Attempting to register gcm id on surespot server.");
			// do this synchronously so android doesn't kill the service thread before it's done

			SyncHttpClient client = new SyncHttpClient() {

				@Override
				public String onRequestFailed(Throwable arg0, String arg1) {
					Log.v(TAG, "Error saving gcmId on surespot server: " + arg1);
					return "failed";
				}
			};

			client.setCookieStore(NetworkController.getCookieStore());

			Map<String, String> params = new HashMap<String, String>();
			params.put("gcmId", id);

			String result = client.post(SurespotConstants.BASE_URL + "/registergcm", new RequestParams(params));
			// success returns 204 = null result
			if (result == null) {
				Log.v(TAG, "Successfully saved GCM id on surespot server.");

				// the server and client match, we're golden
				Utils.putSharedPrefsString(SurespotConstants.PrefNames.GCM_ID_SENT, id);
				GCMRegistrar.setRegisteredOnServer(context, true);

			}
		}
		else {
			Log.v(TAG, "Can't save GCM id on surespot server as user is not logged in.");
		}
	}

	@Override
	protected void onUnregistered(Context arg0, String arg1) {
		// TODO Auto-generated method stub

	}

	private static void generateMessageNotification(Context context, String user, String title, String message) {
		int icon = R.drawable.ic_launcher;

		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context).setSmallIcon(icon).setContentTitle(title)
				.setContentText(message);
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		// if we're logged in, go to the chat, otherwise go to login

		Intent mainIntent = new Intent(context, StartupActivity.class);
		mainIntent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, user);
		stackBuilder.addNextIntent(mainIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_CANCEL_CURRENT);

		builder.setContentIntent(resultPendingIntent);

		Notification notification = builder.build();
		notification.flags = Notification.FLAG_AUTO_CANCEL;
		notification.defaults |= Notification.DEFAULT_LIGHTS;
		notification.defaults |= Notification.DEFAULT_SOUND;
		notification.defaults |= Notification.DEFAULT_VIBRATE;

		notificationManager.notify(1, notification);
	}

	private static void generateInviteNotification(Context context, String user, String title, String message) {
		int icon = R.drawable.ic_launcher;

		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context).setSmallIcon(icon).setContentTitle(title)
				.setContentText(message);
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

		Intent mainIntent = new Intent(context, StartupActivity.class);
		stackBuilder.addNextIntent(mainIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_CANCEL_CURRENT);

		builder.setContentIntent(resultPendingIntent);

		Notification notification = builder.build();
		notification.flags = Notification.FLAG_AUTO_CANCEL;
		notification.defaults |= Notification.DEFAULT_LIGHTS;
		notification.defaults |= Notification.DEFAULT_SOUND;
		notification.defaults |= Notification.DEFAULT_VIBRATE;

		notificationManager.notify(1, notification);
	}

}
