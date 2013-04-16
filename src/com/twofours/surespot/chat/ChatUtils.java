package com.twofours.surespot.chat;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore.Images;
import ch.boye.httpclientandroidlib.androidextra.Base64;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.ImageDownloader;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class ChatUtils {
	private static final String TAG = "ChatUtils";

	public static String getOtherUser(String from, String to) {
		return to.equals(IdentityController.getLoggedInUser()) ? from : to;
	}

	public static String getSpot(String from, String to) {
		return (to.compareTo(from) < 0 ? to + ":" + from : from + ":" + to);
	}

	public static String getSpot(SurespotMessage message) {
		return getSpot(message.getTo(), message.getFrom());
	}

	public static String getOtherSpotUser(String spot, String user) {
		String[] split = spot.split(":");

		return split[0].equals(user) ? split[1] : split[0];
	}

	public static SurespotMessage buildPlainMessage(String to, String mimeType, String plainData, String iv) {
		SurespotMessage chatMessage = new SurespotMessage();
		chatMessage.setFrom(IdentityController.getLoggedInUser());
		// chatMessage.setFromVersion(IdentityController.getOurLatestVersion());
		chatMessage.setTo(to);
		// chatMessage.setToVersion(IdentityController.getTheirLatestVersion(to));
		// chatMessage.setCipherData(cipherData);
		chatMessage.setPlainData(plainData);
		chatMessage.setIv(iv);

		// store the mime type outside teh encrypted envelope, this way we can offload resources
		// by mime type
		chatMessage.setMimeType(mimeType);
		return chatMessage;
	}

	public static SurespotMessage buildMessage(String to, String mimeType, String plainData, String iv, String cipherData) {
		SurespotMessage chatMessage = new SurespotMessage();
		chatMessage.setFrom(IdentityController.getLoggedInUser());
		chatMessage.setFromVersion(IdentityController.getOurLatestVersion());
		chatMessage.setTo(to);
		chatMessage.setToVersion(IdentityController.getTheirLatestVersion(to));
		chatMessage.setData(cipherData);
		chatMessage.setPlainData(plainData);
		chatMessage.setIv(iv);

		// store the mime type outside teh encrypted envelope, this way we can offload resources
		// by mime type
		chatMessage.setMimeType(mimeType);
		return chatMessage;
	}

	@SuppressWarnings("resource")
	public static void uploadPictureMessageAsync(final Activity activity, final ChatController chatController,
			final NetworkController networkController, final Uri imageUri, final String to, final boolean scale,
			final IAsyncCallback<Boolean> callback) {

		Runnable runnable = new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub

				SurespotLog.v(TAG, "uploadPictureMessageAsync");
				try {
					Bitmap bitmap = null;
					InputStream dataStream;
					if (scale) {
						SurespotLog.v(TAG, "scalingImage");
						bitmap = decodeSampledBitmapFromUri(activity, imageUri, -1);

						final Bitmap finalBitmap = bitmap;
						final PipedOutputStream pos = new PipedOutputStream();
						dataStream = new PipedInputStream(pos);
						Runnable runnable = new Runnable() {
							@Override
							public void run() {
								SurespotLog.v(TAG, "compressingImage");
								finalBitmap.compress(Bitmap.CompressFormat.JPEG, 75, pos);
								try {
									pos.close();
									SurespotLog.v(TAG, "imageCompressed");
								}
								catch (IOException e) {
									SurespotLog.w(TAG, e, "error compressing image");
								}
							}
						};
						SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);
					}
					else {
						dataStream = activity.getContentResolver().openInputStream(imageUri);
					}

					// save encrypted image locally until we receive server confirmation

					String localImageDir = activity.getCacheDir() + File.separator + "uploadedImages";
					new File(localImageDir).mkdirs();

					String localImageFilename = localImageDir + File.separator
							+ URLEncoder.encode(new Random().nextInt() + ".tmp", "UTF-8");
					final File localImageFile = new File(localImageFilename);

					localImageFile.createNewFile();
					String localImageUri = Uri.fromFile(localImageFile).toString();
					SurespotLog.v(TAG, "saving copy of encrypted image to: %s", localImageFilename);

					PipedOutputStream encryptionOutputStream = new PipedOutputStream();
					final PipedInputStream encryptionInputStream = new PipedInputStream(encryptionOutputStream);


					final String ourVersion = IdentityController.getOurLatestVersion();
					final String theirVersion = IdentityController.getTheirLatestVersion(to);

					final String iv = EncryptionController.runEncryptTask(ourVersion, to, theirVersion,
							new BufferedInputStream(dataStream), encryptionOutputStream);

					// add a message immediately
					if (scale) {
						// use iv as key

						if (bitmap != null) {
							// scale to display size
							ByteArrayOutputStream bos = new ByteArrayOutputStream();
							bitmap.compress(Bitmap.CompressFormat.JPEG, 75, bos);
							bitmap = getSampledImage(bos.toByteArray());
							bos.close();

						}
					}
					else {
						// scale to display size
						bitmap = getSampledImage(Utils.inputStreamToBytes(activity.getContentResolver().openInputStream(imageUri)));
					}
					if (bitmap != null) {
						SurespotLog.v(TAG, "adding bitmap to caches: %s", localImageUri);

						ImageDownloader.addBitmapToCache(localImageUri, bitmap);
						final SurespotMessage message = buildMessage(to, SurespotConstants.MimeTypes.IMAGE, null, iv, localImageUri);
						message.setId(null);
						message.setHeight(bitmap.getHeight());
						activity.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								chatController.addMessage(message);
							}
						});
					}

					Runnable saveFileRunnable = new Runnable() {
						@Override
						public void run() {

							// save encrypted image to disk
							FileOutputStream fileSaveStream;
							try {
								fileSaveStream = new FileOutputStream(localImageFile);

								int bufferSize = 1024;
								byte[] buffer = new byte[bufferSize];

								int len = 0;
								while ((len = encryptionInputStream.read(buffer)) != -1) {
									fileSaveStream.write(buffer, 0, len);
								}
								fileSaveStream.close();
								encryptionInputStream.close();

							}

							catch (IOException e) {
								SurespotLog.w(TAG, e, "uploadPictureMessageAsync");
								callback.handleResponse(false);
								return;
							}

							// upload encrypted image to server
							FileInputStream uploadStream;
							try {
								uploadStream = new FileInputStream(localImageFile);
							}
							catch (FileNotFoundException e) {
								SurespotLog.w(TAG, e, "uploadPictureMessageAsync");
								callback.handleResponse(false);
								return;
							}

							networkController.postFileStream(activity, ourVersion, to, theirVersion, iv, uploadStream,
									SurespotConstants.MimeTypes.IMAGE, new IAsyncCallback<Boolean>() {

										@Override
										public void handleResponse(Boolean result) {
											// if it failed update the message
											if (!result) {
												chatController.getChatAdapter(activity, to).getMessageByIv(iv).setErrorStatus(500);
											}
											else {
												// localImageFile.delete();
											}

											callback.handleResponse(result);
										}
									});

						}
					};

					SurespotApplication.THREAD_POOL_EXECUTOR.execute(saveFileRunnable);

				}

				catch (IOException e) {
					SurespotLog.w(TAG, e, "uploadPictureMessageAsync");
				}
			}
		};

		SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);

	}

	public static Bitmap decodeSampledBitmapFromUri(Context context, Uri imageUri, int rotate) {
		//

		try {// First decode with inJustDecodeBounds=true to check dimensions
			BitmapFactory.Options options = new BitmapFactory.Options();
			InputStream is;
			options.inJustDecodeBounds = true;

			is = context.getContentResolver().openInputStream(imageUri);
			BitmapFactory.decodeStream(is, null, options);
			is.close();

			// rotate as necessary
			int rotatedWidth, rotatedHeight;

			int orientation = 0;

			// if we have a rotation use it otherwise look at the EXIF
			if (rotate > -1) {
				orientation = rotate;
			}
			else {
				orientation = (int) rotationForImage(context, imageUri);
			}
			if (orientation == 90 || orientation == 270) {
				rotatedWidth = options.outHeight;
				rotatedHeight = options.outWidth;
			}
			else {
				rotatedWidth = options.outWidth;
				rotatedHeight = options.outHeight;
			}

			Bitmap srcBitmap;
			is = context.getContentResolver().openInputStream(imageUri);
			if (rotatedWidth > SurespotConstants.MAX_IMAGE_DIMENSION || rotatedHeight > SurespotConstants.MAX_IMAGE_DIMENSION) {
				float widthRatio = ((float) rotatedWidth) / ((float) SurespotConstants.MAX_IMAGE_DIMENSION);
				float heightRatio = ((float) rotatedHeight) / ((float) SurespotConstants.MAX_IMAGE_DIMENSION);
				float maxRatio = Math.max(widthRatio, heightRatio);

				// Create the bitmap from file
				options = new BitmapFactory.Options();
				options.inSampleSize = (int) Math.round(maxRatio);
				SurespotLog.v(TAG, "Rotated width: " + rotatedWidth + ", height: " + rotatedHeight + ", insamplesize: "
						+ options.inSampleSize);
				srcBitmap = BitmapFactory.decodeStream(is, null, options);
			}
			else {
				srcBitmap = BitmapFactory.decodeStream(is);
			}

			SurespotLog.v(TAG, "loaded width: " + srcBitmap.getWidth() + ", height: " + srcBitmap.getHeight());
			is.close();

			if (orientation > 0) {
				Matrix matrix = new Matrix();
				matrix.postRotate(orientation);

				srcBitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.getWidth(), srcBitmap.getHeight(), matrix, true);
				SurespotLog.v(TAG, "post rotated width: " + srcBitmap.getWidth() + ", height: " + srcBitmap.getHeight());
			}

			return srcBitmap;
		}

		catch (Exception e) {
			SurespotLog.w(TAG, "decodeSampledBitmapFromUri", e);
		}
		return null;

		// Calculate inSampleSize
		// options.inSampleSize = 8;// calculateInSampleSize(options, reqWidth, reqHeight);
		//
		// // Decode bitmap with inSampleSize set
		// options.inJustDecodeBounds = false;
		// try {
		// return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(imageUri), null, options);
		// }
		// catch (FileNotFoundException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		// return null;

	}

	public static Bitmap getSampledImage(byte[] data) {
		BitmapFactory.Options options = new Options();
		decodeBounds(options, data);

		int reqHeight = SurespotConstants.IMAGE_DISPLAY_HEIGHT;
		if (options.outHeight > reqHeight) {
			options.inSampleSize = calculateInSampleSize(options, 0, reqHeight);
			// SurespotLog.v(TAG, "getSampledImage, inSampleSize: " + options.inSampleSize);
		}

		options.inJustDecodeBounds = false;
		return BitmapFactory.decodeByteArray(data, 0, data.length, options);
	}

	private static void decodeBounds(Options options, byte[] data) {
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(data, 0, data.length, options);
	}

	private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {
			// if (width > height) {
			inSampleSize = Math.round((float) height / (float) reqHeight);
			// }
			// else {
			// inSampleSize = Math.round((float) width / (float) reqWidth);
			// }

			// This offers some additional logic in case the image has a strange
			// aspect ratio. For example, a panorama may have a much larger
			// width than height. In these cases the total pixels might still
			// end up being too large to fit comfortably in memory, so we should
			// be more aggressive with sample down the image (=larger
			// inSampleSize).

			if (reqWidth > 0 && reqHeight > 0) {
				final float totalPixels = width * height;

				// Anything more than 2x the requested pixels we'll sample down
				// further.
				final float totalReqPixelsCap = reqWidth * reqHeight * 2;

				while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
					inSampleSize++;

				}
			}
		}
		return inSampleSize;
	}

	public static float rotationForImage(Context context, Uri uri) {
		if (uri.getScheme().equals("content")) {
			String[] projection = { Images.ImageColumns.ORIENTATION };
			Cursor c = context.getContentResolver().query(uri, projection, null, null, null);
			if (c.moveToFirst()) {
				return c.getInt(0);
			}
		}
		else if (uri.getScheme().equals("file")) {
			try {
				ExifInterface exif = new ExifInterface(uri.getPath());
				int rotation = (int) exifOrientationToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
						ExifInterface.ORIENTATION_NORMAL));
				return rotation;
			}
			catch (IOException e) {
				SurespotLog.e(TAG, e, "Error checking exif");
			}
		}
		return 0f;
	}

	private static float exifOrientationToDegrees(int exifOrientation) {
		if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
			return 90;
		}
		else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
			return 180;
		}
		else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
			return 270;
		}
		return 0;
	}

	public static JSONArray chatMessagesToJson(Collection<SurespotMessage> messages) {

		JSONArray jsonMessages = new JSONArray();
		Iterator<SurespotMessage> iterator = messages.iterator();
		while (iterator.hasNext()) {
			jsonMessages.put(iterator.next().toJSONObject());

		}
		return jsonMessages;

	}

	public static ArrayList<SurespotMessage> jsonStringToChatMessages(String jsonMessageString) {

		ArrayList<SurespotMessage> messages = new ArrayList<SurespotMessage>();
		try {
			JSONArray jsonUM = new JSONArray(jsonMessageString);
			for (int i = 0; i < jsonUM.length(); i++) {
				messages.add(SurespotMessage.toSurespotMessage(jsonUM.getJSONObject(i)));
			}
		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "jsonStringToChatMessages", e);
		}
		return messages;

	}

	public static ArrayList<SurespotMessage> jsonStringsToMessages(String jsonMessageString) {

		ArrayList<SurespotMessage> messages = new ArrayList<SurespotMessage>();
		try {
			JSONArray jsonUM = new JSONArray(jsonMessageString);
			for (int i = 0; i < jsonUM.length(); i++) {
				messages.add(SurespotMessage.toSurespotMessage(new JSONObject(jsonUM.getString(i))));
			}
		}
		catch (JSONException e) {
			SurespotLog.w(TAG, "jsonStringsToMessages", e);
		}
		return messages;

	}

	public static byte[] base64EncodeNowrap(byte[] buf) {
		return Base64.encode(buf, Base64.NO_WRAP);
	}

	public static byte[] base64DecodeNowrap(String buf) {
		return Base64.decode(buf, Base64.NO_WRAP);
	}

	public static byte[] base64Encode(byte[] buf) {
		return Base64.encode(buf, Base64.DEFAULT);
	}

	public static byte[] base64Decode(String buf) {
		return Base64.decode(buf, Base64.DEFAULT);
	}

}
