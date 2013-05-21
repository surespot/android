package com.twofours.surespot.identity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.spongycastle.util.Arrays;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.support.v4.app.NotificationCompat;

import com.twofours.surespot.R;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class IdentityBackupAgent extends BackupAgent {
	private static final String TAG = null;
	NotificationManager mNotificationManager;

	@Override
	public void onCreate() {
		super.onCreate();
		mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	@Override
	public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) throws IOException {

		List<String> names = IdentityController.getIdentityNames(this);
		List<String> deletedNames = IdentityController.getDeletedIdentityNames(this);
		boolean backup = true;

		HashMap<String, byte[]> identityBytes = new HashMap<String, byte[]>(names.size());
		Iterator<String> iterator = names.iterator();
		byte[] currentChecksum = null;

		// load all the identities to compute checksum for identities we are backing up

		while (iterator.hasNext()) {
			String name = iterator.next();
			if (getSharedPreferences(name, MODE_PRIVATE).getBoolean("pref_auto_android_backup_enabled", false)) {
				String filename = FileUtils.getIdentityDir(this) + File.separator + name + IdentityController.IDENTITY_EXTENSION;

				synchronized (IdentityController.IDENTITY_FILE_LOCK) {
					// identity
					FileInputStream fis = new FileInputStream(filename);
					byte[] buffer = Utils.inputStreamToBytes(fis);
					identityBytes.put(name, buffer);
					fis.close();
				}
			}
			else {
				// delete the backup if there is one
				deletedNames.add(name);
			}
		}

		if (identityBytes.size() > 0 || deletedNames.size() > 0) {
			// compute and compare checksum
			try {
				MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
				for (byte[] bytes : identityBytes.values()) {
					digest.update(bytes);
				}
				currentChecksum = digest.digest();

				FileInputStream instream = new FileInputStream(oldState.getFileDescriptor());

				try {
					// Get the checksum from the last backup
					byte[] lastChecksum = Utils.inputStreamToBytes(instream);

					if (Arrays.areEqual(lastChecksum, currentChecksum)) {
						SurespotLog.v(TAG, "won't backup identities because checksum has not changed");

						// Don't back up because the checksum hasn't changed
						backup = false;
					}

					// write checksum
					if (currentChecksum != null) {
						FileOutputStream outstream = new FileOutputStream(newState.getFileDescriptor());
						outstream.write(currentChecksum);
						outstream.close();
					}
				}
				catch (IOException e) {
					// Unable to read state file... be safe and do a backup
				}

			}
			catch (NoSuchAlgorithmException e) {
				SurespotLog.w(TAG, "onBackup could not compute checksum");
				backup = false;
			}
		}

		Iterator<Entry<String, byte[]>> iterator2 = identityBytes.entrySet().iterator();
		while (iterator2.hasNext()) {
			Entry<String, byte[]> entry = iterator2.next();
			String name = entry.getKey();
			if (getSharedPreferences(name, MODE_PRIVATE).getBoolean("pref_auto_android_backup_enabled", false)) {

				synchronized (IdentityController.IDENTITY_FILE_LOCK) {
					byte[] buffer = null;
					int len = 0;

					if (backup) {
						SurespotLog.v(TAG, "backing up identity: " + name);
						// identity
						buffer = entry.getValue();
						len = buffer.length;
						data.writeEntityHeader("identity:" + name, len);
						data.writeEntityData(buffer, len);
					}
					// always backup shared prefs
					String filename = this.getApplicationInfo().dataDir + File.separator + "shared_prefs" + File.separator + name + ".xml";
					SurespotLog.v(TAG, "backing up shared prefs: " + filename);
					FileInputStream fis = new FileInputStream(filename);

					buffer = Utils.inputStreamToBytes(fis);
					len = buffer.length;
					data.writeEntityHeader("sharedPref:" + name, len);
					data.writeEntityData(buffer, len);
					fis.close();

				}

			}

		}

		if (backup) {

			iterator = deletedNames.iterator();

			while (iterator.hasNext()) {
				String name = iterator.next();
				SurespotLog.v(TAG, "deleting identity backup for: %s", name);

				synchronized (IdentityController.IDENTITY_FILE_LOCK) {
					// delete identity backup
					data.writeEntityHeader("identity:" + name, -1);
					String filename = FileUtils.getIdentityDir(this) + File.separator + name
							+ IdentityController.IDENTITY_DELETED_EXTENSION;
					new File(filename).delete();

					// delete shared prefs backup
					data.writeEntityHeader("sharedPref:" + name, -1);
					filename = this.getApplicationInfo().dataDir + File.separator + "shared_prefs" + File.separator + name + ".xml";
					SurespotLog.v(TAG, "deleting shared prefs backup for: %s", name);
					new File(filename).delete();
				}
			}

			if ((identityBytes.size() > 0) || deletedNames.size() > 0) {
				createBackedupNotification(identityBytes.keySet(), deletedNames);
			}
		}

	}

	public void createBackedupNotification(Set<String> backedUp, List<String> deleted) {
		String message = "";

		for (String name : backedUp) {
			message = getString(R.string.identity_backed_up, name);
			showBackupNotification(name, message);
		}

		for (String name : deleted) {
			message = getString(R.string.identity_backup_deleted, name);
			showBackupNotification(name, message);
		}
	}

	private void showBackupNotification(String user, String message) {
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(), // Dummy Intent do nothing
				Intent.FLAG_ACTIVITY_NEW_TASK);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this).setSmallIcon(R.drawable.surespot_logo)
				.setContentTitle(getString(R.string.identity_backup_complete_notification_title)).setContentText(message)
				.setContentIntent(pendingIntent).setAutoCancel(true).setWhen(System.currentTimeMillis());
		mNotificationManager.notify(user, SurespotConstants.IntentRequestCodes.BACKUP_NOTIFICATION, builder.build());
	}

	@Override
	public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) throws IOException {

		String identitydirname = FileUtils.getIdentityDir(this);
		File dir = new File(identitydirname);
		dir.mkdirs();

		while (data.readNextHeader()) {
			String key = data.getKey();

			if (key.startsWith("identity:")) {
				String[] split = key.split(":");
				String name = split[1];

				String filename = identitydirname + File.separator + name + IdentityController.IDENTITY_EXTENSION;
				int dataSize = data.getDataSize();

				synchronized (IdentityController.IDENTITY_FILE_LOCK) {
					FileOutputStream fos = new FileOutputStream(filename);
					SurespotLog.v(TAG, "restoring identity: " + filename);

					byte[] dataBuf = new byte[dataSize];
					data.readEntityData(dataBuf, 0, dataSize);

					fos.write(dataBuf);
					fos.close();
				}
			}
			else {
				if (key.startsWith("sharedPref:")) {
					String[] split = key.split(":");
					String name = split[1];
					String sharedPrefsFile = this.getApplicationInfo().dataDir + File.separator + "shared_prefs" + File.separator + name
							+ ".xml";

					FileOutputStream fos = new FileOutputStream(sharedPrefsFile);
					SurespotLog.v(TAG, "restoring shared prefs: " + sharedPrefsFile);

					int dataSize = data.getDataSize();

					byte[] dataBuf = new byte[dataSize];
					data.readEntityData(dataBuf, 0, dataSize);

					fos.write(dataBuf);
					fos.close();
				}
			}
		}

	}

}
