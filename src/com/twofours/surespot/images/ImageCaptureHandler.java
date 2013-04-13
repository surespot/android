package com.twofours.surespot.images;

import java.io.File;

import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;

import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;

public class ImageCaptureHandler {
	private static final String TAG = "ImageCaptureHandler";
	private MainActivity mActivity;
	private String mCurrentPhotoPath;
	private String mTo;

	public ImageCaptureHandler(MainActivity activity, String to) {
		mActivity = activity;
		mTo = to;
	}

	public void capture() {
		// intent = new Intent(this, ImageCaptureActivity.class);
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

		File f;
		try {
			f = FileUtils.createGalleryImageFile(".jpg");
			mCurrentPhotoPath = f.getAbsolutePath();
			intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));

			mActivity.startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_CAPTURE_IMAGE);
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "capture", e);
		}

	}

	
	public void handleResult() {
		mActivity.getChatController().scrollToEnd(mTo);
		//Utils.makeToast(mActivity, mActivity.getString(R.string.uploading_image));
		ChatUtils.uploadPictureMessageAsync(mActivity, mActivity.getChatController(),mActivity.getNetworkController(), Uri.fromFile(new File(mCurrentPhotoPath)), mTo, true, new IAsyncCallback<Boolean>() {
			@Override
			public void handleResponse(Boolean result) {
				if (!result) {
					Utils.makeToast(mActivity, mActivity.getString(R.string.could_not_upload_image));
				}
				

				// new File(filename).delete();
			}
		});
		FileUtils.galleryAddPic(mActivity, mCurrentPhotoPath);
		
	}

	
}
