package com.twofours.surespot.camera;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.widget.ImageButton;

import com.google.android.cameraview.CameraView;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.utils.FileUtils;
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


        //find best size for preview
//        AspectRatio bestRatio = null;
//        Set<AspectRatio> aspectRatios = mGoogleCameraView.getSupportedAspectRatios();
//        for (AspectRatio ar : aspectRatios) {
////            if (ar.getY() > keyboardHeight) {
////                continue;
////            }
////            if (bestRatio == null || bestRatio.getY() < ar.getY()) {
////                bestRatio = ar;
////            }
//            SurespotLog.d(TAG, "ratio x: %d, ratio y: %d", ar.getX(), ar.getY());
//        }
//
//        //SurespotLog.d(TAG, "keyboard height: %d, setting camera  ratio x: %d, ratio y: %d, ratio: %s", keyboardHeight,bestRatio.getX(), bestRatio.getY(),  bestRatio);
//        mGoogleCameraView.setAspectRatio(bestRatio);

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
