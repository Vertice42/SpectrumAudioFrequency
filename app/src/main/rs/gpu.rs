// File: singlesource.rs

#pragma version(1)
#pragma rs java_package_name(com.android.rssample)

static const II_PI = (2 * M_PI);

uchar4 RS_KERNEL invert(uchar4 in, uint32_t x) {
  uchar4 out = in;
  return out;
}

float *WavePiece;
float WavePieceLength;
float PRECISION;

void process(float *output, int x) {
    float points_distance = ( II_PI / WavePieceLength) * (x / PRECISION);

    float x_some = 0;
    float y_some = 0;

    for (int i = 0; i < (int) WavePieceLength; i++) {
        float amplitude = (float) i;

        x_some += cos(amplitude * points_distance) * WavePiece[i];
        y_some += sin(amplitude * points_distance) * WavePiece[i];
    }

    x_some /= WavePieceLength;
    y_some /= WavePieceLength;

    *output = (x_some + y_some) * PRECISION;

}