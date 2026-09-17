import os
import sys
import numpy as np
from PIL import Image, ImageDraw
from ai_edge_litert.interpreter import Interpreter


MODEL_PATH = "models/efficientdet_lite0.tflite"

PERSON_CLASS_ID = 0
CONFIDENCE_THRESHOLD = 0.50


class PersonDetector:

    def __init__(self, model_path=MODEL_PATH):

        self.interpreter = Interpreter(
            model_path=model_path
        )

        self.interpreter.allocate_tensors()

        self.input_details = (
            self.interpreter.get_input_details()
        )

        self.output_details = (
            self.interpreter.get_output_details()
        )

        input_shape = self.input_details[0]["shape"]

        self.input_height = int(input_shape[1])
        self.input_width = int(input_shape[2])

        print(
            f"Person detector ready "
            f"({self.input_width}x{self.input_height})"
        )


    def detect(self, image_path):

        original = Image.open(
            image_path
        ).convert("RGB")

        original_width, original_height = (
            original.size
        )

        resized = original.resize(
            (
                self.input_width,
                self.input_height
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

        self.interpreter.set_tensor(
            self.input_details[0]["index"],
            input_data
        )

        self.interpreter.invoke()


        # EfficientDet Lite output layout:
        #
        # output 0 -> boxes
        # output 1 -> classes
        # output 2 -> scores
        # output 3 -> number of detections

        boxes = self.interpreter.get_tensor(
            self.output_details[0]["index"]
        )[0]

        classes = self.interpreter.get_tensor(
            self.output_details[1]["index"]
        )[0]

        scores = self.interpreter.get_tensor(
            self.output_details[2]["index"]
        )[0]

        num_detections = int(
            self.interpreter.get_tensor(
                self.output_details[3]["index"]
            )[0]
        )


        detections = []

        for i in range(num_detections):

            score = float(scores[i])

            class_id = int(classes[i])

            if score < CONFIDENCE_THRESHOLD:
                continue

            if class_id != PERSON_CLASS_ID:
                continue


            ymin, xmin, ymax, xmax = boxes[i]

            x1 = int(
                xmin * original_width
            )

            y1 = int(
                ymin * original_height
            )

            x2 = int(
                xmax * original_width
            )

            y2 = int(
                ymax * original_height
            )


            detections.append({
                "confidence": score,
                "box": (
                    x1,
                    y1,
                    x2,
                    y2
                )
            })


        return detections


def draw_detections(
    image_path,
    detections,
    output_path
):

    image = Image.open(
        image_path
    ).convert("RGB")

    draw = ImageDraw.Draw(
        image
    )


    for detection in detections:

        x1, y1, x2, y2 = (
            detection["box"]
        )

        confidence = (
            detection["confidence"]
        )

        draw.rectangle(
            [x1, y1, x2, y2],
            width=3
        )

        draw.text(
            (x1, max(0, y1 - 12)),
            f"person {confidence:.2f}"
        )


    image.save(
        output_path
    )


if __name__ == "__main__":

    if len(sys.argv) != 2:

        print(
            "Usage:"
            "\npython person_detector.py "
            "<image_path>"
        )

        sys.exit(1)


    image_path = sys.argv[1]

    if not os.path.exists(
        image_path
    ):

        print(
            "Image does not exist:",
            image_path
        )

        sys.exit(1)


    detector = PersonDetector()

    detections = detector.detect(
        image_path
    )


    print()
    print(
        "Person detections:",
        len(detections)
    )


    for detection in detections:

        print(
            "PERSON confidence:",
            f"{detection['confidence']:.3f}",
            "box:",
            detection["box"]
        )


    output_path = (
        "person_detection_result.png"
    )

    draw_detections(
        image_path,
        detections,
        output_path
    )


    print()
    print(
        "Result saved:",
        output_path
    )
