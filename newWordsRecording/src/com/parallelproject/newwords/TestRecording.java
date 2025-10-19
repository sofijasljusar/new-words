package com.parallelproject.newwords;

import java.io.File;
import java.io.IOException;
import javax.sound.sampled.LineUnavailableException;

public class TestRecording {
    private static final int RECORD_TIME = 5000;

    public static void main (String[] args) {
        String desktopPath = System.getProperty("user.home") + "/Desktop/";
        File wavFile = new File(desktopPath + "recording.wav");

        final AudioRecorder recorder = new AudioRecorder();

        Thread recordThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("Start recording...");
                    recorder.start();
                } catch (LineUnavailableException ex) {
                    ex.printStackTrace();
                    System.exit(-1);
                }
            }
        });

        recordThread.start();

        try {
            Thread.sleep(RECORD_TIME);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        try {
           recorder.stop();
           recorder.save(wavFile);
            System.out.println("STOPPED");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        System.out.println("DONE");
    }
}
