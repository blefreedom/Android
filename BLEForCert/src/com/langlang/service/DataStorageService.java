package com.langlang.service;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.json.JSONArray;
import org.json.JSONObject;

import com.langlang.ble.AccelAnalyzer;
import com.langlang.ble.DataErrorException;
import com.langlang.ble.DetectC7StateMachine;
import com.langlang.ble.InvalidStepCheckStateMachine;
import com.langlang.ble.StepCountManager;
import com.langlang.cutils.ECGHeartRateCalculator;
import com.langlang.cutils.GlobalAccelCalculator;
import com.langlang.cutils.RespCalculator;
import com.langlang.data.AccelVector;
import com.langlang.data.ECGResult;
import com.langlang.data.StepCaloriesEntry;
import com.langlang.data.StressLevelItem;
import com.langlang.data.ValueEntry;
import com.langlang.global.Client;
import com.langlang.global.GlobalStatus;
import com.langlang.global.UserInfo;

import com.langlang.utils.BreathFilter;
import com.langlang.utils.CompressedFilePool;
import com.langlang.utils.DateUtil;
import com.langlang.utils.EventLogger;
import com.langlang.utils.Filter;
import com.langlang.utils.MiscUtils;
import com.langlang.utils.Program;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

public class DataStorageService extends Service {
	public static final int DATA_FRAME_UNKNOWN = -1;
	public static final int DATA_FRAME_60 = 0; 
//	public static final int DATA_FRAME_90 = 1;
	
	public static final int DATA_FRAME_61 = 1;
	public static final int DATA_FRAME_62 = 2;
	public static final int DATA_FRAME_63 = 3;
	public static final int DATA_FRAME_64 = 4;
	public static final int DATA_FRAME_65 = 5;
	public static final int DATA_FRAME_69 = 9;
	
	public static final String ACTION_ACCELERATED_X="action_accelerated_x";
	public static final String ACTION_ACCELERATED_Y="action_accelerated_y";
	public static final String ACTION_ACCELERATED_Z="action_accelerated_z";
	public static final String ACTION_ACCELERATED_X_DATA="action_accelerated_x_data";
	public static final String ACTION_ACCELERATED_Y_DATA="action_accelerated_y_data";
	public static final String ACTION_ACCELERATED_Z_DATA="action_accelerated_z_date";
	
	List<ValueEntry> ecgList = new ArrayList<ValueEntry>(20000);
	List<ValueEntry> accelerationXlist = new ArrayList<ValueEntry>(1000);
	List<ValueEntry> accelerationYlist = new ArrayList<ValueEntry>(1000);
	List<ValueEntry> accelerationZlist = new ArrayList<ValueEntry>(1000);
	List<ValueEntry> temDatalist = new ArrayList<ValueEntry>(1000);
	List<ValueEntry> tumbleDatalist = new ArrayList<ValueEntry>(1000);
	List<ValueEntry> respList = new ArrayList<ValueEntry>(5000);
	
	List<ValueEntry> hteWarningList = new ArrayList<ValueEntry>();
	
//	LastUploadManager lastUploadManager = new LastUploadManager(this);
//	UploadTaskManager uploadTaskManager = new UploadTaskManager(this);
	
	List<AccelVector> accelList = new ArrayList<AccelVector>(1000);
	List<ValueEntry> voltageList = new ArrayList<ValueEntry>(1000);
	
	List<StepCaloriesEntry> stepCaloriesList = new ArrayList<StepCaloriesEntry>(1000);
	
	List<ValueEntry> heartRateList = new ArrayList<ValueEntry>(1000);
	
//	WarningTempManager warningTempManager = new WarningTempManager(this);
	
//	MinuteStressLevelManager minuteStressLevelManager = new MinuteStressLevelManager(this);
//	MinuteECGResultManager minuteECGResultManager = new MinuteECGResultManager(this);
	
	Filter filter = new Filter();
	ECGHeartRateCalculator ecgHeartRateCal;
	
	BreathFilter breathFilter = new BreathFilter();
	RespCalculator respCalculator;

	public final static String ACTION_START_UPLOAD = "com.langlang.android.bluetooth.le.ACTION_START_UPLOAD";
	public final static String ACTION_CLEAR_STORAGE = "com.langlang.android.bluetooth.le.ACTION_CLEAR_STORAGE";
	public final static String CLEAR_UID = "com.langlang.android.bluetooth.le.CLEAR_UID";
	
	public final static String ACTION_ALERT_SD_STATUS 
							= "com.langlang.android.bluetooth.le.ACTION_ALERT_SD_STATUS";
	public final static String ALERT_SD_STATUS_LEVEL 
							= "com.langlang.android.bluetooth.le.ALERT_SD_STATUS_LEVEL";

	public final static String ACTION_RESET_STEP_COUNTER 
								= "com.langlang.android.bluetooth.le.ACTION_RESET_STEP_COUNTER";

	public final static String ACTION_UPDATE_STEP_AND_CALORIES
								= "com.langlang.android.bluetooth.le.ACTION_UPDATE_STEP_AND_CALORIES";
	public final static String STEP_COUNT = "com.langlang.android.bluetooth.le.STEP_COUNT";
	public final static String CALORIES = "com.langlang.android.bluetooth.le.CALORIES";
	public final static String ACTION_CURRENT_STATE_CHANGE="action_current_state_change";
	public final static String ACTION_TEMP="action_temp";

	public final static String ACTION_UPDATE_ECG_HEART_RATE
		= "com.langlang.android.bluetooth.le.ACTION_UPDATE_ECG_HEART_RATE";
	public final static String ECG_HEART_RATE = "com.langlang.android.bluetooth.le.ECG_HEART_RATE";
	
	public final static String ACTION_QUERY_ECG_HEART_RATE 
					= "com.langlang.android.bluetooth.le.ACTION_QUERY_ECG_HEART_RATE";
	
	public final static String ACTION_ECG_DATA_AVAILABLE 
					= "com.langlang.android.bluetooth.le.ACTION_ECG_DATA_AVAILABLE";
	public final static String ACTION_RESP_DATA_AVAILABLE 
					= "com.langlang.android.bluetooth.le.ACTION_RESP_DATA_AVAILABLE";	
	
	public final static String ACTION_POSTURE_DATA_AVAILABLE
					= "com.langlang.android.bluetooth.le.ACTION_POSTURE_DATA_AVAILABLE";
	public final static String ACTION_GAIT_DATA_AVAILABLE
					= "com.langlang.android.bluetooth.le.ACTION_GAIT_DATA_AVAILABLE";
	
	public final static String ACTION_STRESS_DATA_AVAILABLE
					= "com.langlang.android.bluetooth.le.ACTION_STRESS_DATA_AVAILABLE";
	public final static String STRESS_DATA = "com.langlang.android.bluetooth.le.STRESS_DATA";
	
	public final static String POSTURE_DATA = "com.langlang.android.bluetooth.le.POSTURE_DATA";
	
	private static final String ACTION_ALARM_GET_STEP_COUNT_SNAPSHOT 
					=  "com.langlang.android.bluetooth.le.ACTION_ALARM_GET_STEP_COUNT_SNAPSHOT";
	
	public static final String ACTION_TUMBLE_HAPPENED
					=  "com.langlang.android.bluetooth.le.ACTION_TUMBLE_HAPPENED";
//	public static final String TUMBLE_TIME
//					=  "com.langlang.android.bluetooth.le.TUMBLE_TIME";
	
	private static final String ACTION_ALARM_JUDGE_TUMBLE
					=  "com.langlang.android.bluetooth.le.ACTION_ALARM_JUDGE_TUMBLE";
	
	private static final String ACTION_SAVE_ALARM_TO_FILE
									= "com.langlang.android.bluetooth.le.ACTION_SAVE_ALARM_TO_FILE";
	private static final String ALARM_TYPE
									= "com.langlang.android.bluetooth.le.ALARM_TYPE";
	private static final String ALARM_TIME
									= "com.langlang.android.bluetooth.le.ALARM_TIME";
	private static final String ALARM_VALUE
									= "com.langlang.android.bluetooth.le.ALARM_VALUE";
	
	public final static String ACTION_NOTIFY_INVALID_ECG
									= "com.langlang.android.bluetooth.le.ACTION_NOTIFY_INVALID_ECG";
	
	public final static String ACTION_TEMPERATURE_DATA_AVAILABLE
									= "com.langlang.android.bluetooth.le.ACTION_TEMPERATURE_DATA_AVAILABLE";
	
	private long mPrevStepCountTimestamp = 0;
	private int mPrevStepCount = 0;
	private boolean isFirstStepCountSnapshot = true;
		
	public byte bPreFrame60SequenceNO = (byte)0xff;
	public boolean isFirstPoint = false;
	
	private Object lockPersist = new Object();
//	public long numberOfItem = 0;
	
//	public boolean isFirstPointOf90 = false;
//	public byte bPreFrame90SequenceNO = (byte)0xff;
	
//	public byte[] tmpByte = new byte[20];
	
//	public int totalFrame60 = 0;
//	public int totalFrame60fixed = 0;
		
	int countFrame60 = 0;
	int countFrame90 = 0;
	int step_state=0;
	// 换行写入
	String nextLine = System.getProperty("line.separator");
	private byte Current_state;
	private byte Prev_state = 0x20;
	
	private boolean isFirstX = true;
	private boolean isFirstY = true;
	private boolean isFirstZ = true;
	
	private byte prevXSeqNo = 0x00;
	private byte prevYSeqNo = 0x00;
	private byte prevZSeqNo = 0x00;
	
	
	AccelAnalyzer accelAnalyzer = new AccelAnalyzer();
	
	private int mGender = 0;
	private int mAge = 0;
	private int mHeight = 0;
	private int mWeight = 0;
	private int mLimitHeartUp = 0;
	private int mLimitHeartDown = 0;
	
	private static final String ACTION_ALARM_GET_STRESS_LEVEL_ITEM 
					= "com.langlang.android.bluetooth.le.ACTION_ALARM_GET_STRESS_LEVEL_ITEM";
//	WarningHteManager warningHteManager = new WarningHteManager(this);
//	WarningTumbleManager warningTumbleManager = new WarningTumbleManager(this);
	
//	private byte prevStepSeqNo = 0x00;
//	private boolean isFirstStepCount = true;
	
	public static final String ACTION_STAND_STILL_DATA_AVAILABLE = "com.langlang.android.bluetooth.le.ACTION_GAIT_DATA_AVAILABLE";
	public static final String STAND_STILL_POSTURE = "com.langlang.android.bluetooth.le.STAND_STILL_POSTURE";
	public static final String STAND_STILL_TIME = "com.langlang.android.bluetooth.le.STAND_STILL_TIME";

	public static final int STAND_STILL_STAND = 0;
	public static final int STAND_STILL_SIT = 1;
	public static final int STAND_STILL_UNKNOWN = -1;
	public int mStandStill = STAND_STILL_UNKNOWN;
	private long mStandStillTime = 0;
	private Date mPrevStandStillDate = null;
	private boolean mIsFirstStandStill = true;
	
	private int mStepCountSnapshot = 0;
	private boolean mIsJudgeTumbleTimerStarted = false;
	
	private CompressedFilePool mCompressedFilePool = new CompressedFilePool();
	
	private String mUID = null;
	
	private int mCountFrameFromConnected = 0;
	
	private boolean mIsBleConnected = false;
	private final static int SKIP_FRAMES = 100;
	
	private final static int BACKWARD_TIME = 1000 * 60;
	
	private int mCountInvalidEcg = 0;
	
	private StepCountManager mStepCountManager;
	
	private DetectC7StateMachine mDetectC7StateMachine;
	
	private Queue<Integer> queueHte = new LinkedList<Integer>();
	
	private SharedPreferences spTumbleFlag;
	
	private final static String TUMBLE_FLAG = "TUMBLE_FLAG";
	private final static int TUMBLE_FLAG_ON = 1;
	private final static String TUMBLE_TIME = "TUMBLE_TIME";
	
	private InvalidStepCheckStateMachine stepCheckStateMachine;
	
	// ECG / Heart Rate switch mode
	public static final int MODE_ECG_ECG = 1;			// ECG
	public static final int MODE_ECG_HEART_RATE = 2;	// HEART RATE
	
	private int mLastEcgMode = MODE_ECG_ECG;
	private int mCurrentEcgMode = MODE_ECG_ECG;
	
	private Queue<Integer> historyStepCounts = new LinkedList<Integer>();
	
	private int mLastRecordedStep = 0;
	private Date mLastRecordedStepDate = null;
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
//		android.os.Process.setThreadPriority(19);
		super.onCreate();
		 acquireWakeLock();
//		reastOncreat();
		ecgHeartRateCal = new ECGHeartRateCalculator();
		filter.reset();
		isFirstX = true;
		isFirstY = true;
		isFirstZ = true;
		
		prevXSeqNo = 0x00;
		prevYSeqNo = 0x00;
		prevZSeqNo = 0x00;
		
		respCalculator = new RespCalculator();
		breathFilter.reset();
//		ecgHeartRateCal.setECGOFiles(null, null, 
//									null, 
//									Program.getSDPath() + File.separator + "ecgDebug_angle.csv");
		isFirstPoint = true;
		
		mStepCountManager = new StepCountManager(this);
		
		mCountFrameFromConnected = 0;
		mIsBleConnected = false;
		
		mCountInvalidEcg = 0;
		
		queueHte.clear();
		
//		ecgNotificationTime = 0;
		
		initEcgMode();
		
		accelAnalyzer.reset();
		mCompressedFilePool.reset();
		
		getUserInfo();
		System.out.println("action DataStorageService ecgHeartRateCal setGender:" + mGender);
		System.out.println("action DataStorageService ecgHeartRateCal setAge:" + mAge);
//		ecgHeartRateCal.setGender((char) mGender);
		if (mGender == 1) { // men
			ecgHeartRateCal.setGender((char) 1);
		} else if (mGender == 0) { // women
			ecgHeartRateCal.setGender((char) 2);
		} else {
			ecgHeartRateCal.setGender((char) 0);
		}
			
		ecgHeartRateCal.setGender((char) mGender);
		ecgHeartRateCal.setAge((short) mAge);
		
		GlobalAccelCalculator.getInstance().setHeight((short) mHeight);
		GlobalAccelCalculator.getInstance().setWeight((short) mWeight);
		
		GlobalAccelCalculator.getInstance().setAccelInitXYZ(1002, -78, -164);
		
		this.registerReceiver(StorageReceiver, makeGattUpdateIntentFilter());
		
		startGetStressLevelTimer();
		startGetStepCountSnapshotTimer();
		
		mDetectC7StateMachine = new DetectC7StateMachine();
		mDetectC7StateMachine.reset();
		
		spTumbleFlag = this.getSharedPreferences(TUMBLE_FLAG, MODE_PRIVATE);
		
		stepCheckStateMachine = new InvalidStepCheckStateMachine();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		// TODO Auto-generated method stub
		super.onStart(intent, startId);
	}
	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		releaseWarkLock();
		System.out.println("action onDestroy------- datastorage");
		stopGetStressLevelTimer();
		stopGetStepCountSnapshotTimer();
	}
	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter
				.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		intentFilter.addAction(ACTION_RESET_STEP_COUNTER);
		intentFilter.addAction(ACTION_QUERY_ECG_HEART_RATE);
		intentFilter.addAction(ACTION_ALARM_GET_STRESS_LEVEL_ITEM);
		intentFilter.addAction(ACTION_ALARM_GET_STEP_COUNT_SNAPSHOT);
		intentFilter.addAction(ACTION_ALARM_JUDGE_TUMBLE);
		return intentFilter;
	}

	private final BroadcastReceiver StorageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				
				mCountFrameFromConnected = 0;
				mIsBleConnected = true;

				queueHte.clear();
				
				step_state = 0;
				
				GlobalStatus.getInstance().setCurrentStep(mStepCountManager.getLastStepCount());
				GlobalStatus.getInstance().setCalories(GlobalAccelCalculator.getInstance().getCalories());

				stepCheckStateMachine.reset();
				
				historyStepCounts.clear();
				
				clearLastRecordedStep();
				
			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED
					.equals(action)) {
				
				mIsBleConnected = false;
				step_state = 0;
				
				if (UserInfo.isInDebugMode()) {
				} else {
					if (mCompressedFilePool.getMinuteData() != null) {
						persistData(mCompressedFilePool.getMinuteData());
						
						mCompressedFilePool.flushCompressFiles();
					}
				}
				
				if (stepCheckStateMachine != null) {
					if ((InvalidStepCheckStateMachine.S_FIRST_INVALID 
									== stepCheckStateMachine.getCurrentState())
					  ||(InvalidStepCheckStateMachine.S_INVALID
							  		== stepCheckStateMachine.getCurrentState())) {
						
						int stepCountBase = mStepCountManager.getStepCountBase();
						int subInvalidCount = stepCheckStateMachine.getSubInvalidStep();
						
						mStepCountManager.setStepCountBase(stepCountBase - subInvalidCount);
					}
				}
				
			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED
					.equals(action)) {

			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
//				Date traceStart = new Date();
				
				if (mCountFrameFromConnected >= 10000) { }
				else { mCountFrameFromConnected++; }
				
				if (!mIsBleConnected) return; //屏蔽掉蓝牙断开之后的数据
				
				// final Intent intents = new Intent(action);
				final byte[] data = intent.getByteArrayExtra("langlangdata");
				// --- System.out.println("action dataStorageService data-----"+
				// Arrays.toString(data));
				
				final StringBuilder stringBuilder = new StringBuilder(data.length);
				for (byte byteChar : data) {
					stringBuilder.append(String.format("%02X ", byteChar));
				}
				System.out.println("action received ble data:[" + stringBuilder.toString() + "]");
				
//				if (UserInfo.getIntance().getUserData().getLogin_mode() == 1) {
//					// 非登录用户不判断UID有效性
//					mUID = null;
//				}
//				else {
//					mUID = UserInfo.getIntance().getUserData().getMy_name();
//					if (mUID == null || mUID.length() <= 0) {
//						System.out.println("action DataStorageService mUID is empty");
//						return;
//					}
//				}
				
				if (data != null && data.length > 0) {
					// ---System.out.println("action: data[data != null && data.length > 0]");

					int dataFrameType = DATA_FRAME_UNKNOWN;
					int intValueOfByte = Program.byteToInt(data[3]);
					if (intValueOfByte == 96) {
						dataFrameType = DATA_FRAME_60;
					}  
					if (intValueOfByte == 97) {
						dataFrameType = DATA_FRAME_61;
					}
					if (intValueOfByte == 98) {
						dataFrameType = DATA_FRAME_62;
					}
					if (intValueOfByte == 99) {
						dataFrameType = DATA_FRAME_63;
					}
					if (intValueOfByte == 100) {
						dataFrameType = DATA_FRAME_64;
					}
					if (intValueOfByte == 101) {
						dataFrameType = DATA_FRAME_65;
					}
					if (intValueOfByte == 105) {
						dataFrameType = DATA_FRAME_69;
					}
					
					if (dataFrameType == DATA_FRAME_60 || dataFrameType == DATA_FRAME_61
								|| dataFrameType == DATA_FRAME_62 || dataFrameType == DATA_FRAME_63
								|| dataFrameType == DATA_FRAME_64 || dataFrameType == DATA_FRAME_65
								|| dataFrameType == DATA_FRAME_69) {
						
					} else {
						return;
					}
					
					if (dataFrameType == DATA_FRAME_69) {
						mCurrentEcgMode = MODE_ECG_HEART_RATE;
						
						final Intent notifyDataAlive = new Intent(BluetoothLeService.ACTION_GATT_DATA_ALIVE);
						sendBroadcast(notifyDataAlive);
					}
					else {
						mCurrentEcgMode = MODE_ECG_ECG;
					}
					checkEcgModeChanged();
					
					Date now = new Date();
					// -------------------------------------------
					if (dataFrameType != DATA_FRAME_69) {
						int[] origECGData = Program.getECGValues(data);
						
						mDetectC7StateMachine.consume(Program.isEcgC7(data));
						
						int[] ECGData = new int[origECGData.length * 2];
						for (int i = 0; i < origECGData.length; i++) {
							ECGData[i * 2] = origECGData[i];
							ECGData[i * 2 + 1] = origECGData[i];
						}
						// ---System.out.println("action StorageService ecgdata "+Arrays.toString(ECGData));
						int[] origRespData = Program.getResp(data);
						int[] respData = new int[origRespData.length * 2];
						for (int i = 0; i < origRespData.length; i++) {
							respData[i * 2] = origRespData[i];
							respData[i * 2 + 1] = origRespData[i];
						}
						
						if (isFirstPoint) {
							isFirstPoint = false;
						} else {
								int uintValOfFrame60SeqNo = Program.convertByteToUnsignedInt(data[4]);
								int uintValOfPreFrame60SeqNo = Program.convertByteToUnsignedInt(bPreFrame60SequenceNO);
								
								if ((uintValOfFrame60SeqNo - uintValOfPreFrame60SeqNo) > 1) {
									System.out.println("action received ble data frame error detected0.[" 
													+ String.format("%d %d", uintValOfFrame60SeqNo, uintValOfPreFrame60SeqNo) + "],[" 
													+ String.format("%02X %02X", data[4], bPreFrame60SequenceNO) + "]");
									
									// fix the lost data
									int lostFrame = uintValOfFrame60SeqNo - uintValOfPreFrame60SeqNo - 1;
									
									lostFrame = lostFrame * 2;
									
									int[] lostECGData = new int[lostFrame * 5];
									for (int i = 0; i < lostFrame * 5; i++) {
										lostECGData[i] = ECGData[0];
									}
									
									int[] lostRespData = new int[lostFrame * 2];
									for (int i = 0; i < lostFrame * 2; i++) {
										lostRespData[i] = respData[0];
									}
									
									System.out.println("action ACTION_ECG_DATA_AVAILABLE ACTION_ECG_DATA_AVAILABLE aaa lost1[" 
													+ Arrays.toString(lostECGData) + "]");
									notifyDataAvailable(ACTION_ECG_DATA_AVAILABLE, "ECGData", lostECGData);
									notifyDataAvailable(ACTION_RESP_DATA_AVAILABLE, "RespData", lostRespData);
									
									if (!mDetectC7StateMachine.isInMaskStatus()) {
//									addDataToList(ecgList, lostECGData);
//									addDataToList(respList, lostRespData);
									}
									else {
//										int[] tmpECGData = new int[lostECGData.length];
//										for (int i = 0; i < lostECGData.length; i++) {
//											tmpECGData[i] = Program.INVALID_ECG_VALUE;
//										}
//										addDataToList(ecgList, tmpECGData);
//										
//										int[] tmpResp = new int[lostRespData.length];
//										for (int i = 0; i < lostRespData.length; i++) {
//											tmpResp[i] = Program.INVALID_ECG_VALUE;
//										}
//										addDataToList(respList, tmpResp);
									}

									// ---添加滤波器---
									for (int i = 0; i < lostECGData.length; i++) {
										try {
											filter.addData(lostECGData[i]);
										} catch (Exception e) {
											e.printStackTrace();
										}
									}									
									for (int i = 0; i < lostECGData.length; i++) {
										Date ecgDate = new Date();						
										try {
											ecgHeartRateCal.pushECGData(ecgDate.getTime(), filter.getOne());
//											checkECGCalculateDone();
//											checkECGCalculateDone(mUID);
											checkECGCalculateDone(mUID, Program.isValidEcg(data));
										} catch (Exception e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
									}								
//									for (int i = 0; i < lostECGData.length; i++) {
//										Date ecgDate = new Date();						
//										ecgHeartRateCal.pushECGData(ecgDate.getTime(), lostECGData[i]);
//									}
									
									for (int i = 0; i < lostRespData.length; i++) {
										try {
											breathFilter.addData(lostRespData[i]);
										} catch (Exception e) {
											e.printStackTrace();
										}
									}
									for (int i = 0; i < lostRespData.length; i++) {
										if (breathFilter.canGetOne()) {
											Date respDate = new Date();						
											try {
												respCalculator.pushRespData(respDate.getTime(), breathFilter.getOne());
											} catch (Exception e) {
												e.printStackTrace();
											}
										} else {
											break;
										}
									}
									
//									totalFrame60fixed += lostFrame;
									
								} else if (uintValOfFrame60SeqNo < uintValOfPreFrame60SeqNo) {
									if (uintValOfFrame60SeqNo > 10) return;
									
									
									System.out.println("action received ble data frame error detected1.[" 
											+ String.format("%d %d", uintValOfFrame60SeqNo, uintValOfPreFrame60SeqNo) + "],[" 
											+ String.format("%02X %02X", data[4], bPreFrame60SequenceNO) + "]");
							
									// fix the lost data
									int lostFrame = (255 - uintValOfPreFrame60SeqNo) + uintValOfFrame60SeqNo;
									
									lostFrame = lostFrame * 2;
									
									int[] lostECGData = new int[lostFrame * 5];
									for (int i = 0; i < lostFrame * 5; i++) {
										lostECGData[i] = ECGData[0];
									}
									
									int[] lostRespData = new int[lostFrame * 2];
									for (int i = 0; i < lostFrame * 2; i++) {
										lostRespData[i] = respData[0];
									}
									
									System.out.println("action ACTION_ECG_DATA_AVAILABLE ACTION_ECG_DATA_AVAILABLE aaa lost2[" 
											+ Arrays.toString(lostECGData) + "]");
									
									notifyDataAvailable(ACTION_ECG_DATA_AVAILABLE, "ECGData", lostECGData);
									notifyDataAvailable(ACTION_RESP_DATA_AVAILABLE, "RespData", lostRespData);
									
									if (!mDetectC7StateMachine.isInMaskStatus()) {
//									addDataToList(ecgList, lostECGData);
//									addDataToList(respList, lostRespData);
									}
									else {
//										int[] tmpECGData = new int[lostECGData.length];
//										for (int i = 0; i < lostECGData.length; i++) {
//											tmpECGData[i] = Program.INVALID_ECG_VALUE;
//										}
//										addDataToList(ecgList, tmpECGData);
//										
//										int[] tmpResp = new int[lostRespData.length];
//										for (int i = 0; i < lostRespData.length; i++) {
//											tmpResp[i] = Program.INVALID_ECG_VALUE;
//										}
//										addDataToList(respList, tmpResp);
									}									
									
									// ---添加滤波器---
									for (int i = 0; i < lostECGData.length; i++) {
										try {
											filter.addData(lostECGData[i]);
										} catch (Exception e) {
											e.printStackTrace();
										}
									}									
									for (int i = 0; i < lostECGData.length; i++) {
										Date ecgDate = new Date();						
										try {
											ecgHeartRateCal.pushECGData(ecgDate.getTime(), filter.getOne());
//											checkECGCalculateDone();
//											checkECGCalculateDone(mUID);
											checkECGCalculateDone(mUID, Program.isValidEcg(data));
										} catch (Exception e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
									}
//									for (int i = 0; i < lostECGData.length; i++) {
//										Date ecgDate = new Date();						
//										ecgHeartRateCal.pushECGData(ecgDate.getTime(), lostECGData[i]);
//									}
									for (int i = 0; i < lostRespData.length; i++) {
										try {
											breathFilter.addData(lostRespData[i]);
										} catch (Exception e) {
											e.printStackTrace();
										}
									}
									for (int i = 0; i < lostRespData.length; i++) {
										if (breathFilter.canGetOne()) {
											Date respDate = new Date();						
											try {
												respCalculator.pushRespData(respDate.getTime(), breathFilter.getOne());
											} catch (Exception e) {
												e.printStackTrace();
											}
										} else {
											break;
										}
									}
									
//									totalFrame60fixed += lostFrame;									
								}
						}						
						bPreFrame60SequenceNO =  data[4];
						
						// ---添加滤波器---
						for (int i = 0; i < ECGData.length; i++) {
							try {
								filter.addData(ECGData[i]);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						for (int i = 0; i < ECGData.length; i++) {
							if (filter.canGetOne()) {
								Date ecgDate = new Date();						
								try {
									ecgHeartRateCal.pushECGData(ecgDate.getTime(), filter.getOne());
//									checkECGCalculateDone();
//									checkECGCalculateDone(mUID);
									checkECGCalculateDone(mUID, Program.isValidEcg(data));
								} catch (Exception e) {
									e.printStackTrace();
								}
							} else {
								break;
							}
						}						
//						for (int i = 0; i < ECGData.length; i++) {
//							Date ecgDate = new Date();						
//							ecgHeartRateCal.pushECGData(ecgDate.getTime(), ECGData[i]);
//						}
						for (int i = 0; i < respData.length; i++) {
							try {
								breathFilter.addData(respData[i]);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						for (int i = 0; i < respData.length; i++) {
							if (breathFilter.canGetOne()) {
								Date respDate = new Date();						
								try {
									respCalculator.pushRespData(respDate.getTime(), breathFilter.getOne());
								} catch (Exception e) {
									e.printStackTrace();
								}
							} else {
								break;
							}
						}

						System.out.println("action ACTION_ECG_DATA_AVAILABLE ACTION_ECG_DATA_AVAILABLE aaa data[" 
								+ Arrays.toString(ECGData) + "]");
						
						notifyDataAvailable(ACTION_ECG_DATA_AVAILABLE, "ECGData", ECGData);
						notifyDataAvailable(ACTION_RESP_DATA_AVAILABLE, "RespData", respData);

						if (!mDetectC7StateMachine.isInMaskStatus()) {
//						addDataToList(ecgList, ECGData);
//						addDataToList(respList, respData);
						}
						else {
//							int[] tmpECGData = new int[ECGData.length];
//							for (int i = 0; i < ECGData.length; i++) {
//								tmpECGData[i] = Program.INVALID_ECG_VALUE;
//							}
//							addDataToList(ecgList, tmpECGData);
//							
//							int[] tmpResp = new int[respData.length];
//							for (int i = 0; i < respData.length; i++) {
//								tmpResp[i] = Program.INVALID_ECG_VALUE;
//							}
//							addDataToList(respList, tmpResp);
						}
						
						
						System.out.println("action DataStorageService getRespRate:" + respCalculator.getRespRate());
						GlobalStatus.getInstance().setBreath(respCalculator.getRespRate());
					}
					
					if (dataFrameType == DATA_FRAME_69) {
					}
						
//						if (dataFrameType == DATA_FRAME_60) {
					if (dataFrameType == DATA_FRAME_60 || dataFrameType == DATA_FRAME_69) {
					}
						
					if (dataFrameType == DATA_FRAME_61) {
					}
						
					if (dataFrameType == DATA_FRAME_62) {
					}
						
					if (dataFrameType == DATA_FRAME_63) {
					}
						
					if (dataFrameType == DATA_FRAME_64 || dataFrameType == DATA_FRAME_69) {
					}
						
					if (dataFrameType == DATA_FRAME_65) {
						if (dataFrameType == DATA_FRAME_65) {
						int[] TEMPData = Program.getTemp(data);
						
						GlobalStatus.getInstance().setTemp(TEMPData[0] * 1.0f / 5);
//							addDataToLists(temDatalist, TEMPData[0]);
//						addDataToList(temDatalist, TEMPData);
						
						String macAddr = UserInfo.getIntance().getMacAddr();
						if (macAddr != null) {
							final Intent tempIntent = new Intent(ACTION_TEMPERATURE_DATA_AVAILABLE);
							tempIntent.putExtra("MAC", macAddr);
							
							if (!"78A504843639".equals(macAddr)) {
								tempIntent.putExtra("TEMP_VALUE", 15.5f);
							} else {
								tempIntent.putExtra("TEMP_VALUE", TEMPData[0] * 1.0f / 5);
							}
							sendBroadcast(tempIntent);
						}
						
						int[] voltageData = Program.getVoltageData(data);
						GlobalStatus.getInstance().setVoltage(voltageData[0] * 1.0f / 50);
//							addDataToLists(voltageList, voltageData[0]);
//						addDataToList(voltageList, voltageData);
						
						System.out.println("action DataStorage Temperature[" 
										+ TEMPData[0] + ","	+ TEMPData[0] * 1.0f / 5 
										+ "] voltage ["	+ voltageData[0] + "," 
										+ voltageData[0] * 1.0f / 50 + "]");
						}
//						else {
//							int[] TEMPData = Program.getTempByNewFrame(data);
//							
//							GlobalStatus.getInstance().setTemp(TEMPData[0] * 1.0f / 5);
////								addDataToLists(temDatalist, TEMPData[0]);
//							addDataToList(temDatalist, TEMPData);
//							
//							int[] voltageData = Program.getVoltageDataByNewFrame(data);
//							GlobalStatus.getInstance().setVoltage(voltageData[0] * 1.0f / 50);
////								addDataToLists(voltageList, voltageData[0]);
//							addDataToList(voltageList, voltageData);
//							
//							System.out.println("action DataStorage Temperature[" 
//											+ TEMPData[0] + ","	+ TEMPData[0] * 1.0f / 5 
//											+ "] voltage ["	+ voltageData[0] + "," 
//											+ voltageData[0] * 1.0f / 50 + "]");								
//						}
					}	
				}
			}
			else if (ACTION_RESET_STEP_COUNTER.equals(action)) {
			} else if (ACTION_QUERY_ECG_HEART_RATE.equals(action)) {
				int ecgHeartRate = ecgHeartRateCal.getECGHeartRate();
				System.out.println("action DataStorageService getECGHeartRate " + ecgHeartRate);
				
				final Intent ecgHteIntent = new Intent(ACTION_UPDATE_ECG_HEART_RATE);				
				ecgHteIntent.putExtra(DataStorageService.ECG_HEART_RATE, ecgHeartRateCal.getHeartRateRealtime());
				sendBroadcast(ecgHteIntent);
			}
			else if (ACTION_ALARM_GET_STRESS_LEVEL_ITEM.equals(action)) {
				StressLevelItem item = ecgHeartRateCal.getStressLevelItem();
				System.out.println("action DataStorageService StressLevelItem[" + item.toString() + "]");
				GlobalStatus.getInstance().appendStressLevelItem(item);
			}
			else if (ACTION_ALARM_GET_STEP_COUNT_SNAPSHOT.equals(action)) {
				if (isFirstStepCountSnapshot) {
					isFirstStepCountSnapshot = false;
					Date now = new Date();
					mPrevStepCountTimestamp = now.getTime();					
					mPrevStepCount = GlobalStatus.getInstance().getCurrentStep();
				} else {					
					Date now = new Date();
					long timestamp = now.getTime();
					int stepCount = GlobalStatus.getInstance().getCurrentStep();
					
					long deltaTime = timestamp - mPrevStepCountTimestamp;
					int deltaSteps 
									= GlobalStatus.getInstance().getCurrentStep() - mPrevStepCount;
					
					mPrevStepCountTimestamp = timestamp;
					mPrevStepCount = stepCount;
					System.out.println("action DataStorageService pushDeltaSteps[" 
											+ deltaTime + "," + deltaSteps + "]");
					GlobalAccelCalculator.getInstance().pushDeltaSteps(deltaTime, deltaSteps, stepCount);
				}
			}
			else if (ACTION_ALARM_JUDGE_TUMBLE.equals(action)) {
//				mIsJudgeTumbleTimerStarted = false;
//				
//				if (GlobalStatus.getInstance().getCurrentStep() == mStepCountSnapshot) {
//					Date nowDate = new Date();
////					warningTumbleManager.increase(Program.getMinuteDataByDate(nowDate));
//					String uid = intent.getStringExtra("UID");
////						warningTumbleManager.increase(Program.getMinuteDataByDate(nowDate), mUID);
//						warningTumbleManager.increase(Program.getMinuteDataByDate(nowDate), uid);
//		//							tmblStrList.add(new TumbleStrEntry(nowDate.getTime(), stringBuilder.toString()));
//						GlobalAccelCalculator.getInstance().setFreeFalling();
//						
//						EventLogger.logEvent("TUMBLE detected");
//						
////						int[] tumble = new int[1];
////						tumble[0] = 1;
////						addDataToList(tumbleDatalist, tumble);
//						if (uid != null && uid.length() > 0) {	
//							Date now = new Date();
//							new SendRealTimeAlarmThread("5", now.getTime(), 1).start();
//						}
//						
//						setTumbleFlag();
//						notifyUITumbleHappened();
//				}
			}
			else if (ACTION_SAVE_ALARM_TO_FILE.equals(action)) {
				String type = intent.getStringExtra(ALARM_TYPE);
				long timestamp = intent.getLongExtra(ALARM_TIME, 0);
				int value = intent.getIntExtra(ALARM_VALUE, 0);
				
				ValueEntry entry = new ValueEntry(timestamp, value);
				
				if ("1".equals(type)) {					
					hteWarningList.add(entry);					
				}
				
				if ("5".equals(type)) {
					tumbleDatalist.add(entry);
				}
			}
		}
	};

	private void sendIntentToSaveAlarm(String type, long timestamp, int value) {
		Intent intent = new Intent(ACTION_SAVE_ALARM_TO_FILE);
		intent.putExtra(ALARM_TYPE, type);
		intent.putExtra(ALARM_TIME, timestamp);
		intent.putExtra(ALARM_VALUE, value);
		
		sendBroadcast(intent);
	}
	
	class SendRealTimeAlarmThread extends Thread {
		String type;
		long timestamp;
		int value;
		
		public SendRealTimeAlarmThread(String type, long timestamp, int value)
		{
			this.type = type;
			this.timestamp = timestamp;
			this.value = value;
		}

		@Override
		public void run() {
			String uid = UserInfo.getIntance().getUserData().getMy_name();
			if ((uid != null) && (uid.length() >0)) {
				String upperLimit;
				String floorLimit;
				
				if ("1".equals(type)) {
					int heartUp = UserInfo.getIntance().getUserData().getLimit_heart_up();
					upperLimit = Integer.toString(heartUp);
					int heartDw = UserInfo.getIntance().getUserData().getLimit_heart_dw();
					floorLimit = Integer.toString(heartDw);
				} 
				else if ("5".equals(type)) {
					upperLimit = "1";
					floorLimit = "1";
				}
				else {
					return;
				}
				
				Date date = new Date();
				date.setTime(this.timestamp);
				String currDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
						.format(date);
	
//				String requestInfo = "[{username:\""
//						+ UserInfo.getIntance().getUserData().getMy_name()
//						+ "\",alertType:\"" + this.type + "\",alertTime:\"" + currDate
//						+ "\",value:\"" + this.value + "\",deviceNumber:\""
//						+ UserInfo.getIntance().getUserData().getDevice_number() + "\""
//						+ ",upperLimit:\"" + upperLimit + "\""
//						+ ",floorLimit:\"" + floorLimit + "\""
//						+ "}]";
				String weath_city=UserInfo.getIntance().getUserData().getProvince();
				String update_GPS_time=UserInfo.getIntance().getUserData().getUpdate_GPS_time();
				double longitude=UserInfo.getIntance().getUserData().getLongitude();
				double latitude=UserInfo.getIntance().getUserData().getLatitude();
				String requestInfo = "[{username:\""
						+ UserInfo.getIntance().getUserData().getMy_name()
						+ "\",alertType:\"" + this.type + "\",alertTime:\"" + currDate
						+ "\",value:\"" + this.value + "\",upperLimit:\"" + ""
						+ "\",floorLimit:\"" + "" + "\",deviceNumber:\""
						+ UserInfo.getIntance().getUserData().getDevice_number()
						+ "\",longitude:\"" + longitude + "\",latitude:\""
						+ latitude + "\",positioningTime:\"" + update_GPS_time
						+ "\",city:\"" + weath_city+ "\"}]";
				
				
				String responseInfo = Client.getsendAlarm(requestInfo);
				
				if (responseInfo == null) {
					sendIntentToSaveAlarm(type, timestamp, value);
				} else {
					if ("1".equals(responseInfo)) {
						// The server has received the alarm
					} else {
						sendIntentToSaveAlarm(type, timestamp, value);					
					}
				}
			}
		}
	}
	
	/**
	 * 写文件
	 * 
	 * @param list
	 *            文件列表
	 * @param data
	 *            文件类容
	 */
	private void addDataToList(List<ValueEntry> list, int[] data) {
		synchronized (lockPersist) {
		for (int i = 0; i < data.length; i++) {
			Date currDate = new Date();
			list.add(new ValueEntry(currDate.getTime(), data[i]));
		}
		}
	}
	
	private void addStepCaloriesToList(List<StepCaloriesEntry> list, StepCaloriesEntry[] data) {
		for (int i = 0; i < data.length; i++) {
			Date currDate = new Date();
			list.add(new StepCaloriesEntry(currDate.getTime(), data[i].stepCount, data[i].calories));
		}
	}
	
	private void notifyDataAvailable(String action, String extra, int[] data) {
		final Intent intent = new Intent(action);
		intent.putExtra(extra, data);
		sendBroadcast(intent);
	}

	private void saveFrame60(String currMinute) {
		try {
			Program.createDataDirForCurrMinute(currMinute);
			Program.createTempDirForCurrMinute(currMinute);
			
			saveListToFile(Program.ECG_DATA, ecgList, currMinute);
			saveListToFile(Program.RESP_DATA, respList, currMinute);
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("action dataStorageService saveFrame60");
	}
	
	private void saveFrame60(String currMinute, String uid) {
		try {
			Program.createDataDirForCurrMinute(currMinute, uid);
//			Program.createTempDirForCurrMinute(currMinute, uid);
			
			saveListToFile(Program.ECG_DATA, ecgList, currMinute, uid);
			saveListToFile(Program.HEART_RATE_DATA, heartRateList, currMinute, uid);
			saveListToFile(Program.RESP_DATA, respList, currMinute, uid);
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("action dataStorageService saveFrame60");
	}

	private void notifyUpload(String lastUpload, String uid) {
		Intent intent = new Intent(ACTION_START_UPLOAD);
		intent.putExtra("LAST_UPLOAD", lastUpload);
		intent.putExtra("UID", uid);
		sendBroadcast(intent);
	}

	/**
	 * 把一批数据从List里保存到文件
	 */
	private void saveFrame90(String currMinute) {
		try {
			// if the directory yyyyMMdd/MINUTE_FORMAT dose not exist, we create
			// one.
			Program.createDataDirForCurrMinute(currMinute);
			Program.createTempDirForCurrMinute(currMinute);
			// ---System.out.println("action save Program.TEMP_DATA");
			saveListToFile(Program.TEMP_DATA, temDatalist, currMinute);
			
			// ---System.out.println("action save Program.TUMBLE_DATA");
			saveListToFile(Program.TUMBLE_DATA, tumbleDatalist, currMinute);

			saveListToFile(Program.ACCELERATION_X_DATA, accelerationXlist,
					currMinute);
			saveListToFile(Program.ACCELERATION_Y_DATA, accelerationYlist,
					currMinute);
			saveListToFile(Program.ACCELERATION_Z_DATA, accelerationZlist,
					currMinute);
			
			saveAccelVectorListToFile(Program.ACCE_VECTOR_DATA, accelList, currMinute);
			
			saveListToFile(Program.VOLTAGE_DATA, voltageList, currMinute);
			
			saveListToFile(Program.HTE_WARNING_DATA, hteWarningList, currMinute);
			
			saveStepCaloriesListToFile(Program.STEP_CALORIES_DATA, stepCaloriesList, currMinute);
			
		} catch (IOException e) {
			// ---System.out.println("action saveFrame90 IOException");
			e.printStackTrace();
		}
	}

	/**
	 * 把一批数据从List里保存到文件
	 */
	private void saveFrame90(String currMinute, String uid) {
		try {
			// if the directory yyyyMMdd/MINUTE_FORMAT dose not exist, we create
			// one.
			Program.createDataDirForCurrMinute(currMinute, uid);
//			Program.createTempDirForCurrMinute(currMinute, uid);
			
			// ---System.out.println("action save Program.TEMP_DATA");
			saveListToFile(Program.TEMP_DATA, temDatalist, currMinute, uid);
			
			// ---System.out.println("action save Program.TUMBLE_DATA");
			saveListToFile(Program.TUMBLE_DATA, tumbleDatalist, currMinute, uid);

			saveListToFile(Program.ACCELERATION_X_DATA, accelerationXlist,
					currMinute, uid);
			saveListToFile(Program.ACCELERATION_Y_DATA, accelerationYlist,
					currMinute, uid);
			saveListToFile(Program.ACCELERATION_Z_DATA, accelerationZlist,
					currMinute, uid);

			saveAccelVectorListToFile(Program.ACCE_VECTOR_DATA, 
					  accelList, 
					  currMinute, uid);
			
			saveListToFile(Program.VOLTAGE_DATA, 
					  voltageList, 
					  currMinute, uid);			
			
			saveListToFile(Program.HTE_WARNING_DATA, hteWarningList, currMinute, uid);
			
			saveStepCaloriesListToFile(Program.STEP_CALORIES_DATA, 
											stepCaloriesList, 
											currMinute, uid);
			
		} catch (IOException e) {
			// ---System.out.println("action saveFrame90 IOException");
			e.printStackTrace();
		}
	}
	
	private void saveStepCaloriesListToFile(String dataType, 
								List<StepCaloriesEntry> list, String currMinute) throws IOException {
		if (list.size() == 0) {
			StepCaloriesEntry[] stepCalories = new StepCaloriesEntry[1];
			StepCaloriesEntry stepCaloriesEntry = new StepCaloriesEntry();
			stepCaloriesEntry.stepCount = GlobalStatus.getInstance().getCurrentStep();
			stepCaloriesEntry.calories = GlobalStatus.getInstance().getCalories();
			stepCalories[0] = stepCaloriesEntry;
			addStepCaloriesToList(list, stepCalories);
			
			stepCaloriesEntry = null;
			stepCalories = null;
		}
		
		if (UserInfo.isInDebugMode()) {
		String filePath = Program.getSDPathByDataType(dataType, currMinute);		
		File file = new File(filePath);
	    if(!file.exists()) {
	    	file.createNewFile();
	    }
		
		BufferedWriter out = null;
	    try {
			out = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(filePath, true)));
			Iterator<StepCaloriesEntry> it = list.iterator();
			while (it.hasNext()) {
				StepCaloriesEntry entry = it.next();
				out.write(entry.timestamp + "\t" + entry.stepCount 
										  + "\t" + entry.calories + nextLine);
			}
		} catch (IOException e) {
			//---System.out.println("action saveListToFile IOException");
			e.printStackTrace();
			throw e;
		} finally {
			out.flush();
			out.close();
			out = null;
		}
		}
		else {
			String dataPath = Program.getSDDataPathByDataType(dataType, currMinute);
			if (dataPath != null && dataPath.length() > 0) {
				File file = new File(dataPath);
				if (!file.exists()) {
					mCompressedFilePool.createDataOutputStream(dataType, currMinute);
				}
			}
			
			DataOutputStream out = mCompressedFilePool.getOutputStream(dataType);			
			if (out != null) {
				Iterator<StepCaloriesEntry> it = list.iterator();
				while (it.hasNext()) {
					StepCaloriesEntry entry = it.next();
					out.writeLong(entry.timestamp - (1000 * 60));
					out.writeInt(entry.stepCount);
					out.writeInt(entry.calories);
				}
			}			
		}
	}
	
	private void saveStepCaloriesListToFile(String dataType, 
			List<StepCaloriesEntry> list, String currMinute, String uid) throws IOException {

		if (list.size() == 0) {
			StepCaloriesEntry[] stepCalories = new StepCaloriesEntry[1];
			StepCaloriesEntry stepCaloriesEntry = new StepCaloriesEntry();
			stepCaloriesEntry.stepCount = GlobalStatus.getInstance().getCurrentStep();
			stepCaloriesEntry.calories = GlobalStatus.getInstance().getCalories();
			stepCalories[0] = stepCaloriesEntry;
			addStepCaloriesToList(list, stepCalories);
		}
		
		if (UserInfo.isInDebugMode()) {
			String filePath = Program.getSDPathByDataType(dataType, currMinute, uid);		
			File file = new File(filePath);
			if(!file.exists()) {
				file.createNewFile();
			}

			BufferedWriter out = null;
			try {
				out = new BufferedWriter(new OutputStreamWriter(
								new FileOutputStream(filePath, true)));
				Iterator<StepCaloriesEntry> it = list.iterator();
				while (it.hasNext()) {
					StepCaloriesEntry entry = it.next();
					out.write(entry.timestamp + "\t" + entry.stepCount 
							+ "\t" + entry.calories + nextLine);
				}
			} catch (IOException e) {
				//---System.out.println("action saveListToFile IOException");
				e.printStackTrace();
				throw e;
			} finally {
				out.flush();
				out.close();
				out = null;
			}
		}
		else {
			String dataPath = Program.getSDDataPathByDataType(dataType, currMinute, uid);
			if (dataPath != null && dataPath.length() > 0) {
				File file = new File(dataPath);
				if (!file.exists()) {
					mCompressedFilePool.createDataOutputStream(dataType, currMinute, uid);
				}
			}

			DataOutputStream out = mCompressedFilePool.getOutputStream(dataType);			
			if (out != null) {
				Iterator<StepCaloriesEntry> it = list.iterator();
				while (it.hasNext()) {
					StepCaloriesEntry entry = it.next();
					out.writeLong(entry.timestamp);
					out.writeInt(entry.stepCount);
					out.writeInt(entry.calories);
				}
			}
		}
	}

	private void saveAccelVectorListToFile(String dataType,
			List<AccelVector> accelList, String currMinute) throws IOException {
		if (UserInfo.isInDebugMode()) {
		String filePath = Program.getSDPathByDataType(dataType, currMinute);		
		File file = new File(filePath);
	    if(!file.exists()) {
	    	file.createNewFile();
	    }
		
		BufferedWriter out = null;
	    try {
			out = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(filePath, true)));
			Iterator<AccelVector> it = accelList.iterator();
			while (it.hasNext()) {
				AccelVector entry = it.next();
				out.write(entry.timestamp + "\t" + entry.acceX 
										  + "\t" + entry.acceY + "\t" 
										  + entry.acceZ + nextLine);
			}
		} catch (IOException e) {
			//---System.out.println("action saveListToFile IOException");
			e.printStackTrace();
			throw e;
		} finally {
			if (out != null) {
				out.flush();
				out.close();
				out = null;
			}
		}
		} else {
			String dataPath = Program.getSDDataPathByDataType(dataType, currMinute);
			if (dataPath != null && dataPath.length() > 0) {
				File file = new File(dataPath);
				if (!file.exists()) {
					mCompressedFilePool.createDataOutputStream(dataType, currMinute);
				}
			}
			
			DataOutputStream out = mCompressedFilePool.getOutputStream(dataType);
			if (out != null) {
				if (UserInfo.CMPR_DATX == UserInfo.dataCompressType()) {
					out.writeInt(Integer.MAX_VALUE);
					out.writeInt(Integer.MAX_VALUE);				
					Date now = new Date();
					out.writeLong(now.getTime());
				}
				
				Iterator<AccelVector> it = accelList.iterator();
				while (it.hasNext()) {
					AccelVector entry = it.next();
					if (UserInfo.CMPR_DAT == UserInfo.dataCompressType()) {
						out.writeLong(entry.timestamp);
					}
					out.writeInt(entry.acceX);
					out.writeInt(entry.acceY);
					out.writeInt(entry.acceZ);
				}
			}
		}
	}


	/**
	 * 保存数据到文件
	 * 
	 * @param dataType
	 *            数据类型
	 * @param list
	 *            数据列表
	 * @param currMinute
	 *            文件夹名
	 * @throws IOException
	 */
	private void saveListToFile(String dataType, List<ValueEntry> list,
			String currMinute) throws IOException {
		if (UserInfo.isInDebugMode()) {
			String filePath = Program.getSDPathByDataType(dataType, currMinute);
	
			System.out.println("action saveListToFile filePath[" + filePath + "]");
	
			File file = new File(filePath);
			if (!file.exists()) {
				file.createNewFile();
			}
	
			BufferedWriter out = null;
			try {
				out = new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream(filePath, true)));
				Iterator<ValueEntry> it = list.iterator();
				while (it.hasNext()) {
					ValueEntry entry = it.next();
					out.write(entry.timestamp + "\t" + entry.value + nextLine);
				}
			} catch (IOException e) {
				// ---System.out.println("action saveListToFile IOException");
				e.printStackTrace();
				throw e;
			} finally {
				if (out != null) {
					out.flush();
					out.close();
					out = null;
				}
			}
		} else {
			String dataPath = Program.getSDDataPathByDataType(dataType, currMinute);
			if (dataPath != null && dataPath.length() > 0) {
				File file = new File(dataPath);
				if (!file.exists()) {
					mCompressedFilePool.createDataOutputStream(dataType, currMinute);
				}
			}
			
			DataOutputStream out = mCompressedFilePool.getOutputStream(dataType);			
			if (out != null) {
				if (UserInfo.CMPR_DATX == UserInfo.dataCompressType()) {
					out.writeInt(Integer.MAX_VALUE);
					out.writeInt(Integer.MAX_VALUE);
					Date now = new Date();
					out.writeLong(now.getTime());
				}
				
				Iterator<ValueEntry> it = list.iterator();
				while (it.hasNext()) {
					ValueEntry entry = it.next();
					if (UserInfo.CMPR_DAT == UserInfo.dataCompressType()) {
						out.writeLong(entry.timestamp);
					}
					out.writeInt(entry.value);
				}
			}
		}
	}
	private void saveListToFile(String dataType, List<ValueEntry> list,
			String currMinute, String uid) throws IOException {
		if (UserInfo.isInDebugMode()) {
			String filePath = Program.getSDPathByDataType(dataType, currMinute, uid);
	
			System.out.println("action saveListToFile filePath[" + filePath + "]");
	
			File file = new File(filePath);
			if (!file.exists()) {
				file.createNewFile();
			}
	
			BufferedWriter out = null;
			try {
				out = new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream(filePath, true)));
				Iterator<ValueEntry> it = list.iterator();
				while (it.hasNext()) {
					ValueEntry entry = it.next();
					out.write(entry.timestamp + "\t" + entry.value + nextLine);
				}
			} catch (IOException e) {
				// ---System.out.println("action saveListToFile IOException");
				e.printStackTrace();
				throw e;
			} finally {
				if (out != null) {
					out.flush();
					out.close();
					out = null;
				}
			}
		}
		else {
			String dataPath = Program.getSDDataPathByDataType(dataType, currMinute, uid);
			if (dataPath != null && dataPath.length() > 0) {
				File file = new File(dataPath);
				if (!file.exists()) {
					mCompressedFilePool.createDataOutputStream(dataType, currMinute, uid);
				}
			}
			
			if (isWarningType(dataType)) {
				DataOutputStream out = mCompressedFilePool.getOutputStream(dataType);			
				if (out != null) {
					int warningUp = 0;
					int warningDw = 0;
					
					if (dataType.equals(Program.HTE_WARNING_DATA)) {
						warningUp = UserInfo.getIntance().getUserData().getLimit_heart_up();
						warningDw = UserInfo.getIntance().getUserData().getLimit_heart_dw();
					}
					else if (dataType.equals(Program.TUMBLE_DATA)) {
						warningUp = 1;
						warningDw = 1;
					}
					
					Iterator<ValueEntry> it = list.iterator();
					while (it.hasNext()) {
						ValueEntry entry = it.next();
						out.writeLong(entry.timestamp);
						out.writeInt(entry.value);
						out.writeInt(warningDw);
						out.writeInt(warningUp);
					}
				}
				
				return;
			}
			
			DataOutputStream out = mCompressedFilePool.getOutputStream(dataType);			
			if (out != null) {
				if (UserInfo.CMPR_DATX == UserInfo.dataCompressType() 
							&& (!isWarningType(dataType))) {
					out.writeInt(Integer.MAX_VALUE);
					out.writeInt(Integer.MAX_VALUE);
					Date now = new Date();
					out.writeLong(now.getTime() - BACKWARD_TIME);
				}
				
//				int countECG = 0;				
				Iterator<ValueEntry> it = list.iterator();
				while (it.hasNext()) {
//					countECG++;
					ValueEntry entry = it.next();
					if (UserInfo.CMPR_DAT == UserInfo.dataCompressType()) {
						out.writeLong(entry.timestamp);
					}
					out.writeInt(entry.value);
				}
				
//				if (dataType.equals(Program.ECG_DATA)) {
//					System.out.println("action DataStorageService countECG " + countECG);
//				}
			}
		}
	}
	
	private boolean isWarningType(String dataType) {
		if (dataType == Program.TUMBLE_DATA) {
			return true;	
		}
		if (dataType == Program.HTE_WARNING_DATA) {
			return true;
		}
		
		return false;
	}
	
	private void saveAccelVectorListToFile(String dataType,
			List<AccelVector> accelList, String currMinute, String uid) throws IOException {
		if (UserInfo.isInDebugMode()) {
		String filePath = Program.getSDPathByDataType(dataType, currMinute, uid);		
		File file = new File(filePath);
	    if(!file.exists()) {
	    	file.createNewFile();
	    }
		
		BufferedWriter out = null;
	    try {
			out = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(filePath, true)));
			Iterator<AccelVector> it = accelList.iterator();
			while (it.hasNext()) {
				AccelVector entry = it.next();
				out.write(entry.timestamp + "\t" + entry.acceX 
										  + "\t" + entry.acceY + "\t" 
										  + entry.acceZ + nextLine);
			}
		} catch (IOException e) {
			//---System.out.println("action saveListToFile IOException");
			e.printStackTrace();
			throw e;
		} finally {
			if (out != null) {
				out.flush();
				out.close();
				out = null;
			}
		}
		} else {
			String dataPath = Program.getSDDataPathByDataType(dataType, currMinute, uid);
			if (dataPath != null && dataPath.length() > 0) {
				File file = new File(dataPath);
				if (!file.exists()) {
					mCompressedFilePool.createDataOutputStream(dataType, currMinute, uid);
				}
			}
			
			DataOutputStream out = mCompressedFilePool.getOutputStream(dataType);
			if (out != null) {
				if (UserInfo.CMPR_DATX == UserInfo.dataCompressType()) {
					out.writeInt(Integer.MAX_VALUE);
					out.writeInt(Integer.MAX_VALUE);
					Date now = new Date();
					out.writeLong(now.getTime() - BACKWARD_TIME);
				}
				
				Iterator<AccelVector> it = accelList.iterator();
				while (it.hasNext()) {
					AccelVector entry = it.next();
					if (UserInfo.CMPR_DAT == UserInfo.dataCompressType()) {
						out.writeLong(entry.timestamp);
					}
					out.writeInt(entry.acceX);
					out.writeInt(entry.acceY);
					out.writeInt(entry.acceZ);
				}
			}
		}
	}

	@Override
	public boolean stopService(Intent name) {
		// TODO Auto-generated method stub
		this.unregisterReceiver(StorageReceiver);
		System.out.println("action serviced dataStorage");
		return super.stopService(name);
	}

	private byte getSeqNo(byte[] data) {
		return data[4];
	}
	
	private int[] duplicateData(int[] origData) {
		int[] data = new int[origData.length * 2];
		for (int i = 0; i < origData.length; i++) {
			data[i * 2] = origData[i];
			data[i * 2 + 1] = origData[i];
		}
		return data;
	}

//	private void addDataToLists(List<ValueEntry> list, int data) {
//
//		Date currDate = new Date();
//		list.add(new ValueEntry(currDate.getTime(), data));
//
//	}

	private void accelAceptX(int seq, int val) {
		try {
			accelAnalyzer.acceptX(seq, val);
		} catch (DataErrorException e) {									
			e.printStackTrace();
			accelAnalyzer.reset();
		}
	}
	
	private void accelAceptY(int seq, int val) {
		try {
			accelAnalyzer.acceptY(seq, val);
		} catch (DataErrorException e) {									
			e.printStackTrace();
			accelAnalyzer.reset();
		}
	}
	
	private void accelAceptZ(int seq, int val) {
		try {
			accelAnalyzer.acceptZ(seq, val);
		} catch (DataErrorException e) {									
			e.printStackTrace();
			accelAnalyzer.reset();
		}
	}
	
	private boolean isAccelVectorReady() {
		boolean isAccelReady = false;
		try {
			isAccelReady = accelAnalyzer.isAccelVectorReady();
		} catch (DataErrorException e) {
			e.printStackTrace();
			accelAnalyzer.reset();
		}
		
		return isAccelReady;
	}
	
	private int[] getAccelXYZ() {
		int[] xyz = null;

		try {
			xyz = accelAnalyzer.getAccelXYZ();
		} catch (DataErrorException e) {
			e.printStackTrace();
			xyz = null;
			accelAnalyzer.reset();
		}
		
		return xyz;
	}
	
	private int getNextVal(int start, int step) {
		return (start + step) > 255 ? (start + step) - 256 : (start + step); 
	}
	
	private void sendData(String action, String extra, int[] data) {
			final Intent intent = new Intent(action);
			intent.putExtra(extra, data);
			sendBroadcast(intent);
	}
	
	private void sendStressData(String action, String extra, StressLevelItem stressLevelItem) {
		final Intent intent = new Intent(action);
		String stressData = "";
		
		stressData += "stressA: ";
		stressData += Float.toString(stressLevelItem.stressA);
		stressData += "\n";

		stressData += "stressB1: ";
		stressData += Float.toString(stressLevelItem.stressB1);
		stressData += "\n";
		
		stressData += "stressB2: ";
		stressData += Float.toString(stressLevelItem.stressB2);
		stressData += "\n";
		
		stressData += "stressC1: ";
		stressData += Float.toString(stressLevelItem.stressC1);
		stressData += "\n";
	
		stressData += "stressC2: ";
		stressData += Float.toString(stressLevelItem.stressC2);
		stressData += "\n";
		
		stressData += "stressC3: ";
		stressData += Float.toString(stressLevelItem.stressC3);
		stressData += "\n";
		
		stressData += "stressD: ";
		stressData += Float.toString(stressLevelItem.stressD);
		stressData += "\n";
		
		stressData += "stressE1: ";
		stressData += Float.toString(stressLevelItem.stressE1);
		stressData += "\n";
		
		stressData += "stressE2: ";
		stressData += Float.toString(stressLevelItem.stressE2);
		stressData += "\n";
		
		intent.putExtra(extra, stressData);
		sendBroadcast(intent);
	}
	
	private void getUserInfo() {
//		peopledatas = this.getSharedPreferences("peopleinfo", MODE_PRIVATE);
//		String info = peopledatas.getString("userinfo", "");
//		try {
//			JSONArray jsonArray = new JSONArray(info);
//			JSONObject jsonObject = jsonArray.getJSONObject(0);
//			String gender = jsonObject.getString("sex");
//			if (gender != null){
//				mGender = Integer.parseInt(gender);
//			} else {
//				mGender = 0;
//			}
//			
//			String age = jsonObject.getString("age");
//			if (age != null) {
//				mAge = Integer.parseInt(age);
//			} else {
//				mAge = 0;
//			}
//		} catch (Exception e) {
//			mGender = 0;
//			mAge = 0;
//		}
		mGender = UserInfo.getIntance().getUserData().getSex();
		mAge = UserInfo.getIntance().getUserData().getAge();
		mHeight = UserInfo.getIntance().getUserData().getHeight();
		mWeight = UserInfo.getIntance().getUserData().getWeight();
		mLimitHeartUp = UserInfo.getIntance().getUserData().getLimit_heart_up();
		mLimitHeartDown = UserInfo.getIntance().getUserData().getLimit_heart_dw();
		
		System.out.println("action DataStorageService getUserInfo [" + mGender + "]["
							+ mAge + "][" + mHeight + "][" + mWeight + "][" 
							+ mLimitHeartUp + "][" + mLimitHeartDown + "]");
	}
	
	private void startGetStressLevelTimer() {
//		Intent intent = new Intent(); 
//		intent.setAction(ACTION_ALARM_GET_STRESS_LEVEL_ITEM);  
//		
//		AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);		
//		PendingIntent pi=PendingIntent.getBroadcast(this, 0, intent,0);
//		
//		Calendar c=Calendar.getInstance();
//		long triggerAtTime=c.getTimeInMillis();
//
////		am.setRepeating(AlarmManager.RTC_WAKEUP, triggerAtTime, 1000 * 5, pi);		
//		am.setRepeating(AlarmManager.RTC, triggerAtTime, 1000 * 5, pi);		
	}
	
	private void stopGetStressLevelTimer() {
//		Intent intent = new Intent();  
//		intent.setAction(ACTION_ALARM_GET_STRESS_LEVEL_ITEM);  
//		PendingIntent sender=PendingIntent.getBroadcast(this, 0, intent, 0);  
//		AlarmManager alarm=(AlarmManager)getSystemService(ALARM_SERVICE);  
//		alarm.cancel(sender);		
	}
	
	private void startJudgeTumbleTimer() {
		mIsJudgeTumbleTimerStarted = true;
		
		Intent intent = new Intent(); 
		intent.setAction(ACTION_ALARM_JUDGE_TUMBLE);  
		
		AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);		
		PendingIntent pi=PendingIntent.getBroadcast(this, 0, intent,0);
		
		Calendar c=Calendar.getInstance();
		long triggerAtTime=c.getTimeInMillis();

//		am.setRepeating(AlarmManager.RTC_WAKEUP, triggerAtTime, 1000 * 5, pi);
		am.set(AlarmManager.RTC, triggerAtTime + 1000, pi);
	}
	
	private void startJudgeTumbleTimer(String uid) {
		mIsJudgeTumbleTimerStarted = true;
		
		Intent intent = new Intent(); 
		intent.setAction(ACTION_ALARM_JUDGE_TUMBLE);
		intent.putExtra("UID", uid);
		
		AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);		
		PendingIntent pi=PendingIntent.getBroadcast(this, 0, intent,0);
		
		Calendar c=Calendar.getInstance();
		long triggerAtTime=c.getTimeInMillis();

//		am.setRepeating(AlarmManager.RTC_WAKEUP, triggerAtTime, 1000 * 5, pi);
//		am.set(AlarmManager.RTC_WAKEUP, triggerAtTime + 1000, pi);
		am.set(AlarmManager.RTC, triggerAtTime + 1000, pi);
	}
	
	private void stopJudgeTumbleTimer() {
		mIsJudgeTumbleTimerStarted = false;
		
		Intent intent = new Intent();  
		intent.setAction(ACTION_ALARM_JUDGE_TUMBLE);  
		PendingIntent sender=PendingIntent.getBroadcast(this, 0, intent, 0);  
		AlarmManager alarm=(AlarmManager)getSystemService(ALARM_SERVICE);  
		alarm.cancel(sender);		
	}
	
	private void checkECGCalculateDone() {
		if (ecgHeartRateCal.isECGCalculateDone()) {
			int realTimeHeartRate 
								= ecgHeartRateCal.getHeartRateRealtime();
			System.out.println("action DataStorageService getECGHeartRate real time: " 
									+ ecgHeartRateCal.getHeartRateRealtime());
		
			GlobalStatus.getInstance().setHeartRate(realTimeHeartRate);
			
			if (realTimeHeartRate < mLimitHeartDown || realTimeHeartRate > mLimitHeartUp) {
//				warningHteManager.increase(Program.getMinuteDataByDate(new Date()), realTimeHeartRate);
//				
////				int[] heartRate = new int[1];
////				heartRate[0] = realTimeHeartRate;
////				addDataToList(hteWarningList, heartRate);
//				Date now = new Date();
//				new SendRealTimeAlarmThread("1", now.getTime(), realTimeHeartRate).start();
			}
			
			int mentalStress = ecgHeartRateCal.getStress();
			GlobalStatus.getInstance().setStressLevel(mentalStress);
		}
	}
	
	private void checkECGCalculateDone(String uid, boolean isValidEcg) {
		System.out.println("action DataStorageService checkECGCalculateDone isValidEcg:"
							+ isValidEcg);
		if (isValidEcg) {
			if (ecgHeartRateCal.isECGCalculateDone()) {
				int realTimeHeartRate 
									= ecgHeartRateCal.getHeartRateRealtime();
				System.out.println("action DataStorageService getECGHeartRate real time: " 
										+ realTimeHeartRate);
				
				if (realTimeHeartRate == 0) {
					return;
				}
				
				if (!mDetectC7StateMachine.isInMaskStatus()) {
				if (queueHte.size() < 10) {
					queueHte.offer(realTimeHeartRate);
					mCountInvalidEcg = 0;
					
					int mentalStress = ecgHeartRateCal.getStress();
					GlobalStatus.getInstance().setStressLevel(mentalStress);
					
					return;
				} else {
					queueHte.offer(realTimeHeartRate);
					
					int delta = queueHte.size() - 10;
					for (int i = 0; i < delta; i++) {
						queueHte.poll();
					}
					
					int sum = 0;
					for (Integer iVal : queueHte) {
						sum += iVal;
					}
					
					realTimeHeartRate = (int) (sum * 1.0f / (queueHte.size()));
				}
				

				GlobalStatus.getInstance().setHeartRate(realTimeHeartRate);
				
				if ((uid != null) && (uid.length() > 0)) {
					if ((realTimeHeartRate != 0) && (realTimeHeartRate < mLimitHeartDown || realTimeHeartRate > mLimitHeartUp)) {
					if (mCountFrameFromConnected > SKIP_FRAMES) {
												
//						warningHteManager.increase(
//												Program.getSecondDataByDate(new Date()), 
//												realTimeHeartRate,
//												uid);
//						
//		//				int[] heartRate = new int[1];
//		//				heartRate[0] = realTimeHeartRate;
//		//				addDataToList(hteWarningList, heartRate);
//						Date now = new Date();
//						new SendRealTimeAlarmThread("1", now.getTime(), realTimeHeartRate).start();						
					
					}
				}
				
				int mentalStress = ecgHeartRateCal.getStress();
				GlobalStatus.getInstance().setStressLevel(mentalStress);
				}
				} else {
					System.out.println("action DataStorageService checkECGCalculateDone in C7 mask status");
				}
			}
			
			mCountInvalidEcg = 0;
		}
		else {
//			if (ecgHeartRateCal.isECGCalculateDone()) {
			
//				int realTimeHeartRate 
//									= ecgHeartRateCal.getHeartRateRealtime();
//				System.out.println("action DataStorageService getECGHeartRate real time: " 
//										+ realTimeHeartRate);
//			
//				GlobalStatus.getInstance().setHeartRate(0);
//				
//				int mentalStress = ecgHeartRateCal.getStress();
//				GlobalStatus.getInstance().setStressLevel(mentalStress);
				
//			}
				
			mCountInvalidEcg++;
			if (mCountInvalidEcg > 2000) {				
				final Intent intent = new Intent(ACTION_NOTIFY_INVALID_ECG);
				sendBroadcast(intent);
				mCountInvalidEcg = 0;
			}
		}
	}
	
	private void checkECGCalculateDoneByHeartRate(int heartRate, String uid, boolean isValidEcg) {
		System.out.println("action DataStorageService checkECGCalculateDone isValidEcg:"
							+ isValidEcg);
		if (isValidEcg) {
			int realTimeHeartRate 
								= heartRate;
			System.out.println("action DataStorageService checkECGCalculateDoneByHeartRate real time heart rate: " 
									+ realTimeHeartRate);
			if (realTimeHeartRate == 0) {
				return;
			}
			
//			if (!mDetectC7StateMachine.isInMaskStatus()) {
			if (queueHte.size() < 10) {
				queueHte.offer(realTimeHeartRate);
				mCountInvalidEcg = 0;
				
				int mentalStress = ecgHeartRateCal.getStress();
				GlobalStatus.getInstance().setStressLevel(mentalStress);
				
				return;
			} else {
				queueHte.offer(realTimeHeartRate);
				
				int delta = queueHte.size() - 10;
				for (int i = 0; i < delta; i++) {
					queueHte.poll();
				}
				
				int sum = 0;
				for (Integer iVal : queueHte) {
					sum += iVal;
				}
				
				realTimeHeartRate = (int) (sum * 1.0f / (queueHte.size()));
			}

			GlobalStatus.getInstance().setHeartRate(realTimeHeartRate);
			
			if ((uid != null) && (uid.length() > 0)) {
				if ((realTimeHeartRate != 0) && (realTimeHeartRate < mLimitHeartDown || realTimeHeartRate > mLimitHeartUp)) {
//					warningHteManager.increase(
//											Program.getSecondDataByDate(new Date()), 
//											realTimeHeartRate,
//											uid);
					Date now = new Date();
					new SendRealTimeAlarmThread("1", now.getTime(), realTimeHeartRate).start();						
				}
			
				int mentalStress = ecgHeartRateCal.getStress();
				GlobalStatus.getInstance().setStressLevel(mentalStress);
			}
//			} else {
//				System.out.println("action DataStorageService checkECGCalculateDone in C7 mask status");
//			}
			
			mCountInvalidEcg = 0;
		}
		else {				
			mCountInvalidEcg++;
			if (mCountInvalidEcg > 10) {				
				final Intent intent = new Intent(ACTION_NOTIFY_INVALID_ECG);
				sendBroadcast(intent);
				mCountInvalidEcg = 0;
			}
		}
	}
	
	private void checkRespCalculateDone() {		
	}
	
	private void checkAccelCalculateDone() {
		System.out.println("action DataStorageService checkAccelCalculateDone.");
//		if (GlobalAccelCalculator.getInstance().isAccelCalculateDone()) {
			int posture = GlobalAccelCalculator.getInstance().getPosture();
			System.out.println("action DataStorage GlobalAccelCalculator getPosture:" + posture);									
			int[] postureData = new int[2];
			postureData[0] = posture;
	
			int gait = GlobalAccelCalculator.getInstance().getGait();
			System.out.println("action DataStorage GlobalAccelCalculator getGait:" + gait);
			postureData[1] = gait;
			
			GlobalStatus.getInstance().setPosture(posture);
			GlobalStatus.getInstance().setGait(gait);

			if ((stepCheckStateMachine != null) 
					  && (InvalidStepCheckStateMachine.S_VALID == stepCheckStateMachine.getCurrentState())) {
				sendData(ACTION_POSTURE_DATA_AVAILABLE, POSTURE_DATA, postureData);
			}
			else {
				int[] invalidPostureData = new int[2];
				invalidPostureData[0] = 0;
				invalidPostureData[1] = 0;
				sendData(ACTION_POSTURE_DATA_AVAILABLE, POSTURE_DATA, invalidPostureData);
			}
				
			if ((stepCheckStateMachine != null) 
			  && (InvalidStepCheckStateMachine.S_VALID == stepCheckStateMachine.getCurrentState())) {
				updateStandStill(posture, gait);
			}
			else {
				updateStandStill(0, 0);
			}
//		}
	}
	
	private void checkAccelCalculateDoneDebug() {
		System.out.println("action DataStorageService checkAccelCalculateDone.");
//		if (GlobalAccelCalculator.getInstance().isAccelCalculateDone()) {
			int posture = GlobalAccelCalculator.getInstance().getPosture();
			System.out.println("action DataStorage GlobalAccelCalculator getPosture:" + posture);									
			int[] postureData = new int[2];
			postureData[0] = posture;
	
			int gait = GlobalAccelCalculator.getInstance().getGait();
			System.out.println("action DataStorage GlobalAccelCalculator getGait:" + gait);
			postureData[1] = gait;

			GlobalStatus.getInstance().setPosture(posture);
			GlobalStatus.getInstance().setGait(gait);
			
			sendData(ACTION_POSTURE_DATA_AVAILABLE, POSTURE_DATA, postureData);
			
			updateStandStill(posture, gait);
//		}
	}
	
	private void checkStepAndCalories(byte[] data) {
		int[] stepCount = Program.getStepCountData(data);
		
		int currentStepCount = stepCount[0];
		if (currentStepCount == 0) { return; }
		
		Date lastRecordDate = new Date();
		if (mLastRecordedStep != 0) {
			if (currentStepCount > mLastRecordedStep) {
				if ((currentStepCount - mLastRecordedStep) >
					(((lastRecordDate.getTime() - mLastRecordedStepDate.getTime()) / 1000 + 1) * 6)
				   )
				{
					EventLogger.logEvent("Invalid STEP0:0:" + currentStepCount);
					return;
				}
				
				setLastRecordedStep(currentStepCount, lastRecordDate);
			}
			else if (currentStepCount < mLastRecordedStep) {
				if (currentStepCount > 
						(((lastRecordDate.getTime() - mLastRecordedStepDate.getTime()) / 1000 + 1) * 6)
				   )
				{
					EventLogger.logEvent("Invalid STEP0:1:" + currentStepCount);
					return;
				}
				
				setLastRecordedStep(currentStepCount, lastRecordDate);
			}
			else {
				setLastRecordedStep(currentStepCount, lastRecordDate);
			}
		}
		else {
			setLastRecordedStep(currentStepCount, lastRecordDate);
			return;
		}
		
		EventLogger.logStep("STEP 0:" + stepCount[0] 
							+ " LAST:" 
							+ mStepCountManager.getLastStepCount() 
							+ " BASE:"
							+ mStepCountManager.getStepCountBase()
							+ " Step_Count:"
							+ mStepCountManager.getStepCount());
		
		addToHistoryStepCount(currentStepCount);
		int averageStepCount = averageHistoryStepCount();
		if (currentStepCount > averageStepCount * 1.2f) {
			EventLogger.logEvent("Too large step count 0 " + currentStepCount + ":"
					 + MiscUtils.dumpBytesAsString(data));
			return;
		}
		
		System.out.println("action DataStorageService stepCount:" + currentStepCount);
		
		boolean isValid = Program.isValidEcg(data);
		
		Date lastStepDate = mStepCountManager.getLastStepDate();
		if (lastStepDate == null) {
			mStepCountManager.setLastStepDate(new Date());
			mStepCountManager.setStepCountBase(0);
			mStepCountManager.setStepCount(currentStepCount);
			
			stepCheckStateMachine.consume(isValid, currentStepCount);
			
			return;
		}
		
		// 只处理S_FIRST_VALID和S_VALID两种情况
		stepCheckStateMachine.consume(isValid, currentStepCount);
		if ((InvalidStepCheckStateMachine.S_VALID == stepCheckStateMachine.getCurrentState())
		 || (InvalidStepCheckStateMachine.S_FIRST_VALID == stepCheckStateMachine.getCurrentState())) {
		}
		else {
			return;
		}

		
		if (InvalidStepCheckStateMachine.S_FIRST_VALID == stepCheckStateMachine.getCurrentState()) {
			int stepCountBase = mStepCountManager.getStepCountBase();
			int subStepCount = stepCheckStateMachine.getSubtractionStep();
			System.out.println("action Step Check State Machine stepCountBase, subStepCount:" 
							+ stepCountBase + "," + subStepCount);
			mStepCountManager.setStepCountBase(stepCountBase - subStepCount);
		}
		
		if (currentStepCount == 0) {
			GlobalStatus.getInstance().setCurrentStep(currentStepCount);
			GlobalStatus.getInstance().setCalories(0);
			return;
		}

		int lastStepCount = mStepCountManager.getLastStepCount();
		
		Date now = new Date();
		if (DateUtil.isInTheSameDay(now, lastStepDate)) {
			if (currentStepCount + mStepCountManager.getStepCountBase() < lastStepCount) {
				mStepCountManager.setStepCountBase(lastStepCount);
			} else {
			}
			mStepCountManager.setStepCount(currentStepCount);
		}
		else {
//			mStepCountManager.setStepCountBase(0);
//			mStepCountManager.setStepCount(0);
		}
		
		mStepCountManager.setLastStepDate(now);
		
		if (step_state != mStepCountManager.getLastStepCount()) {
			step_state = mStepCountManager.getLastStepCount();

			int calories = GlobalAccelCalculator.getInstance().getCalories();
			
			if (mStepCountManager.getLastStepCount() < 0) {
				GlobalStatus.getInstance().setCalories(0);
				GlobalStatus.getInstance().setCurrentStep(0);				
			}
			else {
			GlobalStatus.getInstance().setCalories(calories);
			GlobalStatus.getInstance().setCurrentStep(mStepCountManager.getLastStepCount());
			
			GlobalStatus.getInstance().setLastCalories(calories);
			GlobalStatus.getInstance().setLastStepCount(mStepCountManager.getLastStepCount());
			}
			
			// Notify UI that new step and calories data are available
			final Intent updateIntent = new Intent(ACTION_UPDATE_STEP_AND_CALORIES);
			sendBroadcast(updateIntent);

			StepCaloriesEntry[] stepCalories = new StepCaloriesEntry[1];
			StepCaloriesEntry stepCaloriesEntry = new StepCaloriesEntry();
			
			stepCaloriesEntry.stepCount 
					= (mStepCountManager.getLastStepCount() >= 0) 
					 ? mStepCountManager.getLastStepCount() : 0;
			if (0 >= mStepCountManager.getLastStepCount()) {
				calories = 0;
			}			
			stepCaloriesEntry.calories = calories;
			stepCalories[0] = stepCaloriesEntry;
			addStepCaloriesToList(stepCaloriesList, stepCalories);
		}
	}
	
	private void checkStepAndCaloriesByNewFrame(byte[] data) {
		int[] stepCount = Program.getStepCountDataByNewFrame(data);
		
		int currentStepCount = stepCount[0];
		if (currentStepCount == 0) { return; }
		System.out.println("action DataStorageService stepCount:" + currentStepCount);
		
		Date lastRecordDate = new Date();
		if (mLastRecordedStep != 0) {
			if (currentStepCount > mLastRecordedStep) {
				if ((currentStepCount - mLastRecordedStep) >
					(((lastRecordDate.getTime() - mLastRecordedStepDate.getTime()) / 1000 + 1) * 6)
				   )
				{
					EventLogger.logEvent("Invalid STEP1:0:" + currentStepCount);
					return;
				}
				
				setLastRecordedStep(currentStepCount, lastRecordDate);
			}
			else if (currentStepCount < mLastRecordedStep) {
				if (currentStepCount > 
						(((lastRecordDate.getTime() - mLastRecordedStepDate.getTime()) / 1000 + 1) * 6)
				   )
				{
					EventLogger.logEvent("Invalid STEP1:1:" + currentStepCount);
					return;
				}
				
				setLastRecordedStep(currentStepCount, lastRecordDate);
			}
			else {
				setLastRecordedStep(currentStepCount, lastRecordDate);
			}
		}
		else {
			setLastRecordedStep(currentStepCount, lastRecordDate);
			return;
		}

//		EventLogger.logStep("STEP 0:" + stepCount[0] + " LAST:" + mStepCountManager.getLastStepCount());
		EventLogger.logStep("STEP 1:" + stepCount[0] 
				+ " LAST:" 
				+ mStepCountManager.getLastStepCount() 
				+ " BASE:"
				+ mStepCountManager.getStepCountBase()
				+ " Step_Count:"
				+ mStepCountManager.getStepCount());
		
		addToHistoryStepCount(currentStepCount);
		int averageStepCount = averageHistoryStepCount();
		if (currentStepCount > averageStepCount * 1.2f) {
			EventLogger.logEvent("Too large step count 1 " + currentStepCount + ":"
					 		+ MiscUtils.dumpBytesAsString(data));
			return;
		}
		
		boolean isValid = Program.isValidEcg(data);
		
		Date lastStepDate = mStepCountManager.getLastStepDate();
		if (lastStepDate == null) {
			mStepCountManager.setLastStepDate(new Date());
			mStepCountManager.setStepCountBase(0);
			mStepCountManager.setStepCount(currentStepCount);
			
			stepCheckStateMachine.consume(isValid, currentStepCount);
			
			return;
		}
		
		// 只处理S_FIRST_VALID和S_VALID两种情况
		stepCheckStateMachine.consume(isValid, currentStepCount);
		if ((InvalidStepCheckStateMachine.S_VALID == stepCheckStateMachine.getCurrentState())
		 || (InvalidStepCheckStateMachine.S_FIRST_VALID == stepCheckStateMachine.getCurrentState())) {
		}
		else {
			return;
		}

		
		if (InvalidStepCheckStateMachine.S_FIRST_VALID == stepCheckStateMachine.getCurrentState()) {
			int stepCountBase = mStepCountManager.getStepCountBase();
			int subStepCount = stepCheckStateMachine.getSubtractionStep();
			System.out.println("action Step Check State Machine stepCountBase, subStepCount:" 
							+ stepCountBase + "," + subStepCount);
			mStepCountManager.setStepCountBase(stepCountBase - subStepCount);
		}
		
		if (currentStepCount == 0) {
			GlobalStatus.getInstance().setCurrentStep(currentStepCount);
			GlobalStatus.getInstance().setCalories(0);
			return;
		}

		int lastStepCount = mStepCountManager.getLastStepCount();
		
		Date now = new Date();
		if (DateUtil.isInTheSameDay(now, lastStepDate)) {
			if (currentStepCount + mStepCountManager.getStepCountBase() < lastStepCount) {
				mStepCountManager.setStepCountBase(lastStepCount);
			} else {
			}
			mStepCountManager.setStepCount(currentStepCount);
		}
		else {
//			mStepCountManager.setStepCountBase(0);
//			mStepCountManager.setStepCount(0);
		}
		
		mStepCountManager.setLastStepDate(now);
		
		if (step_state != mStepCountManager.getLastStepCount()) {
			step_state = mStepCountManager.getLastStepCount();

			int calories = GlobalAccelCalculator.getInstance().getCalories();
			
			if (mStepCountManager.getLastStepCount() < 0) {
				GlobalStatus.getInstance().setCalories(0);
				GlobalStatus.getInstance().setCurrentStep(0);				
			}
			else {
			GlobalStatus.getInstance().setCalories(calories);
			GlobalStatus.getInstance().setCurrentStep(mStepCountManager.getLastStepCount());
			
			GlobalStatus.getInstance().setLastCalories(calories);
			GlobalStatus.getInstance().setLastStepCount(mStepCountManager.getLastStepCount());
			}
			
			// Notify UI that new step and calories data are available
			final Intent updateIntent = new Intent(ACTION_UPDATE_STEP_AND_CALORIES);
			sendBroadcast(updateIntent);

			StepCaloriesEntry[] stepCalories = new StepCaloriesEntry[1];
			StepCaloriesEntry stepCaloriesEntry = new StepCaloriesEntry();
			
			stepCaloriesEntry.stepCount 
					= (mStepCountManager.getLastStepCount() >= 0) 
					 ? mStepCountManager.getLastStepCount() : 0;
			if (0 >= mStepCountManager.getLastStepCount()) {
				calories = 0;
			}			
			stepCaloriesEntry.calories = calories;
			stepCalories[0] = stepCaloriesEntry;
			addStepCaloriesToList(stepCaloriesList, stepCalories);
		}
	}
	
	private void checkStepAndCaloriesDebug(byte[] data) {
		int[] stepCount = Program.getStepCountData(data);
		
		int currentStepCount = stepCount[0];
		System.out.println("action DataStorageService stepCount:" + currentStepCount);
		
		if (currentStepCount == 0) {
			GlobalStatus.getInstance().setCurrentStep(currentStepCount);
			GlobalStatus.getInstance().setCalories(0);
			return;
		}
		
		Date lastStepDate = mStepCountManager.getLastStepDate();
		int lastStepCount = mStepCountManager.getLastStepCount();
		
		if (lastStepDate == null) {
			mStepCountManager.setLastStepDate(new Date());
			mStepCountManager.setStepCountBase(0);
			mStepCountManager.setStepCount(currentStepCount);
			
			return;
		}
		
		Date now = new Date();
		if (DateUtil.isInTheSameDay(now, lastStepDate)) {
			if (currentStepCount + mStepCountManager.getStepCountBase() < lastStepCount) {
				mStepCountManager.setStepCountBase(lastStepCount);
			} else {
			}
			mStepCountManager.setStepCount(currentStepCount);
		}
		else {
			mStepCountManager.setStepCountBase(0);
			mStepCountManager.setStepCount(currentStepCount);
		}
		
		mStepCountManager.setLastStepDate(now);
		
		if (step_state != mStepCountManager.getLastStepCount()) {
			step_state = mStepCountManager.getLastStepCount();

			int calories = GlobalAccelCalculator.getInstance().getCalories();
			GlobalStatus.getInstance().setCalories(calories);
			GlobalStatus.getInstance().setCurrentStep(mStepCountManager.getLastStepCount());
			
			// Notify UI that new step and calories data are available
			final Intent updateIntent = new Intent(ACTION_UPDATE_STEP_AND_CALORIES);
			sendBroadcast(updateIntent);

			StepCaloriesEntry[] stepCalories = new StepCaloriesEntry[1];
			StepCaloriesEntry stepCaloriesEntry = new StepCaloriesEntry();
			
			stepCaloriesEntry.stepCount = mStepCountManager.getLastStepCount();
			if (0 == mStepCountManager.getLastStepCount()) {
				calories = 0;
			}			
			stepCaloriesEntry.calories = calories;
			stepCalories[0] = stepCaloriesEntry;
			addStepCaloriesToList(stepCaloriesList, stepCalories);
		}
		
//		int[] stepCount = Program.getStepCountData(data);
//		System.out.println("action DataStorageService stepCount:" + stepCount[0]);
//		
//		int calories = GlobalAccelCalculator.getInstance().getCalories();
//		if(step_state!=stepCount[0]){
//			step_state=stepCount[0];
//		
//			Intent updateIntent = new Intent(ACTION_UPDATE_STEP_AND_CALORIES);
//			updateIntent.putExtra(STEP_COUNT,stepCount[0]);
//			
////			int calories = GlobalAccelCalculator.getInstance().getCalories();
//
//			System.out.println("action DataStorageService calories[" + calories + "]");
//			updateIntent.putExtra(CALORIES, calories);
//			
//			sendBroadcast(updateIntent);
//			
//			StepCaloriesEntry[] stepCalories = new StepCaloriesEntry[1];
//			StepCaloriesEntry stepCaloriesEntry = new StepCaloriesEntry();
//			stepCaloriesEntry.stepCount = stepCount[0];
//			
//			if (0 == stepCount[0]) {
//				calories = 0;
//			}
//			
//			stepCaloriesEntry.calories = calories;
//			stepCalories[0] = stepCaloriesEntry;
//			addStepCaloriesToList(stepCaloriesList, stepCalories);
//			
//			stepCaloriesEntry = null;
//			stepCalories = null;
//			
////			sendBroadcast(updateIntent);
//		}
//		
//		GlobalStatus.getInstance().setCurrentStep(stepCount[0]);
//		
//		if (0 == stepCount[0]) {
//			GlobalStatus.getInstance().setCalories(0);
//		}
//		else {
//			GlobalStatus.getInstance().setCalories(calories);
//		}
	}
	
	private void saveECGResultToDB(String minuteData) {
		int stressLevel = ecgHeartRateCal.getStress();
//		minuteStressLevelManager.increase(minuteData, stressLevel);
		
		ECGResult ecgResult = new ECGResult();
		ecgResult.LF = ecgHeartRateCal.getLf();
		ecgResult.HF = ecgHeartRateCal.getHf();
		System.out.println("action DataStorageService saveECGResultToDB ["
						+ ecgResult.LF + "," + ecgResult.HF + "]");
//		minuteECGResultManager.increase(minuteData, ecgResult);
	}
	
	private void saveECGResultToDB(String minuteData, String uid) {		
		ECGResult ecgResult = new ECGResult();
		ecgResult.LF = ecgHeartRateCal.getLf();
		ecgResult.HF = ecgHeartRateCal.getHf();
		System.out.println("action DataStorageService saveECGResultToDB ["
						+ ecgResult.LF + "," + ecgResult.HF + "]");
//		minuteECGResultManager.increase(minuteData, ecgResult, uid);
	}
	
	private void startGetStepCountSnapshotTimer() {
//		Intent intent = new Intent(); 
//		intent.setAction(ACTION_ALARM_GET_STEP_COUNT_SNAPSHOT);  
//		
//		AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);		
//		PendingIntent pi=PendingIntent.getBroadcast(this, 0, intent,0);
//		
//		Calendar c=Calendar.getInstance();
//		long triggerAtTime=c.getTimeInMillis();
//
////		am.setRepeating(AlarmManager.RTC_WAKEUP, triggerAtTime, 1000 * 1, pi);
//		am.setRepeating(AlarmManager.RTC, triggerAtTime, 1000 * 1, pi);
	}
	
	private void stopGetStepCountSnapshotTimer() {
//		Intent intent = new Intent();  
//		intent.setAction(ACTION_ALARM_GET_STEP_COUNT_SNAPSHOT);  
//		PendingIntent sender=PendingIntent.getBroadcast(this, 0, intent, 0);  
//		AlarmManager alarm=(AlarmManager)getSystemService(ALARM_SERVICE);  
//		alarm.cancel(sender);		
	}

	private void notifyUITumbleHappened() {
		Intent intent = new Intent(ACTION_TUMBLE_HAPPENED);
		Date now = new Date();
		intent.putExtra(TUMBLE_TIME, now.getTime());
		sendBroadcast(intent);
	}
	
	private void updateStandStill(int posture, int gait) {
		if (mIsFirstStandStill) {
			if (gait == 1) {
				if (posture == 20) {
					mStandStill = STAND_STILL_STAND;
				}
				else if (posture == 21) {
					mStandStill = STAND_STILL_SIT;
				}
				else {
					mStandStill = STAND_STILL_UNKNOWN;
				}
			} else {
				mStandStill = STAND_STILL_UNKNOWN;
			}			
			
			mPrevStandStillDate = new Date();
			mStandStillTime = 0;
			
			mIsFirstStandStill = false;	
			
			GlobalStatus.getInstance().setStandStillTime(0);
			return ;
		}
		
		Date now = new Date();
		if (gait == 1) {
			if (posture == 20) {
				if (mStandStill == STAND_STILL_STAND) {
					mStandStillTime += (now.getTime() - mPrevStandStillDate.getTime());
				}
				else {
					mStandStill = STAND_STILL_STAND;
					mStandStillTime = 0;
				}				
			}
			else if (posture == 21) {
				if (mStandStill == STAND_STILL_SIT) {
					mStandStillTime += (now.getTime() - mPrevStandStillDate.getTime());
				}
				else {
					mStandStill = STAND_STILL_SIT;
					mStandStillTime = 0;
				}					
			}
			else {
				mStandStill = STAND_STILL_UNKNOWN;
				mStandStillTime = 0;
			}
		}
		else {
			mStandStill = STAND_STILL_UNKNOWN;
			mStandStillTime = 0;
		}				
		
		GlobalStatus.getInstance().setStandStillTime(mStandStillTime);
		notifyUIStandStill(mStandStill, mStandStillTime);
		mPrevStandStillDate = now;
	}
	
	private void notifyUIStandStill(int standStillPosture, long standStillTime) {
		final Intent intent = new Intent(ACTION_STAND_STILL_DATA_AVAILABLE);
		intent.putExtra(STAND_STILL_POSTURE, standStillPosture);
		intent.putExtra(STAND_STILL_TIME, standStillTime);
		
		sendBroadcast(intent);
	}
	
	private WakeLock wakeLock = null;
	private void acquireWakeLock(){
		if(wakeLock==null){
			PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);
			wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,this.getClass().getCanonicalName());
			wakeLock.acquire();
		}
	}
	private void releaseWarkLock(){
		if(wakeLock!=null&&wakeLock.isHeld()){
				wakeLock.release();
				wakeLock=null;
		}
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		flags = START_STICKY;
		return super.onStartCommand(intent, flags, startId);
	}

	private void reastOncreat() {
		Notification notification = new Notification();
		startForeground(1, notification);
	}
	
	private synchronized void persistData(String nowStr) {
		synchronized (lockPersist) {
		if (UserInfo.getIntance().getUserData().getLogin_mode() == 1) {
		} else {
//		saveFrame60(DateUtil.parseDateToInput(now, Program.MINUTE_FORMAT), mUID);
//		saveFrame90(DateUtil.parseDateToInput(now,Program.MINUTE_FORMAT), mUID);
//		saveFrame60(nowStr, mUID);
//		saveFrame90(nowStr, mUID);
		}
		
		ecgList.clear();
		heartRateList.clear();
		respList.clear();
		
		tumbleDatalist.clear();
//		tmblStrList.clear();
		
		temDatalist.clear();
		voltageList.clear();
		accelerationXlist.clear();
		accelerationYlist.clear();
		accelerationZlist.clear();
		accelList.clear();
		
		hteWarningList.clear();
		stepCaloriesList.clear();
		}
	}
	
	private boolean isLastUploadLastMinute(String lastUpload)
	{
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.SECOND, 0);
		calendar.add(Calendar.MINUTE, -1);
		
		Date lastMinute = calendar.getTime();
		
		SimpleDateFormat sdf = new SimpleDateFormat(Program.MINUTE_FORMAT);
		String strLastMinute = sdf.format(lastMinute);
		
		return strLastMinute.equals(lastUpload);
	}
	
	private void setTumbleFlag() {
		SharedPreferences.Editor editor = spTumbleFlag.edit();
		editor.putInt(TUMBLE_FLAG, TUMBLE_FLAG_ON);
		Date now = new Date();
		editor.putLong(TUMBLE_TIME, now.getTime());
		editor.commit();
	}
	
	private void initEcgMode() {
		mLastEcgMode = MODE_ECG_ECG;
		mCurrentEcgMode = MODE_ECG_ECG;
	}
	
	private void checkEcgModeChanged() {
		GlobalStatus.getInstance().setCurrentECGMode(mCurrentEcgMode);
		if (mLastEcgMode == mCurrentEcgMode) {
			// Do nothing;
		}
		else {
//			if (mCurrentEcgMode == MODE_ECG_HEART_RATE) {
//				mCompressedFilePool.flushEcgFile();
//				mCompressedFilePool.createHeartRateFile();
//			}
//			else if (mCurrentEcgMode == MODE_ECG_ECG) {
//				mCompressedFilePool.flushHeartRate();
//				mCompressedFilePool.createECGFile();
//			}
			if (UserInfo.isInDebugMode()) {
			} else {
				if (mCompressedFilePool.getMinuteData() != null) {
					persistData(mCompressedFilePool.getMinuteData());
					
					mCompressedFilePool.flushCompressFiles();
				}
			}
		}
		mLastEcgMode = mCurrentEcgMode;
	}
	
	private void addToHistoryStepCount(int stepCount) {
		historyStepCounts.offer(stepCount);
		
		int popCount = historyStepCounts.size() - 10;
		for (int i = 0; i < popCount; i++) {
			historyStepCounts.poll();
		}
	}

	private int averageHistoryStepCount() {
		if (historyStepCounts.size() == 0) {
			return 0;
		}
		int sum = 0;
		for (Integer iVal: historyStepCounts) {
			sum += iVal;
		}
		
		return sum / historyStepCounts.size();
	}
	
	private void clearLastRecordedStep() {
		mLastRecordedStep = 0;
		mLastRecordedStepDate = null;
	}
	private void setLastRecordedStep(int step, Date date) {
		mLastRecordedStep = step;
		mLastRecordedStepDate = date;		
	}
}
