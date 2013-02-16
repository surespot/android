package com.twofours.surespot.activities;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.Bundle;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.CameraPreview;
import com.twofours.surespot.R;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class ImageSelectActivity extends SherlockActivity {
	private static final String TAG = "ImageSelectActivity";
	public static final int REQUEST_EXISTING_IMAGE = 1;
	public static final int REQUEST_CAPTURE_IMAGE = 2;

	private ImageView mImageView;
	private Button mSendButton;
	private Button mCancelButton;
	private Button mCaptureButton;
	private File mCapturedImagePath;
	private File mCompressedImagePath;
	private int mSource;
	private Camera mCamera;
	private OrientationEventListener mOrientationEventListener;
	private int mOrientation;
	private int mCaptureOrientation;
	private int mCameraOrientation;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_image_select);

		mOrientationEventListener = new OrientationEventListener(this) {

			@Override
			public void onOrientationChanged(int orientation) {
				// SurespotLog.v(TAG, "orientation: " + orientation);
				mOrientation = orientation;

			}
		};

		final String to = getIntent().getStringExtra("to");

		mImageView = (ImageView) this.findViewById(R.id.image);
		mSendButton = (Button) this.findViewById(R.id.send);
		mSendButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent dataIntent = new Intent();
				dataIntent.setData(Uri.fromFile(mCompressedImagePath));

				// dataIntent.setData(mUri);
				dataIntent.putExtra("to", to);
				dataIntent.putExtra("filename", mCompressedImagePath.getPath());
				// if (getParent() == null) {
				setResult(Activity.RESULT_OK, dataIntent);
				// }
				// else {
				// getParent().setResult(Activity.RESULT_OK, data);
				// }
				finish();

			}

		});

		mCaptureButton = (Button) this.findViewById(R.id.capture);
		mCaptureButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mCaptureOrientation = mOrientation;
				mCamera.takePicture(null, null, new PictureCallback() {

					@Override
					public void onPictureTaken(byte[] data, Camera camera) {

						if (data == null) {
							SurespotLog.e(TAG, "onPictureTaken", new IOException("could not get postview image data"));
						}
						else {
							try {
								deleteCapturedImage();

								mCapturedImagePath = createImageFile();
								FileOutputStream fos = new FileOutputStream(mCapturedImagePath);
								fos.write(data);
								fos.close();

								// portrait orientation is really 90 not 0
								int rotation = (mCaptureOrientation + 45) / 90 * 90;

								// leave it upside down
								if (rotation == 360 || rotation == 180) {
									rotation = 0;
								}

								// compress image
								mImageView.setImageBitmap(compressImage(Uri.fromFile(mCapturedImagePath), rotation + mCameraOrientation));
							}
							catch (FileNotFoundException e) {
								SurespotLog.d(TAG, "File not found: " + e.getMessage());
							}
							catch (IOException e) {
								SurespotLog.d(TAG, "Error accessing file: " + e.getMessage());
							}

						}
					}
				});
			}

		});

		mCancelButton = (Button) this.findViewById(R.id.cancel);
		mCancelButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mCompressedImagePath = null;
				finish();
			}
		});

		mSource = getIntent().getIntExtra("source", 0);

		if (Camera.getNumberOfCameras() == 0) {

			// if we don't have a camera we shouldn't be here but in case we are let them pick an image
			mSource = REQUEST_EXISTING_IMAGE;
		}

		switch (mSource) {
		case REQUEST_EXISTING_IMAGE:

			try {
				createImageFile();
			}
			catch (IOException e1) {
				// TODO tell user
				SurespotLog.w(TAG, "selectImage", e1);
				finish();
			}
			Intent intent = new Intent();
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_GET_CONTENT);
			Utils.configureActionBar(this, "select image", to, true);
			startActivityForResult(Intent.createChooser(intent, "select Image"), REQUEST_EXISTING_IMAGE);
			break;

		case REQUEST_CAPTURE_IMAGE:

			captureImage();
			// Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
			// Utils.configureActionBar(this, "capture image", to, true);
			// try {
			// createImageFile();
			// cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mCompressedImagePath));
			// startActivityForResult(cameraIntent, REQUEST_CAPTURE_IMAGE);
			// }
			// catch (IOException e) {
			// // TODO tell user
			// SurespotLog.w(TAG, "selectImage", e);
			// finish();
			// }
			break;

		default:
			finish();
		}

	}

	private void captureImage() {
		mCaptureButton.setVisibility(View.VISIBLE);
		mCamera = getCameraInstance();
		if (mCamera != null) {
			CameraPreview cameraPreview = new CameraPreview(this, mCamera);
			FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
			preview.setVisibility(View.VISIBLE);
			preview.addView(cameraPreview);
		}
	}

	/** A safe way to get an instance of the Camera object. */
	public Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
			setCameraDisplayOrientation(this, 0, c);
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "getCameraInstance", e);
			// Camera is not available (in use or does not exist)
		}

		return c; // returns null if camera is unavailable
	}

	public void setCameraDisplayOrientation(Activity activity, int cameraId, android.hardware.Camera camera) {
		android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		android.hardware.Camera.getCameraInfo(cameraId, info);
		int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
		int degrees = 0;
		switch (rotation) {
		case Surface.ROTATION_0:
			degrees = 0;
			break;
		case Surface.ROTATION_90:
			degrees = 90;
			break;
		case Surface.ROTATION_180:
			degrees = 180;
			break;
		case Surface.ROTATION_270:
			degrees = 270;
			break;
		}

		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			result = (info.orientation + degrees) % 360;
			result = (360 - result) % 360; // compensate the mirror
		}
		else { // back-facing
			SurespotLog.v(TAG, "camera orientation: " + info.orientation);
			mCameraOrientation = info.orientation;
			result = (info.orientation - degrees + 360) % 360;
		}

		camera.setDisplayOrientation(result);
	}

	@SuppressWarnings("static-access")
	private File createImageFile() throws IOException {

		// Create an image file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = "image" + "_" + timeStamp;

		File dir = FileUtils.getImageCaptureDir(this);

		if (FileUtils.ensureDir(dir)) {

			return new File(dir.getPath(), imageFileName);

		}
		else {
			throw new IOException("Could not create image temp file dir: " + dir.getPath());
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getSupportMenuInflater().inflate(R.menu.activity_image_select, menu);
		return true;
	}

	private void deleteCompressedImage() {
		if (mCompressedImagePath != null) {
			mCompressedImagePath.delete();
			mCompressedImagePath = null;
		}
	}

	private void deleteCapturedImage() {
		if (mCapturedImagePath != null) {
			mCapturedImagePath.delete();
			mCapturedImagePath = null;
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			// TODO on thread
			Uri uri = data.getData();
			// scale, compress and save the image
			mImageView.setImageBitmap(compressImage(uri, -1));
		}
		else {
			finish();
		}

	}

	private Bitmap compressImage(Uri uri, int rotate) {
		// scale, compress and save the image
		Bitmap bitmap = ChatUtils.decodeSampledBitmapFromUri(this, uri, rotate);
		try {
			deleteCompressedImage();
			mCompressedImagePath = createImageFile();
			FileOutputStream fos = new FileOutputStream(mCompressedImagePath);

			if (bitmap != null) {
				bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos);
				bitmap = null;
			}
			fos.close();
			mSendButton.setEnabled(true);
			return bitmap;

		}
		catch (IOException e) {
			SurespotLog.w(TAG, "onActivityResult", e);
			if (mCompressedImagePath != null) {
				mCompressedImagePath.delete();
				mCompressedImagePath = null;
			}
			return null;
		}

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();

			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}

	@Override
	protected void onResume() {
		super.onResume();
		mOrientationEventListener.enable();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mOrientationEventListener.disable();
		releaseCamera();
	}

	private void releaseCamera() {
		if (mCamera != null) {
			mCamera.release(); // release the camera for other applications
			mCamera = null;
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// super.onConfigurationChanged(newConfig);

		// Checks the orientation of the screen
		if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
			SurespotLog.v(TAG, "orientationChanged to landscape");
		}
		else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
			SurespotLog.v(TAG, "orientationChanged to portrait");
		}
	}
}
