package com.absinthe.kage.server;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.absinthe.kage.protocol.BaseProtocol;
import com.absinthe.kage.protocol.DataAckProtocol;
import com.absinthe.kage.protocol.DataProtocol;
import com.absinthe.kage.protocol.PingAckProtocol;
import com.absinthe.kage.protocol.PingProtocol;
import com.absinthe.kage.utils.SocketUtil;
import com.absinthe.kage.utils.ToastUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ServerResponseTask implements Runnable {

    private static final String TAG = ServerResponseTask.class.getSimpleName();
    private ReceiveTask receiveTask;
    private SendTask sendTask;
    private Socket socket;
    private IResponseCallback tBack;

    private volatile ConcurrentLinkedQueue<BaseProtocol> dataQueue = new ConcurrentLinkedQueue<>();
    private static ConcurrentHashMap<String, Socket> onLineClient = new ConcurrentHashMap<>();

    private String userIP;

    public String getUserIP() {
        return userIP;
    }

    public ServerResponseTask(Socket socket, IResponseCallback tBack) {
        this.socket = socket;
        this.tBack = tBack;
        this.userIP = socket.getInetAddress().getHostAddress();
        Log.d(TAG, "用户IP地址：" + userIP);
    }

    @Override
    public void run() {
        try {
            //开启接收线程
            receiveTask = new ReceiveTask();
            receiveTask.inputStream = new DataInputStream(socket.getInputStream());
            receiveTask.start();

            //开启发送线程
            sendTask = new SendTask();
            sendTask.outputStream = new DataOutputStream(socket.getOutputStream());
            sendTask.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (receiveTask != null) {
            receiveTask.isCancel = true;
            receiveTask.interrupt();
            if (receiveTask.inputStream != null) {
                SocketUtil.closeInputStream(receiveTask.inputStream);
                receiveTask.inputStream = null;
            }
            receiveTask = null;
        }

        if (sendTask != null) {
            sendTask.isCancel = true;
            sendTask.interrupt();
            if (sendTask.outputStream != null) {
                synchronized (sendTask.outputStream) {//防止写数据时停止，写完再停
                    sendTask.outputStream = null;
                }
            }
            sendTask = null;
        }
    }

    public void addMessage(BaseProtocol data) {
        if (!isConnected()) {
            return;
        }

        dataQueue.offer(data);
        toNotifyAll(dataQueue);//有新增待发送数据，则唤醒发送线程
    }

    public Socket getConnectedClient(String clientID) {
        return onLineClient.get(clientID);
    }

    /**
     * 打印已经链接的客户端
     */
    public static void printAllClient() {
        if (onLineClient == null) {
            return;
        }
        for (String s : onLineClient.keySet()) {
            Log.d(TAG, "client:" + s);
        }
    }

    public void toWaitAll(Object o) {
        synchronized (o) {
            try {
                o.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void toNotifyAll(Object obj) {
        synchronized (obj) {
            obj.notifyAll();
        }
    }

    private boolean isConnected() {
        if (socket.isClosed() || !socket.isConnected()) {
            onLineClient.remove(userIP);
            ServerResponseTask.this.stop();
            Log.d(TAG, "socket closed...");
            return false;
        }
        return true;
    }

    public class ReceiveTask extends Thread {

        private DataInputStream inputStream;
        private boolean isCancel;

        @Override
        public void run() {
            while (!isCancel) {
                if (!isConnected()) {
                    isCancel = true;
                    break;
                }

                BaseProtocol clientData = SocketUtil.readFromStream(inputStream);

                if (clientData != null) {
                    if (clientData.getProtocolType() == DataProtocol.PROTOCOL_TYPE) {
                        Log.d(TAG, "dtype: " + ((DataProtocol) clientData).getDtype() + ", pattion: " + ((DataProtocol) clientData).getPattion() + ", msgId: " + ((DataProtocol) clientData).getMsgId() + ", data: " + ((DataProtocol) clientData).getData());

                        DataAckProtocol dataAck = new DataAckProtocol();
                        dataAck.setUnused("收到消息：" + ((DataProtocol) clientData).getData());
                        dataQueue.offer(dataAck);
                        toNotifyAll(dataQueue); //唤醒发送线程

                        tBack.targetIsOnline(userIP);
                        new Handler(Looper.getMainLooper()).post(() ->
                                ToastUtil.makeText("收到消息：" + ((DataProtocol) clientData).getData()));
                    } else if (clientData.getProtocolType() == PingProtocol.PROTOCOL_TYPE) {
                        Log.d(TAG, "pingId: " + ((PingProtocol) clientData).getPingId());

                        PingAckProtocol pingAck = new PingAckProtocol();
                        pingAck.setUnused("收到心跳");
                        dataQueue.offer(pingAck);
                        toNotifyAll(dataQueue); //唤醒发送线程

                        tBack.targetIsOnline(userIP);
                    }
                } else {
                    Log.d(TAG, "client is offline...");
                    break;
                }
            }

            SocketUtil.closeInputStream(inputStream);
        }
    }

    public class SendTask extends Thread {

        private DataOutputStream outputStream;
        private boolean isCancel;

        @Override
        public void run() {
            while (!isCancel) {
                if (!isConnected()) {
                    isCancel = true;
                    break;
                }

                BaseProtocol protocol = dataQueue.poll();
                if (protocol == null) {
                    toWaitAll(dataQueue);
                } else if (outputStream != null) {
                    synchronized (outputStream) {
                        SocketUtil.write2Stream(protocol, outputStream);
                    }
                }
            }

            SocketUtil.closeOutputStream(outputStream);
        }
    }
}