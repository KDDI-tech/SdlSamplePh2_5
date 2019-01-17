/**
 Copyright 2018 KDDI Technology Corp.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.kddi_tech.sd4.sdlsamplev2_5;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.smartdevicelink.managers.CompletionListener;
import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.managers.SdlManagerListener;
import com.smartdevicelink.managers.file.FileManager;
import com.smartdevicelink.managers.file.filetypes.SdlArtwork;
import com.smartdevicelink.managers.file.filetypes.SdlFile;
import com.smartdevicelink.managers.lockscreen.LockScreenConfig;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.proxy.LockScreenManager;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.RPCResponse;
import com.smartdevicelink.proxy.TTSChunkFactory;
import com.smartdevicelink.proxy.interfaces.OnSystemCapabilityListener;
import com.smartdevicelink.proxy.rpc.AddCommand;
import com.smartdevicelink.proxy.rpc.DisplayCapabilities;
import com.smartdevicelink.proxy.rpc.GetVehicleData;
import com.smartdevicelink.proxy.rpc.GetVehicleDataResponse;
import com.smartdevicelink.proxy.rpc.HeadLampStatus;
import com.smartdevicelink.proxy.rpc.ListFiles;
import com.smartdevicelink.proxy.rpc.ListFilesResponse;
import com.smartdevicelink.proxy.rpc.MenuParams;
import com.smartdevicelink.proxy.rpc.OnCommand;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.OnLockScreenStatus;
import com.smartdevicelink.proxy.rpc.OnSystemRequest;
import com.smartdevicelink.proxy.rpc.OnVehicleData;
import com.smartdevicelink.proxy.rpc.SetDisplayLayout;
import com.smartdevicelink.proxy.rpc.Speak;
import com.smartdevicelink.proxy.rpc.SubscribeVehicleData;
import com.smartdevicelink.proxy.rpc.SubscribeVehicleDataResponse;
import com.smartdevicelink.proxy.rpc.TireStatus;
import com.smartdevicelink.proxy.rpc.VehicleType;
import com.smartdevicelink.proxy.rpc.enums.AmbientLightStatus;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.ComponentVolumeStatus;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.HMILevel;
import com.smartdevicelink.proxy.rpc.enums.LockScreenStatus;
import com.smartdevicelink.proxy.rpc.enums.PredefinedLayout;
import com.smartdevicelink.proxy.rpc.enums.RequestType;
import com.smartdevicelink.proxy.rpc.enums.SystemCapabilityType;
import com.smartdevicelink.proxy.rpc.enums.VehicleDataEventStatus;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener;
import com.smartdevicelink.transport.BaseTransportConfig;
import com.smartdevicelink.transport.MultiplexTransportConfig;
import com.smartdevicelink.transport.TCPTransportConfig;
import com.smartdevicelink.util.CorrelationIdGenerator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public class SdlService extends Service implements TextToSpeech.OnInitListener {

    private static final String LOG_TAG				= "[Log:[SdlService]]";
    private static final String DEBUG_TAG			= "[Log:[DEBUG]]";
    private static String APP_ID						= "0";  // set your own APP_ID
    private static Boolean USE_MANTICORE				= true;
    private static String APP_NAME					= null;
    private static int MANTICORE_TCP_PORT				= 0;
    private static String MANTICORE_IP_ADDRESS		= null;
    private static String NOTIFICATION_CHANNEL_ID		= null;
    private static final String SUPPORTED			= "supported";
    private static final String NONE_SUPPORTED		= "not supported";

    private SdlManager sdlManager						= null;
    private LockScreenManager lockScreenManager		= new LockScreenManager();
    private PrefManager prefManager;

    // Settings(DisplayCapabilities)
    private DisplayCapabilities mDisplayCapabilities	= null;
    private ArrayList<String> mAvailableTemplates		= null;

    // SubscribeVehicleData時の登録成否確認用の一時変数
    private Map<Integer,String> subscribeVehicleRequest = new HashMap<Integer,String>();

    // 取得可能な車両情報のMapデータ
    private Map<String, Boolean> usableVehicleData		= new HashMap<String, Boolean>();
    private static final String VD_FUEL_LEVEL			= "FUEL_LEVEL";
    private static final String VD_HEAD_LAMP_STATUS	= "HEAD_LAMP_STATUS";
    private static final String VD_TIRE_PRESSURE		= "TIRE_PRESSURE";
    private static final String VD_SPEED				= "SPEED";
    private static final String VD_BREAKING				= "DIVER_BREAKING";

    // 画面表示切替用のQueue
    private static Queue<UISettings> uiQueue				= new LinkedList<UISettings>();
    // Templateの変更管理
    private static String reqTemplateName					= PredefinedLayout.GRAPHIC_WITH_TEXT.toString();  // 変更要求をかける際のテンプレート

    // Command
    private static final int COMMAND_ID_1				= 1;

    // SoftButton
    private static final int SOFT_BUTTON_ID_1	= 1;
    private static final int SOFT_BUTTON_ID_2	= 2;
    private static final int SOFT_BUTTON_ID_3	= 3;

    // image file name
    private Map<String, SdlArtwork> artWorks = new HashMap<>();
    private static final String ICON_LOCK_SCREEN		= "sdl_lock_screen_img.png";
    private static final String ICON_TIRE				= "sdl_tire.png";
    private static final String ICON_HEADLIGHT			= "sdl_headlight.png";
    private static final String ICON_FUEL				= "sdl_fuel.png";
    private static final String ICON_FILENAME			= "sdl_hu_icon.png";
    private static final String PIC_CHARACTER			= "sdl_chara.png";
    private static final String PIC_SORRY				= "sdl_hu_sorry.png";

    // 主要機能のデータ
    private static AmbientLightStatus currentAmbientStatus	= AmbientLightStatus.UNKNOWN;
    private static final List<String> FUEL_SWITCH_LIST		= new ArrayList<String>() {{ add("seekSwitch1"); add("seekSwitch2"); add("seekSwitch3"); add("seekSwitch4"); add("seekSwitch5"); }};
    private static final List<String> FUEL_LEVEL_LIST		= new ArrayList<String>() {{ add("seekText1"); add("seekText2"); add("seekText3"); add("seekText4"); add("seekText5"); }};
    private static List<Integer> fuelLvThreshold		= new ArrayList<Integer>();    // FuelLevelの通知閾値
    private static int prevFuelLevel					= 0;
    private boolean isHeadlightTurnOn				= false;
    private boolean isHeadlightTurnOff				= false;

    private boolean isTimerWorked					= false;      // タイマーの動作状況(何らかの画面変更を行った後、TIMER_DELAY_MS時間経過するまではtrue)
    private boolean isChangeUIWorked				= false;
    private static final int TIMER_DELAY_MS		= 7000; // 画面内UIの(デフォルト時以外の)表示時間

    // RSS取得イベント(車両停止検知)用変数
    private static int latestSpeed					= -1;
    private static VehicleDataEventStatus latestBreakState = VehicleDataEventStatus.NO;
    private static boolean detectVehicleStop = false;

    // TTS用変数
    private TextToSpeech tts;
    private boolean isTtsEnabled = false;
    private Map<Integer, String> ttsStandby = new HashMap<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        _connectForeground();
    }

    /**
     * ServiceをstartForegroundで起動させる
     */
    private void _connectForeground() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            APP_ID = getResources().getString(R.string.app_id);
            APP_NAME = getResources().getString(R.string.app_name);

            // BuildConfigにManticoreのフィールドがなければ動作をさせないようにする
            String manticorePort = BuildConfig.MANTICORE_PORT;
            MANTICORE_IP_ADDRESS = BuildConfig.MANTICORE_IP_ADDR;
            if(manticorePort == null || MANTICORE_IP_ADDRESS == null) {
                USE_MANTICORE = false;
            } else {
                MANTICORE_TCP_PORT = Integer.parseInt(manticorePort);
                USE_MANTICORE = true;
            }
            // パッケージ毎に一意のID値(長い文字列長の場合切り捨てられる場合があるようです)
            NOTIFICATION_CHANNEL_ID = getResources().getString(R.string.notif_channel_id);
            usableVehicleData.put(VD_FUEL_LEVEL,false);
            usableVehicleData.put(VD_HEAD_LAMP_STATUS,false);
            usableVehicleData.put(VD_TIRE_PRESSURE,false);
            usableVehicleData.put(VD_SPEED,false);
            usableVehicleData.put(VD_BREAKING,false);
            prevFuelLevel = 0;
            startForeground(1, _createNotification());
            tts = new TextToSpeech(this, this);
        }
    }

    /**
     * Android Oreo(v26)以降の端末向け対応
     * 通知チャネル(NotificationChannel)を登録し、通知用のインスタンスを返却する
     * @return Notification 作成した通知情報
     */
    private Notification _createNotification() {
        String name = getResources().getString(R.string.notif_channel_name);
        String description = getResources().getString(R.string.notif_channel_desctiption);
        int importance = NotificationManager.IMPORTANCE_HIGH; // デフォルトの重要度

        NotificationManager notifManager = getSystemService(NotificationManager.class);
        if (notifManager != null && notifManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.setSound(null, null);
            channel.setShowBadge(false);
            notifManager.createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getResources().getString(R.string.notif_content_title))
            .setContentText(getResources().getString(R.string.notif_content_text))
            .setSmallIcon(R.drawable.ic_sdl)
            .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(LOG_TAG,"onStartCommand called");
        if (! intent.getBooleanExtra(getResources().getString(R.string.is_first_connect),true)) {
            _connectForeground();
        }
        prefManager = PrefManager.getInstance(getApplicationContext());
        //@Deprecated

        if (sdlManager == null) {
            BaseTransportConfig transport = null;
            if(BuildConfig.TRANSPORT.equals("MULTI")){
                int securityLevel;
                if (BuildConfig.SECURITY.equals("HIGH")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_HIGH;
                } else if (BuildConfig.SECURITY.equals("MED")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_MED;
                } else if (BuildConfig.SECURITY.equals("LOW")) {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_LOW;
                } else {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF;
                }
                transport = new MultiplexTransportConfig(this, APP_ID, securityLevel);
            } else if(BuildConfig.TRANSPORT.equals("TCP")){
                transport = new TCPTransportConfig(MANTICORE_TCP_PORT, MANTICORE_IP_ADDRESS, true);
            } else if (BuildConfig.TRANSPORT.equals("MULTI_HB")) {
                MultiplexTransportConfig mtc = new MultiplexTransportConfig(this, APP_ID, MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF);
                mtc.setRequiresHighBandwidth(true);
                transport = mtc;
            }

            // SdlManagerでのイベントリスナー
            // sdlandroid 4.6.3のIProxyListenerALMを実装することで発生した大量のリスナーを空実装していましたが、
            // 4.7.1では、onStart()内に、FunctionID.ON_XXXXXを設定することで、必要最上限の実装で済むように変更されました。
            SdlManagerListener listener = new SdlManagerListener() {
                @Override
                public void onStart() {


                    // RPC listeners and other functionality can be called once this callback is triggered.
                    // HMI Status Listener
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnHMIStatus status = (OnHMIStatus) notification;
                            if(status.getFirstRun() && mDisplayCapabilities == null) {
                                _getDisplayCapabilities();
                            }
                            if (status.getHmiLevel().equals(HMILevel.HMI_FULL)) {
                                // Other HMI (Show, PerformInteraction, etc.) would go here
                                if(status.getFirstRun()) {
                                    _registVehicleData();
                                    _setCommand();
                                    _showGreetingUI();
                                }

                                // @todo 仮対応
                                // lockscreenが自動的に表示されないため、
                                // FunctionID.ON_LOCK_SCREEN_STATUSも同様に発火しないため、
                                // HMI_StatusがFullの時にロックスクリーンを表示する
                                Intent showLockScreenIntent = new Intent(getApplicationContext(), LockScreenActivity.class);
                                showLockScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                if(lockScreenManager.getLockScreenIcon() != null){
                                    // HUからロックスクリーン用のアイコンが取得できた場合、デフォルトで設定していた画像は上書きする
                                    showLockScreenIntent.putExtra(LockScreenActivity.LOCKSCREEN_BITMAP_EXTRA, lockScreenManager.getLockScreenIcon());
                                }
                                startActivity(showLockScreenIntent);
                            } else {
                                sendBroadcast(new Intent(LockScreenActivity.CLOSE_LOCK_SCREEN_ACTION));
                            }
                        }
                    });

                    // Menu Selected Listener
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_COMMAND, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnCommand command = (OnCommand) notification;
                            Integer id = command.getCmdID();

                            if(id != null) {
                                switch (id) {
                                    case COMMAND_ID_1:
                                        // set data to MainActivity
                                        Intent broadcast = new Intent();
                                        broadcast.putExtra(getResources().getString(R.string.is_first_connect), false);
                                        broadcast.setAction(getResources().getString(R.string.action_service_close));
                                        getBaseContext().sendBroadcast(broadcast);

                                        // @todo 仮対応
                                        // sdl_android 4.7.1ではonDestroy()をコールすると
                                        // スマホは接続解除されるが、
                                        // Manticore側は待機中のまま解除されなため
                                        // これが正しいやり方かどうか不明
                                        stopSelf();
                                        //SdlService.this.onDestroy();
                                        //SdlService.this.stopSelf();
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                    });

                    // onOnVehicleData
                    // 車両データに変更があった場合、このメソッドに通知されます。
                    // 複数登録していても、変更のあった項目1つのみが飛んできます。
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_VEHICLE_DATA, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notif) {
                            OnVehicleData notification = (OnVehicleData) notif;
                            if (usableVehicleData.get(VD_HEAD_LAMP_STATUS) && notification.getHeadLampStatus() != null) {
                                _changeDisplayByHeadLampStatus(notification.getHeadLampStatus());
                            }
                            if (usableVehicleData.get(VD_FUEL_LEVEL) && notification.getFuelLevel() != null) {
                                _changeDisplayByFuelLevel(notification.getFuelLevel());
                            }
                            if (usableVehicleData.get(VD_TIRE_PRESSURE) && notification.getTirePressure() != null) {
                                _changeDisplayByTirePressure(notification.getTirePressure());
                            }
                            if(usableVehicleData.get(VD_SPEED) && notification.getSpeed() != null){
                                latestSpeed = notification.getSpeed().intValue();
                                if(latestSpeed == 0) {
                                    _checkVehicleDriveState();
                                }
                            }
                            if(usableVehicleData.get(VD_BREAKING) && notification.getDriverBraking() != null){
                                latestBreakState = notification.getDriverBraking();
                                if(latestBreakState.equals(VehicleDataEventStatus.YES)) {
                                    _checkVehicleDriveState();
                                }
                            }
                        }
                    });

                    // onOnSystemRequest
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_LOCK_SCREEN_STATUS, new OnRPCNotificationListener() {
                        // [原因調査中]実際にはLockSceenが自動的に起動しないため、この処理には入らない。
                        @Override
                        public void onNotified(RPCNotification notif) {
                            OnLockScreenStatus notification = (OnLockScreenStatus) notif;
                            if(notification.getHMILevel() == HMILevel.HMI_FULL && notification.getShowLockScreen() == LockScreenStatus.REQUIRED) {
                                Intent showLockScreenIntent = new Intent(getApplicationContext(), LockScreenActivity.class);
                                showLockScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                if(lockScreenManager.getLockScreenIcon() != null){
                                    // HUからロックスクリーン用のアイコンが取得できた場合、デフォルトで設定していた画像は上書きする
                                    showLockScreenIntent.putExtra(LockScreenActivity.LOCKSCREEN_BITMAP_EXTRA, lockScreenManager.getLockScreenIcon());
                                }
                                startActivity(showLockScreenIntent);
                            }
                        }
                    });

                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_SYSTEM_REQUEST,new OnRPCNotificationListener() {

                        @Override
                        public void onNotified(RPCNotification notif) {
                            OnSystemRequest notification = (OnSystemRequest) notif;
                            if(notification.getRequestType().equals(RequestType.LOCK_SCREEN_ICON_URL)){
                                Log.i(LOG_TAG,"ON_SYSTEM_REQUEST");
                                lockScreenManager.downloadLockScreenIcon(notification.getUrl(), new LockScreenDownloadedListener());

                            }
                        }
                    });
                }

                @Override
                public void onDestroy() {
                    Log.w(LOG_TAG,"onDestroy");
                    SdlService.this.stopSelf();
                }

                @Override
                public void onError(String info, Exception e) {
                    e.printStackTrace();
                }
            };

            SdlArtwork appIcon = new SdlArtwork(ICON_FILENAME, FileType.GRAPHIC_PNG, R.drawable.ic_application_icon, true);

            // The app type to be used
            Vector<AppHMIType> appType = new Vector<>();
            appType.add(AppHMIType.INFORMATION);
            appType.add(AppHMIType.MESSAGING);
            appType.add(AppHMIType.COMMUNICATION);
            appType.add(AppHMIType.TESTING);

            // The manager builder sets options for your session
            SdlManager.Builder builder = new SdlManager.Builder(this, APP_ID, APP_NAME, listener);
            builder.setAppTypes(appType);
            if(USE_MANTICORE) {
                builder.setTransportType(new TCPTransportConfig(MANTICORE_TCP_PORT, MANTICORE_IP_ADDRESS, true));
            } else {
                builder.setTransportType(transport);
            }
            builder.setAppIcon(appIcon);

            // set Lock Screen
            LockScreenConfig lockScreenConfig = new LockScreenConfig();
            lockScreenConfig.setEnabled(true);
            lockScreenConfig.setBackgroundColor(R.color.colorPrimaryDark);
            lockScreenConfig.setAppIcon(R.drawable.sdl_lockscreen_icon);
            builder.setLockScreenConfig(lockScreenConfig);
            sdlManager = builder.build();
            sdlManager.start();

        }
        return START_STICKY;
    }

    private class LockScreenDownloadedListener implements LockScreenManager.OnLockScreenIconDownloadedListener{
        @Override
        public void onLockScreenIconDownloaded(Bitmap icon) {
            Log.i(LOG_TAG, "Lock screen icon downloaded successfully");
        }
        @Override
        public void onLockScreenIconDownloadError(Exception e) {
            Log.e(LOG_TAG, "Couldn't download lock screen icon, resorting to default.");
        }
    }

    @Override
    public void onDestroy(){

        Log.w(LOG_TAG,"parent  onDestroy");
        if(tts != null) {
            tts.shutdown();
        }
        if (sdlManager != null) {
            sdlManager.dispose();
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if(notificationManager!=null){ //If this is the only notification on your channel
            notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
        }
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    /**
     * ※※※※※
     * DisplayCapabilitiesを取得し、画面表示が可能であれば表示する
     */
    private void _getDisplayCapabilities() {
        if (sdlManager == null) {
            return;
        }

        _detectDisplayCapabilities();
        if(mDisplayCapabilities != null) {
            Boolean gSupport = mDisplayCapabilities.getGraphicSupported();
            if (gSupport != null && gSupport) {
                // Graphics Supported
                _createArtWork();
            }

            mAvailableTemplates = new ArrayList<String>(mDisplayCapabilities.getTemplatesAvailable());
            if (mAvailableTemplates != null && mAvailableTemplates.contains(reqTemplateName)) {
                // mDisplayLayoutSupported = true;
                /*
                // show enable template list
                for (String str : mAvailableTemplates) {
                    Log.i(DEBUG_TAG, "dispCapabilities：" + str);
                }
                 */
                _updateTemplate();
            }
        }
    }

    /**
     * アプリで使用する画像を準備する
     */
    private void _createArtWork() {
        artWorks.put(ICON_TIRE, new SdlArtwork(ICON_TIRE, FileType.GRAPHIC_PNG, R.drawable.tire, true));
        artWorks.put(ICON_FUEL, new SdlArtwork(ICON_FUEL, FileType.GRAPHIC_PNG, R.drawable.fuel, true));
        artWorks.put(ICON_HEADLIGHT, new SdlArtwork(ICON_HEADLIGHT, FileType.GRAPHIC_PNG, R.drawable.headlight, true));
        artWorks.put(PIC_SORRY, new SdlArtwork(PIC_SORRY, FileType.GRAPHIC_PNG, R.drawable.pic_sorry, true));
        artWorks.put(PIC_CHARACTER, new SdlArtwork(PIC_CHARACTER, FileType.GRAPHIC_PNG, R.drawable.pic_welcome, true));
    }

    /**
     * ※※※※※
     * 画面表示中のテンプレートを別のものに切り替える
     */
    private void _updateTemplate() {
        Log.i(DEBUG_TAG, "Called updateTemplate");
        SetDisplayLayout setDisplayLayoutRequest = new SetDisplayLayout();
        setDisplayLayoutRequest.setDisplayLayout(reqTemplateName);
        sdlManager.sendRPC(setDisplayLayoutRequest);
    }

    /**
     * ※※※※※
     * DisplayCapabilitiesを取得する
     */
    private void _detectDisplayCapabilities () {
        if (mDisplayCapabilities != null) {
            return;
        }
        sdlManager.getSystemCapabilityManager().getCapability(SystemCapabilityType.DISPLAY, new OnSystemCapabilityListener(){

            @Override
            public void onCapabilityRetrieved(Object capability){
                mDisplayCapabilities = (DisplayCapabilities) capability;
            }

            @Override
            public void onError(String info){
                Log.e(LOG_TAG, "DisplayCapability could not be retrieved: "+ info);
            }
        });
    }

    /**
     * ※※※※※
     * GetVehicleDataで取得した車両情報を元に、車両がサポートしている情報をSharedPreferencesに保存する。
     * この際、サポートしていない項目と、SettingsAvctivityで設定した通知情報を加味して、
     * 必要のない車両情報はsubscribeしないようにする。
     */
    private void _registVehicleData() {
        GetVehicleData vdRequest = new GetVehicleData();
        vdRequest.setVin(true);
        vdRequest.setTirePressure(true);
        vdRequest.setFuelLevel(true);

        // RSSの情報が登録されていない場合、スキップする
        if(!prefManager.getPrefByStr(R.id.rssText,"").isEmpty()) {
            // 車両停止状態の判定用として
            vdRequest.setDriverBraking(true);
            vdRequest.setSpeed(true);
        }

        vdRequest.setOnRPCResponseListener(new OnRPCResponseListener() {

            Vehicle vehicle;
            TireStatus tire;

            @Override
            public void onResponse(int correlationId, RPCResponse response) {
                if(response.getSuccess()){
                    String vin = ((GetVehicleDataResponse) response).getVin();
                    LocalDateTime d = LocalDateTime.now();
                    DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

                    VehicleType vehicleType = new VehicleType();
                    vehicle = new Vehicle();
                    vehicle.setVin(vin);
                    vehicle.setCreateAt(d.format(f));
                    vehicle.setUpdateAt(d.format(f));
                    vehicle.setMaker(vehicleType.getMake());
                    vehicle.setModel(vehicleType.getModel());
                    vehicle.setModelYear(vehicleType.getModelYear());

                    // タイヤ空気圧の判定
                    tire = ((GetVehicleDataResponse) response).getTirePressure();
                    if(tire == null) {
                        ComponentVolumeStatus tireNotSupport = ComponentVolumeStatus.NOT_SUPPORTED;
                        _checkTirePressureSupport(tireNotSupport, getResources().getString(R.string.tire_front_left));
                        _checkTirePressureSupport(tireNotSupport, getResources().getString(R.string.tire_front_right));
                        _checkTirePressureSupport(tireNotSupport, getResources().getString(R.string.tire_rear_left));
                        _checkTirePressureSupport(tireNotSupport, getResources().getString(R.string.tire_rear_right));
                        _checkTirePressureSupport(tireNotSupport, getResources().getString(R.string.tire_inner_left));
                        _checkTirePressureSupport(tireNotSupport, getResources().getString(R.string.tire_inner_right));
                    } else {
                        _checkTirePressureSupport(tire.getLeftFront().getStatus(), getResources().getString(R.string.tire_front_left));
                        _checkTirePressureSupport(tire.getRightFront().getStatus(), getResources().getString(R.string.tire_front_right));
                        _checkTirePressureSupport(tire.getLeftRear().getStatus(), getResources().getString(R.string.tire_rear_left));
                        _checkTirePressureSupport(tire.getRightRear().getStatus(), getResources().getString(R.string.tire_rear_right));
                        _checkTirePressureSupport(tire.getInnerLeftRear().getStatus(), getResources().getString(R.string.tire_inner_left));
                        _checkTirePressureSupport(tire.getInnerRightRear().getStatus(), getResources().getString(R.string.tire_inner_right));
                    }

                    // 燃料残量の判定
                    Double fuel = ((GetVehicleDataResponse) response).getFuelLevel();
                    if (fuel == null || fuel.intValue() < 0) {
                        vehicle.setFuelLevel(NONE_SUPPORTED);
                    } else {
                        vehicle.setFuelLevel(SUPPORTED);
                        usableVehicleData.put(VD_FUEL_LEVEL,true);
                    }

                    // RSSの情報が登録されていない場合、スキップする
                    if(!prefManager.getPrefByStr(R.id.rssText,"").isEmpty()) {
                        // 車速情報の取得判定
                        Double speed = ((GetVehicleDataResponse) response).getSpeed();
                        if (speed == null || speed.intValue() < 0) {
                            vehicle.setSpeed(NONE_SUPPORTED);
                        } else {
                            vehicle.setSpeed(SUPPORTED);
                            usableVehicleData.put(VD_SPEED, true);
                        }

                        // ブレーキ情報の取得判定
                        VehicleDataEventStatus status = ((GetVehicleDataResponse) response).getDriverBraking();
                        // manticoreの場合、ブレーキの初期値がNOT_SUPPORTEDになっていて、永久に処理が走らない
                        //if (status == null || status.equals(VehicleDataEventStatus.NOT_SUPPORTED)) {
                        if (status == null) {
                            vehicle.setBreake(NONE_SUPPORTED);
                        } else {
                            vehicle.setBreake(SUPPORTED);
                            usableVehicleData.put(VD_BREAKING, true);
                        }
                    }

                    ArrayList<String> arrayList = new ArrayList<>();
                    Gson gson = new Gson();
                    String vinKey = getResources().getString(R.string.pref_key_vin);
                    String json = prefManager.read(vinKey,"");
                    boolean isNweCar = false; // 未登録の車に接続している場合True
                    if(json.isEmpty()) {
                        isNweCar = true;
                    } else {
                        arrayList = gson.fromJson(json, new TypeToken<ArrayList<String>>(){}.getType());
                        if (!arrayList.contains(vin)) {
                            isNweCar = true;
                        }
                    }

                    // SharedPreferencesに車両情報が保持されているか確認し、
                    // 無ければ追加、あれば最終接続日時を更新する
                    if(isNweCar) {
                        // 車両識別番号のリストを保存する
                        arrayList.add(vin);
                        prefManager.write(vinKey, gson.toJson(arrayList));
                        prefManager.write(vin, gson.toJson(vehicle));
                    } else {
                        Vehicle existVehicle = gson.fromJson(prefManager.read(vin, ""), Vehicle.class);
                        existVehicle.setUpdateAt(d.format(f));
                        prefManager.write(vin, gson.toJson(existVehicle));
                    }

                    // ユーザの通知許可があるものに限り通知するようする
                    // TirePressure
                    if (!prefManager.read(getResources().getResourceEntryName(R.id.tireSwitch),true) ) {
                        usableVehicleData.put(VD_TIRE_PRESSURE,false);
                    } else {
                        _changeDisplayByTirePressure(tire);
                    }
                    // FuelLevelをSubscribeするかどうか
                    if(usableVehicleData.get(VD_FUEL_LEVEL)) {
                        for(int i = 0; i < FUEL_SWITCH_LIST.size(); i++){
                            if (prefManager.read(FUEL_SWITCH_LIST.get(i), true)) {
                                int fuelLv = Integer.parseInt(prefManager.read(FUEL_LEVEL_LIST.get(i),"0"));
                                if(!fuelLvThreshold.contains(fuelLv)) {
                                    fuelLvThreshold.add(fuelLv);
                                    Collections.reverse(fuelLvThreshold);
                                }
                            }
                        }
                        if (fuelLvThreshold.size() == 0) {
                            usableVehicleData.put(VD_FUEL_LEVEL,false);
                        } else {
                            usableVehicleData.put(VD_FUEL_LEVEL,true);
                            assert fuel != null;
                            _changeDisplayByFuelLevel(fuel);
                        }
                    }
                    // HeadLightをSubscribeするかどうか
                    isHeadlightTurnOn = prefManager.read(getResources().getResourceEntryName(R.id.lightOnSwitch),true);
                    isHeadlightTurnOff = prefManager.read(getResources().getResourceEntryName(R.id.lightOffSwitch),true);

                    if (isHeadlightTurnOn || isHeadlightTurnOff) {
                        usableVehicleData.put(VD_HEAD_LAMP_STATUS,true);
                    }
                    _subscribeVehicleData();
                }
            }
            private void _checkTirePressureSupport(ComponentVolumeStatus status, String str) {
                if(ComponentVolumeStatus.NOT_SUPPORTED.equals(status)) {
                    vehicle.setTireMap(str, NONE_SUPPORTED);
                } else {
                    vehicle.setTireMap(str, SUPPORTED);
                    usableVehicleData.put(VD_TIRE_PRESSURE,true);
                }
            }
        });
        sdlManager.sendRPC(vdRequest);
    }

    /**
     * ※※※※※
     * コマンド(メニュー)をHUに表示する
     * どのコマンドが選択されたかは、onOnCommand()で判定を行う
     */
    private void _setCommand() {
        MenuParams params = new MenuParams();
        params.setParentID(0);
        params.setPosition(0);
        params.setMenuName(getResources().getString(R.string.cmd_exit));

        AddCommand command = new AddCommand();
        command.setCmdID(COMMAND_ID_1);
        command.setMenuParams(params);
        command.setVrCommands(Collections.singletonList(getResources().getString(R.string.cmd_exit)));
        sdlManager.sendRPC(command);
    }

    /**
     * ※※※※※
     * Greetingメッセージを表示する
     */
    private void _showGreetingUI() {
        UISettings ui = new UISettings(UISettings.EventType.Greeting, PIC_CHARACTER,null,
                "こんにちは！あなたの運転のサポートを担当いたします。",
                "パワーアップするために皆様のコメントお待ちしています！",
                null,
                null);
        _addChangeUIQueue(ui);
        //_showSoftButtons();
    }


    /**
     * 車両データの登録結果から、利用可否を保持するクラス
     * ※sdl_android 4.6.1 で実装されていたonSubscribeVehicleDataResponseと同じ処理を行うため
     * 関数名は同一にしていますが、SDL非標準機能です。
     */
    private class onSubscribeVehicleDataResponse extends OnRPCResponseListener {
        @Override
        public void onResponse(int correlationId, RPCResponse response) {
            usableVehicleData.put(subscribeVehicleRequest.get(correlationId), response.getSuccess());

            // 全ての機能に対してsubscribeが失敗した場合は、エラーを表示する。
            subscribeVehicleRequest.remove(correlationId);
            if(subscribeVehicleRequest.isEmpty()) {
                boolean supportFlg = false;
                for(String key : usableVehicleData.keySet()){
                    if(usableVehicleData.get(key)){
                        Log.i(DEBUG_TAG,"onSubscribeVehicleDataResponse "+ usableVehicleData.get(key) + "support");
                        supportFlg = true;
                        break;
                    } else {
                        Log.i(DEBUG_TAG,"onSubscribeVehicleDataResponse "+ usableVehicleData.get(key) + " is not supported");
                    }
                }
                if(!supportFlg) {
                    UISettings ui = new UISettings(UISettings.EventType.NotSupport,
                            PIC_SORRY,null,
                            "ご乗車中のお車ではお手伝い出来ることがなさそうです。",
                            "別の車にてお試しください。",
                            null, null);
                    _addChangeUIQueue(ui);
                }
            }
        }
    }

    /**
     * ※※※※※
     * 車両情報に変更があった際、通知するように要求する
     */
    private void _subscribeVehicleData() {
        SubscribeVehicleData subscribeRequest;// = new SubscribeVehicleData();
        if(usableVehicleData.get(VD_HEAD_LAMP_STATUS)) {
            subscribeRequest = new SubscribeVehicleData();
            subscribeRequest.setHeadLampStatus(true);
            subscribeVehicleRequest.put(subscribeRequest.getCorrelationID(),VD_HEAD_LAMP_STATUS);
            subscribeRequest.setOnRPCResponseListener(new onSubscribeVehicleDataResponse());
            sdlManager.sendRPC(subscribeRequest);
        }
        if(usableVehicleData.get(VD_FUEL_LEVEL)) {
            subscribeRequest = new SubscribeVehicleData();
            subscribeRequest.setFuelLevel(true);
            subscribeVehicleRequest.put(subscribeRequest.getCorrelationID(),VD_FUEL_LEVEL);
            subscribeRequest.setOnRPCResponseListener(new onSubscribeVehicleDataResponse());
            sdlManager.sendRPC(subscribeRequest);
        }
        if(usableVehicleData.get(VD_TIRE_PRESSURE)) {
            subscribeRequest = new SubscribeVehicleData();
            subscribeRequest.setTirePressure(true);
            subscribeVehicleRequest.put(subscribeRequest.getCorrelationID(),VD_TIRE_PRESSURE);
            subscribeRequest.setOnRPCResponseListener(new onSubscribeVehicleDataResponse());
            sdlManager.sendRPC(subscribeRequest);
        }
        if(usableVehicleData.get(VD_SPEED)) {
            subscribeRequest = new SubscribeVehicleData();
            subscribeRequest.setSpeed(true);
            subscribeVehicleRequest.put(subscribeRequest.getCorrelationID(),VD_SPEED);
            subscribeRequest.setOnRPCResponseListener(new onSubscribeVehicleDataResponse());
            sdlManager.sendRPC(subscribeRequest);
        }
        if(usableVehicleData.get(VD_BREAKING)) {
            subscribeRequest = new SubscribeVehicleData();
            subscribeRequest.setDriverBraking(true);
            subscribeVehicleRequest.put(subscribeRequest.getCorrelationID(),VD_BREAKING);
            subscribeRequest.setOnRPCResponseListener(new onSubscribeVehicleDataResponse());
            sdlManager.sendRPC(subscribeRequest);
        }
    }

    /**
     * ※※※※※
     * 車両の走行状態(停止中かどうか)
     * 登録されているRSS情報からデータを取得する
     */
    private void _checkVehicleDriveState() {
        // (瞬間的な情報なので実用向きではないけれども)車両が停止して3秒程度動かない場合に
        // RSS情報を取得しにいく
        try {
            Thread.sleep(3 * 1000);
            if(latestSpeed == 0 && latestBreakState.equals(VehicleDataEventStatus.YES)) {
                String url = prefManager.getPrefByStr(R.id.rssText,"");
                if(!url.isEmpty()) {
                    String lastMod = prefManager.read(RssActivity.RSS_LAST_MODIFIED_KEY, "");
                    new RssAsyncReader(this).execute(url, lastMod);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * ※※※※※
     * 取得したRSSを表示する
     * @param url url
     * @param isSuccess RSS取得成否
     * @param rssData 取得したRSS情報
     */
    public void _checkRssCallback(String url, boolean isSuccess, Map<Integer, RssAsyncReader.RssContent> rssData) {
        if(!rssData.isEmpty()) {
            for(int i: rssData.keySet()){
                // 表示切替までの時間が長いため、デモとしては表示件数を3件に制限する
                if(i>=3) {
                    break;
                }
                UISettings ui = new UISettings(UISettings.EventType.Other, PIC_CHARACTER , null,
                        rssData.get(i).getTitle(), null,null, null);
                _addChangeUIQueue(ui);
            }
        }
    }

    /**
     * ※※※※※
     * タイヤ空気圧に変更があった場合の処理を定義しています
     * @param tire TireStatus
     */
    private void _changeDisplayByTirePressure(TireStatus tire) {
        ComponentVolumeStatus inLeft = tire.getInnerLeftRear().getStatus();
        ComponentVolumeStatus inRight = tire.getInnerRightRear().getStatus();
        ComponentVolumeStatus frontLeft = tire.getLeftFront().getStatus();
        ComponentVolumeStatus frontRight = tire.getRightFront().getStatus();
        ComponentVolumeStatus rearLeft = tire.getLeftRear().getStatus();
        ComponentVolumeStatus rearRight = tire.getRightRear().getStatus();

        String textfield1 = _checkTirePressure(ComponentVolumeStatus.LOW, frontLeft, frontRight, rearLeft, rearRight, inLeft, inRight);
        String textfield2 = _checkTirePressure(ComponentVolumeStatus.ALERT, frontLeft, frontRight, rearLeft, rearRight, inLeft, inRight);
        String textfield3 = _checkTirePressure(ComponentVolumeStatus.FAULT, frontLeft, frontRight, rearLeft, rearRight, inLeft, inRight);

        if (textfield1 != null) {
            textfield1 = textfield1.concat("の空気圧が低くなっています。");
        }

        if (textfield2 != null) {
            if (textfield3 != null) {
                textfield2 = String.join("、", textfield2,textfield3);
            }
            textfield2 = textfield2.concat("に異常を検知しました。");
        } else if (textfield3 != null) {
            textfield2 = textfield3.concat("に異常を検知しました。");
        }
        if (textfield1 == null && textfield2 != null) {
            textfield1 = textfield2;
            textfield2 = null;
        }

        if (textfield1 != null) {
            UISettings ui = new UISettings(UISettings.EventType.Tire, ICON_TIRE,null,textfield1, textfield2, null, null);
            _addChangeUIQueue(ui);
        }
    }

    /**
     * ※※※※※
     * タイヤ空気圧のチェックを行い、指定したステータスと一致したものを文字列ベースで連結して返却する
     * @param checkStatus ComponentVolumeStatusのインスタンス
     * @param frontLeft 前輪(左)の状態
     * @param frontRight 前輪(右)の状態
     * @param rearLeft 後輪(左)の状態
     * @param rearRight 後輪(右)の状態
     * @param inLeft 中輪(左)の状態
     * @param inRight 中輪(右)の状態
     * @return checkStatusで指定された状態と一致したタイヤ情報
     */
    private String _checkTirePressure(ComponentVolumeStatus checkStatus, ComponentVolumeStatus frontLeft, ComponentVolumeStatus frontRight, ComponentVolumeStatus rearLeft, ComponentVolumeStatus rearRight, ComponentVolumeStatus inLeft, ComponentVolumeStatus inRight) {
        List<String> list = new ArrayList<>();
        if (checkStatus.equals(frontLeft)) {
            list.add(getResources().getString(R.string.tire_front_left));
        }
        if (checkStatus.equals(frontRight)) {
            list.add(getResources().getString(R.string.tire_front_right));
        }
        if (checkStatus.equals(rearLeft)) {
            list.add(getResources().getString(R.string.tire_rear_left));
        }
        if (checkStatus.equals(rearRight)) {
            list.add(getResources().getString(R.string.tire_rear_right));
        }
        if (checkStatus.equals(inLeft)) {
            list.add(getResources().getString(R.string.tire_inner_left));
        }
        if (checkStatus.equals(inRight)) {
            list.add(getResources().getString(R.string.tire_inner_right));
        }
        if (list.size() != 0) {
            return String.join("、", list);
        }
        return null;
    }

    /**
     * ※※※※※
     * 残燃料状態に応じてメッセージを表示する
     * @param fuelLevel 燃料残量
     */
    private void _changeDisplayByFuelLevel(Double fuelLevel) {
        int fuel = fuelLevel.intValue();
        // doubleをintに変換したことで、同じ整数値が最大10回呼ばれるため、前回の値と比較をする
        // ex.30.9%～30.0%までのdouble値がすべて30%(int)となる
        if(fuel == prevFuelLevel) {
            return;
        }
        prevFuelLevel = fuel;
        if(fuelLvThreshold.contains(fuel)){
            // 30%を切ったらGSを探すように通知する
            String str1 = "燃料の残量が" + fuel + "%になりました。";
            String str2 = (fuel <= 30) ? "そろそろガソリンスタンドを探しましょう。" : "";
            UISettings ui = new UISettings(UISettings.EventType.Fuel, ICON_FUEL,null,str1,str2,null,null);
            _addChangeUIQueue(ui);
        }
    }

    /**
     * ※※※※※
     * ヘッドランプステータスの状態変更通知があった際の処理
     * @param lampStatus OnVehicleData()で取得したnotification.getHeadLampStatus()
     */
    private void _changeDisplayByHeadLampStatus(HeadLampStatus lampStatus) {
        AmbientLightStatus lightStatus = lampStatus.getAmbientLightStatus();
        if (_checkAmbientStatusIsNight(lightStatus) && isHeadlightTurnOn) {
            if (! _checkAnyHeadLightIsOn(lampStatus)){
                UISettings ui = new UISettings(UISettings.EventType.Headlight, ICON_HEADLIGHT, null,
                        "ヘッドライトが点灯していませんが大丈夫ですか？","安全運転を心がけてください。",null, null);
                _addChangeUIQueue(ui);
            }
        } else if (lightStatus.equals(AmbientLightStatus.DAY) && isHeadlightTurnOff) {
            if(_checkAnyHeadLightIsOn(lampStatus)) {
                UISettings ui = new UISettings(UISettings.EventType.Headlight, ICON_HEADLIGHT, null,
                        "ヘッドライトが点灯していませんか？","まだ明るいようなので、消灯してはいかがでしょうか？",null, null);
                _addChangeUIQueue(ui);
            }
        }
    }

    /**
     * ※※※※※
     * 周辺光センサーの値が夜(Twilight_1～4、Night)かどうか判定する
     * @param lightStatus AmbientLightStatus
     * @return 周辺光が夜に該当する場合Trueを返却する
     */
    private boolean _checkAmbientStatusIsNight(AmbientLightStatus lightStatus) {
        return (lightStatus.equals(AmbientLightStatus.TWILIGHT_1) ||
                lightStatus.equals(AmbientLightStatus.TWILIGHT_2) ||
                lightStatus.equals(AmbientLightStatus.TWILIGHT_3) ||
                lightStatus.equals(AmbientLightStatus.TWILIGHT_4) ||
                lightStatus.equals(AmbientLightStatus.NIGHT));
    }

    /**
     * ※※※※※
     * ハイビームかロービームのいずれかが点灯状態にあるか確認する
     * @param lampStatus HeadLampStatus
     * @return いずれかが点灯状態の場合Trueを返却する
     */
    private boolean _checkAnyHeadLightIsOn(HeadLampStatus lampStatus){
        return (lampStatus.getHighBeamsOn() || lampStatus.getLowBeamsOn());
    }

    /**
     * ※※※※※
     * HU上にデフォルト画面用のアイコン、文字を表示するように設定する
     */
    private void _showDefaultUI() {
        UISettings ui = new UISettings(UISettings.EventType.Default,
                PIC_CHARACTER,null,
                "デフォルト画面になります。",null,
                null,null);
        _addChangeUIQueue(ui);
    }

    /**
     * ※※※※※
     * HUに表示したい画像、テキスト情報をキューに格納する
     * @param ui UISettings 表示したい情報が格納されたUISettings
     */
    private void _addChangeUIQueue(UISettings ui){
        uiQueue.offer(ui);
        _checkNextQueue();
    }

    /**
     * ※※※※※
     * 画面の表示コントロールをするためのタイマー機能。
     * 何らかの画面を表示した後、一定時間経過すると、デフォルト画面になるようにする。
     */
    private void _waitTimer() {
        isChangeUIWorked = false;
        if(isTimerWorked) {
            return;
        } else if(uiQueue.isEmpty()) {
            return;
        }
        isTimerWorked = true;
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                isTimerWorked = false;
                UISettings ui = uiQueue.poll();
                if(uiQueue.isEmpty()){
                    if (ui.getEventType().equals(UISettings.EventType.NotSupport)) {
                      // サポートしている機能がないので、画面をロックする
                      Log.w(LOG_TAG,"利用中の車両で、本アプリケーションがサポート可能な機能がありません。");
                    } else if (! ui.getEventType().equals(UISettings.EventType.Default)) {
                        _showDefaultUI();
                    }
                } else {
                    _checkNextQueue();
                }
            }
        };
        int delayTime = TIMER_DELAY_MS;
        UISettings ui = uiQueue.peek();
        if(ui.getEventType().equals(UISettings.EventType.Default)) {
            delayTime = 0;
        }
        Timer uiChangeTimer = new Timer(false);
        uiChangeTimer.schedule(task, delayTime);
    }

    /**
     * ※※※※※
     * キューに格納されている画面情報を元に(表示可能なタイミングになったら)HUに表示リクエストを出す
     */
    private synchronized void _checkNextQueue() {
        // 次の画面変更があれば、一定時間経過後に表示するようにする
        if (!uiQueue.isEmpty() && ! isTimerWorked && ! isChangeUIWorked) {

            isChangeUIWorked = true;
            int id = CorrelationIdGenerator.generateId();
            uiQueue.peek().setId(id);
            UISettings que = uiQueue.peek();
            sdlManager.getScreenManager().beginTransaction();

            if(que.getText1() != null) {
                sdlManager.getScreenManager().setTextField1(que.getText1());
            }
            if(que.getText2() != null) {
                //show.setMainField2(que.getText2());
                sdlManager.getScreenManager().setTextField2(que.getText2());
            }
            if(que.getText3() != null) {
                sdlManager.getScreenManager().setTextField3(que.getText3());
            }
            if(que.getText4() != null) {
                sdlManager.getScreenManager().setTextField4(que.getText4());
            }
            if(que.getImage1() != null) {
                sdlManager.getScreenManager().setPrimaryGraphic(artWorks.get(que.getImage1()));
            }
            if(que.getImage2() != null) {
                sdlManager.getScreenManager().setSecondaryGraphic(artWorks.get(que.getImage2()));
            }
            // TTS
            if(que.getEventType() != UISettings.EventType.Default &&
                    que.getEventType() != UISettings.EventType.Greeting) {
                ttsStandby.put(que.getId(), que.getText1());
            }
            //show.setCorrelationID(que.getId());
            //sdlManager.sendRPC(show);
            sdlManager.getScreenManager().commit(new CompletionListener() {
                @Override
                public void onComplete(boolean success) {
                    int reqId = uiQueue.peek().getId();
                    if(success){
                        if(ttsStandby.containsKey(reqId)) {
                            _ttsSpeech(ttsStandby.get(reqId), String.valueOf(reqId));
                        }
                    }
                    _waitTimer();
                }
            });
            // response onShowResponse() called
        }
    }

    /**
     * TTS：initialize
     * @param status init status
     */
    @Override
    public void onInit(int status) {
        if (TextToSpeech.SUCCESS == status) {
            Locale locale = Locale.JAPAN;
            if(tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                isTtsEnabled = true;
                tts.setLanguage(locale);
            } else {
                Log.w(LOG_TAG,"言語設定に日本語を選択できませんでした");
                isTtsEnabled = true;
            }
        } else {
            isTtsEnabled = false;
        }
    }

    /**
     * ※※※※※
     * TTS：指定した文字列を読み上げさせる
     * @param str TTSで読み上げさせたい文字列
     * @param utteranceId リクエスト用の一意のID値(null可)
     */
    private void _ttsSpeech(String str, @Nullable String utteranceId) {
        if(isTtsEnabled && tts != null) {
            // SDLのTTSには読み上げキャンセル機能が見当たらないので、デモ用にはスマホ側のTTSを利用する
            boolean mobileTTS = true;
            if(mobileTTS) {
                // スマホ側のTTSを利用して読み上げさせる
                if (tts.isSpeaking()) {
                    tts.stop();
                }
                tts.setSpeechRate(1.2f);
                tts.setPitch(1.0f);
                if (utteranceId == null) {
                    utteranceId = String.valueOf(CorrelationIdGenerator.generateId());
                }
                tts.speak(str, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            } else {
                // ヘッドユニット側のTTSを利用して読み上げさせる
                sdlManager.sendRPC(new Speak(TTSChunkFactory.createSimpleTTSChunks(str)));
            }
        }
    }

}
