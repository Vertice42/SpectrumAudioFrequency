#include <cmath>
#include <jni.h>
#include <string>
#include <android/log.h>
#include <cstdio>
#include <sstream>
#include <thread>

using namespace std;

static double PRECISION = 10;
const double II_PI = ((double) (-2.0 * M_PI));

extern "C" JNIEXPORT jdoubleArray JNICALL
DoubleArrayToJavaDoubleArray(JNIEnv *env, const double *DoubleArray, int size) {
    jdoubleArray result;
    result = env->NewDoubleArray(size);

    if (result == nullptr)return nullptr; /* out of memory error thrown */

    /*
    int i;
    // fill a temp structure to use to populate the java int array
    auto fill = new double[size];
    for (i = 0; i < size; i++) {
        fill[i] = (jdouble) DoubleArray[i]; // put whatever logic you want to populate the values here
    }
    */
    //todo conversão para jdouble pode ser necesaria

    // move from the temp structure to the java structure
    env->SetDoubleArrayRegion(result, 0, size, DoubleArray);
    return result;
}

int JDoubleArrayToDoubleArray(JNIEnv *env, jdoubleArray array, double **P_doubleArray) {
    int length = env->GetArrayLength(array);

    jdouble *doubleArrayElements = env->GetDoubleArrayElements(array, nullptr);

    double NewArray[length];

    for (int i = 0; i < length; i++) NewArray[i] = (double) doubleArrayElements[i];

    *P_doubleArray = NewArray;

    return length;
}

extern "C" JNIEXPORT __unused  jfloatArray JNICALL
FloatArrayToJavaFloatArray(JNIEnv *env, const float *FloatArray, int size) {
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

__unused int JFloatArrayTOFloatArray(JNIEnv *env, jfloatArray array, float **P_floatArray) {
    int length = env->GetArrayLength(array);

    jfloat *floatArrayElements = env->GetFloatArrayElements(array, nullptr);

    float NewArray[length];

    for (int i = 0; i < length; i++) NewArray[i] = floatArrayElements[i];

    *P_floatArray = NewArray;

    return length;
}

extern "C" JNIEXPORT __unused  jshortArray JNICALL
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

    auto NewArray = new short[length];

    for (int i = 0; i < length; i++) NewArray[i] = shortArrayElements[i];

    *P_shortArray = NewArray;

    return length;
}


static double **Angles;
static int AnglesLength = -1;
static int SampleLength = -1;

void CalculateAnglesOfFrequenciesRange(int anglesLength, int sampleLength) {
    if (AnglesLength != anglesLength || SampleLength != sampleLength) {
        AnglesLength = anglesLength;
        SampleLength = sampleLength;
        delete Angles;

        Angles = new double *[anglesLength];
        for (int frequency = 0; frequency < anglesLength; frequency++) {
            Angles[frequency] = new double[sampleLength];

            double pointsDistance = (II_PI / sampleLength) * (frequency / PRECISION);
            for (int angle = 0; angle < sampleLength; angle++) {
                Angles[frequency][angle] = sin((angle * pointsDistance));
            }
        }
    }
}

double *fft(const short *Sample, int start, int end, int sampleLength) {
    int fftLength = end - start;
    auto *fftResult = new double[fftLength];

    for (int x = 0; x < fftLength; x++) {
        double x_some = 0;
        for (int radius = 0; radius < sampleLength; radius++) {
            x_some += Angles[x+start][radius] * Sample[radius];
        }
        fftResult[x] = x_some / sampleLength;
    }

    return fftResult;
}


extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_com_example_spectrumaudiofrequency_core_FourierFastTransform_00024Native_fftNative(JNIEnv *env,
                                                                                        __unused jclass clazz,
                                                                                        jint start, jint end,
                                                                                        jshortArray sample) {
    short *shortArray = nullptr;
    int shortArrayLength = JShortArrayTOShortArray(env, sample, &shortArray);

    return DoubleArrayToJavaDoubleArray(env, fft(shortArray, start, end, shortArrayLength),
                                        end - start);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_example_spectrumaudiofrequency_core_FourierFastTransform_00024Native_CalculateAnglesOfFrequenciesRange(
        __unused JNIEnv *env, __unused jclass clazz,
        jint anglesLength,
        jint sampleLength) {
    CalculateAnglesOfFrequenciesRange(anglesLength, sampleLength);
}extern "C"
JNIEXPORT void JNICALL
Java_com_example_spectrumaudiofrequency_core_FourierFastTransform_00024Native_setPrecision(
        JNIEnv *env, jclass clazz, jdouble precision) {
    PRECISION = precision;
}