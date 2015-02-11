package org.zippert.watchmotioncontroller;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.wearable.MessageApi.MessageListener;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;

public class PlaybackService extends Service implements MessageListener, ConnectionCallbacks,
        OnConnectionFailedListener{

    private static final String NEXT = "DO_NEXT/NEXT";
    private static final String PREV = "DO_NEXT/PREV";
    private static final String PAUSE = "DO_NEXT/PAUSE";
    private static final String PLAY = "DO_NEXT/PLAY";

    static final String INTENT_ACTION_START = "org.zippert.watchmotioncontroller.startservice";
    static final String INTENT_ACTION_STOP = "org.zippert.watchmotioncontroller.stopservice";


    private GoogleApiClient mWearableApiClient;

    public PlaybackService() {
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(INTENT_ACTION_STOP.equalsIgnoreCase(action)){
                stopSelf();
            }

        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mWearableApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(Wearable.API).addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).build();
        registerReceiver(mBroadcastReceiver, new IntentFilter(INTENT_ACTION_START));
        registerReceiver(mBroadcastReceiver, new IntentFilter(INTENT_ACTION_STOP));

        Wearable.MessageApi.addListener(mWearableApiClient, this);
        mWearableApiClient.connect();

        return START_STICKY;
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onDestroy() {
        mWearableApiClient.unregisterConnectionCallbacks(this);
        mWearableApiClient.unregisterConnectionFailedListener(this);
        super.onDestroy();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        KeyEvent event;
        if (messageEvent.getPath().equals(NEXT)) {
            event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
        } else if (messageEvent.getPath().equals(PREV)) {
            event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        } else if (messageEvent.getPath().equals(PAUSE)) {
            event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        } else if (messageEvent.getPath().equals(PLAY)) {
            event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        } else {
            event = null;
        }

        if(event != null) {
            intent.putExtra(Intent.EXTRA_KEY_EVENT, event);
            sendBroadcast(intent);
        }


    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}
