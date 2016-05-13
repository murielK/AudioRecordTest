package com.zipato.audiorecorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String FOLDER_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/recorder/";
    private static final String AUDIO_FILE_NAME = "audioFile.wav";
    private static final String REC_FILE_NAME = "recFile.wav";

    private static int REC_SAMPLE_RATE = 16000;

    private MediaPlayer mp;
    private AudioRecord recorder;

    private volatile boolean isRecording;
    private volatile boolean started;
    private int bufferSize = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });

        findViewById(R.id.button2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop();
            }
        });

        copyAudioTestFileToSD(); // this should be in separate thread, but who cares about it for this purpose?!
    }

    private void copyAudioTestFileToSD() {
        final File file = new File(FOLDER_DIR);
        if (!file.exists()) {
            file.mkdir();
        }

        final File audioFile = new File(file, AUDIO_FILE_NAME);
        if (audioFile.exists()) return;

        InputStream is = null;
        OutputStream os = null;

        try {
            is = getAssets().open(AUDIO_FILE_NAME);
            os = new FileOutputStream(audioFile);
            final byte[] buf = new byte[1024];
            while (true) {
                int len = is.read(buf);
                if (len < 0) {
                    break;
                }
                os.write(buf, 0, len);
            }
        } catch (IOException e) {
            Log.e(TAG, "", e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e(TAG, "", e);
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    Log.e(TAG, "", e);
                }
            }
        }

    }

    private void stop() {
        if (!started){
            Toast.makeText(this, "ALREADY STOPPED", Toast.LENGTH_LONG).show();
            return;
        }

        if (started) {
            try {
                if (mp.isPlaying())
                    mp.stop();
            } catch (Exception e) {
                //
            }

            try {
                mp.release();
            } catch (Exception e) {
                //
            }

            stopRecording();
            started = false;
        }
    }

    private void start() {
        if (started) {
            Toast.makeText(this, "ALREADY STARTED", Toast.LENGTH_LONG).show();
            return;
        }

        started = true;

        try {
            Log.d(TAG, "starting...process");
            mp = new MediaPlayer();
            mp.setDataSource(getMediaPath());
            mp.prepare();
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.d(TAG, "stopping as player completed task");
                    stop();
                    Toast.makeText(MainActivity.this, "Done", Toast.LENGTH_LONG).show();
                }
            });
            startRecording();
            mp.start();
        } catch (IOException e) {
            Log.d(TAG, "", e);
            started = false;
        }

    }

    private String getRecordingPath() {
        return FOLDER_DIR + REC_FILE_NAME;
    }

    private String getMediaPath() {
        return FOLDER_DIR + AUDIO_FILE_NAME;
    }

    private boolean initializeRecord() {
        try {
            int REC_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
            int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
            bufferSize = AudioRecord.getMinBufferSize(REC_SAMPLE_RATE,
                    RECORDER_CHANNELS, REC_ENCODING);
            Log.d(TAG, "bufferSize ==" + bufferSize);

            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    REC_SAMPLE_RATE, RECORDER_CHANNELS,
                    REC_ENCODING, bufferSize);

            return true;

        } catch (Exception e) {
            Log.e(TAG, "", e);
        }

        return false;
    }

    private byte[] getWaveHeader() {
        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (36 & 0xff);
        header[5] = (byte) ((36 >> 8));
        header[6] = (byte) ((36 >> 16));
        header[7] = (byte) ((36 >> 24));
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) 1; /*REC_CHANNEL_INPUT */ //channel
        header[23] = 0;
        header[24] = (byte) (REC_SAMPLE_RATE & 0xff);
        header[25] = (byte) ((REC_SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte) ((REC_SAMPLE_RATE >> 16) & 0xff);
        header[27] = (byte) ((REC_SAMPLE_RATE >> 24) & 0xff);
        header[28] = (byte) ((REC_SAMPLE_RATE /* * REC_CHANNEL_INPUT */ * (/*RECORDER_BPP*/ 16 / 8)) & 0xff);
        header[29] = (byte) (((REC_SAMPLE_RATE /* * REC_CHANNEL_INPUT */ * (/*RECORDER_BPP*/ 16 / 8)) >> 8) & 0xff);
        header[30] = (byte) (((REC_SAMPLE_RATE /* * REC_CHANNEL_INPUT */ * (/*RECORDER_BPP*/ 16 / 8)) >> 16) & 0xff);
        header[31] = (byte) (((REC_SAMPLE_RATE /* * REC_CHANNEL_INPUT */ * (/*RECORDER_BPP*/ 16 / 8)) >> 24) & 0xff);
        header[32] = (byte) (/* REC_CHANNEL_INPUT * */ (/*RECORDER_BPP*/ 16 / 8));  // block align
        header[33] = 0;
        header[34] = (byte) /*RECORDER_BPP*/ 16;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (0x0);
        header[41] = (byte) ((0x0));
        header[42] = (byte) ((0x0));
        header[43] = (byte) ((0x0));
        Log.d(TAG, "BufferSize " + bufferSize);
        Log.d(TAG, "PCM " + /*RECORDER_BPP*/ 16);
        Log.d(TAG, "Sample Rate " + REC_SAMPLE_RATE);
        Log.d(TAG, "Channel " + 1 /* REC_CHANNEL_INPUT */);
        return header;
    }

    private void writeAudioDataToFile(String Dir) {
        Log.d(TAG, "file dir" + Dir);
        byte[] data = new byte[bufferSize];

        final byte[] header = getWaveHeader();

        DataOutputStream output = null;
        try {
            output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(Dir)));
            // make place for wav header so i wont have to re-copy the whole file, but instead just update the length in the header when recording is done.
            output.write(header, 0, 44);
        } catch (Exception e) {
            Log.e(TAG, "", e);
            stopRecording();
            return;
        }

        while (isRecording) {
            final int sampleLength = recorder.read(data, 0, bufferSize);
            try {
                for (int i = 0; i < sampleLength; i++) {
                    output.write(data[i]);
                }
            } catch (IOException e) {
                Log.e(TAG, "", e);
                stopRecording();
            }
        }

        flush(output);
    }

    private void flush(DataOutputStream output) {
        if (output == null) return;

        try {
            output.flush();
        } catch (Exception e) {
            //
        } finally {
            try {
                output.close();
            } catch (IOException e) {
                //
            }
            output = null;
        }
    }

    private void fixWaveHeaderFileSize(String dir) throws IOException {
        File file = new File(dir);

        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        randomAccessFile.getChannel().size();

        final byte[] header = new byte[8];
        header[0] = (byte) (randomAccessFile.getChannel().size() + 36 & 0xff);
        header[1] = (byte) ((randomAccessFile.getChannel().size() + 36 >> 8) & 0xff);
        header[2] = (byte) ((randomAccessFile.getChannel().size() + 36 >> 16) & 0xff);
        header[3] = (byte) ((randomAccessFile.getChannel().size() + 36 >> 24) & 0xff);
        header[4] = (byte) (randomAccessFile.getChannel().size() & 0xff);
        header[5] = (byte) ((randomAccessFile.getChannel().size() >> 8) & 0xff);
        header[6] = (byte) ((randomAccessFile.getChannel().size() >> 16) & 0xff);
        header[7] = (byte) ((randomAccessFile.getChannel().size() >> 24) & 0xff);

        randomAccessFile.seek(4);
        randomAccessFile.write(header, 0, 4);
        randomAccessFile.seek(40);
        randomAccessFile.write(header, 4, 4);
        randomAccessFile.close();
    }

    private void startRecording() {
        if (isRecording) {
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "initialising recorder");
                isRecording = initializeRecord();
                if (!isRecording) return;
                Log.d(TAG, "start recording...");
                recorder.startRecording();
                writeAudioDataToFile(getRecordingPath());
            }
        }).start();
    }

    private void stopRecording() {
        Log.d(TAG, "Stopping...recorder");
        if (isRecording) {
            try {
                recorder.stop();
                Log.d(TAG, "Recording stopped");
                recorder.release();
                Log.d(TAG, "recorder released");
                recorder = null;
            } catch (Exception e) {
                //TODO
            }

            isRecording = false;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.d(TAG, "correcting recording file header");
                        fixWaveHeaderFileSize(getRecordingPath());
                    } catch (Exception e) {
                        Log.e(TAG, "", e);
                    }
                }
            }).start();
        }



    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stop();

    }
}




