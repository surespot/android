package com.twofours.surespot.images;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import com.twofours.surespot.R;
import com.twofours.surespot.R.string;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;

public class ImageCaptureHandler {
	private static final String TAG = "ImageCaptureHandler";
	private Activity mActivity;
	private String mCurrentPhotoPath;
	private String mTo;

	public ImageCaptureHandler(Activity activity, String to) {
		mActivity = activity;
		mTo = to;
	}

	public void capture() {
		// intent = new Intent(this, ImageCaptureActivity.class);
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

		File f;
		try {
			f = createImageFile(".jpg");
			mCurrentPhotoPath = f.getAbsolutePath();
			intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));

			mActivity.startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_CAPTURE_IMAGE);
		}
		catch (IOException e) {
			SurespotLog.v(TAG, "capture", e);
		}

	}

	private File createImageFile(String suffix) throws IOException {

		// Create a unique image file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = "image" + "_" + timeStamp + "_" + suffix;

		File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "surespot");
		if (FileUtils.ensureDir(dir)) {
			File file = new File(dir.getPath(), imageFileName);
			file.createNewFile();
			file.setWritable(true, false);
			SurespotLog.v(TAG, "createdFile: " + file.getPath());
			return file;
		}
		else {
			throw new IOException("Could not create image temp file dir: " + dir.getPath());
		}

	}

	public void handleResult() {

		ChatUtils.uploadPictureMessageAsync(mActivity, Uri.fromFile(new File(mCurrentPhotoPath)), mTo, true, mCurrentPhotoPath, new IAsyncCallback<Boolean>() {
			@Override
			public void handleResponse(Boolean result) {
				if (result) {
					Utils.makeToast(mActivity, mActivity.getString(R.string.image_successfully_uploaded));
				}
				else {
					Utils.makeToast(mActivity, mActivity.getString(R.string.could_not_upload_image));
				}

				// new File(filename).delete();
			}
		});

		galleryAddPic();
	}

	private void galleryAddPic() {
		Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		File f = new File(mCurrentPhotoPath);
		Uri contentUri = Uri.fromFile(f);
		mediaScanIntent.setData(contentUri);
		mActivity.sendBroadcast(mediaScanIntent);
	}
}
