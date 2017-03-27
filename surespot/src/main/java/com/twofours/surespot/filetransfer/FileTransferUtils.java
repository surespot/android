package com.twofours.surespot.filetransfer;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.backup.DriveHelper;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.utils.PBFileUtils;
import com.twofours.surespot.utils.Utils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


/**
 * Created by adam on 3/25/17.
 */

public class FileTransferUtils {
    private static final String DRIVE_DATA_FOLDER = "surespot_data";
    private static final String TAG = "FileTransferUtils";

    private static HashMap<String, String> mDataDirIdMap = new HashMap<>(SurespotConstants.MAX_IDENTITIES);

    public static void uploadFileAsync(final Activity activity, final ChatController cc, final String ourUsername, final String theirUsername, final Uri data) {
        java.io.File filename = PBFileUtils.getFile(activity, data);
        String mimeType = getMimeType(activity, data);
        uploadFileAsync(activity, cc, filename.getName(), data.toString(), mimeType, ourUsername, theirUsername);
    }

    private static void uploadFileAsync(final Activity activity, final ChatController chatController,
                                       final String filename, final String uri, final String mimeType, final String ourUsername, final String theirUsername) {

        SurespotLog.d(TAG, "uploadFileAsync, filename: %s, uri: %s, mimeType: %s", filename, uri, mimeType);
        Runnable runnable = new Runnable() {

            @Override
            public void run() {


                String iv = EncryptionController.getStringIv();

                //will need local url, filename, remote url if
                SurespotMessage.FileMessageData fmd = new SurespotMessage.FileMessageData();
                fmd.setFilename(filename);
                fmd.setLocalUri(uri);
                fmd.setMimeType(mimeType);


                SurespotMessage message = ChatUtils.buildPlainFileMessage(ourUsername, theirUsername, SurespotConstants.MimeTypes.FILE, fmd, iv);
                final SurespotMessage finalMessage = message;

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        SurespotLog.d(TAG, "adding local image message %s", finalMessage);
                        chatController.addMessage(finalMessage);
                    }
                });

                chatController.enqueueMessage(finalMessage);


            }
        };

        SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);
    }



    //must be called from non UI thread
    public static void createFile(final Activity activity, final DriveHelper driveHelper, final String from, InputStream encryptedContentStream, final IAsyncCallback<SurespotMessage.FileMessageData> callback) {
        SurespotLog.d(TAG, "createFile,  thread: %s", Thread.currentThread().getName());

//        if (TextUtils.isEmpty(dataDir)) {
//            SurespotLog.d(TAG, "createFile, couldn't get data dir");
//            callback.handleResponse(null);
//            return;
//        }
        try {

            SurespotLog.d(TAG, "createFile, before open resource, thread: %s", Thread.currentThread().getName());
            final InputStreamContent mediaContent = new InputStreamContent(SurespotConstants.MimeTypes.FILE, new BufferedInputStream(encryptedContentStream));
            final String filename = EncryptionController.getPsuedoRandomKey();
            //create.xecute needs to execute on a different thread than the stream apparently
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        final String dataDir = ensureDriveDataDirectory(activity, driveHelper, from);
                        File file = new File();
                        file.setParents(Arrays.asList(dataDir));
                        file.setName(filename);
                        file.setMimeType(SurespotConstants.MimeTypes.FILE);

                        Permission permission = new Permission();
                        permission
                                .setRole("reader")
                                .setType("anyone");

                        SurespotLog.d(TAG, "createFile, before execute, thread: %s", Thread.currentThread().getName());

                        Drive.Files.Create create = driveHelper.getDriveService().files().create(file, mediaContent);
                        create.getMediaHttpUploader().setProgressListener(new CustomProgressListener());
                        File created = create
                                //.set("permissions", Arrays. permission)
                                .setFields("id,size, webContentLink")
                                .execute();

                        //   SurespotLog.d(TAG, "createFile, filename: %s, created file: %s", filename, created.toPrettyString());
                        //set permissions

                        Permission createdPermission = driveHelper.getDriveService()
                                .permissions()
                                .create(created.getId(), permission)
                                .execute();


                        SurespotLog.d(TAG, "createFile, filename: %s, file: %s, permission: %s", filename, created.toPrettyString(), createdPermission.toPrettyString());
                        SurespotMessage.FileMessageData fmd = new SurespotMessage.FileMessageData();
                        fmd.setCloudUrl(created.getWebContentLink());
                        fmd.setSize(created.getSize());
                        callback.handleResponse(fmd);
                    }
                    catch (UserRecoverableAuthIOException e) {
                        try {
                            activity.startActivityForResult(e.getIntent(), SurespotConstants.IntentRequestCodes.REQUEST_GOOGLE_AUTH);
                        }
                        catch (NullPointerException npe) {

                        }

                    }
                    catch (Exception e) {
                        callback.handleResponse(null);
                        SurespotLog.e(TAG, e, "error executing create");
                    }
                }
            };

            SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);

        }

        catch (SecurityException e) {
            SurespotLog.e(TAG, e, "createFile");
            // when key is revoked on server this happens...should return userrecoverable it seems
            // was trying to figure out how to test this
            // seems like the only way around this is to remove and re-add android account:
            // http://stackoverflow.com/questions/5805657/revoke-account-permission-for-an-app
            final Activity finalActivity = activity;
            activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    Utils.makeLongToast(finalActivity, finalActivity.getString(R.string.re_add_google_account));
                }
            });
            callback.handleResponse(null);

        }
        catch (Exception e) {
            SurespotLog.w(TAG, e, "createFile");
            callback.handleResponse(null);
        }


    }

    private static String ensureDriveDataDirectory(Activity activity, DriveHelper driveHelper, String username) {
        String driveDirId = getCachedDataDirId(activity, username);
        if (!TextUtils.isEmpty(driveDirId)) {
            return driveDirId;
        }

        SurespotLog.d(TAG, "ensureDriveDataDirectory, id not cached");
        try {
            // see if identities directory exists
            Drive drive = driveHelper.getDriveService();

            SurespotLog.d(TAG, "ensureDriveDataDirectory, got drive: %s", drive.toString());
            FileList identityDir = drive.files().list()
                    .setQ("name = '" + DRIVE_DATA_FOLDER + "' and trashed = false and mimeType='application/vnd.google-apps.folder'").execute();
            SurespotLog.d(TAG, "ensureDriveDataDirectory, got filelist: %s", identityDir);
            List<File> items = identityDir.getFiles();


            if (items.size() > 0) {
                File file = items.get(0);

                SurespotLog.d(TAG, "identity folder already exists");
                driveDirId = file.getId();
            }
            if (driveDirId == null) {
                File file = new File();
                file.setName(DRIVE_DATA_FOLDER);
                file.setMimeType(SurespotConstants.MimeTypes.DRIVE_FOLDER);

                com.google.api.services.drive.model.File insertedFile = driveHelper.getDriveService().files().create(file).execute();

                driveDirId = insertedFile.getId();
            }
        }
        catch (UserRecoverableAuthIOException e) {
            try {
                activity.startActivityForResult(e.getIntent(), SurespotConstants.IntentRequestCodes.REQUEST_GOOGLE_AUTH);
            }
            catch (NullPointerException npe) {

            }
        }
        catch (SecurityException e) {
            SurespotLog.e(TAG, e, "createDriveIdentityDirectory");
            // when key is revoked on server this happens...should return userrecoverable it seems
            // was trying to figure out how to test this
            // seems like the only way around this is to remove and re-add android account:
            // http://stackoverflow.com/questions/5805657/revoke-account-permission-for-an-app
            final Activity finalActivity = activity;
            activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    Utils.makeLongToast(finalActivity, finalActivity.getString(R.string.re_add_google_account));
                }
            });

        }
        catch (Exception e) {
            SurespotLog.w(TAG, e, "createDriveIdentityDirectory");
        }

        if (!TextUtils.isEmpty(driveDirId)) {
            mDataDirIdMap.put(username, driveDirId);
        }
        return driveDirId;


    }

    static class CustomProgressListener implements MediaHttpUploaderProgressListener {

        private static final String TAG = "FileTransferUtils::CustomProgressListener";

        public void progressChanged(MediaHttpUploader uploader) throws IOException {
            switch (uploader.getUploadState()) {
                case INITIATION_STARTED:
                    SurespotLog.d(CustomProgressListener.TAG, "Initiation has started!");
                    break;
                case INITIATION_COMPLETE:
                    SurespotLog.d(CustomProgressListener.TAG, "Initiation is complete!");
                    break;
                case MEDIA_IN_PROGRESS:
                    SurespotLog.d(CustomProgressListener.TAG, "progress: " + uploader.getProgress());
                    break;
                case MEDIA_COMPLETE:
                    SurespotLog.d(CustomProgressListener.TAG, "Upload is complete!");
            }
        }
    }

    static private String getCachedDataDirId(Context context, String username) {
        String dataDirId = mDataDirIdMap.get(username);
        if (dataDirId == null) {
            dataDirId = Utils.getUserSharedPrefsString(context, username, "drive_data_dir_id");
            if (dataDirId != null) {
                mDataDirIdMap.put(username, dataDirId);
            }
        }
        return dataDirId;

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static void handleClipDataFileSelection(final Activity activity, final ChatController cc, final String ourUsername, final String theirUsername, final Intent data) {
        SurespotLog.d(TAG, "handleClipDataFileSelection");
        final ClipData clipData = data.getClipData();
        final int itemCount = clipData.getItemCount();

        //handle single file selection
        if (itemCount == 1) {
            Uri uri = clipData.getItemAt(0).getUri();
            uploadFileAsync(activity, cc, ourUsername, theirUsername, uri);
            return;
        }

        new AsyncTask<Void, Void, Integer>() {
            //returns < 0 if finish
            //0 if handled
            //or > 1 for images not handled
            @Override
            protected Integer doInBackground(Void... params) {
                int errorCount = 0;

                for (int n = 0; n < itemCount; n++) {
                    Uri uri = clipData.getItemAt(n).getUri();
                    FileTransferUtils.uploadFileAsync(activity, cc, ourUsername, theirUsername, uri);
                }
                return errorCount;
            }

            @Override
            protected void onPostExecute(Integer errorCount) {
                //    if (errorCount > 0) {
                //     Utils.makeLongToast(ImageSelectActivity.this, String.format(getString(R.string.did_not_send_x_images), errorCount, itemCount));
                //   }
                // finish();
            }
        }.execute();
    }


//
//    public static String getFilenameFromContentResolver(Activity activity, Uri uri) {
//
//        String uriString = uri.toString();
//        java.io.File myFile = new java.io.File(uriString);
//        //  String path = myFile.getAbsolutePath();
//        String displayName = null;
//
//        if (uriString.startsWith("content://")) {
//            Cursor cursor = null;
//            try {
//                cursor = activity.getContentResolver().query(uri, null, null, null, null);
//                if (cursor != null && cursor.moveToFirst()) {
//                    displayName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
//                }
//            } finally {
//                cursor.close();
//            }
//        } else if (uriString.startsWith("file://")) {
//            displayName = myFile.getName();
//        }
//        return displayName;
//    }

    public static String getMimeType(Context context, Uri uri) {
        String mimeType = null;
        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver cr = context.getContentResolver();
            mimeType = cr.getType(uri);
        } else {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri
                    .toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    fileExtension.toLowerCase());
        }
        return mimeType;
    }

}
