#include <cmath>
#include <jni.h>
#include <string>
#include <android/log.h>
#include <cstdio>
#include <sstream>
#include <thread>

using namespace std;

static float PRECISION = 10;
const float II_PI = ((float) (-2.0 * M_PI));

extern "C" JNIEXPORT jfloatArray JNICALL
FloatArrayTOJjFloatArray(JNIEnv *env, const float FloatArray[], int size) {
    jfloatArray result;
    result = env->NewFloatArray(size);

    if (result == nullptr)return nullptr; /* out of memory error thrown */

    int i;
    // fill a temp structure to use to populate the java int array
    jfloat fill[size];
    for (i = 0; i < size; i++) {
        fill[i] = FloatArray[i]; // put whatever logic you want to populate the values here
    }
    // move from the temp structure to the java structure
    env->SetFloatArrayRegion(result, 0, size, fill);
    return result;
}

int JFloatArrayTOFloatArray(JNIEnv *env, jfloatArray array, float **P_floatArray) {
    int length = env->GetArrayLength(array);

    jfloat *floatArrayElements = env->GetFloatArrayElements(array, nullptr);

    float NewArray[length];

    for (int i = 0; i < length; i++) NewArray[i] = (float) floatArrayElements[i];

    *P_floatArray = NewArray;

    return length;
}

extern "C" JNIEXPORT jshortArray JNICALL
ShortArrayTOJShortArray(JNIEnv *env, const short ShortArray[], int size) {
    jshortArray result;
    result = env->NewShortArray(size);

    if (result == nullptr)return nullptr; /* out of memory error thrown */

    int i;
    // fill a temp structure to use to populate the java int array
    jshort fill[size];
    for (i = 0; i < size; i++) {
        fill[i] = ShortArray[i]; // put whatever logic you want to populate the values here
    }
    // move from the temp structure to the java structure
    env->SetShortArrayRegion(result, 0, size, fill);
    return result;
}

int JShortArrayTOShortArray(JNIEnv *env, jshortArray array, short **P_shortArray) {
    int length = env->GetArrayLength(array);

    jshort *shortArrayElements = env->GetShortArrayElements(array, nullptr);

    short NewArray[length];

    for (int i = 0; i < length; i++) NewArray[i] = (short) shortArrayElements[i];

    *P_shortArray = NewArray;

    return length;
}


static float **Angles;
static int AnglesLength = -1;
static int SampleLength = -1;

void CalculateAnglesOfFrequenciesRange(int anglesLength, int sampleLength) {
    if (AnglesLength != anglesLength || SampleLength != sampleLength) {
        AnglesLength = anglesLength;
        SampleLength = sampleLength;
        delete Angles;

        Angles = new float *[anglesLength];

        for (int Frequency = 0; Frequency < anglesLength; Frequency++) {
            Angles[Frequency] = new float[sampleLength];
            float PointsDistance =
                    (II_PI / (float) sampleLength) * (((float) Frequency) / PRECISION);
            for (int angle_number = 0; angle_number < sampleLength; angle_number++) {
                Angles[Frequency][angle_number] = cos((float) (angle_number + 1) * PointsDistance);
            }
        }
    }
}

float fft(const short Sample[], int sampleLength, int Frequency) {
    auto F_sampleLength = (float) sampleLength;
    float x_some = 0;

    for (int i = 0; i < sampleLength; i++) {
        auto radius = (float) Sample[i];
        x_some += Angles[Frequency][i] * radius;
    }

    return (x_some) / F_sampleLength;
}

float *fft(const short *Sample, int start, int end, int sampleLength) {
    int fftLength = end - start;

    auto *fftResult = new float[fftLength];

    for (int Frequency = 0; Frequency < fftLength; Frequency++)
        fftResult[Frequency] = fft(Sample, sampleLength, start + Frequency);

    return fftResult;
}


extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_example_spectrumaudiofrequency_SinusoidConverter_fftNative(JNIEnv *env,
                                                                    __unused jclass clazz,
                                                                    jint start, jint end,
                                                                    jshortArray wave_piece) {
    short *floatArray = nullptr;
    int floatArrayLength = JShortArrayTOShortArray(env, wave_piece, &floatArray);

    return FloatArrayTOJjFloatArray(env,
            fft(floatArray, start, end, floatArrayLength),end - start);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_example_spectrumaudiofrequency_SinusoidConverter_CalculateAnglesOfFrequenciesRange(
        JNIEnv *env,
        jclass clazz,
        jint anglesLength,
        jint wave_piece_length) {
    CalculateAnglesOfFrequenciesRange((int) anglesLength, (int) wave_piece_length);
}extern "C"
JNIEXPORT void JNICALL
Java_com_example_spectrumaudiofrequency_SinusoidConverter_setPrecision(JNIEnv *env, jclass clazz,
                                                                       jfloat precision) {
    PRECISION = precision;
}