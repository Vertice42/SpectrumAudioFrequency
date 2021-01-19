#pragma version(1)
#pragma rs java_package_name(com.example.spectrumaudiofrequency)
#pragma rs_fp_relaxed

#pragma rs reduce(addint) accumulator(addintAccum)
static void addintAccum(float *accum, float val) {
  *accum += val;
}


static const float II_PI = (2 * M_PI);

int16_t *WavePiece;
float WavePieceLength;
float PRECISION;

void CalculatePointsDistances(float *v_out, uint32_t x) {
    *v_out = (II_PI / WavePieceLength) * ( ((float)(x+1)) / PRECISION);
}

float *PointsDistances;
int PointsDistancesID;
void process(float2 *v_out, uint32_t x) {
    float radius = (float) WavePiece[x];
    float amplitude = (float) x;

    v_out->x = cos(amplitude * PointsDistances[PointsDistancesID]) * radius;
    v_out->y = sin(amplitude * PointsDistances[PointsDistancesID]) * radius;
}



