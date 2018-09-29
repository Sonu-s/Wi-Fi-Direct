package com.example.user.wifidirect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Button onOffButton;
    private Button discoverButton;
    private Button sendButton;
    private ListView peerListView;
    private TextView redMsg;
    public static TextView connectionStatus;
    private EditText writeMsg;

    private WifiManager wifiManager;

    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private BroadcastReceiver mReceiver;
    private IntentFilter mFilter;

    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    String[] deviceNameArray; // show divice name in list
    WifiP2pDevice[] deviceArray; // For connecting Device..

    private ArrayAdapter<String> adapter;

    static final int READ_MSG = 1;

    private ServerClass serverClass;
    private ClientClass clientClass;
    private SendReceiveClass sendReceiveClass;
    private Animation animation;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        onOffButton = findViewById(R.id.onOffButton);
        discoverButton = findViewById(R.id.discoverButton);
        sendButton = findViewById(R.id.sendButton);
        peerListView = findViewById(R.id.peerListView);
        redMsg = findViewById(R.id.readMsg);
        connectionStatus = findViewById(R.id.connectionStatus);
        writeMsg = findViewById(R.id.writeMsg);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (wifiManager.isWifiEnabled()) {

            onOffButton.setText("Wifi off");
        } else {

            onOffButton.setText("Wifi on");
        }

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);

        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        mFilter = new IntentFilter();


        mFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);


        peerListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {


                final WifiP2pDevice device = deviceArray[position];
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {

                        Toast.makeText(MainActivity.this, "Connected to" + device.deviceName, Toast.LENGTH_SHORT).show();

                    }

                    @Override
                    public void onFailure(int reason) {

                        Toast.makeText(MainActivity.this, "Not Connected", Toast.LENGTH_SHORT).show();
                    }
                });

            }
        });


    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {

            switch (msg.what){

                case READ_MSG:
                    byte [] readBuffer = (byte[])msg.obj;
                    String tempMsg = new String(readBuffer, 0, msg.arg1);
                    redMsg.setText(tempMsg);
                    break;
            }
            return true;
        }
    });

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {

            peers.clear();
            if (!peerList.getDeviceList().equals(peers)) {

                peers.clear();
                peers.addAll(peerList.getDeviceList());

                deviceNameArray = new String[peerList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[peerList.getDeviceList().size()];
                int index = 0;

                for (WifiP2pDevice device : peerList.getDeviceList()) {

                    deviceNameArray[index] = device.deviceName;
                    deviceArray[index] = device;
                    index++;
                }
                adapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, deviceNameArray);
                adapter.notifyDataSetChanged();
                peerListView.setAdapter(adapter);

            }

            if (peers.size() == 0) {

                Toast.makeText(MainActivity.this, "No device Found!", Toast.LENGTH_SHORT).show();
                return;
            }

        }
    };

    public void onOffWifi(View view) {

        animation = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.animation);
        onOffButton.startAnimation(animation);

        if (wifiManager.isWifiEnabled()) {

            wifiManager.setWifiEnabled(false);
            onOffButton.setText("Wifi on");

        } else {

            wifiManager.setWifiEnabled(true);
            onOffButton.setText("Wifi off");
        }


    }

    public void discover(View view) {

        animation = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.animation);
        discoverButton.startAnimation(animation);

        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {

                connectionStatus.setText("Discovery Started.");

            }

            @Override
            public void onFailure(int reason) {

                connectionStatus.setText("Discovery Starting failed");
            }
        });

    }

    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {

           final InetAddress groupOwnerAddress = info.groupOwnerAddress;
           if (info.groupFormed && info.isGroupOwner){

               connectionStatus.setText("Host");
               serverClass = new ServerClass();
               serverClass.start();

           }else if (info.groupFormed){

               connectionStatus.setText("Client");
               clientClass = new ClientClass(groupOwnerAddress);
               clientClass.start();
           }

        }
    };

    public void send(View view) {

        String msg = writeMsg.getText().toString().trim();
        sendReceiveClass.write(msg.getBytes());

    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    public class ServerClass extends Thread{

        Socket socket;
        ServerSocket serverSocket;

        @Override
        public void run() {
            try {

                serverSocket = new ServerSocket(88888);
                socket = serverSocket.accept();
                sendReceiveClass = new SendReceiveClass(socket);
                sendReceiveClass.start();

            } catch (IOException e) {

                e.printStackTrace();
            }
        }
    }

    public class SendReceiveClass extends Thread{

        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public  SendReceiveClass(Socket stk){

            socket = stk;

            try {

                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {

            byte [] readBuffer = new byte[1024];
            int bytes;

            while (socket != null){

                try {

                    bytes = inputStream.read(readBuffer);

                    if (bytes > 0){

                        handler.obtainMessage(READ_MSG, bytes, -1, readBuffer).sendToTarget();
                    }
                } catch (IOException e) {

                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes){

            try {
                outputStream.write(bytes);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class ClientClass extends Thread{

        Socket socket;
        String hostAdd;

        public ClientClass(InetAddress hostAddress){

            hostAdd = hostAddress.getHostAddress();
            socket = new Socket();
        }
        @Override
        public void run() {
            try {

                socket.connect(new InetSocketAddress(hostAdd,88888),500);
                sendReceiveClass = new SendReceiveClass(socket);
                sendReceiveClass.start();

            } catch (IOException e) {

                e.printStackTrace();
            }
        }
    }
}