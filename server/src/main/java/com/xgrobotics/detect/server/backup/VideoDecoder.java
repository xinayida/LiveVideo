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

/**
 * This class use for Decode Video Frame Data and show to SurfaceTexture
 * Created by zj on 2018/7/29 0029.
 */
public class VideoDecoder {
    private final static String TAG = "VideoEncoder";
    private final static int CONFIGURE_FLAG_DECODE = 0;

    private MediaCodec mMediaCodec;
    private MediaFormat mMediaFormat;
    private Surface mSurface;
    private int mViewWidth;
    private int mViewHeight;

    private VideoEncoder mVideoEncoder;
    private Handler mVideoDecoderHandler;
    private HandlerThread mVideoDecoderHandlerThread = new HandlerThread("VideoDecoder");

    private MediaCodec.Callback mCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int id) {
            try {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(id);
                inputBuffer.clear();

                byte[] dataSources = null;
                if (mVideoEncoder != null) {
                    dataSources = mVideoEncoder.pollFrameFromEncoder();
                }
                int length = 0;
                if (dataSources != null) {
                    inputBuffer.put(dataSources);
                    length = dataSources.length;
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
            if (mMediaFormat == outputFormat && outputBuffer != null && bufferInfo.size > 0) {
                byte[] buffer = new byte[outputBuffer.remaining()];
                outputBuffer.get(buffer);
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

    public VideoDecoder(String mimeType, Surface surface, int viewwidth, int viewheight) {
        try {
            mMediaCodec = MediaCodec.createDecoderByType(mimeType);
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            mMediaCodec = null;
            return;
        }

        if (surface == null) {
            return;
        }

        this.mViewWidth = viewwidth;
        this.mViewHeight = viewheight;
        this.mSurface = surface;

        mVideoDecoderHandlerThread.start();
        mVideoDecoderHandler = new Handler(mVideoDecoderHandlerThread.getLooper());

        mMediaFormat = MediaFormat.createVideoFormat(mimeType, mViewWidth, mViewHeight);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1920 * 1280);
        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
    }

    public void setEncoder(VideoEncoder videoEncoder) {
        this.mVideoEncoder = videoEncoder;
    }

    public void startDecoder() {
        if (mMediaCodec != null && mSurface != null) {
            mMediaCodec.setCallback(mCallback, mVideoDecoderHandler);
            mMediaCodec.configure(mMediaFormat, mSurface, null, CONFIGURE_FLAG_DECODE);
            mMediaCodec.start();
        } else {
            throw new IllegalArgumentException("startDecoder failed, please check the MediaCodec is init correct");
        }
    }

    public void stopDecoder() {
        if (mMediaCodec != null) {
            mMediaCodec.stop();
        }
    }

    /**
     * release all resource that used in Encoder
     */
    public void release() {
        if (mMediaCodec != null) {
            mMediaCodec.release();
            mMediaCodec = null;
        }
    }
}