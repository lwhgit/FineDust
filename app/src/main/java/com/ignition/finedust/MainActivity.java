package com.ignition.finedust;

import android.Manifest;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    public static final int PERMISSION_REQUEST = 1234;

    private Toolbar toolbar = null;

    private BluetoothManager bluetoothManager = null;
    
    private TextView dataView = null;
    private TextView stateView = null;
    private TextView deviceView = null;

    private AlertDialog bluetoothDialog = null;
    private View bluetoothView = null;
    private Switch bluetoothSwh = null;
    private ImageButton searchBtn = null;
    private ProgressBar searchPrg = null;
    private TextView nothingView = null;
    private ListView deviceListView= null;
    
    private ArrayList<BluetoothDevice> deviceList = null;
    private DeviceArrayAdapter deviceArrayAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        try {
            bluetoothManager = new BluetoothManager(this);
        } catch (BluetoothManager.BluetoothException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }

        demandPermission();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST) {
            if (!(grantResults.length == 1 &&grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "권한이 거부되었습니다.", Toast.LENGTH_SHORT).show();
            } else {
                init();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BluetoothManager.REQUEST_BLUETOOTH_ENABLE) {
            Toast.makeText(this, "Request Enable.", Toast.LENGTH_SHORT).show();

            if (bluetoothManager.isEnabled()) {
                bluetoothSwh.setVisibility(View.INVISIBLE);
                searchBtn.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add("Bluetooth");
        menu.add("Disconnect");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getTitle().equals("Bluetooth")) {
            // Toast.makeText(this, "Open Bluetooth Dialog.", Toast.LENGTH_SHORT).show();
            openBluetooth();
        } else if (item.getTitle().equals("Disconnect")) {
            bluetoothManager.close();
        }
        return true;
    }
    
    @Override
    protected void onDestroy() {
        bluetoothManager.close();
        super.onDestroy();
    }
    
    private void demandPermission() {
        if(Build.VERSION.SDK_INT >= 23) {
            if(!(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST);
            } else {
                init();
            }
        } else {
            init();
        }
    }

    private void init() {
        dataView = (TextView) findViewById(R.id.dataView);
        stateView = (TextView) findViewById(R.id.stateView);
        deviceView = (TextView) findViewById(R.id.deviceView);
    }

    private void openBluetooth() {
        if (bluetoothDialog == null) {
            bluetoothView = ((LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.dialog_bluetooth, null);

            bluetoothSwh = (Switch) bluetoothView.findViewById(R.id.bluetoothSwh);
            bluetoothSwh.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        // Log.i("FindDust", "On Bluetooth");
                        bluetoothManager.enable();
                    }
                }
            });

            searchBtn = (ImageButton) bluetoothView.findViewById(R.id.searchBtn);
            searchBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchBtn.setVisibility(View.INVISIBLE);
                    searchPrg.setVisibility(View.VISIBLE);
                    bluetoothManager.startDiscovery();
                    stateView.setText("State : Searching devices.");
                }
            });

            searchPrg = (ProgressBar) bluetoothView.findViewById(R.id.searchPrg);
            
            nothingView = (TextView) bluetoothView.findViewById(R.id.nothingView);
            deviceListView = (ListView) bluetoothView.findViewById(R.id.deviceListView);
            deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    // Toast.makeText(MainActivity.this, "" + position, Toast.LENGTH_SHORT).show();
                    Toast.makeText(MainActivity.this, "연결 시도", Toast.LENGTH_SHORT).show();
                    bluetoothManager.stopDiscovery();
                    bluetoothManager.connect(deviceList.get(position));
                }
            });
            
            deviceList = new ArrayList<>();
            deviceArrayAdapter = new DeviceArrayAdapter(this, deviceList);
            
            deviceListView.setAdapter(deviceArrayAdapter);
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(bluetoothView);

            bluetoothDialog = builder.create();
            
            bluetoothManager.setBluetoothListener(new BluetoothManager.BluetoothListener() {
                @Override
                public void onDeviceFound(BluetoothDevice device) {
                    // Log.i("DeviceFound", "Name : " + device.getName() + "\tAddress : " + device.getAddress());
                    // Toast.makeText(MainActivity.this, "Name : " + device.getName() + "\tAddress : " + device.getAddress(), Toast.LENGTH_SHORT).show();
    
                    nothingView.setVisibility(View.INVISIBLE);
                    deviceListView.setVisibility(View.VISIBLE);
                    
                    if (deviceList.indexOf(device) == -1) {
                        deviceList.add(device);
                        deviceArrayAdapter.notifyDataSetChanged();
                    }
                }
    
                @Override
                public void onDiscoveryFinished() {
                    searchBtn.setVisibility(View.VISIBLE);
                    searchPrg.setVisibility(View.INVISIBLE);
                    stateView.setText("State : Watiing for connection.");
                }
    
                @Override
                public void onDeviceConnected(BluetoothDevice device) {
                    dataView.setText("00.00");
                    stateView.setText("State : Device connected.");
                    deviceView.setText("Device : " + device.getName());
                    
                    bluetoothDialog.hide();
                }
    
                @Override
                public void onDataReceived(int data) {
                    dataView.setText("" + data);
                    stateView.setText("State : Data receiving.");
                }
    
                @Override
                public void onDeviceDisconnected() {
                    dataView.setText("Disconnected");
                    deviceView.setText("Device : undefined.");
                }
            });
        }

        if (bluetoothManager.isEnabled()) {
            bluetoothSwh.setVisibility(View.INVISIBLE);
            searchBtn.setVisibility(View.VISIBLE);
            
            if (bluetoothManager.isDiscovering()) {
                searchBtn.setVisibility(View.INVISIBLE);
                searchPrg.setVisibility(View.VISIBLE);
            } else {
                searchBtn.setVisibility(View.VISIBLE);
                searchPrg.setVisibility(View.INVISIBLE);
            }
        }

        bluetoothDialog.show();
    }
    
    private class DeviceArrayAdapter extends ArrayAdapter<BluetoothDevice> {
        private LayoutInflater li = null;
        private ArrayList<BluetoothDevice> list = null;
        
        public DeviceArrayAdapter(Context context, ArrayList<BluetoothDevice> items) {
            super(context, 0, items);
            li = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            list = items;
        }
    
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            BluetoothDevice device = list.get(position);
            
            convertView = li.inflate(R.layout.list_device, null);
            TextView deviceNameView = (TextView) convertView.findViewById(R.id.deviceNameView);
            deviceNameView.setText(device.getName());
            
            return convertView;
        }
    }
    
    /*
    
    private String getBTMajorDeviceClass(int major){
        switch(major){
        case BluetoothClass.Device.Major.AUDIO_VIDEO: 	return "AUDIO_VIDEO";
        case BluetoothClass.Device.Major.COMPUTER: 		return "COMPUTER";
        case BluetoothClass.Device.Major.HEALTH:			return "HEALTH";
        case BluetoothClass.Device.Major.IMAGING:			return "IMAGING";
        case BluetoothClass.Device.Major.MISC:			return "MISC";
        case BluetoothClass.Device.Major.NETWORKING:		return "NETWORKING";
        case BluetoothClass.Device.Major.PERIPHERAL:		return "PERIPHERAL";
        case BluetoothClass.Device.Major.PHONE:			return "PHONE";
        case BluetoothClass.Device.Major.TOY:				return "TOY";
        case BluetoothClass.Device.Major.UNCATEGORIZED:	return "UNCATEGORIZED";
        case BluetoothClass.Device.Major.WEARABLE:		return "AUDIO_VIDEO2";
        default: 							return "unknown!";
        }
    }*/
}
