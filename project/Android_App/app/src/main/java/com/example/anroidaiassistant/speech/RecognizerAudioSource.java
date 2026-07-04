package com.example.anroidaiassistant.speech;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.ParcelFileDescriptor;

import java.io.IOException;

public final class RecognizerAudioSource implements AutoCloseable {
    public static final int SAMPLE_RATE_HZ = 16000;
    public static final int CHANNEL_COUNT = 1;
    public static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    private final AudioRecord audioRecord;
    private final ParcelFileDescriptor readDescriptor;
    private ParcelFileDescriptor writeDescriptor;
    private final int bufferSize;
    private Thread audioThread;
    private volatile boolean running;

    private RecognizerAudioSource(
            AudioRecord audioRecord,
            ParcelFileDescriptor readDescriptor,
            ParcelFileDescriptor writeDescriptor,
            int bufferSize
    ) {
        this.audioRecord = audioRecord;
        this.readDescriptor = readDescriptor;
        this.writeDescriptor = writeDescriptor;
        this.bufferSize = bufferSize;
    }

    public static RecognizerAudioSource start(Context context) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_ENCODING);
        if (minBufferSize <= 0) {
            return null;
        }

        ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();
        } catch (IOException ignored) {
            return null;
        }

        int bufferSize = Math.max(minBufferSize * 2, SAMPLE_RATE_HZ / 5);
        AudioRecord record = null;
        try {
            record = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE_HZ,
                    CHANNEL_CONFIG,
                    AUDIO_ENCODING,
                    bufferSize
            );

            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                closeQuietly(pipe[0]);
                closeQuietly(pipe[1]);
                record.release();
                return null;
            }

            RecognizerAudioSource source = new RecognizerAudioSource(record, pipe[0], pipe[1], bufferSize);
            if (!source.begin()) {
                source.close();
                return null;
            }
            return source;
        } catch (Exception ignored) {
            closeQuietly(pipe[0]);
            closeQuietly(pipe[1]);
            if (record != null) {
                record.release();
            }
            return null;
        }
    }

    public ParcelFileDescriptor getReadDescriptor() {
        return readDescriptor;
    }

    private boolean begin() {
        try {
            audioRecord.startRecording();
        } catch (Exception ignored) {
            return false;
        }

        running = true;
        audioThread = new Thread(this::pumpAudio, "RecognizerAudioSource");
        audioThread.start();
        return true;
    }

    private void pumpAudio() {
        byte[] buffer = new byte[bufferSize];
        try (ParcelFileDescriptor.AutoCloseOutputStream outputStream =
                     new ParcelFileDescriptor.AutoCloseOutputStream(writeDescriptor)) {
            writeDescriptor = null;
            while (running) {
                int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead);
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION
                        || bytesRead == AudioRecord.ERROR_BAD_VALUE
                        || bytesRead == AudioRecord.ERROR_DEAD_OBJECT) {
                    break;
                }
            }
        } catch (IOException ignored) {
        } finally {
            running = false;
        }
    }

    @Override
    public void close() {
        running = false;

        try {
            audioRecord.stop();
        } catch (Exception ignored) {}

        audioRecord.release();

        closeQuietly(writeDescriptor);
        writeDescriptor = null;
        closeQuietly(readDescriptor);

        if (audioThread != null && audioThread != Thread.currentThread()) {
            audioThread.interrupt();
        }
        audioThread = null;
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (IOException ignored) {}
    }
}
