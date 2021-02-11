#include <cmath>
#include <jni.h>
#include <string>
#include <android/log.h>
#include <cstdio>
#include <sstream>
#include <thread>

using namespace std;

const float PRECISION = 10;
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
static int WavePieceLength = -1;

void CalculateAnglesOfFrequenciesRange(int anglesLength, int wavePieceLength) {
    if (AnglesLength != anglesLength || WavePieceLength != wavePieceLength) {
        AnglesLength = anglesLength;
        WavePieceLength = wavePieceLength;
        delete Angles;

        Angles = new float *[anglesLength];

        for (int Frequency = 0; Frequency < anglesLength; ++Frequency) {
            Angles[Frequency] = new float[wavePieceLength];
            float PointsDistance =
                    (II_PI / (float) wavePieceLength) * ((float) (Frequency + 1) / PRECISION);
            for (int angle_number = 0; angle_number < wavePieceLength; angle_number++) {
                Angles[Frequency][angle_number] = cos((float) (angle_number + 1) * PointsDistance);
            }
        }
    }
}

float fft(const short Sample[], int SampleLength, int Frequency) {
    auto WavePieceLength_F = (float) SampleLength;
    float x_some = 0;

    for (int i = 0; i < SampleLength; i++) {
        auto radius = (float) Sample[i];
        x_some += Angles[Frequency][i] * radius;
    }

    return (x_some) / WavePieceLength_F;
}

float *fft(const short *WavePiece, int start, int end, int SampleLength) {
    int fftArrayLength = end - start;

    auto *fftArray = new float[fftArrayLength];

    for (int Frequency = 0; Frequency < fftArrayLength; Frequency++)
        fftArray[Frequency] = fft(WavePiece, SampleLength, start + Frequency);

    return fftArray;
}


extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_example_spectrumaudiofrequency_SinusoidConverter_NativeFFT(JNIEnv *env,
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
}