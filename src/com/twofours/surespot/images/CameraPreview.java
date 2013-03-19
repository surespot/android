package com.twofours.surespot.images;

import java.io.IOException;

import android.content.Context;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.twofours.surespot.common.SurespotLog;

/** A basic Camera preview class */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
	private static final String TAG = "CameraPreview";
	private SurfaceHolder mHolder;
	private Camera mCamera;

	public CameraPreview(Context context) {
		super(context);

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = getHolder();
		// deprecated setting, but required on Android versions prior to 3.0
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		//
		mHolder.addCallback(this);

	}

	public void setCamera(Camera camera) {
		mCamera = camera;
		if (camera != null) {
			try {
				SurespotLog.v(TAG, "setting preview display");
				camera.setPreviewDisplay(mHolder);
			}
			catch (IOException exception) {
				SurespotLog.w(TAG, "IOException caused by setPreviewDisplay()", exception);
			}
			requestLayout();
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		SurespotLog.v(TAG, "surfaceCreated");
		try {
			if (mCamera != null) {
				SurespotLog.v(TAG, "setting preview display");
				mCamera.setPreviewDisplay(holder);
			}
		}
		catch (IOException exception) {
			SurespotLog.w(TAG, "IOException caused by setPreviewDisplay()", exception);
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		// empty. Take care of releasing the Camera preview in your activity.
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		// If your preview can change or rotate, take care of those events here.
		// Make sure to stop the preview before resizing or reformatting it.
//
//		if (mHolder.getSurface() == null) {
//			// preview surface does not exist
//			SurespotLog.v(TAG, "mHolder surface null");
//			return;
//		}
//
//		SurespotLog.v(TAG, "surfaceChanged, starting camera preview");
//		if (mCamera != null) {
//			mCamera.startPreview();
//		}
	}

}