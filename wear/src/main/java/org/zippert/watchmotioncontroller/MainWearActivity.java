package org.zippert.watchmotioncontroller;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageApi.SendMessageResult;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.wearable.view.WatchViewStub;
import android.util.FloatMath;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class MainWearActivity extends Activity implements SensorEventListener, ConnectionCallbacks, OnConnectionFailedListener {

    private TextView mTextView;
    private Button mPlay, mPause, mPrev, mNext;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private float[] mGravity;
    private float mAccel;
    private float mAccelCurrent;
    private float mAccelLast;
    private GoogleApiClient mWearableApiClient;
    private TimerHandler mTimerHandler;

    private enum ConnectStatus {
        NOT_CONNECTED, CONNECTED, CONNECT_FAILED, CONNECT_SUSPENDED
    }

    private static final String NEXT = "DO_NEXT/NEXT";
    private static final String PREV = "DO_NEXT/PREV";
    private static final String PAUSE = "DO_NEXT/PAUSE";
    private static final String PLAY = "DO_NEXT/PLAY";


    private ConnectStatus mConnectStatus = ConnectStatus.NOT_CONNECTED;


    private static class TimerHandler extends Handler {
        static final int ADD = 0;
        static final int CLEAR = 1;
        static final int COLLATE = 2;
        private int mShakeCounter = 0;
        private boolean mListenForAdd = true;
        private Vibrator mVibrator;
        private WeakReference<GoogleApiClient> mRefApiClient;
        private WeakReference<MainWearActivity> mRefWearActivity;
        private static final long[] SINGLE_BUZZ = new long[]{0, 200};
        private static final long[] DUAL_BUZZ = new long[]{0, 200, 200, 200};
        private static final long[] TRIPLE_BUZZ = new long[]{0, 200, 200, 200, 200, 200};

        public TimerHandler(GoogleApiClient wearableApiClient, MainWearActivity wearActivity) {
            mRefApiClient = new WeakReference<GoogleApiClient>(wearableApiClient);
            mRefWearActivity = new WeakReference<MainWearActivity>(wearActivity);
            mVibrator = (Vibrator)wearActivity.getSystemService(Context.VIBRATOR_SERVICE);
        }

        @Override
        public void handleMessage(Message msg) {
            String debugText = null;
            switch (msg.what) {
                case ADD:

                    if (mListenForAdd) {
                        debugText = "Adding...";
                        mVibrator.vibrate(SINGLE_BUZZ, -1);
                        mShakeCounter++;
                        mListenForAdd = false;
                        this.sendEmptyMessageDelayed(CLEAR, 500);
                        this.removeMessages(COLLATE);
                        this.sendEmptyMessageDelayed(COLLATE, 2000);

                    }
                    break;
                case CLEAR:
                    debugText = "Pausing...";
                    mListenForAdd = true;
                    break;
                case COLLATE:
                    debugText = "Finishing: " + mShakeCounter;
                    analyzeAndSendMotionData();
                    mShakeCounter = 0;
                    break;
            }
            MainWearActivity act = mRefWearActivity.get();
            if(act != null){
                act.setDebugText(debugText);
            }
        }

        private void analyzeAndSendMotionData() {
            final GoogleApiClient client = mRefApiClient.get();
            String event = null;


            switch (mShakeCounter) {
                case 0:
                    //Should not happen
                    break;
                case 1:
                    mVibrator.vibrate(SINGLE_BUZZ,-1);
                    event = PLAY;
                    break;
                case 2:
                    mVibrator.vibrate(DUAL_BUZZ,-1);
                    event = NEXT;
                    break;
                case 3:
                    mVibrator.vibrate(TRIPLE_BUZZ,-1);
                    event = PREV;
                    break;
            }

            if (client != null && event != null) {
                final String theEvent = event;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        List<Node> nodes = Wearable.NodeApi.getConnectedNodes(client).await()
                                .getNodes();
                        for (Node n : nodes) {
                            Wearable.MessageApi
                                    .sendMessage(client, n.getId(), theEvent, null).await();
                        }
                    }
                }).start();

            }

        }
    }

    void setDebugText(String debugText){
        if(mTextView != null && debugText != null) {
            mTextView.setText(debugText);
        }
    }

    private final OnClickListener mButtonClicker = new OnClickListener() {
        @Override
        public void onClick(final View v) {
            if (mConnectStatus == ConnectStatus.CONNECTED) {
                new AsyncTask<Void, Void, ArrayList<SendMessageResult>>() {
                    @Override
                    protected ArrayList<SendMessageResult> doInBackground(Void... params) {
                        List<Node> nodes = Wearable.NodeApi.getConnectedNodes(mWearableApiClient)
                                .await().getNodes();
                        ArrayList<SendMessageResult> results = new ArrayList<SendMessageResult>();
                        String path = getPathFromButton(v);
                        for (Node n : nodes) {
                            results.add(Wearable.MessageApi
                                    .sendMessage(mWearableApiClient, n.getId(), path, null)
                                    .await());
                        }

                        return results;
                    }

                    @Override
                    protected void onPostExecute(ArrayList<SendMessageResult> results) {
                        StringBuffer buffer = new StringBuffer();
                        buffer.append("Results: \n");
                        for (SendMessageResult result : results) {
                            if (result.getRequestId() == MessageApi.UNKNOWN_REQUEST_ID) {
                                buffer.append("Failed sendMessage\n");
                            } else {
                                buffer.append("Success: " + result.getRequestId() + "\n");
                            }
                        }
                        mTextView.setText(buffer.toString());
                    }
                }.execute();


            } else {
                mTextView.setText("Not connected: " + mConnectStatus);
            }
        }
    };

    private String getPathFromButton(View view) {
        if (view == mPlay) {
            return PLAY;
        } else if (view == mPause) {
            return PAUSE;
        } else if (view == mPrev) {
            return PREV;
        } else if (view == mNext) {
            return NEXT;
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_wear);
        final WatchViewStub stub = (WatchViewStub)findViewById(R.id.watch_view_stub);
        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {

                mTextView = (TextView)stub.findViewById(R.id.text);
                mPlay = (Button)stub.findViewById(R.id.play);
                mPause = (Button)stub.findViewById(R.id.pause);
                mPrev = (Button)stub.findViewById(R.id.prev);
                mNext = (Button)stub.findViewById(R.id.next);
                mPlay.setOnClickListener(mButtonClicker);
                mPause.setOnClickListener(mButtonClicker);
                mPrev.setOnClickListener(mButtonClicker);
                mNext.setOnClickListener(mButtonClicker);
            }
        });
        getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
        mAccel = 0.00f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;
        mWearableApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(Wearable.API).addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).build();
        mTimerHandler = new TimerHandler(mWearableApiClient, this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (mTextView != null && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mGravity = event.values.clone();
            // Shake detection
            float x = mGravity[0];
            float y = mGravity[1];
            float z = mGravity[2];
            mAccelLast = mAccelCurrent;
            mAccelCurrent = FloatMath.sqrt(x * x + y * y + z * z);
            float delta = mAccelCurrent - mAccelLast;
            mAccel = mAccel * 0.9f + delta;
            // Make this higher or lower according to how much
            // motion you want to detect

            if (mAccel > 10) {
                mTimerHandler.sendEmptyMessage(TimerHandler.ADD);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    protected void onStart() {
        super.onStart();
        mWearableApiClient.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWearableApiClient.unregisterConnectionCallbacks(this);
        mWearableApiClient.unregisterConnectionFailedListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        mTimerHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onConnected(Bundle bundle) {
        mConnectStatus = ConnectStatus.CONNECTED;
    }

    @Override
    public void onConnectionSuspended(int i) {
        mConnectStatus = ConnectStatus.CONNECT_SUSPENDED;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        mConnectStatus = ConnectStatus.CONNECT_FAILED;
    }

}
