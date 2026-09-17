import sys
import numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter

MODEL_PATH = "models/efficientdet_lite0.tflite"

image_path = sys.argv[1]

interpreter = Interpreter(model_path=MODEL_PATH)
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

image = Image.open(image_path).convert("RGB")
image = image.resize((320, 320))

input_data = np.array(image, dtype=np.uint8)
input_data = np.expand_dims(input_data, axis=0)

interpreter.set_tensor(
    input_details[0]["index"],
    input_data
)

interpreter.invoke()

print("\n=== OUTPUT TENSORS ===")

for i, detail in enumerate(output_details):

    value = interpreter.get_tensor(
        detail["index"]
    )

    print()
    print("OUTPUT", i)
    print("name:", detail["name"])
    print("shape:", value.shape)

    flattened = value.flatten()

    print(
        "first values:",
        flattened[:30]
    )
