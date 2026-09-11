import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch
import requests
import script
import organize_openproject as organize


class ImportTests(unittest.TestCase):
    def test_successful_rerun_does_not_create_duplicates(self):
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory) / 'state.json'
            post = Mock(return_value=Mock(status_code=201, json=lambda: {'id': 123}))
            rows = [{'subject': 'Example task'}]
            self.assertEqual(0, script.run_import(rows, state, post=post))
            self.assertEqual(0, script.run_import(rows, state, post=post))
            self.assertEqual(1, post.call_count)
            self.assertEqual((10, 60), post.call_args.kwargs['timeout'])

    def test_timeout_requires_reconciliation_before_rerun(self):
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory) / 'state.json'
            post = Mock(side_effect=requests.Timeout())
            rows = [{'subject': 'Example task'}]
            self.assertEqual(1, script.run_import(rows, state, post=post))
            self.assertEqual(1, script.run_import(rows, state, post=post))
            self.assertEqual(1, post.call_count)

    def test_dry_run_neither_writes_nor_posts(self):
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory) / 'state.json'
            post = Mock()
            self.assertEqual(0, script.run_import([{'subject': 'Example task'}], state, dry_run=True, post=post))
            post.assert_not_called()
            self.assertFalse(state.exists())

    def test_http_failure_is_nonzero(self):
        with tempfile.TemporaryDirectory() as directory:
            post = Mock(return_value=Mock(status_code=422))
            self.assertEqual(1, script.run_import([{'subject': 'Example task'}], Path(directory) / 'state.json', post=post))

    def test_organizer_resolves_configured_project(self):
        def api(method, path, **kwargs):
            if method == 'GET':
                self.assertEqual('/api/v3/projects/configured-project', path)
                return {'_links': {'self': {'href': '/api/v3/projects/42'}}}
            self.assertEqual('/api/v3/projects/42', kwargs['json']['_links']['definingProject']['href'])
            return kwargs['json']
        with patch.object(organize, 'PROJECT_ID', 'configured-project'), patch.object(organize, 'load_versions', return_value=[]), patch.object(organize, 'api', side_effect=api):
            organize.ensure_version({'name': 'Phase', 'description': 'Description', 'start': '2026-09-01'})

    def test_organizer_reads_all_pages(self):
        pages = [
            {'_embedded': {'elements': [{'id': 1}]}, '_links': {'nextByOffset': {'href': '/page2'}}},
            {'_embedded': {'elements': [{'id': 2}]}, '_links': {}},
        ]
        with patch.object(organize, 'api', side_effect=pages):
            self.assertEqual([{'id': 1}, {'id': 2}], organize.collection('/page1'))


if __name__ == '__main__':
    unittest.main()
