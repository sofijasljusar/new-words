public class SilenceDetector {
    private final int peakThreshold;

    public SilenceDetector() {
        this.peakThreshold = 1000;
    }

    public boolean isSilent(byte[] pcmData) { // pcmData = list of bytes from the mic
        for (int i = 0; i < pcmData.length - 1; i += 2) { // sound sample uses 2 bytes
            // pcmData[i] = first byte (low byte)
            // pcmData[i+1] = second byte (high byte)
            // << 8 means “move this byte 8 bits to the left”
            // & 0xFF makes sure this byte is treated as 0–255 (unsigned)
            // | (bitwise OR) combines the high byte and low byte into one single 16-bit number
            // so, in the line below we combine 2 numbers stored for sound into one to know how loud this moment is
            int sample = (pcmData[i + 1] << 8) | (pcmData[i] & 0xFF);
            if (Math.abs(sample) > peakThreshold) {  // peak threshold
                return false;
            }
        }
        return true;
    }
}
