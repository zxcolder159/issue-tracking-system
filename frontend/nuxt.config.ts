export default defineNuxtConfig({
  compatibilityDate: '2026-11-17',
  modules: ['@nuxt/ui'],
  ui: { fonts: false },
  colorMode: { preference: 'light' },
  css: ['~/assets/css/main.css'],
  devtools: { enabled: true },
  runtimeConfig: {
    backend: { baseURL: 'http://localhost:8080', email: '', password: '' },
    public: { projectId: 1 },
  },
  nitro: { preset: 'bun' },
  typescript: {
    tsConfig: {
      vueCompilerOptions: {
        plugins: ['@vue/language-plugin-pug'],
      },
    },
  },
})
