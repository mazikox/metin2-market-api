import json
import unittest
from usage_report import summarize, html_report


def entry(ip='192.0.2.1', uri='/', ts=1790856000, agent='Mozilla/5.0', host='metin2bazar.pl', status=200):
    return json.dumps({'logger': 'http.log.access.log0', 'ts': ts, 'status': status,
                       'request': {'host': host, 'method': 'GET', 'uri': uri,
                                   'remote_ip': ip, 'headers': {'User-Agent': [agent]}}})


class UsageReportTests(unittest.TestCase):
    def test_searches_ignore_startup_suggestions_pages_and_duplicates(self):
        initial = '/backend/api/v1/servers/pandora/items?query=Zatruty%20miecz&page=0' + ''.join('&vnum=' + str(n) for n in range(180, 190))
        search = '/backend/api/v1/servers/pandora/items?query=Miecz&page=0'
        data = summarize([entry(), entry(uri=initial), entry(uri=search, ts=1790856010),
                          entry(uri=search, ts=1790856011), entry(uri=search, ts=1790856014),
                          entry(uri=search.replace('page=0', 'page=1')),
                          entry(uri='/backend/api/v1/servers/pandora/items/suggestions?query=Miecz')])
        self.assertEqual(data['unique_ips'], 1)
        self.assertEqual(data['visitors'][0]['searches'], 2)
        self.assertEqual(data['visitors'][0]['results'], 5)

    def test_same_ip_across_days_is_one_in_period_and_each_day(self):
        data = summarize([entry(), entry(ts=1790942400), entry(ip='192.0.2.2')])
        self.assertEqual(data['unique_ips'], 2)
        self.assertEqual(sorted(row['unique_ips'] for row in data['daily']), [1, 2])

    def test_excludes_known_bots_other_hosts_errors_and_invalid_ip(self):
        data = summarize(['bad JSON', entry(agent='Googlebot'), entry(host='game.mazikox.pl'),
                          entry(status=404), entry(ip='not an IP'), entry()])
        self.assertEqual(data['unique_ips'], 1)

    def test_report_does_not_leak_ip_query_or_headers(self):
        search = '/backend/api/v1/servers/elder/items?query=PRIVATE_QUERY&page=0'
        text = html_report(summarize([entry(uri=search)]))
        self.assertNotIn('192.0.2.1', text)
        self.assertNotIn('PRIVATE_QUERY', text)
        self.assertNotIn('Mozilla', text)
        self.assertIn('Odwiedzający 1', text)


if __name__ == '__main__':
    unittest.main()
