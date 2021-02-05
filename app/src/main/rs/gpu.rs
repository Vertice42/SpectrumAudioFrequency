#pragma version(1)
#pragma rs java_package_name(com.example.spectrumaudiofrequency)
#pragma rs_fp_relaxed

static const float II_PI = (-2 * M_PI);

//reduce requer API 24
#pragma rs reduce(addfloat) accumulator(addfloatAccum)
static void addfloatAccum(float *accum, float val) {
  *accum += val;
}

float WavePieceLength;
float PRECISION;

/*
void CalculatePointsDistances(float *v_out, uint32_t x) {
    *v_out = (II_PI / WavePieceLength) * ( ((float)(x+1)) / PRECISION);
}
*/

static void write2DArray(int x,int y, float* array,int Length,float value){
    array[Length*x + y] = value;
}

static float read2DArray(int x,int y, float* array,int Length){
    return array[Length*x + y];
}

float *Angles;
int AnglesLength;

void CalculateAngles(float *v_out, uint32_t x) {
    float PointsDistance = (II_PI / WavePieceLength) * (((float) (x+1)) / PRECISION);

    for (int i = 0; i < (int) WavePieceLength; i++) {
        write2DArray(x,i,Angles,AnglesLength,cos(((float)i) * PointsDistance));
    }
}

//float *PointsDistances;
float *WavePiece;

void fftArray(float *v_out, uint32_t x) {
    float x_some = 0;
    //float y_some = 0;
    for (int y = 0; y < (int) WavePieceLength; y++) {
        float radius = (float) WavePiece[y];
        //float point = (float) y;
        x_some += read2DArray((int)x,y,Angles,AnglesLength)*radius;
        //y_some += (sin(point * PointsDistances[x]) * radius);
     }

      *v_out = x_some / WavePieceLength;
}


