from ai_edge_litert.interpreter import Interpreter

MODEL_PATH = "models/efficientdet_lite0.tflite"

print("Ucitavam model...")

interpreter = Interpreter(model_path=MODEL_PATH)
interpreter.allocate_tensors()

print("MODEL LOADED")

print("\nINPUT:")
for x in interpreter.get_input_details():
    print(
        "name =", x["name"],
        "shape =", x["shape"],
        "dtype =", x["dtype"]
    )

print("\nOUTPUT:")
for x in interpreter.get_output_details():
    print(
        "name =", x["name"],
        "shape =", x["shape"],
        "dtype =", x["dtype"]
    )
