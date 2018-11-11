package com.twofours.surespot;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.multidex.MultiDex;
import android.support.v4.content.ContextCompat;

import com.twofours.surespot.billing.BillingController;
import com.twofours.surespot.images.FileCacheController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.utils.FileUtils;
import com.twofours.surespot.utils.Utils;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import java.io.IOException;
import java.security.Security;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ReportsCrashes(mode = ReportingInteractionMode.DIALOG,  // will not be used
        formUri = "https://www.surespot.me:3000/logs/surespot", resToastText = R.string.crash_toast_text, resDialogText = R.string.crash_dialog_text, resDialogOkToast = R.string.crash_dialog_ok_toast, resDialogCommentPrompt = R.string.crash_dialog_comment_prompt)
public class SurespotApplication extends Application {
    private static final String TAG = "SurespotApplication";
    private static CredentialCachingService mCredentialCachingService;
    private static StateController mStateController = null;
    private static String mVersion;
    private static BillingController mBillingController;
    private static String mUserAgent;
    private static int mTextColor;
    private static boolean mThemeChanged;

    public static final int CORE_POOL_SIZE = 24;
    public static final int MAXIMUM_POOL_SIZE = Integer.MAX_VALUE;
    public static final int KEEP_ALIVE = 1;
    private static FileCacheController mFileCacheController;


    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    // create our own thread factory to handle message decryption where we have potentially hundreds of messages to decrypt
    // we need a tall queue and a slim pipe
    public static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "surespot #" + mCount.getAndIncrement());
        }
    };

    public static final BlockingQueue<Runnable> sPoolWorkQueue = new LinkedBlockingQueue<Runnable>();

    /**
     * An {@link Executor} that can be used to execute tasks in parallel.
     */
    public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS, sPoolWorkQueue,
            sThreadFactory);

    public void onCreate() {
        super.onCreate();

        ACRA.init(this);

        PackageManager manager = this.getPackageManager();
        PackageInfo info = null;

        try {
            info = manager.getPackageInfo(this.getPackageName(), 0);
            mVersion = Integer.toString(info.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            mVersion = "unknown";
        }

        //workaround Stack:java.lang.NoClassDefFoundError: android/os/AsyncTask: https://code.google.com/p/android/issues/detail?id=81083
        try {
            Class.forName("android.os.AsyncTask");
        }
        catch(Throwable ignore) {
            // ignored
        }


        mUserAgent = "surespot/" + SurespotApplication.getVersion() + " (Android)";

        Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());

        SurespotConfiguration.LoadConfigProperties(getApplicationContext());
        mStateController = new StateController(this);
        try {
            mFileCacheController = new FileCacheController(this);
        } catch (IOException e) {
            SurespotLog.w(TAG, e, "could not create file cache controller");
        }

        boolean oneTimeGotNoCase = Utils.getSharedPrefsBoolean(this, "73onetime");
        if (!oneTimeGotNoCase) {


            //set confirm logout default to true
            Utils.putSharedPrefsBoolean(SurespotApplication.this,"pref_confirm_logout", true);
            //wipe the cache
            StateController.clearCache(this, new IAsyncCallback<Void>() {
                @Override
                public void handleResponse(Void result) {
                    SurespotLog.d(TAG, "cache cleared");
                    Utils.putSharedPrefsBoolean(SurespotApplication.this, "73onetime", true);
                }
            });


        }

        mBillingController = new BillingController(this);
        FileUtils.wipeImageCaptureDir(this);

        setThemeChanged(this);
        setThemeChanged(null);
    }

    private boolean versionChanged(Context context) {

        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.

        String registeredVersion = Utils.getSharedPrefsString(context, SurespotConstants.PrefNames.APP_VERSION);
        SurespotLog.v(TAG, "registeredversion: %s, currentVersion: %s", registeredVersion, getVersion());
        if (!getVersion().equals(registeredVersion)) {
            SurespotLog.i(TAG, "App version changed.");
            return true;
        }
        return false;
    }

    public static CredentialCachingService getCachingService(Context context) {
        if (mCredentialCachingService == null) {
            mCredentialCachingService = new CredentialCachingService(context);
        }
        return mCredentialCachingService;
    }

    public static StateController getStateController() {
        return mStateController;
    }

    public static String getVersion() {
        return mVersion;
    }

    public static BillingController getBillingController() {
        return mBillingController;
    }

    public static String getUserAgent() {
        return mUserAgent;
    }

    public static FileCacheController getFileCacheController() {
        return mFileCacheController;
    }


    public static void setThemeChanged(Context context) {
        if (context != null) {
            mThemeChanged = true;
            boolean black = Utils.getSharedPrefsBoolean(context, SurespotConstants.PrefNames.BLACK);
            mTextColor = ContextCompat.getColor(context, black ? R.color.surespotGrey : android.R.color.black);
        }
        else {
            mThemeChanged = false;
        }
    }

    public static int getTextColor() {
        return mTextColor;
    }
    public static boolean getThemeChanged() {
        return mThemeChanged;
    }
}
