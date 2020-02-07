package com.absinthe.kage.connect.tcp;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.absinthe.kage.protocol.IProtocolHandler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

public class KageSocket {
    private static final String TAG = KageSocket.class.getSimpleName();
    private Socket mSocket;
    private ISocketCallback mSocketCallback;
    private DataInputStream mIn;
    private DataOutputStream mOut;
    private ConnectThread mConnectThread;
    private final byte[] LOCK = new byte[0];
    private IPacketWriter mPacketWriter;
    private IPacketReader mPacketReader;

    public void connect(final String ip, final int port, int timeout) {
        synchronized (LOCK) {
            if (mSocket == null) {
                mConnectThread = new ConnectThread(ip, port, timeout);
                mConnectThread.start();
            } else {
                Log.e(TAG, "socket already exist");
            }
        }
    }

    private class ConnectThread extends Thread {

        private String ip;
        private int port;
        private int timeout;

        public ConnectThread(String ip, int port, int timeout) {
            this.ip = ip;
            this.port = port;
            this.timeout = timeout;
        }

        @Override
        public void run() {
            synchronized (LOCK) {
                if (mSocket == null) {
                    try {
                        mSocket = new Socket();
                        SocketAddress endPoint = new InetSocketAddress(ip, port);
                        mSocket.connect(endPoint, timeout);
                        mSocket.setKeepAlive(true);
                        mIn = new DataInputStream(mSocket.getInputStream());
                        mOut = new DataOutputStream(mSocket.getOutputStream());
                        mPacketWriter = new PacketWriter(mOut, mSocketCallback);
                        mPacketReader = new PacketReader(mIn, mSocketCallback);
                        ISocketCallback.TCastSocketCallbackThreadHandler.getInstance().post(new Runnable() {
                            @Override
                            public void run() {
                                if (mSocketCallback != null) {
                                    mSocketCallback.onConnected();
                                }
                            }
                        });
                    } catch (final Exception e) {
                        Log.i(TAG, "socket connect IOException=" + e);
                        mSocket = null;
                        if (e instanceof SocketTimeoutException) {
                            ISocketCallback.TCastSocketCallbackThreadHandler.getInstance().post(new Runnable() {
                                @Override
                                public void run() {
                                    if (mSocketCallback != null) {
                                        mSocketCallback.onConnectError(ISocketCallback.CONNECT_ERROR_CODE_CONNECT_TIMEOUT, e);
                                    }
                                }
                            });
                        } else if (e instanceof ConnectException) {
                            ISocketCallback.TCastSocketCallbackThreadHandler.getInstance().post(new Runnable() {
                                @Override
                                public void run() {
                                    if (mSocketCallback != null) {
                                        mSocketCallback.onConnectError(ISocketCallback.CONNECT_ERROR_CODE_CONNECT_IP_OR_PORT_UNREACHABLE, e);
                                    }
                                }
                            });
                        } else {
                            ISocketCallback.TCastSocketCallbackThreadHandler.getInstance().post(new Runnable() {
                                @Override
                                public void run() {
                                    if (mSocketCallback != null) {
                                        mSocketCallback.onConnectError(ISocketCallback.CONNECT_ERROR_CODE_CONNECT_UNKNOWN, e);
                                    }
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    public boolean disConnect() {
        synchronized (LOCK) {
            if (mSocket != null && mSocket.isConnected()) {
                try {
                    mIn.close();
                    mOut.close();
                    mSocket.close();
                    mIn = null;
                    mOut = null;
                    mSocket = null;
                    if (null != mPacketWriter) {
                        mPacketWriter.shutdown();
                        mPacketWriter = null;
                    }
                    if (null != mPacketReader) {
                        mPacketReader.shutdown();
                        mPacketReader = null;
                    }
                    ISocketCallback.TCastSocketCallbackThreadHandler.getInstance().post(new Runnable() {
                        @Override
                        public void run() {
                            if (mSocketCallback != null) {
                                mSocketCallback.onDisConnected();
                            }
                        }
                    });
                    return true;
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                    return false;
                }
            }
            return false;
        }
    }

    public void send(final Packet packet) {
        synchronized (LOCK) {
            if (mPacketWriter != null) {
                if (packet instanceof Request) {
                    Request request = (Request) packet;
                    request.setId(System.currentTimeMillis() + "");
                    if (null != mPacketReader) {
                        mPacketReader.addRequest(request);
                    }
                }
                mPacketWriter.writePacket(packet);
            } else {
                Log.e(TAG, "send error:" + "mPacketWriter == null");
            }
        }
    }

    public void setSocketCallback(ISocketCallback socketCallback) {
        this.mSocketCallback = socketCallback;
    }

    public interface ISocketCallback {
        int CONNECT_ERROR_CODE_CONNECT_UNKNOWN = 1;
        int CONNECT_ERROR_CODE_CONNECT_IP_OR_PORT_UNREACHABLE = 2;
        int CONNECT_ERROR_CODE_CONNECT_TIMEOUT = 3;
        int CONNECT_ERROR_CODE_HAND_SHAKE_UNCOMPLETE = 4;
        int READ_ERROR_CODE_CONNECT_UNKNOWN = 101;
        int WRITE_ERROR_CODE_CONNECT_UNKNOWN = 102;
        int READ_ERROR_CODE_RECEIVE_LENGTH_TOO_BIG = 103;

        void onConnected();

        void onDisConnected();

        void onReceiveMsg(String msg);

        void onConnectError(int errorCode, Exception e);

        void onReadAndWriteError(int errorCode);

        void onWriterIdle();

        void onReaderIdle();

        class TCastSocketCallbackThreadHandler extends Handler {
            private static HandlerThread mHandlerThread;
            private static IProtocolHandler.KageProtocolThreadHandler mHandler;

            public TCastSocketCallbackThreadHandler(Looper looper) {
                super(looper);
            }

            public static IProtocolHandler.KageProtocolThreadHandler getInstance() {
                if (null == mHandler) {
                    synchronized (IProtocolHandler.KageProtocolThreadHandler.class) {
                        if (null == mHandler) {
                            if (null == mHandlerThread) {
                                mHandlerThread = new HandlerThread("KageSocketCallbackThreadHandler");
                                mHandlerThread.start();
                            }
                            mHandler = new IProtocolHandler.KageProtocolThreadHandler(mHandlerThread.getLooper());
                        }
                    }
                }
                return mHandler;
            }
        }
    }

    private boolean isConnected() {
        return null != mSocket && null != mIn && null != mOut && null != mPacketWriter && null != mPacketReader;
    }
}
