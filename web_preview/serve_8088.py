import http.server
import socketserver
import os

PORT = 8088
DIRECTORY = os.path.dirname(os.path.abspath(__file__))

class NoCacheHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def guess_type(self, path):
        if str(path).endswith('.apk'):
            return 'application/vnd.android.package-archive'
        return super().guess_type(path)

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0')
        self.send_header('Pragma', 'no-cache')
        self.send_header('Expires', '0')
        self.send_header('Access-Control-Allow-Origin', '*')
        if self.path.endswith('.apk') or 'apk' in self.path.lower():
            name = os.path.basename(self.path.split('?')[0])
            if not name.endswith('.apk'):
                name = 'SpendWise-v3.2.apk'
            self.send_header('Content-Disposition', f'attachment; filename="{name}"')
        super().end_headers()

class ThreadedTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    daemon_threads = True
    allow_reuse_address = True

if __name__ == '__main__':
    with ThreadedTCPServer(('', PORT), NoCacheHTTPRequestHandler) as httpd:
        print(f'Serving {DIRECTORY} on port {PORT} with multithreaded no-cache headers...')
        httpd.serve_forever()
