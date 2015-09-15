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
import java.net.URI;
import java.net.URISyntaxException;
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
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;

import ch.boye.httpclientandroidlib.androidextra.Base64;

import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.MessageImageDownloader;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.IAsyncCallbackTriplet;
import com.twofours.surespot.network.NetworkController;

public class ChatUtils {
    private static final String TAG = "ChatUtils";
    private static Random mImageUploadFileRandom = new Random();

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

    public static boolean isMyMessage(SurespotMessage message) {
        return message.getFrom().equals(IdentityController.getLoggedInUser());
    }

    public static SurespotMessage buildPlainMessage(String to, String mimeType, CharSequence plainData, String iv) {
        SurespotMessage chatMessage = new SurespotMessage();
        chatMessage.setFrom(IdentityController.getLoggedInUser());
        chatMessage.setTo(to);
        chatMessage.setPlainData(plainData);
        chatMessage.setIv(iv);
        chatMessage.setHashed(true);
        // store the mime type outside teh encrypted envelope, this way we can offload resources
        // by mime type
        chatMessage.setMimeType(mimeType);
        return chatMessage;
    }

    public static SurespotMessage buildPlainBinaryMessage(String to, String mimeType, byte[] plainData, String iv) {
        SurespotMessage chatMessage = new SurespotMessage();
        chatMessage.setFrom(IdentityController.getLoggedInUser());
        chatMessage.setTo(to);
        chatMessage.setPlainBinaryData(plainData);
        chatMessage.setIv(iv);
        chatMessage.setHashed(true);

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
        chatMessage.setHashed(true);

        // store the mime type outside teh encrypted envelope, this way we can offload resources
        // by mime type
        chatMessage.setMimeType(mimeType);
        return chatMessage;
    }

    public static void uploadPictureMessageAsync(final Activity activity, final ChatController chatController, final NetworkController networkController,
                                                 final Uri imageUri, final String to, final boolean scale, final IAsyncCallback<Boolean> callback) {

        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                SurespotLog.v(TAG, "uploadPictureMessageAsync");
                try {
                    Bitmap bitmap = null;
                    InputStream dataStream = null;
                    if (scale) {
                        SurespotLog.v(TAG, "scalingImage");
                        bitmap = decodeSampledBitmapFromUri(activity, imageUri, -1, SurespotConstants.MESSAGE_IMAGE_DIMENSION);

                        if (bitmap != null) {
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
                                    } catch (IOException e) {
                                        SurespotLog.w(TAG, e, "error compressing image");
                                    }
                                }
                            };
                            SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);
                        }
                    } else {
                        dataStream = activity.getContentResolver().openInputStream(imageUri);
                    }

                    if (dataStream != null) {

                        PipedOutputStream encryptionOutputStream = new PipedOutputStream();
                        final PipedInputStream encryptionInputStream = new PipedInputStream(encryptionOutputStream);

                        final String ourVersion = IdentityController.getOurLatestVersion();
                        final String theirVersion = IdentityController.getTheirLatestVersion(to);

                        final String iv = EncryptionController.runEncryptTask(ourVersion, to, theirVersion, new BufferedInputStream(dataStream),
                                encryptionOutputStream);

                        if (scale) {
                            // use iv as key

                            if (bitmap != null) {
                                // scale to display size
                                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, bos);
                                bitmap = getSampledImage(bos.toByteArray());
                                bos.close();

                            }
                        } else {
                            // scale to display size
                            bitmap = getSampledImage(Utils.inputStreamToBytes(activity.getContentResolver().openInputStream(imageUri)));
                        }

                        // save encrypted image locally until we receive server confirmation
                        String localImageDir = FileUtils.getImageUploadDir(activity);
                        new File(localImageDir).mkdirs();

                        String localImageFilename = localImageDir + File.separator
                                + URLEncoder.encode(String.valueOf(mImageUploadFileRandom.nextInt()) + ".tmp", "UTF-8");
                        final File localImageFile = new File(localImageFilename);

                        localImageFile.createNewFile();
                        String localImageUri = Uri.fromFile(localImageFile).toString();
                        SurespotLog.v(TAG, "saving copy of encrypted image to: %s", localImageFilename);
                        SurespotMessage message = null;
                        if (bitmap != null) {
                            SurespotLog.v(TAG, "adding bitmap to cache: %s", localImageUri);

                            MessageImageDownloader.addBitmapToCache(localImageUri, bitmap);
                            message = buildMessage(to, SurespotConstants.MimeTypes.IMAGE, null, iv, localImageUri);
                            message.setId(null);

                            final SurespotMessage finalMessage = message;
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    SurespotLog.v(TAG, "adding local image message %s", finalMessage);
                                    chatController.addMessage(activity, finalMessage);
                                }
                            });
                        }

                        final SurespotMessage finalMessage = message;
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

                                } catch (IOException e) {
                                    SurespotLog.w(TAG, e, "uploadPictureMessageAsync");
                                    if (finalMessage != null) {
                                        finalMessage.setErrorStatus(500);
                                    }
                                    callback.handleResponse(true);
                                    return;
                                }

                                // upload encrypted image to server
                                FileInputStream uploadStream;
                                try {
                                    uploadStream = new FileInputStream(localImageFile);
                                } catch (FileNotFoundException e) {
                                    SurespotLog.w(TAG, e, "uploadPictureMessageAsync");
                                    if (finalMessage != null) {
                                        finalMessage.setErrorStatus(500);
                                    }
                                    callback.handleResponse(true);
                                    return;
                                }

                                networkController.postFileStream(activity, ourVersion, to, theirVersion, iv, uploadStream, SurespotConstants.MimeTypes.IMAGE,
                                        new IAsyncCallback<Integer>() {

                                            @Override
                                            public void handleResponse(Integer statusCode) {
                                                // if it failed update the message
                                                SurespotLog.v(TAG, "postFileStream complete, result: %d", statusCode);
                                                ChatAdapter chatAdapter = null;
                                                switch (statusCode) {
                                                    case 200:
                                                        break;
                                                    case 402:
                                                        if (finalMessage != null) {
                                                            finalMessage.setErrorStatus(402);
                                                        }
                                                        chatAdapter = chatController.getChatAdapter(activity, to);
                                                        if (chatAdapter != null) {
                                                            chatAdapter.notifyDataSetChanged();
                                                        }
                                                        break;
                                                    default:
                                                        if (finalMessage != null) {
                                                            finalMessage.setErrorStatus(500);
                                                        }
                                                        chatAdapter = chatController.getChatAdapter(activity, to);
                                                        if (chatAdapter != null) {
                                                            chatAdapter.notifyDataSetChanged();
                                                        }
                                                }

                                                callback.handleResponse(true);
                                            }
                                        });

                            }
                        };

                        SurespotApplication.THREAD_POOL_EXECUTOR.execute(saveFileRunnable);

                    } else {
                        callback.handleResponse(false);
                    }
                } catch (IOException e) {
                    SurespotLog.w(TAG, e, "uploadPictureMessageAsync");
                }
            }
        };

        SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);

    }

    public static void uploadFriendImageAsync(final Activity activity, final NetworkController networkController, final Uri imageUri, final String friendName,
                                              final IAsyncCallbackTriplet<String, String, String> callback) {

        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                SurespotLog.v(TAG, "uploadFriendImageAsync");
                try {
                    InputStream dataStream = activity.getContentResolver().openInputStream(imageUri);
                    PipedOutputStream encryptionOutputStream = new PipedOutputStream();
                    final PipedInputStream encryptionInputStream = new PipedInputStream(encryptionOutputStream);

                    final String ourVersion = IdentityController.getOurLatestVersion();
                    final String username = IdentityController.getLoggedInUser();
                    final String iv = EncryptionController.runEncryptTask(ourVersion, username, ourVersion, new BufferedInputStream(dataStream),
                            encryptionOutputStream);

                    networkController.postFriendImageStream(activity, friendName, ourVersion, iv, encryptionInputStream, new IAsyncCallback<String>() {

                        @Override
                        public void handleResponse(String uri) {
                            if (uri != null) {
                                callback.handleResponse(uri, ourVersion, iv);
                            } else {
                                callback.handleResponse(null, null, null);
                            }
                        }
                    });
                } catch (IOException e) {
                    callback.handleResponse(null, null, null);
                    SurespotLog.w(TAG, e, "uploadFriendImageAsync");
                }
            }
        };

        SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);

    }

    public static void uploadVoiceMessageAsync(final Activity activity, final ChatController chatController, final NetworkController networkController,
                                               final Uri audioUri, final String to, final IAsyncCallback<Boolean> callback) {

        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                SurespotLog.v(TAG, "uploadVoiceMessageAsync");
                try {
                    InputStream dataStream = null;

                    dataStream = activity.getContentResolver().openInputStream(audioUri);

                    if (dataStream != null) {

                        PipedOutputStream encryptionOutputStream = new PipedOutputStream();
                        final PipedInputStream encryptionInputStream = new PipedInputStream(encryptionOutputStream);

                        final String ourVersion = IdentityController.getOurLatestVersion();
                        final String theirVersion = IdentityController.getTheirLatestVersion(to);

                        final String iv = EncryptionController.runEncryptTask(ourVersion, to, theirVersion, new BufferedInputStream(dataStream),
                                encryptionOutputStream);

                        // save encrypted audio locally until we receive server confirmation
                        String localImageDir = FileUtils.getImageUploadDir(activity);
                        new File(localImageDir).mkdirs();

                        String localImageFilename = localImageDir + File.separator
                                + URLEncoder.encode(String.valueOf(mImageUploadFileRandom.nextInt()) + ".tmp", "UTF-8");
                        final File localImageFile = new File(localImageFilename);

                        localImageFile.createNewFile();
                        final String localImageUri = Uri.fromFile(localImageFile).toString();
                        SurespotLog.v(TAG, "saving copy of encrypted image to: %s", localImageFilename);

                        Runnable saveFileRunnable = new Runnable() {
                            @Override
                            public void run() {

                                SurespotMessage message = null;
                                // save encrypted voice message to disk
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

                                    message = buildMessage(to, SurespotConstants.MimeTypes.M4A, null, iv, localImageUri);
                                    message.setId(null);

                                    final SurespotMessage finalMessage = message;
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            SurespotLog.v(TAG, "adding local voice message %s", finalMessage);
                                            chatController.addMessage(activity, finalMessage);
                                        }
                                    });

                                } catch (IOException e) {
                                    SurespotLog.w(TAG, e, "uploadVoiceMessageAsync");
                                    if (message != null) {
                                        message.setErrorStatus(500);
                                    }
                                    callback.handleResponse(true);
                                    return;
                                }

                                // upload encrypted image to server
                                FileInputStream uploadStream;
                                try {
                                    uploadStream = new FileInputStream(localImageFile);
                                } catch (FileNotFoundException e) {
                                    SurespotLog.w(TAG, e, "uploadVoiceMessageAsync");
                                    if (message != null) {
                                        message.setErrorStatus(500);
                                    }
                                    callback.handleResponse(true);
                                    return;
                                }

                                final SurespotMessage finalMessage = message;
                                networkController.postFileStream(activity, ourVersion, to, theirVersion, iv, uploadStream, SurespotConstants.MimeTypes.M4A,
                                        new IAsyncCallback<Integer>() {

                                            @Override
                                            public void handleResponse(Integer statusCode) {
                                                // if it failed update the message
                                                SurespotLog.v(TAG, "postFileStream complete, result: %d", statusCode);
                                                ChatAdapter chatAdapter = null;
                                                switch (statusCode) {
                                                    case 200:
                                                        break;
                                                    case 402:
                                                        if (finalMessage != null) {
                                                            finalMessage.setErrorStatus(402);
                                                        }
                                                        chatAdapter = chatController.getChatAdapter(activity, to);
                                                        if (chatAdapter != null) {
                                                            chatAdapter.notifyDataSetChanged();
                                                        }
                                                        break;
                                                    default:
                                                        if (finalMessage != null) {
                                                            finalMessage.setErrorStatus(500);
                                                        }
                                                        chatAdapter = chatController.getChatAdapter(activity, to);
                                                        if (chatAdapter != null) {
                                                            chatAdapter.notifyDataSetChanged();
                                                        }
                                                }

                                                callback.handleResponse(true);
                                            }
                                        });

                            }
                        };

                        SurespotApplication.THREAD_POOL_EXECUTOR.execute(saveFileRunnable);

                    } else {
                        callback.handleResponse(false);
                    }
                } catch (IOException e) {
                    SurespotLog.w(TAG, e, "uploadPictureMessageAsync");
                    callback.handleResponse(false);
                }
            }
        };

        SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);

    }

    public static void resendFileMessage(Context context, NetworkController networkController, final SurespotMessage message,
                                         final IAsyncCallback<Integer> callback) {

        // upload encrypted file to server
        FileInputStream uploadStream = null;
        try {
            if (message.getData().startsWith("file")) {
                uploadStream = new FileInputStream(new File(new URI(message.getData())));
            } else {
                callback.handleResponse(500);
            }
        } catch (IllegalArgumentException e) {
            SurespotLog.w(TAG, e, "uploadPictureMessageAsync");
            callback.handleResponse(500);
            return;
        } catch (FileNotFoundException e) {
            SurespotLog.w(TAG, e, "uploadPictureMessageAsync");
            callback.handleResponse(500);
            return;
        } catch (URISyntaxException e) {
            SurespotLog.w(TAG, e, "uploadPictureMessageAsync");
            callback.handleResponse(500);
            return;
        }

        networkController.postFileStream(context, message.getOurVersion(), message.getTo(), message.getTheirVersion(), message.getIv(), uploadStream,
                message.getMimeType(), new IAsyncCallback<Integer>() {

                    @Override
                    public void handleResponse(Integer statusCode) {
                        SurespotLog.v(TAG, "postFileStream complete, result: %d", statusCode);
                        callback.handleResponse(statusCode);
                    }
                });
    }

    public static Bitmap decodeSampledBitmapFromUri(Context context, Uri imageUri, int rotate, int maxDimension) {
        //

        try {// First decode with inJustDecodeBounds=true to check dimensions
            BitmapFactory.Options options = new BitmapFactory.Options();
            InputStream is;
            options.inJustDecodeBounds = true;

            is = context.getContentResolver().openInputStream(imageUri);
            Bitmap bm = BitmapFactory.decodeStream(is, null, options);
            is.close();

            // rotate as necessary
            int rotatedWidth, rotatedHeight;

            int orientation = 0;

            // if we have a rotation use it otherwise look at the EXIF
            if (rotate > -1) {
                orientation = rotate;
            } else {
                orientation = (int) rotationForImage(context, imageUri);
            }
            if (orientation == 90 || orientation == 270) {
                rotatedWidth = options.outHeight;
                rotatedHeight = options.outWidth;
            } else {
                rotatedWidth = options.outWidth;
                rotatedHeight = options.outHeight;
            }

            Bitmap srcBitmap;
            is = context.getContentResolver().openInputStream(imageUri);
            if (rotatedWidth > maxDimension || rotatedHeight > maxDimension) {
                float widthRatio = ((float) rotatedWidth) / ((float) maxDimension);
                float heightRatio = ((float) rotatedHeight) / ((float) maxDimension);
                float maxRatio = Math.max(widthRatio, heightRatio);

                // Create the bitmap from file
                options = new BitmapFactory.Options();
                options.inSampleSize = (int) Math.round(maxRatio);
                SurespotLog.v(TAG, "Rotated width: " + rotatedWidth + ", height: " + rotatedHeight + ", insamplesize: " + options.inSampleSize);
                srcBitmap = BitmapFactory.decodeStream(is, null, options);
            } else {
                srcBitmap = BitmapFactory.decodeStream(is);
            }

            is.close();
            if (srcBitmap != null) {

                SurespotLog.v(TAG, "loaded width: " + srcBitmap.getWidth() + ", height: " + srcBitmap.getHeight());

                if (orientation > 0) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(orientation);

                    srcBitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.getWidth(), srcBitmap.getHeight(), matrix, true);
                    SurespotLog.v(TAG, "post rotated width: " + srcBitmap.getWidth() + ", height: " + srcBitmap.getHeight());
                }
            }

            return srcBitmap;
        } catch (Exception e) {
            SurespotLog.w(TAG, e, "decodeSampledBitmapFromUri");
        }
        return null;

    }

    public static Bitmap getSampledImage(byte[] data) {
        BitmapFactory.Options options = new Options();
        decodeBounds(options, data);

        int reqHeight = SurespotConfiguration.getImageDisplayHeight();
        if (options.outHeight > reqHeight) {
            options.inSampleSize = calculateInSampleSize(options, 0, reqHeight);
            SurespotLog.v(TAG, "getSampledImage, inSampleSize: " + options.inSampleSize);
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    String path = getRealPathFromURI_API19(context, uri);

                    float rotation2 = getRotationFromPath(path);

                    if (rotation2 == 0) {
                        // this one appears to work all the time for local images!
                        rotation2 = getRotationFromPath(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + path);
                    }

                    if (rotation2 == 0) {
                        rotation2 = getRotationFromPath("file://" + path);
                    }

                    if (rotation2 != 0) {
                        return rotation2;
                    }
                } catch (Exception e) {
                    //fallback to old code
                }
            }


            String[] projection = {Images.Media.ORIENTATION}; //{Images.ImageColumns.ORIENTATION};
            Cursor c = context.getContentResolver().query(uri, projection, null, null, null);
            if (c.moveToFirst()) {
                SurespotLog.d(TAG, "Image orientation: %d", c.getInt(0));
                return c.getInt(0);
            }

        } else if (uri.getScheme().equals("file")) {
            return getRotationFromPath(uri.getPath());
        }
        return 0f;
    }

    private static float getRotationFromPath(String path) {
        try {
            ExifInterface exif = new ExifInterface(path);
            int rotation = (int) exifOrientationToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL));
            return rotation;
        } catch (IOException e) {
            SurespotLog.e(TAG, e, "Error checking exif");
        }
        return 0;
    }

    @android.annotation.SuppressLint("NewApi")
    public static String getRealPathFromURI_API11to18(Context context, Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        String result = null;

        android.content.CursorLoader cursorLoader = new android.content.CursorLoader(
                context,
                contentUri, proj, null, null, null);
        Cursor cursor = cursorLoader.loadInBackground();

        if (cursor != null) {
            int column_index =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            result = cursor.getString(column_index);
        }
        return result;
    }

    public static String getRealPathFromURI_BelowAPI11(Context context, Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
        int column_index
                = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    @android.annotation.SuppressLint("NewApi")
    public static String getRealPathFromURI_API19(Context context, Uri uri) {
        String filePath = "";
        String wholeID = android.provider.DocumentsContract.getDocumentId(uri);

        // Split at colon, use second item in the array
        String id = wholeID.split(":")[1];

        String[] column = {MediaStore.Images.Media.DATA};

        // where id is equal to
        String sel = MediaStore.Images.Media._ID + "=?";

        Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                column, sel, new String[]{id}, null);

        int columnIndex = cursor.getColumnIndex(column[0]);

        if (cursor.moveToFirst()) {
            filePath = cursor.getString(columnIndex);
        } else {
            if (wholeID.startsWith("primary:")) {
                filePath = wholeID.replace("primary:", "");
            } else {
                return wholeID;
            }
        }
        cursor.close();
        return filePath;
    }

    private static float exifOrientationToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }

    public static JSONArray chatMessagesToJson(Collection<SurespotMessage> messages) {
        // avoid concurrent modification issues
        synchronized (messages) {
            SurespotMessage[] messageArray = messages.toArray(new SurespotMessage[messages.size()]);
            JSONArray jsonMessages = new JSONArray();

            for (SurespotMessage message : messageArray) {
                jsonMessages.put(message.toJSONObject());
            }

            return jsonMessages;
        }
    }

    public static ArrayList<SurespotMessage> jsonStringToChatMessages(String jsonMessageString) {

        ArrayList<SurespotMessage> messages = new ArrayList<SurespotMessage>();
        try {
            JSONArray jsonUM = new JSONArray(jsonMessageString);
            for (int i = 0; i < jsonUM.length(); i++) {
                messages.add(SurespotMessage.toSurespotMessage(jsonUM.getJSONObject(i)));
            }
        } catch (JSONException e) {
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
        } catch (JSONException e) {
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

    /**
     * Converts the string to the unicode format '\u0020'.
     * <p/>
     * This format is the Java source code format.
     * <p/>
     * <pre>
     *   CharUtils.unicodeEscaped(' ') = "\u0020"
     *   CharUtils.unicodeEscaped('A') = "\u0041"
     * </pre>
     *
     * @param ch the character to convert
     * @return the escaped unicode string
     */
    public static String unicodeEscaped(int ch) {
        if (ch < 0x10) {
            return "\\u000" + Integer.toHexString(ch);
        } else if (ch < 0x100) {
            return "\\u00" + Integer.toHexString(ch);
        } else if (ch < 0x1000) {
            return "\\u0" + Integer.toHexString(ch);
        }
        return "\\u" + Integer.toHexString(ch);
    }

    /**
     * Converts the string to the unicode format '\u0020'.
     * <p/>
     * This format is the Java source code format.
     * <p/>
     * If <code>null</code> is passed in, <code>null</code> will be returned.
     * <p/>
     * <pre>
     *   CharUtils.unicodeEscaped(null) = null
     *   CharUtils.unicodeEscaped(' ')  = "\u0020"
     *   CharUtils.unicodeEscaped('A')  = "\u0041"
     * </pre>
     *
     * @param ch the character to convert, may be null
     * @return the escaped unicode string, null if null input
     */
    public static String unicodeEscaped(Character ch) {
        if (ch == null) {
            return null;
        }
        return unicodeEscaped(ch.charValue());
    }

    public static class CodePoint {
        public int codePoint;
        public int start;
        public int end;
    }

    // iterate through codepoints http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5003547
    public static Iterable<CodePoint> codePoints(final String s) {
        return new Iterable<CodePoint>() {
            public Iterator<CodePoint> iterator() {
                return new Iterator<CodePoint>() {
                    int nextIndex = 0;

                    public boolean hasNext() {
                        return nextIndex < s.length();
                    }

                    public CodePoint next() {
                        int result = s.codePointAt(nextIndex);

                        CodePoint cp = new CodePoint();
                        cp.codePoint = result;
                        cp.start = nextIndex;
                        nextIndex += Character.charCount(result);
                        cp.end = nextIndex;
                        return cp;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

}


