package com.xgrobotics.detect.server;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

/**
 * Created by Stefan on 2018/10/16.
 */
public class Decoder implements Runnable {
    private static final String TAG = "Stefan";
    private final String MIME_TYPE = "video/avc";
    private MediaCodec mDecoder;
    private MediaFormat mDecOutputFormat;
    private ByteBuffer[] inputBuffer, decOutputBuffers;
    private MediaCodec.BufferInfo bufferInfo;
    private Surface mSurface;
    private boolean mIsRunning;
    private Thread t;

    public Decoder(Surface surface) throws IOException {
        this.mSurface = surface;
        mDecoder = MediaCodec.createDecoderByType(MIME_TYPE);
        bufferInfo = new MediaCodec.BufferInfo();
    }

    private void config(byte[] sps, byte[] pps, Surface surface) {
        int[] width = new int[1];
        int[] height = new int[1];
        AvcUtils.parseSPS(sps, width, height);
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width[0], height[0]);
        mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
        mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width[0] * height[0]);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatYUV420Flexible);
        mDecoder.configure(mediaFormat, surface, null, 0);
    }

    public void start() {
        mDecoder.start();
        inputBuffer = mDecoder.getInputBuffers();
        decOutputBuffers = mDecoder.getOutputBuffers();
        mIsRunning = true;
        if (t == null) {
            t = new Thread(this);
            t.start();
        }
    }

    public void decode(byte[] data, int len, long ts) {
        int index = mDecoder.dequeueInputBuffer(ts);
        if (index >= 0) {
            ByteBuffer buffer = inputBuffer[index];
            buffer.clear();
            buffer.put(data, 0, len);
            mDecoder.queueInputBuffer(index, 0, len, ts, 0);
        }
    }

    public void init(byte[] buffer, int len) {
        byte[] sps_nal = null;
        int sps_len = 0;
        byte[] pps_nal = null;
        int pps_len = 0;
        ByteBuffer byteb = ByteBuffer.wrap(buffer, 0, len);
        //SPS
        if (AvcUtils.goToPrefix(byteb)) {
            int sps_position = 0;
            int pps_position = 0;
            int nal_type = AvcUtils.getNalType(byteb);
            if (AvcUtils.NAL_TYPE_SPS == nal_type) {
                Log.d(TAG, "OutputAvcBuffer, AVC NAL type: SPS");
                sps_position = byteb.position() - AvcUtils.START_PREFIX_LENGTH - AvcUtils.NAL_UNIT_HEADER_LENGTH;
                //PPS
                if (AvcUtils.goToPrefix(byteb)) {
                    nal_type = AvcUtils.getNalType(byteb);
                    if (AvcUtils.NAL_TYPE_PPS == nal_type) {
                        pps_position = byteb.position() - AvcUtils.START_PREFIX_LENGTH - AvcUtils.NAL_UNIT_HEADER_LENGTH;
                        sps_len = pps_position - sps_position;
                        sps_nal = new byte[sps_len];
                        int cur_pos = byteb.position();
                        byteb.position(sps_position);
                        byteb.get(sps_nal, 0, sps_len);
                        byteb.position(cur_pos);
                        //slice
                        if (AvcUtils.goToPrefix(byteb)) {
                            nal_type = AvcUtils.getNalType(byteb);
                            int pps_end_position = byteb.position() - AvcUtils.START_PREFIX_LENGTH - AvcUtils.NAL_UNIT_HEADER_LENGTH;
                            pps_len = pps_end_position - pps_position;
                        } else {
                            pps_len = byteb.position() - pps_position;
                            //pps_len = byteb.limit() - pps_position + 1;
                        }
                        if (pps_len > 0) {
                            pps_nal = new byte[pps_len];
                            cur_pos = byteb.position();
                            byteb.position(pps_position);
                            byteb.get(pps_nal, 0, pps_len);
                            byteb.position(cur_pos);
                        }
                    } else {
                        //Log.d(log_tag, "OutputAvcBuffer, AVC NAL type: "+nal_type);
                        throw new UnsupportedOperationException("SPS is not followed by PPS, nal type :" + nal_type);
                    }
                }
            } else {
                Log.d(TAG, "OutputAvcBuffer, AVC NAL type: " + nal_type);
            }

            //2. configure AVC decoder with SPS/PPS
            if (sps_nal != null && pps_nal != null) {
                config(sps_nal, pps_nal, mSurface);
                start();
            }
        }
    }

    @Override
    public void run() {
        int index;
        while (mIsRunning) {
            try {
                index = mDecoder.dequeueOutputBuffer(bufferInfo, 50000);
                if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    decOutputBuffers = mDecoder.getOutputBuffers();
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mDecOutputFormat = mDecoder.getOutputFormat();
                } else if (index >= 0) {
                    if (decOutputBuffers[index] != null) {
//                    decOutputBuffers[index].clear();
                        mDecoder.releaseOutputBuffer(index, true);
                    }
                }
            } catch (Exception e) {
                Log.e("Stefan", "Decoder exception: " + e.getMessage());
                e.printStackTrace();
                break;
            }
        }
        Log.e("Stefan", "Decoder stopped");
    }

    public void stop() {
        mIsRunning = false;
        if (t != null) {
            t.interrupt();
//            try {
//                t.join();
//            } catch (InterruptedException e) {
//            }
            t = null;
        }
        if (mDecoder != null) {
            mDecoder.stop();
            mDecoder.release();
            mDecoder = null;
        }
    }
}
