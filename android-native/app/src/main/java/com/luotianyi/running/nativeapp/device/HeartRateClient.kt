package com.luotianyi.running.nativeapp.device
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.luotianyi.running.nativeapp.domain.HeartRatePacket
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HrDevice(val address:String,val name:String)
data class HrState(val scanning:Boolean=false,val devices:List<HrDevice> = emptyList(),val connected:Boolean=false,val label:String="未连接",val bpm:Int?=null,val measuredAt:Long=0,val error:String="")
@SuppressLint("MissingPermission")
class HeartRateClient(private val context:Context) {
    private val manager=context.getSystemService(BluetoothManager::class.java)
    private val handler=Handler(Looper.getMainLooper());private val mutable=MutableStateFlow(HrState());val state=mutable.asStateFlow()
    private var gatt:BluetoothGatt?=null;private var wanted:String?=null;private var retries=0;private var notificationRetries=0
    private val service=UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val measurement=UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val cccd=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private var scanGeneration=0
    private fun permitted()=if(Build.VERSION.SDK_INT>=31)ContextCompat.checkSelfPermission(context,Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(context,Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED else ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    private val scan=object:ScanCallback() {
        override fun onScanResult(callbackType:Int,result:ScanResult) {handler.post {if(!mutable.value.scanning)return@post;val device=HrDevice(result.device.address,result.device.name ?: result.scanRecord?.deviceName ?: "心率设备");mutable.value=mutable.value.copy(devices=(mutable.value.devices+device).distinctBy {it.address})}}
        override fun onScanFailed(errorCode:Int) {handler.post {stopScan();mutable.value=mutable.value.copy(error="扫描失败 $errorCode，请检查蓝牙和权限")}}
    }
    fun scan() {
        if(!permitted()) {mutable.value=mutable.value.copy(error="请允许蓝牙扫描与连接权限");return}
        if(manager.adapter?.isEnabled!=true) {mutable.value=mutable.value.copy(error="请先打开系统蓝牙");return}
        stopScan();val generation=++scanGeneration;mutable.value=mutable.value.copy(scanning=true,devices=emptyList(),error="")
        try {manager.adapter.bluetoothLeScanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(service)).build()),ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scan)} catch(e:Exception) {mutable.value=mutable.value.copy(scanning=false,error=e.message ?: "扫描不可用")}
        handler.postDelayed({if(generation==scanGeneration)stopScan()},10000)
    }
    fun stopScan() {scanGeneration++;runCatching {manager.adapter?.bluetoothLeScanner?.stopScan(scan)};mutable.value=mutable.value.copy(scanning=false)}
    fun connect(address:String) {if(!permitted())return;stopScan();disconnect();wanted=address;retries=0;open(address)}
    private fun open(address:String) {
        try {mutable.value=mutable.value.copy(label="连接中…",bpm=null,error="");gatt=manager.adapter.getRemoteDevice(address).connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE)
            val pending=gatt;handler.postDelayed({if(gatt===pending && !mutable.value.connected) {gatt?.close();gatt=null;mutable.value=mutable.value.copy(label="连接超时",error="请在手环上开启心率广播，并断开其他应用后重试")}},15000)
        } catch(e:Exception) {mutable.value=mutable.value.copy(label="未连接",error=e.message ?: "连接失败")}
    }
    fun disconnect() {wanted=null;runCatching {gatt?.disconnect();gatt?.close()};gatt=null;mutable.value=mutable.value.copy(connected=false,bpm=null,label="未连接")}
    fun freshBpm(now:Long=SystemClock.elapsedRealtime())=mutable.value.bpm.takeIf {mutable.value.connected && now-mutable.value.measuredAt<10000}
    private fun notify(g:BluetoothGatt) {
        val c=g.getService(service)?.getCharacteristic(measurement)
        val descriptor=c?.getDescriptor(cccd)
        if(c==null || descriptor==null) {mutable.value=mutable.value.copy(error="设备没有标准心率广播服务",label="不支持心率");g.disconnect();return}
        g.setCharacteristicNotification(c,true)
        notificationRetries++
        val success=if(Build.VERSION.SDK_INT>=33)g.writeDescriptor(descriptor,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)==BluetoothStatusCodes.SUCCESS else { @Suppress("DEPRECATION") descriptor.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;@Suppress("DEPRECATION") g.writeDescriptor(descriptor) }
        if(!success)retryNotification(g)
    }
    private fun retryNotification(g:BluetoothGatt) {if(notificationRetries<3)handler.postDelayed({if(g===gatt)notify(g)},700) else {mutable.value=mutable.value.copy(error="心率通知被拒绝，请关闭官方 App 的连接后重试");g.disconnect()}}
    private fun sample(g:BluetoothGatt,bytes:ByteArray) {handler.post {if(g!==gatt)return@post;val packet=HeartRatePacket.parse(bytes) ?: return@post;if(packet.contact==false) {mutable.value=mutable.value.copy(bpm=null,error="设备未贴紧皮肤");return@post};mutable.value=mutable.value.copy(bpm=packet.bpm,measuredAt=SystemClock.elapsedRealtime(),error="")}}
    private val callback=object:BluetoothGattCallback() {
        override fun onConnectionStateChange(g:BluetoothGatt,status:Int,newState:Int) {handler.post {
            if(g!==gatt)return@post
            if(newState==BluetoothProfile.STATE_CONNECTED && status==BluetoothGatt.GATT_SUCCESS) {mutable.value=mutable.value.copy(label="发现心率服务…");g.discoverServices()}
            else if(newState==BluetoothProfile.STATE_DISCONNECTED) {g.close();gatt=null;mutable.value=mutable.value.copy(connected=false,bpm=null,label="连接断开");val address=wanted;if(address!=null && retries++<3)handler.postDelayed({if(wanted==address)open(address)},2000)}
        }}
        override fun onServicesDiscovered(g:BluetoothGatt,status:Int) {handler.post {if(g!==gatt)return@post;if(status==BluetoothGatt.GATT_SUCCESS) {notificationRetries=0;notify(g)} else {mutable.value=mutable.value.copy(error="服务发现失败 $status");g.disconnect()}}}
        override fun onDescriptorWrite(g:BluetoothGatt,descriptor:BluetoothGattDescriptor,status:Int) {handler.post {if(g!==gatt)return@post;if(status==BluetoothGatt.GATT_SUCCESS) {mutable.value=mutable.value.copy(connected=true,label=g.device.name ?: "心率设备",error="");retries=0} else retryNotification(g)}}
        override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic,value:ByteArray) {if(c.uuid==measurement)sample(g,value)}
        @Deprecated("Platform API") override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic) {@Suppress("DEPRECATION") val value=c.value;if(c.uuid==measurement && value!=null)sample(g,value)}
    }
}
