import unittest
from unittest.mock import Mock

from merge_and_check import fetch_file_strings, fetch_translation_files


def response(data):
    result = Mock()
    result.json.return_value = data
    return result


class FetchStringsTest(unittest.TestCase):
    def test_drafts_survive_pagination_and_file_collection(self):
        draft = {"id": 1, "key": "draft", "stage": 0, "translation": "草稿译文"}
        checked = {"id": 2, "key": "checked", "stage": 3, "translation": "已检查译文"}
        session = Mock()
        session.get.side_effect = [
            response([{"id": 42, "name": "test.json"}]),
            response({"results": [draft], "pageCount": 2}),
            response({"results": [checked], "pageCount": 2}),
        ]
        files, count, corpus = fetch_translation_files(session)
        self.assertEqual(files, [[draft, checked]])
        self.assertEqual(count, 1)
        self.assertEqual(corpus[0]["translation"], "草稿译文")
        self.assertEqual(corpus[0]["_source_file"]["id"], 42)
        calls = session.get.call_args_list[1:]
        self.assertEqual([call.kwargs["params"]["page"] for call in calls], [1, 2])
        for call in calls:
            self.assertTrue(call.args[0].endswith("/strings"))
            self.assertEqual(call.kwargs["params"]["file"], 42)
            self.assertNotIn("stage", call.kwargs["params"])

    def test_empty_and_invalid_pages(self):
        session = Mock()
        session.get.return_value = response({"results": [], "pageCount": 0})
        self.assertEqual(fetch_file_strings(session, 42), [])
        for payload in [{"results": []}, {"results": [], "pageCount": 2}]:
            with self.subTest(payload=payload):
                session.get.return_value = response(payload)
                with self.assertRaises(ValueError):
                    fetch_file_strings(session, 42)

    def test_http_failure_is_not_returned_as_partial_data(self):
        session = Mock()
        failed = response({})
        failed.raise_for_status.side_effect = RuntimeError("HTTP failure")
        session.get.side_effect = [response({"results": [{"id": 1}], "pageCount": 2}), failed]
        with self.assertRaisesRegex(RuntimeError, "HTTP failure"):
            fetch_file_strings(session, 42)


if __name__ == "__main__":
    unittest.main()
