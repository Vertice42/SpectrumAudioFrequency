#pragma version(1)
#pragma rs java_package_name(com.example.spectrumaudiofrequency)
#pragma rs_fp_relaxed

static const float II_PI = (2 * M_PI);

//reduce requer API 24
#pragma rs reduce(addfloat) accumulator(addfloatAccum)
static void addfloatAccum(float *accum, float val) {
  *accum += val;
}

float WavePieceLength;
float PRECISION;

void CalculatePointsDistances(float *v_out, uint32_t x) {
    *v_out = ((2 * M_PI) / WavePieceLength) * ( ((float)(x+1)) / PRECISION);
}

float *PointsDistances;
float *WavePiece;

void fftArray(float *v_out, uint32_t x) {
    float x_some = 0;
    float y_some = 0;
    for (int i = 0; i < (int) WavePieceLength; i++) {
        float radius = (float) WavePiece[i];
        float point = (float) i;
        x_some += (cos(point * PointsDistances[x]) * radius);
        y_some += (sin(point * PointsDistances[x]) * radius);
     }

      *v_out = (float) ((short) (((x_some + y_some) / WavePieceLength)* PRECISION));
}


