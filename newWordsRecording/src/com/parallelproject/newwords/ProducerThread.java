package com.parallelproject.newwords;

import javax.sound.sampled.TargetDataLine;
import java.util.concurrent.BlockingQueue;


public class ProducerThread implements Runnable{
    private final BlockingQueue<byte[]> queue;
    private final TargetDataLine microphone;
    byte[] buffer;
    private volatile boolean running = true;


    public ProducerThread(int chunkBytes, BlockingQueue<byte[]> queue, TargetDataLine microphone) {
        this.queue = queue;
        this.microphone = microphone;
        this.buffer = new byte[chunkBytes];
    }


    @Override
    public void run() {
        try {
            while (running) {
                int read = microphone.read(buffer, 0, buffer.length);
                if (read > 0) {
                    byte[] chunkCopy = new byte[read];
                    System.arraycopy(buffer, 0, chunkCopy, 0, read);
                    queue.put(chunkCopy);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Producer interrupted");
        }
    }


    public void stop() {
        running = false;
    }

}