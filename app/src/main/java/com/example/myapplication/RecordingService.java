package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.ExecuteCallback;
import com.arthenica.mobileffmpeg.FFmpeg;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class RecordingService extends Service {
    public static final String ACTION_START_RECORDING = "com.example.myapplication.START_RECORDING";
    public static final String ACTION_STOP_RECORDING = "com.example.myapplication.STOP_RECORDING";
    public static final String ACTION_SAVE_RECORDING = "com.example.myapplication.SAVE_RECORDING";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "RecordingServiceChannel";

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 4;
    private static long MAX_DURATION_MS = 5 * 60 * 1000;

    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;

    private LinkedBlockingQueue<byte[]> audioDataQueue;


    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_RECORDING.equals(action)) {
                startRecording();
                startForeground(NOTIFICATION_ID, createNotification());
            } else if (ACTION_STOP_RECORDING.equals(action)) {
                stopRecording();
                stopForeground(true);
                stopSelf();
            } else if (ACTION_SAVE_RECORDING.equals(action)) {
                saveRecording();
            }

        }
        return START_STICKY;
    }

    private void startRecording() {
        if (!isRecording) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
            audioRecord.startRecording();
            isRecording = true;
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();

            int maxQueueSize = (int) (MAX_DURATION_MS * SAMPLE_RATE * 2 / (1000 * BUFFER_SIZE));
            audioDataQueue = new LinkedBlockingQueue<>(maxQueueSize);
            recordingThread = new Thread(() -> {
                while (isRecording) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead = audioRecord.read(buffer, 0, BUFFER_SIZE);
                    if (bytesRead > 0) {
                        synchronized (audioDataQueue) {
                            if (audioDataQueue.remainingCapacity() == 0) {
                                audioDataQueue.poll();
                            }
                            audioDataQueue.add(buffer);
                        }
                    }
                }
            });
            recordingThread.start();
        }
    }

    private void stopRecording() {
        if (isRecording) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveRecording() {
        String newFileName = "Recording_" + System.currentTimeMillis();
        String rawFilePath = getExternalFilesDir(Environment.DIRECTORY_MUSIC) + "/" + newFileName + ".raw";
        String mp3FilePath = getExternalFilesDir(Environment.DIRECTORY_MUSIC) + "/" + newFileName + ".mp3";
        File rawFile = new File(rawFilePath);
        File mp3File = new File(mp3FilePath);

        try (FileOutputStream fos = new FileOutputStream(rawFile)) {
            synchronized (audioDataQueue) {
                for (byte[] buffer : new ArrayList<>(audioDataQueue)) {
                    fos.write(buffer);
                }
            }
            Toast.makeText(this, "Raw recording saved temporarily", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Failed to save raw recording", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return;
        }

        String[] cmd = new String[]{
                "-y",
                "-f", "s16le",
                "-ar", String.valueOf(SAMPLE_RATE),
                "-ac", "1",
                "-i", rawFilePath,
                "-acodec", "libmp3lame",
                "-b:a", "128k",
                mp3File.getAbsolutePath() // Use mp3File here
        };

        FFmpeg.executeAsync(cmd, (executionId, returnCode) -> {
            if (returnCode == Config.RETURN_CODE_SUCCESS) {
                rawFile.delete();
                addRecordingToMediaStore(mp3File);
                Toast.makeText(getApplicationContext(), "Recording saved as MP3", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Failed to convert raw recording to MP3", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addRecordingToMediaStore(File mp3File) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, mp3File.getName());
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC);
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri contentUri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues);

        if (contentUri != null) {
            try (OutputStream os = getContentResolver().openOutputStream(contentUri)) {
                Files.copy(mp3File.toPath(), os);
                mp3File.delete();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "Failed to move recording to MediaStore", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }

            contentValues.clear();
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(contentUri, contentValues, null, null);
        } else {
            Toast.makeText(getApplicationContext(), "Failed to insert recording into MediaStore", Toast.LENGTH_SHORT).show();
        }
    }

    private Notification createNotification() {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Recording Service")
                .setContentText("Recording in progress")
                .setSmallIcon(R.drawable.ic_launcher_background) // Replace with your app's icon
                .setContentIntent(pendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Recording Service";
            String description = "Foreground service for recording audio";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }


    public void setMaxDurationMs(int maxDurationMs) {
        MAX_DURATION_MS = maxDurationMs;
    }

    @Nullable
    @Override

    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }
    public class LocalBinder extends Binder {
        public RecordingService getService() {
            return RecordingService.this;
        }
    }

}
