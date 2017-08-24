package com.ignition.finedust;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Created by W on 2017-06-20.
 */

public class BluetoothManager {
    public static final int REQUEST_BLUETOOTH_ENABLE = 2000;

    private final UUID UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private MainActivity mainActivity = null;

    private BroadcastReceiver broadcastReceiver = null;
    private BluetoothAdapter bluetoothAdapter = null;

    private ConnectThread connectThread = null;
    
    private BluetoothListener bluetoothListener = null;

    public BluetoothManager(MainActivity mainActivity) throws BluetoothException {
        this.mainActivity = mainActivity;

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            throw new BluetoothException("Cannot get BluetoothAdapter.");
        }
    }

    public boolean isEnabled() {
        return bluetoothAdapter.isEnabled();
    }

    public void enable() {
        mainActivity.iLog("Request on bluetooth.");
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        mainActivity.startActivityForResult(intent, BluetoothManager.REQUEST_BLUETOOTH_ENABLE);
    }

    public void startDiscovery() {
        if (broadcastReceiver != null) {
            mainActivity.unregisterReceiver(broadcastReceiver);
            broadcastReceiver = null;
        }

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    BluetoothDevice searchedDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    bluetoothListener.onDeviceFound(searchedDevice);
                    mainActivity.iLog("Found Device : " + searchedDevice.getName());
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
                    close();
                    bluetoothListener.onDeviceDisconnected();
                    mainActivity.iLog("Disconnected with device.");
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                    if (bluetoothListener != null)
                        bluetoothListener.onDiscoveryFinished();
                    mainActivity.iLog("Discovering was finished.");
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        mainActivity.registerReceiver(broadcastReceiver, intentFilter);
        
        bluetoothAdapter.startDiscovery();
        mainActivity.iLog("Started discovering.");
    }
    
    public void stopDiscovery() {
        bluetoothAdapter.cancelDiscovery();
        mainActivity.iLog("Canceled discovering.");
    }
    
    public void connect(BluetoothDevice device, boolean secure) {
        if (connectThread != null) {
            close();
        }
        
        connectThread = new ConnectThread(device, secure);
        connectThread.start();
        mainActivity.iLog("Try connecting.");
    }
    
    public void close() {
        if (connectThread != null) {
            connectThread.close();
            connectThread = null;
            mainActivity.iLog("Bluetooth closed.");
        }
    }
    
    public boolean isDiscovering() {
        return bluetoothAdapter.isDiscovering();
    }

    public void setBluetoothListener(BluetoothListener bluetoothListener) {
        this.bluetoothListener = bluetoothListener;
    }

    public class BluetoothException extends Exception {
        public BluetoothException(String str) {
            super(str);
            mainActivity.wLog(str);
        }
    }

    public interface BluetoothListener {
        public void onDeviceFound(BluetoothDevice device);
        public void onDiscoveryFinished();
        public void onDeviceConnected(BluetoothDevice device);
        public void onDataReceived(int data);
        public void onDeviceDisconnected();
    }
    
    private class ConnectThread extends Thread {
        private BluetoothDevice device = null;
        private BluetoothSocket socket = null;
        private InputStream inputStream = null;
        private int data = 0;
        
        public ConnectThread(BluetoothDevice device, boolean secure) {
            this.device = device;
            BluetoothSocket tmp = null;
            
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(UUID_SPP);
                    mainActivity.iLog("Get socket. type: secure");
                } else if (!secure) {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(UUID_SPP);
                    mainActivity.iLog("Get socket. type: insecure");
                }
            } catch (IOException e) {
                e.printStackTrace();
                mainActivity.wLog("IOException in BluetoothManager/ConnectThread/Constructor\n" + e.getMessage());
            }
            socket = tmp;
        }
        
        @Override
        public void run() {
            try {
                socket.connect();
                mainActivity.iLog("Connecting to socket.");
            } catch (IOException connectException) {
                mainActivity.wLog("IOException in BluetoothManager/ConnectThread/run try1\n" + connectException.getMessage());
                try {
                    socket.close();
                } catch (IOException closeException) {
                    mainActivity.wLog("IOException in BluetoothManager/ConnectThread/run try2\n" + closeException.getMessage());
                }
            }
            
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bluetoothListener.onDeviceConnected(device);
                }
            });
            
            mainActivity.iLog("Connected with " + device.getName());
            
            try {
                inputStream = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
                mainActivity.wLog("IOException in BluetoothManager/ConnectThread/run try3\n" + e.getMessage());
                return;
            }
            
            while (socket.isConnected()) {
                try {
                    data = inputStream.read();
                    Log.i("Receive", "" + data);
                    mainActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            bluetoothListener.onDataReceived(data);
                        }
                    });
                } catch (IOException e) {
                    mainActivity.wLog("IOException in BluetoothManager/ConnectThread/run try4\n" + e.getMessage());
                    e.printStackTrace();
                    break;
                }
            }
        }
        
        public void close() {
            try {
                socket.close();
            } catch (IOException closeException) {
                mainActivity.wLog("IOException in BluetoothManager/ConnectThread/close\n" + closeException.getMessage());}
            
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bluetoothListener.onDeviceDisconnected();
                }
            });
        }
    }
}
