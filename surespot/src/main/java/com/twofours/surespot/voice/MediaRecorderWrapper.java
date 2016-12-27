package com.twofours.surespot.voice;

import android.media.MediaRecorder;

import com.twofours.surespot.SurespotLog;

public class MediaRecorderWrapper {
    /**
     * INITIALIZING : recorder is initializing; READY : recorder has been initialized, recorder not yet started RECORDING : recording ERROR : reconstruction
     * needed STOPPED: reset needed
     */
    public enum State {
        INITIALIZING, READY, RECORDING, ERROR, STOPPED
    }

    protected static final String TAG = "MediaRecorderWrapper";


    // Recorder used for compressed recording
    private MediaRecorder mRecorder = null;

    // Output file path
    private String fPath = null;

    // Recorder state; see State
    private State state;


    // Number of channels, sample rate, sample size(size in bits), buffer size, audio source, sample size(see AudioFormat)
    private short nChannels;
    private int sRate;
    private short bSamples;
    private int bufferSize;
    private int aSource;
    private int aFormat;

    // Number of frames written to file on each output(only in uncompressed mode)
    private int framePeriod;

    // Buffer for output(only in uncompressed mode)
    private byte[] buffer;

    // Number of bytes written to file after header(only in uncompressed mode)
    // after stop() is called, this size is written to the header/data chunk in the wave file
    private int payloadSize;

    /**
     * Returns the state of the recorder in a RehearsalAudioRecord.State typed object. Useful, as no exceptions are thrown.
     *
     * @return recorder state
     */
    public State getState() {
        return state;
    }


    /**
     * Default constructor
     * <p/>
     * Instantiates a new recorder, in case of compressed recording the parameters can be left as 0. In case of errors, no exception is thrown, but the state is
     * set to ERROR
     */
    public MediaRecorderWrapper() {
        try {
            mRecorder = new MediaRecorder();
            mRecorder.setAudioEncodingBitRate(24000);
            mRecorder.setAudioChannels(1);
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

            fPath = null;
            state = State.INITIALIZING;
        } catch (Exception e) {
            if (e.getMessage() != null) {
                SurespotLog.e(MediaRecorderWrapper.class.getName(), e, e.getMessage());
            } else {
                SurespotLog.e(MediaRecorderWrapper.class.getName(), e, "Unknown error occured while initializing recording");
            }
            state = State.ERROR;
        }
    }

    /**
     * Sets output file path, call directly after construction/reset.
     *
     * @param argPath file path
     */
    public void setOutputFile(String argPath) {
        try {
            if (state == State.INITIALIZING) {
                fPath = argPath;
                mRecorder.setOutputFile(fPath);
            }
        } catch (Exception e) {
            if (e.getMessage() != null) {
                SurespotLog.e(MediaRecorderWrapper.class.getName(), e, e.getMessage());
            } else {
                SurespotLog.e(MediaRecorderWrapper.class.getName(), e, "Unknown error occured while setting output path");
            }
            state = State.ERROR;
        }
    }

    /**
     * Returns the largest amplitude sampled since the last call to this method.
     *
     * @return returns the largest amplitude since the last call, or 0 when not in recording state.
     */
    public int getMaxAmplitude() {
        if (state == State.RECORDING) {
            try {
                return mRecorder.getMaxAmplitude();
            } catch (IllegalStateException e) {
                return 0;
            }
        } else {
            return 0;
        }
    }

    /**
     * Prepares the recorder for recording, in case the recorder is not in the INITIALIZING state and the file path was not set the recorder is set to the ERROR
     * state, which makes a reconstruction necessary. In case uncompressed recording is toggled, the header of the wave file is written. In case of an
     * exception, the state is changed to ERROR
     */
    public void prepare() {
        try {
            if (state == State.INITIALIZING) {
                mRecorder.prepare();
                state = State.READY;
            } else {
                SurespotLog.w(MediaRecorderWrapper.class.getName(), "prepare() method called on illegal state");
                release();
                state = State.ERROR;
            }
        } catch (Exception e) {
            if (e.getMessage() != null) {
                SurespotLog.e(MediaRecorderWrapper.class.getName(), e, e.getMessage());
            } else {
                SurespotLog.e(MediaRecorderWrapper.class.getName(), e, "Unknown error occured in prepare()");
            }
            state = State.ERROR;
        }
    }

    /**
     * Releases the resources associated with this class, and removes the unnecessary files, when necessary
     */
    public void release() {
        if (state == State.RECORDING) {
            stop();
        }

        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }
    }


    /**
     * Starts the recording, and sets the state to RECORDING. Call after prepare().
     */
    public void start() {
        if (state == State.READY) {
            mRecorder.start();
            state = State.RECORDING;
        } else {
            SurespotLog.w(MediaRecorderWrapper.class.getName(), "start() called on illegal state");
            state = State.ERROR;
        }
    }

    /**
     * Stops the recording, and sets the state to STOPPED. In case of further usage, a reset is needed. Also finalizes the wave file in case of uncompressed
     * recording.
     */
    public void stop() {
        if (state == State.RECORDING) {
            mRecorder.stop();
            state = State.STOPPED;

        } else {
            SurespotLog.w(MediaRecorderWrapper.class.getName(), "stop() called on illegal state");
            state = State.ERROR;
        }
    }
}
