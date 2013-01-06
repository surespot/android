package com.twofours.surespot;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;
import com.google.android.gcm.GCMRegistrar;
import com.loopj.android.http.AsyncHttpResponseHandler;
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
			generateMessageNotification(context, otherUser, "New message", "New message from " + otherUser + ".");
		}
		else {
			String user = intent.getStringExtra("user");			
			generateInviteNotification(context, user, "Friend invite", "Friend invite from " + user + ".");
		}

	}

	@Override
	protected void onRegistered(final Context context, final String id) {
		Log.v(TAG, "Successfully registered for GCM. Saving in SharedPrefs");

		// shoved it in shared prefs
		SharedPreferences settings = context.getSharedPreferences(SurespotConstants.PREFS_FILE, android.content.Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(SurespotConstants.GCM_ID, id);
		editor.commit();

		GCMRegistrar.setRegisteredOnServer(context, true);

		//TODO use password instead of session
		if (NetworkController.hasSession()) {
			NetworkController.registerGcmId(id, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(String result) {
					Log.v(TAG, "Successfully saved GCM id on surespot server.");

				}
			});
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
		TaskStackBuilder stackBuilder = TaskStackBuilder.from(context);
		// if we're logged in, go to the chat, otherwise go to login
		//TODO use password instead of sesson
		/*
		if (NetworkController.hasSession()) {

			Intent mainIntent = new Intent(context, ChatActivity.class);
			mainIntent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, user);

			stackBuilder.addParentStack(ChatActivity.class);
			stackBuilder.addNextIntent(mainIntent);
			PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

			builder.setContentIntent(resultPendingIntent);
		}
		else {*/
			// builder.set
			Intent mainIntent = new Intent(context, StartupActivity.class);
			mainIntent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, user);
			stackBuilder.addNextIntent(mainIntent);
			PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

			builder.setContentIntent(resultPendingIntent);
		//}

		Notification notification = builder.getNotification();
		notification.flags = Notification.FLAG_AUTO_CANCEL;

		notificationManager.notify(1, notification);
	}

	private static void generateInviteNotification(Context context, String user, String title, String message) {
		int icon = R.drawable.ic_launcher;

		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context).setSmallIcon(icon).setContentTitle(title)
				.setContentText(message);
		TaskStackBuilder stackBuilder = TaskStackBuilder.from(context);
		// if we're logged in, go to the chat, otherwise go to login
		//TODO save password instead of session
		/*if (NetworkController.hasSession()) {

			Intent mainIntent = new Intent(context, MainActivity.class);
			stackBuilder.addNextIntent(mainIntent);
			PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_CANCEL_CURRENT);

			builder.setContentIntent(resultPendingIntent);
		}
		else {*/
			// builder.set
			Intent mainIntent = new Intent(context, StartupActivity.class);
			stackBuilder.addNextIntent(mainIntent);
			PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_CANCEL_CURRENT);

			builder.setContentIntent(resultPendingIntent);
		//}

		Notification notification = builder.getNotification();
		notification.flags = Notification.FLAG_AUTO_CANCEL;

		notificationManager.notify(1, notification);
	}

}
