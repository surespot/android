package com.twofours.surespot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.jce.interfaces.ECPrivateKey;
import org.spongycastle.jce.interfaces.ECPublicKey;

import android.content.Context;

import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;

public class IdentityController {
	private static final String TAG = "IdentityController";
	public static final String IDENTITY_EXTENSION = ".ssi";

	public static void saveIdentity(Context context, SurespotIdentity identity) {
		// TODO thread

		ECPublicKey ecpub = (ECPublicKey) identity.getKeyPair().getPublic();
		ECPrivateKey ecpriv = (ECPrivateKey) identity.getKeyPair().getPrivate();

		// SurespotLog.d("ke","encoded public key: " +
		// ecpk.getEncoded().toString());
		// pair.getPublic().
		// ecpk.getW().;
		// ecprik.getD().toByteArray();
		String generatedPrivDHex = new String(Utils.base64Encode(ecpriv.getD().toByteArray()));

		String publicKey = EncryptionController.encodePublicKey(ecpub);
		SurespotLog.d("ke", "generated public key:" + publicKey);
		SurespotLog.d("ke", "generated private key d:" + generatedPrivDHex);

		// save keypair in shared prefs json format (hex for now) TODO
		// use something other than hex

		JSONObject json = new JSONObject();
		try {
			json.putOpt("username", identity.getUsername());
			json.putOpt("private_key", generatedPrivDHex);
			json.putOpt("public_key", publicKey);
			File cacheDir = FileUtils.getDiskCacheDir(context, SurespotConstants.FileLocations.IDENTITIES);
			String identityDir = cacheDir.getPath() + File.separator;
			if (!FileUtils.ensureDir(identityDir)) {
				SurespotLog.e(TAG, "Could not create identity cache dir.", new RuntimeException("Could not create identity cache dir."));
				return;
			}

			String identityFile = identityDir + identity.getUsername() + IDENTITY_EXTENSION;

			SurespotLog.v(TAG, "saving identity: " + identityFile);

			FileOutputStream fos = new FileOutputStream(identityFile);
			fos.write(json.toString().getBytes());
			fos.close();
		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "saveIdentity", e);
		}
		catch (FileNotFoundException e) {
			SurespotLog.w(TAG, "saveIdentity", e);
		}
		catch (IOException e) {
			SurespotLog.w(TAG, "saveIdentity", e);
		}

	}

	public static List<String> getIdentityNames(Context context) {
		File cacheDir = FileUtils.getDiskCacheDir(context, SurespotConstants.FileLocations.IDENTITIES);
		ArrayList<String> identityNames = new ArrayList<String>();
		for (File f : cacheDir.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String filename) {
				return filename.endsWith(IDENTITY_EXTENSION);
			}
		})) {
			identityNames.add(f.getName().substring(0, f.getName().length() - IDENTITY_EXTENSION.length()));
		}

		return identityNames;

	}
}
