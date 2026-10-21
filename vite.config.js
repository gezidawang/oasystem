import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { resolve } from "path";

export default defineConfig({
  plugins: [vue()],

  // 路径别名
  resolve: {
    alias: {
      "@": resolve(import.meta.dirname, "src"),
    },
  },

  // 开发服务器配置
  server: {
    port: 5173,
    host: "localhost", 

    // 代理配置（解决跨域）
    proxy: {
      "/api": {
        target: "http://localhost:3000",
        changeOrigin: true,
      },
    },
  },

  // 构建配置
  build: {
    outDir: "dist",
    assetsDir: "assets",

    // 分包策略
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) return;
          if (id.includes("element-plus") || id.includes("@element-plus"))
            return "element-plus";
          if (id.includes("echarts")) return "echarts";
          if (
            id.includes("vue") ||
            id.includes("pinia") ||
            id.includes("vue-router") ||
            id.includes("axios")
          )
            return "vue-vendor";
          return "vendor";
        },
      },
    },
  },
});
