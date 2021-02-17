#pragma version(1)
#pragma rs java_package_name(com.example.spectrumaudiofrequency)

static const float II_PI = (-2 * M_PI);

float F32_SampleLength;
uint16_t uint32_t_SampleLength;
float PRECISION;
uint16_t AnglesLength;

rs_allocation Angles;

void CalculateAngles(float *v_out, uint32_t x) {
    float PointsDistance = II_PI / F32_SampleLength * x / PRECISION;

    uint16_t y = 0;
    float interaction = 0;
    while(y < uint32_t_SampleLength){
        rsSetElementAt_float(Angles, cos(interaction * PointsDistance),x,y);
        y++;
        interaction += 1.0;
    }
}

short *Sample;
void fft(float *v_out, uint32_t x) {
    float x_some = 0;
    for (uint32_t y = 0; y < uint32_t_SampleLength; y++) {
        x_some += rsGetElementAt_float(Angles,x,y) * Sample[y];
     }
      *v_out = x_some / F32_SampleLength;
}