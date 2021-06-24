package com.example.spectrumaudiofrequency.sinusoid_manipulador;

public class SinusoidManipulator {
    public static float ToLogarithmicScale(float data) {
        if (data == 0) return 0;
        return (float) (((data < 0) ? Math.log10(data * -1) * -1 : (Math.log10(data))) * 100);
    }

    public static short[][] mix(short[][][] tracks) {
        int tracksLength = tracks.length;
        int channelsLength = tracks[0].length;

        int shorterDataLength = tracks[0][0].length;
        for (int i = 1; i < tracks.length; i++) {
            if (tracks[i][0].length < shorterDataLength) shorterDataLength = tracks[i][0].length;
        }

        short[][] r = new short[channelsLength][shorterDataLength];
        for (int channel = 0; channel < channelsLength; channel++) {
            for (int data = 0; data < shorterDataLength; data++) {
                short datum = 0;
                for (short[][] channels : tracks) datum += channels[channel][data];
                r[channel][data] = datum;
            }
        }
        return r;
    }
}
