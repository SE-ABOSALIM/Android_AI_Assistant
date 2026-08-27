import unittest
from types import SimpleNamespace
from unittest.mock import patch

from V3.services.model_service import inference
from V3.validation.service import validate_and_build_response


class _Scalar:
    def __init__(self, value):
        self.value = value

    def item(self):
        return self.value


class _Probabilities:
    shape = (6,)

    def __getitem__(self, index):
        return self


class _Inputs(dict):
    def to(self, device):
        return self


class _Tokenizer:
    def __call__(self, *args, **kwargs):
        return _Inputs()


class _Model:
    config = SimpleNamespace(
        id2label={
            0: "UNKNOWN_COMMAND__none",
            1: "OPEN_APP__app_name=maps",
            2: "GO_HOME__none",
            3: "GO_BACK__none",
            4: "SHOW_RECENTS__none",
            5: "TAKE_SCREENSHOT__none",
        }
    )

    def __call__(self, **kwargs):
        return SimpleNamespace(logits=object())


class _NoGrad:
    def __enter__(self):
        return None

    def __exit__(self, exc_type, exc, traceback):
        return False


class _Torch:
    def __init__(self):
        self.requested_topk = None

    def no_grad(self):
        return _NoGrad()

    def softmax(self, logits, dim):
        return _Probabilities()

    def topk(self, probabilities, k):
        self.requested_topk = k
        return SimpleNamespace(
            values=[
                _Scalar(0.999),
                _Scalar(0.0005),
                _Scalar(0.0003),
                _Scalar(0.0001),
                _Scalar(0.0001),
            ],
            indices=[_Scalar(3), _Scalar(1), _Scalar(4), _Scalar(0), _Scalar(2)],
        )


class ModelInferenceContractTests(unittest.TestCase):
    def test_predictionKeepsLegacyTopOneDecisionWithoutPublicAlternatives(self):
        torch = _Torch()
        with patch.object(
            inference,
            "get_model_bundle",
            return_value=(torch, _Tokenizer(), _Model(), "cpu"),
        ):
            result = inference.predict_model("go back", "EN")

        self.assertEqual(5, torch.requested_topk)
        self.assertEqual(
            {"intent", "parameters", "confidence", "raw_label"},
            set(result),
        )
        self.assertEqual("GO_BACK", result["intent"])
        self.assertEqual({}, result["parameters"])
        self.assertEqual(0.999, result["confidence"])
        self.assertEqual("GO_BACK__none", result["raw_label"])

        validated = validate_and_build_response(
            original_text="go back",
            language="EN",
            model_intent=result["intent"],
            model_parameters=result["parameters"],
            confidence=result["confidence"],
            raw_label=result["raw_label"],
        )
        self.assertTrue(validated["accepted"])
        self.assertEqual("GO_BACK", validated["intent"])


if __name__ == "__main__":
    unittest.main()
