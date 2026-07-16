import json
from http.server import HTTPServer, BaseHTTPRequestHandler
import threading

class LambdaHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        print(f"HTTP Server Log: {format%args}")

    def do_GET(self):
        print(f"Received GET: {self.path}")
        if self.path.endswith('/next'):
            self.send_response(200)
            self.send_header('Lambda-Runtime-Aws-Request-Id', '12345')
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            
            event = {
                "version": "2.0",
                "routeKey": "$default",
                "rawPath": "/api/prayers",
                "rawQueryString": "",
                "headers": {
                    "host": "localhost:8080",
                    "accept": "*/*",
                    "content-type": "application/json"
                },
                "requestContext": {
                    "http": {
                        "method": "POST",
                        "path": "/api/prayers",
                        "protocol": "HTTP/1.1",
                        "userAgent": "curl/7.79.1"
                    }
                },
                "body": "{\"deviceId\":\"91af252d-9128-45cf-90a3-e4410bd73556\",\"prayerText\":\"This is a test\",\"groupId\":null}",
                "isBase64Encoded": False
            }
            self.wfile.write(json.dumps(event).encode())
            
    def do_POST(self):
        print(f"Received POST: {self.path}")
        
        post_data = b""
        if self.headers.get('Transfer-Encoding', '') == 'chunked':
            while True:
                line = self.rfile.readline().strip()
                if not line:
                    continue
                chunk_size = int(line, 16)
                if chunk_size == 0:
                    self.rfile.readline()
                    break
                post_data += self.rfile.read(chunk_size)
                self.rfile.readline()
        else:
            content_length = int(self.headers.get('Content-Length', 0))
            post_data = self.rfile.read(content_length)
            
        print(f"=== NATIVE IMAGE HANDLER HTTP {self.path} ===")
        print(post_data.decode('utf-8'))
        print("==========================================")
        
        self.send_response(202)
        self.end_headers()
        
        threading.Thread(target=self.server.shutdown).start()

print("Mock Lambda API Server Starting...")
server = HTTPServer(('127.0.0.1', 8082), LambdaHandler)
server.serve_forever()
