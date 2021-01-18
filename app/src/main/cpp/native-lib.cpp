#include <cmath>
#include <jni.h>
#include <string>
#include <android/log.h>
#include <cstdio>
#include <sstream>
#include <thread>

using namespace std;

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

const int PRECISION = 10;
const int SPECTRUM_ANALYSIS_SIZE = 16000;

class Thread {
private:
    pthread_t c_thread{};


private:
    function<void()> *lambda;

private:
    static void *ThreadFunction(void *p) {

        // ((function<void()> *) p)->operator()();
        thread first([]() {

        });

        pthread_exit(nullptr);
    }


public:
    void start() {
        pthread_create(&c_thread, nullptr, ThreadFunction, (static_cast<void *> (lambda)));
    }

public:
    explicit Thread(function<void()> lambda) {
        this->lambda = &lambda;
    }
};


short fft(const short WavePiece[], int WavePieceLength, int Frequency) {
    auto points_distance = (float) (((2 * M_PI) / (float) WavePieceLength) * ((float) Frequency) /PRECISION);
    float x_some = 0;
    float y_some = 0;

    for (int i = 0; i < WavePieceLength; i++) {
        auto radius = (float) WavePiece[i];
        auto amplitude = (float) i;

        x_some += cos(amplitude * points_distance) * radius;
        y_some += sin(amplitude * points_distance) * radius;
    }

    auto WavePieceLength_F = (float) WavePieceLength;

    x_some /= WavePieceLength_F;
    y_some /= WavePieceLength_F;

    return (short) ((x_some + y_some) * PRECISION);
}

short *fftArray(short WavePiece[], int WavePieceLength) {

    auto *fftArray = new short[SPECTRUM_ANALYSIS_SIZE * PRECISION];

    /*
    int threads_Number = 4;

    Thread *threads[threads_Number];

    int filled = 0;

    for (int i = 0; i < threads_Number; ++i) {

        threads[i] = new Thread([&]() {
            for (; filled < WavePieceLength / threads_Number; filled++)
                fftArray[filled] = fft(WavePiece, WavePieceLength, filled);

            delete threads[i];
        });

        threads[i]->start();
        thread first([]() {
        });

    }
    */

    for (int i = 0; i < WavePieceLength; i++)fftArray[i] = fft(WavePiece, WavePieceLength, i);

    return fftArray;
}

extern "C" JNIEXPORT jshortArray JNICALL
Java_com_example_spectrumaudiofrequency_SinusoidConverter_C_1fftArray(JNIEnv *env,__unused jclass THIS,jshortArray array) {
    short *ShortArray = nullptr;
    int Length = JShortArrayTOShortArray(env, array, &ShortArray);

    return ShortArrayTOJShortArray(env, fftArray(ShortArray, Length), Length);
}extern "C"

JNIEXPORT jshort JNICALL
Java_com_example_spectrumaudiofrequency_SinusoidConverter_C_1FFT(JNIEnv *env, __unused jclass clazz,
                                                                 jshortArray j_wave_piece,
                                                                 jint frequency) {
    int length = env->GetArrayLength(j_wave_piece);
    jshort *C_WavePiece = env->GetShortArrayElements(j_wave_piece, nullptr);

    auto points_distance = (float) (((2 * M_PI) / (float) length) * ((float) frequency) /
                                    PRECISION);

    float x_some = 0;
    float y_some = 0;

    for (int i = 0; i < length; i++) {
        auto radius = (float) C_WavePiece[i];
        auto f = (float) i;

        x_some += (cos(f * points_distance) * radius);
        y_some += (sin(f * points_distance) * radius);
    }

    auto WavePieceLength_F = (float) length;

    x_some /= WavePieceLength_F;
    y_some /= WavePieceLength_F;

    return (jshort) ((x_some + y_some) * PRECISION);
}