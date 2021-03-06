package org.hawkdev.apps.homebot;

import android.*;
import android.Manifest;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;


//import com.physicaloid.lib.Physicaloid;
//import com.physicaloid.lib.usb.driver.uart.ReadLisener;

import com.crashlytics.android.Crashlytics;
import com.genband.kandy.api.Kandy;
import com.genband.kandy.api.access.KandyConnectServiceNotificationListener;
import com.genband.kandy.api.access.KandyConnectionState;
import com.genband.kandy.api.access.KandyLoginResponseListener;
import com.genband.kandy.api.access.KandyLogoutResponseListener;
import com.genband.kandy.api.access.KandyRegistrationState;
import com.genband.kandy.api.services.calls.IKandyCall;
import com.genband.kandy.api.services.calls.IKandyIncomingCall;
import com.genband.kandy.api.services.calls.IKandyOutgoingCall;
import com.genband.kandy.api.services.calls.KandyCallResponseListener;
import com.genband.kandy.api.services.calls.KandyCallServiceNotificationListener;
import com.genband.kandy.api.services.calls.KandyCallState;
import com.genband.kandy.api.services.calls.KandyOutgingVoipCallOptions;
import com.genband.kandy.api.services.calls.KandyRecord;
import com.genband.kandy.api.services.calls.KandyView;
import com.genband.kandy.api.services.common.KandyMissedCallMessage;
import com.genband.kandy.api.services.common.KandyWaitingVoiceMailMessage;
import com.genband.kandy.api.utils.KandyIllegalArgumentException;
import com.genband.kandy.api.utils.KandyLog;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import io.fabric.sdk.android.Fabric;

public class MainActivity extends AppCompatActivity {
    //TODO add kandy api key and api secret here
    private static final String KANDY_API_KEY = "-----";
    private static final String KANDY_API_SECRET = "-----";

    //TODO add username and password for robot here
    private static final java.lang.String HOMEBOT_ROBOT_NAME = "-----";
    private static final String HOMEBOT_ROBOT_PASS = "------";

    //TODO add username for controlling app/user here
    private static final java.lang.String HOMEBOT_USER_NAME = "-----";



    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 123;
    private static final Integer CMD_OPEN_USB = 500;

    private static int TIMEOUT = 0;
    private boolean forceClaim = true;
    private static final String TAG = "";

    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";

    UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbInterface usbInterface;

    private UsbManager mManager;
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication
//                            Log.d(TAG, "Device: " + device.getDeviceName());

                            usbInterface = device.getInterface(0);
                            connection = mManager.openDevice(device);
                            connection.claimInterface(usbInterface, forceClaim);

                            Toast.makeText(MainActivity.this, "Connected to device", Toast.LENGTH_SHORT).show();

                        }
                    }
                    else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };
    private PendingIntent mPermissionIntent;
    private UsbSerialDriver driver;
    private KandyView localVideoView;
    private KandyView remoteVideoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_main);


            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("command");
        ref.setValue(0);
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Integer command = dataSnapshot.getValue(Integer.class);

                if (command == CMD_OPEN_USB){
                    onClickOpen();
                }

                sendByte((byte) command.intValue());
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });



        localVideoView = (KandyView)findViewById (R.id.kandy_local_video_view);
        remoteVideoView = (KandyView)findViewById (R.id.kandy_remote_video_view);

        initializeKandy();


        //initialize robot
        mManager = (UsbManager) getSystemService(Context.USB_SERVICE);

//        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
//        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
//        registerReceiver(mUsbReceiver, filter);

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(mManager);
        if (availableDrivers.isEmpty()) {
            return;
        }

// Open a connection to the first available driver.
        driver = availableDrivers.get(0);

    }


    public void onClickOpen(View v) {

        onClickOpen();

    }

    public void onClickOpen() {

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(mManager);
        if (availableDrivers.isEmpty()) {
            return;
        }

        // Open a connection to the first available driver.
        driver = availableDrivers.get(0);

    }

    public void onClickClose(View v) {

//        if(connection != null && usbInterface != null){
//            connection.releaseInterface(usbInterface);
//            connection.close();
//        }

    }

    public void onClickSwitch(View view) {
        switch (view.getId()) {
            case R.id.bt_stop:
                sendByte((byte)0);
                break;
            case R.id.bt_forward:
                sendByte((byte)1);
                break;
            case R.id.bt_backward:
                sendByte((byte)2);
                break;
            case R.id.bt_left:
                sendByte((byte)3);
                break;
            case R.id.bt_right:
                sendByte((byte)4);
                break;
        }

    }

    private void sendByte(byte b) {
        if(driver == null)
            return;

        connection = mManager.openDevice(driver.getDevice());
        if (connection == null) {
            // You probably need to call UsbManager.requestPermission(driver.getDevice(), ..)
            return;
        }

        // Read some data! Most have just one port (port 0).
        UsbSerialPort port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            byte buffer[] = new byte[1];
            buffer[0] = b;
            port.write(buffer, 1000);
            Log.d(TAG, "Written 1");
        } catch (IOException e) {
            e.printStackTrace();
            // Deal with error.
        } finally {
            try {
                port.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



    private void initializeKandy() {
        //code for requesting permissions
//        ActivityCompat.requestPermissions(this,
//                new String[]{android.Manifest.permission.READ_PHONE_STATE,
//                        Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
//                MY_PERMISSIONS_REQUEST_READ_CONTACTS);

        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {


            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_READ_CONTACTS);

            // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
            // app-defined int constant. The callback method gets the
            // result of the request.
        }
        else{
            Kandy.initialize(getApplicationContext(), KANDY_API_KEY, KANDY_API_SECRET);
            Kandy.getKandyLog().setLogLevel(KandyLog.Level.VERBOSE);

            registerKandyNotificationListener();

            loginHomeBotUser();

            registerCallNottificaitons();
        }
    }

    private void registerCallNottificaitons() {
        Kandy.getServices().getCallService().registerNotificationListener(new KandyCallServiceNotificationListener() {
            @Override
            public void onIncomingCall(IKandyIncomingCall iKandyIncomingCall) {
                iKandyIncomingCall.setLocalVideoView(localVideoView);
                iKandyIncomingCall.setRemoteVideoView(remoteVideoView);
                iKandyIncomingCall.accept(true, new KandyCallResponseListener() {
                    @Override
                    public void onRequestSucceeded(IKandyCall iKandyCall) {
                        Toast.makeText(MainActivity.this, "Call Accepted", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onRequestFailed(IKandyCall iKandyCall, int i, String s) {

                    }
                });
            }

            @Override
            public void onMissedCall(KandyMissedCallMessage kandyMissedCallMessage) {

            }

            @Override
            public void onWaitingVoiceMailCall(KandyWaitingVoiceMailMessage kandyWaitingVoiceMailMessage) {

            }

            @Override
            public void onCallStateChanged(KandyCallState kandyCallState, IKandyCall iKandyCall) {

            }

            @Override
            public void onVideoStateChanged(IKandyCall iKandyCall, boolean b, boolean b1) {

            }

            @Override
            public void onGSMCallIncoming(IKandyCall iKandyCall, String s) {

            }

            @Override
            public void onGSMCallConnected(IKandyCall iKandyCall, String s) {

            }

            @Override
            public void onGSMCallDisconnected(IKandyCall iKandyCall, String s) {

            }
        });
    }

    private void loginHomeBotUser() {
        KandyRecord kandyRecord = null;
        try {
            kandyRecord = new KandyRecord(HOMEBOT_ROBOT_NAME);
        } catch (KandyIllegalArgumentException e) {
            //TODO insert your code here
            return;
        }
        String password = HOMEBOT_ROBOT_PASS;


        //show progress dialog
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Logging in to kandy");
        progressDialog.show();

        Kandy.getAccess().login(kandyRecord, password, new KandyLoginResponseListener() {

            @Override
            public void onRequestFailed(int responseCode, String err) {
                //TODO insert your code here
                progressDialog.dismiss();
                Toast.makeText(MainActivity.this, "Failed to login. " + err, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onLoginSucceeded() {
                //TODO insert your code here
                progressDialog.dismiss();
                Toast.makeText(MainActivity.this, "Successfully Logged in.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void logoutHomeBotUser(){
        Kandy.getAccess().logout(new KandyLogoutResponseListener() {

            @Override
            public void onRequestFailed(int responseCode, String err) {
                //TODO insert your code here
                Toast.makeText(MainActivity.this, "Failed to logout. " + err, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onLogoutSucceeded() {
                //TODO insert your code here
                Toast.makeText(MainActivity.this, "Successfully Logged out.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onVideoCallClick(View view) {

        KandyRecord caller = null;
        KandyRecord callee = null;
        try {
            callee = new KandyRecord(HOMEBOT_USER_NAME);
        } catch (KandyIllegalArgumentException e) {
            //TODO insert your code here
            return;
        }

        IKandyOutgoingCall currentCall = Kandy.getServices().getCallService().createVoipCall(caller,
                callee, KandyOutgingVoipCallOptions.START_CALL_WITH_VIDEO);

//YOU MUST TO PASS THE NON NULL VALUE OF LOCAL VIEW TO THE KandyOutgoingCall or/and KandyIncomingCall
        currentCall.setLocalVideoView(localVideoView);
//YOU MUST TO PASS THE NON NULL VALUE OF REMOTE VIEW TO THE KandyOutgoingCall or/and KandyIncomingCall
        currentCall.setRemoteVideoView(remoteVideoView);
        currentCall.establish(new KandyCallResponseListener() {

            @Override
            public void onRequestSucceeded(IKandyCall call) {
                //TODO insert your code here
            }

            @Override
            public void onRequestFailed(IKandyCall call, int arg1, String arg2) {
                //TODO insert your code here
            }
        });
    }

    private void registerKandyNotificationListener() {
        Kandy.getAccess().registerNotificationListener(new KandyConnectServiceNotificationListener() {
            @Override
            public void onRegistrationStateChanged(KandyRegistrationState kandyRegistrationState) {

            }

            @Override
            public void onConnectionStateChanged(KandyConnectionState kandyConnectionState) {

            }

            @Override
            public void onInvalidUser(String s) {

            }

            @Override
            public void onSessionExpired(String s) {

            }

            @Override
            public void onSDKNotSupported(String s) {

            }

            @Override
            public void onCertificateError(String s) {

            }

            @Override
            public void onServerConfigurationReceived(JSONObject jsonObject) {

            }
        });

    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_CONTACTS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    initializeKandy();

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logoutHomeBotUser();
    }


}
