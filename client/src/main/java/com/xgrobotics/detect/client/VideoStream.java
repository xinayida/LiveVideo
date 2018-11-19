package com.xgrobotics.detect.client;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;


/**
 * Created by Stefan on 2018/10/15.
 */
public class VideoStream {

    private final int VIDEO_WIDTH = 640;
    private final int VIDEO_HEIGHT = 480;
    //编码类型
    private String MIME = "video/avc";
    private final static String TAG = "Stefan";
    private Camera mCamera;
    private int mCameraId;
    private MediaCodec mMediaCodec;
    private SurfaceView mSurfaceView;
    private Looper mCameraLooper;
    private boolean mSurfaceReady;
    private boolean mUpdated;
    private boolean mUnlocked;
    //    private boolean mCameraOpenManully = true;
    private boolean mPreviewStarted;
    private boolean mStreaming;
    private SurfaceHolder.Callback mSurfaceHolderCallback;

    private final static int TRANS_MODE = 0;//0:H264,1:YUVImage
    private ByteBuffer[] mBuffers = null;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private byte[] mIOBuffer;
    private Thread decodeThread;
    private boolean mIsRunning = true;
    //转成后的数据
    private byte[] yuv420 = null;
    //旋转后的数据
    private byte[] rotateYuv420 = null;

    private Sender sender;

    /**
     * 新开线程启动camera，避免preview callback在主线程中回调
     * 如果发生异常，则将异常带回主线程
     *
     * @throws RuntimeException 如果有其他app占用了摄像头，则抛出异常
     */
    private void openCamera() throws RuntimeException {
        final Semaphore lock = new Semaphore(0);
        final RuntimeException[] exceptions = new RuntimeException[1];
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mCameraLooper = Looper.myLooper();
                try {
                    mCamera = Camera.open(mCameraId);
                } catch (RuntimeException e) {
                    exceptions[0] = e;
                } finally {
                    lock.release();
                    Looper.loop();
                }
            }
        }).start();
        lock.acquireUninterruptibly();
        if (exceptions[0] != null) throw new RuntimeException(exceptions[0].getMessage());
    }

    protected synchronized void createCamera() throws RuntimeException {
        if (mSurfaceView == null) {
            throw new RuntimeException("Invalid surface !");
        }
        if (mSurfaceView.getHolder() == null || !mSurfaceReady) {
            throw new RuntimeException("Invalid surface !");
        }

        if (mCamera == null) {
            openCamera();
            mUpdated = false;
            mUnlocked = false;
            mCamera.setErrorCallback(new Camera.ErrorCallback() {
                @Override
                public void onError(int error, Camera camera) {
                    //某些手机调用前置摄像头，media server会崩溃
                    if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
                        //这种情况下，应用必须释放camera并且重建一个新的
                        Log.e(TAG, "Media server diead !");
                        //我们不知道当前是在哪个线程，所以调用stop需要加上同步
//                        mCameraOpenManully = false;
                        destroyCamera();
                    } else {
                        Log.e(TAG, "Error unkown with the camera: " + error);
                    }
                }
            });

            try {

                Camera.Parameters parameters = mCamera.getParameters();
                if (parameters.getFlashMode() != null) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                }
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);//自动对焦
                parameters.setRecordingHint(true);
                mCamera.setParameters(parameters);
                mCamera.setDisplayOrientation(90);
                try {
                    mCamera.setPreviewDisplay(mSurfaceView.getHolder());
                } catch (IOException e) {
                    throw new RuntimeException("Invalid surface !");
                }
            } catch (RuntimeException e) {
                destroyCamera();
                throw e;
            }
        }
    }

//    public synchronized void stop() {
//        if (decodeThread != null) {
//            decodeThread.interrupt();
//            try {
//                decodeThread.join();
//            } catch (InterruptedException e) {
//            }
//            decodeThread = null;
//        }
//        stopDecoder();
//        if (!mCameraOpenManully) {

//            destroyCamera();

//        } else {
//            try {
//                startPreview();
//            } catch (RuntimeException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//    public synchronized void startPreview() throws RuntimeException {
//        mCameraOpenManully = true;
//        if (!mPreviewStarted) {
//            createCamera();
//            updateCamera();
//        }
//    }
//
//    public synchronized void stopPreview() {
//        mCameraOpenManully = false;
//        stop();
//    }

    public synchronized void updateCamera() throws RuntimeException {
        if (mUpdated) return;

        if (mPreviewStarted) {
            mPreviewStarted = false;
            mCamera.stopPreview();
        }

        Camera.Parameters parameters = mCamera.getParameters();
//        mSurfaceView.requestAs
        parameters.setPreviewFormat(ImageFormat.NV21);
        parameters.setPreviewSize(VIDEO_WIDTH, VIDEO_HEIGHT);
        int max[] = determineMaximumSupportedFramerate(parameters);
        parameters.setPreviewFpsRange(max[0], max[1]);
        parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
        try {
            mCamera.setParameters(parameters);
            mCamera.setDisplayOrientation(90);
            mCamera.startPreview();
            mPreviewStarted = true;
            mUpdated = true;
        } catch (RuntimeException e) {
            destroyCamera();
            throw e;
        }

    }

    public synchronized void setSurfaceView(SurfaceView surfaceView) {
        mSurfaceView = surfaceView;
        if (mSurfaceHolderCallback != null && mSurfaceView != null && mSurfaceView.getHolder() != null) {
            mSurfaceView.getHolder().removeCallback(mSurfaceHolderCallback);
        }
        if (mSurfaceView.getHolder() != null) {
            mSurfaceHolderCallback = new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    mSurfaceReady = true;
                    try {
                        encodeWithMediaCodec();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    mSurfaceReady = false;
                    stopDecoder();
                }
            };
            mSurfaceView.getHolder().addCallback(mSurfaceHolderCallback);
            mSurfaceReady = true;
        }
    }

    private int[] determineMaximumSupportedFramerate(Camera.Parameters parameters) {
        int[] maxFps = new int[]{0, 0};
        String supportedFpsRangesStr = "Supported frame rates: ";
        List<int[]> supportedFpsRanges = parameters.getSupportedPreviewFpsRange();
        for (Iterator<int[]> it = supportedFpsRanges.iterator(); it.hasNext(); ) {
            int[] interval = it.next();
            // Intervals are returned as integers, for example "29970" means "29.970" FPS.
            supportedFpsRangesStr += interval[0] / 1000 + "-" + interval[1] / 1000 + "fps" + (it.hasNext() ? ", " : "");
            if (interval[1] > maxFps[1] || (interval[0] > maxFps[0] && interval[1] == maxFps[1])) {
                maxFps = interval;
            }
        }
        Log.v(TAG, supportedFpsRangesStr);
        return maxFps;
    }

    protected void lockCamera() {
        if (mUnlocked) {
            Log.d(TAG, "Locking camera");
            try {
                mCamera.reconnect();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            mUnlocked = false;
        }
    }

    protected synchronized void destroyCamera() {
        if (mCamera != null) {
            mCameraLooper.quit();
//            if (mStreaming) stopDecoder();
            if (mCamera != null) {
                mCamera.setPreviewCallbackWithBuffer(null);
            }
            lockCamera();
            mCamera.stopPreview();
            try {
                mCamera.release();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage() != null ? e.getMessage() : "unknown error");
            }
            mCamera = null;
//            mCameraLooper.quit();
            mUnlocked = false;
            mPreviewStarted = false;
        }
    }

    public synchronized void stopDecoder() {
        mStreaming = false;
    }

    public void encodeWithMediaCodec() throws RuntimeException, IOException {
        Log.d(TAG, "Video encoded using the MediaCodec API with a buffer");

        createCamera();
        updateCamera();

        if (!mPreviewStarted) {
            try {
                mCamera.startPreview();
                mPreviewStarted = true;
            } catch (RuntimeException e) {
                destroyCamera();
                throw e;
            }
        }

        Camera.PreviewCallback callback = new Camera.PreviewCallback() {
            long now = System.nanoTime() / 1000, oldnow = now, i = 0;
            ByteBuffer[] inputBuffers;

            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                if (!mIsRunning || !mStreaming) return;
                oldnow = now;
                now = System.nanoTime() / 1000;
//                if (i++ > 3) {
//                    i = 0;
//                    Log.d("Stefan", "Measured: " + 1000000L / (now - oldnow) + " fps.");
//                }
                try {
                    if (TRANS_MODE == 0) {
                        if (inputBuffers == null) {
                            inputBuffers = mMediaCodec.getInputBuffers();
                        }
                        int bufferIndex = mMediaCodec.dequeueInputBuffer(10);
                        if (bufferIndex >= 0) {
                            if (data == null)
                                Log.e(TAG, "Symptom of the \"Callback buffer was to small\" problem...");
                            else {
                                NV21ToNV12(data, rotateYuv420, VIDEO_WIDTH, VIDEO_HEIGHT);
//                            convertor.convert(data, inputBuffers[bufferIndex]);
                                //把视频顺时针旋转90度。（正常视觉效果）
                                YUV420spRotate90Clockwise(rotateYuv420, yuv420, VIDEO_WIDTH, VIDEO_HEIGHT);
                                ByteBuffer inputBuffer = inputBuffers[bufferIndex];
                                inputBuffer.clear();
                                inputBuffer.put(yuv420);
                            }
                            if (mStreaming) {
                                mMediaCodec.queueInputBuffer(bufferIndex, 0, inputBuffers[bufferIndex].position(), now, 0);
                            } else {
                                Log.w(TAG, "END OF STREAM");
                                mMediaCodec.queueInputBuffer(bufferIndex, 0, inputBuffers[bufferIndex].position(), now, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                mIsRunning = false;
                                destroyCamera();

                            }
                        } else {
                            Log.e(TAG, "No buffer available !");
                        }

                    } else if (TRANS_MODE == 1) {
                        YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, VIDEO_WIDTH, VIDEO_HEIGHT, null);
                        if (yuvImage != null) {
                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            yuvImage.compressToJpeg(new Rect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT), 80, stream);
                            byte[] bytes = stream.toByteArray();
                            try {
                                sender.writeInt(bytes.length);
                                sender.write(bytes, 0, bytes.length);
                                stream.close();
                            } catch (Exception e) {
                                Log.e("Stefan", "compress image error: " + e.getMessage());
                                e.printStackTrace();
                            }

                        }
                    }

                } finally {
                    camera.addCallbackBuffer(data);
                }
            }
        };

        for (int i = 0; i < 10; i++)
            mCamera.addCallbackBuffer(new byte[/*convertor.getBufferSize()*/getYuvBuffer()]);
        mCamera.setPreviewCallbackWithBuffer(callback);

        if (TRANS_MODE == 0) {
            int colorFormat = selectColorFormat(selectCodec(MIME), MIME);
            mMediaCodec = MediaCodec.createEncoderByType(MIME);
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME, VIDEO_HEIGHT, VIDEO_WIDTH);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1000000);
            mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);//编码器尽量把输出码率控制为设定值
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 40);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
        }
        startDecoder();
        mStreaming = true;
    }

    private void startDecoder() {
        decodeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                sender.init();
                if (TRANS_MODE == 0) {
                    mBuffers = mMediaCodec.getOutputBuffers();
                    while (!Thread.interrupted() && mIsRunning) {
                        ByteBuffer mBuffer;
                        int index = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 10);
                        if (index >= 0) {
                            mBuffer = mBuffers[index];
//                            mBuffer.position(0);
                            mBuffer.position(mBufferInfo.offset);
                            mBuffer.limit(mBufferInfo.offset + mBufferInfo.size);
                            if (mIOBuffer == null || mIOBuffer.length < mBufferInfo.size) {
                                mIOBuffer = new byte[mBufferInfo.size];
                            }
                            mBuffer.get(mIOBuffer, 0, mBufferInfo.size);
                            mMediaCodec.releaseOutputBuffer(index, false);

                            try {
                                sender.writeInt(mBufferInfo.size);
                                sender.writeLong(mBufferInfo.presentationTimeUs);
                                sender.write(mIOBuffer, 0, mBufferInfo.size);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            mBuffers = mMediaCodec.getOutputBuffers();
                        }
                    }
                    try {
                        if (mMediaCodec != null) {
                            mMediaCodec.stop();
                            mMediaCodec.release();
                            mMediaCodec = null;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (sender != null) {
                        sender.release();
                        sender = null;
                    }
                }
            }
        });
        decodeThread.start();
    }

//    private class NV21Convertor {
//        private byte[] mBuffer;
//        private int mSize;
//        private int mWidth, mHeight;
//
//        public NV21Convertor(int width, int height) {
//            mWidth = width;
//            mHeight = height;
//            mSize = mWidth * mHeight;
//        }
//
//        public void convert(byte[] data, ByteBuffer buffer) {
//            byte[] result = convert(data);
//            int min = buffer.capacity() < data.length ? buffer.capacity() : data.length;
//            buffer.put(result, 0, min);
//        }
//
//        public byte[] convert(byte[] data) {
//            if (mBuffer == null || mBuffer.length != 3 * mHeight * mWidth / 2) {
//                mBuffer = new byte[3 * mWidth * mHeight / 2];
//            }
//
//            for (int i = 0; i < mSize / 4; i += 1) {
//                mBuffer[i] = data[mSize + 2 * i];
//                mBuffer[mSize / 4 + i] = data[mSize + 2 * i + 1];
//            }
//            System.arraycopy(mBuffer, 0, data, mSize, mSize / 2);
//            return data;
//        }
//
//        public int getBufferSize() {
//            return 3 * mSize / 2;
//        }
//
//    }

    public void setCamera(int camera) {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == camera) {
                mCameraId = i;
                break;
            }
        }
    }

    public VideoStream(int cameraId, String ip, int port) {
        setCamera(cameraId);
//        convertor = new NV21Convertor(VIDEO_WIDTH, VIDEO_HEIGHT);
        sender = new Sender(ip, port);
        yuv420 = new byte[getYuvBuffer()];
        rotateYuv420 = new byte[getYuvBuffer()];
    }


    //计算YUV的buffer的函数，需要根据文档计算，而不是简单“*3/2”
    public int getYuvBuffer() {
        int width = VIDEO_WIDTH;
        int height = VIDEO_HEIGHT;
        // stride = ALIGN(width, 16)
        int stride = (int) Math.ceil(width / 16.0) * 16;
        // y_size = stride * height
        int y_size = stride * height;
        // c_stride = ALIGN(stride/2, 16)
        int c_stride = (int) Math.ceil(width / 32.0) * 16;
        // c_size = c_stride * height/2
        int c_size = c_stride * height / 2;
        // size = y_size + c_size * 2
        return y_size + c_size * 2;
    }

    private MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }


    //通过mimeType确定支持的格式
    private int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.e(TAG, "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;   // not reached
    }


    private boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    //选择了YUV420SP作为编码的目标颜色空间，其实YUV420SP就是NV12，咱们CAMERA设置的是NV21，所以需要转一下
    private void NV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null) return;
        int framesize = width * height;
        int i = 0, j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for (i = 0; i < framesize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j - 1] = nv21[j + framesize];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j] = nv21[j + framesize - 1];
        }
    }

    //顺时针旋转90
    private void YUV420spRotate90Clockwise(byte[] src, byte[] dst, int width, int height) {
        int wh = width * height;
        int uvHeight = height >> 1;

        //旋转Y
        int k = 0;
        for (int i = 0; i < width; i++) {
            int nPos = 0;
            for (int j = 0; j < height; j++) {
//                dst[k] = src[nPos + i];
                dst[getReverseK(k, height)] = src[nPos + i];
                k++;
                nPos += width;
            }
        }

        for (int i = 0; i < width; i += 2) {
            int nPos = wh;
            for (int j = 0; j < uvHeight; j++) {
                int rk = getReverseK(k, height);
//                dst[k] = src[nPos + i];
//                dst[k + 1] = src[nPos + i + 1];
                dst[rk - 1] = src[nPos + i];
                dst[rk] = src[nPos + i + 1];
                k += 2;
                nPos += width;
            }
        }
    }

    private int getReverseK(int k, int height) {
        return ((k / height) * height + (height - k % height - 1));
    }

}
