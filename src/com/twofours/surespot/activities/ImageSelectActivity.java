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
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.CameraPreview;
import com.twofours.surespot.R;
import com.twofours.surespot.chat.ChatUtils;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class ImageSelectActivity extends SherlockActivity {
	private static final String TAG = "ImageSelectActivity";
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

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_image_select);

		mImageView = (ImageView) this.findViewById(R.id.image);
		mSendButton = (Button) this.findViewById(R.id.send);
		mCancelButton = (Button) this.findViewById(R.id.cancel);
		mCaptureButton = (Button) this.findViewById(R.id.capture);

		final String to = getIntent().getStringExtra("to");
		final int source = getIntent().getIntExtra("source", SOURCE_EXISTING_IMAGE);

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
					deleteCompressedImage();
					mCaptureButton.setText("capture");
					mImageView.setVisibility(View.GONE);
					mImageView.setImageBitmap(null);
					mSendButton.setEnabled(false);
					mCamera.startPreview();
				}
				else {
					mCaptureOrientation = mOrientation;
					mCaptureButton.setEnabled(false);
					mSendButton.setEnabled(false);					
					mCamera.takePicture(null, null, null, new PictureCallback() {

						@Override
						public void onPictureTaken(final byte[] data, Camera camera) {

							// SurespotLog.v(TAG, "onPictureTaken");
							new AsyncTask<Void, Void, Boolean>() {

								@Override
								protected Boolean doInBackground(Void... params) {
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
											compressImage(Uri.fromFile(mCapturedImagePath), rotation + mCameraOrientation);
											deleteCapturedImage();

											return true;
										}
										catch (FileNotFoundException e) {
											SurespotLog.w(TAG, "File not found: " + e.getMessage());
										}
										catch (IOException e) {
											SurespotLog.w(TAG, "Error accessing file: " + e.getMessage());
										}
									}
									return false;
								}

								@Override
								protected void onPostExecute(Boolean result) {
									if (result) {
										
										mCaptureButton.setText("reject");
										Uri uri = Uri.fromFile(mCompressedImagePath);										
										mImageView.setImageURI(uri);
										mImageView.setVisibility(View.VISIBLE);																	
										mSendButton.setEnabled(true);
									}
									else {
										Utils.makeToast(ImageSelectActivity.this, "could not capture image");
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

		switch (source) {
		case SOURCE_EXISTING_IMAGE:
			Utils.configureActionBar(this, getString(R.string.select_image), to, false);
			// TODO paid version allows any file
			Intent intent = new Intent();
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_GET_CONTENT);
			startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
					SurespotConstants.IntentRequestCodes.REQUEST_EXISTING_IMAGE);
			return;

		case SOURCE_CAPTURE_IMAGE:
			findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
			mCaptureButton.setVisibility(View.VISIBLE);
			Utils.configureActionBar(this, getString(R.string.capture_image), to, true);
			initCamera();
			break;

		default:
			finish();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		SurespotLog.v(TAG, "onActivityResult, requestCode: " + requestCode);

		switch (requestCode) {
		case SurespotConstants.IntentRequestCodes.REQUEST_EXISTING_IMAGE:
			if (resultCode == RESULT_OK) {

				mImageView.setVisibility(View.VISIBLE);

				new AsyncTask<Void, Void, Bitmap>() {
					@Override
					protected Bitmap doInBackground(Void... params) {
						Uri uri = data.getData();
						// scale, compress and save the image
						return compressImage(uri, -1);
					}

					protected void onPostExecute(Bitmap result) {
						if (result != null) {
							mImageView.setImageBitmap(result);
							Animation fadeIn = new AlphaAnimation(0, 1);
							fadeIn.setDuration(1000);
							mImageView.startAnimation(fadeIn);

							mSendButton.setEnabled(true);
						}
						else {
							mSendButton.setEnabled(false);
						}
					}
				}.execute();
			}
			else {
				finish();
			}
			break;
		}
	}

	private void initCamera() {
		new AsyncTask<Void, Void, Camera>() {
			protected Camera doInBackground(Void... params) {
				return getCameraInstance();
			};

			protected void onPostExecute(Camera result) {
				if (result != null) {
					mCamera = result;
					mCameraPreview = new CameraPreview(ImageSelectActivity.this, mCamera);
					FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
					preview.setVisibility(View.VISIBLE);
					preview.addView(mCameraPreview);
					findViewById(R.id.progressBar).setVisibility(View.GONE);
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
			//SurespotLog.v(TAG, "deleteCompressedImage: " + mCompressedImagePath.getPath());
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
			Utils.makeLongToast(this, "could not load image");
			finish();
			return null;
		}

		// scale, compress and save the image
		Bitmap bitmap = ChatUtils.decodeSampledBitmapFromUri(ImageSelectActivity.this, finalUri, rotate);
		try {

			if (bitmap != null) {				
				// SurespotLog.v(TAG, "compressingImage to: " + mCompressedImagePath);
				FileOutputStream fos = new FileOutputStream(mCompressedImagePath);

				bitmap.compress(Bitmap.CompressFormat.JPEG, 75, fos);
				fos.close();
				// SurespotLog.v(TAG, "done compressingImage to: " + mCompressedImagePath);
			}
			else {
				Utils.makeLongToast(this, "could not load image");
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

	@Override
	protected void onResume() {
		super.onResume();
		mOrientationEventListener.enable();
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
			mCamera.release(); // release the camera for other applications
			mCamera = null;
		}
	}
}
