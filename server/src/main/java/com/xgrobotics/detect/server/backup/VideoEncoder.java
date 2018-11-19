package com.xgrobotics.detect.server.backup;

/**
 * Created by Stefan on 2018/10/13.
 */

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * This class use for Encode Video Frame Data.
 * Created by zj on 2018/7/29 0029.
 */
public class VideoEncoder {
    private final static String TAG = "VideoEncoder";
    private final static int CONFIGURE_FLAG_ENCODE = MediaCodec.CONFIGURE_FLAG_ENCODE;
    private final static int CACHE_BUFFER_SIZE = 8;

    private MediaCodec mMediaCodec;
    private MediaFormat mMediaFormat;
//    private int mViewWidth;
//    private int mViewHeight;

    private Handler mVideoEncoderHandler;
    private HandlerThread mVideoEncoderHandlerThread = new HandlerThread("VideoEncoder");

    //This video stream format must be I420
    private final static ArrayBlockingQueue<byte[]> mInputDatasQueue = new ArrayBlockingQueue<byte[]>(CACHE_BUFFER_SIZE);
    //Cachhe video stream which has been encoded.
    private final static ArrayBlockingQueue<byte[]> mOutputDatasQueue = new ArrayBlockingQueue<byte[]>(CACHE_BUFFER_SIZE);

    private MediaCodec.Callback mCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int id) {
            try {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(id);
                inputBuffer.clear();
                byte[] dataSources = mInputDatasQueue.poll();
                int length = 0;
                if (dataSources != null) {
                    inputBuffer.put(dataSources);
                    length = dataSources.length;
                    Log.d("Stefan", "length: " + length);

                }
                mediaCodec.queueInputBuffer(id, 0, length, 0, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int id, @NonNull MediaCodec.BufferInfo bufferInfo) {
            ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(id);
            MediaFormat outputFormat = mMediaCodec.getOutputFormat(id);
            if (outputBuffer != null && bufferInfo.size > 0) {
                byte[] buffer = new byte[outputBuffer.remaining()];
                outputBuffer.get(buffer);
                boolean result = mOutputDatasQueue.offer(buffer);
                if (!result) {
                    Log.d(TAG, "Offer to queue failed, queue in full state");
                }
            }
            mMediaCodec.releaseOutputBuffer(id, true);
        }

        @Override
        public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
            Log.d(TAG, "------> onError");
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
            Log.d(TAG, "------> onOutputFormatChanged");
        }
    };

    public VideoEncoder(String mimeType, int viewwidth, int viewheight) {
        try {
            mMediaCodec = MediaCodec.createEncoderByType(mimeType);
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            mMediaCodec = null;
            return;
        }

        mVideoEncoderHandlerThread.start();
        mVideoEncoderHandler = new Handler(mVideoEncoderHandlerThread.getLooper());

        mMediaFormat = MediaFormat.createVideoFormat(mimeType, viewwidth, viewheight);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1920 * 1280);
        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
    }

    /**
     * Input Video stream which need encode to Queue
     *
     * @param needEncodeData I420 format stream
     */
    public void inputFrameToEncoder(byte[] needEncodeData) {
        boolean inputResult = mInputDatasQueue.offer(needEncodeData);
//        Log.d(TAG, "-----> inputEncoder queue result = " + inputResult + " queue current size = " + mInputDatasQueue.size());
    }

    /**
     * Get Encoded frame from queue
     *
     * @return a encoded frame; it would be null when the queue is empty.
     */
    public byte[] pollFrameFromEncoder() {
        return mOutputDatasQueue.poll();
    }

    /**
     * start the MediaCodec to encode video data
     */
    public void startEncoder() {
        if (mMediaCodec != null) {
            mMediaCodec.setCallback(mCallback, mVideoEncoderHandler);
            mMediaCodec.configure(mMediaFormat, null, null, CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
        } else {
            throw new IllegalArgumentException("startEncoder failed,is the MediaCodec has been init correct?");
        }
    }

    /**
     * stop encode the video data
     */
    public void stopEncoder() {
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.setCallback(null);
        }
    }

    /**
     * release all resource that used in Encoder
     */
    public void release() {
        if (mMediaCodec != null) {
            mInputDatasQueue.clear();
            mOutputDatasQueue.clear();
            mMediaCodec.release();
        }
    }
}