package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Locale;

public class SpeechRecognitionService extends Service {
    public static final String ACTION_START_LISTENING = "start_listening";
    public static final String ACTION_STOP_LISTENING = "stop_listening";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "speech_recognition_service_channel";
    private SpeechRecognizer speechRecognizer;
    private MediaPlayer mediaPlayer;
    private static final long MIN_ALERT_INTERVAL = 5000;
    private Handler alertHandler;
    private Runnable alertRunnable;



    @Override
    public void onCreate() {
        super.onCreate();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        mediaPlayer = MediaPlayer.create(this, R.raw.alert_sound);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            if (ACTION_START_LISTENING.equals(intent.getAction())) {
                startListening();
            } else if (ACTION_STOP_LISTENING.equals(intent.getAction())) {
                stopListening();
                stopForeground(true);
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL,en-US");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {}

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }


            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                speechRecognizer.cancel();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    speechRecognizer.startListening(intent);
                }, 1000);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                processResults(matches);
                speechRecognizer.startListening(intent);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                processResults(matches);
            }
            @Override
            public void onEvent(int eventType, Bundle params) {}

            private void processResults(ArrayList<String> matches) {
                if (alertRunnable != null) {
                    return; // If the alertRunnable is not null, it means the minimum interval hasn't passed yet.
                }

                if (matches != null) {
                    for (String match : matches) {
                        if (match.contains("תיקייה") || match.contains("תיקיות") || match.contains("תיקיה") || match.contains("folder") || match.contains("folders") || match.contains("root") || match.contains("directories") || match.contains("directory")) {
                            mediaPlayer.start();
                            alertRunnable = () -> alertRunnable = null;
                            if (alertHandler == null) {
                                alertHandler = new Handler(Looper.getMainLooper());
                            }
                            alertHandler.postDelayed(alertRunnable, MIN_ALERT_INTERVAL);
                            break;
                        }
                    }
                }
            }

        });

        speechRecognizer.startListening(intent);
        startForeground(NOTIFICATION_ID, createNotification());
    }

    private void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
            speechRecognizer.destroy();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Speech Recognition Service")
                .setContentText("Listening for commands...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        return builder.build();
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Speech Recognition Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }
}
