import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import Components from 'unplugin-vue-components/vite'
import AutoImport from 'unplugin-auto-import/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    // 【性能 2026-08-08】Element Plus 按需引入：主包 1.1MB → ~1/3，首次加载提速
    AutoImport({ resolvers: [ElementPlusResolver()], dts: false }),
    Components({ resolvers: [ElementPlusResolver()], dts: false }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  build: {
    rolldownOptions: {
      output: {
        // 【性能 2026-08-08】按需引入把 element-plus 拆成几十个小 chunk → 慢网请求数爆炸。
        // 合并成少数 vendor chunk：请求数 90+ → ~10，慢网提速明显
        codeSplitting: {
          groups: [
            { name: 'el-plus', test: /node_modules[/\\](element-plus|@element-plus)/ },
            { name: 'vue-core', test: /node_modules[/\\](vue|@vue|pinia|vue-router)/ },
          ],
        },
      },
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
