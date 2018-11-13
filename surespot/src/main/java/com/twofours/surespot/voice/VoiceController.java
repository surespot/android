package com.twofours.surespot.voice;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatManager;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.utils.UIUtils;
import com.twofours.surespot.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

public class VoiceController {
    private static final String TAG = "VoiceController";

    private static String mFileName = null;
    private static String mFrom = null;
    private static String mTo = null;

    public static final int SEND_THRESHOLD = 3500;
    public static final int MAX_TIME = 30000;
    public static final int INTERVAL = 50;
    private static final int SEEK_MAX = 1000;

    private static MediaRecorderWrapper mRecorder = null;

    static TimerTask mCurrentTimeTask;
    static boolean mRecording = false;
    private static SeekBarThread mSeekBarThread;
    private static SurespotMessage mMessage;

    static Timer mTimer;
    private static File mAudioFile;
    static MediaPlayer mPlayer;
    static SeekBar mSeekBar;
    private static boolean mPlaying = false;
    private static VolumeEnvelopeView mEnvelopeView;
    private static View mVoiceHeaderView;
    private static TextView mVoiceRecTimeLeftView;
    private static float mTimeLeft;
    private static String mSendingFile;
    private static Activity mActivity;
    private static IAsyncCallback<SurespotMessage> mPlayCompletedCallback;

    enum State {
        INITIALIZING, READY, STARTED, RECORDING
    }


    private static State mState;
    private static int mDuration;

    static {
        mState = State.STARTED;
    }

    static int getMaxAmplitude() {
        if (mRecorder == null || mState != State.RECORDING) {
            return 0;
        }
        return mRecorder.getMaxAmplitude();
    }

    private static void startTimer(final Activity activity) {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
        }

        final int rate = INTERVAL;
        mTimeLeft = MAX_TIME;
        mTimer = new Timer();
        mCurrentTimeTask = new TimerTask() {
            public void run() {
                activity.runOnUiThread(new Runnable() {
                    public void run() {

                        if (mState == State.RECORDING) {

                            mTimeLeft -= rate;

                            final int currentTimeLeft = (int) mTimeLeft;

                            //SurespotLog.v(TAG, "currentTimeLeft: %d", currentTimeLeft);

                            mEnvelopeView.setNewVolume(getMaxAmplitude(), true);

                            // if we're at a second boundary, update time display
                            if (currentTimeLeft % 1000 == 0) {
                                //SurespotLog.v(TAG, "currentTimeLeft mod: %d", currentTimeLeft%1000);
                                mVoiceRecTimeLeftView.post(new Runnable() {

                                    @Override
                                    public void run() {
                                        mVoiceRecTimeLeftView.setText(Integer.toString(currentTimeLeft / 1000));
                                    }
                                });
                            }

                            if (currentTimeLeft < -150) {
                                stopRecording(mActivity, true, false);
                                return;
                            }

                            return;
                        }

                        mEnvelopeView.clearVolume();
                    }
                });
            }
        };
        mTimer.scheduleAtFixedRate(mCurrentTimeTask, 0, rate);

    }

    private synchronized static boolean startRecordingInternal(final Activity activity) {
        if (mState != State.STARTED) {
            return false;
        }

        try {
            // MediaRecorder has major delay issues on gingerbread so we record raw PCM then convert natively to m4a
            if (mFileName != null) {
                SurespotLog.v(TAG, "start recording, deleting file: %s", mFileName);
                new File(mFileName).delete();
            }

            // create a temp file to hold the uncompressed audio data
            mFileName = File.createTempFile("record", ".m4a").getAbsolutePath();
            SurespotLog.v(TAG, "recording to: %s", mFileName);

            if (mRecorder != null) {
                mRecorder.release();
            }
            mRecorder = new MediaRecorderWrapper();

            mEnvelopeView.setVisibility(View.VISIBLE);
            mVoiceHeaderView.setVisibility(View.VISIBLE);
            mVoiceRecTimeLeftView.setText(String.valueOf(MAX_TIME / 1000));
            mEnvelopeView.clearVolume();
            mRecorder.setOutputFile(mFileName);
            mRecorder.prepare();
            mRecorder.start();

            startTimer(activity);
            mState = State.RECORDING;
            return true;
        } catch (Exception e) {
            SurespotLog.e(TAG, e, "startRecordingInternal() failed");
            Utils.makeToast(activity, activity.getString(R.string.could_not_record_audio));
            stopRecording(activity, false, true);
            return false;
        }

    }

    private synchronized static void stopRecordingInternal() {
        // state must be RECORDING
        if (mState != State.RECORDING) {
            return;
        }
        try {

            mTimer.cancel();
            mTimer.purge();
            mTimer = null;
            mCurrentTimeTask = null;
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;

            mState = State.STARTED;
        } catch (Exception e) {
            SurespotLog.e(TAG, e, "stopRecordingInternal() failed");
        }
    }

    // Play is over, cleanup

    private synchronized static void playCompleted() {

        mSeekBarThread.completed();
        mMessage.setPlayMedia(false);

        if (mPlayer != null) {
            mPlayer.setOnCompletionListener(null);
            mPlayer.release();
            mPlayer = null;
        }

        SurespotMessage message = mMessage;

        mMessage = null;
        if (mAudioFile != null) {
            mAudioFile.delete();
        }

        mPlaying = false;
        updatePlayControls();

        if (mPlayCompletedCallback != null) {
            mPlayCompletedCallback.handleResponse(message);
        }
    }

    public static synchronized void startRecording(Activity context, String from, String to) {
        if (TextUtils.isEmpty(from) || TextUtils.isEmpty(to)) {
            return;
        }

        if (!mRecording) {
            stopPlaying();
            // disable rotation
            UIUtils.lockOrientation(context);

            mActivity = context;
            mFrom = from;
            mTo = to;
            mEnvelopeView = (VolumeEnvelopeView) context.findViewById(R.id.volume_envelope);
            mVoiceHeaderView = (View) context.findViewById(R.id.voiceHeader);
            mVoiceRecTimeLeftView = (TextView) context.findViewById(R.id.voiceRecTimeLeft);
            mRecording = startRecordingInternal(context);
        }
    }

    public synchronized static void stopRecording(Activity activity, boolean send, boolean force) {
        if (mRecording || force) {
            stopRecordingInternal();

            if (send) {
                mSendingFile = mFileName;
                mFileName = null;
                sendVoiceMessage(activity);
            } else {
                SurespotLog.v(TAG, "not sending, deleting: %s", mSendingFile);
                new File(mFileName).delete();
            }

            VolumeEnvelopeView mEnvelopeView = (VolumeEnvelopeView) activity.findViewById(R.id.volume_envelope);
            mEnvelopeView.setVisibility(View.GONE);
            mVoiceHeaderView.setVisibility(View.GONE);
            // enable rotation
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

            mRecording = false;
        }
    }

    private synchronized static void sendVoiceMessage(final Activity activity) {
        int maxVolume = mEnvelopeView.getMaxVolume();
        SurespotLog.v(TAG, "max recorded volume: %d", maxVolume);
        try {
            if (maxVolume < SEND_THRESHOLD) {
                new File(mSendingFile).delete();
                Utils.makeToast(activity, activity.getString(R.string.no_audio_detected));
            } else {
                ChatController cc = ChatManager.getChatController(mFrom);
                if (cc != null) {
                    final String m4aFile = mSendingFile;
                    ChatUtils.uploadVoiceMessageAsync(
                            activity,
                            cc,
                            Uri.fromFile(new File(m4aFile)),
                            mFrom,
                            mTo);
                } else {
                    SurespotLog.w(TAG, "sendVoiceMessage null chat controller, deleting: %s", mSendingFile);
                    new File(mSendingFile).delete();
                    Utils.makeToast(activity, activity.getString(R.string.error_message_generic));
                }
            }
        } catch (Exception e) {
            if (mSendingFile != null) {
                SurespotLog.w(TAG, e, "sendVoiceMessage, deleting: %s", mSendingFile);
                new File(mSendingFile).delete();
            }
            Utils.makeToast(activity, activity.getString(R.string.error_message_generic));
        }
    }

    public synchronized static void playVoiceMessage(Context context, final SeekBar seekBar, final SurespotMessage message) {
        if (mRecording) {
            return;
        }

        SurespotLog.v(TAG, "playVoiceMessage");

        if (message.getPlainBinaryData() == null) {
            return;
        }

        boolean differentMessage = !message.equals(mMessage);

        stopPlaying();

        if (!mPlaying && differentMessage) {
            mPlaying = true;
            mMessage = message;
            mSeekBar = seekBar;
            if (mSeekBar != null) {
                mSeekBar.setMax(SEEK_MAX);
            }

            if (mSeekBarThread == null) {
                mSeekBarThread = new SeekBarThread();
            }

            mPlayer = new MediaPlayer();
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            try {
                if (mAudioFile != null) {
                    mAudioFile.delete();
                }

                mAudioFile = File.createTempFile("play", ".m4a");

                FileOutputStream fos = new FileOutputStream(mAudioFile);
                fos.write(message.getPlainBinaryData());
                fos.close();

                mPlayer.setOnPreparedListener(new OnPreparedListener() {

                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        mPlayer.start();
                        updatePlayControls();
                        mDuration = mPlayer.getDuration();
                        mPlayer.setOnPreparedListener(null);
                    }
                });

                mPlayer.setDataSource(mAudioFile.getAbsolutePath());
                mPlayer.prepareAsync();

            } catch (Exception e) {
                SurespotLog.w(TAG, e, "playVoiceMessage error");
                playCompleted();
                return;
            }

            mMessage.setVoicePlayed(true);
            new Thread(mSeekBarThread).start();
            mPlayer.setOnCompletionListener(new OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer mp) {

                    playCompleted();
                }
            });
        }
    }

    private static void stopPlaying() {
        if (mPlaying) {
            if (mPlayer != null) {
                mPlayer.stop();
            }
            playCompleted();
            if (mSeekBar != null) {
                setProgress(mSeekBar, 0);
            }

        }
    }

    public static void attach(final SeekBar seekBar) {
        if (isCurrentMessage(seekBar)) {
            mSeekBar = seekBar;
        } else {
            setProgress(seekBar, 0);
        }

        updatePlayControls();
    }

    private static void updatePlayControls() {

        ImageView voicePlayed = null;
        ImageView voicePlay = null;
        ImageView voiceStop = null;

        if (mSeekBar != null) {
            voicePlay = (ImageView) ((View) mSeekBar.getParent()).findViewById(R.id.voicePlay);
            voicePlayed = (ImageView) ((View) mSeekBar.getParent()).findViewById(R.id.voicePlayed);
            voiceStop = (ImageView) ((View) mSeekBar.getParent()).findViewById(R.id.voiceStop);
        }
        if (voicePlayed != null && voiceStop != null) {
            if (isCurrentMessage()) {
                SurespotLog.v(TAG, "updatePlayControls, currentMessage");

                voicePlayed.setVisibility(View.GONE);
                if (voicePlay != null) {
                    voicePlay.setVisibility(View.GONE);
                }
                voiceStop.setVisibility(View.VISIBLE);
            } else {
                SurespotMessage message = getSeekbarMessage(mSeekBar);
                if (message != null) {

                    SurespotLog.v(TAG, "message: %s not playing", message);

                    if (ChatUtils.isMyMessage(IdentityController.getLoggedInUser(), message)) {
                        voicePlayed.setVisibility(View.VISIBLE);
                    }
                    // //if it's ours we don't care if it's been played or not
                    else {

                        if (message.isVoicePlayed()) {
                            SurespotLog.v(TAG, "setting played to visible");
                            voicePlayed.setVisibility(View.VISIBLE);
                            if (voicePlay != null) {
                                voicePlay.setVisibility(View.GONE);
                            }
                        } else {
                            SurespotLog.v(TAG, "setting played to gone");
                            voicePlayed.setVisibility(View.GONE);
                            if (voicePlay != null) {
                                voicePlay.setVisibility(View.VISIBLE);
                            }
                        }
                    }

                    voiceStop.setVisibility(View.GONE);
                }
            }
        }
    }

    private static void setProgress(final SeekBar seekBar, final int progress) {
        if (seekBar == null) {
            return;
        }
        seekBar.post(new Runnable() {

            @Override
            public void run() {
                // SurespotLog.v(TAG, "Setting progress to %d", progress);
                seekBar.setProgress(progress);

            }

        });
    }

    private static class SeekBarThread implements Runnable {
        private boolean mRun = true;
        private int mLastPosition = 0;

        @Override
        public void run() {
            mRun = true;
            while (mRun) {
                int progress = 0;

                if (mDuration > -1) {

                    if (isCurrentMessage()) {

                        int currentPosition = 0;
                        try {
                            currentPosition = mPlayer.getCurrentPosition();
                        } catch (Exception e) {
                            SurespotLog.w(TAG, "SeekBarThread error getting current position");
                            mRun = false;
                            break;
                        }

                        //currentPosition sometimes wrong making seeker skip...at least stop it going backwards
                        //https://code.google.com/p/android/issues/detail?id=2559
                        if (currentPosition < mLastPosition) {
                            currentPosition = mLastPosition;
                        }

                        mLastPosition = currentPosition;

                        progress = (int) (((float) currentPosition / (float) mDuration) * SEEK_MAX);
                        // SurespotLog.v(TAG, "SeekBarThread: %s, currentPosition: %d, duration: %d, percent: %d", mSeekBar, currentPosition, mDuration,
                        // progress);

                        // TODO weight by length
                        if (progress < 0) {
                            progress = 0;
                        }
                        //SurespotLog.d(TAG, "setting seekBar: %s, progress: %d", mSeekBar, progress);

                        if (currentPosition < mDuration) {
                            if (!mRun) {
                                break;
                            }
                        }
                    }

                    setProgress(mSeekBar, progress);
                }

                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    mRun = false;
                    SurespotLog.w(TAG, e, "SeekBarThread interrupted");
                }
            }

            mLastPosition = 0;
            setProgress(mSeekBar, 0);
        }

        public void completed() {
            SurespotLog.v(TAG, "SeekBarThread completed");
            mRun = false;
            mLastPosition = 0;
            setProgress(mSeekBar, 0);
        }
    }

    private static boolean isCurrentMessage() {
        if (mSeekBar != null) {
            return isCurrentMessage(mSeekBar);
        }
        return false;

    }

    private static boolean isCurrentMessage(SeekBar seekBar) {
        if (seekBar == null) {
            return false;
        }

        SurespotMessage seekBarMessage = getSeekbarMessage(seekBar);
        if (seekBarMessage != null && seekBarMessage.equals(mMessage) && mPlaying) { //
            return true;
        } else {
            return false;
        }
    }

    public static SurespotMessage getSeekbarMessage(SeekBar seekBar) {
        if (seekBar != null) {
            WeakReference<SurespotMessage> ref = (WeakReference<SurespotMessage>) seekBar.getTag(R.id.tagMessage);
            if (ref != null) {
                return ref.get();
            }
        }

        return null;

    }

    public static void pause() {
        stopPlaying();
        mPlayCompletedCallback = null;
    }

    public static synchronized boolean isRecording() {
        return mState == State.RECORDING;
    }
    public static synchronized boolean isPlaying() {
        return mPlaying;
    }

    public static void setPlayCompletedCallback(IAsyncCallback<SurespotMessage> callback) {
        mPlayCompletedCallback = callback;
    }

}
