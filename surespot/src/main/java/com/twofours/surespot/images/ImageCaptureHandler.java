package com.twofours.surespot.images;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;

import com.twofours.surespot.BuildConfig;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatManager;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.utils.FileUtils;

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

            Uri photoURI = FileProvider.getUriForFile(activity,BuildConfig.APPLICATION_ID + ".provider",f);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

            //fix permission issue
            //https://medium.com/@quiro91/sharing-files-through-intents-part-2-fixing-the-permissions-before-lollipop-ceb9bb0eec3a
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                intent.setClipData(ClipData.newRawUri("", photoURI));
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            activity.startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_CAPTURE_IMAGE);
        } catch (Exception e) {
            SurespotLog.w(TAG,e, "capture");
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
