import os
import time
import json
import threading
import subprocess

import cv2
import serial
import requests
import numpy as np
import paho.mqtt.client as mqtt

from PIL import Image, ImageDraw
from flask import Flask, render_template, jsonify
from flask_sock import Sock
from ai_edge_litert.interpreter import Interpreter


# ============================================================
# CONFIGURATION
# ============================================================

SERIAL_PORT = "/dev/ttyACM0"
BAUD_RATE = 115200

ARDUINO_WIDTH = 160
ARDUINO_HEIGHT = 120

# Laptop je samo kvalitetniji izvor kamere.
LAPTOP_CAMERA_URL = "http://172.20.10.3:5001/snapshot"
LAPTOP_VIDEO_URL = "http://172.20.10.3:5001/video"

MODEL_PATH = "models/efficientdet_lite0.tflite"

PERSON_CLASS_ID = 0
PERSON_CONFIDENCE_THRESHOLD = 0.50

# Live session se gasi ako 5 sekundi nema osobe.
NO_PERSON_TIMEOUT = 5.0

# Bez obzira na sve, maksimalno 30 sekundi.
MAX_SESSION_SECONDS = 30.0

# Raspberry radi oko 5 inference frame-ova u sekundi.
LIVE_TARGET_FPS = 5.0

# Koliko fajlova cuvamo.
MAX_ARDUINO_FRAMES = 9
MAX_DETECTION_IMAGES = 3
MAX_VIDEOS = 3

# ============================================================
# MQTT CONFIGURATION
# ============================================================

MQTT_BROKER = "localhost"
MQTT_PORT = 1883

MQTT_TOPIC_SYSTEM = "room/system"
MQTT_TOPIC_AUDIO = "room/audio"
MQTT_TOPIC_PERSON = "room/person"
MQTT_TOPIC_LIVE = "room/live"
MQTT_TOPIC_MONITORING = "room/monitoring"
MQTT_TOPIC_ALARM = "room/alarm"

# ============================================================
# PATHS
# ============================================================

BASE_DIR = os.path.dirname(
    os.path.abspath(__file__)
)

ARDUINO_FRAME_DIR = os.path.join(
    BASE_DIR,
    "static",
    "arduino_frames"
)

DETECTION_DIR = os.path.join(
    BASE_DIR,
    "static",
    "detections"
)

VIDEO_DIR = os.path.join(
    BASE_DIR,
    "static",
    "videos"
)

HLS_DIR = os.path.join(
    BASE_DIR,
    "static",
    "hls"
)

os.makedirs(
    ARDUINO_FRAME_DIR,
    exist_ok=True
)

os.makedirs(
    DETECTION_DIR,
    exist_ok=True
)

os.makedirs(
    VIDEO_DIR,
    exist_ok=True
)

os.makedirs(
    HLS_DIR,
    exist_ok=True
)


HLS_PLAYLIST_PATH = os.path.join(
    HLS_DIR,
    "live.m3u8"
)

HLS_PUBLIC_URL = (
    "/static/hls/live.m3u8"
)


# ============================================================
# FLASK + WEBSOCKET
# ============================================================

app = Flask(__name__)
sock = Sock(app)


# ============================================================
# SYSTEM STATE
# ============================================================

system_state = {
    "arduino_connected": False,

    "state": "Starting",

    "audio_event": "-",
    "audio_confidence": 0.0,

    "frames": [],

    "person_detected": None,
    "person_confidence": 0.0,

    "detection_image": None,

    "live_session_active": False,
    "persons_detected": 0,
    "live_elapsed": 0.0,

    "video_recording": False,
    "last_video": None,

    "hls_available": False,
    "hls_url": HLS_PUBLIC_URL
}


# ============================================================
# SHARED OBJECTS
# ============================================================

ser = None

live_session_lock = threading.Lock()

websocket_clients = []
websocket_clients_lock = threading.Lock()

# ============================================================
# MQTT
# ============================================================

mqtt_client = mqtt.Client(
    mqtt.CallbackAPIVersion.VERSION2
)


def mqtt_publish(
    topic,
    data
):
    try:
        payload = json.dumps(
            data
        )

        mqtt_client.publish(
            topic,
            payload
        )

        print(
            "MQTT PUBLISH:",
            topic,
            payload
        )

    except Exception as e:
        print(
            "MQTT publish error:",
            e
        )


def on_mqtt_connect(
    client,
    userdata,
    flags,
    reason_code,
    properties
):
    if reason_code == 0:

        print(
            "MQTT connected to Mosquitto."
        )

        mqtt_publish(
            MQTT_TOPIC_SYSTEM,
            {
                "status": "online",
                "timestamp": time.time()
            }
        )

    else:

        print(
            "MQTT connection failed:",
            reason_code
        )


def on_mqtt_disconnect(
    client,
    userdata,
    disconnect_flags,
    reason_code,
    properties
):
    print(
        "MQTT disconnected:",
        reason_code
    )


mqtt_client.on_connect = (
    on_mqtt_connect
)

mqtt_client.on_disconnect = (
    on_mqtt_disconnect
)

# ============================================================
# LITERT MODEL
# ============================================================

print("Loading LiteRT object detection model...")

interpreter = Interpreter(
    model_path=MODEL_PATH
)

interpreter.allocate_tensors()

input_details = (
    interpreter.get_input_details()
)

output_details = (
    interpreter.get_output_details()
)

input_shape = input_details[0]["shape"]

MODEL_HEIGHT = int(
    input_shape[1]
)

MODEL_WIDTH = int(
    input_shape[2]
)

print(
    f"Object detector ready: "
    f"{MODEL_WIDTH}x{MODEL_HEIGHT}"
)


# ============================================================
# CLEANUP HELPERS
# ============================================================

def cleanup_old_files(
    folder,
    max_files
):
    try:
        files = []

        for filename in os.listdir(
            folder
        ):
            path = os.path.join(
                folder,
                filename
            )

            if os.path.isfile(
                path
            ):
                files.append(
                    path
                )

        files.sort(
            key=os.path.getmtime,
            reverse=True
        )

        for old_file in files[
            max_files:
        ]:
            try:
                os.remove(
                    old_file
                )

                print(
                    "Deleted old file:",
                    old_file
                )

            except Exception as e:
                print(
                    "Could not delete:",
                    old_file,
                    e
                )

    except Exception as e:
        print(
            "Cleanup error:",
            e
        )


def clear_hls_directory():
    try:
        for filename in os.listdir(
            HLS_DIR
        ):
            path = os.path.join(
                HLS_DIR,
                filename
            )

            if (
                os.path.isfile(path)
                and (
                    filename.endswith(
                        ".m3u8"
                    )
                    or filename.endswith(
                        ".ts"
                    )
                )
            ):
                try:
                    os.remove(
                        path
                    )

                except Exception:
                    pass

    except Exception as e:
        print(
            "HLS cleanup error:",
            e
        )


# ============================================================
# WEBSOCKET
# ============================================================

def broadcast_event(
    event_type,
    data=None
):
    message = {
        "type": event_type,
        "timestamp": time.time()
    }

    if data:
        message.update(
            data
        )

    payload = json.dumps(
        message
    )

    dead_clients = []

    with websocket_clients_lock:

        for ws in websocket_clients:
            try:
                ws.send(
                    payload
                )

            except Exception:
                dead_clients.append(
                    ws
                )

        for ws in dead_clients:
            try:
                websocket_clients.remove(
                    ws
                )

            except ValueError:
                pass

    print(
        "WebSocket broadcast:",
        payload
    )


# ============================================================
# SERIAL
# ============================================================

def send_arduino_command(
    command
):
    global ser

    try:
        if (
            ser
            and ser.is_open
        ):
            ser.write(
                (
                    command
                    + "\n"
                ).encode(
                    "utf-8"
                )
            )

            ser.flush()

            print(
                "Sent to Arduino:",
                command
            )

    except Exception as e:
        print(
            "Arduino command error:",
            e
        )


def read_exact(
    serial_port,
    size
):
    data = bytearray()

    while len(data) < size:

        chunk = serial_port.read(
            size - len(data)
        )

        if not chunk:
            raise TimeoutError(
                f"Expected {size} bytes, "
                f"received {len(data)}"
            )

        data.extend(
            chunk
        )

    return bytes(
        data
    )


# ============================================================
# RGB565 -> PIL IMAGE
# ============================================================

def rgb565_to_image(
    raw
):
    data = np.frombuffer(
        raw,
        dtype=np.uint8
    )

    high = data[
        0::2
    ].astype(
        np.uint16
    )

    low = data[
        1::2
    ].astype(
        np.uint16
    )

    rgb565 = (
        (high << 8)
        | low
    )

    r = (
        ((rgb565 >> 11) & 0x1F)
        * 255
        // 31
    )

    g = (
        ((rgb565 >> 5) & 0x3F)
        * 255
        // 63
    )

    b = (
        (rgb565 & 0x1F)
        * 255
        // 31
    )

    rgb = np.stack(
        [
            r,
            g,
            b
        ],
        axis=-1
    )

    rgb = rgb.reshape(
        (
            ARDUINO_HEIGHT,
            ARDUINO_WIDTH,
            3
        )
    )

    return Image.fromarray(
        rgb.astype(
            np.uint8
        )
    )


# ============================================================
# AUDIO EVENT
# ============================================================

def process_trigger(
    line
):
    parts = line.split(
        ":"
    )

    if len(parts) < 3:
        return

    event = parts[1]

    try:
        confidence = float(
            parts[2]
        )

    except ValueError:
        confidence = 0.0

    system_state[
        "audio_event"
    ] = event

    system_state[
        "audio_confidence"
    ] = confidence
    
    mqtt_publish(
		MQTT_TOPIC_AUDIO,
		{
			"event": event,
			"confidence": confidence,
			"timestamp": time.time()
		}
	)
    
    broadcast_event(
		"AUDIO_EVENT",
		{
			"event": event,
			"confidence": confidence
		}
	)

    system_state[
        "state"
    ] = "Audio event detected"

    system_state[
        "frames"
    ] = []

    system_state[
        "person_detected"
    ] = None

    system_state[
        "person_confidence"
    ] = 0.0

    system_state[
        "detection_image"
    ] = None

    print()
    print("==============================")
    print("AUDIO EVENT")
    print("Type:", event)
    print("Confidence:", confidence)
    print("==============================")


# ============================================================
# ARDUINO CAMERA FRAME
# ============================================================

def process_frame(
    frame_number,
    frame_size
):
    system_state[
        "state"
    ] = (
        f"Receiving Arduino frame "
        f"{frame_number + 1}/3"
    )

    print(
        f"Receiving Arduino frame "
        f"{frame_number}: "
        f"{frame_size} bytes"
    )

    raw = read_exact(
        ser,
        frame_size
    )

    image = rgb565_to_image(
        raw
    )

    timestamp = int(
        time.time()
        * 1000
    )

    filename = (
        f"frame_"
        f"{timestamp}_"
        f"{frame_number}.png"
    )

    path = os.path.join(
        ARDUINO_FRAME_DIR,
        filename
    )

    image.save(
        path
    )

    cleanup_old_files(
        ARDUINO_FRAME_DIR,
        MAX_ARDUINO_FRAMES
    )

    relative_path = (
        "/static/"
        "arduino_frames/"
        + filename
    )

    system_state[
        "frames"
    ].append(
        relative_path
    )


# ============================================================
# LAPTOP SNAPSHOT
# ============================================================

def capture_laptop_snapshot():

    system_state[
        "state"
    ] = (
        "Capturing "
        "high-resolution image"
    )

    print(
        "Requesting laptop snapshot..."
    )

    response = requests.get(
        LAPTOP_CAMERA_URL,
        timeout=10
    )

    response.raise_for_status()

    timestamp = int(
        time.time()
        * 1000
    )

    filename = (
        f"laptop_"
        f"{timestamp}.jpg"
    )

    path = os.path.join(
        DETECTION_DIR,
        filename
    )

    with open(
        path,
        "wb"
    ) as file:
        file.write(
            response.content
        )

    return path


# ============================================================
# INITIAL PERSON DETECTION
# ============================================================

def detect_person_image(
    image_path
):
    image = Image.open(
        image_path
    ).convert(
        "RGB"
    )

    original_width, original_height = (
        image.size
    )

    resized = image.resize(
        (
            MODEL_WIDTH,
            MODEL_HEIGHT
        )
    )

    input_data = np.array(
        resized,
        dtype=np.uint8
    )

    input_data = np.expand_dims(
        input_data,
        axis=0
    )

    interpreter.set_tensor(
        input_details[0][
            "index"
        ],
        input_data
    )

    interpreter.invoke()

    boxes = interpreter.get_tensor(
        output_details[0][
            "index"
        ]
    )[0]

    classes = interpreter.get_tensor(
        output_details[1][
            "index"
        ]
    )[0]

    scores = interpreter.get_tensor(
        output_details[2][
            "index"
        ]
    )[0]

    num_detections = int(
        interpreter.get_tensor(
            output_details[3][
                "index"
            ]
        )[0]
    )

    detections = []

    for i in range(
        num_detections
    ):
        class_id = int(
            classes[i]
        )

        score = float(
            scores[i]
        )

        if (
            class_id
            != PERSON_CLASS_ID
        ):
            continue

        if (
            score
            < PERSON_CONFIDENCE_THRESHOLD
        ):
            continue

        ymin, xmin, ymax, xmax = (
            boxes[i]
        )

        xmin = max(
            0.0,
            min(
                1.0,
                float(xmin)
            )
        )

        xmax = max(
            0.0,
            min(
                1.0,
                float(xmax)
            )
        )

        ymin = max(
            0.0,
            min(
                1.0,
                float(ymin)
            )
        )

        ymax = max(
            0.0,
            min(
                1.0,
                float(ymax)
            )
        )

        detections.append({
            "confidence":
                score,

            "box": (
                int(
                    xmin
                    * original_width
                ),
                int(
                    ymin
                    * original_height
                ),
                int(
                    xmax
                    * original_width
                ),
                int(
                    ymax
                    * original_height
                )
            )
        })

    return (
        image,
        detections
    )


# ============================================================
# SAVE INITIAL DETECTION IMAGE
# ============================================================

def save_detection_image(
    image,
    detections
):
    draw = ImageDraw.Draw(
        image
    )

    best_confidence = 0.0

    for detection in detections:

        x1, y1, x2, y2 = (
            detection[
                "box"
            ]
        )

        confidence = (
            detection[
                "confidence"
            ]
        )

        best_confidence = max(
            best_confidence,
            confidence
        )

        draw.rectangle(
            [
                x1,
                y1,
                x2,
                y2
            ],
            outline="red",
            width=5
        )

        draw.text(
            (
                x1,
                max(
                    0,
                    y1 - 20
                )
            ),
            (
                f"PERSON "
                f"{confidence * 100:.1f}%"
            ),
            fill="red"
        )

    timestamp = int(
        time.time()
        * 1000
    )

    filename = (
        f"person_detection_"
        f"{timestamp}.jpg"
    )

    path = os.path.join(
        DETECTION_DIR,
        filename
    )

    image.save(
        path,
        quality=95
    )

    relative_path = (
        "/static/"
        "detections/"
        + filename
    )

    return (
        relative_path,
        best_confidence
    )


# ============================================================
# LIVE LITERT PERSON DETECTION
# ============================================================

def detect_persons_in_frame(
    frame
):
    height, width = (
        frame.shape[:2]
    )

    rgb = cv2.cvtColor(
        frame,
        cv2.COLOR_BGR2RGB
    )

    resized = cv2.resize(
        rgb,
        (
            MODEL_WIDTH,
            MODEL_HEIGHT
        )
    )

    input_data = np.expand_dims(
        resized.astype(
            np.uint8
        ),
        axis=0
    )

    interpreter.set_tensor(
        input_details[0][
            "index"
        ],
        input_data
    )

    interpreter.invoke()

    boxes = interpreter.get_tensor(
        output_details[0][
            "index"
        ]
    )[0]

    classes = interpreter.get_tensor(
        output_details[1][
            "index"
        ]
    )[0]

    scores = interpreter.get_tensor(
        output_details[2][
            "index"
        ]
    )[0]

    num_detections = int(
        interpreter.get_tensor(
            output_details[3][
                "index"
            ]
        )[0]
    )

    detections = []

    for i in range(
        num_detections
    ):
        class_id = int(
            classes[i]
        )

        score = float(
            scores[i]
        )

        if (
            class_id
            != PERSON_CLASS_ID
        ):
            continue

        if (
            score
            < PERSON_CONFIDENCE_THRESHOLD
        ):
            continue

        ymin, xmin, ymax, xmax = (
            boxes[i]
        )

        xmin = max(
            0.0,
            min(
                1.0,
                float(xmin)
            )
        )

        xmax = max(
            0.0,
            min(
                1.0,
                float(xmax)
            )
        )

        ymin = max(
            0.0,
            min(
                1.0,
                float(ymin)
            )
        )

        ymax = max(
            0.0,
            min(
                1.0,
                float(ymax)
            )
        )

        detections.append({
            "confidence":
                score,

            "box": (
                int(
                    xmin
                    * width
                ),
                int(
                    ymin
                    * height
                ),
                int(
                    xmax
                    * width
                ),
                int(
                    ymax
                    * height
                )
            )
        })

    return detections


# ============================================================
# DRAW LIVE DETECTIONS
# ============================================================

def draw_live_detections(
    frame,
    detections
):
    output = frame.copy()

    for detection in detections:

        x1, y1, x2, y2 = (
            detection[
                "box"
            ]
        )

        confidence = (
            detection[
                "confidence"
            ]
        )

        cv2.rectangle(
            output,
            (
                x1,
                y1
            ),
            (
                x2,
                y2
            ),
            (
                0,
                0,
                255
            ),
            3
        )

        label = (
            f"PERSON "
            f"{confidence * 100:.1f}%"
        )

        (
            text_width,
            text_height
        ), _ = cv2.getTextSize(
            label,
            cv2.FONT_HERSHEY_SIMPLEX,
            0.6,
            2
        )

        label_y = max(
            y1,
            text_height + 10
        )

        cv2.rectangle(
            output,
            (
                x1,
                label_y
                - text_height
                - 10
            ),
            (
                x1
                + text_width
                + 10,
                label_y
            ),
            (
                0,
                0,
                255
            ),
            -1
        )

        cv2.putText(
            output,
            label,
            (
                x1 + 5,
                label_y - 5
            ),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.6,
            (
                255,
                255,
                255
            ),
            2
        )

    # LIVE indikator

    cv2.circle(
        output,
        (
            25,
            25
        ),
        8,
        (
            0,
            0,
            255
        ),
        -1
    )

    cv2.putText(
        output,
        "LIVE EDGE AI",
        (
            42,
            32
        ),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.7,
        (
            0,
            0,
            255
        ),
        2
    )

    cv2.putText(
        output,
        (
            f"Persons: "
            f"{len(detections)}"
        ),
        (
            20,
            65
        ),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.65,
        (
            255,
            255,
            255
        ),
        2
    )

    return output


# ============================================================
# START FFMPEG HLS
# ============================================================

def start_hls_ffmpeg(
    width,
    height
):
    clear_hls_directory()

    segment_pattern = os.path.join(
        HLS_DIR,
        "segment_%05d.ts"
    )

    command = [
        "ffmpeg",

        "-hide_banner",
        "-loglevel",
        "warning",

        # Input: raw OpenCV BGR frames.
        "-f",
        "rawvideo",

        "-pix_fmt",
        "bgr24",

        "-s",
        f"{width}x{height}",

        "-r",
        str(
            LIVE_TARGET_FPS
        ),

        "-i",
        "-",

        # No audio.
        "-an",

        # H.264 encoder.
        "-c:v",
        "libx264",

        "-preset",
        "ultrafast",

        "-tune",
        "zerolatency",

        # Android compatibility.
        "-pix_fmt",
        "yuv420p",

        # Frequent keyframes for low HLS latency.
        "-g",
        str(
            max(
                1,
                int(
                    LIVE_TARGET_FPS
                )
            )
        ),

        "-keyint_min",
        str(
            max(
                1,
                int(
                    LIVE_TARGET_FPS
                )
            )
        ),

        "-sc_threshold",
        "0",

        # HLS
        "-f",
        "hls",

        "-hls_time",
        "1",

        "-hls_list_size",
        "3",

        "-hls_flags",
        "delete_segments",

        "-hls_segment_filename",
        segment_pattern,

        HLS_PLAYLIST_PATH
    ]

    print(
        "Starting FFmpeg HLS..."
    )

    process = subprocess.Popen(
        command,
        stdin=subprocess.PIPE
    )

    return process


# ============================================================
# LIVE EDGE AI SESSION
# ============================================================

def run_live_edge_ai_session():

    if not live_session_lock.acquire(
        blocking=False
    ):
        print(
            "Live session already running."
        )

        return

    cap = None
    video_writer = None
    ffmpeg_process = None

    video_filename = None

    try:
        print()
        print("================================")
        print("LIVE EDGE AI SESSION START")
        print("================================")

        system_state[
            "live_session_active"
        ] = True
        
        mqtt_publish(
			MQTT_TOPIC_LIVE,
			{
				"active": True,
				"timestamp": time.time()
			}
		)

        system_state[
            "video_recording"
        ] = True

        system_state[
            "hls_available"
        ] = False

        system_state[
            "state"
        ] = "LIVE EDGE AI"

        system_state[
            "persons_detected"
        ] = 0

        system_state[
            "live_elapsed"
        ] = 0.0

        send_arduino_command(
            "MONITORING_OFF"
        )

        # ----------------------------------------------------
        # Laptop network video source
        # ----------------------------------------------------

        cap = cv2.VideoCapture(
            LAPTOP_VIDEO_URL
        )

        if not cap.isOpened():
            raise RuntimeError(
                "Could not open laptop video stream"
            )

        ret, first_frame = cap.read()

        if not ret:
            raise RuntimeError(
                "Could not read first laptop frame"
            )

        height, width = (
            first_frame.shape[:2]
        )

        print(
            "Live source resolution:",
            width,
            "x",
            height
        )

        # ----------------------------------------------------
        # Start HLS encoder
        # ----------------------------------------------------

        ffmpeg_process = start_hls_ffmpeg(
            width,
            height
        )

        # ----------------------------------------------------
        # Saved event recording on Raspberry
        # ----------------------------------------------------

        timestamp = time.strftime(
            "%Y%m%d_%H%M%S"
        )

        video_filename = (
            f"edge_ai_event_"
            f"{timestamp}.mp4"
        )

        video_path = os.path.join(
            VIDEO_DIR,
            video_filename
        )

        fourcc = cv2.VideoWriter_fourcc(
            *"mp4v"
        )

        video_writer = cv2.VideoWriter(
            video_path,
            fourcc,
            LIVE_TARGET_FPS,
            (
                width,
                height
            )
        )

        if not video_writer.isOpened():
            raise RuntimeError(
                "Could not create recording file"
            )

        session_start = (
            time.time()
        )

        last_person_seen = (
            time.time()
        )

        frame_interval = (
            1.0
            / LIVE_TARGET_FPS
        )

        next_frame_time = (
            time.time()
        )

        current_frame = (
            first_frame
        )

        last_broadcast_count = (
            None
        )

        hls_started_event_sent = (
            False
        )

        # ----------------------------------------------------
        # LIVE LOOP
        # ----------------------------------------------------

        while True:

            elapsed = (
                time.time()
                - session_start
            )

            system_state[
                "live_elapsed"
            ] = round(
                elapsed,
                1
            )

            if (
                elapsed
                >= MAX_SESSION_SECONDS
            ):
                print(
                    "Maximum live session "
                    "duration reached."
                )

                break

            # First iteration already has first_frame.

            if current_frame is not None:

                frame = current_frame
                current_frame = None

            else:

                ret, frame = cap.read()

                if not ret:
                    print(
                        "Could not read "
                        "video frame."
                    )

                    time.sleep(
                        0.1
                    )

                    continue

            # ------------------------------------------------
            # Edge AI inference on Raspberry
            # ------------------------------------------------

            detections = (
                detect_persons_in_frame(
                    frame
                )
            )

            person_count = len(
                detections
            )

            system_state[
                "persons_detected"
            ] = person_count

            if (
                person_count
                != last_broadcast_count
            ):
                broadcast_event(
                    "PERSON_COUNT",
                    {
                        "persons":
                            person_count
                    }
                )
                
                mqtt_publish(
					MQTT_TOPIC_PERSON,
					{
						"detected": person_count > 0,
						"persons": person_count,
						"source": "live",
						"timestamp": time.time()
					}
				)

                last_broadcast_count = (
                    person_count
                )

            if person_count > 0:
                last_person_seen = (
                    time.time()
                )

            # ------------------------------------------------
            # Draw bounding boxes
            # ------------------------------------------------

            annotated = (
                draw_live_detections(
                    frame,
                    detections
                )
            )

            # ------------------------------------------------
            # Save processed video
            # ------------------------------------------------

            video_writer.write(
                annotated
            )

            # ------------------------------------------------
            # Send the SAME annotated frame to FFmpeg/HLS
            # ------------------------------------------------

            if (
                ffmpeg_process
                and
                ffmpeg_process.stdin
            ):
                try:
                    ffmpeg_process.stdin.write(
                        annotated.tobytes()
                    )

                    ffmpeg_process.stdin.flush()

                except (
                    BrokenPipeError,
                    OSError
                ) as e:

                    print(
                        "FFmpeg pipe error:",
                        e
                    )

                    raise RuntimeError(
                        "FFmpeg HLS process stopped"
                    )

            # ------------------------------------------------
            # Broadcast LIVE_STARTED only when playlist exists
            # ------------------------------------------------

            if (
                not hls_started_event_sent
                and
                os.path.exists(
                    HLS_PLAYLIST_PATH
                )
            ):
                system_state[
                    "hls_available"
                ] = True

                broadcast_event(
                    "LIVE_STARTED",
                    {
                        "hls":
                            HLS_PUBLIC_URL
                    }
                )

                hls_started_event_sent = (
                    True
                )

                print(
                    "HLS stream available:",
                    HLS_PUBLIC_URL
                )

            # ------------------------------------------------
            # End session if nobody seen for 5s
            # ------------------------------------------------

            no_person_for = (
                time.time()
                - last_person_seen
            )

            if (
                no_person_for
                >= NO_PERSON_TIMEOUT
            ):
                print(
                    f"No person for "
                    f"{NO_PERSON_TIMEOUT}s."
                )

                break

            # ------------------------------------------------
            # Limit inference/output FPS
            # ------------------------------------------------

            next_frame_time += (
                frame_interval
            )

            sleep_time = (
                next_frame_time
                - time.time()
            )

            if sleep_time > 0:

                time.sleep(
                    sleep_time
                )

            else:

                next_frame_time = (
                    time.time()
                )

        # ----------------------------------------------------
        # Final saved recording
        # ----------------------------------------------------

        if video_filename:

            system_state[
                "last_video"
            ] = (
                "/static/videos/"
                + video_filename
            )

            cleanup_old_files(
                VIDEO_DIR,
                MAX_VIDEOS
            )

        print(
            "Live session finished."
        )

    except Exception as e:

        print(
            "LIVE SESSION ERROR:",
            e
        )

        system_state[
            "state"
        ] = "Live session error"

        broadcast_event(
            "LIVE_ERROR",
            {
                "message":
                    str(e)
            }
        )

    finally:

        # ----------------------------------------------------
        # Stop saved video
        # ----------------------------------------------------

        if video_writer is not None:

            try:
                video_writer.release()

            except Exception:
                pass

        # ----------------------------------------------------
        # Stop camera
        # ----------------------------------------------------

        if cap is not None:

            try:
                cap.release()

            except Exception:
                pass

        # ----------------------------------------------------
        # Stop FFmpeg cleanly
        # ----------------------------------------------------

        if ffmpeg_process is not None:

            try:

                if (
                    ffmpeg_process.stdin
                ):
                    ffmpeg_process.stdin.close()

            except Exception:
                pass

            try:
                ffmpeg_process.wait(
                    timeout=5
                )

            except subprocess.TimeoutExpired:

                ffmpeg_process.terminate()

                try:
                    ffmpeg_process.wait(
                        timeout=2
                    )

                except Exception:
                    ffmpeg_process.kill()

        system_state[
            "live_session_active"
        ] = False
        
        mqtt_publish(
			MQTT_TOPIC_LIVE,
			{
				"active": False,
				"last_video":
					system_state["last_video"],
				"timestamp": time.time()
			}
		)

        system_state[
            "video_recording"
        ] = False

        system_state[
            "persons_detected"
        ] = 0

        system_state[
            "live_elapsed"
        ] = 0.0

        system_state[
            "hls_available"
        ] = False

        broadcast_event(
            "LIVE_ENDED",
            {
                "video":
                    system_state[
                        "last_video"
                    ]
            }
        )

        system_state[
            "state"
        ] = "Monitoring"

        send_arduino_command(
            "MONITORING_ON"
        )

        print("================================")
        print("LIVE EDGE AI SESSION END")
        print("================================")
        print()

        live_session_lock.release()


# ============================================================
# INITIAL VISION PIPELINE
# ============================================================

def run_person_detection_pipeline():

    try:
        print()
        print("==============================")
        print("VISION PIPELINE START")
        print("==============================")

        snapshot_path = (
            capture_laptop_snapshot()
        )

        system_state[
            "state"
        ] = (
            "Running LiteRT "
            "person detection"
        )

        image, detections = (
            detect_person_image(
                snapshot_path
            )
        )

        (
            detection_image,
            best_confidence
        ) = save_detection_image(
            image,
            detections
        )

        system_state[
            "detection_image"
        ] = detection_image

        # Brisemo originalni snapshot,
        # ostaje samo detection slika.

        try:
            os.remove(
                snapshot_path
            )

        except Exception:
            pass

        cleanup_old_files(
            DETECTION_DIR,
            MAX_DETECTION_IMAGES
        )

        # ----------------------------------------------------
        # PERSON
        # ----------------------------------------------------

        if len(
            detections
        ) > 0:

            system_state[
                "person_detected"
            ] = True

            system_state[
                "person_confidence"
            ] = best_confidence

            system_state[
                "state"
            ] = "PERSON DETECTED"

            print()
            print(
                "PERSON DETECTED"
            )

            print(
                "Confidence:",
                best_confidence
            )

            send_arduino_command(
                "PERSON_DETECTED"
            )
            
            mqtt_publish(
				MQTT_TOPIC_PERSON,
				{
					"detected": True,
					"confidence": best_confidence,
					"persons": len(detections),
					"timestamp": time.time()
				}
			)

            broadcast_event(
                "PERSON_DETECTED",
                {
                    "confidence":
                        best_confidence,

                    "image":
                        detection_image,

                    "persons":
                        len(detections)
                }
            )

            live_thread = (
                threading.Thread(
                    target=
                        run_live_edge_ai_session,
                    daemon=True
                )
            )

            live_thread.start()

        # ----------------------------------------------------
        # NO PERSON
        # ----------------------------------------------------

        else:

            system_state[
                "person_detected"
            ] = False

            system_state[
                "person_confidence"
            ] = 0.0

            system_state[
                "state"
            ] = "NO PERSON"

            send_arduino_command(
                "NO_PERSON"
            )
            
            mqtt_publish(
				MQTT_TOPIC_PERSON,
				{
					"detected": False,
					"confidence": 0.0,
					"persons": 0,
					"timestamp": time.time()
				}
			)

            broadcast_event(
                "NO_PERSON"
            )

        print("==============================")
        print("VISION PIPELINE END")
        print("==============================")

    except Exception as e:

        print(
            "Vision pipeline error:",
            e
        )

        system_state[
            "state"
        ] = "Vision error"

        send_arduino_command(
            "NO_PERSON"
        )

        broadcast_event(
            "VISION_ERROR",
            {
                "message":
                    str(e)
            }
        )


# ============================================================
# SERIAL WORKER
# ============================================================

def serial_worker():

    global ser

    while True:

        try:

            system_state[
                "state"
            ] = (
                "Connecting to Arduino"
            )

            print(
                "Opening",
                SERIAL_PORT
            )

            ser = serial.Serial(
                SERIAL_PORT,
                BAUD_RATE,
                timeout=10
            )

            time.sleep(
                2
            )

            system_state[
                "arduino_connected"
            ] = True

            system_state[
                "state"
            ] = "Monitoring"

            print(
                "Arduino connected."
            )

            send_arduino_command(
                "MONITORING_ON"
            )

            while True:

                line_bytes = (
                    ser.readline()
                )

                if not line_bytes:
                    continue

                line = (
                    line_bytes
                    .decode(
                        "utf-8",
                        errors="ignore"
                    )
                    .strip()
                )

                if not line:
                    continue

                print(
                    "ARDUINO:",
                    line
                )

                # --------------------------------------------
                # TinyML audio trigger
                # --------------------------------------------

                if line.startswith(
                    "TRIGGER:"
                ):

                    process_trigger(
                        line
                    )

                # --------------------------------------------
                # Arduino camera binary frame
                # --------------------------------------------

                elif line.startswith(
                    "FRAME:"
                ):

                    parts = line.split(
                        ":"
                    )

                    if len(parts) != 3:
                        continue

                    try:

                        frame_number = int(
                            parts[1]
                        )

                        frame_size = int(
                            parts[2]
                        )

                    except ValueError:
                        continue

                    process_frame(
                        frame_number,
                        frame_size
                    )

                # --------------------------------------------
                # 3 Arduino frames finished
                # --------------------------------------------

                elif (
                    line
                    == "FRAME_SEQUENCE_END"
                ):

                    system_state[
                        "state"
                    ] = (
                        "Arduino frames received"
                    )

                    print(
                        "All Arduino frames received."
                    )

                    if not system_state[
                        "live_session_active"
                    ]:

                        run_person_detection_pipeline()

                # --------------------------------------------
                # Arduino ACK
                # --------------------------------------------

                elif line.startswith(
                    "ACK:"
                ):

                    print(
                        "Arduino acknowledgement:",
                        line
                    )

        except Exception as e:

            print(
                "Serial error:",
                e
            )

            system_state[
                "arduino_connected"
            ] = False

            system_state[
                "state"
            ] = (
                "Arduino disconnected"
            )

            broadcast_event(
                "ARDUINO_DISCONNECTED"
            )

            try:

                if ser:
                    ser.close()

            except Exception:
                pass

            time.sleep(
                2
            )


# ============================================================
# FLASK ROUTES
# ============================================================
def get_archive_files(
    folder,
    public_prefix,
    allowed_extensions
):
    items = []

    try:
        for filename in os.listdir(
            folder
        ):
            path = os.path.join(
                folder,
                filename
            )

            if not os.path.isfile(
                path
            ):
                continue

            extension = os.path.splitext(
                filename
            )[1].lower()

            if (
                extension
                not in allowed_extensions
            ):
                continue

            modified = os.path.getmtime(
                path
            )

            items.append({
                "name": filename,
                "url": (
                    public_prefix
                    + filename
                ),
                "timestamp": modified
            })

        items.sort(
            key=lambda item:
                item["timestamp"],
            reverse=True
        )

    except Exception as e:
        print(
            "Archive error:",
            e
        )

    return items

@app.route("/api/archive")
def api_archive():

    detection_images = (
        get_archive_files(
            DETECTION_DIR,
            "/static/detections/",
            {
                ".jpg",
                ".jpeg",
                ".png"
            }
        )
    )

    arduino_frames = (
        get_archive_files(
            ARDUINO_FRAME_DIR,
            "/static/arduino_frames/",
            {
                ".jpg",
                ".jpeg",
                ".png"
            }
        )
    )

    videos = (
        get_archive_files(
            VIDEO_DIR,
            "/static/videos/",
            {
                ".mp4",
                ".avi"
            }
        )
    )

    return jsonify({
        "detection_images":
            detection_images,

        "arduino_frames":
            arduino_frames,

        "videos":
            videos
    })

@app.route("/")
def index():

    return render_template(
        "index.html"
    )


@app.route("/api/status")
def api_status():

    return jsonify(
        system_state
    )


# ============================================================
# WEBSOCKET ROUTE
# ============================================================

@sock.route("/ws")
def websocket(ws):

    print(
        "WebSocket client connected."
    )

    with websocket_clients_lock:

        websocket_clients.append(
            ws
        )

    try:

        ws.send(
            json.dumps({
                "type":
                    "CONNECTED",

                "timestamp":
                    time.time()
            })
        )

        ws.send(
            json.dumps({
                "type":
                    "STATUS",

                "data":
                    system_state,

                "timestamp":
                    time.time()
            })
        )

        while True:

            message = ws.receive()

            if message is None:
                break

            print(
                "WebSocket received:",
                message
            )

            # --------------------------------------------
            # Alarm
            # --------------------------------------------

            if (
                message
                == "ACTIVATE_ALARM"
            ):

                send_arduino_command(
                    "ACTIVATE_ALARM"
                )
                
                mqtt_publish(
					MQTT_TOPIC_ALARM,
					{
						"activated": True,
						"timestamp": time.time()
					}
				)

                ws.send(
                    json.dumps({
                        "type":
                            "ALARM_ACTIVATED"
                    })
                )

            # --------------------------------------------
            # Monitoring ON
            # --------------------------------------------

            elif (
                message
                == "MONITORING_ON"
            ):

                send_arduino_command(
                    "MONITORING_ON"
                )
                
                mqtt_publish(
					MQTT_TOPIC_MONITORING,
					{
						"enabled": True,
						"timestamp": time.time()
					}
				)

                ws.send(
                    json.dumps({
                        "type":
                            "MONITORING_ON"
                    })
                )

            # --------------------------------------------
            # Monitoring OFF
            # --------------------------------------------

            elif (
                message
                == "MONITORING_OFF"
            ):

                send_arduino_command(
                    "MONITORING_OFF"
                )
                
                mqtt_publish(
					MQTT_TOPIC_MONITORING,
					{
						"enabled": False,
						"timestamp": time.time()
					}
				)

                ws.send(
                    json.dumps({
                        "type":
                            "MONITORING_OFF"
                    })
                )

            # --------------------------------------------
            # Status
            # --------------------------------------------

            elif (
                message
                == "STATUS"
            ):

                ws.send(
                    json.dumps({
                        "type":
                            "STATUS",

                        "data":
                            system_state
                    })
                )

            # --------------------------------------------
            # Ping
            # --------------------------------------------

            elif (
                message
                == "PING"
            ):

                ws.send(
                    json.dumps({
                        "type":
                            "PONG",

                        "timestamp":
                            time.time()
                    })
                )

            else:

                ws.send(
                    json.dumps({
                        "type":
                            "UNKNOWN_COMMAND",

                        "message":
                            message
                    })
                )

    except Exception as e:

        print(
            "WebSocket error:",
            e
        )

    finally:

        with websocket_clients_lock:

            try:

                websocket_clients.remove(
                    ws
                )

            except ValueError:
                pass

        print(
            "WebSocket client disconnected."
        )


# ============================================================
# MAIN
# ============================================================

if __name__ == "__main__":

    clear_hls_directory()

    # --------------------------------------------------------
    # MQTT
    # --------------------------------------------------------

    try:
        mqtt_client.connect(
            MQTT_BROKER,
            MQTT_PORT,
            60
        )

        mqtt_client.loop_start()

    except Exception as e:
        print(
            "MQTT startup error:",
            e
        )

    # --------------------------------------------------------
    # SERIAL
    # --------------------------------------------------------

    serial_thread = (
        threading.Thread(
            target=
                serial_worker,
            daemon=True
        )
    )

    serial_thread.start()

    # --------------------------------------------------------
    # FLASK
    # --------------------------------------------------------

    app.run(
        host="0.0.0.0",
        port=5000,
        debug=False,
        threaded=True
    )

