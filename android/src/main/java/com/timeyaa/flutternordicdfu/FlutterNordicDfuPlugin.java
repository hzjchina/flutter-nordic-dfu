package com.timeyaa.flutternordicdfu;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter;
import no.nordicsemi.android.dfu.DfuServiceController;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;

public class FlutterNordicDfuPlugin implements MethodCallHandler {

    private String TAG = "FlutterNordicDfuPlugin";

    private String NAMESPACE = "com.timeyaa.flutter_nordic_dfu";

    /**
     * hold context
     */
    private Context mContext;

    /**
     * hold Registrar
     */
    private Registrar registrar;

    /**
     * hold result
     */
    private Result pendingResult;

    /**
     * Method Channel
     */
    private MethodChannel channel;

    private DfuServiceController controller;

    private boolean hasCreateNotification = false;

    private FlutterNordicDfuPlugin(Registrar registrar) {
        this.mContext = registrar.context();
        this.channel = new MethodChannel(registrar.messenger(), NAMESPACE + "/method");
        this.registrar = registrar;
        channel.setMethodCallHandler(this);
    }

    public static void registerWith(Registrar registrar) {
        FlutterNordicDfuPlugin instance = new FlutterNordicDfuPlugin(registrar);
        DfuServiceListenerHelper.registerProgressListener(registrar.context(), instance.mDfuProgressListener);
    }

    String dfuAddress;
    String dfuName;
    String filePath ;
    Boolean fileInAsset ;
    Boolean forceDfu ;
    Boolean enableUnsafeExperimentalButtonlessServiceInSecureDfu ;
    Boolean disableNotification ;
    Boolean keepBond ;
    Boolean packetReceiptNotificationsEnabled ;
    Boolean restoreBond ;
    Boolean startAsForegroundService ;
    Integer numberOfPackets ;
    Boolean enablePRNs ;


    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if (call.method.equals("startDfu")) {
             dfuAddress = call.argument("address");
             dfuName = call.argument("name");
             filePath = call.argument("filePath");
             fileInAsset = call.argument("fileInAsset");
             forceDfu = call.argument("forceDfu");
             enableUnsafeExperimentalButtonlessServiceInSecureDfu = call.argument("enableUnsafeExperimentalButtonlessServiceInSecureDfu");
             disableNotification = call.argument("disableNotification");
             keepBond = call.argument("keepBond");
             packetReceiptNotificationsEnabled = call.argument("packetReceiptNotificationsEnabled");
             restoreBond = call.argument("restoreBond");
             startAsForegroundService = call.argument("startAsForegroundService");
             numberOfPackets = call.argument("numberOfPackets");
             enablePRNs = call.argument("enablePRNs");

            if (fileInAsset == null) {
                fileInAsset = false;
            }

            if (dfuAddress == null || filePath == null) {
                result.error("Abnormal parameter", "address and filePath are required", null);
                return;
            }

            if (fileInAsset) {
                filePath = registrar.lookupKeyForAsset(filePath);
                String tempFileName = PathUtils.getExternalAppCachePath(mContext)
                        + UUID.randomUUID().toString();

                tempFileName += filePath.substring(filePath.lastIndexOf("."));
                // copy asset file to temp path
                ResourceUtils.copyFileFromAssets(filePath, tempFileName, mContext);
                // now, the path is an absolute path, and can pass it to nordic dfu libarary
                filePath = tempFileName;
            }

            pendingResult = result;
            startDfu(dfuAddress, dfuName, filePath, forceDfu, enableUnsafeExperimentalButtonlessServiceInSecureDfu, disableNotification, keepBond, packetReceiptNotificationsEnabled, restoreBond, startAsForegroundService, result,numberOfPackets,enablePRNs);
        } else if (call.method.equals("abortDfu")) {
            if (controller != null) {
                controller.abort();
            }
        } else {
            result.notImplemented();
        }
    }

    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            if(what == 2){
                scanLeDevice(true);
            }else if(what == 3){
                startDfu(dfuAddress, dfuName, filePath, forceDfu, enableUnsafeExperimentalButtonlessServiceInSecureDfu, disableNotification, keepBond, packetReceiptNotificationsEnabled, restoreBond, startAsForegroundService, pendingResult,numberOfPackets,enablePRNs);
            }else if(what == 4){
              //  dfuMacTV.setText(dfuMac+" "+dfuName);
//                dfuMacTV.setText(dfuMac);
            }
        }
    };

    private void scanLeDevice(boolean enable) {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            //Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //startActivityForResult(enableBtIntent, 1);
            return;
        }
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            // 预先定义停止蓝牙扫描的时间（因为蓝牙扫描需要消耗较多的电量）
//            mHandler.removeCallbacks(scanLeRunable);
            mHandler.postDelayed(scanLeRunnable, 1000*20);
            // 定义一个回调接口供扫描结束处理
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
//            mHandler.removeCallbacks(scanLeRunable);

        }
    }
    private Runnable scanLeRunnable = new Runnable() {
        @Override
        public void run() {
            //if(StringUtils.isEmpty(dfuMac)) updatePercentTV.setText(getString(R.string.src_no_dfu));
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

//            VDLog.i(device.getName() + " : " + rssi);
            String name = device.getName();
            if (name != null) {
                if (name.toUpperCase().contains("DFU")) {// if (name.toUpperCase().equals("DFUTARG")) { DfuTar� :  DfuTar� : -49 for samsung
                    dfuAddress = device.getAddress();
                    dfuName = name;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
//                    mHandler.removeCallbacks(scanLeRunable);


                    mHandler.sendEmptyMessageDelayed(3,200);
                }
            }
        }
    };

    /**
     * Start Dfu
     */
    private void startDfu(String address, @Nullable String name, String filePath, Boolean forceDfu, Boolean enableUnsafeExperimentalButtonlessServiceInSecureDfu, Boolean disableNotification, Boolean keepBond, Boolean packetReceiptNotificationsEnabled, Boolean restoreBond, Boolean startAsForegroundService, Result result,Integer numberOfPackets,Boolean enablePRNs) {

        if(dfuAddress == null || dfuAddress.equals("")){
            scanLeDevice(true);
            return;
        }

        DfuServiceInitiator starter = new DfuServiceInitiator(address)
                .setDeviceName(name)
                .setKeepBond(true)
                .setForceDfu(false)
//                .setForceDfu(forceDfu == null ? false:forceDfu)
//                .setPacketsReceiptNotificationsEnabled(enablePRNs == null ? Build.VERSION.SDK_INT < Build.VERSION_CODES.M:enablePRNs)
//                .setPacketsReceiptNotificationsValue(numberOfPackets== null ? 0 :numberOfPackets)
                .setPrepareDataObjectDelay(400)
                .setPacketsReceiptNotificationsEnabled(false)
//        .setForceScanningForNewAddressInLegacyDfu(true)
                .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true);

        pendingResult = result;

        if (name != null) {
            starter.setDeviceName(name);
        }

        if(filePath.endsWith("hex")){
            starter.setBinOrHex(DfuService.TYPE_APPLICATION,null, filePath)
                    .setInitFile(null,null);
        }else if(filePath.endsWith("zip")){
            starter.setZip(filePath);
        }else{

            if (pendingResult != null) {
                pendingResult.error("3", "DFU FAILED", "not support file type " +filePath );
                pendingResult = null;
            }
            return;
        }


        if (enableUnsafeExperimentalButtonlessServiceInSecureDfu != null) {
            starter.setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(enableUnsafeExperimentalButtonlessServiceInSecureDfu);
        }

        if (forceDfu != null) {
            starter.setForceDfu(forceDfu);
        }

        if (disableNotification != null) {
            starter.setDisableNotification(disableNotification);
        }

        if (startAsForegroundService != null) {
            starter.setForeground(startAsForegroundService);
        }

        if (keepBond != null) {
            starter.setKeepBond(keepBond);
        }

        if (restoreBond != null) {
            starter.setRestoreBond(restoreBond);
        }

        if (packetReceiptNotificationsEnabled != null) {
            starter.setPacketsReceiptNotificationsEnabled(packetReceiptNotificationsEnabled);
        }

        // fix notification on android 8 and above
        if (startAsForegroundService == null || startAsForegroundService) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !hasCreateNotification) {
                DfuServiceInitiator.createDfuNotificationChannel(mContext);
                hasCreateNotification = true;
            }
        }

        controller = starter.start(mContext, DfuService.class);
    }

    private DfuProgressListenerAdapter mDfuProgressListener = new DfuProgressListenerAdapter() {
        @Override
        public void onDeviceConnected(@NonNull String deviceAddress) {
            super.onDeviceConnected(deviceAddress);
            channel.invokeMethod("onDeviceConnected", deviceAddress);
        }

        @Override
        public void onError(@NonNull String deviceAddress, int error, int errorType, String message) {
            super.onError(deviceAddress, error, errorType, message);
            cancelNotification();

            channel.invokeMethod("onError", deviceAddress);

            if (pendingResult != null) {
                pendingResult.error("2", "DFU FAILED", "device address: " + deviceAddress);
                pendingResult = null;
            }
        }

        @Override
        public void onDeviceConnecting(@NonNull String deviceAddress) {
            super.onDeviceConnecting(deviceAddress);
            channel.invokeMethod("onDeviceConnecting", deviceAddress);
        }

        @Override
        public void onDeviceDisconnected(@NonNull String deviceAddress) {
            super.onDeviceDisconnected(deviceAddress);
            channel.invokeMethod("onDeviceDisconnected", deviceAddress);
        }

        @Override
        public void onDeviceDisconnecting(String deviceAddress) {
            super.onDeviceDisconnecting(deviceAddress);
            channel.invokeMethod("onDeviceDisconnecting", deviceAddress);
        }

        @Override
        public void onDfuAborted(@NonNull String deviceAddress) {
            super.onDfuAborted(deviceAddress);
            cancelNotification();

            if (pendingResult != null) {
                pendingResult.error("2", "DFU ABORTED", "device address: " + deviceAddress);
                pendingResult = null;
            }

            channel.invokeMethod("onDfuAborted", deviceAddress);
        }

        @Override
        public void onDfuCompleted(@NonNull String deviceAddress) {
            super.onDfuCompleted(deviceAddress);
            cancelNotification();

            if (pendingResult != null) {
                pendingResult.success(deviceAddress);
                pendingResult = null;
            }

            channel.invokeMethod("onDfuCompleted", deviceAddress);

//            if(controller!=null)controller.abort();
        }

        @Override
        public void onDfuProcessStarted(@NonNull String deviceAddress) {
            super.onDfuProcessStarted(deviceAddress);
            channel.invokeMethod("onDfuProcessStarted", deviceAddress);
        }

        @Override
        public void onDfuProcessStarting(@NonNull String deviceAddress) {
            super.onDfuProcessStarting(deviceAddress);
            channel.invokeMethod("onDfuProcessStarting", deviceAddress);
        }

        @Override
        public void onEnablingDfuMode(@NonNull String deviceAddress) {
            super.onEnablingDfuMode(deviceAddress);
            channel.invokeMethod("onEnablingDfuMode", deviceAddress);

            Log.d(TAG,"onEnablingDfuMode  "+deviceAddress);
            ///search the dfu mac
            dfuAddress = "";
            mHandler.sendEmptyMessageDelayed(3,1000);
        }

        @Override
        public void onFirmwareValidating(@NonNull String deviceAddress) {
            super.onFirmwareValidating(deviceAddress);
            channel.invokeMethod("onFirmwareValidating", deviceAddress);
        }

        @Override
        public void onProgressChanged(@NonNull final String deviceAddress, final int percent, final float speed, final float avgSpeed, final int currentPart, final int partsTotal) {
            super.onProgressChanged(deviceAddress, percent, speed, avgSpeed, currentPart, partsTotal);

            Map<String, Object> paras = new HashMap<String, Object>() {{
                put("percent", percent);
                put("speed", speed);
                put("avgSpeed", avgSpeed);
                put("currentPart", currentPart);
                put("partsTotal", partsTotal);
                put("deviceAddress", deviceAddress);
            }};

            channel.invokeMethod("onProgressChanged", paras);
        }
    };

    private void cancelNotification() {
        // let's wait a bit until we cancel the notification. When canceled immediately it will be recreated by service again.
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                final NotificationManager manager = (NotificationManager) registrar.activity().getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null)
                    manager.cancel(DfuService.NOTIFICATION_ID);
            }
        }, 200);
    }

}

