/*
 * Edge AI Room Presence Detection
 *
 * Arduino Nano 33 BLE Sense + OV7675
 *
 * Funkcije:
 * 1. TinyML audio klasifikacija:
 *      - speech
 *      - footsteps
 *      - door
 *      - other_noise
 *      - silence
 *
 * 2. speech / footsteps / door predstavljaju potencijalno
 *    prisustvo osobe.
 *
 * 3. Nakon audio trigger-a Arduino:
 *      - snima 3 frame-a OV7675 kamerom
 *      - nad svakim frame-om lokalno pokrece FOMO person detection
 *      - ako FOMO nadje osobu, iscrtava bounding box i duze treperi crvenom LED
 *      - iste frame-ove zatim salje Raspberry Pi-ju preko USB Serial-a
 *
 * 4. Raspberry Pi vraca:
 *      PERSON_DETECTED
 *      ili
 *      NO_PERSON
 *
 * 5. RGB LED:
 *      plava   = monitoring
 *      zelena  = audio event / slanje frame-ova
 *      crvena  = osoba potvrđena
 *
 * 6. ACTIVATE_ALARM komanda:
 *      crvena LED treperi
 */


// ============================================================
// EDGE IMPULSE SETTINGS
// ============================================================

#define EIDSP_QUANTIZE_FILTERBANK 0

#define EI_CLASSIFIER_SLICES_PER_MODEL_WINDOW 4


// ============================================================
// LIBRARIES
// ============================================================

#include <PDM.h>
#include <Arduino_OV767X.h>
#include <Room_Presence_Multi_Inferencing.h>
#include <string.h>


// ============================================================
// RGB LED
// Nano 33 BLE Sense koristi active-low RGB LED.
// LOW = ukljuceno
// HIGH = iskljuceno
// ============================================================

#define RED_LED_PIN    22
#define GREEN_LED_PIN  24
#define BLUE_LED_PIN   23


// ============================================================
// AUDIO INFERENCE BUFFERS
// ============================================================

typedef struct {
    signed short *buffers[2];
    unsigned char buf_select;
    unsigned char buf_ready;
    unsigned int buf_count;
    unsigned int n_samples;
} inference_t;

static inference_t inference;

static bool record_ready = false;
static bool microphoneRunning = false;

static signed short *sampleBuffer;

static bool debug_nn = false;

static int print_results =
    -(EI_CLASSIFIER_SLICES_PER_MODEL_WINDOW);


// ============================================================
// SYSTEM SETTINGS
// ============================================================

// Minimalni confidence za audio trigger.
const float TRIGGER_THRESHOLD = 0.75f;

// Sprečava da isti zvuk odmah generise mnogo trigger-a.
const unsigned long TRIGGER_COOLDOWN_MS = 3000;

unsigned long lastTriggerTime = 0;


// ============================================================
// CAMERA SETTINGS
// ============================================================

// Saljemo tri frame-a Raspberry Pi-ju.
const int FRAME_COUNT = 3;

// QQVGA:
// 160 x 120
// RGB565 = 2 bytes/pixel
//
// 160 * 120 * 2 = 38400 bytes
//
// Namerno ne koristimo QVGA zbog RAM-a na Arduinu.
static byte cameraFrame[160 * 120 * 2];

// ============================================================
// FOMO SETTINGS
// ============================================================

// Novi FOMO impulse je treniran na 96x96 grayscale ulazu.
const int FOMO_INPUT_WIDTH = 96;
const int FOMO_INPUT_HEIGHT = 96;
const int FOMO_INPUT_PIXELS = FOMO_INPUT_WIDTH * FOMO_INPUT_HEIGHT;

// Originalni OV7675 frame je 160x120 (4:3). Edge Impulse impulse koristi
// resize "Fit shortest axis" za kvadratni 96x96 ulaz. Ekvivalentno tome
// uzimamo centralni 120x120 deo slike i skaliramo ga na 96x96.
const int FOMO_SOURCE_CROP_SIZE = 120;
const int FOMO_SOURCE_X_OFFSET = 20;

// Rezultat lokalne FOMO detekcije.
typedef struct {
    bool found;
    float confidence;
    int x;
    int y;
    int width;
    int height;
} FomoDetection;


// ============================================================
// SYSTEM STATE
// ============================================================

enum SystemState {
    MONITORING,
    SENDING_FRAMES,
    WAITING_FOR_RESULT
};

SystemState systemState = MONITORING;


// ============================================================
// LED FUNCTIONS
// ============================================================

void ledsOff()
{
    digitalWrite(RED_LED_PIN, HIGH);
    digitalWrite(GREEN_LED_PIN, HIGH);
    digitalWrite(BLUE_LED_PIN, HIGH);
}


void setMonitoringLed()
{
    ledsOff();

    // plava
    digitalWrite(BLUE_LED_PIN, LOW);
}


void setSendingLed()
{
    ledsOff();

    // zelena
    digitalWrite(GREEN_LED_PIN, LOW);
}


void setPersonDetectedLed()
{
    ledsOff();

    // crvena
    digitalWrite(RED_LED_PIN, LOW);
}


void activateAlarm()
{
    Serial.println("ALARM_STARTED");

    for (int i = 0; i < 6; i++) {

        // crvena ON
        digitalWrite(RED_LED_PIN, LOW);
        digitalWrite(GREEN_LED_PIN, HIGH);
        digitalWrite(BLUE_LED_PIN, HIGH);

        delay(250);

        // sve OFF
        ledsOff();

        delay(250);
    }

    // Posle alarma vracamo LED prema trenutnom stanju.

    if (systemState == MONITORING) {
        setMonitoringLed();
    }
    else if (systemState == WAITING_FOR_RESULT) {
        setSendingLed();
    }
}


// ============================================================
// LOCAL FOMO PERSON DETECTION
// ============================================================

static int fomo_camera_signal_get_data(
    size_t offset,
    size_t length,
    float *out_ptr
)
{
    for (size_t i = 0; i < length; i++) {

        size_t pixelIndex = offset + i;

        int targetX = pixelIndex % FOMO_INPUT_WIDTH;
        int targetY = pixelIndex / FOMO_INPUT_WIDTH;

        int sourceX =
            FOMO_SOURCE_X_OFFSET +
            (targetX * FOMO_SOURCE_CROP_SIZE) / FOMO_INPUT_WIDTH;

        int sourceY =
            (targetY * FOMO_SOURCE_CROP_SIZE) / FOMO_INPUT_HEIGHT;

        int byteIndex = (sourceY * 160 + sourceX) * 2;

        // OV7675 RGB565: MSB pa LSB.
        uint16_t rgb565 =
            ((uint16_t)cameraFrame[byteIndex] << 8) |
            cameraFrame[byteIndex + 1];

        uint8_t r5 = (rgb565 >> 11) & 0x1F;
        uint8_t g6 = (rgb565 >> 5) & 0x3F;
        uint8_t b5 = rgb565 & 0x1F;

        uint8_t r8 = (r5 * 255) / 31;
        uint8_t g8 = (g6 * 255) / 63;
        uint8_t b8 = (b5 * 255) / 31;

        uint32_t rgb888 =
            ((uint32_t)r8 << 16) |
            ((uint32_t)g8 << 8) |
            b8;

        out_ptr[i] = (float)rgb888;
    }

    return 0;
}


// Pretvara FOMO 96x96 koordinate nazad u koordinate originalnog
// OV7675 frame-a 160x120.
static int fomoXToCameraX(int x)
{
    return FOMO_SOURCE_X_OFFSET +
           (x * FOMO_SOURCE_CROP_SIZE) / FOMO_INPUT_WIDTH;
}

static int fomoYToCameraY(int y)
{
    return (y * FOMO_SOURCE_CROP_SIZE) / FOMO_INPUT_HEIGHT;
}

static int fomoWToCameraW(int w)
{
    return (w * FOMO_SOURCE_CROP_SIZE) / FOMO_INPUT_WIDTH;
}

static int fomoHToCameraH(int h)
{
    return (h * FOMO_SOURCE_CROP_SIZE) / FOMO_INPUT_HEIGHT;
}


// Upisuje RGB565 piksel u cameraFrame.
static void setCameraPixelRGB565(int x, int y, uint16_t color)
{
    if (x < 0 || x >= 160 || y < 0 || y >= 120) {
        return;
    }

    int index = (y * 160 + x) * 2;

    cameraFrame[index] = (color >> 8) & 0xFF;
    cameraFrame[index + 1] = color & 0xFF;
}


static void drawFomoBoxOnCameraFrame(const FomoDetection &det)
{
    const uint16_t RED_RGB565 = 0xF800;
    const int thickness = 2;

    int x1 = det.x;
    int y1 = det.y;
    int x2 = det.x + det.width - 1;
    int y2 = det.y + det.height - 1;

    if (x1 < 0) x1 = 0;
    if (y1 < 0) y1 = 0;
    if (x2 > 159) x2 = 159;
    if (y2 > 119) y2 = 119;

    for (int t = 0; t < thickness; t++) {
        for (int x = x1; x <= x2; x++) {
            setCameraPixelRGB565(x, y1 + t, RED_RGB565);
            setCameraPixelRGB565(x, y2 - t, RED_RGB565);
        }

        for (int y = y1; y <= y2; y++) {
            setCameraPixelRGB565(x1 + t, y, RED_RGB565);
            setCameraPixelRGB565(x2 - t, y, RED_RGB565);
        }
    }
}


static bool runFomoOnCurrentFrame(FomoDetection &bestDetection)
{
    bestDetection.found = false;
    bestDetection.confidence = 0.0f;
    bestDetection.x = 0;
    bestDetection.y = 0;
    bestDetection.width = 0;
    bestDetection.height = 0;

    signal_t imageSignal;
    imageSignal.total_length = FOMO_INPUT_PIXELS;
    imageSignal.get_data = &fomo_camera_signal_get_data;

    ei_impulse_result_t fomoResult = {0};

    EI_IMPULSE_ERROR error = run_classifier(
        &impulse_handle_1114969_1,
        &imageSignal,
        &fomoResult,
        false
    );

    if (error != EI_IMPULSE_OK) {
        return false;
    }

    for (size_t i = 0; i < fomoResult.bounding_boxes_count; i++) {

        auto bb = fomoResult.bounding_boxes[i];

        if (bb.value <= 0.0f) {
            continue;
        }

        if (strcmp(bb.label, "person") != 0) {
            continue;
        }

        if (!bestDetection.found || bb.value > bestDetection.confidence) {

            bestDetection.found = true;
            bestDetection.confidence = bb.value;

            bestDetection.x = fomoXToCameraX(bb.x);
            bestDetection.y = fomoYToCameraY(bb.y);
            bestDetection.width = fomoWToCameraW(bb.width);
            bestDetection.height = fomoHToCameraH(bb.height);
        }
    }

    return bestDetection.found;
}


// FOMO lokalna potvrda namerno treperi duze od Raspberry PERSON_DETECTED
// indikacije, kako bi se na demonstraciji jasno razlikovale dve detekcije.
static void activateFomoPersonLed()
{
    for (int i = 0; i < 8; i++) {

        digitalWrite(RED_LED_PIN, LOW);
        digitalWrite(GREEN_LED_PIN, HIGH);
        digitalWrite(BLUE_LED_PIN, HIGH);

        delay(250);

        ledsOff();

        delay(250);
    }

    // Posle FOMO signalizacije i dalje cekamo Raspberry rezultat.
    setSendingLed();
}


// ============================================================
// SEND CAMERA FRAMES + LOCAL FOMO
// ============================================================

void sendCameraFrames()
{
    int bytesPerFrame =
        Camera.width() *
        Camera.height() *
        Camera.bytesPerPixel();

    bool anyFomoPerson = false;
    FomoDetection strongestDetection = {false, 0.0f, 0, 0, 0, 0};
    int strongestFrame = -1;

    Serial.println("FRAME_SEQUENCE_START");

    Serial.print("FRAME_COUNT:");
    Serial.println(FRAME_COUNT);

    Serial.print("FRAME_WIDTH:");
    Serial.println(Camera.width());

    Serial.print("FRAME_HEIGHT:");
    Serial.println(Camera.height());

    Serial.print("FRAME_BYTES:");
    Serial.println(bytesPerFrame);


    for (int i = 0; i < FRAME_COUNT; i++) {

        // 1. Snimanje OV7675 frame-a.
        Camera.readFrame(cameraFrame);

        // 2. Lokalna FOMO inferencija direktno na Arduinu.
        FomoDetection currentDetection;
        bool personFound = runFomoOnCurrentFrame(currentDetection);

        if (personFound) {
            anyFomoPerson = true;

            // Crtamo FOMO okvir direktno u frame pre slanja Raspberry Pi-ju.
            drawFomoBoxOnCameraFrame(currentDetection);

            if (
                strongestFrame < 0 ||
                currentDetection.confidence > strongestDetection.confidence
            ) {
                strongestDetection = currentDetection;
                strongestFrame = i;
            }
        }

        // 3. Postojeci protokol za Raspberry Pi ostaje isti.
        Serial.print("FRAME:");
        Serial.print(i);
        Serial.print(":");
        Serial.println(bytesPerFrame);

        Serial.write(
            cameraFrame,
            bytesPerFrame
        );

        Serial.flush();

        delay(250);
    }

    Serial.println();
    Serial.println("FRAME_SEQUENCE_END");

    if (anyFomoPerson) {

        Serial.println("FOMO_PERSON_DETECTED");

        Serial.print("FOMO_FRAME:");
        Serial.println(strongestFrame);

        Serial.print("FOMO_CONFIDENCE:");
        Serial.println(strongestDetection.confidence, 3);

        Serial.print("FOMO_BOX:");
        Serial.print(strongestDetection.x);
        Serial.print(":");
        Serial.print(strongestDetection.y);
        Serial.print(":");
        Serial.print(strongestDetection.width);
        Serial.print(":");
        Serial.println(strongestDetection.height);

        activateFomoPersonLed();
    }
    else {
        Serial.println("FOMO_NO_PERSON");
    }
}


// ============================================================
// HANDLE COMMANDS FROM RASPBERRY PI
// ============================================================

void handleSerialCommand()
{
    if (!Serial.available()) {
        return;
    }

    String command = Serial.readStringUntil('\n');

    command.trim();


    // --------------------------------------------------------
    // Raspberry je potvrdio osobu
    // --------------------------------------------------------

    if (command == "PERSON_DETECTED") {

        Serial.println("ACK:PERSON_DETECTED");

        setPersonDetectedLed();

        /*
         * Crvena ostaje kratko upaljena da se jasno vidi
         * fizicka reakcija sistema.
         */
        delay(2000);

        systemState = MONITORING;
        ensureMicrophoneStarted();

        setMonitoringLed();
    }


    // --------------------------------------------------------
    // Raspberry nije pronasao osobu
    // --------------------------------------------------------

    else if (command == "NO_PERSON") {

        Serial.println("ACK:NO_PERSON");

        systemState = MONITORING;
        ensureMicrophoneStarted();

        setMonitoringLed();
    }


    // --------------------------------------------------------
    // Rucna aktuacija iz Raspberry / Android aplikacije
    // --------------------------------------------------------

    else if (command == "ACTIVATE_ALARM") {

        Serial.println("ACK:ACTIVATE_ALARM");

        activateAlarm();
    }


    // --------------------------------------------------------
    // Kasnije Android moze ukljuciti monitoring
    // --------------------------------------------------------

    else if (command == "MONITORING_ON") {

        systemState = MONITORING;
        ensureMicrophoneStarted();

        setMonitoringLed();

        Serial.println("ACK:MONITORING_ON");
    }


    // --------------------------------------------------------
    // Kasnije Android moze iskljuciti monitoring
    // --------------------------------------------------------

    else if (command == "MONITORING_OFF") {

        /*
         * Za sada koristimo WAITING_FOR_RESULT kao stanje
         * u kome se audio klasifikacija ne izvrsava.
         */

        systemState = WAITING_FOR_RESULT;
        microphone_inference_end();

        ledsOff();

        Serial.println("ACK:MONITORING_OFF");
    }
}


// ============================================================
// SETUP
// ============================================================

void setup()
{
    // --------------------------------------------------------
    // Serial
    // --------------------------------------------------------

    Serial.begin(115200);

    while (!Serial);

    Serial.setTimeout(100);


    Serial.println();
    Serial.println("====================================");
    Serial.println("EDGE AI ROOM PRESENCE SYSTEM");
    Serial.println("====================================");


    // --------------------------------------------------------
    // RGB LED
    // --------------------------------------------------------

    pinMode(RED_LED_PIN, OUTPUT);
    pinMode(GREEN_LED_PIN, OUTPUT);
    pinMode(BLUE_LED_PIN, OUTPUT);

    ledsOff();

    setMonitoringLed();


    // --------------------------------------------------------
    // Edge Impulse informacije
    // --------------------------------------------------------

    Serial.println();
    Serial.println("Initializing TinyML audio model...");

    ei_printf("Inferencing settings:\n");

    ei_printf(
        "\tInterval: %.2f ms.\n",
        (float)EI_CLASSIFIER_INTERVAL_MS
    );

    ei_printf(
        "\tFrame size: %d\n",
        EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE
    );

    ei_printf(
        "\tSample length: %d ms.\n",
        EI_CLASSIFIER_RAW_SAMPLE_COUNT / 16
    );

    ei_printf(
        "\tNo. of classes: %d\n",
        sizeof(ei_classifier_inferencing_categories) /
        sizeof(ei_classifier_inferencing_categories[0])
    );


    // --------------------------------------------------------
    // TinyML classifier
    // --------------------------------------------------------

    run_classifier_init(&impulse_handle_1094947_1);
    run_classifier_init(&impulse_handle_1114969_1);


    // --------------------------------------------------------
    // Microphone
    // --------------------------------------------------------

    if (
        microphone_inference_start(
            EI_CLASSIFIER_SLICE_SIZE
        ) == false
    ) {

        ei_printf(
            "ERR: Could not allocate audio buffer "
            "(size %d)\r\n",
            EI_CLASSIFIER_RAW_SAMPLE_COUNT
        );

        while (1);
    }

    Serial.println("MICROPHONE_READY");


    // --------------------------------------------------------
    // Camera
    // --------------------------------------------------------

    Serial.println("Initializing OV7675 camera...");

    if (!Camera.begin(QQVGA, RGB565, 1)) {

        Serial.println("CAMERA_INIT_FAILED");

        // Crvena LED oznacava gresku.
        setPersonDetectedLed();

        while (1);
    }


    Serial.println("CAMERA_READY");

    Serial.print("CAMERA_WIDTH:");
    Serial.println(Camera.width());

    Serial.print("CAMERA_HEIGHT:");
    Serial.println(Camera.height());

    Serial.print("CAMERA_BYTES_PER_PIXEL:");
    Serial.println(Camera.bytesPerPixel());


    // --------------------------------------------------------

    Serial.println();
    Serial.println("SYSTEM_READY");
    Serial.println("STATE:MONITORING");

    setMonitoringLed();
}


// ============================================================
// MAIN LOOP
// ============================================================

void loop()
{
    handleSerialCommand();

    if (systemState != MONITORING) {

        delay(10);

        return;
    }

    bool m = microphone_inference_record();

    if (!m) {

        ei_printf(
            "ERR: Failed to record audio...\n"
        );

        return;
    }
    signal_t signal;

    signal.total_length =
        EI_CLASSIFIER_SLICE_SIZE;

    signal.get_data =
        &microphone_audio_signal_get_data;


    ei_impulse_result_t result = {0};

    EI_IMPULSE_ERROR r =
        run_classifier_continuous(
            &impulse_handle_1094947_1,
            &signal,
            &result,
            debug_nn
        );

    if (r != EI_IMPULSE_OK) {

        ei_printf(
            "ERR: Failed to run classifier (%d)\n",
            r
        );

        return;
    }


    if (
        ++print_results >=
        EI_CLASSIFIER_SLICES_PER_MODEL_WINDOW
    ) {

        float maxScore = 0.0f;

        const char *maxLabel = "";


        Serial.println();

        Serial.println("PREDICTIONS_START");


        for (
            size_t ix = 0;
            ix < EI_CLASSIFIER_LABEL_COUNT;
            ix++
        ) {

            const char *label =
                result.classification[ix].label;

            float score =
                result.classification[ix].value;


            Serial.print("PREDICTION:");

            Serial.print(label);

            Serial.print(":");

            Serial.println(score, 5);

            if (score > maxScore) {

                maxScore = score;

                maxLabel = label;
            }
        }


        Serial.println("PREDICTIONS_END");


        bool humanPresenceEvent =
            strcmp(maxLabel, "speech") == 0 ||
            strcmp(maxLabel, "footsteps") == 0 ||
            strcmp(maxLabel, "door") == 0;


        bool confidenceHighEnough =
            maxScore >= TRIGGER_THRESHOLD;


        bool cooldownFinished =
            (
                millis() - lastTriggerTime
                >= TRIGGER_COOLDOWN_MS
            );



        if (
            humanPresenceEvent &&
            confidenceHighEnough &&
            cooldownFinished
        ) {

            Serial.println();

            Serial.print("TRIGGER:");

            Serial.print(maxLabel);

            Serial.print(":");

            Serial.println(maxScore, 3);


            lastTriggerTime = millis();



            systemState = SENDING_FRAMES;

            setSendingLed();


            Serial.println("STATE:SENDING_FRAMES");

            microphone_inference_end();



            sendCameraFrames();


            systemState = WAITING_FOR_RESULT;

            Serial.println("STATE:WAITING_FOR_RESULT");
        }


        print_results = 0;
    }
}


static void pdm_data_ready_inference_callback(void)
{
    int bytesAvailable = PDM.available();

    int bytesRead =
        PDM.read(
            (char *)&sampleBuffer[0],
            bytesAvailable
        );


    if (record_ready == true) {

        for (
            int i = 0;
            i < bytesRead >> 1;
            i++
        ) {

            inference
                .buffers[inference.buf_select]
                        [inference.buf_count++] =
                sampleBuffer[i];


            if (
                inference.buf_count >=
                inference.n_samples
            ) {

                inference.buf_select ^= 1;

                inference.buf_count = 0;

                inference.buf_ready = 1;
            }
        }
    }
}

static bool microphone_inference_start(
    uint32_t n_samples
)
{
    inference.buffers[0] =
        (signed short *)malloc(
            n_samples *
            sizeof(signed short)
        );


    if (inference.buffers[0] == NULL) {

        return false;
    }


    inference.buffers[1] =
        (signed short *)malloc(
            n_samples *
            sizeof(signed short)
        );


    if (inference.buffers[1] == NULL) {

        free(inference.buffers[0]);

        return false;
    }


    sampleBuffer =
        (signed short *)malloc(
            (n_samples >> 1) *
            sizeof(signed short)
        );


    if (sampleBuffer == NULL) {

        free(inference.buffers[0]);

        free(inference.buffers[1]);

        return false;
    }


    inference.buf_select = 0;

    inference.buf_count = 0;

    inference.n_samples = n_samples;

    inference.buf_ready = 0;


    PDM.onReceive(
        &pdm_data_ready_inference_callback
    );


    PDM.setBufferSize(
        (n_samples >> 1) *
        sizeof(int16_t)
    );

    if (
        !PDM.begin(
            1,
            EI_CLASSIFIER_FREQUENCY
        )
    ) {

        ei_printf(
            "Failed to start PDM!\n"
        );

        return false;
    }

    PDM.setGain(127);

    record_ready = true;
    microphoneRunning = true;

    return true;
}



static bool microphone_inference_record(void)
{
    bool ret = true;


    if (inference.buf_ready == 1) {

        ei_printf(
            "Warning: audio buffer overrun.\n"
        );
		
        ret = false;
    }


    while (inference.buf_ready == 0) {

        delay(1);
    }


    inference.buf_ready = 0;

    return ret;
}

static int microphone_audio_signal_get_data(
    size_t offset,
    size_t length,
    float *out_ptr
)
{
    numpy::int16_to_float(
        &inference
            .buffers[inference.buf_select ^ 1]
                    [offset],
        out_ptr,
        length
    );


    return 0;
}


static void microphone_inference_end(void)
{
    if (!microphoneRunning) {
        return;
    }

    record_ready = false;

    PDM.end();

    free(inference.buffers[0]);
    free(inference.buffers[1]);
    free(sampleBuffer);

    inference.buffers[0] = NULL;
    inference.buffers[1] = NULL;
    sampleBuffer = NULL;

    inference.buf_ready = 0;
    inference.buf_count = 0;

    microphoneRunning = false;
}


static bool ensureMicrophoneStarted(void)
{
    if (microphoneRunning) {
        return true;
    }

    Serial.println("MICROPHONE_RESTART");

    if (!microphone_inference_start(EI_CLASSIFIER_SLICE_SIZE)) {
        Serial.println("MICROPHONE_RESTART_FAILED");
        return false;
    }

    Serial.println("MICROPHONE_READY");
    return true;
}


// ============================================================
// VERIFY EDGE IMPULSE SENSOR TYPE
// ============================================================

#if !defined(EI_CLASSIFIER_SENSOR) || \
    EI_CLASSIFIER_SENSOR != EI_CLASSIFIER_SENSOR_MICROPHONE

#error "Invalid model for current sensor."

#endif