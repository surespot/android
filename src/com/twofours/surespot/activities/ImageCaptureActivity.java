package com.twofours.surespot.activities;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.CameraPreview;
import com.twofours.surespot.R;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class ImageCaptureActivity extends SherlockActivity {
	private static final String TAG = "ImageCaptureActivity";
	public static final int SOURCE_EXISTING_IMAGE = 1;
	public static final int SOURCE_CAPTURE_IMAGE = 2;
	private static final String COMPRESS_SUFFIX = "compress";
	private static final String CAPTURE_SUFFIX = "capture";

	private ImageView mImageView;
	private Button mSendButton;
	private Button mCancelButton;
	private Button mCaptureButton;
	private File mCapturedImagePath;
	private File mCompressedImagePath;
	private Camera mCamera;
	private OrientationEventListener mOrientationEventListener;
	private int mOrientation;
	private int mCaptureOrientation;
	private int mCameraOrientation;
	private CameraPreview mCameraPreview;
	private View mCameraFrame;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SurespotLog.v(TAG, "onCreate");
		setContentView(R.layout.activity_image_capture);

		mCameraFrame = this.findViewById(R.id.camera_preview);
		mImageView = (ImageView) this.findViewById(R.id.image);
		mSendButton = (Button) this.findViewById(R.id.send);
		mCancelButton = (Button) this.findViewById(R.id.cancel);
		mCaptureButton = (Button) this.findViewById(R.id.capture);

		final String to = getIntent().getStringExtra("to");

		mOrientationEventListener = new OrientationEventListener(this) {
			@Override
			public void onOrientationChanged(int orientation) {
				// SurespotLog.v(TAG, "orientation: " + orientation);
				mOrientation = orientation;
			}
		};

		mSendButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new AsyncTask<Void, Void, Void>() {
					protected Void doInBackground(Void... params) {
						Intent dataIntent = new Intent();
						dataIntent.setData(Uri.fromFile(mCompressedImagePath));
						dataIntent.putExtra("to", to);
						dataIntent.putExtra("filename", mCompressedImagePath.getPath());
						setResult(Activity.RESULT_OK, dataIntent);
						finish();
						return null;
					};

				}.execute();
			}
		});
		mCaptureButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// if we have an image captured already, they are clicking reject
				if (mCompressedImagePath != null) {
					initialize();
				}
				else {
					mCaptureOrientation = mOrientation;
					mCaptureButton.setEnabled(false);
					mSendButton.setEnabled(false);
					mCamera.takePicture(null, null, null, new PictureCallback() {

						@Override
						public void onPictureTaken(final byte[] data, Camera camera) {

							// SurespotLog.v(TAG, "onPictureTaken");
							new AsyncTask<Void, Void, Bitmap>() {

								@Override
								protected Bitmap doInBackground(Void... params) {
									if (data == null) {
										SurespotLog.e(TAG, "onPictureTaken", new IOException("could not get postview image data"));
									}
									else {
										try {
											deleteCapturedImage();
											mCapturedImagePath = createImageFile(CAPTURE_SUFFIX);

											FileOutputStream fos = new FileOutputStream(mCapturedImagePath);
											fos.write(data);
											fos.close();

											int rotation = (mCaptureOrientation + 45) / 90 * 90;

											// leave it upside down
											if (rotation == 360 || rotation == 180) {
												rotation = 0;
											}
											deleteCompressedImage();
											mCompressedImagePath = createImageFile(COMPRESS_SUFFIX);
											Bitmap bitmap = null;
											if (mCapturedImagePath != null) {
												bitmap = compressImage(Uri.fromFile(mCapturedImagePath), rotation
														+ mCameraOrientation);
												deleteCapturedImage();
											}

											return bitmap;
										}
										catch (FileNotFoundException e) {
											SurespotLog.w(TAG, "File not found: " + e.getMessage());
										}
										catch (IOException e) {
											SurespotLog.w(TAG, "Error accessing file: " + e.getMessage());
										}
									}
									return null;
								}

								@Override
								protected void onPostExecute(Bitmap bitmap) {
									if (bitmap != null) {

										mCaptureButton.setText("reject");

										mImageView.setImageBitmap(bitmap);
										mImageView.setVisibility(View.VISIBLE);
										mCameraFrame.setVisibility(View.GONE);
										mSendButton.setEnabled(true);
									}
									else {
										//Utils.makeToast(ImageCaptureActivity.this, "could not capture image");
										mSendButton.setEnabled(false);
									}
									mCaptureButton.setEnabled(true);
								}
							}.execute();
						}
					});
				}
			}
		});

		mCancelButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				deleteCapturedImage();
				deleteCompressedImage();
				finish();
			}
		});

		mCameraPreview = new CameraPreview(ImageCaptureActivity.this);
		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
		preview.addView(mCameraPreview);
		Utils.configureActionBar(this, getString(R.string.capture_image), "send to " + to, true);

	}

	private void initialize() {
		deleteCompressedImage();
		mCaptureButton.setText("capture");
		mImageView.setVisibility(View.GONE);
		mCameraFrame.setVisibility(View.VISIBLE);
		mImageView.setImageBitmap(null);
		mSendButton.setEnabled(false);
		mCamera.startPreview();
	}

	@Override
	protected void onResume() {
		super.onResume();
		SurespotLog.v(TAG, "onResume");
		findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
		initCamera();
		mOrientationEventListener.enable();
	}

	private void initCamera() {
		new AsyncTask<Void, Void, Camera>() {
			protected Camera doInBackground(Void... params) {
				return getCameraInstance();
			};

			protected void onPostExecute(Camera result) {
				if (result != null) {
					mCamera = result;
					mCameraPreview.setCamera(mCamera);
					findViewById(R.id.progressBar).setVisibility(View.GONE);
					if (!mSendButton.isEnabled()) {
						initialize();
					}
				}
			};
		}.execute();
	}

	/** A safe way to get an instance of the Camera object. */
	private Camera getCameraInstance() {
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

	// TODO handle forward camera
	private void setCameraDisplayOrientation(Activity activity, int cameraId, android.hardware.Camera camera) {
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

	@Override
	protected void onPause() {
		super.onPause();
		mOrientationEventListener.disable();
		new AsyncTask<Void, Void, Void>() {
			protected Void doInBackground(Void... params) {
				releaseCamera();
				return null;
			};
		}.execute();
	}

	private void releaseCamera() {
		if (mCamera != null) {

			mCamera.stopPreview();
			mCameraPreview.setCamera(null);
			mCamera.release(); // release the camera for other applications
			mCamera = null;
		}
	}

	private synchronized File createImageFile(String suffix) throws IOException {

		// Create a unique image file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = "image" + "_" + timeStamp + "_" + suffix;

		File dir = FileUtils.getImageCaptureDir(this);
		if (FileUtils.ensureDir(dir)) {
			File file = new File(dir.getPath(), imageFileName);
			file.createNewFile();
			// SurespotLog.v(TAG, "createdFile: " + file.getPath());
			return file;
		}
		else {
			throw new IOException("Could not create image temp file dir: " + dir.getPath());
		}

	}

	private void deleteCompressedImage() {
		if (mCompressedImagePath != null) {
			// SurespotLog.v(TAG, "deleteCompressedImage: " + mCompressedImagePath.getPath());
			mCompressedImagePath.delete();
			mCompressedImagePath = null;
		}
	}

	private void deleteCapturedImage() {
		if (mCapturedImagePath != null) {
			// SurespotLog.v(TAG, "deleteCapturedImage: " + mCapturedImagePath.getPath());
			// Thread.dumpStack();
			mCapturedImagePath.delete();
			mCapturedImagePath = null;
		}
	}

	private Bitmap compressImage(final Uri uri, final int rotate) {
		final Uri finalUri;
		try {
			if (mCompressedImagePath == null) {
				mCompressedImagePath = createImageFile(COMPRESS_SUFFIX);
			}

			// if it's an external image save it first
			if (uri.getScheme().startsWith("http")) {
				FileOutputStream fos = new FileOutputStream(mCompressedImagePath);
				InputStream is = new URL(uri.toString()).openStream();
				byte[] buffer = new byte[1024];
				int len;
				while ((len = is.read(buffer)) != -1) {
					fos.write(buffer, 0, len);
				}

				fos.close();
				finalUri = Uri.fromFile(mCompressedImagePath);
			}
			else {
				finalUri = uri;
			}
		}
		catch (IOException e1) {
			SurespotLog.w(TAG, "compressImage", e1);
			// Utils.makeLongToast(this, "could not load image");
			finish();
			return null;
		}

		// scale, compress and save the image
		Bitmap bitmap = ChatUtils.decodeSampledBitmapFromUri(ImageCaptureActivity.this, finalUri, rotate);
		try {

			if (bitmap != null && mCompressedImagePath != null) {
				// SurespotLog.v(TAG, "compressingImage to: " + mCompressedImagePath);
				FileOutputStream fos = new FileOutputStream(mCompressedImagePath);

				bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos);
				fos.close();
				// SurespotLog.v(TAG, "done compressingImage to: " + mCompressedImagePath);
			}
			else {
				//Utils.makeLongToast(this, "could not load image");
				finish();
			}
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

}
