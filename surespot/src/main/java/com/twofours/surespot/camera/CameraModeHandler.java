package com.twofours.surespot.camera;

import android.content.Context;
import android.net.Uri;
import android.support.v4.view.ViewCompat;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.google.android.cameraview.AspectRatio;
import com.google.android.cameraview.CameraView;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.utils.FileUtils;
import com.twofours.surespot.utils.UIUtils;
import com.twofours.surespot.utils.Utils;

import java.io.File;
import java.io.IOException;


/**
 * Created by adam on 5/15/17.
 */

public class CameraModeHandler {
    private static final String TAG = "CameraModeHandler";
    private Context mContext;
    private CameraView mCameraView;
    //private Fotoapparat mFotoapparat;
    private IAsyncCallback<Uri> mPictureTakenCallback;

    public void setupCamera(Context context, View parentView, final int keyboardHeight, final IAsyncCallback<Uri> pictureTakenCallback) {
        mContext = context;
        mPictureTakenCallback = pictureTakenCallback;

        mCameraView = (CameraView) parentView.findViewById(R.id.camera);
        AspectRatio ratio = mCameraView.getAspectRatio();


        Display display = ViewCompat.getDisplay(parentView);
        if (display == null) {
            SurespotLog.d(TAG, "getting display from window manager");
            display = UIUtils.getDisplay(context);
        }

        int rotation = Surface.ROTATION_0;
        if (display != null) {
            rotation = display.getRotation();
            SurespotLog.d(TAG, "got display, rotation: %d", display.getRotation());
        }
        else {
            SurespotLog.d(TAG, "display null");
        }

        ViewGroup.LayoutParams params = mCameraView.getLayoutParams();

        if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {

            //compensate for portrait
            float width = (keyboardHeight / ratio.getX() * ratio.getY());
            params.width = (int) width;
        }
        else {
            float width = (keyboardHeight / ratio.getY()* ratio.getX());
            params.width = (int) width;
        }
        mCameraView.setLayoutParams(params);

        SurespotLog.d(TAG, "ratio: %s, width: %d, height: %d", ratio, params.width, keyboardHeight);
//
//
//        mCameraView.setDisplayOrientation(Surface.ROTATION_0);
//        mCameraView.getPreview().setSize(200,keyboardHeight );


        startCamera();

//        AspectFrameLayout afl = (AspectFrameLayout) parentView.findViewById(R.id.afl);
//        afl.setAspectRatio(200 / keyboardHeight);





        ImageButton fab = (ImageButton) parentView.findViewById(R.id.take_picture);
        fab.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mCameraView.takePicture();
                    }
                });


        mCameraView.addCallback(new com.google.android.cameraview.CameraView.Callback() {
            @Override
            public void onCameraOpened(com.google.android.cameraview.CameraView cameraView) {
                super.onCameraOpened(cameraView);
            }

            @Override
            public void onPictureTaken(com.google.android.cameraview.CameraView cameraView, byte[] data) {
                File f = null;
                try {
                    f = FileUtils.createGalleryImageFile(".jpg");
                    Utils.bytesToFile(data, f);
                }
                catch (IOException e) {
                    SurespotLog.e(TAG, e, "error sending camera image");
                    pictureTakenCallback.handleResponse(null);
                    return;

                }

                String path = f.getAbsolutePath();
                pictureTakenCallback.handleResponse(Uri.fromFile(new File(path)));


            }

        });


    }

    public void startCamera() {
        if (mCameraView != null) {
            mCameraView.start();
        }
    }


    public void stopCamera() {
        if (mCameraView != null) {
            try {
                mCameraView.stop();
            }
            catch (IllegalStateException e) {
                SurespotLog.w(TAG, e, "stop camera");
            }
        }
    }
}
