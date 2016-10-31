package com.cts.dronetest.src.activities;

/**
 * Created by cts on 23-9-16.
 */

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.cts.dronetest.R;
import com.cts.dronetest.src.drone.BebopDrone;
import com.cts.dronetest.src.view.BebopVideoView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;


public class BebopActivity extends AppCompatActivity {
    private static final String TAG = "BebopActivity";
    private BebopDrone mBebopDrone;

    private ProgressDialog mConnectionProgressDialog;
    private ProgressDialog mDownloadProgressDialog;

    private BebopVideoView mVideoView;

    private TextView mBatteryLabel;
    private Button mTakeOffLandBt, mEmergencyBtn;
    private Button mDownloadBt, mTakePictureBtn;
    private Button mAutoBtn;
    private Button mLeftBtn, mRightBtn, mFrontBtn, mBackBtn;
    private Button mYawLeftBtn, mYawRightBtn, mGazUpBtn, mGazDownBtn;
    private Button mRollLeftBtn, mRollRightBtn;
    private Button mInfoBtn;
    private Button mCamUp, mCamDown;
    public TextView mInfoText, mLogText;
    public Handler statusHandler;
    private TimerTask timerTask;
    private int mNbMaxDownload;
    private int mCurrentDownloadIndex;
    int timestamp = 0;
    private AutoMatedMovementTask callbackTask;

    public static float pressedIncr = 0.0f;
    public final float dX = 1.0f;

    public final float dZ = 0.2f;
    public final byte gazUp = (byte) 25;
    public final byte gazDown = (byte) (-25);
    public final byte ONE = (byte) 1;
    public final byte ZERO = (byte) 0;
    public final byte yawRight = (byte) 50;
    public final byte yawLeft = (byte) (-50);
    public final byte rollRight = (byte) 60;
    public final byte rollLeft = (byte) (-60);
    public final byte pitchFwd = (byte) (50);
    public final byte pitchBcwd = (byte) (-50);
    public final float camTiltDegrees = 10.f;
    float metps = 0.5f;
    //        float radps = 0.628319f; // 36 degrees per second.
    float radps = 1.0f; // 72 degrees per second.
    float maxAlt = 1.0f;
    float maxIncl = 25.f;
    public int seq = 0;
    private double samples = 5;
    public float dY = dX * 2 * (float) Math.sin(Math.PI / samples);

    public float angle = (float) Math.toDegrees(Math.asin(dX / Math.hypot(dX, dY)));
    public float heading = (float) (2 * Math.PI / samples);
    // DECLARE MOTION EVENTS ///
    MotionEvent pitchBackEvent;
    MotionEvent pitchFwdEvent;
    MotionEvent release;
    MotionEvent oneSecondTouchEvent;


    public void initEvents() {
        // Simulate event for 1 second.
        long downTime = SystemClock.uptimeMillis();
        long releaseTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis() + 2000;
        long releaseEvent = SystemClock.uptimeMillis() + 100;
        float x = 0.0f, y = 0.0f;
        int metaState = 0;

        oneSecondTouchEvent = MotionEvent.obtain(
                downTime,
                eventTime,
                MotionEvent.ACTION_DOWN,
                x,
                y,
                metaState
        );

        release = MotionEvent.obtain(
                releaseTime,
                releaseEvent,
                MotionEvent.ACTION_UP,
                x,
                y,
                metaState
        ); //

        pitchBackEvent = MotionEvent.obtain(
                downTime,
                eventTime,
                MotionEvent.ACTION_DOWN,
                x,
                y,
                metaState
        );// Pitch for 1 second.


        pitchFwdEvent = MotionEvent.obtain(
                downTime,
                eventTime,
                MotionEvent.ACTION_DOWN,
                x,
                y,
                metaState
        );// Pitch for 1 second.
    }


    private int mInterval = 1000;

    void startRepeatingTask() {
        mStatusChecker.run();
    }

    void stopRepeatingTask() {
        statusHandler.removeCallbacks(mStatusChecker);
    }

    Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            try {
                mInfoBtn.performClick();
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                statusHandler.postDelayed(mStatusChecker, mInterval);
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bebop);
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                // do your magic
                Log.d(TAG, "CAUGHT EXCEPTION " + throwable.getCause());
//                mBebopDrone.land();
                mBebopDrone.emergency();
                mBebopDrone.dispose();
                mBebopDrone.disconnect();
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        initIHM();
        initEvents();
        Intent intent = getIntent();
        ARDiscoveryDeviceService service = intent.getParcelableExtra(MainActivity.EXTRA_DEVICE_SERVICE);
        mBebopDrone = new BebopDrone(this, service);
        mBebopDrone.activity = this;
        mBebopDrone.addListener(mBebopListener);
        Date current = new Date();
        String date = new SimpleDateFormat("dd-MM-yyyy").format(current);
        String time = new SimpleDateFormat("hh:mm:ss").format(current);
        Log.e(TAG, "Setting date:" + date + ":" + time);

        mBebopDrone.setDate(date, time);
        mBebopDrone.changeRun(ONE);

        statusHandler = new Handler();
        startRepeatingTask();


    }

    @Override
    protected void onStart() {
        super.onStart();

        // show a loading view while the bebop drone is connecting
        if ((mBebopDrone != null) && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(mBebopDrone.getConnectionState()))) {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Connecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();

            // if the connection to the Bebop fails, finish the activity
            if (!mBebopDrone.connect()) {
                finish();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mBebopDrone != null) {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Disconnecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();
            stopRepeatingTask();
            if (!mBebopDrone.disconnect()) {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        mBebopDrone.land();
        mBebopDrone.changeRun(ZERO);
        mBebopDrone.dispose();
        mBebopDrone.disconnect();
        stopRepeatingTask();
        statusHandler.removeCallbacks(mStatusChecker);
        super.onDestroy();
    }


    private void initIHM() {

        mCamUp = (Button) findViewById(R.id.camUp);
        mCamDown = (Button) findViewById(R.id.camDown);

        mCamUp.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mBebopDrone.setCameraOrientation((byte) camTiltDegrees, ZERO);
                        v.setPressed(true);
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        break;
                    default:
                        break;

                }
                return true;

            }
        });

        mCamDown.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        mBebopDrone.setCameraOrientation((byte) -camTiltDegrees, ZERO);

                        v.setPressed(true);
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        break;
                    default:
                        break;

                }
                return true;

            }
        });
        mVideoView = (BebopVideoView) findViewById(R.id.videoView);

        mInfoBtn = (Button) findViewById(R.id.getInfoBtn);

        mInfoText = (TextView) findViewById(R.id.infoTextView);
        mLogText = (TextView) findViewById(R.id.logTextView);

        mInfoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mInfoText.setText("Current timestamp:" + timestamp + "\n" + mBebopDrone.toString());
            }
        });


        mFrontBtn = (Button) findViewById(R.id.forwardBt);
        mBackBtn = (Button) findViewById(R.id.backBt);
        mLeftBtn = (Button) findViewById(R.id.rollLeftBt);
        mRightBtn = (Button) findViewById(R.id.rollRightBt);
        mAutoBtn = (Button) findViewById(R.id.autoButton);

        mEmergencyBtn = (Button) findViewById(R.id.emergencyBt);
        mEmergencyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBebopDrone.emergency();
            }
        });

        mTakeOffLandBt = (Button) findViewById(R.id.takeOffOrLandBt);
        mTakeOffLandBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                switch (mBebopDrone.getFlyingState()) {
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                        mBebopDrone.takeOff();
                        break;
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                        mBebopDrone.land();
                        break;
                    default:
                }
            }
        });
        mTakePictureBtn = (Button) findViewById(R.id.takePictureBt);
        mTakePictureBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBebopDrone.takePicture();
            }
        });

        mDownloadBt = (Button) findViewById(R.id.downloadBt);
        mDownloadBt.setEnabled(false);
        mDownloadBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBebopDrone.getLastFlightMedias();

                mDownloadProgressDialog = new ProgressDialog(BebopActivity.this, R.style.AppCompatAlertDialogStyle);
                mDownloadProgressDialog.setIndeterminate(true);
                mDownloadProgressDialog.setMessage("Fetching medias");
                mDownloadProgressDialog.setCancelable(false);
                mDownloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mBebopDrone.cancelGetLastFlightMedias();
                    }
                });
                mDownloadProgressDialog.show();
            }
        });
        mGazUpBtn = (Button) findViewById(R.id.gazUpBt);
        mGazUpBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setGaz(gazUp);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setGaz(ZERO);
                        break;

                    default:

                        break;
                }

                return true;
            }

        });
        mGazDownBtn = (Button) findViewById(R.id.gazDownBt);
        mGazDownBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setGaz(gazDown);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setGaz(ZERO);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });
        mYawLeftBtn = (Button) findViewById(R.id.yawLeftBt);
        mYawLeftBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setYaw(yawLeft);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setYaw(ZERO);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });
        mYawRightBtn = (Button) findViewById(R.id.yawRightBt);
        mYawRightBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setYaw(yawRight);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setYaw(ZERO);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        mFrontBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setPitch(pitchFwd);
                        mBebopDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setPitch(ZERO);
                        mBebopDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });
        mBackBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mBebopDrone.setPitch(pitchBcwd);
                        mBebopDrone.setFlag((byte) 1);
                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setPitch(ZERO);
                        mBebopDrone.setFlag((byte) 0);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });
        mRollLeftBtn = (Button) findViewById(R.id.rollLeftBt);


        mRollLeftBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);

                        mBebopDrone.setRoll(rollLeft);
                        mBebopDrone.setFlag(ONE);

                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setRoll(ZERO);
                        mBebopDrone.setFlag(ZERO);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });


        mRollRightBtn = (Button) findViewById(R.id.rollRightBt);

        mRollRightBtn.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);

                        mBebopDrone.setRoll(rollRight);
                        mBebopDrone.setFlag(ONE);

                        break;

                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        mBebopDrone.setRoll(ZERO);
                        mBebopDrone.setFlag(ZERO);
                        break;

                    default:

                        break;
                }

                return true;
            }
        });

        mBatteryLabel = (TextView) findViewById(R.id.batteryLabel);
        mAutoBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBebopDrone.takeOff();
                mTakeOffLandBt.setEnabled(true);
                mTakeOffLandBt.setText("Land");


                // Samples,dX,dZ
                AutomatedVariables current = new AutomatedVariables(10, 1.5f, mBebopDrone.currentAlt,1.5f);
                AutomatedVariables next = null;
//                AutomatedVariables next = new AutomatedVariables(5, -0.5f, mBebopDrone.currentAlt + 0.5f,1.0f);
                callbackTask = new AutoMatedMovementTask(current, next);

                callbackTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
                Log.e(TAG, "MOVE BACK AND START");
            }
        });
    }


    private final BebopDrone.Listener mBebopListener = new BebopDrone.Listener() {


        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state) {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    mConnectionProgressDialog.dismiss();
                    break;

                case ARCONTROLLER_DEVICE_STATE_STOPPED:
                    // if the deviceController is stopped, go back to the previous activity
                    mConnectionProgressDialog.dismiss();
                    finish();
                    break;

                default:
                    break;
            }
        }

        @Override
        public void onBatteryChargeChanged(int batteryPercentage) {
            mBatteryLabel.setText(String.format("%d%%", batteryPercentage));
        }

        @Override
        public void onPilotingStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
            switch (state) {
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                    mTakeOffLandBt.setText("Take off");
                    mTakeOffLandBt.setEnabled(true);
                    mDownloadBt.setEnabled(true);
                    mAutoBtn.setEnabled(true);
                    break;
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                    mTakeOffLandBt.setText("Land");
                    mTakeOffLandBt.setEnabled(true);
                    mDownloadBt.setEnabled(false);
//                    mAutoBtn.setEnabled(false);
                    break;
                default:
//                    if(autoTask2!=null && taskFinished==false){
//                        autoTask2.cancel(true);
//                    }
                    mAutoBtn.setEnabled(false);
                    mTakeOffLandBt.setEnabled(false);
                    mDownloadBt.setEnabled(false);
            }
        }


        @Override
        public void onPictureTaken(ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
            Log.i(TAG, "Picture has been taken");
            Log.e(TAG, "Error:" + error.name());
        }

        @Override
        public void configureDecoder(ARControllerCodec codec) {
            mVideoView.configureDecoder(codec);
        }

        @Override
        public void onFrameReceived(ARFrame frame) {
            mVideoView.displayFrame(frame);
        }

        @Override
        public void onMatchingMediasFound(int nbMedias) {
            mDownloadProgressDialog.dismiss();
            mNbMaxDownload = nbMedias;
            mCurrentDownloadIndex = 1;

            if (nbMedias > 0) {
                mDownloadProgressDialog = new ProgressDialog(BebopActivity.this, R.style.AppCompatAlertDialogStyle);
                mDownloadProgressDialog.setIndeterminate(false);
                mDownloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mDownloadProgressDialog.setMessage("Downloading medias");
                mDownloadProgressDialog.setMax(mNbMaxDownload * 100);
                mDownloadProgressDialog.setSecondaryProgress(mCurrentDownloadIndex * 100);
                mDownloadProgressDialog.setProgress(0);
                mDownloadProgressDialog.setCancelable(false);
                mDownloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mBebopDrone.cancelGetLastFlightMedias();
                    }
                });
                mDownloadProgressDialog.show();
            }
        }

        @Override
        public void onDownloadProgressed(String mediaName, int progress) {
            mDownloadProgressDialog.setProgress(((mCurrentDownloadIndex - 1) * 100) + progress);
        }

        @Override
        public void onDownloadComplete(String mediaName) {
            mCurrentDownloadIndex++;
            mDownloadProgressDialog.setSecondaryProgress(mCurrentDownloadIndex * 100);

            if (mCurrentDownloadIndex > mNbMaxDownload) {
                mDownloadProgressDialog.dismiss();
                mDownloadProgressDialog = null;
            }
        }
    };

    public class AutoMatedMovementTask extends AsyncTask<Object, Object, Object> {

        AutomatedVariables current;
        AutomatedVariables next;

        public AutoMatedMovementTask(AutomatedVariables current, AutomatedVariables next) {



            this.current = current;
            this.next = next;
            mBebopDrone.setMaxRotationSpeed(current.maxRotation);
            mBebopDrone.setMaxVerticalSpeed(current.maxVertical);
            mBebopDrone.setMaxAltitude(current.maxAltitude);
            mBebopDrone.setInclination(current.maxInclination);
            mBebopDrone.setCameraOrientation((byte)-current.camTilt,ZERO);

        }

        private void waitOnCallback() {
            long start = System.currentTimeMillis();
            long current = System.currentTimeMillis();
            long prev = 0;
            while (!mBebopDrone.moveByCallback) {
                long dt = Math.abs(start - current) / 1000;
                if (dt == 15) {
                    break;
                } else if (Math.abs(prev - dt) == 1) {
                    Log.e(TAG, "Waiting on callback for " + dt + " seconds");
                }
                prev = dt;
                current = System.currentTimeMillis();

            }
            mBebopDrone.moveByCallback = false;
        }

        @Override
        protected void onPreExecute() {

            try {

                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

//            }

            Log.e(TAG, "Moving back " + current.dX + "X radius");
            Log.e(TAG, "Y distance per capture:" + dY + " Angle positioning:" + current.heading / 2);
            Log.e(TAG, "Altitude is " + current.dZ);
        }

        @Override
        protected void onPostExecute(Object result) {
            super.onPostExecute(result);
            mBebopDrone.setCameraOrientation((byte)current.camTilt,ZERO);
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (next != null) {
                if (next.dZ != current.dZ) {
                    mBebopDrone.moveToPosition(0.0f, 0.0f, next.dZ, 0.0f);
                    waitOnCallback();
                    Log.e(TAG, "DOING DOME MOVEMENT, CHANGED ALTITUDE");

                }
                Log.e(TAG, "STARTING NEW DRONE AUTOMATED RUN");
                new AutoMatedMovementTask(next, null);
            }
            else{
                Log.e(TAG,"NO NEW MOVEMENT");
                mBebopDrone.land();
            }
        }


        @Override
        protected Object doInBackground(Object[] objects) {
            mBebopDrone.setFlag(ZERO);

            try {
                TimeUnit.SECONDS.sleep(3);
                mBebopDrone.moveToPosition(-current.dX, 0.0f, 0.0f, 0.0f);
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            waitOnCallback();
            int cnt = 0;
            int iter = 0;
            while (iter < current.samples + 1) {
                Log.e(TAG, "Doing turn toward roll position:" + cnt++);
                mBebopDrone.moveToPosition(0.0f, 0.0f, 0.0f, -current.heading / 2);
                waitOnCallback();
//                try {
//                    TimeUnit.SECONDS.sleep(1);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
                mBebopDrone.moveToPosition(0.0f, current.dY, 0.0f, 0.0f);
                Log.e(TAG, "Doing roll:" + cnt++);
                waitOnCallback();
                mBebopDrone.takePicture();
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mBebopDrone.moveToPosition(0.0f, 0.0f, 0.0f, -current.heading / 2);
                waitOnCallback();
                Log.e(TAG, "Picture captured");
                Log.e(TAG, "Doing turn toward object:" + cnt++);
                Log.e(TAG, "Finished 1 turn: " + 2 + " second for next roll:" + (++iter));
                cnt = 0;
            }
            return null;
        }

    }


    public void changeUI(int state) {

        mFrontBtn.setVisibility(state);
        mBackBtn.setVisibility(state);
        mLeftBtn.setVisibility(state);
        mRightBtn.setVisibility(state);
        mTakeOffLandBt.setVisibility(state);
        mYawLeftBtn.setVisibility(state);
        mYawRightBtn.setVisibility(state);
        mDownloadBt.setVisibility(state);
        mTakePictureBtn.setVisibility(state);
    }


    public class AutomatedVariables {
        float startingDistance;
        float maxVertical;
        float dX;
        //        private float dY = dX * 2 * (float) Math.sin( Math.PI / samples);
        float dY;
        float dZ;
        float heading;
        int samples;
        float maxRotation;
        float maxAltitude;

        float maxInclination;
        float camTilt;

//        public float heading = (float) (2 * Math.PI / samples);

        public AutomatedVariables() {

        }

        public AutomatedVariables(int samples, float dX, float dZ,float startingDistance) {
            this.dX = dX;
            this.samples = samples;
            this.heading = (float) (2 * Math.PI / samples);
            this.dY = dX * 2 * (float) Math.sin(Math.PI / samples);
            this.heading = (float) (2 * Math.PI / samples);
            this.maxRotation = radps;
            this.maxAltitude = maxAlt;
            this.maxInclination = maxIncl;
            this.maxVertical = metps;
            this.startingDistance = startingDistance;
            this.camTilt = (float)Math.toDegrees((float)(dZ/Math.hypot(this.startingDistance,dZ)));
        }

        public AutomatedVariables(float dX, float dZ, int samples, float maxRotation, float maxAltitude, float maxInclination, float maxVertical) {
            this.maxVertical = maxVertical;
            this.samples = samples;
            this.dX = dX;
            this.dY = dX * 2 * (float) Math.sin(Math.PI / samples);
            this.heading = (float) (2 * Math.PI / samples);
            this.dZ = dZ;

            this.maxRotation = maxRotation;
            this.maxAltitude = maxAltitude;
            this.maxInclination = maxInclination;
            this.camTilt = (float)Math.toDegrees((float)(dZ/Math.hypot(dX,dZ)));
        }

        public AutomatedVariables(float dX, float dY, float dZ, float heading, int samples, float maxRotation, float maxAltitude, float maxInclination, float maxVertical) {
            this.maxVertical = maxVertical;
            this.dX = dX;
            this.dY = dY;
            this.dZ = dZ;
            this.heading = heading;
            this.samples = samples;
            this.maxRotation = maxRotation;
            this.maxAltitude = maxAltitude;
            this.maxInclination = maxInclination;
            this.camTilt = (float)Math.toDegrees((float)(dZ/Math.hypot(dX,dZ)));
        }
    }
}
