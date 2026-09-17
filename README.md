RoomPresence AI is an Edge AI / TinyML system for detecting human presence in a room using an Arduino Nano 33 BLE Sense Lite, Raspberry Pi 4, an OV7675 camera, a laptop camera, and an Android application.

The system combines lightweight on-device event detection with more computationally demanding object detection on the Raspberry Pi.

## System Overview

The main processing flow is:

1. Arduino continuously analyzes audio using an Edge Impulse TinyML model.
2. Relevant sound events such as speech, footsteps, or a door event trigger scene capture.
3. The Arduino captures frames using the OV7675 camera.
4. An experimental FOMO model can perform local person detection directly on the Arduino.
5. Frames are sent to the Raspberry Pi through USB Serial.
6. The Raspberry Pi requests a higher-quality snapshot from the laptop camera.
7. EfficientDet-Lite0 performs person detection on the Raspberry Pi.
8. If a person is confirmed, the system starts a short live monitoring session.
9. Events and system status are forwarded to the Android application.

## Hardware

- Arduino Nano 33 BLE Sense Lite
- OV7675 camera module
- Raspberry Pi 4
- Laptop camera
- Android device / emulator

## Main Technologies

### Arduino / TinyML

- Edge Impulse
- Audio classification
- FOMO object detection
- Arduino_OV767X
- PDM microphone
- RGB LED feedback

### Raspberry Pi / Edge AI

- Python
- LiteRT / TensorFlow Lite
- EfficientDet-Lite0
- OpenCV
- Flask
- WebSocket
- MQTT / Mosquitto
- FFmpeg / HLS

### Android

- Kotlin
- Jetpack Compose
- WebSocket communication
- ExoPlayer for live HLS playback

## Audio Model

The audio model recognizes:

- `speech`
- `footsteps`
- `door`
- `other_noise`
- `silence`

The positive classes are used as triggers for further scene analysis.

## Object Detection

Two approaches are used:

### FOMO on Arduino

FOMO was tested as an experimental local detector directly on the Arduino Nano 33 BLE Sense Lite.

The model uses:

- 96 × 96 image input
- grayscale preprocessing
- quantized int8 inference
- EON Compiler, RAM optimized

Because of the limited image quality of the OV7675 camera and the constrained dataset, the FOMO model achieved limited detection accuracy. This experiment was included to evaluate the feasibility and limitations of executing person detection directly on the microcontroller while the audio model is also present.

### EfficientDet-Lite0 on Raspberry Pi

The Raspberry Pi performs the main person detection using EfficientDet-Lite0 and a higher-quality image obtained from the laptop camera.

This stage is used as the more reliable confirmation of room occupancy.

## Communication

The system uses several communication mechanisms:

- USB Serial between Arduino and Raspberry Pi
- HTTP between Raspberry Pi and laptop camera server
- WebSocket between Raspberry Pi and Android application
- MQTT for local event distribution
- HLS for live video streaming

