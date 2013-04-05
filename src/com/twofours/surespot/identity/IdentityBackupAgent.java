/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twofours.surespot.identity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.os.ParcelFileDescriptor;

import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class IdentityBackupAgent extends BackupAgent {
	private static final String TAG = null;

	@Override
	public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) throws IOException {

		List<String> names = IdentityController.getIdentityNames(this);

		for (String name : names) {
			if (getSharedPreferences(name, MODE_PRIVATE).getBoolean("pref_auto_android_backup_enabled", false)) {
				String filename = FileUtils.getIdentityDir(this) + File.separator + name + IdentityController.IDENTITY_EXTENSION;
				SurespotLog.v(TAG, "backing up identity: " + filename);

				synchronized (IdentityController.IDENTITY_FILE_LOCK) {
					FileInputStream fis = new FileInputStream(filename);

					byte[] buffer = Utils.inputStreamToBytes(fis);
					int len = buffer.length;
					data.writeEntityHeader("identity:" + name, len);
					data.writeEntityData(buffer, len);
					fis.close();
				}

				filename = this.getApplicationInfo().dataDir + File.separator + "shared_prefs" + File.separator + name + ".xml";
				SurespotLog.v(TAG, "backing up shared prefs: " + filename);
				FileInputStream fis = new FileInputStream(filename);

				byte[] buffer = Utils.inputStreamToBytes(fis);
				int len = buffer.length;
				data.writeEntityHeader("sharedPref:" + name, len);
				data.writeEntityData(buffer, len);
				fis.close();
			}
		}

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
					String sharedPrefsFile = this.getApplicationInfo().dataDir + File.separator + "shared_prefs" + File.separator + name + ".xml";

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
