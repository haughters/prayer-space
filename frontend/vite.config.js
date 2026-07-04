import { defineConfig } from 'vite';
import { resolve } from 'path';

export default defineConfig({
  root: resolve(__dirname, '.'),
  define: {
    __BUNDLED_DEV__: false,
    __SERVER_FORWARD_CONSOLE__: null,
  },
  server: {
    port: 5173,
    proxy: {
      '/api/prayers': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        secure: false,
      },
      '/api/groups': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        secure: false,
      },
      '/api/identity': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        secure: false,
      },
      '/api/admin': {
        target: 'http://localhost:8084',
        changeOrigin: true,
        secure: false,
      },
      '/api/auth': {
        target: 'http://localhost:8084',
        changeOrigin: true,
        secure: false,
      },
      '/api/notifications': {
        target: 'http://localhost:8085',
        changeOrigin: true,
        secure: false,
      }
    },
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url && req.url.startsWith('/pray/')) {
          req.url = '/pray.html';
        }
        next();
      });
    }
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        pray: resolve(__dirname, 'pray.html'),
        portal: resolve(__dirname, 'portal.html'),
        intercessor: resolve(__dirname, 'intercessor.html'),
      }
    }
  }
});
