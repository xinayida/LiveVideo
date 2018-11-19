package com.xgrobotics.detect.server;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by Stefan on 2018/10/17.
 */
public class VideoPlayView extends SurfaceView implements SurfaceHolder.Callback {
    private final static int CACHE_BUFFER_SIZE = 1;
    private LoopThread loopThread;
    private Receiver.IReceiveCallback callback;
    private final static ArrayBlockingQueue<ImageData> mInputDatasQueue = new ArrayBlockingQueue<>(CACHE_BUFFER_SIZE);

    public VideoPlayView(Context context) {
        super(context);
        init();
    }

    public VideoPlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }


    private void init() {
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        loopThread = new LoopThread(getContext(), holder);
        callback = new Receiver.IReceiveCallback() {
            @Override
            public void onDataUpdate(byte[] data, int len) {
                loopThread.updateImage(data, len);
            }
        };
    }

    public Receiver.IReceiveCallback getCallback() {
        return callback;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        loopThread.isRunning = true;
        loopThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        loopThread.isRunning = false;
        try {
            loopThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    class LoopThread extends Thread {
        public static final int TIME_IN_FRAME = 10;
        SurfaceHolder surfaceHolder;
        Context context;
        boolean isRunning;
        //        float radius = 10f;
        Paint paint;
        private Bitmap bitmap;

        public LoopThread(Context context, SurfaceHolder surfaceHolder) {
            this.context = context;
            this.surfaceHolder = surfaceHolder;

            paint = new Paint();
//            paint.setColor(Color.YELLOW);
//            paint.setStyle(Paint.Style.STROKE);
        }

        @Override
        public void run() {
            Canvas c;
            long ts = System.currentTimeMillis();
            int count = 0;
            while (isRunning) {
                try {
                    long startTime = System.currentTimeMillis();
                    c = surfaceHolder.lockCanvas();
                    doDraw(c);
                    surfaceHolder.unlockCanvasAndPost(c);
                    count++;
                    if (System.currentTimeMillis() - ts > 1000) {
                        Log.d("Stefan", "FPS: " + count);
                        ts = System.currentTimeMillis();
                        count = 0;
                    }
                    long endTime = System.currentTimeMillis();
                    int diffTime = (int) (endTime - startTime);
                    if (diffTime < TIME_IN_FRAME) {
                        Thread.sleep(TIME_IN_FRAME - diffTime);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void doDraw(Canvas c) {
//            if (mInputDatasQueue.size() > 0) {
//                ImageData img = mInputDatasQueue.poll();
//                bitmap = BitmapFactory.decodeByteArray(img.data, 0, img.len);
//            }
            if (bitmap != null) {
                synchronized (this) {
                    c.drawBitmap(bitmap, 0, 0, paint);
                }
            }
//            c.drawColor(Color.BLACK);
//            c.translate(200, 200);
//            c.drawCircle(0, 0, radius++, paint);
//            if (radius > 100) {
//                radius = 10f;
//            }
        }

        public synchronized void updateImage(byte[] data, int len) {
//            mInputDatasQueue.offer(new ImageData(data, len));
            bitmap = BitmapFactory.decodeByteArray(data, 0, len);
        }
    }

    private class ImageData {
        public ImageData(byte[] data, int len) {
            this.data = data;
            this.len = len;
        }

        byte[] data;
        int len;
    }

}
