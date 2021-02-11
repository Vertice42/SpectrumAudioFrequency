#pragma version(1)
#pragma rs java_package_name(com.example.spectrumaudiofrequency)
#pragma rs_fp_relaxed

static const float II_PI = (-2 * M_PI);

float F32_SampleLength;
int I32_SampleLength;
float PRECISION;

static void write2DArray(int x,int y, float* array,int Length,float value){
    array[Length*x + y] = value;
}

static float read2DArray(int x,int y, float* array,int Length){
    return array[Length*x + y];
}

float *Angles;
int AnglesLength;

void CalculateAngles(float *v_out, uint32_t x) {
    float PointsDistance = (II_PI / F32_SampleLength) * (((float) (x+1)) / PRECISION);

    float point = 1;
    int i = 0;
        while(point < F32_SampleLength){
            write2DArray(x,i,Angles,AnglesLength,cos(point * PointsDistance));

            point+=1;
            i++;
            }
}

float *Sample;

void fft(float *v_out, uint32_t x) {
    float x_some = 0;
    //float y_some = 0;
    for (int y = 0; y < I32_SampleLength; y++) {
        //float point = (float) y;
        x_some += read2DArray(x,y,Angles,AnglesLength)*Sample[y];
        //y_some += (sin(point * PointsDistances[x]) * Sample[y];);
     }
      *v_out = x_some / F32_SampleLength;
}