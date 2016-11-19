package com.twofours.surespot.images;

import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatManager;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;

import java.io.File;

public class ImageCaptureHandler implements Parcelable {
    private static final String TAG = "ImageCaptureHandler";

    private String mCurrentPhotoPath;
    private String mTo;
    private String mFrom;

    public String getImagePath() {
        return mCurrentPhotoPath;
    }

    public String getTo() {
        return mTo;
    }

    private ImageCaptureHandler(Parcel in) {
        mCurrentPhotoPath = in.readString();
        mFrom = in.readString();
        mTo = in.readString();
    }

    public ImageCaptureHandler(String from, String to) {
        mFrom = from;
        mTo = to;
    }

    public void capture(MainActivity activity) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        File f;
        try {
            f = FileUtils.createGalleryImageFile(".jpg");
            mCurrentPhotoPath = f.getAbsolutePath();
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));

            activity.startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_CAPTURE_IMAGE);
        } catch (Exception e) {
            SurespotLog.w(TAG, "capture", e);
        }

    }

    public void handleResult(final MainActivity activity) {
        ChatController cc = ChatManager.getChatController(mFrom);
        if (cc != null) {
            cc.scrollToEnd(mTo);
            ChatUtils.uploadPictureMessageAsync(
                    activity,
                    cc,
                    Uri.fromFile(new File(mCurrentPhotoPath)),
                    mFrom,
                    mTo,
                    true);

            FileUtils.galleryAddPic(activity, mCurrentPhotoPath);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mCurrentPhotoPath);
        dest.writeString(mFrom);
        dest.writeString(mTo);

    }

    public static final Parcelable.Creator<ImageCaptureHandler> CREATOR = new Parcelable.Creator<ImageCaptureHandler>() {
        public ImageCaptureHandler createFromParcel(Parcel in) {
            return new ImageCaptureHandler(in);
        }

        public ImageCaptureHandler[] newArray(int size) {
            return new ImageCaptureHandler[size];
        }
    };

}
