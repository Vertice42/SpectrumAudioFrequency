#include <cmath>
#include <jni.h>
#include <string>
#include <android/log.h>
#include <cstdio>
#include <sstream>
#include <thread>

using namespace std;

const float PRECISION = 10;

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

float fft(const short WavePiece[], int WavePieceLength, float Frequency) {
    auto WavePieceLength_F = (float) WavePieceLength;
    auto points_distance = ((((float)(-2.0 * M_PI)) / WavePieceLength_F) * (Frequency + 1) /
                                    PRECISION);

    float x_some = 0;
    float y_some = 0;

    for (int i = 0; i < WavePieceLength; i++) {
        auto radius = (float) WavePiece[i];
        auto point = (float) i;
        x_some += (cos(point * points_distance) * radius);
       //y_some += (sin(point * points_distance) * radius);
    }

    return ((x_some + y_some) / WavePieceLength_F);
}

float *fftArray(short WavePiece[], int Offset, int Length, int WavePieceLength) {
    int fftArrayLength = Length-Offset;

    auto *fftArray = new float[fftArrayLength];

    for (int i = 0; i < fftArrayLength; i++)fftArray[i] = fft(WavePiece, WavePieceLength, (float)(i+Offset));

    return fftArray;
}

float *fftArray(short WavePiece[], int Length, int WavePieceLength) {
    return fftArray(WavePiece, 0, Length, WavePieceLength);
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_example_spectrumaudiofrequency_SinusoidConverter_C_1fftArray__I_3S(JNIEnv *env,
                                                                            jclass clazz,
                                                                            jint length,
                                                                            jshortArray wave_piece) {
    short *floatArray = nullptr;
    int floatArrayLength = JShortArrayTOShortArray(env, wave_piece, &floatArray);

    return FloatArrayTOJjFloatArray(env, fftArray(floatArray, length, floatArrayLength),
                                    floatArrayLength);
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_example_spectrumaudiofrequency_SinusoidConverter_C_1fftArray__II_3S(JNIEnv *env,
                                                                             jclass clazz,
                                                                             jint offset,
                                                                             jint length,
                                                                             jshortArray wave_piece) {
    short *floatArray = nullptr;
    int floatArrayLength = JShortArrayTOShortArray(env, wave_piece, &floatArray);

    return FloatArrayTOJjFloatArray(env, fftArray(floatArray, (int) offset, (int) length,
                                                  floatArrayLength), floatArrayLength);
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_example_spectrumaudiofrequency_SinusoidConverter_C_1FFT(JNIEnv *env, __unused jclass clazz,
                                                                 jshortArray j_wave_piece,
                                                                 jint frequency) {
    int length = env->GetArrayLength(j_wave_piece);
    jshort *C_WavePiece = env->GetShortArrayElements(j_wave_piece, nullptr);

    return (jshort) fft(C_WavePiece, length, frequency);
}