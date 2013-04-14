package com.twofours.surespot.images;

import java.io.File;

import org.acra.ACRA;

import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;

public class ImageCaptureHandler implements Parcelable {
	private static final String TAG = "ImageCaptureHandler";

	private String mCurrentPhotoPath;
	private String mTo;

	public String getImagePath() {
		return mCurrentPhotoPath;
	}

	public String getTo() {
		return mTo;
	}

	public ImageCaptureHandler(Parcel in) {
		mCurrentPhotoPath = in.readString();
		mTo = in.readString();
	}

	public ImageCaptureHandler(String to) {

		mTo = to;
	}

	public void capture(MainActivity activity) {
		// intent = new Intent(this, ImageCaptureActivity.class);
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

		File f;
		try {
			f = FileUtils.createGalleryImageFile(".jpg");
			mCurrentPhotoPath = f.getAbsolutePath();
			intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));

			activity.startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_CAPTURE_IMAGE);
			if (activity.mDadLogging) {
				ACRA.getErrorReporter().putCustomData("method", "ImageCaptureHandler.capture");
				ACRA.getErrorReporter().handleSilentException(null);
			}
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "capture", e);
		}

	}

	public void handleResult(final MainActivity activity) {
		activity.getChatController().scrollToEnd(mTo);
		// Utils.makeToast(mActivity, mActivity.getString(R.string.uploading_image));
		ChatUtils.uploadPictureMessageAsync(activity, activity.getChatController(), activity.getNetworkController(),
				Uri.fromFile(new File(mCurrentPhotoPath)), mTo, true, new IAsyncCallback<Boolean>() {
					@Override
					public void handleResponse(Boolean result) {
						if (!result) {
							Utils.makeToast(activity, activity.getString(R.string.could_not_upload_image));
						}

						// new File(filename).delete();
					}
				});
		FileUtils.galleryAddPic(activity, mCurrentPhotoPath);

	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(mCurrentPhotoPath);
		dest.writeString(mTo);

	}

}
