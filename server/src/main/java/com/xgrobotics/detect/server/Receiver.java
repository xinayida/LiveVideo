package com.xgrobotics.detect.server;

import android.util.Log;
import android.view.SurfaceView;

import com.xgrobotics.detect.lib.DetectConst;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by Stefan on 2018/10/12.
 */
public class Receiver implements Runnable {

    private ServerSocket mServerSocket;
    private Decoder mDecoder;
    private byte[] mIOBuffer;
    private Thread t;
    public static final int TRAN_MODE = 0;//0:h.264 1:bitmap

    public Receiver(SurfaceView surfaceView) {
        if (TRAN_MODE == 0) {
            try {
                mDecoder = new Decoder(surfaceView.getHolder().getSurface());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            setReceiveCallback(((VideoPlayView) surfaceView).getCallback());
        }
    }

    public void start() {
        if (t == null) {
            t = new Thread(this);
            t.start();
        }
    }

    public void stop() {
        if (t != null) {
            t.interrupt();
            t = null;
        }
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
                mServerSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mDecoder != null)
            mDecoder.stop();
    }

    private void readNAL(DataInputStream dis, int[] len, long[] ts) throws IOException {
        len[0] = dis.readInt();
        ts[0] = dis.readLong();
        if (mIOBuffer == null || mIOBuffer.length < len[0]) {
            mIOBuffer = new byte[len[0]];
        }
        int readed = dis.read(mIOBuffer, 0, len[0]);
        while (len[0] - readed > 0) {
            readed += dis.read(mIOBuffer, readed, len[0] - readed);
        }
    }

    private void readFrame(DataInputStream dis) throws IOException {
        int len = dis.readInt();
        if (mIOBuffer == null || mIOBuffer.length < len) {
            mIOBuffer = new byte[len];
        }
        int readed = dis.read(mIOBuffer, 0, len);
        while (len - readed > 0) {
            readed += dis.read(mIOBuffer, readed, len - readed);
        }
        if (iReceiveCallback != null) {
            iReceiveCallback.onDataUpdate(mIOBuffer.clone(), len);
        }
    }

    public void run() {
        try {
            mServerSocket = new ServerSocket(DetectConst.STREAM_PORT);
            mServerSocket.setSoTimeout(5000);//milliseconds
            Socket socket;
            DataInputStream dis;
            socket = mServerSocket.accept();
            dis = new DataInputStream(socket.getInputStream());
            if (TRAN_MODE == 0) {
                int[] len = new int[1];
                long[] ts = new long[1];
                readNAL(dis, len, ts);
                mDecoder.init(mIOBuffer, len[0]);
                while (!t.isInterrupted()) {
                    readNAL(dis, len, ts);
                    mDecoder.decode(mIOBuffer, len[0], ts[0]);
                }
            } else if (TRAN_MODE == 1) {
                while (!t.isInterrupted()) {
                    readFrame(dis);
                }
            }
            dis.close();
            socket.close();
        } catch (IOException e) {
            Log.e("Stefan", "exception: " + e.toString());
            e.printStackTrace();
        } finally {
            Log.e("Stefan", "receiver stopped ");
            if (mServerSocket != null) {
                try {
                    mServerSocket.close();
                    mServerSocket = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public interface IReceiveCallback {
        void onDataUpdate(byte[] data, int len);
    }

    private IReceiveCallback iReceiveCallback;

    public void setReceiveCallback(IReceiveCallback callback) {
        this.iReceiveCallback = callback;
    }
}
