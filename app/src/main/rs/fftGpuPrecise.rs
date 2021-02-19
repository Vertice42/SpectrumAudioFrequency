#pragma version(1)
#pragma rs java_package_name(com.example.spectrumaudiofrequency)

static const float II_PI = (-2 * M_PI);

uint32_t SampleLength;
float PRECISION;
uint32_t AnglesLength;

/*
static void write2DArray(int x,int y, double* array,int Length,double value){
    array[Length*x + y] = value;
}

static float read2DArray(int x,int y, double* array,int Length){
    return array[Length*x + y];
}
*/

//float2

static void write2DArray(int x,int y, float2 *array,int Length,float2 value){
    array[Length*x + y] = value;
}

static float2 read2DArray(int x,int y, float2* array,int Length){
    return array[Length*x + y];
}

float2 *Angles;
void CalculateAngles(float2 *v_out, uint32_t x) {
    float PointsDistance = II_PI / SampleLength * x / PRECISION;

    uint32_t y = 0;
    float interaction = 0;
    while(y < SampleLength){
        float2 angle = {sin(interaction * PointsDistance),cos(interaction * PointsDistance)};
        write2DArray(x,y,Angles,AnglesLength,angle);
        y++;
        interaction += 1.0;
    }
}

short *Sample;
void fft(float *v_out, uint32_t x) {
    float x_some = 0;
    float y_some = 0;
    for (uint32_t y = 0; y < SampleLength; y++) {
        float2 angle = read2DArray(x,y,Angles,AnglesLength);
        x_some += angle.x * Sample[y];
        y_some += angle.y * Sample[y];
     }
      *v_out = (x_some + y_some) / SampleLength;
}