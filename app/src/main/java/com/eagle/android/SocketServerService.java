package com.eagle.android;
import android.app.IntentService;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketServerService extends IntentService {

    private PrintWriter out = null;

    public SocketServerService(){
        super("socketService");
    }
    public SocketServerService(String name) {
        super(name);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter("ACTION_RESULT");
        ActionResultReceiver actionResultReceiver = new ActionResultReceiver(this);
        registerReceiver(actionResultReceiver, filter,RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        new Thread(()->{
            try {
                ServerSocket serverSocket = new ServerSocket(9090);
                System.out.println("Server started, waiting for clients...");

                while (true) {
                    Socket clientSocket = null;
                    BufferedReader in = null;
                    try{
                        // 等待客户端连接
                        clientSocket = serverSocket.accept();

                        // 创建输入流和输出流
                        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
//                        out = new PrintWriter(clientSocket.getOutputStream(), true);
                        out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"), true);


                        // 进入循环，保持长连接状态
                        while (true) {
                            // 接收客户端消息
                            String message = in.readLine();
                            if (message == null) {
                                // 如果客户端关闭连接，则退出循环等待新的连接
                                break;
                            }
                            Log.i("socket", message);

                            // 解析消息并处理
                            JSONObject jsonObject = new JSONObject(message);
                            String action = jsonObject.getString("action");
                            Log.i("socket", jsonObject.getString("action"));

                            // 创建广播意图并发送
                            Intent msg = new Intent("FIND_ALL_FOCUS_INFO");
                            msg.putExtra("action", jsonObject.getString("action"));
                            msg.putExtra("extra", jsonObject.getString("extra"));
                            sendBroadcast(msg);
                        }


                    }catch (Exception e){
                        // 关闭流和连接
                        in.close();
                        out.close();
                        clientSocket.close();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }).start();

//        while (true){
//            Log.i("service",">>>>>>>>.start service");
//            try {
//                Thread.sleep(2_000);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        }
    }

    public void sendRes2Client(JSONObject res){
        if(res != null){
            Log.i("res",res.toString());

            // 使用 AsyncTask 进行网络请求
            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... params) {
                    if(out == null || res == null){
                        return "";
                    }
                    out.println(res.toString());
                    Log.i("res",res.toString());
                    out.flush();
                    return "";
                }

                @Override
                protected void onPostExecute(String result) {
                    // 在这里更新UI
                }
            }.execute();

        }
    }

}
