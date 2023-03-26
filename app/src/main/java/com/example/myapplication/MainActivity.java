package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.content.Intent;
public class MainActivity extends AppCompatActivity implements ServiceConnection {
    private RecordingService recordingService;
    private boolean isServiceBound;
    private static long MAX_DURATION_MS = 5 * 60 * 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button startButton = findViewById(R.id.startButton);
        Button stopButton = findViewById(R.id.stopButton);
        Button saveButton = findViewById(R.id.saveButton);

            startButton.setOnClickListener(v -> {
                Intent startIntent = new Intent(MainActivity.this, RecordingService.class);
                startIntent.setAction(RecordingService.ACTION_START_RECORDING);
                ContextCompat.startForegroundService(MainActivity.this, startIntent);
            });

        stopButton.setOnClickListener(v -> {
            Intent stopSpeechRecognitionIntent = new Intent(MainActivity.this, SpeechRecognitionService.class);
            stopSpeechRecognitionIntent.setAction(SpeechRecognitionService.ACTION_STOP_LISTENING);
            startService(stopSpeechRecognitionIntent);
            Intent stopIntent = new Intent(MainActivity.this, RecordingService.class);
            stopIntent.setAction(RecordingService.ACTION_STOP_RECORDING);
            startService(stopIntent);
        });


        saveButton.setOnClickListener(v -> {
            Intent saveIntent = new Intent(MainActivity.this, RecordingService.class);
            saveIntent.setAction(RecordingService.ACTION_SAVE_RECORDING);
            ContextCompat.startForegroundService(MainActivity.this, saveIntent);
        });

        Button FoldersRecognitionButton = findViewById(R.id.foldersRecognition);
        FoldersRecognitionButton.setOnClickListener(v -> {
            Intent startSpeechRecognitionIntent = new Intent(MainActivity.this, SpeechRecognitionService.class);
            startSpeechRecognitionIntent.setAction(SpeechRecognitionService.ACTION_START_LISTENING);
            ContextCompat.startForegroundService(MainActivity.this, startSpeechRecognitionIntent);
        });

        EditText durationEditText = findViewById(R.id.durationEditText);
        durationEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                try {
                    int minutes = Integer.parseInt(durationEditText.getText().toString());
                    MAX_DURATION_MS = minutes * 60 * 1000;
                    updateRecordingServiceMaxDurationMs();
                    Toast.makeText(MainActivity.this, "Duration updated to " + minutes + " minutes", Toast.LENGTH_SHORT).show();
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "Invalid duration entered", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });

    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        RecordingService.LocalBinder binder = (RecordingService.LocalBinder) service;
        recordingService = binder.getService();
        isServiceBound = true;
    }


    public void onServiceDisconnected(ComponentName name) {
        isServiceBound = false;
    }

    private void bindService() {
        Intent bindIntent = new Intent(MainActivity.this, RecordingService.class);
        bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
    }

    private void unbindService() {
        if (isServiceBound) {
            unbindService(this);
            isServiceBound = false;
        }
    }
    @Override
    protected void onStart() {
        super.onStart();
        bindService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService();
    }
    private void updateRecordingServiceMaxDurationMs() {
        if (isServiceBound) {
            recordingService.setMaxDurationMs((int) MAX_DURATION_MS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}

